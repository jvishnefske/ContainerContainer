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
import org.artifactory.addon.plugin.PluginAction;

import java.util.Objects;

/**
 * One hook declared by a plugin, for example {@code download { altResponse { request, path -> } }}.
 *
 * <p>The closure is never invoked directly: {@link HookInvoker} clones it for each call, so the delegate
 * it sets cannot leak between concurrent requests.
 *
 * @param pluginName name of the declaring plugin, used in diagnostics
 * @param block      DSL block the hook was declared in ({@code download}, {@code storage}, {@code upload})
 * @param hookName   hook name inside the block
 * @param actionType the {@link PluginAction} interface the hook is bound to
 * @param closure    the hook body
 */
public record PluginHook(
        String pluginName,
        String block,
        String hookName,
        Class<? extends PluginAction> actionType,
        Closure<?> closure) {

    public PluginHook {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(hookName, "hookName");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(closure, "closure");
    }

    /** @return {@code block.hookName}, the form used in listings and diagnostics */
    public String qualifiedName() {
        return block + "." + hookName;
    }
}
