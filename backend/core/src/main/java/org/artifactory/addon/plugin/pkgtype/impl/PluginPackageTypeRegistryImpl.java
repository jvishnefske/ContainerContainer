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
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Spring singleton {@link PluginPackageTypeRegistry}.
 *
 * <p>State is one immutable {@code Map<name, type>} held in an {@link AtomicReference}. Readers ({@link #find},
 * {@link #all}, {@link #snapshot}) take the current map with a single volatile read and never lock. Writers are
 * serialized with {@code synchronized} so that each write computes its next map from the latest one; the computation
 * itself is the pure function {@link #plan(Map, String, List)}, which either rejects the whole batch or returns the
 * complete next map. A rejected batch leaves the registry unchanged (all or nothing).
 *
 * <p>Rules enforced by {@link #plan}: the plugin name is not blank; every type is non-null, belongs to the calling
 * plugin ({@link PluginPackageType#pluginName()} equals {@code pluginName}) and has a name accepted by
 * {@link PluginPackageTypes#validateName(String)}; no name repeats within the batch; and no name is already owned by
 * a different plugin.
 */
@Component
public class PluginPackageTypeRegistryImpl implements PluginPackageTypeRegistry {

    private final AtomicReference<Map<String, PluginPackageType>> current = new AtomicReference<>(Map.of());

    @Override
    public synchronized List<String> replacePluginTypes(String pluginName, List<PluginPackageType> types) {
        Plan plan = plan(current.get(), pluginName, types);
        if (plan instanceof Plan.Apply apply) {
            current.set(apply.next());
            return List.of();
        }
        return ((Plan.Reject) plan).reasons();
    }

    @Override
    public synchronized void removePluginTypes(String pluginName) {
        current.set(without(current.get(), pluginName));
    }

    @Override
    public Optional<PluginPackageType> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(current.get().get(name));
    }

    @Override
    public Collection<PluginPackageType> all() {
        return List.copyOf(current.get().values());
    }

    /** The current immutable name-to-type map; later registry changes do not affect the returned map. */
    public Map<String, PluginPackageType> snapshot() {
        return current.get();
    }

    /** Outcome of {@link #plan}: either the complete next map, or the reasons the batch is refused. */
    sealed interface Plan {
        record Apply(Map<String, PluginPackageType> next) implements Plan {
        }

        record Reject(List<String> reasons) implements Plan {
        }
    }

    /**
     * Computes the registry content after replacing {@code pluginName}'s types with {@code types}. Pure: neither
     * argument is modified.
     */
    static Plan plan(Map<String, PluginPackageType> before, String pluginName, List<PluginPackageType> types) {
        List<String> reasons = new ArrayList<>();
        if (pluginName == null || pluginName.isBlank()) {
            return new Plan.Reject(List.of("Plugin name must not be blank"));
        }
        List<PluginPackageType> batch = types == null ? List.of() : types;
        Set<String> seen = new HashSet<>();
        for (PluginPackageType type : batch) {
            if (type == null) {
                reasons.add("Plugin " + pluginName + " registered a null package type");
                continue;
            }
            PluginPackageTypes.validateName(type.name()).ifPresent(reasons::add);
            if (!pluginName.equals(type.pluginName())) {
                reasons.add("Package type " + type.name() + " declares plugin " + type.pluginName() +
                        " but was registered by plugin " + pluginName);
            }
            if (!seen.add(type.name())) {
                reasons.add("Package type " + type.name() + " is declared more than once by plugin " + pluginName);
            }
            PluginPackageType existing = before.get(type.name());
            if (existing != null && !existing.pluginName().equals(pluginName)) {
                reasons.add("Package type " + type.name() + " is already registered by plugin " +
                        existing.pluginName());
            }
        }
        if (!reasons.isEmpty()) {
            return new Plan.Reject(List.copyOf(reasons));
        }
        Map<String, PluginPackageType> next = new LinkedHashMap<>(without(before, pluginName));
        batch.forEach(type -> next.put(type.name(), type));
        return new Plan.Apply(Map.copyOf(next));
    }

    private static Map<String, PluginPackageType> without(Map<String, PluginPackageType> before, String pluginName) {
        Map<String, PluginPackageType> next = new LinkedHashMap<>();
        before.forEach((name, type) -> {
            if (!Objects.equals(type.pluginName(), pluginName)) {
                next.put(name, type);
            }
        });
        return Map.copyOf(next);
    }
}
