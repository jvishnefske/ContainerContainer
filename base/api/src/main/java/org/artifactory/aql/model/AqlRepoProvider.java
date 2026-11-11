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

package org.artifactory.aql.model;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.repo.RepoPath;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yinon Avraham
 */
public interface AqlRepoProvider {

    List<String> getVirtualRepoKeysContainingRepo(String repoKey);

    boolean isRepoPathAccepted(RepoPath repoPath);

    List<String> getVirtualRepoKeys();

    List<String> getVirtualResolvedLocalFederatedAndCacheRepoKeys(String virtualRepoKey);

    default RemoteRepoDescriptor getRemoteRepoDescriptor(String remoteRepoKey) {
        return null;
    }

    default String remoteRepoActualNameByKey(String repoKey) {
        return "";
    }

    default boolean existsVirtualRepoKey(String repoKey) {
        return false;
    }

    default boolean isRemoteRepoOffline(String repoKey) {
        return false;
    }

    default boolean virtualRepoContainsTransitiveValidRepos(String repoKey) {
        return false;
    }

    default List<String> getTransitiveRepoKeysFromVirtual(String repoKey) {
        return new ArrayList<>();
    }

    default CloseableHttpResponse executeAqlRemotely(String aqlQuery, String repoKey) {
        return null;
    }

    default boolean isRepoTransitiveValid(String repoKey) {
        return false;
    }
}
