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

package org.artifactory.addon.docker;

import org.artifactory.common.addon.Addon;
import org.artifactory.addon.common.UserReadableFilesCache;
import org.artifactory.addon.docker.rest.DockerTokenCacheKey;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * @author Shay Yaakov
 */
public interface DockerAddon extends Addon {

    String DOCKER_TEMP_UPLOADS_FOLDER = "_uploads";

    static boolean isUnderDockerTempUploadsFolder(RepoPath repoPath) {
        if (repoPath == null || repoPath.isRoot()) {
            return false;
        }
        if (isDockerTempUploadsFolder(repoPath)) {
            return true;
        }
        return isUnderDockerTempUploadsFolder(repoPath.getParent());
    }

    static boolean isDockerTempUploadsFolder(RepoPath repoPath) {
        return repoPath != null && repoPath.isFolder() &&
                DOCKER_TEMP_UPLOADS_FOLDER.equals(repoPath.getName());
    }

    /**
     * clean docker temp "_uploads" folder that has files that are older than 1 day
     */
    default void cleanup(String repoId, String uploadsPath, boolean async) {

    }

    /**
     * search for docker temp folders ("_uploads") and initiate cleanup
     * for files that are older than 1 day
     */
    default void searchAndCleanupTempFolders(String repoId) {

    }

    default DockerV2InfoModel getDockerV2Model(RepoPath manifestPath, boolean convertSizeToHumanReadable)
            throws IOException {
        return null;
    }

    /**
     * Retrieves a new auth token for the {@param tokenCacheKey} passed to it.
     */
    default String fetchDockerAuthToken(DockerTokenCacheKey tokenCacheKey) {
        return null;
    }

    /**
     * @param repoKey   Repo to promote
     * @param promotion Contains all relevant promotion details
     */
    default void dockerV2promote(String repoKey, DockerV2Promotion promotion) {
    }

    /**
     * @return Blobs cache of the form <blobFileName_repoKey_username, blobPath>.
     */
    default UserReadableFilesCache getBlobsRepoPathCache() {
        return null;
    }

    default boolean isDockerManifestFolder(@Nonnull RepoPath repoPath) {
        return false;
    }
}