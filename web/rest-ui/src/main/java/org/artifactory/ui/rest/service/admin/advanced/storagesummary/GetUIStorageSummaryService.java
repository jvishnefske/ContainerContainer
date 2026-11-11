package org.artifactory.ui.rest.service.admin.advanced.storagesummary;

import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.ui.rest.model.storage.StorageSummaryUIModel;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Inbar Tal
 */
@Component
public class GetUIStorageSummaryService implements RestService {

    @Autowired
    private StorageService storageService;
    @Autowired
    private StorageSummaryService storageSummaryService;
    @Autowired
    private ProjectsService projectsService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        StorageSummaryInfo info = storageSummaryService.getLocalCache();
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        StorageSummaryUIModel model;
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            model = new StorageSummaryUIModel(info);
        } else {
            Set<String> projectRepos = projectsService.getProjectRepositoriesKeys(projectKey, false);
            List<RepoStorageSummaryInfo> filteredList = info.getRepoStorageSummaries().values().stream()
                    .filter(repoSum -> projectRepos.contains(repoSum.getRepoKey())).collect(Collectors.toList());
            model = new StorageSummaryUIModel(info, filteredList,
                    info.getProjectsTotalSizes().getOrDefault(projectKey, 0L));
        }
        model.getRepositoriesSummaryList()
                .forEach(repo -> repo.setProjectName(projectsService.getProjectDisplayName(repo.getProjectKey())));
        response.iModel(model);
    }
}
