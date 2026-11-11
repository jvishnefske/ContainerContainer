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

package org.artifactory.ui.rest.service.onboarding;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.config.RepoConfigDefaultValues.DefaultUrl;
import org.artifactory.repo.onboarding.DefaultRepoModel;
import org.artifactory.repo.onboarding.RemoteDefaultRepoModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.artifactory.common.ConstantValues.huggingFaceMLVirtualEnabled;

/**
 * Service for creating default repositories for repo types stated in CreateDefaultReposModel
 *
 * @author nadavy
 */
@Slf4j
@Component
public class GetReposModelService implements RestService<Void> {

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        try (InputStream json = getClass().getResourceAsStream("/templates/defaultRepository.json")) {
            List<DefaultRepoModel> model = JacksonReader.streamAsValueTypeReference(json, new TypeReference<>() {
            });
            model.forEach(this::populateRemoteValues);
            model.forEach(this::filterOutHuggingFaceVirtualRepos);
            response.iModel(model);
        } catch (IOException e) {
            log.error("Can't import default repository file: {}", e.getMessage());
            log.debug("Can't import default repository file", e);
            response.error("Can't import default repository file");
        }
    }

    private void populateRemoteValues(DefaultRepoModel repoType) {
        List<RemoteDefaultRepoModel> remoteRepos = repoType.getRemoteRepoKeys();
        if (remoteRepos != null) {
            remoteRepos.forEach(repo -> {
                if (StringUtils.isEmpty(repo.getUrl())) {
                    repo.setUrl(DefaultUrl.urlByType(RepoType.fromType(repoType.getRepoType())));
                }
                repo.setExists(repositoryService.remoteRepoDescriptorByKey(repo.getRepoKey()) != null);
            });
        }
    }

    private void filterOutHuggingFaceVirtualRepos(DefaultRepoModel repoType) {
        if (repoType.getRepoType().equals(RepoType.HuggingFaceML.getType()) &&
                !huggingFaceMLVirtualEnabled.getBoolean()) {
            repoType.setVirtualRepoKeys(null);
        }
    }
}
