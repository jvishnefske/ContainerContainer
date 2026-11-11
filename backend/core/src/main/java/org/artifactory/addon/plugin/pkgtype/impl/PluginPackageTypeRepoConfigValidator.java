/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.apache.http.HttpStatus;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Rejects repository creates and updates that name an unregistered plugin package type (requirement R2).
 *
 * <p>Runs with the other {@link RepoConfigChangeValidator}s before {@code RepositoryConfigServiceImpl} saves a
 * change, so it covers every create/update entry point that goes through that service (REST repository API, UI,
 * bulk). A failure is a {@link ConfigurationException} with status 400, which the REST layer maps to a 400 response
 * whose message names the type.
 *
 * <p>Imports are skipped, and an update that keeps the type name the stored configuration already has is allowed
 * even when that type is not registered, so that a repository whose plugin was removed keeps working and stays
 * editable as plain Generic (requirement R6). Changing to, or creating with, an unregistered name is rejected.
 */
@Component
public class PluginPackageTypeRepoConfigValidator implements RepoConfigChangeValidator {

    private final PluginPackageTypeRegistry registry;

    @Autowired
    public PluginPackageTypeRepoConfigValidator(PluginPackageTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        if (repoConfigChanges.isImport()) {
            return;
        }
        for (RepoConfigChange change : repoConfigChanges.getAll()) {
            if (change.isCreateOrUpdate()) {
                check(change.getNewRepoConfig(), change.getOldRepoConfig(), name -> registry.find(name).isPresent())
                        .ifPresent(message -> {
                            throw new ConfigurationException(message, HttpStatus.SC_BAD_REQUEST);
                        });
            }
        }
    }

    /**
     * Pure check of one create or update.
     *
     * @param newConfig    the configuration about to be saved
     * @param oldConfig    the stored configuration, {@code null} on create
     * @param isRegistered whether a type name is currently registered
     * @return empty when acceptable, otherwise the error message naming the unknown type
     */
    public static Optional<String> check(RepoConfig newConfig, RepoConfig oldConfig, Predicate<String> isRegistered) {
        if (newConfig == null) {
            return Optional.empty();
        }
        Optional<String> name = PluginPackageTypes.typeNameOf(newConfig.getPackageType(),
                newConfig.getPackageTypeConfig());
        if (name.isEmpty() || isRegistered.test(name.get())) {
            return Optional.empty();
        }
        Optional<String> previous = oldConfig == null ? Optional.empty()
                : PluginPackageTypes.typeNameOf(oldConfig.getPackageType(), oldConfig.getPackageTypeConfig());
        if (previous.equals(name)) {
            return Optional.empty();
        }
        return Optional.of("Unknown plugin package type '" + name.get() + "' for repository '" +
                newConfig.getKey() + "': no loaded plugin registers it");
    }
}
