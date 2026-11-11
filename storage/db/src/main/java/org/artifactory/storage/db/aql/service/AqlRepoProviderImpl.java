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

package org.artifactory.storage.db.aql.service;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gidi Shabat
 */
public class AqlRepoProviderImpl implements AqlRepoProvider {

    private RepositoryService repositoryService;
    private RepositoryConfigService repositoryConfigService;

    RepositoryService getRepositoryService() {
        if (repositoryService == null) {
            repositoryService = ContextHelper.get().getRepositoryService();
        }
        return repositoryService;
    }

    RepositoryConfigService getRepositoryConfigService() {
        if (repositoryConfigService == null) {
            repositoryConfigService = ContextHelper.get().getRepositoryConfigService();
        }
        return repositoryConfigService;
    }

    @Override
    public List<String> getVirtualRepoKeysContainingRepo(String repoKey) {
        RepoDescriptor repoDescriptor = getRepositoryService().repoDescriptorByKey(repoKey);
        List<VirtualRepoDescriptor> virtualRepoDescriptors =
                getRepositoryService().getVirtualReposContainingRepo(repoDescriptor);
        return virtualRepoDescriptors.stream()
                .map(VirtualRepoDescriptor::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isRepoPathAccepted(RepoPath repoPath) {
        return getRepositoryService().isRepoPathAccepted(repoPath);
    }

    @Override
    public List<String> getVirtualRepoKeys() {
        return getRepositoryService().getVirtualRepoDescriptors().stream()
                .map(VirtualRepoDescriptor::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsVirtualRepoKey(String repoKey) {
        return getRepositoryConfigService().existsByType(RepoDetailsType.VIRTUAL, repoKey);
    }

    @Override
    public List<String> getVirtualResolvedLocalFederatedAndCacheRepoKeys(String virtualRepoKey) {
        return getRepositoryService().getVirtualResolvedLocalFederatedAndCacheDescriptors(virtualRepoKey).stream()
                .map(RepoDescriptor::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public RemoteRepoDescriptor getRemoteRepoDescriptor(String remoteRepoKey) {
        return getRepositoryService().remoteRepoDescriptorByKey(remoteRepoKey);
    }

    @Override
    public boolean isRemoteRepoOffline(String repoKey) {
        return getRepositoryService().isRemoteRepoOffline(repoKey);
    }

    @Override
    public boolean virtualRepoContainsTransitiveValidRepos(String repoKey) {
        return getRepositoryService().virtualRepoContainsTransitiveValidRepos(repoKey);
    }

    @Override
    public String remoteRepoActualNameByKey(String repoKey) {
        return getRepositoryService().remoteRepoActualNameByKey(repoKey);
    }

    @Override
    public List<String> getTransitiveRepoKeysFromVirtual(String repoKey) {
        return getRepositoryService().getTransitiveRepoKeysFromVirtual(repoKey);
    }

    @Override
    public CloseableHttpResponse executeAqlRemotely(String aqlQuery, String repoKey) {
        return getRepositoryService().executeAqlRemotely(aqlQuery, repoKey);
    }

    @Override
    public boolean isRepoTransitiveValid(String repoKey) {
        return getRepositoryService().isRepoTransitiveValid(repoKey);
    }
}