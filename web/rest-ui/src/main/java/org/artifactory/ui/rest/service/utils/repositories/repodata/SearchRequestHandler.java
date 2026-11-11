package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.AUTO_COMPLETE_SORT;
import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.LOCAL_CACHE_SORT;

@Slf4j
@Component
public class SearchRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String SEARCH_QUERY_PARAM = "search";

    @Override
    public String getHandledQueryParam() {
        return SEARCH_QUERY_PARAM;
    }

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        String keySearch = getKeySearch(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        List<RepoConfig> repoConfigs =
                repoService.getRepoConfigsBySearchPattern(Set.of(LOCAL, FEDERATED), projectKey,
                        keySearch);
        addLocalCacheRepoConfigs(repoConfigs, projectKey, keySearch);
        long limit = getLimit(request);
        List<RepoKeyType> repoKeyTypes =
                repoConfigs.stream()
                        .filter(repoConfig -> !isHiddenRepo(repoConfig.getKey()))
                        .sorted(comparator)
                        .filter(this::hasReadPermission)
                        .map(LocalConfig.class::cast)
                        .map(localConfig -> toRepoKeyType(localConfig, fedAttrOptIn))
                        .limit(limit)
                        .toList();
        repositoriesData.setRepoTypesList(repoKeyTypes);
        response.iModel(repositoriesData);
    }

    @Override
    protected Comparator<RepoConfig> getRepoConfigComparator(ArtifactoryRestRequest<T> request, String keySearch) {
        Comparator<RepoConfig> comparator;
        if (isAutoComplete(request)) {
            comparator = SortStrategyFactory.getComparator(AUTO_COMPLETE_SORT, keySearch);
        } else {
            comparator = SortStrategyFactory.getComparator(LOCAL_CACHE_SORT);
        }
        return comparator;
    }

    private RepoKeyType toRepoKeyType(LocalConfig localConfig, boolean fedAttrOptIn) {
        String type;
        if (localConfig.isCache()) {
            type = REMOTE.typeNameLowercase();
        } else if (fedAttrOptIn && localConfig.getRepoType() == FEDERATED) {
            type = LOCAL.typeNameLowercase();
        } else {
            type = localConfig.getRepoType().typeNameLowercase();
        }
        return new RepoKeyType(type, localConfig.getKey());
    }
}