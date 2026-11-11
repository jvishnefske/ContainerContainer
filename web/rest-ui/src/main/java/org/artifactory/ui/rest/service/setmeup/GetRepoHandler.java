package org.artifactory.ui.rest.service.setmeup;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.security.ArtifactoryPermission.READ;

/**
 * This handler retrieves {@link RepoKeyType} for a given repository.
 * It provides the repository data for the "Set Me Up" feature in the UI.
 */
@Component
@Slf4j
public class GetRepoHandler extends BaseSetMeUpHandler<Void> {

    public GetRepoHandler(AuthorizationService authorizationService,
            InternalRepositoryConfigService repositoryConfigService, ProjectsService projectsService) {
        super(authorizationService, repositoryConfigService, projectsService);
    }

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        String repoKey = getRepoKeyFromRequest(request);
        RepoConfig repoConfig = getRepoConfig(repoKey);
        SetMeUpModel setMeUpModel = createSetMeUpModel(repoConfig);
        response.iModel(setMeUpModel);
    }

    private String getRepoKeyFromRequest(ArtifactoryRestRequest<Void> request) {
        String repoKey = request.getPathParamByKey(REPO_KEY);
        if (StringUtils.isBlank(repoKey)) {
            throw new BadRequestException("Missing repo key");
        }
        return repoKey;
    }

    private RepoConfig getRepoConfig(String repoKey) {
        try {
            RepoConfig repoConfig =
                    ((InternalRepositoryConfigService) repositoryConfigService).getRepoConfigByKey(repoKey, true);
            if (repoConfig == null) {
                throw new BadRequestException("Repository: '%s' not found".formatted(repoKey));
            }
            return repoConfig;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid repository key: '%s'".formatted(repoKey));
        }
    }

    private SetMeUpModel createSetMeUpModel(RepoConfig repoConfig) {
        SetMeUpModel setMeUpModel = new SetMeUpModel();
        String repoKey = repoConfig.getKey();
        boolean canRead = authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null);
        boolean canDeploy = hasDeployPermission(repoConfig, null);
        if (canRead || canDeploy) {
            RepoKeyType repoKeyType = createRepoKeyType(repoConfig, canRead, canDeploy);
            setMeUpModel.setRepoKeyTypes(List.of(repoKeyType));
        }
        return setMeUpModel;
    }
}
