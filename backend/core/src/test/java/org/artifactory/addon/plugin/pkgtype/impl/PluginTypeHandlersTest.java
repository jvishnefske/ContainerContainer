/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/** Plugin boundary: every handler outcome, including exceptions, becomes a value; upload routing is pure. */
class PluginTypeHandlersTest {

    private final Request request = mock(Request.class);
    private final RepoPath path = RepoPathFactory.create("r", "a/b.txt");

    @Test
    void downloadHandledCarriesFilledResponse() {
        PluginPackageType type = PluginPackageType.builder("t", "p").download((req, p, resp) -> {
            resp.setMessage("index");
            return true;
        }).build();
        PluginTypeHandlers.DownloadOutcome outcome =
                PluginTypeHandlers.offerDownload(type, request, path, ResponseCtx::new);
        ResponseCtx response = assertInstanceOf(PluginTypeHandlers.DownloadOutcome.Handled.class, outcome).response();
        assertEquals("index", response.getMessage());
        assertEquals(200, response.getStatus());
    }

    @Test
    void downloadDeclinedAndMissingHandler() {
        PluginPackageType declining = PluginPackageType.builder("t", "p").download((req, p, resp) -> false).build();
        assertInstanceOf(PluginTypeHandlers.DownloadOutcome.Declined.class,
                PluginTypeHandlers.offerDownload(declining, request, path, ResponseCtx::new));
        assertInstanceOf(PluginTypeHandlers.DownloadOutcome.Declined.class, PluginTypeHandlers.offerDownload(
                PluginPackageType.builder("t", "p").build(), request, path, ResponseCtx::new));
    }

    @Test
    void downloadExceptionBecomesFailed() {
        PluginPackageType throwing = PluginPackageType.builder("t", "p").download((req, p, resp) -> {
            throw new IllegalStateException("boom");
        }).build();
        PluginTypeHandlers.DownloadOutcome outcome =
                PluginTypeHandlers.offerDownload(throwing, request, path, ResponseCtx::new);
        assertEquals("boom",
                assertInstanceOf(PluginTypeHandlers.DownloadOutcome.Failed.class, outcome).error().getMessage());
    }

    @Test
    void uploadDecisionsAndFailures() {
        assertInstanceOf(PluginTypeHandlers.UploadOutcome.Decided.class,
                PluginTypeHandlers.decideUpload(PluginPackageType.builder("t", "p").build(), request, path));
        PluginPackageType throwing = PluginPackageType.builder("t", "p").upload((req, p) -> {
            throw new IllegalArgumentException("bad");
        }).build();
        assertInstanceOf(PluginTypeHandlers.UploadOutcome.Failed.class,
                PluginTypeHandlers.decideUpload(throwing, request, path));
        PluginPackageType nullDecision = PluginPackageType.builder("t", "p").upload((req, p) -> null).build();
        assertInstanceOf(PluginTypeHandlers.UploadOutcome.Failed.class,
                PluginTypeHandlers.decideUpload(nullDecision, request, path));
    }

    @Test
    void routeAcceptStoresAtRequestedPath() {
        assertEquals(new PluginTypeHandlers.UploadRoute.Store(path),
                PluginTypeHandlers.route(UploadDecision.accept(), path));
    }

    @Test
    void routeRejectRefusesWithStatusAndMessage() {
        assertEquals(new PluginTypeHandlers.UploadRoute.Refuse(403, "no"),
                PluginTypeHandlers.route(UploadDecision.reject(403, "no"), path));
    }

    @Test
    void routeRewriteInSameRepoStoresAtTarget() {
        RepoPath target = RepoPathFactory.create("r", "pool/b.txt");
        assertEquals(new PluginTypeHandlers.UploadRoute.Store(target),
                PluginTypeHandlers.route(UploadDecision.rewrite(target), path));
    }

    @Test
    void routeRewriteToOtherRepoRefusedWith400() {
        PluginTypeHandlers.UploadRoute route = PluginTypeHandlers.route(
                UploadDecision.rewrite(RepoPathFactory.create("other", "b.txt")), path);
        assertEquals(400, assertInstanceOf(PluginTypeHandlers.UploadRoute.Refuse.class, route).status());
    }
}
