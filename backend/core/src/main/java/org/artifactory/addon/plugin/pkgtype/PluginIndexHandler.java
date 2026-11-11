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

import org.artifactory.repo.RepoPath;

/**
 * Runs after an artifact is stored in a repository of a plugin package type,
 * off the request thread. Typical use is maintaining index or metadata files.
 * Writes the handler makes to the same repository do not re-trigger it.
 */
@FunctionalInterface
public interface PluginIndexHandler {
    void onStored(RepoPath stored);
}
