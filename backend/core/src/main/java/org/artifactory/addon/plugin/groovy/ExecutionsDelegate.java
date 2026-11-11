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

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;

import java.util.Optional;

/**
 * Delegate of an {@code executions} block. Each call {@code name { params -> }} or
 * {@code name(users: [...], groups: [...], httpMethod: 'GET') { params, body -> }} declares a
 * {@link PluginExecution}. Declaring the same name twice in one plugin fails the load.
 */
public final class ExecutionsDelegate {

    private final PluginDefinitionBuilder definition;

    ExecutionsDelegate(PluginDefinitionBuilder definition) {
        this.definition = definition;
    }

    /** Groovy dispatch entry point for execution declarations. */
    public Object methodMissing(String name, Object args) {
        Optional<Closure<?>> body = HookBlockDelegate.closureArgument(args);
        if (body.isEmpty()) {
            throw new MissingMethodException(name, ExecutionsDelegate.class, HookBlockDelegate.asArray(args));
        }
        definition.addExecution(PluginExecution.fromOptions(definition.pluginName(), name,
                HookBlockDelegate.optionsArgument(args), body.get()));
        return null;
    }
}
