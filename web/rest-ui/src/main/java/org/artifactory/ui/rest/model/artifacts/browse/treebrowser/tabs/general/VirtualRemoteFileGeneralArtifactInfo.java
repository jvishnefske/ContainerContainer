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

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.checksums.Checksums;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.dependecydeclaration.DependencyDeclaration;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.FileInfoUiModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.trash.TrashItemDetails;
import org.artifactory.util.RepoPathUtils;

/**
 * @author Chen Keinan
 */
public class VirtualRemoteFileGeneralArtifactInfo extends GeneralArtifactInfo {
    private Boolean bintrayInfoEnabled;
    private DependencyDeclaration dependencyDeclaration;
    private Checksums checksums;
    private TrashItemDetails trash;
    private String originPath;

    public VirtualRemoteFileGeneralArtifactInfo() {
    }

    @Override
    public void populateGeneralDataForVirtualItem(VirtualRepoItem item) {
        RepoPath repoPath = retrieveRepoPath();
        RepositoryService repoService = retrieveRepoService();
        setInfo(new FileInfoUiModel(retrieveRepoPath(), item));
        if (item != null) {
            getAditionalLocalInfoForItem(repoPath, repoService);
        }
    }

    @Override
    public void populateGeneralData() {
        RepoPath repoPath = retrieveRepoPath();
        setInfo(new FileInfoUiModel(repoPath, null));
        RepositoryService repoService = retrieveRepoService();
        if (repoService.isVirtualRepoExist(repoPath.getRepoKey())) {
            getAditionalLocalInfoForItem(repoPath, repoService);
        }
    }

    private void getAditionalLocalInfoForItem(RepoPath repoPath, RepositoryService repoService) {
        RepoPath localRepoPath = repoService.getVirtualFileInfo(repoPath).getRepoPath();

        LocalRepoDescriptor localRepoDescriptor = repoService
                .localFederatedCachedRepoDescriptorByKey(localRepoPath.getRepoKey());

        // update file info checksum
        originPath = localRepoPath.toPath();
        updateFileInfoCheckSum(localRepoPath, localRepoDescriptor);
        if (localRepoDescriptor.getType() != RepoType.Distribution) {
            updateDependencyDeclaration(localRepoPath, localRepoDescriptor);
            updateTrashDetails(localRepoPath);
        }
    }

    private void updateTrashDetails(RepoPath repoPath) {
        if (!RepoPathUtils.isTrash(repoPath)) {
            return;
        }
        Properties properties = ContextHelper.get().beanForType(PropertiesService.class).getProperties(repoPath);
        trash = new TrashItemDetails(properties);
    }

    private boolean isValidFile(RepoPath repoPath) {
        return NamingUtils.isJarVariant(repoPath.getName()) || NamingUtils.isPom(repoPath.getName());
    }

    private boolean isUserExists() {
        return !ContextHelper.get().beanForType(UserGroupService.class).currentUser().isTransientUser();
    }

    private void updateDependencyDeclaration(RepoPath repoPath, LocalRepoDescriptor localRepoDescriptor) {
        DependencyDeclaration localDependencyDeclaration = new DependencyDeclaration();
        localDependencyDeclaration.updateDependencyDeclaration("", repoPath, localRepoDescriptor);
        if (localDependencyDeclaration.getTypes() != null) {
            dependencyDeclaration = localDependencyDeclaration;
        }
    }

    private void updateFileInfoCheckSum(RepoPath repoPath, LocalRepoDescriptor localRepoDescriptor) {
        org.artifactory.fs.FileInfo fileInfo = retrieveRepoService().getFileInfo(repoPath);
        checksums = new Checksums();
        checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
    }

    public DependencyDeclaration getDependencyDeclaration() {
        return dependencyDeclaration;
    }

    public Checksums getChecksums() {
        return checksums;
    }

    public Boolean getBintrayInfoEnabled() {
        return bintrayInfoEnabled;
    }

    public TrashItemDetails getTrash() {
        return trash;
    }

    public String getOriginPath() {
        return originPath;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
