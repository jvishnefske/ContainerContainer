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

import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.Request;

/**
 * Handles a GET or HEAD request to a repository of a plugin package type.
 *
 * <p>The handler either fills {@code response} and returns {@code true}, or
 * returns {@code false} to decline, in which case normal Generic handling
 * applies. A handled response is served the same way as the existing
 * {@code altResponse} plugin hook: a set status with no stream sends that
 * status and message; a set input stream sends that content.
 */
@FunctionalInterface
public interface PluginDownloadHandler {
    boolean handle(Request request, RepoPath repoPath, ResponseCtx response);
}
