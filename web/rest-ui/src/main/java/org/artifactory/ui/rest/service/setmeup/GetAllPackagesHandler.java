package org.artifactory.ui.rest.service.setmeup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This handler retrieves set of {@link RepoType} of the supported repositories based on the user's permissions.
 */
@Component
@Slf4j
public class GetAllPackagesHandler extends BaseSetMeUpHandler<Void> {

    public GetAllPackagesHandler(AuthorizationService authorizationService,
            RepositoryConfigService repositoryConfigService,
            ProjectsService projectsService) {
        super(authorizationService, repositoryConfigService, projectsService);
    }

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        List<String> supportedUserPackageTypes = getUserRepoTypes(projectKey).stream()
                .map(RepoType::getType)
                .sorted()
                .toList();
        response.iModelList(supportedUserPackageTypes);
    }

    private Set<RepoType> getUserRepoTypes(String projectKey) {
        Set<RepoType> userRepoTypes = new HashSet<>();
        List<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByTypesAndProject(REPO_DETAILS_TYPES, projectKey);
        Map<String, Boolean> readPermissionCache = new HashMap<>();
        Map<String, Boolean> deployPermissionCache = new HashMap<>();
        for (RepoConfig repoConfig : repoConfigs) {
            if (!userRepoTypes.contains(repoConfig.getPackageType()) && isValidRepoConfig(repoConfig) &&
                    hasReadOrDeployPermission(repoConfig, readPermissionCache, deployPermissionCache)) {
                userRepoTypes.add(repoConfig.getPackageType());
            }
        }
        return userRepoTypes;
    }
}