package org.artifactory.ui.rest.service.setmeup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepoKeyAutoCompleteComparator;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.READ;

@Slf4j
@AllArgsConstructor
public abstract class BaseSetMeUpHandler<T> implements RestService<T> {
    public static final String PACKAGE_TYPE = "packageType";
    public static final String REPO_KEY = "repoKey";
    public static final String AUTO_COMPLETION = "autoCompletion";
    public static final String KEY_SEARCH = "keySearch";
    public static final String LIMIT = "limit";
    static final Comparator<RepoConfig> COMPARATOR_LEXICOGRAPHIC = Comparator.comparing(RepoConfig::getKey);
    protected static final Set<RepoDetailsType> REPO_DETAILS_TYPES = Set.of(LOCAL, FEDERATED, REMOTE, VIRTUAL);
    protected final AuthorizationService authService;
    protected final RepositoryConfigService repositoryConfigService;
    protected final ProjectsService projectsService;

    protected Stream<RepoConfig> getRepoConfigs(Set<RepoType> packageTypes, String projectKey, String keySearch) {
        return repositoryConfigService.getRepoConfigsBySearchPattern(REPO_DETAILS_TYPES, packageTypes, projectKey,
                        keySearch)
                .stream()
                .filter(this::isValidRepoConfig);
    }

    protected Comparator<RepoConfig> getComparator(ArtifactoryRestRequest<T> request, String keySearch) {
        if (isAutoComplete(request)) {
            return new RepoKeyAutoCompleteComparator(keySearch);
        }
        return COMPARATOR_LEXICOGRAPHIC;
    }

    protected RepoKeyType createRepoKeyType(RepoConfig repoConfig, boolean canRead, boolean canDeploy) {
        RepoKeyType repoKeyType = new RepoKeyType(repoConfig);
        if (repoConfig.getRepoType() == VIRTUAL) {
            repoKeyType.setIsDefaultDeploymentConfigured(isDefaultLocalConfigured((VirtualRepoConfig) repoConfig));
        }
        repoKeyType.setCanDeploy(canDeploy);
        repoKeyType.setCanRead(canRead);
        return repoKeyType;
    }

    private static boolean isDefaultLocalConfigured(VirtualRepoConfig repoConfig) {
        return repoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef() != null;
    }

    private boolean isAutoComplete(ArtifactoryRestRequest<T> request) {
        String autoCompleteParam = request.getQueryParamByKey(AUTO_COMPLETION);
        if (StringUtils.isNotBlank(autoCompleteParam)) {
            return Boolean.parseBoolean(autoCompleteParam);
        }
        return false;
    }

    protected long getLimit(ArtifactoryRestRequest<T> request) {
        String queryParamValue = request.getQueryParamByKey(LIMIT);
        if (StringUtils.isNotBlank(queryParamValue)) {
            return parseLimit(queryParamValue);
        }
        return getDefaultLimit();
    }

    protected boolean isValidRepoConfig(RepoConfig repoConfig) {
        return !repoConfig.getPackageType().isBuildInfo() &&
                !repoConfig.getPackageType().isPipeInfo() &&
                !SystemRepos.SystemRepoConfig.isInvisibleSystemRepo(repoConfig.getKey());
    }

    private long parseLimit(String queryParamValue) {
        try {
            return Long.parseLong(queryParamValue);
        } catch (NumberFormatException e) {
            return getDefaultLimit();
        }
    }

    private long getDefaultLimit() {
        long defaultLimit = ConstantValues.setMeUpDefaultLimit.getLong();
        return defaultLimit > 0 ? defaultLimit : Long.MAX_VALUE;
    }

    protected boolean hasReadOrDeployPermission(RepoConfig repoConfig, Map<String, Boolean> readPermissionCache,
            Map<String, Boolean> deployPermissionCache) {
        return authService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(), Set.of(READ), readPermissionCache) ||
                hasDeployPermission(repoConfig, deployPermissionCache);
    }

    protected boolean hasDeployPermission(RepoConfig repoConfig, Map<String, Boolean> permissionCache) {
        String repoKey = repoConfig.getKey();
        if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig) {
            String defaultDeploymentRepoRef = virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
            if (isBlank(defaultDeploymentRepoRef)) {
                return false;
            }
            repoKey = defaultDeploymentRepoRef;
        }
        return authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(DEPLOY), permissionCache);
    }

    protected RepoKeyType toRepoKeyTypeWithPermissionCheck(RepoConfig repoConfig,
            Map<String, Boolean> readPermissionCache, Map<String, Boolean> deployPermissionCache) {
        String repoKey = repoConfig.getKey();
        boolean canRead = authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), readPermissionCache);
        boolean canDeploy = hasDeployPermission(repoConfig, deployPermissionCache);
        if (canRead || canDeploy) {
            return createRepoKeyType(repoConfig, canRead, canDeploy);
        }
        return null;
    }
}