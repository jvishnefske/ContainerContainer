package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;

@Slf4j
@Component
public class PermissionRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String PERMISSION_QUERY_PARAM = "permission";

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        String keySearch = getKeySearch(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        long limit = getLimit(request);
        List<RepoKeyType> repoKeyTypes =
                repoService.getRepoConfigsBySearchPattern(Set.of(LOCAL, FEDERATED, REMOTE), projectKey,
                                keySearch).stream()
                        .filter(repo -> !isInfoRepo(repo.getPackageType()))
                        .filter(repo -> !ReleaseBundles.equals(repo.getPackageType()))
                        .filter(repo -> !isHiddenRepo(repo.getKey()))
                        .sorted(comparator)
                        .filter(this::hasReadPermission)
                        .map(repoConfig -> toRepoKeyType(repoConfig, fedAttrOptIn))
                        .limit(limit)
                        .toList();
        repositoriesData.setRepoTypesList(repoKeyTypes);
        response.iModel(repositoriesData);
    }

    private RepoKeyType toRepoKeyType(RepoConfig repoConfig, boolean fedAttrOptIn) {
        if (fedAttrOptIn && repoConfig.getRepoType() == FEDERATED) {
            return new RepoKeyType(LOCAL.typeNameLowercase(), repoConfig.getKey());
        }
        return new RepoKeyType(repoConfig.getRepoType().typeNameLowercase(), repoConfig.getKey());
    }

    @Override
    public String getHandledQueryParam() {
        return PERMISSION_QUERY_PARAM;
    }
}