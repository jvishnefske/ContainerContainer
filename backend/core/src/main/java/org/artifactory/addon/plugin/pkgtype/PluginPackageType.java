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

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable description of one plugin-defined package type.
 *
 * <p>Construct it with {@link #builder(String, String)}. The handlers are
 * optional: a type with no download handler serves its repositories exactly as
 * Generic does, and a type with no API handler has no HTTP API (its
 * {@code /api/docker/} and {@code /api/pkg/} URLs answer 404).
 *
 * @param name         unique type name, validated by {@link PluginPackageTypes#validateName(String)}
 * @param displayName  human-readable name
 * @param pluginName   name of the plugin that registered the type, used for reload and diagnostics
 * @param layoutName   optional repository layout name suggested for new repositories
 * @param download     optional handler for GET/HEAD requests
 * @param upload       optional handler for PUT requests
 * @param index        optional handler run after an artifact is stored
 * @param api          optional handler of the type's HTTP API ({@code /api/docker/{repoKey}/...} and
 *                     {@code /api/pkg/{repoKey}/...}), see {@link PluginApiHandler}
 */
public record PluginPackageType(
        String name,
        String displayName,
        String pluginName,
        Optional<String> layoutName,
        Optional<PluginDownloadHandler> download,
        Optional<PluginUploadHandler> upload,
        Optional<PluginIndexHandler> index,
        Optional<PluginApiHandler> api) {

    public PluginPackageType {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(layoutName, "layoutName");
        Objects.requireNonNull(download, "download");
        Objects.requireNonNull(upload, "upload");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(api, "api");
    }

    /** A type without an HTTP API handler; kept so code written before {@link #api()} existed compiles unchanged. */
    public PluginPackageType(String name, String displayName, String pluginName, Optional<String> layoutName,
            Optional<PluginDownloadHandler> download, Optional<PluginUploadHandler> upload,
            Optional<PluginIndexHandler> index) {
        this(name, displayName, pluginName, layoutName, download, upload, index, Optional.empty());
    }

    public static Builder builder(String name, String pluginName) {
        return new Builder(name, pluginName);
    }

    /** Builder for {@link PluginPackageType}; {@code displayName} defaults to {@code name}. */
    public static final class Builder {
        private final String name;
        private final String pluginName;
        private String displayName;
        private String layoutName;
        private PluginDownloadHandler download;
        private PluginUploadHandler upload;
        private PluginIndexHandler index;
        private PluginApiHandler api;

        private Builder(String name, String pluginName) {
            this.name = name;
            this.pluginName = pluginName;
            this.displayName = name;
        }

        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        public Builder layoutName(String value) {
            this.layoutName = value;
            return this;
        }

        public Builder download(PluginDownloadHandler value) {
            this.download = value;
            return this;
        }

        public Builder upload(PluginUploadHandler value) {
            this.upload = value;
            return this;
        }

        public Builder index(PluginIndexHandler value) {
            this.index = value;
            return this;
        }

        public Builder api(PluginApiHandler value) {
            this.api = value;
            return this;
        }

        public PluginPackageType build() {
            return new PluginPackageType(name, displayName, pluginName, Optional.ofNullable(layoutName),
                    Optional.ofNullable(download), Optional.ofNullable(upload), Optional.ofNullable(index),
                    Optional.ofNullable(api));
        }
    }
}
