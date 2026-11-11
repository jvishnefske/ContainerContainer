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
import org.artifactory.addon.plugin.PluginAction;

import java.util.Map;
import java.util.Optional;

/**
 * Delegate of a {@code download}, {@code storage} or {@code upload} block. Each call
 * {@code hookName { ... }} or {@code hookName(options) { ... }} inside the block registers a
 * {@link PluginHook} bound to the action type {@link HookCatalog} gives for that name; options are
 * accepted for compatibility and ignored.
 *
 * <p>A name the catalog does not know raises {@link MissingMethodException} so Groovy can still resolve it
 * against the script (for helper methods called from hook bodies); a name unknown to both fails the load.
 */
public final class HookBlockDelegate {

    private final String block;
    private final PluginDefinitionBuilder definition;

    HookBlockDelegate(String block, PluginDefinitionBuilder definition) {
        this.block = block;
        this.definition = definition;
    }

    /** Groovy dispatch entry point for hook declarations. */
    public Object methodMissing(String name, Object args) {
        Optional<Class<? extends PluginAction>> type = HookCatalog.actionType(block, name);
        Optional<Closure<?>> body = closureArgument(args);
        if (type.isEmpty() || body.isEmpty()) {
            throw new MissingMethodException(name, HookBlockDelegate.class, asArray(args));
        }
        definition.addHook(new PluginHook(definition.pluginName(), block, name, type.get(), body.get()));
        return null;
    }

    /** Accepts {@code (Closure)} and {@code (Map, Closure)}. */
    static Optional<Closure<?>> closureArgument(Object args) {
        Object[] array = asArray(args);
        if (array.length == 1 && array[0] instanceof Closure<?> closure) {
            return Optional.of(closure);
        }
        if (array.length == 2 && array[0] instanceof Map<?, ?> && array[1] instanceof Closure<?> closure) {
            return Optional.of(closure);
        }
        return Optional.empty();
    }

    /** @return the options map of a {@code (Map, Closure)} call, empty otherwise */
    static Map<?, ?> optionsArgument(Object args) {
        Object[] array = asArray(args);
        if (array.length == 2 && array[0] instanceof Map<?, ?> options) {
            return options;
        }
        return Map.of();
    }

    static Object[] asArray(Object args) {
        return args instanceof Object[] array ? array : new Object[]{args};
    }
}
