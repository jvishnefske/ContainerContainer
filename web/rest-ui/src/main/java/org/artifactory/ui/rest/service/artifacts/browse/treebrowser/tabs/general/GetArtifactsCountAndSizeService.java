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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.BaseInfo;
import org.jfrog.storage.common.StorageUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;

/**
 * @author Aviad Shikloshi
 */
@Slf4j
@Component
public class GetArtifactsCountAndSizeService implements RestService {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private RepositoryService repositoryService;

    public void setSecurityService(SecurityService SecurityService) {
        this.securityService = SecurityService;
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        BaseInfo baseInfo = (BaseInfo) request.getImodel();
        try {
            RepoPath repoPath = RepoPathFactory.create(baseInfo.getRepositoryPath());
            if (TRASH_REPO_KEY.equals(repoPath.getRepoKey()) && securityService.isAnonymous()) {
                log.warn("Error while counting and calculating the size of artifacts, User has no permission.");
                response.responseCode(HttpStatus.SC_FORBIDDEN).error("User has no permission on " + TRASH_REPO_KEY);
            } else {
                FolderSummaryInfo folderSummaryInfo = repositoryService.getArtifactCountAndSize(repoPath);
                response.iModel(Map.of("artifactsCount", folderSummaryInfo.getFileCount(), "artifactSize",
                        StorageUnit.toReadableString(folderSummaryInfo.getFolderSize())));
            }
        } catch (Exception e) {
            log.warn("Error while counting and calculating the size of artifacts .", e);
            response.error("Unable to count and calculate the size of artifacts.");
        }
    }
}
