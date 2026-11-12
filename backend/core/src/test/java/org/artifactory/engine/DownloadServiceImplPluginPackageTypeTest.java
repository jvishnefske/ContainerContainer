/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.engine;

import org.artifactory.api.common.BasicStatusHolder;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeRegistryImpl;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeResolver;
import org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.fs.RepoResource;
import org.artifactory.plugin.AltResponseHelper;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** R3/R6: GET/HEAD routing through a plugin package type's download handler in {@link DownloadServiceImpl}. */
class DownloadServiceImplPluginPackageTypeTest extends ArtifactoryHomeBoundJUnitTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private final RequestResponseHelper requestResponseHelper = mock(RequestResponseHelper.class);
    private final ArtifactoryResponse response = mock(ArtifactoryResponse.class);
    private final InternalRequestContext requestContext = mock(InternalRequestContext.class);
    private final Request request = mock(Request.class);
    private final RepoPath path = RepoPathFactory.create("simple-local", "index.json");
    private final AtomicInteger calls = new AtomicInteger();
    private DownloadServiceImpl service;
    private LocalRepo<?, ?> repo;

    @BeforeEach
    void setUp() {
        service = new DownloadServiceImpl();
        service.altResponseHelper = new AltResponseHelper(mock(AddonsManager.class));
        service.requestResponseHelper = requestResponseHelper;
        service.pluginPackageTypeResolver = new PluginPackageTypeResolver(registry);
        repo = PluginTypeTestRepos.localRepo("simple-local", "simple");
        when(requestContext.getRequest()).thenReturn(request);
        when(request.getRepoPath()).thenReturn(path);
    }

    private void register(PluginPackageType.Builder builder) {
        registry.replacePluginTypes("simple-plugin", List.of(builder.build()));
    }

    @Test
    void handledMessageIsServedAsBody() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            calls.incrementAndGet();
            resp.setMessage("{\"packages\":[]}");
            return true;
        }));

        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));

        assertEquals(1, calls.get());
        ArgumentCaptor<ResourceStreamHandle> handle = ArgumentCaptor.forClass(ResourceStreamHandle.class);
        ArgumentCaptor<RepoResource> resource = ArgumentCaptor.forClass(RepoResource.class);
        verify(requestResponseHelper).sendBodyResponse(eq(response), resource.capture(), handle.capture(),
                eq(requestContext), anyBoolean());
        assertEquals("{\"packages\":[]}",
                IOUtils.toString(handle.getValue().getInputStream(), StandardCharsets.UTF_8));
        assertEquals(path, resource.getValue().getRepoPath());
    }

    @Test
    void handlerNotOfferedWhenDownloadIsNotAllowed() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            calls.incrementAndGet();
            resp.setMessage("generated index that must not leak");
            return true;
        }));
        BasicStatusHolder denied = new BasicStatusHolder();
        denied.error("forbidden", 403, org.slf4j.LoggerFactory.getLogger(getClass()));
        when(repo.checkDownloadIsAllowed(path)).thenReturn(denied);

        assertFalse(service.respondedByPluginPackageType(requestContext, response, repo));

        assertEquals(0, calls.get());
        verifyNoInteractions(requestResponseHelper);
    }

    @Test
    void handledStreamIsServedAsBody() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            resp.setInputStream(new ByteArrayInputStream("stored".getBytes(StandardCharsets.UTF_8)));
            resp.setSize(6);
            return true;
        }));
        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));
        ArgumentCaptor<ResourceStreamHandle> handle = ArgumentCaptor.forClass(ResourceStreamHandle.class);
        verify(requestResponseHelper).sendBodyResponse(eq(response), any(), handle.capture(), any(), anyBoolean());
        assertEquals("stored", IOUtils.toString(handle.getValue().getInputStream(), StandardCharsets.UTF_8));
    }

    @Test
    void handledStreamWithoutSizeIsBufferedAndSentThroughTheNormalBodyPath() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            resp.setInputStream(new ByteArrayInputStream("stored".getBytes(StandardCharsets.UTF_8)));
            return true;
        }));
        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));
        ArgumentCaptor<ResourceStreamHandle> handle = ArgumentCaptor.forClass(ResourceStreamHandle.class);
        ArgumentCaptor<RepoResource> resource = ArgumentCaptor.forClass(RepoResource.class);
        verify(requestResponseHelper).sendBodyResponse(eq(response), resource.capture(), handle.capture(),
                eq(requestContext), anyBoolean());
        assertEquals(6, handle.getValue().getSize());
        assertEquals(6, resource.getValue().getSize());
        assertEquals("stored", IOUtils.toString(handle.getValue().getInputStream(), StandardCharsets.UTF_8));
        verify(response, never()).sendStream(any());
    }

    @Test
    void unsizedStreamOverTheBufferLimitIsRefused() throws Exception {
        byte[] big = new byte[DownloadServiceImpl.MAX_UNSIZED_PLUGIN_RESPONSE_BYTES + 1];
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            resp.setInputStream(new ByteArrayInputStream(big));
            return true;
        }));
        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));
        verify(response).sendError(eq(500), contains("has no size"), any());
        verify(requestResponseHelper, never()).sendBodyResponse(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void handledErrorStatusIsSentAsError() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            resp.setStatus(404);
            resp.setMessage("no such package");
            return true;
        }));
        when(response.isError()).thenReturn(true);
        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));
        verify(response).sendError(eq(404), eq("no such package"), any());
        verifyNoInteractions(requestResponseHelper);
    }

    @Test
    void handledHeadSendsHeadResponse() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            resp.setMessage("abc");
            return true;
        }));
        when(request.isHeadOnly()).thenReturn(true);
        assertTrue(service.respondedByPluginPackageType(requestContext, response, repo));
        verify(requestResponseHelper).sendHeadResponse(eq(response), any(), any());
        verify(requestResponseHelper, never()).sendBodyResponse(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void declinedFallsThroughToGeneric() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            calls.incrementAndGet();
            return false;
        }));
        assertFalse(service.respondedByPluginPackageType(requestContext, response, repo));
        assertEquals(1, calls.get());
        verifyNoInteractions(requestResponseHelper);
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void handlerExceptionFallsThroughToGeneric() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            throw new IllegalStateException("plugin bug");
        }));
        assertFalse(service.respondedByPluginPackageType(requestContext, response, repo));
        verifyNoInteractions(requestResponseHelper);
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void unregisteredTypeFallsThroughToGeneric() throws Exception {
        assertFalse(service.respondedByPluginPackageType(requestContext, response, repo));
        registry.replacePluginTypes("simple-plugin", List.of(PluginPackageType.builder("simple", "simple-plugin")
                .download((req, p, resp) -> {
                    calls.incrementAndGet();
                    return true;
                }).build()));
        registry.removePluginTypes("simple-plugin");
        assertFalse(service.respondedByPluginPackageType(requestContext, response, repo));
        assertEquals(0, calls.get());
        verifyNoInteractions(requestResponseHelper);
    }

    @Test
    void remoteRepoWithTheKeyIsNotRouted() throws Exception {
        register(PluginPackageType.builder("simple", "simple-plugin").download((req, p, resp) -> {
            calls.incrementAndGet();
            return true;
        }));
        assertFalse(service.respondedByPluginPackageType(requestContext, response,
                PluginTypeTestRepos.nonLocalRepo("simple-remote", "simple")));
        assertFalse(service.respondedByPluginPackageType(requestContext, response, null));
        assertEquals(0, calls.get());
    }
}
