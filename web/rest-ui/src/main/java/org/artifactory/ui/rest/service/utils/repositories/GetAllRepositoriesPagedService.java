package org.artifactory.ui.rest.service.utils.repositories;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepositoryConfigServiceHelper;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.utils.RequestUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueIndexPage;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.jfrog.commons.continues.util.PagingUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.jfrog.commons.continues.util.ContinueResultIndexUtils.getLastIndex;

/**
 * @author Dvir Twina
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetAllRepositoriesPagedService implements RestService {


    private RepositoryService repoService;
    private RepositoryConfigService repositoryConfigService;

    private String projectKey;
    private String repoSearchStr;
    private List<String> packageTypeSearchStrs;

    @Autowired
    public void setRepoService(RepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        projectKey = RequestUtils.getProjectKeyFromRequest(request);
        repoSearchStr = request.getQueryParamByKey("repoSearchStr");
        packageTypeSearchStrs = request.getQueryParamListByKey("repoType");
        boolean isAnyLocalRepo = Boolean.parseBoolean(request.getQueryParamByKey("isAnyLocalRepo"));
        boolean isAnyRemoteRepo = Boolean.parseBoolean(request.getQueryParamByKey("isAnyRemoteRepo"));
        boolean isAnyFederatedRepo = Boolean.parseBoolean(request.getQueryParamByKey("isAnyFederatedRepo"));
        String continueState = request.getQueryParamByKey("continueState");

        if (StringUtils.isBlank(continueState)) {
            continueState = "0";
        }
        Long limit = StringUtils.isNotBlank(request.getQueryParamByKey("limit")) ?
                Long.parseLong(request.getQueryParamByKey("limit")) : null;

        ContinueIndexPage cip = new ContinueIndexPage(Long.parseLong(continueState), limit);
        List<FetchFunction<RepoKeyType>> functions = generateFetchFunctions(
                isAnyLocalRepo, isAnyRemoteRepo, isAnyFederatedRepo);

        Long totalRequiredRepos = null;
        if (Long.parseLong(continueState) == 0) {
            //add to response a total amount of requested repos
            totalRequiredRepos = getTotalRequiredRepos(isAnyLocalRepo, isAnyRemoteRepo, isAnyFederatedRepo);
        }
        ContinueResult<RepoKeyType> cr = PagingUtils.getPagingFromMultipleFunctions(cip, functions);

        cr.setTotalRequiredDataAmount(totalRequiredRepos);
        response.iModel(cr);
    }

    private List<FetchFunction<RepoKeyType>> generateFetchFunctions(
            boolean isAnyLocalRepo, boolean isAnyRemoteRepo, boolean isAnyFederatedRepo) {

        if (!isAnyLocalRepo && !isAnyRemoteRepo && !isAnyFederatedRepo) {
            return List.of(new FetchFunction<>(this::getLocalRemoteAndFederatedRepos,
                    (long) repoService.getLocalFederatedAndRemoteRepoConfigsByProject(projectKey).size()));
        } else {
            Function<String, Boolean> projectFilter = repoService.createProjectReposFilter(projectKey);
            List<FetchFunction<RepoKeyType>> functions = new ArrayList<>();
            if (isAnyLocalRepo) {
                functions.add(new FetchFunction<>(this::getLocalRepos,
                        (long) repoService.getLocalRepoConfigsByProject(projectKey).size()));
            }
            if (isAnyRemoteRepo) {
                functions.add(new FetchFunction<>(this::getRemoteRepos,
                        (long) repositoryConfigService.getRepoConfigKeysByProjectAndType(projectFilter, Set.of(REMOTE))
                                .size()));
            }
            if (isAnyFederatedRepo) {
                functions.add(new FetchFunction<>(this::getFederatedRepos,
                        (long) repositoryConfigService.getRepoConfigKeysByProjectAndType(projectFilter,
                                        Set.of(FEDERATED))
                                .size()));
            }

            return functions;
        }
    }

    private ContinueResult<RepoKeyType> getLocalRemoteAndFederatedRepos(Integer skip, Integer limit) {
        List<RealRepoConfig> allRequiredLocalRemoteAndFederatedRepoConfigs =
                repoService.getLocalFederatedAndRemoteRepoConfigsByProject(projectKey);
        List<RealRepoConfig> offsetRepoConfigs =
                StreamSupportUtils.stream(allRequiredLocalRemoteAndFederatedRepoConfigs)
                        .skip(skip)
                        .filter(repo -> requiredReposFilter().apply(repo))
                        .limit(limit)
                        .toList();
        int lastIndex = getLastIndex(allRequiredLocalRemoteAndFederatedRepoConfigs, offsetRepoConfigs, limit);
        return new ContinueResult<>(lastIndex, getRepoKeyType(offsetRepoConfigs));
    }

    private ContinueResult<RepoKeyType> getLocalRepos(Integer skip, Integer limit) {
        List<LocalConfig> allRequiredLocalRepoConfigs =
                repoService.getLocalRepoConfigsByProject(projectKey);
        List<LocalConfig> offsetRepoConfigs = StreamSupportUtils.stream(allRequiredLocalRepoConfigs)
                .skip(skip)
                .filter(repo -> requiredRepoConfigsFilter().apply(repo))
                .limit(limit)
                .toList();

        int lastIndex = getLastIndex(allRequiredLocalRepoConfigs, offsetRepoConfigs, limit);
        return new ContinueResult<>(lastIndex, getRepoConfigKeyType(offsetRepoConfigs));
    }

    private ContinueResult<RepoKeyType> getRemoteRepos(Integer skip, Integer limit) {
        return getRepos(skip, limit, REMOTE);
    }

    private ContinueResult<RepoKeyType> getFederatedRepos(Integer skip, Integer limit) {
        return getRepos(skip, limit, FEDERATED);
    }

    private ContinueResult<RepoKeyType> getRepos(Integer skip, Integer limit,
            RepoDetailsType repoDetailsType) {
        List<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByProjectAndType(repoService.createProjectReposFilter(projectKey),
                        Set.of(repoDetailsType));
        List<RepoConfig> offsetRepoConfigs = StreamSupportUtils.stream(repoConfigs)
                .skip(skip)
                .filter(repo -> requiredReposFilter().apply(repo))
                .limit(limit)
                .toList();

        int lastIndex = getLastIndex(repoConfigs, offsetRepoConfigs, limit);
        return new ContinueResult<>(lastIndex, getRepoKeyType(offsetRepoConfigs));
    }

    private Function<RepoConfig, Boolean> specialRepoConfigFilter() {
        return repoDesc ->
                RepositoryConfigServiceHelper.isInfoRepo(repoDesc.getPackageType())
                        || ReleaseBundles.equals(repoDesc.getPackageType())
                        || SystemRepos.isInTransitRepo(repoDesc.getKey())
                        || SystemRepos.isObservabilityRepo(repoDesc.getKey())
                        || SystemRepos.isFullSyncRepo(repoDesc.getKey())
                        || SystemRepos.isGridInfoRepo(repoDesc.getKey());
    }

    private Function<RepoConfig, Boolean> specialRepoFilter() {
        return repoConfig -> RepositoryConfigServiceHelper.isInfoRepo(repoConfig.getPackageType())
                || ReleaseBundles.equals(repoConfig.getPackageType())
                || SystemRepos.isInTransitRepo(repoConfig.getKey())
                || SystemRepos.isObservabilityRepo(repoConfig.getKey())
                || SystemRepos.isFullSyncRepo(repoConfig.getKey())
                || SystemRepos.isGridInfoRepo(repoConfig.getKey());
    }

    private Function<RepoConfig, Boolean> requiredReposFilter() {
        return repo -> !specialRepoFilter().apply(repo)
                && repo.getKey().contains(repoSearchStr)
                && (packageTypeSearchStrs.isEmpty() || packageTypeSearchStrs.contains(repo.getPackageType().getType()));
    }

    private Function<RepoConfig, Boolean> requiredRepoConfigsFilter() {
        return repo -> !specialRepoConfigFilter().apply(repo)
                && repo.getKey().contains(repoSearchStr)
                && (packageTypeSearchStrs.isEmpty() || packageTypeSearchStrs.contains(repo.getPackageType().getType()));
    }

    private List<RepoKeyType> getRepoKeyType(@NonNull List<? extends RepoConfig> filteredRepoConfigs) {
        List<RepoKeyType> repos = new ArrayList<>();
        filteredRepoConfigs
                .forEach(repoConfig -> {
                    if (repoConfig instanceof FederatedRepoConfig) {
                        repos.add(new RepoKeyType(FEDERATED.typeNameLowercase(), repoConfig.getKey()));
                    } else if (repoConfig instanceof LocalRepoConfig) {
                        repos.add(new RepoKeyType(LOCAL.typeNameLowercase(), repoConfig.getKey()));
                    } else if (repoConfig instanceof RemoteRepoConfig) {
                        repos.add(new RepoKeyType(REMOTE.typeNameLowercase(), repoConfig.getKey()));
                    }
                });
        return repos;
    }

    private List<RepoKeyType> getRepoConfigKeyType(@NonNull List<? extends LocalConfig> localConfigs) {
        List<RepoKeyType> repos = new ArrayList<>();
        localConfigs
                .forEach(localConfig -> {
                    if (localConfig instanceof FederatedRepoConfig) {
                        repos.add(new RepoKeyType(FEDERATED.typeNameLowercase(), localConfig.getKey()));
                    } else if (localConfig instanceof LocalRepoConfig) {
                        repos.add(new RepoKeyType(LOCAL.typeNameLowercase(), localConfig.getKey()));
                    } else if (localConfig instanceof LocalCacheRepoConfig || localConfig instanceof RemoteRepoConfig) {
                        repos.add(new RepoKeyType(REMOTE.typeNameLowercase(), localConfig.getKey()));
                    }
                });
        return repos;
    }

    private long getTotalRequiredRepos(boolean isAnyLocalRepo, boolean isAnyRemoteRepo, boolean isAnyFederatedRepo) {
        if (!isAnyLocalRepo && !isAnyRemoteRepo && !isAnyFederatedRepo) {
            return repoService.getLocalFederatedAndRemoteRepoConfigsByProject(projectKey).stream()
                    .filter(repo -> requiredReposFilter().apply(repo)).count();
        } else {
            long sum = 0;
            if (isAnyLocalRepo) {
                sum += repoService.getLocalRepoConfigsByProject(projectKey).stream()
                        .filter(repo -> requiredRepoConfigsFilter().apply(repo)).count();
            }
            if (isAnyRemoteRepo) {
                sum += repositoryConfigService.getRepoConfigsByProjectAndType(
                                repoService.createProjectReposFilter(projectKey), Set.of(REMOTE))
                        .stream()
                        .filter(repo -> requiredReposFilter().apply(repo))
                        .count();
            }
            if (isAnyFederatedRepo) {
                sum += repositoryConfigService.getRepoConfigsByProjectAndType(
                                repoService.createProjectReposFilter(projectKey), Set.of(FEDERATED))
                        .stream()
                        .filter(repo -> requiredReposFilter().apply(repo))
                        .count();
            }
            return sum;
        }
    }
}
