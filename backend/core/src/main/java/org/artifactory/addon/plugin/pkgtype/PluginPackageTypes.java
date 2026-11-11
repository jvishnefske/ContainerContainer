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

package org.artifactory.addon.plugin.pkgtype;

import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure helpers shared by the registry, request routing and validation. */
public final class PluginPackageTypes {

    /** Reserved {@code packageTypeConfig} key naming a repository's plugin package type. */
    public static final String CONFIG_KEY = "pluginPackageType";

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private PluginPackageTypes() {
    }

    /**
     * Checks a proposed type name.
     *
     * @return empty when valid, otherwise the reason it is rejected
     */
    public static Optional<String> validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            return Optional.of("Plugin package type name must match " + NAME.pattern() + ": " + name);
        }
        for (RepoType builtIn : RepoType.values()) {
            if (builtIn.getType().equalsIgnoreCase(name) || builtIn.name().equalsIgnoreCase(name)) {
                return Optional.of("Plugin package type name collides with built-in type " + builtIn.getType());
            }
        }
        return Optional.empty();
    }

    /**
     * Reads the plugin package type name from a repository's package type and
     * {@code packageTypeConfig}. Only Generic repositories can carry one.
     */
    public static Optional<String> typeNameOf(RepoType repoType, Map<String, String> packageTypeConfig) {
        if (repoType != RepoType.Generic || packageTypeConfig == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(packageTypeConfig.get(CONFIG_KEY)).filter(s -> !s.isBlank());
    }
}
