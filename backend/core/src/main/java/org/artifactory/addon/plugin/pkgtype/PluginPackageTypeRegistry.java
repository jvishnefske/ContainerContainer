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

package org.artifactory.addon.plugin.pkgtype;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The set of plugin package types currently registered.
 *
 * <p>The implementation is a Spring singleton shared by the plugin engine
 * (writer) and the request path (readers). It holds an immutable snapshot
 * that is swapped atomically, so readers never see a half-applied reload.
 * That single shared handle is unavoidable: Spring owns both sides.
 */
public interface PluginPackageTypeRegistry {

    /**
     * Replaces every type registered by {@code pluginName} with {@code types}.
     * Applied all or nothing.
     *
     * @return empty on success, otherwise the reasons the set was rejected
     *         (invalid name, or a name owned by a different plugin)
     */
    List<String> replacePluginTypes(String pluginName, List<PluginPackageType> types);

    /** Removes every type registered by {@code pluginName}. */
    void removePluginTypes(String pluginName);

    Optional<PluginPackageType> find(String name);

    Collection<PluginPackageType> all();
}
