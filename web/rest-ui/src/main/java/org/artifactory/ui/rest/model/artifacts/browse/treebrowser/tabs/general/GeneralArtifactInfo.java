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

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.BaseInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.artifactory.ui.utils.GeneralArtifactsInfoUtils;

import java.util.List;

/**
 * @author Chen Keinan
 */
@Slf4j
@NoArgsConstructor
public class GeneralArtifactInfo extends BaseArtifactInfo implements RestGeneralTab {

    private BaseInfo info;
    private List<VirtualRepository> virtualRepositories;
    private String hasVirtualRepositoryAssociation;

    protected boolean isRemoveAssociateAndIncludedReposFromResponse;

    @Override
    public void populateGeneralData() {

    }

    @Override
    public void populateGeneralDataForVirtualItem(VirtualRepoItem item) {
        throw new UnsupportedOperationException("populateGeneralDataForVirtualItem unsupported");
    }

    @Override
    public RepoPath retrieveRepoPath() {
        return RepoPathFactory.create(getRepoKey(), getPath());
    }

    protected RepositoryService retrieveRepoService() {
        return ContextHelper.get().beanForType(RepositoryService.class);
    }

    protected AuthorizationService retrieveAuthorizationService() {
        return ContextHelper.get().beanForType(AuthorizationService.class);
    }

    protected void populateVirtualRepositories(boolean removeAssociateAndIncludedReposFromResponse) {
        if (!removeAssociateAndIncludedReposFromResponse) {
            setVirtualRepositories(
                    GeneralArtifactsInfoUtils.populateVirtualRepositories(retrieveRepoService(), getRepoKey()));
            return;
        }
        RepoConfig repoConfig = retrieveRepoService().getRepoConfigByKey(getRepoKey());
        if (repoConfig == null) {
            log.debug("repoConfig is null for repo: {}", getRepoKey());
            return;
        }
        setHasVirtualRepositoryAssociation(
                String.valueOf(retrieveRepoService().hasDirectlyAssociatedVirtualRepos(repoConfig)));
    }

    public BaseInfo getInfo() {
        return info;
    }

    public void setInfo(BaseInfo info) {
        this.info = info;
    }

    public List<VirtualRepository> getVirtualRepositories() {
        return virtualRepositories;
    }

    public void setVirtualRepositories(List<VirtualRepository> virtualRepositories) {
        this.virtualRepositories = virtualRepositories;
    }

    public String getHasVirtualRepositoryAssociation() {
        return hasVirtualRepositoryAssociation;
    }

    public void setHasVirtualRepositoryAssociation(String hasVirtualRepositoryAssociation) {
        this.hasVirtualRepositoryAssociation = hasVirtualRepositoryAssociation;
    }

    public void setRemoveAssociateAndIncludedReposFromResponse(boolean removeAssociateAndIncludedReposFromResponse) {
        isRemoveAssociateAndIncludedReposFromResponse = removeAssociateAndIncludedReposFromResponse;
    }
}
