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
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Storage interceptor that schedules a plugin package type's index handler after a file is created in a local
 * repository of that type (requirement R5).
 *
 * <p>The decision ({@link #shouldIndex}) is pure; the shell only looks up the repository and hands the work to the
 * {@link PluginIndexDispatcher} async proxy. Nothing is scheduled for folders, for repositories that are not local
 * plugin-typed repositories, for types without an index handler, or while the current thread is already running an
 * index handler for the same repository ({@link PluginIndexReentryGuard}), which keeps the handler's own index
 * writes from triggering it again.
 *
 * <p>Copies and moves into a plugin-typed repository do not trigger the index handler; only created files do.
 */
@Component
public class PluginPackageTypeIndexInterceptor extends StorageInterceptorAdapter {

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
        if (fsItem == null || !fsItem.isFile()) {
            return;
        }
        String repoKey = fsItem.getRepoKey();
        Optional<PluginPackageType> type = resolver.routableTypeOf(repositoryService.repositoryByKey(repoKey));
        if (shouldIndex(type, guard.isIndexing(repoKey))) {
            dispatcher.dispatch(type.get().name(), fsItem.getRepoPath());
        }
    }

    /** Pure decision: index when the repository has a registered type with an index handler and we are not in it. */
    static boolean shouldIndex(Optional<PluginPackageType> type, boolean alreadyIndexingThisRepo) {
        return !alreadyIndexingThisRepo && type.flatMap(PluginPackageType::index).isPresent();
    }
}
