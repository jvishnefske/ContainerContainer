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

import org.artifactory.addon.plugin.PluginAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One immutable generation of plugins: the load result of every plugin file, plus indexes derived from the
 * loaded ones. The engine replaces the whole snapshot on reload, so a reader holding a snapshot always sees
 * hooks and executions from a single generation.
 *
 * <p>Results are kept in load order (plugin file name order); hooks of one action type fire in that order
 * and, within a plugin, in declaration order.
 */
public final class PluginSnapshot {

    private static final PluginSnapshot EMPTY = new PluginSnapshot(List.of());

    private final List<PluginLoadResult> results;
    private final List<LoadedPlugin> loaded;
    private final Map<Class<? extends PluginAction>, List<PluginHook>> hooksByType;
    private final Map<String, PluginExecution> executionsByName;
    private final List<PluginRealm> realms;

    /**
     * @param results load results in load order; when two loaded plugins declare the same execution or realm
     *                name the earlier one wins (the engine rejects such duplicates before building a snapshot)
     */
    public PluginSnapshot(List<PluginLoadResult> results) {
        this.results = List.copyOf(results);
        List<LoadedPlugin> plugins = new ArrayList<>();
        Map<Class<? extends PluginAction>, List<PluginHook>> hooks = new HashMap<>();
        Map<String, PluginExecution> executions = new LinkedHashMap<>();
        Map<String, PluginRealm> realmsByName = new LinkedHashMap<>();
        for (PluginLoadResult result : this.results) {
            if (result instanceof PluginLoadResult.Loaded loadedResult) {
                LoadedPlugin plugin = loadedResult.plugin();
                plugins.add(plugin);
                for (PluginHook hook : plugin.hooks()) {
                    hooks.computeIfAbsent(hook.actionType(), type -> new ArrayList<>()).add(hook);
                }
                for (PluginExecution execution : plugin.executions()) {
                    executions.putIfAbsent(execution.name(), execution);
                }
                for (PluginRealm realm : plugin.realms()) {
                    realmsByName.putIfAbsent(realm.name(), realm);
                }
            }
        }
        this.loaded = List.copyOf(plugins);
        Map<Class<? extends PluginAction>, List<PluginHook>> frozen = new HashMap<>();
        hooks.forEach((type, list) -> frozen.put(type, List.copyOf(list)));
        this.hooksByType = Map.copyOf(frozen);
        this.executionsByName = Collections.unmodifiableMap(executions);
        this.realms = List.copyOf(realmsByName.values());
    }

    /** @return the snapshot with no plugins, the state before the first load */
    public static PluginSnapshot empty() {
        return EMPTY;
    }

    /** @return every load result, loaded and failed, in load order */
    public List<PluginLoadResult> results() {
        return results;
    }

    /** @return the successfully loaded plugins in load order */
    public List<LoadedPlugin> loaded() {
        return loaded;
    }

    /** @return the hooks bound to {@code type}, in firing order; empty when none */
    public List<PluginHook> hooksFor(Class<? extends PluginAction> type) {
        return hooksByType.getOrDefault(type, List.of());
    }

    /** @return the execution named {@code name}, if a loaded plugin declares it */
    public Optional<PluginExecution> execution(String name) {
        return Optional.ofNullable(executionsByName.get(name));
    }

    /**
     * @return the realms of loaded plugins in load order (plugin file name, then declaration order); when two
     *         plugins declare the same realm name the earlier one wins (the engine rejects such duplicates)
     */
    public List<PluginRealm> realms() {
        return realms;
    }

    /** @return all executions of loaded plugins, in load order */
    public List<PluginExecution> executions() {
        return List.copyOf(executionsByName.values());
    }
}
