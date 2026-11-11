package org.artifactory.ui.rest.service.utils.repositories.repodata;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.LocalRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Support;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;

@Slf4j
@Component
public class UserRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String USER_QUERY_PARAM = "user";
    private static final String SOURCE_REPO_KEY = "sourceRepoKey";
    private static final String ACTION = "action";

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        String keySearch = getKeySearch(request);
        long limit = getLimit(request);
        Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
        List<RepoConfig> repoConfigs = getRepoConfigs(request, projectKey, keySearch);
        Stream<RepoConfig> orderedRepoConfigs = repoConfigs.stream().sorted(comparator);
        List<RepoKeyType> repoKeyTypes = getRepoDataForUser(orderedRepoConfigs).limit(limit).toList();
        repositoriesData.setRepoTypesList(repoKeyTypes);
        response.iModel(repositoriesData);
    }

    private List<RepoConfig> getRepoConfigs(ArtifactoryRestRequest<T> request, String projectKey, String keySearch) {
        String sourceRepoKey = request.getQueryParamByKey(SOURCE_REPO_KEY);
        Action action = getActionFromRequest(request);

        if (isRestoreAction(action) || isSourceRepoKeyOrActionInvalid(sourceRepoKey, action)) {
            return getDefaultUserRepoConfigs(projectKey, keySearch);
        }

        RepoConfig sourceRepoConfig = getSourceRepoConfig(sourceRepoKey);
        if (isMoveActionWithBuildInfoPackage(action, sourceRepoConfig)) {
            return getRepoConfigsForMoveActionWithBuildInfo(projectKey, keySearch);
        }

        return getRepoConfigsForSourceRepo(sourceRepoConfig, projectKey, keySearch);
    }

    private boolean isRestoreAction(Action action) {
        return action == Action.RESTORE;
    }

    private List<RepoConfig> getDefaultUserRepoConfigs(String projectKey, String keySearch) {
        List<RepoConfig> repoConfigs = repoService.getRepoConfigsBySearchPattern(
                Set.of(LOCAL, FEDERATED), projectKey, keySearch);
        addSupportBundleRepoConfigIfNeeded(repoConfigs, projectKey, keySearch);
        return repoConfigs;
    }

    private RepoConfig getSourceRepoConfig(String sourceRepoKey) {
        RepoConfig sourceRepoConfig = repoService.repoConfigByKey(sourceRepoKey);
        if (sourceRepoConfig == null) {
            log.warn("Could not find source repo with key '{}'", sourceRepoKey);
            throw new BadRequestException("Could not find source repo with key " + sourceRepoKey);
        }
        return sourceRepoConfig;
    }

    private List<RepoConfig> getRepoConfigsForMoveActionWithBuildInfo(String projectKey, String keySearch) {
        return repoService.getRepoConfigsBySearchPattern(
                Set.of(LOCAL, FEDERATED), Set.of(BuildInfo), projectKey, keySearch);
    }

    private List<RepoConfig> getRepoConfigsForSourceRepo(RepoConfig sourceRepoConfig, String projectKey,
            String keySearch) {
        Set<RepoType> repoTypes = determineRepoTypes(sourceRepoConfig);
        List<RepoConfig> repoConfigs = repoService.getRepoConfigsBySearchPattern(
                Set.of(LOCAL, FEDERATED), repoTypes, projectKey, keySearch);
        if (repoTypes.contains(Support)) {
            addSupportBundleRepoConfigIfNeeded(repoConfigs, projectKey, keySearch);
        }
        return repoConfigs;
    }

    private boolean isSourceRepoKeyOrActionInvalid(String sourceRepoKey, Action action) {
        return StringUtils.isBlank(sourceRepoKey) || action == null;
    }

    private boolean isMoveActionWithBuildInfoPackage(Action action, RepoConfig sourceRepoConfig) {
        return action == Action.MOVE && sourceRepoConfig.getPackageType() == BuildInfo;
    }

    @VisibleForTesting
    Set<RepoType> determineRepoTypes(RepoConfig sourceRepoConfig) {
        RepoType sourceRepoPackageType = sourceRepoConfig.getPackageType();
        if (sourceRepoPackageType.isMavenGroup()) {
            Set<RepoType> mavenGroupWithGeneric = EnumSet.copyOf(RepoType.getMavenGroupTypes());
            mavenGroupWithGeneric.add(Generic);
            return mavenGroupWithGeneric;
        }
        return EnumSet.of(Generic, sourceRepoPackageType);
    }

    private void addSupportBundleRepoConfigIfNeeded(List<RepoConfig> repoConfigs, String projectKey, String keySearch) {
        if (!ProjectsUtils.isDefaultProject(projectKey)) {
            return;
        }
        if (StringUtils.isBlank(keySearch) || SUPPORT_BUNDLE_REPO_KEY.contains(keySearch)) {
            LocalRepo<?, ?> supportBundle = repoService.localOrFederatedRepositoryByKey(SUPPORT_BUNDLE_REPO_KEY);
            if (supportBundle != null) {
                repoConfigs.add(supportBundle.getRepoConfig());
            }
        }
    }

    private Stream<RepoKeyType> getRepoDataForUser(Stream<RepoConfig> repoConfigs) {
        if (!securityService.isAdmin()) {
            repoConfigs = repoConfigs.filter(this::hasDeployPermission);
        }
        return repoConfigs.filter(repoConfig -> !isHiddenRepo(repoConfig.getKey()))
                .map(repoConfig -> new RepoKeyType(repoConfig.getPackageType(), repoConfig.getKey()));
    }

    private Action getActionFromRequest(ArtifactoryRestRequest<T> request) {
        String actionParam = request.getQueryParamByKey(ACTION);
        return Action.fromString(actionParam);
    }

    @Override
    public String getHandledQueryParam() {
        return USER_QUERY_PARAM;
    }

    @AllArgsConstructor
    @Getter
    enum Action {
        MOVE("move"),
        COPY("copy"),
        RESTORE("restore");
        private final String value;

        public static Action fromString(String value) {
            for (Action action : Action.values()) {
                if (action.value.equalsIgnoreCase(value)) {
                    return action;
                }
            }
            return null;
        }
    }
}