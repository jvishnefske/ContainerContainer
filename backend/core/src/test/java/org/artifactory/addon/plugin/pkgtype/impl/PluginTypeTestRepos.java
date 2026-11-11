/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.Repo;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mock repositories and configurations carrying a plugin package type, shared by the package type tests. */
public final class PluginTypeTestRepos {

    private PluginTypeTestRepos() {
    }

    /** A config of {@code repoType} whose {@code packageTypeConfig} names {@code pluginType} (none when null). */
    public static RepoConfig config(String key, RepoType repoType, String pluginType) {
        return config(RepoConfig.class, key, repoType, pluginType);
    }

    private static <T extends RepoConfig> T config(Class<T> configClass, String key, RepoType repoType,
            String pluginType) {
        Map<String, String> packageTypeConfig = new HashMap<>();
        if (pluginType != null) {
            packageTypeConfig.put(PluginPackageTypes.CONFIG_KEY, pluginType);
        }
        T config = mock(configClass);
        when(config.getKey()).thenReturn(key);
        when(config.getPackageType()).thenReturn(repoType);
        when(config.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        return config;
    }

    /** A local, non-cache Generic repository of plugin package type {@code pluginType} that allows downloads. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LocalRepo localRepo(String key, String pluginType) {
        LocalRepo repo = mock(LocalRepo.class);
        LocalConfig config = config(LocalConfig.class, key, RepoType.Generic, pluginType);
        when(repo.getKey()).thenReturn(key);
        when(repo.isLocal()).thenReturn(true);
        when(repo.isCache()).thenReturn(false);
        when(repo.getRepoConfig()).thenReturn(config);
        when(repo.checkDownloadIsAllowed(any())).thenReturn(new BasicStatusHolder());
        return repo;
    }

    /** A non-local (remote or virtual) Generic repository whose config carries {@code pluginType}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Repo nonLocalRepo(String key, String pluginType) {
        Repo repo = mock(Repo.class);
        RepoConfig config = config(key, RepoType.Generic, pluginType);
        when(repo.getKey()).thenReturn(key);
        when(repo.isLocal()).thenReturn(false);
        when(repo.getRepoConfig()).thenReturn(config);
        return repo;
    }
}
