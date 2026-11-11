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

package org.artifactory.repo.cache.expirable;

import org.artifactory.descriptor.repo.RepoType;

/**
 * @author Inbar Tal
 */
public interface MetaDataChecker {
    /**
     * Indicates whether the specified path is metadata path
     *
     * @param path the path to check
     * @return true if the path is metadata, false otherwise
     */
    boolean isMetadata(RepoType repoType, String repoKey, String path);

    /**
     * For metadata artifacts, return its type, e.g. repo_index, pkg_info, etc. This method is used during downloads
     * to correctly classify the current REST API request.
     *
     * @return the metadata artifact type (see RestApiGroup constants), or null if the artifact is not metadata.
     */
    default String getMetadataArtifactType(RepoType repoType, String repoKey, String path) { return null; }
}
