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
import org.artifactory.addon.plugin.download.AfterDownloadAction;
import org.artifactory.addon.plugin.download.AltResponseAction;
import org.artifactory.addon.plugin.download.BeforeDownloadRequestAction;
import org.artifactory.addon.plugin.download.DownloadCtx;
import org.artifactory.addon.plugin.storage.AfterCreateAction;
import org.artifactory.addon.plugin.storage.AfterDeleteAction;
import org.artifactory.addon.plugin.storage.AfterMoveAction;
import org.artifactory.addon.plugin.storage.AfterPropertyCreateAction;
import org.artifactory.addon.plugin.storage.BeforeCreateAction;
import org.artifactory.addon.plugin.storage.BeforeDeleteAction;
import org.artifactory.addon.plugin.upload.BeforeUploadRequestAction;
import org.artifactory.addon.plugin.upload.UploadCtx;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.request.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G3: each DSL block binds its closures to the existing {@code PluginAction} interfaces and fires when the
 * call sites invoke {@code PluginsAddon.execPluginActions} with the arguments they pass in production.
 */
class PluginDslBindingTest {

    @TempDir
    Path dir;

    private PluginTestRig rig;
    private Request request;
    private RepoPath repoPath;
    private ItemInfo item;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        request = mock(Request.class);
        repoPath = RepoPathFactory.create("libs-local", "org/a/1.0/a-1.0.jar");
        when(request.getRepoPath()).thenReturn(repoPath);
        item = mock(ItemInfo.class);
        when(item.getRepoPath()).thenReturn(repoPath);
        rig.write("all-blocks.groovy", """
                download {
                    beforeDownloadRequest { request, repoPath ->
                        calls << 'beforeDownloadRequest:' + repoPath.path
                        modifiedRepoPath = RepoPathFactory.create(repoPath.repoKey, 'redirected/' + repoPath.path)
                    }
                    altResponse { request, responseRepoPath ->
                        calls << 'altResponse'
                        status = 451
                        message = 'unavailable for legal reasons'
                    }
                    afterDownload { request, response -> calls << 'afterDownload:' + response.status }
                }
                storage {
                    beforeCreate { item -> calls << 'beforeCreate:' + item.repoPath.name }
                    afterCreate { item -> calls << 'afterCreate:' + item.repoPath.name }
                    beforeDelete { item -> calls << 'beforeDelete:' + item.repoPath.name }
                    afterDelete { item -> calls << 'afterDelete:' + item.repoPath.name }
                    afterPropertyCreate { item, name, values -> calls << 'prop:' + name + '=' + values.join(',') }
                }
                upload {
                    beforeUploadRequest { request, repoPath ->
                        modifiedRepoPath = RepoPathFactory.create('other', repoPath.path)
                    }
                }
                executions {
                    greet(httpMethod: 'GET', version: '1.2') { params ->
                        message = 'hello ' + params['name'][0]
                        status = 201
                    }
                }
                """);
        rig.reload();
    }

    @Test
    void downloadBeforeDownloadRequestSetsModifiedPathOnDownloadCtx() {
        DownloadCtx ctx = new DownloadCtx();
        rig.addon.execPluginActions(BeforeDownloadRequestAction.class, ctx, request, repoPath);
        assertEquals(List.of("beforeDownloadRequest:org/a/1.0/a-1.0.jar"), rig.calls);
        assertEquals("redirected/org/a/1.0/a-1.0.jar", ctx.getModifiedRepoPath().getPath());
    }

    @Test
    void downloadAltResponseSetsStatusAndMessageOnResponseCtx() {
        ResponseCtx ctx = new ResponseCtx();
        rig.addon.execPluginActions(AltResponseAction.class, ctx, request, repoPath);
        assertEquals(451, ctx.getStatus());
        assertEquals("unavailable for legal reasons", ctx.getMessage());
    }

    @Test
    void downloadAfterDownloadReceivesTheResponse() {
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(200);
        rig.addon.execPluginActions(AfterDownloadAction.class, new Object(), request, response);
        assertEquals(List.of("afterDownload:200"), rig.calls);
    }

    @Test
    void storageHooksFireForCreateAndDelete() {
        rig.addon.execPluginActions(BeforeCreateAction.class, null, item);
        rig.addon.execPluginActions(AfterCreateAction.class, null, item);
        rig.addon.execPluginActions(BeforeDeleteAction.class, null, item);
        rig.addon.execPluginActions(AfterDeleteAction.class, null, item);
        assertEquals(List.of("beforeCreate:a-1.0.jar", "afterCreate:a-1.0.jar",
                "beforeDelete:a-1.0.jar", "afterDelete:a-1.0.jar"), rig.calls);
    }

    @Test
    void storagePropertyHookReceivesVarargValues() {
        rig.addon.execPluginActions(AfterPropertyCreateAction.class, null, item, "qa", new String[]{"pass", "v2"});
        assertEquals(List.of("prop:qa=pass,v2"), rig.calls);
    }

    @Test
    void uploadBeforeUploadRequestRewritesPathOnUploadCtx() {
        UploadCtx ctx = new UploadCtx();
        rig.addon.execPluginActions(BeforeUploadRequestAction.class, ctx, request, repoPath);
        assertEquals("other", ctx.getModifiedRepoPath().getRepoKey());
    }

    @Test
    void executionsBlockDeclaresANamedExecution() {
        ResponseCtx ctx = rig.addon.execute("greet", "GET", Map.of("name", List.of("bob")), null, false);
        assertEquals(201, ctx.getStatus());
        assertEquals("hello bob", ctx.getMessage());
        PluginExecution greet = rig.engine.snapshot().execution("greet").orElseThrow();
        assertEquals("GET", greet.httpMethod());
        assertEquals("1.2", greet.version());
    }

    @Test
    void hooksBindToTheCatalogActionTypes() {
        LoadedPlugin plugin = rig.engine.snapshot().loaded().get(0);
        for (PluginHook hook : plugin.hooks()) {
            assertSame(HookCatalog.actionType(hook.block(), hook.hookName()).orElseThrow(), hook.actionType());
        }
        assertEquals(9, plugin.hooks().size());
        assertTrue(rig.addon.isPluginSupported(AltResponseAction.class));
        assertFalse(rig.addon.isPluginSupported(AfterMoveAction.class));
    }

    @Test
    void hookClosureMayDeclareFewerParametersThanTheCallSitePasses() throws Exception {
        rig.write("short.groovy", "download { altResponse { request -> status = 404 } }");
        rig.reload();
        ResponseCtx ctx = new ResponseCtx();
        rig.addon.execPluginActions(AltResponseAction.class, ctx, request, repoPath);
        assertEquals(404, ctx.getStatus());
    }

    @Test
    void declaringHooksFromInsideAHookAtRequestTimeIsRejected() throws Exception {
        rig.write("sneaky.groovy", "storage { afterCreate { item -> afterDelete { i -> } } }");
        rig.reload();
        rig.reports.clear();
        rig.addon.execPluginActions(AfterCreateAction.class, null, item);
        assertTrue(rig.reports.stream().anyMatch(r -> r.pluginName().equals("sneaky")));
        assertEquals(1, rig.engine.snapshot().hooksFor(AfterDeleteAction.class).size(), "only all-blocks' hook");
    }
}
