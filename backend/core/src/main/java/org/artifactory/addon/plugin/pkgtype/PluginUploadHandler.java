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
import org.artifactory.request.Request;

/** Decides what happens to a PUT request to a repository of a plugin package type. */
@FunctionalInterface
public interface PluginUploadHandler {
    UploadDecision decide(Request request, RepoPath repoPath);
}
