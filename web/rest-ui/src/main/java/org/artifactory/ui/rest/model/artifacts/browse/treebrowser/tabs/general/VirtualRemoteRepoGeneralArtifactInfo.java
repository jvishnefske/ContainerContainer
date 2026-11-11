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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.includedrepositories.Repository;
import org.artifactory.ui.utils.GeneralArtifactsInfoUtils;

import java.util.List;

/**
 * @author Chen Keinan
 */
@JsonTypeName("virtualRemoteRepository")
@NoArgsConstructor
public class VirtualRemoteRepoGeneralArtifactInfo extends RepositoryGeneralArtifactInfo {

    private String offlineMessage;
    private List<Repository> includedRepositories;
    private String hasIncludedRepositories;

    @Override
    public void populateGeneralDataForVirtualItem(VirtualRepoItem item) {
        super.populateGeneralData();
        RepositoryService repositoryService = retrieveRepoService();
        AuthorizationService authorizationService = retrieveAuthorizationService();
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(getRepoKey());
        updateVirtualIncludedRepositories(repoDescriptor, authorizationService);
        updateRepositoryOffline(repoDescriptor);
    }

    void updateVirtualIncludedRepositories(RepoDescriptor repoDescriptor, AuthorizationService authorizationService) {
        if (repoDescriptor instanceof VirtualRepoDescriptor virtualRepoDescriptor) {
            if (!isRemoveAssociateAndIncludedReposFromResponse) {
                setIncludedRepositories(
                        GeneralArtifactsInfoUtils.updateVirtualIncludedRepositories(virtualRepoDescriptor,
                                authorizationService));
            } else {
                setHasIncludedRepositories(String.valueOf(!virtualRepoDescriptor.getRepositories().isEmpty()));
            }
        }
    }

    private void updateRepositoryOffline(RepoDescriptor repoDescriptor) {
        if (repoDescriptor != null && repoDescriptor instanceof RemoteRepoDescriptor) {
            if (((RemoteRepoDescriptor) repoDescriptor).isOffline()) {
                setOfflineMessage("This repository is offline, content is served from the cache only.");
            }
        }
    }

    public String getOfflineMessage() {
        return offlineMessage;
    }

    public void setOfflineMessage(String offlineMessage) {
        this.offlineMessage = offlineMessage;
    }

    public List<Repository> getIncludedRepositories() {
        return includedRepositories;
    }

    public void setIncludedRepositories(List<Repository> includedRepositories) {
        this.includedRepositories = includedRepositories;
    }

    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public String getHasIncludedRepositories() {
        return this.hasIncludedRepositories;
    }

    public void setHasIncludedRepositories(String hasIncludedRepositories) {
        this.hasIncludedRepositories = hasIncludedRepositories;
    }
}
