package org.artifactory.ui.rest.service.setmeup;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.setmeup.GetReposByKeysRequest;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This handler retrieves {@link RepoKeyType} objects for a list of repository keys.
 * It provides the repository data for the "Set Me Up" feature in the UI.
 */
@Component
@Slf4j
public class GetReposByKeysHandler extends BaseSetMeUpHandler<GetReposByKeysRequest> {

    public GetReposByKeysHandler(AuthorizationService authorizationService,
            InternalRepositoryConfigService repositoryConfigService, ProjectsService projectsService) {
        super(authorizationService, repositoryConfigService, projectsService);
    }

    @Override
    public void execute(ArtifactoryRestRequest<GetReposByKeysRequest> request, RestResponse response) {
        GetReposByKeysRequest requestModel = request.getImodel();
        validateRequest(requestModel);

        Map<String, Boolean> readPermissionCache = new HashMap<>();
        Map<String, Boolean> deployPermissionCache = new HashMap<>();
        List<String> repoKeys = requestModel.getRepoKeys();
        List<RepoKeyType> repoKeyTypes =
                repositoryConfigService.getRepoConfigsByKeys(repoKeys).stream().sorted(COMPARATOR_LEXICOGRAPHIC)
                        .map(repoConfig -> toRepoKeyTypeWithPermissionCheck(repoConfig, readPermissionCache,
                                deployPermissionCache)).filter(Objects::nonNull).toList();
        SetMeUpModel setMeUpModel = new SetMeUpModel(repoKeyTypes);

        response.iModel(setMeUpModel);
    }

    private void validateRequest(GetReposByKeysRequest requestModel) {
        if (requestModel == null || CollectionUtils.isEmpty(requestModel.getRepoKeys())) {
            throw new BadRequestException("Missing or empty repository keys list");
        }
    }
}