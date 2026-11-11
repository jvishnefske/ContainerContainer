package org.artifactory.ui.rest.service.onboarding;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.remote.RemoteCacheRepoTypeConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.onboarding.ReposListByPackageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for getting a repositories list according to specific package type
 *
 * @author Ron Malka
 */

@Slf4j
@Component
public class ReposListByPackageTypeService implements RestService<String> {

    // If the entire system repos count is larger than 100, we would skip the nested virtual repositories exclusion
    // calculation.
    public static final int EXCLUDE_NESTED_REPOS_LIMIT = 100;
    private RepositoryService repositoryService;

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String packageTypeStr = request.getImodel();
        if (StringUtils.isBlank(packageTypeStr)) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
            log.debug("PackageType is missing");
            return;
        }
        RepoType packageType = RepoType.fromType(packageTypeStr);
        boolean excludeReposInVirtual = Boolean.parseBoolean(request.getQueryParamByKey("excludeReposInVirtual"));
        ReposListByPackageType reposListByPackageType =
                getRepoTypeToReposKeysMap(packageType, excludeReposInVirtual);
        response.iModel(reposListByPackageType);
        response.responseCode(HttpStatus.SC_OK);
    }

    private ReposListByPackageType getRepoTypeToReposKeysMap(RepoType packageType, boolean excludeReposInVirtual) {
        List<RepoConfig> repoConfigs = getAllRepoConfigsWithoutCachedRepos(packageType);
        ReposListByPackageType reposListByPackageType = new ReposListByPackageType();
        reposListByPackageType.setHideNestedReposSupported(repoConfigs.size() <= EXCLUDE_NESTED_REPOS_LIMIT);
        if (excludeReposInVirtualIsNeeded(repoConfigs.size(), excludeReposInVirtual)) {
            log.debug("exclude repos in virtual is needed, getting results without repos in virtual repos");
            reposListByPackageType.setRepoTypeToReposKeysMap(getRepoKeysMapWithoutNestedRepos(repoConfigs));
        } else {
            log.debug("exclude repos in virtual is not needed, getting results with all repos");
            reposListByPackageType.setRepoTypeToReposKeysMap(getAllRepoKeysMap(repoConfigs));
        }
        return reposListByPackageType;
    }

    @NonNull
    private List<RepoConfig> getAllRepoConfigsWithoutCachedRepos(RepoType packageType) {
        return repositoryService
                .getRepoConfigsByPackageType(packageType)
                .stream()
                //we don't need to include remote-cache repos
                .filter(repoConfig -> !(repoConfig.getRepoTypeConfig() instanceof RemoteCacheRepoTypeConfig))
                .toList();
    }

    /**
     * We need to sort the results so the virtuals repos be first
     */
    private Map<RepoDetailsType, List<String>> getAllRepoKeysMap(List<RepoConfig> repoConfigs) {
        Map<RepoDetailsType, List<String>> results = new LinkedHashMap<>();
        List<RepoConfig> realRepoConfigs = new ArrayList<>();
        List<RepoConfig> virtualRepoConfigs = new ArrayList<>();
        repoConfigs.forEach(repoConfig -> separateRepoConfigs(repoConfig, realRepoConfigs, virtualRepoConfigs));
        results.put(RepoDetailsType.VIRTUAL, virtualRepoConfigs.stream().map(RepoConfig::getKey).toList());
        realRepoConfigs.forEach(
                repoConfig -> computeRepoTypeToReposKeysMap(results, repoConfig)
        );
        return results;
    }

    private void separateRepoConfigs(RepoConfig repoConfig, List<RepoConfig> realRepoConfigs,
            List<RepoConfig> virtualRepoConfigs) {
        if (repoConfig.isReal()) {
            realRepoConfigs.add(repoConfig);
        } else {
            virtualRepoConfigs.add(repoConfig);
        }
    }

    /**
     * @return Map of repo keys aggregated by their type excluding nested repositories (real repos that are
     * aggregated in virtual repositories).
     */
    private Map<RepoDetailsType, List<String>> getRepoKeysMapWithoutNestedRepos(List<RepoConfig> repoConfigs) {
        Set<String> virtualsReposSet = new HashSet<>();
        List<RepoConfig> realRepos = new ArrayList<>();
        //need to keep the virtual repositories first
        Map<RepoDetailsType, List<String>> repoTypeToReposKeysMap = new LinkedHashMap<>();
        repoConfigs.forEach(repoConfig ->
                handleRepoConfigWithoutReposInVirtual(repoConfig,
                        virtualsReposSet,
                        repoTypeToReposKeysMap,
                        realRepos));
        realRepos.forEach(repoConfig ->
                addIndependentRealReposToMap(repoTypeToReposKeysMap, virtualsReposSet, repoConfig));
        return repoTypeToReposKeysMap;
    }

    /**
     * For each repo config, check if it is virtual.
     * If it is, add all the included repositories keys of the virtual one to the virtualReposSet,
     * and include the virtual repository in the results map.
     * If it is not virtual, add the repository to the list of real repositories.
     */
    private void handleRepoConfigWithoutReposInVirtual(RepoConfig repoConfig, Set<String> virtualsReposSet,
            Map<RepoDetailsType, List<String>> repoTypeToReposKeysMap, List<RepoConfig> realRepos) {
        if (repoConfig.isReal()) {
            realRepos.add(repoConfig);
        } else {
            VirtualRepositoryRepoTypeConfig repoTypeConfig =
                    (VirtualRepositoryRepoTypeConfig) repoConfig.getRepoTypeConfig();
            virtualsReposSet.addAll(repoTypeConfig.getRepositoryRefs());
            computeRepoTypeToReposKeysMap(repoTypeToReposKeysMap, repoConfig);
        }
    }

    /**
     * For each real repository, check if its key exists in the virtualReposSet.
     * If it does, it means the repository is already included in a virtual repository
     * and should be excluded from the results map.
     * If it does not exist in the virtualReposSet, add it to the results map.
     */
    private void addIndependentRealReposToMap(Map<RepoDetailsType, List<String>> repoTypeToReposKeysMap,
            Set<String> virtualsReposSet,
            RepoConfig repoConfig) {
        if (!virtualsReposSet.contains(repoConfig.getKey())) {
            computeRepoTypeToReposKeysMap(repoTypeToReposKeysMap, repoConfig);
        }
    }

    private void computeRepoTypeToReposKeysMap(Map<RepoDetailsType, List<String>> repoTypeToReposKeysMap,
            RepoConfig repoConfig) {
        repoTypeToReposKeysMap.computeIfAbsent(repoConfig.getRepoType(),
                key -> new ArrayList<>()).add(repoConfig.getKey());
    }

    private boolean excludeReposInVirtualIsNeeded(int size, boolean excludeReposInVirtual) {
        return excludeReposInVirtual && size <= EXCLUDE_NESTED_REPOS_LIMIT;
    }
}
