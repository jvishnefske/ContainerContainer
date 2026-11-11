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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.util.RepoPathUtils;

import static org.artifactory.utils.ObfuscationUtils.obfuscateIfNeeded;

/**
 * @author Chen Keinan
 */
@JsonPropertyOrder(
        {
                "name", "repositoryPath", "deployedBy", "artifactsCount", "created", "watchingSince",
                "lastReplicationStatus"
        }
)
public class FolderInfo extends BaseInfo {

    private String deployedBy;
    private String created;
    private String watchingSince;

    public FolderInfo() {
    }

    public FolderInfo(RepoPath repoPath, boolean isLocal) {
        if (RepoPathUtils.isTrash(repoPath)) {
            setName(repoPath.getName());
            return;
        }
        if (isLocal) {
            populateFolderInfo(repoPath);
        } else {
            populateVirtualRemoteFolderInfo(repoPath);
        }
    }

    private void populateFolderInfo(RepoPath repoPath) {
        RepositoryService repoService = ContextHelper.get().beanForType(RepositoryService.class);

        this.setName(repoPath.getName());
        this.setRepositoryPath(repoPath.getRepoKey() + "/" + repoPath.getPath() + "/");
        ItemInfo itemInfo = repoService.getItemInfo(repoPath);
        setWatchingSince(fetchWatchingSince(repoPath));
        setCreated(formatIsoDate(itemInfo.getCreated()));
        this.setDeployedBy(obfuscateIfNeeded(itemInfo.getModifiedBy()));
    }

    private void populateVirtualRemoteFolderInfo(RepoPath repoPath) {
        // set name
        this.setName(repoPath.getName());
        // set repository path
        this.setRepositoryPath(repoPath.getRepoKey() + "/" + repoPath.getPath() + "/");

        addVirtualItemDetails(repoPath);
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public void setDeployedBy(String deployedBy) {
        this.deployedBy = deployedBy;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getWatchingSince() {
        return watchingSince;
    }

    public void setWatchingSince(String watchingSince) {
        this.watchingSince = watchingSince;
    }

    private void addVirtualItemDetails(RepoPath repoPath) {
        ArtifactoryContext context = ContextHelper.get();
        RepositoryService repoService = context.getRepositoryService();
        VirtualRepoDescriptor virtual = repoService.virtualRepoDescriptorByKey(repoPath.getRepoKey());
        if (virtual != null) {
            RepositoryBrowsingService browsingService = context.beanForType(RepositoryBrowsingService.class);
            VirtualRepoItem item = browsingService.getVirtualRepoItem(repoPath);
            if (item != null && item.getItemInfo() != null) {
                this.setCreated(formatIsoDate(item.getItemInfo().getCreated()));
            }
        }
    }
}
