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

package org.artifactory.request;

import jakarta.annotation.Nonnull;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

/**
 * Request context that might be passed to the repository when requesting for info.
 *
 * @author Yossi Shaul
 */
public interface RequestContext {

    boolean isFromAnotherArtifactory();

    String getResourcePath();

    Properties getProperties();

    /**
     * Return the client request. This might be null when doing internal requests.
     *
     * @return The request or null if not set.
     */
    Request getRequest();

    void setAttribute(String name, String value);

    String getAttribute(String name);

    @Nonnull
    default RepoPath alterPath(CloseableHttpResponse response, RepoPath repoPath) {
        return repoPath;
    }

    default boolean isSkipCurationOnResource() {
        return true;
    }

    default void setSkipCurationOnResource(boolean skipCurationOnCachedResource) {
    }
}
