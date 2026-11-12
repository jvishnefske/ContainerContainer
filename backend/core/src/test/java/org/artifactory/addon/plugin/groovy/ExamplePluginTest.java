/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.Request;
import org.artifactory.resource.ResourceStreamHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The shipped example plugin {@code plugins/examples/simple-index.groovy} compiles and behaves as documented (R7). */
class ExamplePluginTest {

    /** Relative to the backend/core module directory, where Maven runs the tests. */
    private static final Path EXAMPLE = Path.of("..", "..", "plugins", "examples", "simple-index.groovy");
    private static final String REPO = "simple-local";

    @TempDir
    Path dir;

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final Request request = mock(Request.class);
    private PluginTestRig rig;
    private PluginPackageType type;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        rig.extraBindings.put("repositories",
                new PluginRepositories(repositoryService, mock(PropertiesService.class), path -> true));
        rig.extraBindings.put("security", new PluginSecurity(mock(SecurityService.class)));
        rig.write("simple-index.groovy", Files.readString(EXAMPLE, StandardCharsets.UTF_8));
        rig.reload();
        assertTrue(rig.reports.isEmpty(), String.valueOf(rig.reports));
        type = rig.registry.find("simpleidx").orElseThrow();
    }

    private static RepoPath path(String path) {
        return RepoPathFactory.create(REPO, path);
    }

    private static ItemInfo item(String path, boolean folder) {
        ItemInfo info = mock(ItemInfo.class);
        when(info.isFolder()).thenReturn(folder);
        when(info.getRepoPath()).thenReturn(path(path));
        return info;
    }

    @Test
    void uploadAcceptsOnlyTgz() {
        var upload = type.upload().orElseThrow();
        assertInstanceOf(UploadDecision.Accept.class, upload.decide(request, path("a-1.0.tgz")));
        // The plugin writes its index through the same upload path.
        assertInstanceOf(UploadDecision.Accept.class, upload.decide(request, path("index.json")));
        UploadDecision.Reject reject = assertInstanceOf(UploadDecision.Reject.class,
                upload.decide(request, path("notes.txt")));
        assertEquals(400, reject.status());
    }

    @Test
    void indexListsEveryTgzInTheRepository() throws Exception {
        List<ItemInfo> root = List.of(item("index.json", false), item("a-1.0.tgz", false), item("sub", true));
        List<ItemInfo> sub = List.of(item("sub/b-2.0.tgz", false));
        when(repositoryService.getChildren(path(""))).thenReturn(root);
        when(repositoryService.getChildren(path("sub"))).thenReturn(sub);
        ArgumentCaptor<InputStream> body = ArgumentCaptor.forClass(InputStream.class);
        when(repositoryService.deploy(eq(path("index.json")), any(InputStream.class)))
                .thenReturn(new BasicStatusHolder());

        type.index().orElseThrow().onStored(path("a-1.0.tgz"));

        assertTrue(rig.reports.isEmpty(), String.valueOf(rig.reports));
        verify(repositoryService).deploy(eq(path("index.json")), body.capture());
        assertEquals("{\"packages\":[\"a-1.0.tgz\",\"sub/b-2.0.tgz\"]}\n",
                new String(body.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void downloadDeclinesEverythingButTheIndex() {
        assertFalse(type.download().orElseThrow().handle(request, path("a-1.0.tgz"), new ResponseCtx()));
    }

    @Test
    void downloadServesTheStoredIndexWithItsSize() throws Exception {
        byte[] stored = "{\"packages\":[]}\n".getBytes(StandardCharsets.UTF_8);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        when(handle.getSize()).thenReturn((long) stored.length);
        when(handle.getInputStream()).thenReturn(new ByteArrayInputStream(stored));
        when(repositoryService.exists(path("index.json"))).thenReturn(true);
        when(repositoryService.getResourceStreamHandle(path("index.json"))).thenReturn(handle);

        ResponseCtx response = new ResponseCtx();
        assertTrue(type.download().orElseThrow().handle(request, path("index.json"), response));

        assertEquals(200, response.getStatus());
        assertEquals(stored.length, response.getSize());
        assertEquals(new String(stored, StandardCharsets.UTF_8),
                new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void reindexExecutionIsPostOnlyAndNeedsARepo() {
        ResponseCtx ctx = rig.addon.execute("simpleidxReindex", "POST", Map.of(), null, false);
        assertEquals(400, ctx.getStatus());
        assertEquals(405, rig.addon.execute("simpleidxReindex", "GET", Map.of("repo", List.of(REPO)), null, false)
                .getStatus());
    }
}
