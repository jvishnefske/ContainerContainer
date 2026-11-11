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
import org.artifactory.addon.plugin.PluginsAddonDefaultImpl;
import org.artifactory.addon.plugin.groovy.Caller;
import org.artifactory.addon.plugin.groovy.GroovyPluginCompiler;
import org.artifactory.addon.plugin.groovy.GroovyPluginEngine;
import org.artifactory.addon.plugin.groovy.GroovyPluginsAddon;
import org.artifactory.addon.plugin.groovy.HookFailureReporter;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.addon.rest.RestAddonDefaultImpl;
import org.artifactory.aol.AolService;
import org.artifactory.rest.exception.MissingRestAddonException;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G2, G4, G5 at the REST layer: {@link PluginsResource} through {@link RestAddonDefaultImpl} to a real
 * {@link GroovyPluginsAddon} loading scripts from a temporary plugins directory.
 */
class GroovyPluginsResourceTest {

    @TempDir
    Path dir;

    private final AddonsManager addonsManager = mock(AddonsManager.class);
    private final AolService aolService = mock(AolService.class);
    private final Request request = mock(Request.class);
    private Caller caller = new Caller("admin", true, Set.of());
    private PluginsResource resource;

    @BeforeEach
    void setUp() throws Exception {
        GroovyPluginEngine engine = new GroovyPluginEngine(() -> dir,
                new GroovyPluginCompiler(getClass().getClassLoader(), HookFailureReporter.logging()),
                new NoTypesRegistry(), name -> GroovyPluginsAddon.bindings(name, null, null),
                HookFailureReporter.logging());
        GroovyPluginsAddon addon = new GroovyPluginsAddon(engine, () -> caller, Runnable::run);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(addon);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(new RestAddonDefaultImpl(addonsManager));
        when(aolService.isAol()).thenReturn(false);
        when(request.getMethod()).thenReturn("POST");
        resource = new PluginsResource(mock(BuildFacade.class), addonsManager, aolService);
        Files.writeString(dir.resolve("hello.groovy"), """
                storage { afterCreate { item -> } }
                executions {
                    hello(groups: ['friends']) { params, body ->
                        message = 'hello ' + (body?.inputStream?.text ?: 'nobody')
                        status = 200
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("broken.groovy"), "executions {", StandardCharsets.UTF_8);
    }

    private static InputStream body(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void reloadLoadsPluginsAndReportsEachFile() throws Exception {
        Response response = resource.reload(false);
        assertEquals(200, response.getStatus());
        String text = new String(((InputStream) response.getEntity()).readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("hello.groovy: Loaded"), text);
        assertTrue(text.contains("broken.groovy: Error"), text);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingShowsExecutionsAndPluginsWithHooks() {
        resource.reload(false);
        Response response = resource.getPluginInfo();
        assertEquals(200, response.getStatus());
        Map<String, Object> entity = assertInstanceOf(Map.class, response.getEntity());
        assertEquals(1, ((List<?>) entity.get("executions")).size());
        Map<String, Map<String, String>> plugins = (Map<String, Map<String, String>>) entity.get("plugins");
        assertEquals("storage.afterCreate", plugins.get("hello").get("hooks"));
        assertEquals("Error", plugins.get("broken").get("status"));
    }

    @Test
    void executeRunsTheNamedExecutionAndReturnsItsStatusAndMessage() throws Exception {
        resource.reload(false);
        Response response = resource.execute(request, body("world"), "hello", null, 0);
        assertEquals(200, response.getStatus());
        assertEquals("hello world", response.getEntity());
    }

    @Test
    void executeIsAdminOnlyUnlessTheExecutionAllowsTheCaller() throws Exception {
        resource.reload(false);
        caller = new Caller("eve", false, Set.of("strangers"));
        assertEquals(403, resource.execute(request, body(""), "hello", null, 0).getStatus());
        caller = new Caller("frank", false, Set.of("friends"));
        assertEquals(200, resource.execute(request, body(""), "hello", null, 0).getStatus());
        assertEquals(404, resource.execute(request, body(""), "missing", null, 0).getStatus());
    }

    @Test
    void statusEndpointReportsPlugins() {
        resource.reload(false);
        Response response = resource.getPluginStatus();
        assertEquals(200, response.getStatus());
        assertTrue(String.valueOf(response.getEntity()).contains("hello.groovy: Loaded"));
    }

    @Test
    void stockDefaultPluginsAddonKeepsTheEndpointsUnavailable() {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(new PluginsAddonDefaultImpl());
        // The resource maps any reload failure to 500, as it does for stock OSS.
        assertEquals(500, resource.reload(false).getStatus());
        assertThrows(MissingRestAddonException.class, resource::getPluginInfo);
    }

    /** Registry stub: these scripts declare no package types. */
    private static final class NoTypesRegistry implements PluginPackageTypeRegistry {
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
