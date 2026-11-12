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

import java.nio.file.Path;
import java.util.List;

/**
 * Loads plugins of a kind other than Groovy scripts from the plugins directory, for example WASM modules with their
 * manifests. {@link GroovyPluginEngine#reload()} merges the results with those of the {@code .groovy} files before
 * checking name uniqueness and building the snapshot, so such plugins are listed, reported, reloaded and enforced
 * exactly like Groovy ones.
 */
@FunctionalInterface
public interface PluginLoader {

    /** Loads nothing; the engine with this loader handles Groovy plugins only. */
    PluginLoader NONE = dir -> List.of();

    /**
     * Loads every plugin of this loader's kind. Never throws: a file that cannot be loaded is a
     * {@link PluginLoadResult.Failed} with the reason.
     *
     * @param dir the plugins directory; may be {@code null} or missing, which loads nothing
     */
    List<PluginLoadResult> load(Path dir);
}
