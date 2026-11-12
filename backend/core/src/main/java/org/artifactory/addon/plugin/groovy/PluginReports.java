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

import org.artifactory.addon.plugin.PluginInfo;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Pure renderings of a {@link PluginSnapshot} for the {@code PluginsAddon} status and listing methods.
 */
public final class PluginReports {

    /** Key of {@link #pluginInfo(PluginSnapshot, String)} listing executions, as in Artifactory Pro. */
    public static final String EXECUTIONS = "executions";

    private PluginReports() {
    }

    /** @return file name to {@code Loaded} or {@code Error}, in load order */
    public static Map<String, String> statuses(PluginSnapshot snapshot) {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (PluginLoadResult result : snapshot.results()) {
            statuses.put(result.fileName(), result instanceof PluginLoadResult.Loaded
                    ? PluginsAddon.PLUGIN_STATUS_LOADED
                    : PluginsAddon.PLUGIN_STATUS_ERROR);
        }
        return statuses;
    }

    /**
     * Describes every plugin: {@code fileName}, {@code status}, and for loaded plugins {@code hooks},
     * {@code executions}, {@code packageTypes} and {@code realms} (comma separated), or {@code error} for failed ones.
     *
     * @return plugin name to its description, in load order
     */
    public static Map<String, Map<String, String>> describe(PluginSnapshot snapshot) {
        Map<String, Map<String, String>> plugins = new LinkedHashMap<>();
        for (PluginLoadResult result : snapshot.results()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("fileName", result.fileName());
            switch (result) {
                case PluginLoadResult.Loaded loaded -> {
                    entry.put("status", PluginsAddon.PLUGIN_STATUS_LOADED);
                    entry.put("hooks", loaded.plugin().hooks().stream()
                            .map(PluginHook::qualifiedName).collect(Collectors.joining(",")));
                    entry.put("executions", loaded.plugin().executions().stream()
                            .map(PluginExecution::name).collect(Collectors.joining(",")));
                    entry.put("packageTypes", loaded.plugin().packageTypes().stream()
                            .map(PluginPackageType::name).collect(Collectors.joining(",")));
                    entry.put("realms", loaded.plugin().realms().stream()
                            .map(PluginRealm::name).collect(Collectors.joining(",")));
                }
                case PluginLoadResult.Failed failed -> {
                    entry.put("status", PluginsAddon.PLUGIN_STATUS_ERROR);
                    entry.put("error", failed.error());
                }
            }
            plugins.put(result.name(), entry);
        }
        return plugins;
    }

    /**
     * @param pluginType {@code null} for every kind, otherwise only that kind; this engine only has
     *                   {@code executions}, so other kinds map to an empty list
     * @return kind to the {@link PluginInfo} of each item of that kind
     */
    public static Map<String, List<PluginInfo>> pluginInfo(PluginSnapshot snapshot, String pluginType) {
        return pluginInfo(snapshot, pluginType, execution -> true);
    }

    /**
     * Like {@link #pluginInfo(PluginSnapshot, String)}, listing only the executions {@code visible} accepts,
     * so callers can hide executions the current user may not run.
     */
    public static Map<String, List<PluginInfo>> pluginInfo(PluginSnapshot snapshot, String pluginType,
            Predicate<PluginExecution> visible) {
        List<PluginInfo> executions = new ArrayList<>();
        for (PluginExecution execution : snapshot.executions()) {
            if (visible.test(execution)) {
                executions.add(execution.toPluginInfo());
            }
        }
        Map<String, List<PluginInfo>> info = new LinkedHashMap<>();
        if (pluginType == null || pluginType.isBlank() || EXECUTIONS.equals(pluginType)) {
            info.put(EXECUTIONS, executions);
        } else {
            info.put(pluginType, List.of());
        }
        return info;
    }

    /**
     * @return the plain-text reload summary: one line per plugin file with its status and, for failures,
     *         the reason; a note when no plugin files were found
     */
    public static String reloadMessage(PluginSnapshot snapshot) {
        if (snapshot.results().isEmpty()) {
            return "No plugins found in the plugins directory";
        }
        StringBuilder message = new StringBuilder();
        for (PluginLoadResult result : snapshot.results()) {
            switch (result) {
                case PluginLoadResult.Loaded loaded -> message.append(loaded.fileName()).append(": ")
                        .append(PluginsAddon.PLUGIN_STATUS_LOADED);
                case PluginLoadResult.Failed failed -> message.append(failed.fileName()).append(": ")
                        .append(PluginsAddon.PLUGIN_STATUS_ERROR).append(" - ").append(failed.error());
            }
            message.append('\n');
        }
        return message.toString();
    }
}
