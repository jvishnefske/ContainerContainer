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
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The {@code packageType} DSL builds the {@link PluginPackageType} objects of the contract (R1, DSL half). */
class PackageTypeDslTest {

    private static final String SCRIPT = """
            packageType('simple-index') {
                displayName 'Simple index'
                layout 'simple-default'
                download { request, repoPath, response ->
                    if (repoPath.path == 'index.txt') {
                        status = 200
                        response.inputStream = new ByteArrayInputStream('a\\nb\\n'.bytes)
                        return true
                    }
                    if (repoPath.path == 'gone') {
                        throw new CancelException('gone for good', 410)
                    }
                    if (repoPath.path == 'broken') {
                        throw new IllegalStateException('download handler bug')
                    }
                    false
                }
                upload { request, repoPath ->
                    if (repoPath.path.endsWith('.exe')) {
                        return UploadDecision.reject(403, 'no executables')
                    }
                    if (repoPath.path.startsWith('tmp/')) {
                        def target = RepoPathFactory.create(repoPath.repoKey, repoPath.path.substring(4))
                        return UploadDecision.rewrite(target)
                    }
                    if (repoPath.path == 'conflict') {
                        throw new CancelException('already there', 409)
                    }
                    if (repoPath.path == 'weird') {
                        return 'not a decision'
                    }
                    null
                }
                index { repoPath -> calls << 'indexed:' + repoPath.path }
            }
            packageType('bare') { }
            """;

    @TempDir
    Path dir;

    private PluginTestRig rig;
    private PluginPackageType type;
    private final Request request = mock(Request.class);

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        rig.write("simple.groovy", SCRIPT);
        rig.reload();
        type = rig.registry.find("simple-index").orElseThrow();
    }

    private static RepoPath path(String path) {
        return RepoPathFactory.create("simple-local", path);
    }

    @Test
    void blockBuildsTheDescribedType() {
        assertEquals("simple-index", type.name());
        assertEquals("Simple index", type.displayName());
        assertEquals("simple", type.pluginName());
        assertEquals(Optional.of("simple-default"), type.layoutName());
        assertTrue(type.download().isPresent());
        assertTrue(type.upload().isPresent());
        assertTrue(type.index().isPresent());
        assertEquals(List.of("simple-index", "bare"),
                rig.engine.snapshot().loaded().get(0).packageTypes().stream().map(PluginPackageType::name).toList());
    }

    @Test
    void emptyBlockDefaultsDisplayNameAndHasNoHandlers() {
        PluginPackageType bare = rig.registry.find("bare").orElseThrow();
        assertEquals("bare", bare.displayName());
        assertTrue(bare.layoutName().isEmpty());
        assertTrue(bare.download().isEmpty());
        assertTrue(bare.upload().isEmpty());
        assertTrue(bare.index().isEmpty());
    }

    @Test
    void downloadHandlerServesGeneratedContent() throws Exception {
        ResponseCtx response = new ResponseCtx();
        assertTrue(type.download().orElseThrow().handle(request, path("index.txt"), response));
        assertEquals(200, response.getStatus());
        assertEquals("a\nb\n", new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void downloadHandlerDeclines() {
        ResponseCtx response = new ResponseCtx();
        assertFalse(type.download().orElseThrow().handle(request, path("other.bin"), response));
        assertEquals(ResponseCtx.UNSET_STATUS, response.getStatus());
    }

    @Test
    void downloadHandlerCancelSendsItsStatus() {
        ResponseCtx response = new ResponseCtx();
        assertTrue(type.download().orElseThrow().handle(request, path("gone"), response));
        assertEquals(410, response.getStatus());
        assertEquals("gone for good", response.getMessage());
    }

    @Test
    void downloadHandlerFailureIsReportedAndDeclines() {
        assertFalse(type.download().orElseThrow().handle(request, path("broken"), new ResponseCtx()));
        assertEquals("simple", rig.reports.get(0).pluginName());
        assertEquals("packageType(simple-index).download", rig.reports.get(0).what());
    }

    @Test
    void uploadHandlerDecisions() {
        var upload = type.upload().orElseThrow();
        assertEquals(new UploadDecision.Reject(403, "no executables"), upload.decide(request, path("x/setup.exe")));
        UploadDecision.Rewrite rewrite = assertInstanceOf(UploadDecision.Rewrite.class,
                upload.decide(request, path("tmp/a/b.txt")));
        assertEquals(path("a/b.txt"), rewrite.target());
        assertEquals(new UploadDecision.Reject(409, "already there"), upload.decide(request, path("conflict")));
        assertInstanceOf(UploadDecision.Accept.class, upload.decide(request, path("a/b.txt")));
        assertTrue(rig.reports.isEmpty());
    }

    @Test
    void uploadHandlerReturningSomethingElseIsReportedAndFailsClosed() {
        UploadDecision.Reject reject = assertInstanceOf(UploadDecision.Reject.class,
                type.upload().orElseThrow().decide(request, path("weird")));
        assertEquals(500, reject.status());
        assertEquals(1, rig.reports.size());
    }

    @Test
    void uploadHandlerThatThrowsIsReportedAndFailsClosed() throws Exception {
        rig.write("throwing.groovy", "packageType('throwing') { upload { request, repoPath -> "
                + "throw new IllegalStateException('plugin bug') } }");
        rig.reload();
        PluginPackageType throwing = rig.registry.find("throwing").orElseThrow();
        UploadDecision.Reject reject = assertInstanceOf(UploadDecision.Reject.class,
                throwing.upload().orElseThrow().decide(request, path("a.tgz")));
        assertEquals(500, reject.status());
        assertTrue(rig.reports.stream().anyMatch(r -> r.pluginName().equals("throwing")), rig.reports.toString());
    }

    @Test
    void indexHandlerRunsWithTheStoredPath() {
        type.index().orElseThrow().onStored(path("pkg/1.0/pkg.tgz"));
        assertEquals(List.of("indexed:pkg/1.0/pkg.tgz"), rig.calls);
    }

    @Test
    void invalidTypeNameSurfacesInPluginStatus() throws Exception {
        rig.write("upper.groovy", "packageType('Bad_Name') { }");
        rig.reload();
        String message = rig.addon.reloadPlugins(true, false).getMessage();
        assertTrue(message.contains("upper.groovy: Error - Package types rejected"), message);
        assertTrue(message.contains("Bad_Name"), message);
        assertTrue(rig.addon.getPluginsInfoSupportBundleDumpAsMap().get("upper").get("error").contains("Bad_Name"));
    }

    @Test
    void rejectStatusFallsBackTo403() {
        assertEquals(404, PackageTypeDelegate.rejectStatus(404));
        assertEquals(403, PackageTypeDelegate.rejectStatus(200));
        assertEquals(403, PackageTypeDelegate.rejectStatus(600));
    }
}
