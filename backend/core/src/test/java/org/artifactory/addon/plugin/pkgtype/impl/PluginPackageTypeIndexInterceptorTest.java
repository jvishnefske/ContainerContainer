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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.localRepo;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.nonLocalRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** R5: the index handler runs once per stored file, asynchronously, and never for its own writes. */
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
}
