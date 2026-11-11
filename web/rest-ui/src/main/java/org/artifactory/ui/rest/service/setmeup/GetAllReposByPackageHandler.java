package org.artifactory.ui.rest.service.setmeup;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This handler retrieves set of {@link RepoKeyType} for repositories of the specified {@link RepoType}
 * (path parameter) based on the user's permissions.
 * This handler provides the data for the "Set Me Up" feature in the UI.
 */
@Component
@Slf4j
public class GetAllReposByPackageHandler extends BaseSetMeUpHandler<Void> {

    public GetAllReposByPackageHandler(AuthorizationService authorizationService,
            RepositoryConfigService repositoryConfigService, ProjectsService projectsService) {
        super(authorizationService, repositoryConfigService, projectsService);
    }

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        String packageType = request.getPathParamByKey(PACKAGE_TYPE);
        long limit = getLimit(request);
        String keySearch = getKeySearch(request);
        Set<RepoType> repoTypes = getRepoTypes(packageType);
        Comparator<RepoConfig> comparator = getComparator(request, keySearch);
        Predicate<RepoConfig> virtualRepoVisibility =
                projectsService.createVirtualReposVisibilityFilter(projectKey);
        Stream<RepoConfig> repoConfigStream = getRepoConfigs(repoTypes, projectKey, keySearch)
                .filter(virtualRepoVisibility)
                .sorted(comparator);
        List<RepoKeyType> repoKeyTypes = toRepoKeyType(repoConfigStream, projectKey).limit(limit).toList();
        SetMeUpModel setMeUpModel = new SetMeUpModel(repoKeyTypes);
        response.iModel(setMeUpModel);
    }

    @VisibleForTesting
    Set<RepoType> getRepoTypes(String repoType) {
        validatePackageType(repoType);
        RepoType resolveRepoType = resolveRepoType(repoType);
        return determineRepoTypes(resolveRepoType);
    }

    private void validatePackageType(String packageType) {
        if (StringUtils.isBlank(packageType)) {
            log.debug("Missing package type");
            throw new BadRequestException("Missing package type");
        }
    }

    private RepoType resolveRepoType(String repoType) {
        try {
            return RepoType.fromTypeOrThrow(repoType);
        } catch (IllegalArgumentException e) {
            log.info("Invalid package type: {}", repoType);
            throw new BadRequestException("Invalid package type: " + repoType);
        }
    }

    private Set<RepoType> determineRepoTypes(RepoType repoType) {
        if (RepoType.getMavenGroupExcludeP2Types().contains(repoType)) {
            return RepoType.getMavenGroupExcludeP2Types();
        }
        return Set.of(repoType);
    }

    private Stream<RepoKeyType> toRepoKeyType(Stream<RepoConfig> repoConfigStream, String projectKey) {
        if (projectsService.isProjectAdmin(projectKey) && projectsService.canManageProjectResources(projectKey)) {
            return repoConfigStream.map(repoConfig -> createRepoKeyType(repoConfig, true, true));
        }
        Map<String, Boolean> readPermissionCache = new HashMap<>();
        Map<String, Boolean> deployPermissionCache = new HashMap<>();
        return repoConfigStream.map(repoConfig -> toRepoKeyTypeWithPermissionCheck(repoConfig, readPermissionCache,
                deployPermissionCache)).filter(Objects::nonNull);

    }

    private String getKeySearch(ArtifactoryRestRequest<Void> request) {
        return request.getQueryParamByKey(KEY_SEARCH);
    }
}