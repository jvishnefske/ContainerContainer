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
import org.artifactory.addon.plugin.PluginInfo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A named execution declared in a plugin's {@code executions} block and run through
 * {@code POST /api/plugins/execute/{name}}.
 *
 * <p>When {@code users} and {@code groups} are both empty only administrators may run it; see
 * {@link ExecutionAuthorizer}. The body is an {@link ExecutionHook}: a closure for executions of {@code .groovy}
 * plugins, a hosted call for executions a WASM plugin's manifest declares; the rules are the same for both.
 *
 * @param pluginName  name of the declaring plugin
 * @param name        execution name
 * @param users       user names allowed to run it besides administrators
 * @param groups      group names whose members may run it
 * @param httpMethod  the only HTTP method the execution answers to ({@code POST} by default); other methods get 405
 * @param version     declared version, informational
 * @param description declared description, empty when not given
 * @param hook        the execution body
 */
public record PluginExecution(
        String pluginName,
        String name,
        Set<String> users,
        Set<String> groups,
        String httpMethod,
        String version,
        String description,
        ExecutionHook hook) {

    public PluginExecution {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(name, "name");
        users = Set.copyOf(users);
        groups = Set.copyOf(groups);
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(hook, "hook");
    }

    /**
     * Builds an execution from the options map of the DSL, for example
     * {@code myExec(users: ['bob'], groups: 'deployers', httpMethod: 'GET') { params -> }}.
     * Unknown options are ignored; a single string is accepted where a list is expected.
     */
    public static PluginExecution fromOptions(String pluginName, String name, Map<?, ?> options, Closure<?> closure) {
        Map<?, ?> opts = options == null ? Map.of() : options;
        return new PluginExecution(pluginName, name,
                stringSet(opts.get("users")),
                stringSet(opts.get("groups")),
                stringOr(opts.get("httpMethod"), "POST"),
                stringOr(opts.get("version"), "undefined"),
                stringOr(opts.get("description"), ""),
                new ExecutionHook.Script(closure));
    }

    /** @return the papi {@link PluginInfo} view used by {@code GET /api/plugins} */
    public PluginInfo toPluginInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("users", List.copyOf(users));
        info.put("groups", List.copyOf(groups));
        info.put("httpMethod", httpMethod);
        info.put("version", version);
        if (!description.isEmpty()) {
            info.put("description", description);
        }
        return new PluginInfo(name, info);
    }

    private static Set<String> stringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (value instanceof Iterable<?> items) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : items) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return Set.of(String.valueOf(value));
    }

    private static String stringOr(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
