/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.async.Async;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeIndexInterceptor.Change.COPY;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeIndexInterceptor.Change.CREATE;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeIndexInterceptor.Change.DELETE;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeIndexInterceptor.Change.MOVE;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeIndexInterceptor.affectedPaths;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.localRepo;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.nonLocalRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R5: the index handler runs once per created, copied-in, moved or deleted file, asynchronously, and never for its
 * own changes.
 */
class PluginPackageTypeIndexInterceptorTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private final PluginIndexReentryGuard guard = new PluginIndexReentryGuard();
    private final InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final PluginPackageTypeIndexInterceptor interceptor = new PluginPackageTypeIndexInterceptor();
    private final List<RepoPath> indexed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        PluginIndexDispatcherImpl body = new PluginIndexDispatcherImpl(registry, guard);
        // Stands in for the @Async proxy: the call only queues work, which the test runs later on "another thread".
        PluginIndexDispatcher asyncProxy = (typeName, stored) -> queued.add(() -> body.dispatch(typeName, stored));
        interceptor.setRepositoryService(repositoryService);
        interceptor.setResolver(new PluginPackageTypeResolver(registry));
        interceptor.setDispatcher(asyncProxy);
        interceptor.setGuard(guard);
        LocalRepo<?, ?> repo = localRepo("idx", "indexed");
        when(repositoryService.repositoryByKey("idx")).thenReturn(repo);
        LocalRepo<?, ?> plain = localRepo("plain", null);
        when(repositoryService.repositoryByKey("plain")).thenReturn(plain);
    }

    private void registerIndexingType() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p")
                .index(indexed::add).build()));
    }

    private void copy(RepoPath from, RepoPath to) {
        interceptor.afterCopy(file(from, true), file(to, true), mock(MutableStatusHolder.class), null,
                new InterceptorMoveCopyContext());
    }

    private void move(RepoPath from, RepoPath to) {
        interceptor.afterMove(file(from, true), file(to, true), mock(MutableStatusHolder.class), null,
                new InterceptorMoveCopyContext());
    }

    private void delete(RepoPath path, DeleteContext ctx) {
        interceptor.afterDelete(file(path, true), mock(MutableStatusHolder.class), ctx);
    }

    @SuppressWarnings("unchecked")
    private static VfsItem<ItemInfo> file(RepoPath path, boolean isFile) {
        VfsItem<ItemInfo> item = mock(VfsItem.class);
        when(item.isFile()).thenReturn(isFile);
        when(item.getRepoKey()).thenReturn(path.getRepoKey());
        when(item.getRepoPath()).thenReturn(path);
        return item;
    }

    private void store(RepoPath path) {
        interceptor.afterCreate(file(path, true), mock(MutableStatusHolder.class), null);
    }

    private void runQueued() {
        List<Runnable> work = new ArrayList<>(queued);
        queued.clear();
        work.forEach(Runnable::run);
    }

    @Test
    void indexRunsOnceAndNotForItsOwnWrites() {
        RepoPath indexFile = RepoPathFactory.create("idx", "index.json");
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p").index(stored -> {
            indexed.add(stored);
            // The handler writes its index into the same repository; storage fires afterCreate synchronously.
            store(indexFile);
        }).build()));
        RepoPath artifact = RepoPathFactory.create("idx", "pkg/a-1.0.tgz");

        store(artifact);
        assertTrue(indexed.isEmpty(), "handler must not run on the storing thread");
        assertEquals(1, queued.size());

        runQueued();
        assertEquals(List.of(artifact), indexed);
        assertTrue(queued.isEmpty(), "the handler's own index write must not schedule it again");
        assertFalse(guard.isIndexing("idx"), "guard must be cleared after the handler");
    }

    @Test
    void handlerExceptionIsContainedAndGuardCleared() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p").index(stored -> {
            throw new IllegalStateException("boom");
        }).build()));
        store(RepoPathFactory.create("idx", "a"));
        runQueued();
        assertFalse(guard.isIndexing("idx"));
    }

    @Test
    void noDispatchForFoldersUnregisteredOrNoIndexHandler() {
        interceptor.afterCreate(file(RepoPathFactory.create("idx", "dir"), false), mock(MutableStatusHolder.class),
                null);
        store(RepoPathFactory.create("idx", "a"));
        assertTrue(queued.isEmpty(), "type not registered: plain Generic");
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p").build()));
        store(RepoPathFactory.create("idx", "a"));
        assertTrue(queued.isEmpty(), "type has no index handler");
    }

    @Test
    void remoteRepoWithTheKeyIsIgnored() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p")
                .index(indexed::add).build()));
        var remote = nonLocalRepo("rem", "indexed");
        when(repositoryService.repositoryByKey("rem")).thenReturn(remote);
        store(RepoPathFactory.create("rem", "a"));
        assertTrue(queued.isEmpty());
    }

    @Test
    void typeRemovedBeforeAsyncRunSkipsHandler() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p")
                .index(indexed::add).build()));
        store(RepoPathFactory.create("idx", "a"));
        registry.removePluginTypes("p");
        runQueued();
        assertTrue(indexed.isEmpty());
    }

    @Test
    void shouldIndexDecision() {
        PluginPackageType withIndex = PluginPackageType.builder("t", "p").index(p -> { }).build();
        assertTrue(PluginPackageTypeIndexInterceptor.shouldIndex(Optional.of(withIndex), false));
        assertFalse(PluginPackageTypeIndexInterceptor.shouldIndex(Optional.of(withIndex), true));
        assertFalse(PluginPackageTypeIndexInterceptor.shouldIndex(Optional.empty(), false));
    }

    @Test
    void dispatchIsAsyncAfterCommit() throws NoSuchMethodException {
        Async async = PluginIndexDispatcher.class.getMethod("dispatch", String.class, RepoPath.class)
                .getAnnotation(Async.class);
        assertTrue(async != null && async.delayUntilAfterCommit());
    }

    @Test
    void copyIntoPluginRepoIndexesDestination() {
        registerIndexingType();
        RepoPath target = RepoPathFactory.create("idx", "pkg/a-1.0.tgz");
        copy(RepoPathFactory.create("plain", "a-1.0.tgz"), target);
        runQueued();
        assertEquals(List.of(target), indexed);
    }

    @Test
    void copyOutOfPluginRepoDoesNotIndexSource() {
        registerIndexingType();
        copy(RepoPathFactory.create("idx", "a"), RepoPathFactory.create("plain", "a"));
        assertTrue(queued.isEmpty(), "the source repository did not change");
    }

    @Test
    void moveIntoPluginRepoIndexesDestination() {
        registerIndexingType();
        RepoPath target = RepoPathFactory.create("idx", "a");
        move(RepoPathFactory.create("plain", "a"), target);
        runQueued();
        assertEquals(List.of(target), indexed);
    }

    @Test
    void moveOutOfPluginRepoIndexesSourceWithRemovedPath() {
        registerIndexingType();
        RepoPath removed = RepoPathFactory.create("idx", "pkg/a-1.0.tgz");
        move(removed, RepoPathFactory.create("plain", "a-1.0.tgz"));
        runQueued();
        assertEquals(List.of(removed), indexed);
    }

    @Test
    void moveWithinPluginRepoReportsRemovedAndNewPath() {
        registerIndexingType();
        RepoPath from = RepoPathFactory.create("idx", "old/a");
        RepoPath to = RepoPathFactory.create("idx", "new/a");
        move(from, to);
        runQueued();
        assertEquals(List.of(from, to), indexed);
    }

    @Test
    void deleteFromPluginRepoIndexesRemovedPath() {
        registerIndexingType();
        RepoPath removed = RepoPathFactory.create("idx", "pkg/a-1.0.tgz");
        delete(removed, new DeleteContext(removed));
        runQueued();
        assertEquals(List.of(removed), indexed);
    }

    @Test
    void deleteThatIsPartOfAMoveIsLeftToTheMove() {
        registerIndexingType();
        RepoPath removed = RepoPathFactory.create("idx", "a");
        delete(removed, new DeleteContext(removed).triggeredByMove());
        assertTrue(queued.isEmpty());
    }

    @Test
    void folderCopyMoveDeleteAreIgnored() {
        registerIndexingType();
        RepoPath dir = RepoPathFactory.create("idx", "dir");
        RepoPath plainDir = RepoPathFactory.create("plain", "dir");
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        interceptor.afterCopy(file(plainDir, false), file(dir, false), status, null, new InterceptorMoveCopyContext());
        interceptor.afterMove(file(dir, false), file(plainDir, false), status, null, new InterceptorMoveCopyContext());
        interceptor.afterDelete(file(dir, false), status, new DeleteContext(dir));
        assertTrue(queued.isEmpty());
    }

    @Test
    void handlersOwnMovesAndDeletesDoNotRetrigger() {
        RepoPath oldIndex = RepoPathFactory.create("idx", "index.json");
        RepoPath tmp = RepoPathFactory.create("idx", "index.json.tmp");
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("indexed", "p").index(stored -> {
            indexed.add(stored);
            // The handler rebuilds: it deletes the old index, then moves a freshly written one into place.
            delete(oldIndex, new DeleteContext(oldIndex));
            move(tmp, oldIndex);
        }).build()));
        RepoPath removed = RepoPathFactory.create("idx", "pkg/a");
        delete(removed, new DeleteContext(removed));
        runQueued();
        assertEquals(List.of(removed), indexed);
        assertTrue(queued.isEmpty(), "the handler's own delete and move must not schedule it again");
    }

    @Test
    void affectedPathsDecision() {
        RepoPath src = RepoPathFactory.create("a", "x");
        RepoPath dst = RepoPathFactory.create("b", "x");
        assertEquals(List.of(dst), affectedPaths(CREATE, true, null, dst, false));
        assertEquals(List.of(dst), affectedPaths(COPY, true, src, dst, false));
        assertEquals(List.of(src, dst), affectedPaths(MOVE, true, src, dst, false));
        assertEquals(List.of(src), affectedPaths(DELETE, true, src, null, false));
        assertEquals(List.of(), affectedPaths(DELETE, true, src, null, true));
        for (PluginPackageTypeIndexInterceptor.Change change : PluginPackageTypeIndexInterceptor.Change.values()) {
            assertEquals(List.of(), affectedPaths(change, false, src, dst, false), "folders: " + change);
        }
    }
}
