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

/**
 *
 */
package org.artifactory.request;

import org.artifactory.api.request.ArtifactoryHeaders;

public interface ArtifactoryRequest extends Request, ArtifactoryHeaders {

    String getRepoKey();

    String getPath();

    boolean isMetadata();

    /**
     * Indicates whether the request is coming back to the same proxy as a result of reverse mirroring
     */
    boolean isRecursive();

    long getModificationTime();

    String getName();

    /**
     * Indicates whether the request if for a directory instead of a file
     *
     * @return True if the request uri if for a directory
     */
    boolean isDirectoryRequest();

    /**
     * @return True if the request should not get tracked as a package usage event (i.e. internal upload)
     */
    boolean shouldSkipPackageUsageTrack();
}
