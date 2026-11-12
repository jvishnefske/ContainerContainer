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

import java.util.List;
import java.util.Objects;

/**
 * The immutable result of successfully evaluating one plugin script.
 *
 * @param name         plugin name, the file name without {@code .groovy}
 * @param fileName     file name inside the plugins directory
 * @param hooks        hooks in declaration order
 * @param executions   named executions in declaration order
 * @param packageTypes package types declared with {@code packageType(...)}, in declaration order
 * @param realms       authentication realms declared in {@code realms {}}, in declaration order
 */
public record LoadedPlugin(
        String name,
        String fileName,
        List<PluginHook> hooks,
        List<PluginExecution> executions,
        List<PluginPackageType> packageTypes,
        List<PluginRealm> realms) {

    public LoadedPlugin {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fileName, "fileName");
        hooks = List.copyOf(hooks);
        executions = List.copyOf(executions);
        packageTypes = List.copyOf(packageTypes);
        realms = List.copyOf(realms);
    }

    /** A plugin without realms. */
    public LoadedPlugin(String name, String fileName, List<PluginHook> hooks, List<PluginExecution> executions,
            List<PluginPackageType> packageTypes) {
        this(name, fileName, hooks, executions, packageTypes, List.of());
    }
}
