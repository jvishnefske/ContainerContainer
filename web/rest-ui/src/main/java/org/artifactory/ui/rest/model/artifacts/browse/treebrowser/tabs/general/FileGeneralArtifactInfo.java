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

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.IgnoreSpecialFields;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.checksums.Checksums;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.dependecydeclaration.DependencyDeclaration;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.FileInfoUiModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.trash.TrashItemDetails;
import org.artifactory.util.RepoPathUtils;

/**
 * @author Chen Keinan
 */
@Slf4j
@JsonTypeName("file")
@JsonFilter("exclude fields")
@IgnoreSpecialFields(value = {"repoKey", "path"})
public class FileGeneralArtifactInfo extends GeneralArtifactInfo {

    private DependencyDeclaration dependencyDeclaration;
    private Checksums checksums;
    private TrashItemDetails trash;

    FileGeneralArtifactInfo() {
    }

    @Override
    public void populateGeneralData() {
        RepoPath repoPath = retrieveRepoPath();
        if (RepoPathUtils.isTrash(repoPath)) {
            updateTrashDetails(repoPath);
        } else {
            updateDetails(repoPath);
        }
    }

    void updateTrashDetails(RepoPath repoPath) {
        Properties properties = ContextHelper.get().beanForType(PropertiesService.class).getProperties(repoPath);
        trash = new TrashItemDetails(properties);
    }

    void updateDetails(RepoPath repoPath) {
        RepositoryService repoService = retrieveRepoService();
        LocalRepoDescriptor localRepoDescriptor =
                repoService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey());
        ItemInfo itemInfo = repoService.getItemInfo(repoPath);
        if (!(itemInfo instanceof FileInfo fileInfo)) {
            log.debug("Expected a file but found a folder at: {} skipping file-specific general info population",
                    repoPath);
            return;
        }
        super.setInfo(new FileInfoUiModel(fileInfo));
        updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
        if (localRepoDescriptor.getType() != RepoType.Distribution) {
            updateDependencyDeclaration(repoPath, localRepoDescriptor);
        }
    }

    private void updateDependencyDeclaration(RepoPath repoPath, LocalRepoDescriptor localRepoDescriptor) {
        DependencyDeclaration localDependencyDeclaration = new DependencyDeclaration();
        localDependencyDeclaration.updateDependencyDeclaration("", repoPath, localRepoDescriptor);
        if (localDependencyDeclaration.getTypes() != null) {
            dependencyDeclaration = localDependencyDeclaration;
        }
    }

    private void updateFileInfoCheckSum(FileInfo fileInfo, LocalRepoDescriptor localRepoDescriptor) {
        checksums = new Checksums();
        checksums.updateFileInfoCheckSum(fileInfo, localRepoDescriptor);
    }

    public DependencyDeclaration getDependencyDeclaration() {
        return dependencyDeclaration;
    }

    public Checksums getChecksums() {
        return checksums;
    }

    public TrashItemDetails getTrash() {
        return trash;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToStringIgnoreSpecialFields(this);
    }
}
