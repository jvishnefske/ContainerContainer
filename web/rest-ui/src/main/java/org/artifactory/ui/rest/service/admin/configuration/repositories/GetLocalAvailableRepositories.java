package org.artifactory.ui.rest.service.admin.configuration.repositories;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepoKeyAutoCompleteComparator;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;

@Component
@RequiredArgsConstructor
public class GetLocalAvailableRepositories implements RestService<Object> {

    private static final String PROJECT_KEY_PARAM = "projectKey";
    private static final String SEARCH_KEY_PARAM = "searchKey";
    private static final String LIMIT_PARAM = "limit";

    private final AuthorizationService authorizationService;
    private final RepositoryConfigService repositoryConfigService;

    @Override
    public void execute(ArtifactoryRestRequest<Object> request, RestResponse response) {
        long limit = getLimitOrDefault(request);
        String keyToSearch = getKeyToSearch(request);
        String projectKey = getProjectKey(request);
        List<String> localRepos = getLocalRepos(projectKey, keyToSearch, limit);
        response.iModel(localRepos);
    }

    @VisibleForTesting
    List<String> getLocalRepos(String projectKey, String keyToSearch, long limit) {
        Stream<RepoConfig> localRepos = repositoryConfigService.getRepoConfigsBySearchPattern(
                Set.of(RepoDetailsType.LOCAL), projectKey, keyToSearch)
                        .stream()
                        .filter(repoConfig -> !isInfoOrHiddenRepo(repoConfig));
        if (StringUtils.isNotBlank(keyToSearch)) {
            localRepos = localRepos.sorted(new RepoKeyAutoCompleteComparator(keyToSearch));
        }
        return localRepos
                .map(RepoConfig::getKey)
                .filter(authorizationService::userHasPermissionsOnRepositoryRoot)
                .limit(limit)
                .toList();
    }

    private boolean isInfoOrHiddenRepo(RepoConfig repoConfig) {
        return isInfoRepo(repoConfig.getPackageType())
                || isHiddenRepo(repoConfig.getKey(),false);
    }

    private String getKeyToSearch(ArtifactoryRestRequest<Object> request) {
        return request.getQueryParamByKey(SEARCH_KEY_PARAM);
    }

    private String getProjectKey(ArtifactoryRestRequest<Object> request) {
        return request.getQueryParamByKey(PROJECT_KEY_PARAM);
    }

    private Long getLimitOrDefault(ArtifactoryRestRequest<Object> request) {
        String limitParam = request.getQueryParamByKey(LIMIT_PARAM);
        if (StringUtils.isBlank(limitParam)) {
            return ConstantValues.getLocalAvailableReposDefaultLimit.getLong();
        }
        try {
            return Long.parseLong(limitParam);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid limit value: " + limitParam);
        }
    }
}