/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

package org.artifactory.ui.rest.service.admin.configuration.projects;

import com.jfrog.build.acl.bridge.BuildFacade;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.projects.UnassignedResourceStats;
import org.jfrog.access.common.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Retrieves the unassigned resources stats
 *
 * @author BarakH
 */
@Component
@NoArgsConstructor
@AllArgsConstructor
public class GetUnassignedResourcesStats implements RestService {

    private RepositoryService repositoryService;
    private ProjectsService projectsService;
    private BuildFacade buildFacade;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        List<UnassignedResourceStats> unassignedStatistics = new ArrayList<>();
        unassignedStatistics.add(getRepositoriesStates());
        unassignedStatistics.add(getBuildsStates());
        response.iModel(unassignedStatistics);
    }

    @NonNull
    private UnassignedResourceStats getBuildsStates() {
        int totalCount = buildFacade.getTotalBuildsCount();
        String defaultBuildInfoRepo = buildFacade.getBuildInfoRepoForDefaultProject();
        int unassignedCount = buildFacade.getBuildsCountByRepoKey(defaultBuildInfoRepo);
        int unassignedUniqueCount = buildFacade.getUniqueBuildsCountByRepoKey(defaultBuildInfoRepo);
        return new UnassignedResourceStats(ResourceType.BUILD.getKey(), totalCount, unassignedCount,
                unassignedUniqueCount);
    }

    @NonNull
    private UnassignedResourceStats getRepositoriesStates() {
        Set<String> allRepoKeys = repositoryService.getAllProjectSupportedRepoKeys();
        int totalCount = allRepoKeys.size();
        int unassignedCount = getUnassignedRepositoriesCount(allRepoKeys);
        return new UnassignedResourceStats(ResourceType.REPO.getKey(), totalCount, unassignedCount, null);
    }

    /**
     * @return The amount of all repositories from the given list which are unassigned to any project (Marked as
     * {@link ProjectsService#DEFAULT_PROJECT})
     */
    private int getUnassignedRepositoriesCount(@NonNull Set<String> allRepoKeys) {
        Map<String, String> repoToProjectMap = projectsService.getRepoKeyToProjectMap(allRepoKeys, false);
        return (int) repoToProjectMap.entrySet()
                .stream()
                .filter(entry -> ProjectsUtils.isDefaultProject(entry.getValue()))
                .count();
    }

    @Autowired
    public void setRepositoryService(@NonNull RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setProjectsService(@NonNull ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setBuildFacade(@NonNull BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }
}