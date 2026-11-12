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

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.resource.ResourceStreamHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** G6: plugin scripts see working {@code repositories}, {@code log} and {@code security} bindings. */
class PluginBindingsTest {

    @TempDir
    Path dir;

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final PropertiesService propertiesService = mock(PropertiesService.class);
    private final SecurityService securityService = mock(SecurityService.class);
    private final RepoPath path = RepoPathFactory.create("libs-local", "a/b.txt");
    private PluginTestRig rig;

    @BeforeEach
    void setUp() {
        rig = new PluginTestRig(dir);
        rig.extraBindings.put("repositories",
                new PluginRepositories(repositoryService, propertiesService, path -> true));
        rig.extraBindings.put("security", new PluginSecurity(securityService));
    }

    @Test
    void bindingsMapHoldsTheThreeVariables() {
        Object repositories = new Object();
        Object security = new Object();
        Map<String, Object> bindings = GroovyPluginsAddon.bindings("p", repositories, security);
        assertInstanceOf(Logger.class, bindings.get("log"));
        assertEquals("org.artifactory.addon.plugin.groovy.user.p", ((Logger) bindings.get("log")).getName());
        assertSame(repositories, bindings.get("repositories"));
        assertSame(security, bindings.get("security"));
    }

    @Test
    void scriptUsesEveryRepositoriesMethodAndSecurityAndLog() throws Exception {
        FileInfo info = mock(FileInfo.class);
        when(info.getSize()).thenReturn(42L);
        when(repositoryService.getFileInfo(path)).thenReturn(info);
        when(repositoryService.exists(path)).thenReturn(true);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        when(repositoryService.getResourceStreamHandle(path)).thenReturn(handle);
        when(handle.getSize()).thenReturn(7L);
        when(repositoryService.deploy(eq(path), any(InputStream.class))).thenReturn(new BasicStatusHolder());
        when(repositoryService.undeploy(path)).thenReturn(new BasicStatusHolder());
        Properties props = mock(Properties.class);
        when(props.getFirst("qa")).thenReturn("pass");
        when(propertiesService.getProperties(path)).thenReturn(props);
        when(securityService.currentUsername()).thenReturn("bob");
        when(securityService.isAdmin()).thenReturn(true);

        rig.write("uses-bindings.groovy", """
                executions {
                    probe { params ->
                        def path = RepoPathFactory.create('libs-local', 'a/b.txt')
                        log.info('probing {}', path)
                        def parts = []
                        parts << repositories.exists(path)
                        parts << repositories.getFileInfo(path).size
                        parts << repositories.getContent(path).size
                        parts << repositories.deploy(path, new ByteArrayInputStream('x'.bytes)).error
                        parts << repositories.setProperty(path, 'qa', 'pass').getFirst('qa')
                        parts << repositories.getProperties(path).getFirst('qa')
                        parts << repositories.delete(path).error
                        parts << security.currentUsername << security.admin
                        message = parts.join(',')
                    }
                }
                """);
        rig.reload();

        ResponseCtx ctx = rig.addon.execute("probe", "POST", Map.of(), null, false);

        assertEquals("true,42,7,false,pass,pass,false,bob,true", ctx.getMessage(), String.valueOf(rig.reports));
        ArgumentCaptor<Property> property = ArgumentCaptor.forClass(Property.class);
        verify(propertiesService).addProperty(eq(path), isNull(), property.capture(), eq("pass"));
        assertEquals("qa", property.getValue().getName());
        verify(repositoryService).undeploy(path);
    }

    @Test
    void getChildrenDelegatesToTheRepositoryService() {
        ItemInfo child = mock(ItemInfo.class);
        when(repositoryService.getChildren(path)).thenReturn(List.of(child));
        assertEquals(List.of(child),
                new PluginRepositories(repositoryService, propertiesService, path -> true).getChildren(path));
    }

    @Test
    void stringContentAndAnonymousCheckDelegate() {
        when(repositoryService.getStringContent(path)).thenReturn("hello");
        when(securityService.isAnonymous()).thenReturn(true);
        assertEquals("hello",
                new PluginRepositories(repositoryService, propertiesService, path -> true).getStringContent(path));
        assertEquals(true, new PluginSecurity(securityService).isAnonymous());
    }
}
