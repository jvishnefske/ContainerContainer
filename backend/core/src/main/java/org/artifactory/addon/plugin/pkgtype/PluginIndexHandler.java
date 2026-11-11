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
 * Runs after the set of files in a local repository of a plugin package type
 * changes, off the request thread, after the change commits. Typical use is
 * maintaining index or metadata files.
 *
 * <p>The handler is called with:
 * <ul>
 *     <li>the stored path, when a file is deployed into the repository;</li>
 *     <li>the destination path, when a file is copied or moved into the
 *     repository;</li>
 *     <li>the removed path, when a file is moved out of, or deleted from, the
 *     repository (each file of a deleted folder is reported). A move within
 *     the repository reports both the removed and the new path.</li>
 * </ul>
 * The path passed may therefore no longer exist: a handler that keeps an
 * incremental index should check whether it is present, and a handler that
 * rebuilds from the repository contents can ignore which path it was given.
 * Folder events and trash-can items are not reported. Writes, moves and deletes
 * the handler makes to the same repository do not re-trigger it.
 */
@FunctionalInterface
public interface PluginIndexHandler {
    void onStored(RepoPath stored);
}
