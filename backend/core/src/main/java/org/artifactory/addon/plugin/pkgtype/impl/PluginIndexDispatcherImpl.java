/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginIndexHandler;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.repo.RepoPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Executes a plugin index handler (the body behind the {@link PluginIndexDispatcher} async proxy).
 *
 * <p>The type is looked up again when the work runs, so a plugin reloaded or removed between the store and the
 * index run is honoured. The handler runs inside {@link PluginIndexReentryGuard#runIndexing}, so its own writes to
 * the same repository do not schedule it again. A handler exception is logged with the type and plugin name and
 * does not propagate.
 */
@Service
public class PluginIndexDispatcherImpl implements PluginIndexDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PluginIndexDispatcherImpl.class);

    private final PluginPackageTypeRegistry registry;
    private final PluginIndexReentryGuard guard;

    @Autowired
    public PluginIndexDispatcherImpl(PluginPackageTypeRegistry registry, PluginIndexReentryGuard guard) {
        this.registry = registry;
        this.guard = guard;
    }

    @Override
    public void dispatch(String typeName, RepoPath stored) {
        Optional<PluginPackageType> type = registry.find(typeName);
        Optional<PluginIndexHandler> handler = type.flatMap(PluginPackageType::index);
        if (handler.isEmpty()) {
            log.debug("Plugin package type '{}' has no index handler any more; skipping {}", typeName, stored);
            return;
        }
        guard.runIndexing(stored.getRepoKey(), () -> {
            try {
                handler.get().onStored(stored);
            } catch (RuntimeException e) {
                log.error("Index handler of plugin package type '{}' (plugin '{}') failed for {}: {}",
                        typeName, type.get().pluginName(), stored, e.getMessage(), e);
            }
        });
    }
}
