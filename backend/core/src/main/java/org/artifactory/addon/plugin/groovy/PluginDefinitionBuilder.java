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

import java.util.ArrayList;
import java.util.List;

/**
 * Collects what one plugin script declares while it is being evaluated, then produces the immutable
 * {@link LoadedPlugin}.
 *
 * <p>This is the only mutable object of the DSL. It lives for exactly one script evaluation, is confined
 * to the loading thread, and is sealed by {@link #build()}: a hook body that tries to declare more hooks at
 * request time gets an {@link IllegalStateException} instead of silently changing a loaded plugin.
 */
public final class PluginDefinitionBuilder {

    private final String pluginName;
    private final String fileName;
    private final HookFailureReporter reporter;
    private final List<PluginHook> hooks = new ArrayList<>();
    private final List<PluginExecution> executions = new ArrayList<>();
    private final List<PluginPackageType> packageTypes = new ArrayList<>();
    private boolean sealed;

    /**
     * @param reporter receives failures of package type handlers at request time
     */
    public PluginDefinitionBuilder(String pluginName, String fileName, HookFailureReporter reporter) {
        this.pluginName = pluginName;
        this.fileName = fileName;
        this.reporter = reporter;
    }

    /** @return the name of the plugin being defined */
    public String pluginName() {
        return pluginName;
    }

    /** @return the reporter package type handlers use for swallowed failures */
    public HookFailureReporter reporter() {
        return reporter;
    }

    void addHook(PluginHook hook) {
        checkOpen();
        hooks.add(hook);
    }

    void addExecution(PluginExecution execution) {
        checkOpen();
        for (PluginExecution existing : executions) {
            if (existing.name().equals(execution.name())) {
                throw new IllegalArgumentException("Execution '" + execution.name() + "' is declared twice");
            }
        }
        executions.add(execution);
    }

    void addPackageType(PluginPackageType type) {
        checkOpen();
        packageTypes.add(type);
    }

    /** Seals the builder and returns what the script declared. */
    public LoadedPlugin build() {
        sealed = true;
        return new LoadedPlugin(pluginName, fileName, hooks, executions, packageTypes);
    }

    private void checkOpen() {
        if (sealed) {
            throw new IllegalStateException("Plugin '" + pluginName + "' is already loaded; "
                    + "DSL blocks can only be declared at the top level of the script");
        }
    }
}
