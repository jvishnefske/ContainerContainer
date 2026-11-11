package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

@Slf4j
@Component
public class DefaultRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    static final String NO_QUERY_PARAM = "";
    private static final String LOCAL_QUERY_PARAM = "local";

    @Override
    protected void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        boolean localOnly = Boolean.parseBoolean(request.getQueryParamByKey(LOCAL_QUERY_PARAM));
        String keySearch = getKeySearch(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        long limit = getLimit(request);
        Set<RepoDetailsType> repoTypes = localOnly ? Set.of(LOCAL, FEDERATED) : Set.of(LOCAL, FEDERATED, REMOTE);
        List<String> repoKeys =
                repoService.getRepoConfigsBySearchPattern(repoTypes, projectKey, keySearch).stream()
                        .sorted(comparator)
                        .map(RepoConfig::getKey)
                        .filter(this::hasReadOrDeployPermission)
                        .limit(limit)
                        .toList();
        repositoriesData.setRepoList(repoKeys);
        response.iModel(repositoriesData);
    }

    private boolean hasReadOrDeployPermission(String repoKey) {
        RepoPath repoPath = RepoPathFactory.create(repoKey);
        return authorizationService.canRead(repoPath) || authorizationService.canDeploy(repoPath);
    }

    @Override
    public String getHandledQueryParam() {
        return NO_QUERY_PARAM;
    }
}