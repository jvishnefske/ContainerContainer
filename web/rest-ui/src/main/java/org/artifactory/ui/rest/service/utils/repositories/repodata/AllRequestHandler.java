package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;

@Slf4j
@Component
public class AllRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String ALL_QUERY_PARAM = "all";

    @Override
    protected void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        String keySearch = getKeySearch(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        Set<RepoDetailsType> repoTypes = fedAttrOptIn
                ? Set.of(LOCAL, REMOTE, RELEASE_BUNDLE, FEDERATED)
                : Set.of(LOCAL, REMOTE, RELEASE_BUNDLE);
        List<RepoConfig> repoConfigs =
                repoService.getRepoConfigsBySearchPattern(repoTypes, projectKey, keySearch);
        long limit = getLimit(request);
        List<RepoKeyType> repoKeyTypeList = repoConfigs.stream()
                .sorted(comparator)
                .filter(this::hasReadPermission)
                .map(repoConfig -> mapToRepoKeyType(repoConfig, fedAttrOptIn))
                .limit(limit)
                .toList();
        response.iModelList(repoKeyTypeList);
    }

    private RepoKeyType mapToRepoKeyType(RepoConfig repoConfig, boolean fedAttrOptIn) {
        if (fedAttrOptIn && repoConfig.getRepoType() == FEDERATED) {
            return new RepoKeyType(LOCAL.typeNameLowercase(), repoConfig.getKey());
        }
        if (repoConfig.getRepoType() == LOCAL) {
            return new RepoKeyType(repoConfig.getPackageType(), repoConfig.getKey());
        }
        return new RepoKeyType(repoConfig.getRepoType() == REMOTE ? REMOTE_REPO : RELEASE_BUNDLE_REPO,
                repoConfig.getKey());
    }

    @Override
    public String getHandledQueryParam() {
        return ALL_QUERY_PARAM;
    }
}