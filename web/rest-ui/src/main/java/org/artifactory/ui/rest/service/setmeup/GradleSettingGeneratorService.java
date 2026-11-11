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
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.service.utils.setMeUp.SettingsHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @author chen Keinan
 * @author Lior Hasson
 */
@Component
public class GradleSettingGeneratorService extends MvnGroupSettingGeneratorService<BaseModel> {

    public static final Set<RepoDetailsType> REPO_DETAILS_TYPES =
            Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL);

    @Override
    public void execute(ArtifactoryRestRequest<BaseModel> request, RestResponse response) {
        List<RepoConfig> readableRepoConfigs = getReadableRepoConfigs();
        GradleSettingModel gradleSettingModel = createGradleSettingModel(readableRepoConfigs);
        addRepoLayoutsToModel(gradleSettingModel);
        response.iModel(gradleSettingModel);
    }

    private List<RepoConfig> getReadableRepoConfigs() {
        return SettingsHelper.getReadableRepoConfigs(REPO_DETAILS_TYPES, repositoryConfigService, authorizationService);
    }

    private GradleSettingModel createGradleSettingModel(List<RepoConfig> readableRepoConfigs) {
        GradleSettingModel gradleSettingModel = new GradleSettingModel();
        readableRepoConfigs.forEach(repoConfig -> {
            if (repoConfig.isReal()) {
                gradleSettingModel.getLibsPublisher().add(new RepoKeyType(repoConfig));
            } else {
                RepoKeyType repoKeyType = new RepoKeyType(repoConfig);
                gradleSettingModel.getLibsResolver().add(repoKeyType);
                gradleSettingModel.getPluginResolver().add(repoKeyType);
            }
        });
        return gradleSettingModel;
    }

    private void addRepoLayoutsToModel(GradleSettingModel gradleSettingModel) {
        List<RepoLayout> repoLayouts = centralConfigService.getDescriptor().getRepoLayouts();
        repoLayouts.forEach(repoLayout -> gradleSettingModel.getLayouts().add(repoLayout.getName()));
    }
}