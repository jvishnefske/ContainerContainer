package org.artifactory.ui.rest.service.utils.repositories.repodata;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.AUTO_COMPLETE_SORT;
import static org.artifactory.ui.rest.service.utils.repositories.repodata.SortStrategyFactory.LEXICOGRAPHICALLY_SORT;

@Slf4j
abstract class AbstractGetRepositoriesDataHandler<T> implements GetRepositoriesDataHandler<T> {
    static final String AUTO_COMPLETION = "autoCompletion";
    static final String KEY_SEARCH = "keySearch";
    static final String LIMIT = "limit";
    AuthorizationService authorizationService;
    InternalRepositoryService repoService;
    SecurityService securityService;
    ProjectsService projectsService;

    protected boolean isFedAttrOptIn(ArtifactoryRestRequest<?> request) {
        return RequestUtils.isFedAttrOptIn(request);
    }

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response) {
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        handle(request, response, projectKey);
    }

    protected abstract void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey);

    protected boolean hasReadPermission(RepoConfig repoConfig) {
        return authorizationService.canRead(InternalRepoPathFactory.repoRootPath(repoConfig.getKey()));
    }

    protected boolean hasDeployPermission(RepoConfig repoConfig) {
        if (repoConfig == null) {
            return false;
        }
        if (repoConfig.isReal() && RepoType.BuildInfo.equals(repoConfig.getPackageType())) {
            // Special case for build repo since it moves files to the correct path automatically it's ok to show
            // deployment anywhere
            return authorizationService.hasBuildPermission(repoConfig.getKey(), ArtifactoryPermission.DEPLOY);
        }
        return authorizationService.canDeploy(InternalRepoPathFactory.create(repoConfig.getKey(), ""));
    }

    protected long getLimit(ArtifactoryRestRequest<T> request) {
        String queryParamValue = request.getQueryParamByKey(LIMIT);
        if (StringUtils.isNotBlank(queryParamValue)) {
            return parseLimit(queryParamValue);
        }
        return getDefaultLimit();
    }

    private long parseLimit(String queryParamValue) {
        try {
            return Long.parseLong(queryParamValue);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private long getDefaultLimit() {
        long defaultLimit = ConstantValues.repoDataDefaultLimit.getLong();
        return defaultLimit > 0 ? defaultLimit : Long.MAX_VALUE;
    }

    protected String getKeySearch(ArtifactoryRestRequest<T> request) {
        return request.getQueryParamByKey(KEY_SEARCH);
    }

    protected boolean isAutoComplete(ArtifactoryRestRequest<T> request) {
        String autoCompleteParam = request.getQueryParamByKey(AUTO_COMPLETION);
        if (StringUtils.isNotBlank(autoCompleteParam)) {
            try {
                return Boolean.parseBoolean(autoCompleteParam);
            } catch (Exception e) {
                log.warn("Failed to parse autoCompletion param: {}", autoCompleteParam);
                return false;
            }
        }
        return false;
    }

    protected void addLocalCacheRepoConfigs(List<RepoConfig> repoConfigs, String projectKey, String searchKey) {
        Set<RepoConfig> localCacheRepoConfigs = repoService.getLocalCacheRepoConfigsByProject(projectKey);
        List<RepoConfig> filteredRepoConfigs = filterByPattern(localCacheRepoConfigs.stream(), searchKey).toList();
        repoConfigs.addAll(filteredRepoConfigs);
    }

    private Stream<RepoConfig> filterByPattern(Stream<RepoConfig> repoConfigs, String searchKey) {
        if (StringUtils.isBlank(searchKey)) {
            return repoConfigs;
        }
        return repoConfigs.filter(repoConfig -> repoConfig.getKey().contains(searchKey));
    }

    protected Comparator<RepoConfig> getRepoConfigComparator(ArtifactoryRestRequest<T> request, String keySearch) {
        Comparator<RepoConfig> comparator;
        if (isAutoComplete(request)) {
            comparator = SortStrategyFactory.getComparator(AUTO_COMPLETE_SORT, keySearch);
        } else {
            comparator = SortStrategyFactory.getComparator(LEXICOGRAPHICALLY_SORT);
        }
        return comparator;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }
}