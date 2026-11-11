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

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link PluginPackageTypeRegistry} for engine tests. It applies the contract's rules (valid
 * names via {@link PluginPackageTypes#validateName}, names owned by one plugin) all or nothing, and records
 * removals so tests can assert on them.
 */
final class FakePackageTypeRegistry implements PluginPackageTypeRegistry {

    final Map<String, PluginPackageType> types = new LinkedHashMap<>();
    final List<String> removed = new ArrayList<>();

    @Override
    public List<String> replacePluginTypes(String pluginName, List<PluginPackageType> newTypes) {
        List<String> errors = new ArrayList<>();
        for (PluginPackageType type : newTypes) {
            PluginPackageTypes.validateName(type.name()).ifPresent(errors::add);
            PluginPackageType existing = types.get(type.name());
            if (existing != null && !existing.pluginName().equals(pluginName)) {
                errors.add("Type " + type.name() + " is owned by plugin " + existing.pluginName());
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        types.values().removeIf(type -> type.pluginName().equals(pluginName));
        newTypes.forEach(type -> types.put(type.name(), type));
        return List.of();
    }

    @Override
    public void removePluginTypes(String pluginName) {
        removed.add(pluginName);
        types.values().removeIf(type -> type.pluginName().equals(pluginName));
    }

    @Override
    public Optional<PluginPackageType> find(String name) {
        return Optional.ofNullable(types.get(name));
    }

    @Override
    public Collection<PluginPackageType> all() {
        return List.copyOf(types.values());
    }
}
