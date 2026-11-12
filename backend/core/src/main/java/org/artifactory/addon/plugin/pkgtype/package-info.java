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

/**
 * Plugin-defined repository package types.
 *
 * <p>A plugin package type is a {@code Generic} repository whose
 * {@code packageTypeConfig} map holds {@link org.artifactory.addon.plugin.pkgtype.PluginPackageTypes#CONFIG_KEY}
 * set to a registered type name. {@code RepoType} stays closed: every subsystem
 * that switches on it sees an ordinary Generic repository, and removing the
 * plugin degrades the repository to plain Generic with its content intact.
 *
 * <p>This package is the contract between the Groovy plugin engine, which
 * registers types, and the request path (download, upload, storage events,
 * repository validation, the HTTP API), which looks them up. It depends on
 * neither side.
 *
 * <p>A type may serve a full HTTP API through a {@link org.artifactory.addon.plugin.pkgtype.PluginApiHandler}:
 * every method on {@code /api/docker/{repoKey}/...} and {@code /api/pkg/{repoKey}/...} (and, through the
 * {@code /v2/} rewrite, {@code /v2/{repoKey}/...}) reaches it as an immutable
 * {@link org.artifactory.addon.plugin.pkgtype.PluginApiRequest} after the caller's permission is checked, and it
 * answers with a {@link org.artifactory.addon.plugin.pkgtype.PluginApiResponse} value.
 */
package org.artifactory.addon.plugin.pkgtype;
