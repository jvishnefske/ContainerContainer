/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.engine;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginUploadHandler;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeRegistryImpl;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeResolver;
import org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R4/R6: PUT routing through a plugin package type's upload handler in {@link UploadServiceImpl}. */
class UploadServiceImplPluginPackageTypeTest extends ArtifactoryHomeBoundJUnitTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private final ArtifactoryRequest request = mock(ArtifactoryRequest.class);
    private final ArtifactoryResponse response = mock(ArtifactoryResponse.class);
    private final RepoPath requested = RepoPathFactory.create("simple-local", "incoming/a-1.0.tgz");
    private UploadServiceImpl service;
    private LocalRepo<?, ?> repo;

    @BeforeEach
    void setUp() {
        service = new UploadServiceImpl();
        service.setPluginPackageTypeResolver(new PluginPackageTypeResolver(registry));
        repo = PluginTypeTestRepos.localRepo("simple-local", "simple");
        when(request.getRepoPath()).thenReturn(requested);
        when(request.getRepoKey()).thenReturn("simple-local");
        when(request.getPath()).thenReturn(requested.getPath());
    }

    private void register(PluginUploadHandler handler) {
        registry.replacePluginTypes("simple-plugin",
                List.of(PluginPackageType.builder("simple", "simple-plugin").upload(handler).build()));
    }

    @Test
    void acceptLeavesRequestUnchanged() throws Exception {
        register((req, path) -> UploadDecision.accept());
        Optional<ArtifactoryRequest> routed = service.routeByPluginPackageType(request, response, repo);
        assertSame(request, routed.orElseThrow());
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void rejectSendsStatusAndStoresNothing() throws Exception {
        register((req, path) -> UploadDecision.reject(409, "version already exists"));
        Optional<ArtifactoryRequest> routed = service.routeByPluginPackageType(request, response, repo);
        assertTrue(routed.isEmpty());
        verify(response).sendError(eq(409), eq("version already exists"), any());
    }

    @Test
    void rewriteInSameRepoTranslatesTargetPath() throws Exception {
        RepoPath target = RepoPathFactory.create("simple-local", "pool/a/a-1.0.tgz");
        register((req, path) -> UploadDecision.rewrite(target));
        ArtifactoryRequest routed = service.routeByPluginPackageType(request, response, repo).orElseThrow();
        assertEquals("pool/a/a-1.0.tgz", routed.getPath());
        assertEquals("simple-local", routed.getRepoKey());
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void rewriteToOtherRepoRejectedWith400() throws Exception {
        register((req, path) -> UploadDecision.rewrite(RepoPathFactory.create("other-local", "a-1.0.tgz")));
        Optional<ArtifactoryRequest> routed = service.routeByPluginPackageType(request, response, repo);
        assertTrue(routed.isEmpty());
        verify(response).sendError(eq(400), contains("another repository"), any());
    }

    @Test
    void handlerExceptionStoresUnchanged() throws Exception {
        register((req, path) -> {
            throw new IllegalStateException("plugin bug");
        });
        assertSame(request, service.routeByPluginPackageType(request, response, repo).orElseThrow());
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void unregisteredTypeStoresAsGeneric() throws Exception {
        assertSame(request, service.routeByPluginPackageType(request, response, repo).orElseThrow());
        verify(response, never()).sendError(anyInt(), any(), any());
    }

    @Test
    void folderCreationBypassesHandler() throws Exception {
        register((req, path) -> UploadDecision.reject(403, "never"));
        RepoPath folder = InternalRepoPathFactory.create("simple-local", "dir", true);
        when(request.getRepoPath()).thenReturn(folder);
        assertSame(request, service.routeByPluginPackageType(request, response, repo).orElseThrow());
    }
}
