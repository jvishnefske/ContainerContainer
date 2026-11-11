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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure helpers shared by the registry, request routing, validation and repository updates. */
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

    /**
     * Decides the {@code packageTypeConfig} an update of a repository stores, so that an update which does not
     * mention {@link #CONFIG_KEY} keeps the stored plugin package type.
     *
     * <p>Callers that rebuild a configuration from a partial model (the UI repository editor, for one, has no field
     * for the key) would otherwise drop the type on every edit. The rules, applied only when the updated repository
     * is Generic:
     * <ul>
     *     <li>the request has the key with a non-blank value: that value is stored (set or change);</li>
     *     <li>the request has the key with a {@code null} or blank value: the key is removed (explicit clear);</li>
     *     <li>the request lacks the key: the stored type name, if the stored repository has one, is carried over.</li>
     * </ul>
     *
     * @param newType   package type of the configuration about to be saved
     * @param requested its {@code packageTypeConfig}; not modified
     * @param oldType   package type of the stored configuration
     * @param stored    the stored {@code packageTypeConfig}, {@code null} when unknown
     * @return {@code requested} itself when nothing changes, otherwise a new map with the decided entry
     */
    public static Map<String, String> packageTypeConfigForUpdate(RepoType newType, Map<String, String> requested,
            RepoType oldType, Map<String, String> stored) {
        if (newType != RepoType.Generic || requested == null) {
            return requested;
        }
        if (requested.containsKey(CONFIG_KEY)) {
            String value = requested.get(CONFIG_KEY);
            if (value != null && !value.isBlank()) {
                return requested;
            }
            Map<String, String> cleared = new HashMap<>(requested);
            cleared.remove(CONFIG_KEY);
            return cleared;
        }
        Optional<String> previous = typeNameOf(oldType, stored);
        if (previous.isEmpty()) {
            return requested;
        }
        Map<String, String> kept = new HashMap<>(requested);
        kept.put(CONFIG_KEY, previous.get());
        return kept;
    }
}
