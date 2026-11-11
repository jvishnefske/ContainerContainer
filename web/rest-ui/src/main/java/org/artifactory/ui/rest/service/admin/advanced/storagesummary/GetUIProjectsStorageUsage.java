package org.artifactory.ui.rest.service.admin.advanced.storagesummary;

import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.ui.rest.model.storage.ProjectStorageUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Barakh
 */
@Component
public class GetUIProjectsStorageUsage implements RestService {

    private StorageService storageService;
    private ProjectsService projectsService;
    private StorageSummaryService storageSummaryService;

    @Autowired
    public void setStorageService(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    public void setStorageSummaryService(StorageSummaryService storageSummaryService) {
        this.storageSummaryService = storageSummaryService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        StorageSummaryInfo info = storageSummaryService.getLocalCache();
        List<String> userProjects = getUserProjectsWithoutTheDefault();
        List<ProjectStorageUsage> model = new ArrayList<>();
        userProjects.forEach(projectKey -> {
            ProjectStorageUsage projectStorageUsage =
                    new ProjectStorageUsage(projectKey, projectsService.getProjectDisplayName(projectKey),
                            info.getProjectsTotalSizes().getOrDefault(projectKey, 0L),
                            projectsService.getProjectStorageQuotaBytes(projectKey));
            model.add(projectStorageUsage);
        });

        response.iModel(model);
    }

    private List<String> getUserProjectsWithoutTheDefault() {
        return projectsService.getAllProjectsManagedByUser()
                .stream()
                .filter(projKey -> !ProjectsService.DEFAULT_PROJECT.equals(projKey))
                .collect(Collectors.toList());
    }
}
