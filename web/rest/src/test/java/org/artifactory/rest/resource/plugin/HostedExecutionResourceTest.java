/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.plugin;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.groovy.Caller;
import org.artifactory.addon.plugin.groovy.ExecutionHook;
import org.artifactory.addon.plugin.groovy.GroovyPluginCompiler;
import org.artifactory.addon.plugin.groovy.GroovyPluginEngine;
import org.artifactory.addon.plugin.groovy.GroovyPluginsAddon;
import org.artifactory.addon.plugin.groovy.HookFailureReporter;
import org.artifactory.addon.plugin.groovy.HostedOutcome;
import org.artifactory.addon.plugin.groovy.LoadedPlugin;
import org.artifactory.addon.plugin.groovy.PluginExecution;
import org.artifactory.addon.plugin.groovy.PluginLoadResult;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.addon.rest.RestAddonDefaultImpl;
import org.artifactory.aol.AolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W2, G4 at the REST layer: a hosted (WASM) execution's status, headers and body reach the HTTP response through
 * {@link PluginsResource}, while a Groovy execution's {@code headers} are still not sent (W6).
 */
class HostedExecutionResourceTest {

    @TempDir
    Path dir;

    private final AddonsManager addonsManager = mock(AddonsManager.class);
    private final Request request = mock(Request.class);
    private Caller caller = new Caller("bob", false, Set.of());
    private PluginsResource resource;

    @BeforeEach
    void setUp() throws Exception {
        ExecutionHook.Call hosted = call -> call.name().equals("fails")
                ? new HostedOutcome.Failed<>("trap")
                : new HostedOutcome.Answered<>(new ExecutionHook.Response(201,
                        Map.of("content-type", List.of("application/json"),
                                "x-echo", List.of(call.caller().username())),
                        "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));
        LoadedPlugin plugin = new LoadedPlugin("hosted", "hosted.wasm", List.of(), List.of(
                new PluginExecution("hosted", "echo", Set.of("*"), Set.of(), "POST", "undefined", "",
                        new ExecutionHook.Hosted(hosted)),
                new PluginExecution("hosted", "fails", Set.of("*"), Set.of(), "POST", "undefined", "",
                        new ExecutionHook.Hosted(hosted))), List.of(), List.of());
        GroovyPluginEngine engine = new GroovyPluginEngine(() -> dir,
                new GroovyPluginCompiler(getClass().getClassLoader(), HookFailureReporter.logging()),
                new NoTypes(), name -> GroovyPluginsAddon.bindings(name, null, null),
                (p, w, e) -> { }, d -> List.of(new PluginLoadResult.Loaded(plugin)));
        GroovyPluginsAddon addon = new GroovyPluginsAddon(engine, () -> caller, Runnable::run);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(addon);
        RestAddon restAddon = new RestAddonDefaultImpl(addonsManager, () -> caller.admin());
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        when(request.getMethod()).thenReturn("POST");
        resource = new PluginsResource(mock(BuildFacade.class), addonsManager, mock(AolService.class));
        Files.writeString(dir.resolve("groovy.groovy"), """
                executions { plain(users: ['*']) { params -> headers = ['X-Groovy': 'yes']; message = 'hi' } }
                """, StandardCharsets.UTF_8);
        engine.reload();
    }

    private static InputStream empty() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Test
    void aHostedExecutionSendsItsStatusHeadersAndBody() throws Exception {
        Response response = resource.execute(request, empty(), "echo", null, 0);
        assertEquals(201, response.getStatus());
        assertEquals("application/json", String.valueOf(response.getMetadata().getFirst("Content-Type")));
        assertEquals("bob", String.valueOf(response.getMetadata().getFirst("x-echo")));
        assertEquals("{\"ok\":true}", new String(((InputStream) response.getEntity()).readAllBytes(),
                StandardCharsets.UTF_8));
    }

    @Test
    void aFailedHostedExecutionIs500AndGroovyHeadersStayUnsent() throws Exception {
        assertEquals(500, resource.execute(request, empty(), "fails", null, 0).getStatus());
        Response plain = resource.execute(request, empty(), "plain", null, 0);
        assertEquals(200, plain.getStatus());
        assertEquals("hi", plain.getEntity());
        assertNull(plain.getMetadata().getFirst("X-Groovy"));
        caller = new Caller("anonymous", false, Set.of());
        assertEquals(403, resource.execute(request, empty(), "echo", null, 0).getStatus());
    }

    private static final class NoTypes implements PluginPackageTypeRegistry {
        @Override
        public List<String> replacePluginTypes(String pluginName, List<PluginPackageType> types) {
            return List.of();
        }

        @Override
        public void removePluginTypes(String pluginName) {
        }

        @Override
        public Optional<PluginPackageType> find(String name) {
            return Optional.empty();
        }

        @Override
        public Collection<PluginPackageType> all() {
            return List.of();
        }
    }
}
