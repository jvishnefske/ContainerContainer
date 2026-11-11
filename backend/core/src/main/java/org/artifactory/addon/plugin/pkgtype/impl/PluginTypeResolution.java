/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a repository's configuration says about plugin package types, resolved against a registry snapshot.
 *
 * <p>Three closed cases: the repository is not plugin-typed; it names a type that is not registered (the plugin was
 * removed or has not loaded, so the repository behaves as plain Generic); or it names a registered type.
 */
public sealed interface PluginTypeResolution {

    /** The repository carries no plugin package type, or is not Generic. */
    record NotPluginTyped() implements PluginTypeResolution {
    }

    /** The repository names {@code typeName}, which no loaded plugin registers. */
    record Unregistered(String typeName) implements PluginTypeResolution {
        public Unregistered {
            Objects.requireNonNull(typeName, "typeName");
        }
    }

    /** The repository names a registered type. */
    record Registered(PluginPackageType type) implements PluginTypeResolution {
        public Registered {
            Objects.requireNonNull(type, "type");
        }
    }

    /** The registered type, if this resolution is {@link Registered}. */
    default Optional<PluginPackageType> registered() {
        return this instanceof Registered r ? Optional.of(r.type()) : Optional.empty();
    }

    /**
     * Resolves a repository's package type and {@code packageTypeConfig} against {@code lookup} (typically
     * {@link org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry#find}). Pure as long as {@code lookup}
     * is; costs one lookup in {@code packageTypeConfig} and at most one call to {@code lookup}.
     */
    static PluginTypeResolution resolve(RepoType repoType, Map<String, String> packageTypeConfig,
            Function<String, Optional<PluginPackageType>> lookup) {
        Optional<String> name = PluginPackageTypes.typeNameOf(repoType, packageTypeConfig);
        if (name.isEmpty()) {
            return new NotPluginTyped();
        }
        return lookup.apply(name.get())
                .<PluginTypeResolution>map(Registered::new)
                .orElseGet(() -> new Unregistered(name.get()));
    }
}
