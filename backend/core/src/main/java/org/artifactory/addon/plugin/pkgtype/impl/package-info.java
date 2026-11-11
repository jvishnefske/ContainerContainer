/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Request-side implementation of plugin package types: the registry, request-time type lookup, the plugin boundary
 * that turns handler outcomes into values, upload routing, the asynchronous index hook and save-time validation.
 *
 * <p>Decisions are pure static functions ({@code PluginTypeResolution.resolve}, {@code PluginTypeHandlers.route},
 * {@code PluginPackageTypeRepoConfigValidator.check}, {@code PluginPackageTypeRegistryImpl.plan}); the Spring beans
 * here and the edits in {@code DownloadServiceImpl} and {@code UploadServiceImpl} are the thin shell around them.
 */
package org.artifactory.addon.plugin.pkgtype.impl;
