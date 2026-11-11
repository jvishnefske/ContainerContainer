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

package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.setmeup.IvySettingModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.service.utils.setMeUp.SettingsHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author chen Keinan
 * @author Lior Hasson
 */
@Component
public class IvySettingGeneratorService extends MvnGroupSettingGeneratorService<BaseModel> {

    @Override
    public void execute(ArtifactoryRestRequest<BaseModel> request, RestResponse response) {
        IvySettingModel ivySettingModel = new IvySettingModel();
        List<RepoKeyType> repoKeyTypes = getReadableVirtualRepoKeyTypes();
        repoKeyTypes.forEach(repoKeyType -> ivySettingModel.getLibsRepository().add(repoKeyType));
        List<String> repoLayouts = getRepoLayouts();
        ivySettingModel.getLibsRepositoryLayout().addAll(repoLayouts);
        response.iModel(ivySettingModel);
    }

    private List<RepoKeyType> getReadableVirtualRepoKeyTypes() {
        return SettingsHelper.getReadableVirtualRepoDescriptors(repositoryService, authorizationService)
                .stream()
                .map(virtualRepoDescriptor -> createRepoKeyType(virtualRepoDescriptor.getType(),
                        virtualRepoDescriptor.getKey()))
                .toList();
    }

    private RepoKeyType createRepoKeyType(RepoType type, String key) {
        RepoKeyType repoKeyType = new RepoKeyType(type, key);
        repoKeyType.setIsVirtual(true);
        return repoKeyType;
    }

    private List<String> getRepoLayouts() {
        return centralConfigService.getDescriptor().getRepoLayouts()
                .stream()
                .map(RepoLayout::getName)
                .toList();
    }
}