/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.git;

import org.artifactory.common.addon.Addon;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;

import jakarta.annotation.Nonnull;


/**
 * @author Rotem Kfir
 */
public interface GitAddon extends Addon {

    /**
     * Initialize git repository and propagate to other nodes.
     */
    default void initGitRepository(@Nonnull RepoConfig repoConfig, boolean async) {}
    /**
     * Initialize git repository locally on this Artifactory node.
     */
    default void initGitRepositoryLocalClone(@Nonnull String repoKey) {}

    default void updateGitRepository(@Nonnull RemoteRepoConfig remoteRepoConfig) {}

    /**
     * Delete git repository and propagate to other nodes.
     */
    default void deleteGitRepository(@Nonnull RepoConfig repoConfig, boolean async) {}
    /**
     * Delete git repository locally on this Artifactory node.
     */
    default void deleteGitRepository(@Nonnull String repoKey) {}

    /**
     * Update a git repository that changes were done by another node
     */
    default void syncGitRepository(@Nonnull String repoKey) {}
}
