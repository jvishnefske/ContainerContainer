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
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.Repo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request-time lookup of a repository's plugin package type.
 *
 * <p>Reads the repository's live {@link RepoConfig} ({@code getPackageType()} and {@code getPackageTypeConfig()})
 * and the registry's current immutable snapshot (through {@link PluginPackageTypeRegistry#find}), then delegates
 * the decision to the pure {@link PluginTypeResolution#resolve}. The per-request cost is one lookup in the
 * repository's {@code packageTypeConfig} and, for plugin-typed repositories, one lookup in the snapshot.
 *
 * <p>Only local, non-cache repositories are routed ({@link #routableTypeOf}); remote and virtual repositories that
 * carry the key resolve to nothing and behave as Generic. A repository that names an unregistered type is reported
 * once at info level per repository and type name, then treated as Generic (requirement R6). The set of reported
 * pairs is bounded by the number of repositories times the type names they have used.
 */
@Component
public class PluginPackageTypeResolver {
    private static final Logger log = LoggerFactory.getLogger(PluginPackageTypeResolver.class);

    private final PluginPackageTypeRegistry registry;
    private final Set<String> reportedUnregistered = ConcurrentHashMap.newKeySet();

    @Autowired
    public PluginPackageTypeResolver(PluginPackageTypeRegistry registry) {
        this.registry = registry;
    }

    /** Resolves {@code config} against the current registry snapshot, reporting an unregistered type once. */
    public PluginTypeResolution resolve(RepoConfig config) {
        if (config == null) {
            return new PluginTypeResolution.NotPluginTyped();
        }
        PluginTypeResolution resolution =
                PluginTypeResolution.resolve(config.getPackageType(), config.getPackageTypeConfig(), registry::find);
        if (resolution instanceof PluginTypeResolution.Unregistered unregistered) {
            reportUnregisteredOnce(config.getKey(), unregistered.typeName());
        }
        return resolution;
    }

    /**
     * The registered plugin package type of {@code repo} when it is a local, non-cache repository; empty for every
     * other repository, for an unregistered type, and for a {@code null} repository.
     */
    public Optional<PluginPackageType> routableTypeOf(Repo<?, ?> repo) {
        if (repo == null || !repo.isLocal() || repo.isCache()) {
            return Optional.empty();
        }
        return resolve(repo.getRepoConfig()).registered();
    }

    /** Whether {@code typeName} is currently registered. */
    public boolean isRegistered(String typeName) {
        return registry.find(typeName).isPresent();
    }

    /** The registered type named {@code typeName}, if any. */
    public Optional<PluginPackageType> find(String typeName) {
        return registry.find(typeName);
    }

    private void reportUnregisteredOnce(String repoKey, String typeName) {
        if (reportedUnregistered.add(repoKey + '\u0000' + typeName)) {
            log.info("Repository '{}' uses plugin package type '{}', which no loaded plugin registers; " +
                    "serving it as a plain Generic repository", repoKey, typeName);
        }
    }

    /** Number of distinct repository/type pairs reported as unregistered so far. */
    int reportedUnregisteredCount() {
        return reportedUnregistered.size();
    }
}
