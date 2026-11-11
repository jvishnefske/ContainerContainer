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

package org.artifactory.ui.rest.service.home.widget;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.ui.rest.model.home.HomeWidgetModel;
import org.artifactory.ui.utils.RequestUtils;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author Shay Bagants
 */
@Component
@Slf4j
public class ArtifactCountWidgetService implements RestService {
    public static final String ARTIFACT_COUNT = "artifactCount";
    private RepositoryService repoService;
    private StorageSummaryService storageSummaryService;

    @Autowired
    public ArtifactCountWidgetService(RepositoryService repoService, StorageSummaryService storageSummaryService) {
        this.repoService = repoService;
        this.storageSummaryService = storageSummaryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String projectName = RequestUtils.getProjectKeyFromRequest(request);
        HomeWidgetModel model = new HomeWidgetModel("Artifact count Info");
        long filesCount = 0L;
        try {
            if (ProjectsUtils.isDefaultProject(projectName)) {
                filesCount = ConstantValues.useHomeWidgetArtifactCountV2Cache.getBoolean() ?
                        storageSummaryService.getLocalCache().getTotalFiles() :
                        repoService.getArtifactCount();
            } else {
                filesCount = storageSummaryService.getLocalCache()
                        .getProjectsTotalFilesCount()
                        .getOrDefault(projectName, 0L);
            }
        } catch (HeavyQueryCacheException e) {
            filesCount = storageSummaryService.getLocalCache().getTotalItems();
            log.info("Return artifact count from storage summary");
        } catch (CacheUnAvailableException e) {
            log.info("Cache unavailable while calculating storage summary for the first time", e);
        }
        model.addData(ARTIFACT_COUNT, filesCount);
        response.iModel(model);
    }
}
