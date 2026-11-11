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

import java.util.Objects;

/**
 * The outcome of loading one plugin file: either a {@link Loaded} plugin or a {@link Failed} one with the
 * reason. A failure is data, never an exception, so one bad file cannot stop the others from loading.
 */
public sealed interface PluginLoadResult {

    /** @return the plugin name, the file name without {@code .groovy} */
    String name();

    /** @return the file name inside the plugins directory */
    String fileName();

    /** The script compiled, evaluated and registered without error. */
    record Loaded(LoadedPlugin plugin) implements PluginLoadResult {
        public Loaded {
            Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public String name() {
            return plugin.name();
        }

        @Override
        public String fileName() {
            return plugin.fileName();
        }
    }

    /**
     * The file could not be read, compiled, evaluated, or its package types were rejected.
     *
     * @param error human-readable reason, shown in plugin status
     */
    record Failed(String name, String fileName, String error) implements PluginLoadResult {
        public Failed {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(error, "error");
        }
    }
}
