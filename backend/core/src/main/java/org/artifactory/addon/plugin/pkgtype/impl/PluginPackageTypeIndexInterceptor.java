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
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Storage interceptor that schedules a plugin package type's index handler whenever the set of files in a local
 * repository of that type changes (requirement R5).
 *
 * <p>The storage events covered, and the path handed to the handler for each:
 * <ul>
 *     <li>a file is created (deployed) in the repository: the stored path;</li>
 *     <li>a file is copied into the repository: the destination path;</li>
 *     <li>a file is moved: the destination path for the destination repository and the removed source path for
 *     the source repository (both when source and destination are the same repository);</li>
 *     <li>a file is deleted from the repository (including each file of a deleted folder): the removed path.
 *     The delete that a move performs on its source is not reported separately; the move covers it.</li>
 * </ul>
 * Folder events, items in the trash repository (filtered by the interceptor chain) and whole-repository content
 * deletion are not reported.
 *
 * <p>The decisions ({@link #affectedPaths} and {@link #shouldIndex}) are pure; the shell only looks up each
 * affected repository and hands the work to the {@link PluginIndexDispatcher} async proxy. Nothing is scheduled for
 * repositories that are not local plugin-typed repositories, for types without an index handler, or while the
 * current thread is already running an index handler for the same repository ({@link PluginIndexReentryGuard}),
 * which keeps the handler's own writes, moves and deletes in that repository from triggering it again.
 */
@Component
public class PluginPackageTypeIndexInterceptor extends StorageInterceptorAdapter {

    /** Kind of storage change seen by the interceptor. */
    enum Change { CREATE, COPY, MOVE, DELETE }

    private InternalRepositoryService repositoryService;
    private PluginPackageTypeResolver resolver;
    private PluginIndexDispatcher dispatcher;
    private PluginIndexReentryGuard guard;

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setResolver(PluginPackageTypeResolver resolver) {
        this.resolver = resolver;
    }

    @Autowired
    public void setDispatcher(PluginIndexDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Autowired
    public void setGuard(PluginIndexReentryGuard guard) {
        this.guard = guard;
    }

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx) {
        onChange(Change.CREATE, null, fsItem, false);
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        onChange(Change.COPY, sourceItem, targetItem, false);
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        onChange(Change.MOVE, sourceItem, targetItem, false);
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        onChange(Change.DELETE, fsItem, null, ctx != null && ctx.isTriggeredByMove());
    }

    private void onChange(Change change, VfsItem<? extends ItemInfo> source, VfsItem<? extends ItemInfo> target,
            boolean triggeredByMove) {
        boolean isFile = change == Change.DELETE ? isFile(source) : isFile(target);
        for (RepoPath path : affectedPaths(change, isFile, pathOf(source), pathOf(target), triggeredByMove)) {
            String repoKey = path.getRepoKey();
            Optional<PluginPackageType> type = resolver.routableTypeOf(repositoryService.repositoryByKey(repoKey));
            if (shouldIndex(type, guard.isIndexing(repoKey))) {
                dispatcher.dispatch(type.get().name(), path);
            }
        }
    }

    private static boolean isFile(VfsItem<? extends ItemInfo> item) {
        return item != null && item.isFile();
    }

    private static RepoPath pathOf(VfsItem<? extends ItemInfo> item) {
        return item == null ? null : item.getRepoPath();
    }

    /**
     * Pure decision: the paths whose repositories may need re-indexing after a storage change.
     *
     * @param change          the kind of change
     * @param isFile          whether the changed item is a file (the target for create, copy and move; the deleted
     *                        item for delete)
     * @param source          the copied, moved or deleted path; ignored for create
     * @param target          the created, copied-to or moved-to path; ignored for delete
     * @param triggeredByMove for delete, whether the delete removes the source of a move
     * @return the affected paths, removed source first, each to be checked against its own repository
     */
    static List<RepoPath> affectedPaths(Change change, boolean isFile, RepoPath source, RepoPath target,
            boolean triggeredByMove) {
        if (!isFile) {
            return List.of();
        }
        return switch (change) {
            case CREATE, COPY -> target == null ? List.of() : List.of(target);
            case MOVE -> {
                if (source == null || target == null) {
                    yield target == null ? List.of() : List.of(target);
                }
                yield List.of(source, target);
            }
            case DELETE -> triggeredByMove || source == null ? List.of() : List.of(source);
        };
    }

    /** Pure decision: index when the repository has a registered type with an index handler and we are not in it. */
    static boolean shouldIndex(Optional<PluginPackageType> type, boolean alreadyIndexingThisRepo) {
        return !alreadyIndexingThisRepo && type.flatMap(PluginPackageType::index).isPresent();
    }
}
