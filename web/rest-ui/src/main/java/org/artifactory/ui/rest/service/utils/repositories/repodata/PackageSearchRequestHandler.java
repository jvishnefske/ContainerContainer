package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;
import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.AUTO_COMPLETE_SORT;
import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.LOCAL_CACHE_SORT;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;

@Slf4j
@Component
public class PackageSearchRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String PACKAGE_SEARCH_QUERY_PARAM = "packageSearch";
    @Getter(lazy = true)
    private static final Map<RepoType, Boolean> repoTypeSupportRemoteCache = initRepoTypeSupportRemoteCache();

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        String keySearch = getKeySearch(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        long limit = getLimit(request);
        List<RepoConfig> repoConfigs =
                repoService.getRepoConfigsBySearchPattern(Set.of(LOCAL, FEDERATED), projectKey,
                        keySearch);
        addLocalCacheRepoConfigs(repoConfigs, projectKey, keySearch);
        Stream<LocalConfig> repoConfigsStream = repoConfigs.stream()
                .map(LocalConfig.class::cast);
        List<RepoKeyType> packageSearchRepos =
                getPackageSearchRepos(repoConfigsStream, comparator, fedAttrOptIn).limit(limit).toList();
        repositoriesData.setRepoTypesList(packageSearchRepos);
        response.iModel(repositoriesData);
    }

    private Stream<RepoKeyType> getPackageSearchRepos(Stream<LocalConfig> repoConfigs,
            Comparator<RepoConfig> comparator, boolean fedAttrOptIn) {
        Map<RepoType, List<LocalConfig>> repoTypeListMap = getLocalRepoDescriptorByTypeMap(repoConfigs);
        return repoTypeListMap.values().stream()
                .flatMap(List::stream)
                .sorted(comparator)
                .filter(this::hasReadPermission)
                .map(localConfig -> createRepoKeyType(toRepoType(localConfig, fedAttrOptIn), localConfig));
    }

    private String toRepoType(LocalConfig localConfig, boolean fedAttrOptIn) {
        if (localConfig.isCache()) {
            return REMOTE.typeNameLowercase();
        } else if (fedAttrOptIn && localConfig.getRepoType() == FEDERATED) {
            return LOCAL.typeNameLowercase();
        } else {
            return localConfig.getRepoType().typeNameLowercase();
        }
    }

    /**
     * Returns a map of LocalConfig lists, grouped by RepoType, for the given project.
     * For RepoTypes that support remote cache ({@see PackageSearchType}), it will also include
     * the LocalCacheRepoConfig cache for that type.
     *
     * @param projectKey the key of the project for which to retrieve the local repo configs
     * @return a map where the keys are RepoType and the values are lists of LocalConfig
     */
    private EnumMap<RepoType, List<LocalConfig>> getLocalRepoDescriptorByTypeMap(Stream<LocalConfig> repoConfigs) {
        return repoConfigs.filter(localConfig -> !isInfoRepo(localConfig.getPackageType()) &&
                        !isHiddenRepo(localConfig.getKey()))
                .filter(localConfig -> {
                    RepoType repoType = localConfig.getPackageType();
                    // If the repoType does not exist in the map, it defaults to null as the repo type is not
                    // supported by PackageSearchType.
                    Boolean supportRemoteCache = getRepoTypeSupportRemoteCache().getOrDefault(repoType,
                            repoType.isMavenGroup() ? getRepoTypeSupportRemoteCache().get(Maven) : null);
                    return (supportRemoteCache != null && (!localConfig.isCache() || supportRemoteCache));
                })
                .collect(
                        Collectors.groupingBy(LocalConfig::getPackageType, () -> new EnumMap<>(RepoType.class),
                                Collectors.toList()));
    }

    private RepoKeyType createRepoKeyType(String type, LocalConfig localConfig) {
        if (localConfig.getPackageType().isDockerGroup()) {
            return new RepoKeyType(type, localConfig.getPackageType(), localConfig.getKey(),
                    localConfig.getPackageTypeConfig().get(DOCKER_API_VERSION.getKey()));
        } else {
            return new RepoKeyType(type, localConfig.getPackageType(), localConfig.getKey());
        }
    }

    private static Map<RepoType, Boolean> initRepoTypeSupportRemoteCache() {
        return Arrays.stream(PackageSearchCriteria.PackageSearchType.values())
                .collect(Collectors.toMap(
                        PackageSearchCriteria.PackageSearchType::getRepoType,
                        PackageSearchCriteria.PackageSearchType::isRemoteCachesProps,
                        (existing, replacement) -> existing,
                        () -> new EnumMap<>(RepoType.class)));
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

    @Override
    public String getHandledQueryParam() {
        return PACKAGE_SEARCH_QUERY_PARAM;
    }
}