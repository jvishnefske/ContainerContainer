package org.artifactory.projects;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.permissions.PermissionsCacheType;
import org.artifactory.projects.exception.AccessClientStateException;
import org.artifactory.projects.job.SyncRepositoriesWithAccessJob;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.access.AccessClientUpdateListener;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageService;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AccessPrincipalImpl;
import org.jfrog.access.client.group.GroupsClient;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.GrpcProjectClient;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.client.project.RoleActions;
import org.jfrog.access.client.project.StorageQuotaEntity;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.RoleConstants;
import org.jfrog.access.common.v2.Action;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.token.JwtAccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.projects.ProjectsUtils.isDefaultProject;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.util.bundles.BundleConfigurationUtil.extractProjectKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2DefaultRepoKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2RepoKey;

/**
 * @author BarakH
 */
@Slf4j
@Service
@Primary
@Reloadable(beanClass = ProjectsService.class, initAfter = {AccessService.class, TaskService.class})
public class ProjectsServiceImpl implements ProjectsService, AccessClientUpdateListener {

    private final AtomicBoolean isProjectStorageQuotaNotificationJobRunning = new AtomicBoolean(false);
    private final Object clientLock = new Object();
    private AccessService accessService;
    private volatile GrpcProjectClient grpcProjectClient;
    private StorageService storageService;
    private TaskService taskService;
    private RepositoryService repositoryService;
    private SecurityService securityService;
    private AuthorizationService authorizationService;

    private static SimpleUser getSimpleUser(@NonNull Authentication authentication) {
        return (SimpleUser) authentication.getPrincipal();
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setStorageService(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void onContextCreated() {
        accessService.subscribeForClientChanges(this);
        startStorageQuotaNotificationJobIfNeeded();
        startSyncResourcesWithAccessJob();
    }

    @Override
    public void onClientUpdate() {
        synchronized (clientLock) {
            grpcProjectClient = null;
        }
    }

    @VisibleForTesting
    GrpcProjectClient getGrpcProjectClient() {
        GrpcProjectClient client = grpcProjectClient;
        if (client != null) {
            return client;
        }
        synchronized (clientLock) {
            if (grpcProjectClient == null) {
                log.debug("Initiating new cachedGrpcProject client");
                AccessClient accessClient = accessService.getAccessClient();
                if (accessClient == null) {
                    throw new AccessClientStateException(
                            "Failed to acquire Access project gRPC client. Access might not be initiated");
                }
                grpcProjectClient = accessClient.cachedGrpcProject();
            }
            return grpcProjectClient;
        }
    }

    @Override
    public boolean isProjectAdmin(@Nullable String projectKey, @NonNull SimpleUser user) {
        if (isDefaultProject(projectKey)) {
            return false;
        }
        log.debug("Checking if user '{}' with groups {} isProjectAdmin for projectKey '{}'",
                user.getUsername(), getUserGroupsNames(user), projectKey);
        try {
            return isProjectAdmin(user, projectKey);
        } catch (AccessClientException e) {
            log.warn("Failed to check if user {} is projectKey admin of projectKey {}: {}", user.getUsername(),
                    projectKey,
                    e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean isResourceManager(@NonNull SimpleUser user) {
        log.debug("Checking if user '{}' with groups {} isProjectAdmin for projectKey '{}'",
                user.getUsername(), getUserGroupsNames(user), DEFAULT_PROJECT);

        return isProjectAdmin(user, DEFAULT_PROJECT);
    }

    @Override
    public void syncProjectResources(ResourceType resourceType, Set<String> resources) {
        log.debug("Sync resources of type: {} and values: {} with Access", resourceType,
                Arrays.toString(resources.toArray()));
        try {
            getGrpcProjectClient().syncResources(resourceType, resources);
        } catch (AccessClientException e) {
            log.error("Failed to sync resources of type: {} and values: {} with Access. Exception: {}",
                    resourceType, resources, e.getMessage(), e);
        }
    }

    @Override
    public void addProjectRepository(
            @NonNull String repoKey,
            @Nullable String projectKey,
            @Nullable Set<String> environments) throws AccessClientException, IllegalArgumentException {
        log.debug("Adding repository '{}' in project '{}' with environments '{}'", repoKey, projectKey, environments);
        addRepositoryProjectResource(repoKey, projectKey, environments, true);
        log.debug("Added repository '{}' in project '{}' with environments '{}'", repoKey, projectKey, environments);
    }

    @Override
    public void upsertProjectResource(
            @NonNull String projectKey,
            @NonNull ResourceType resourceType,
            @NonNull ProjectResourceEntity projectResourceEntity,
            boolean isNewResource) throws AccessClientException, IllegalArgumentException {
        getGrpcProjectClient().upsertProjectResource(projectKey, resourceType, projectResourceEntity, isNewResource);
    }

    @Override
    public String getProjectForResource(String resourceName, ResourceType resourceType) {
        return getGrpcProjectClient().getProjectForResource(resourceName, resourceType);
    }

    @Override
    public Map<String, String> getProjectForResourceMap(Set<String> resourceNames, ResourceType resourceType,
            boolean refreshCache) {
        return getGrpcProjectClient().getProjectForResourceMap(resourceNames, resourceType, refreshCache);
    }

    @Override
    public Predicate<RepoConfig> createVirtualReposVisibilityFilter(@Nullable String projectKey) {
        final Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!ConstantValues.uiHideProjectScopedVirtualRepos.getBoolean()
                || !isDefaultProject(projectKey)
                || securityService.isAdmin()
                || authentication == null) {
            return repo -> true;
        }
        SimpleUser user = getSimpleUser(authentication);
        List<String> groups = getUserGroupsNames(user);
        Set<String> userProjectKeys = new HashSet<>(getGrpcProjectClient()
                .getProjectKeysByUsernameAndGroupNames(
                        user.getUsername(), groups != null ? groups : Collections.emptyList()));
        return repo -> {
            if (!(repo instanceof VirtualRepoConfig)) return true;
            String owningProject = getProjectForResource(repo.getKey(), ResourceType.REPO);
            return isDefaultProject(owningProject) || userProjectKeys.contains(owningProject);
        };
    }

    @Override
    public void removeProjectResource(String projectKey, ResourceType resourceType, String resourceName) {
        getGrpcProjectClient().removeProjectResource(projectKey, resourceType, resourceName);
    }

    @Override
    public Map<String, ProjectResourceEntity> getProjectResourceEntitiesMap(
            @NonNull String projectKey,
            @NonNull ResourceType resourceType,
            boolean ignoreCache) throws AccessClientException, IllegalArgumentException {
        return getGrpcProjectClient().getProjectResourceEntitiesMap(projectKey, resourceType, ignoreCache);
    }

    @Override
    public void upsertProjectRepository(
            @NonNull String repoKey,
            @Nullable String projectKey,
            @Nullable Set<String> environments) throws AccessClientException, IllegalArgumentException {
        log.debug("Updating repository '{}' in project '{}' with environments '{}'", repoKey, projectKey, environments);
        addRepositoryProjectResource(repoKey, projectKey, environments, false);
        log.debug("Updated repository '{}' in project '{}' with environments '{}'", repoKey, projectKey, environments);
    }

    @Override
    public void updateProjectRepository(
            @NonNull String repoKey,
            @Nullable Set<String> environments) throws AccessClientException, IllegalArgumentException {
        String projectKey = getProjectKeyOfRepository(repoKey);
        upsertProjectRepository(repoKey, projectKey, environments);
    }

    private void addRepositoryProjectResource(
            @NonNull String repoKey,
            @Nullable String projectKey,
            @Nullable Set<String> environments,
            boolean isNewResource) throws AccessClientException, IllegalArgumentException {
        projectKey = isDefaultProject(projectKey) ? DEFAULT_PROJECT : projectKey;
        environments = ensureEnvironmentsSpecified(projectKey, environments);
        validateProjectAndRepositoryKeyCombination(repoKey, projectKey);
        validateEnvironmentsAvailability(projectKey, environments);
        try {
            GrpcProjectClient grpcClient = getGrpcProjectClient();
            grpcClient.addProjectResource(projectKey, ResourceType.REPO, repoKey, environments, isNewResource);
        } catch (AccessClientException e) {
            log.error("Failed to add project resource: repository={}, project={}, environments={}, " +
                    "isNewResource={}: {}", repoKey, projectKey, environments, isNewResource, e.getMessage());
            log.debug(EMPTY, e);
            throw e;
        }
    }

    /**
     * Before the Access version 7.58.2 (exclusive), there was default auto-assignment
     * of the 'DEV' (default) environment to repository project resources.
     * (in the case, when the incoming environments were empty).
     * <p>
     * Starting from the version 7.58.2 (inclusive), Access does not perform
     * the default environment auto-assignment for "Upsert Project Resources" requests,
     * and errors out upon a request with empty environments instead.
     * This is applicable for all custom projects, but not for the "default" project (the global context).
     * <p>
     * If an Artifactory request reaches this point without any environments specified,
     * defaulting to the 'DEV' environment to preserve the existing behavior for project-specific contexts.
     */
    @NonNull
    private Set<String> ensureEnvironmentsSpecified(
            @Nullable String projectKey, @Nullable Set<String> environments) {
        return isDefaultProject(projectKey)
                ? ofNullable(environments).orElseGet(Set::of)
                : ofNullable(environments).filter(values -> !values.isEmpty())
                .orElseGet(() -> Set.of(DEFAULT_ENVIRONMENT));
    }

    /**
     * Checks that the specified combination of repository key and project key is valid.
     * <p>
     * As of now only Release Bundle V2 repositories are validated.
     */
    @VisibleForTesting
    void validateProjectAndRepositoryKeyCombination(
            @NonNull String repoKey, @Nullable String projectKey) throws IllegalArgumentException {
        if (!isReleaseBundlesV2RepoKey(repoKey)) {
            log.trace("Skip validation of repository: {} and project: {} combination", repoKey, projectKey);
            return;
        }
        boolean isDefaultProject = isDefaultProject(projectKey);
        boolean isDefaultRepoKey = isReleaseBundlesV2DefaultRepoKey(repoKey);
        boolean isDefaultDistributionRepoKey = isReleaseBundlesV2DistributionRepoKey(repoKey);
        if (isDefaultProject && !isDefaultRepoKey && !isDefaultDistributionRepoKey) {
            throw new IllegalArgumentException(
                    format("Default RBv2 repository: %s cannot be created in the project: %s", repoKey, projectKey));
        }
        if (!isDefaultProject && !extractProjectKey(repoKey).equals(projectKey)) {
            throw new IllegalArgumentException(
                    format("RBv2 repository: %s cannot be created in the project: %s", repoKey, projectKey));
        }
    }

    /**
     * Checks that environments for repository assignment exist in the Access.
     * <p>
     * An environment may exist in one of the two forms:
     * <ul>
     *     <li> One of the custom environments: these entries are user-defined keys,
     *          which can be retrieved from the Access' gRPC and HTTP clients.
     *     <li> One of the default environments (i.e. DEV, PROD): these entries are not returned
     *          by the Access' gRPC requests for getting environment names, they are reserved keys.
     * </ul>
     */
    private void validateEnvironmentsAvailability(
            @Nullable String projectKey, @NonNull Set<String> environments) throws IllegalArgumentException {
        Set<String> customEnvironments = collectCustomEnvironments(environments);
        if (!customEnvironments.isEmpty()) {
            validateCustomEnvironmentsAvailability(projectKey, customEnvironments);
        }
    }

    /**
     * Checks that each custom environment exists in the Access.
     * (either in the global context or in a project-specific context)
     */
    private void validateCustomEnvironmentsAvailability(
            @Nullable String projectKey, @NonNull Set<String> environments) throws IllegalArgumentException {
        List<String> existingEnvironments = resolveExistingEnvironments(projectKey);
        environments.forEach(environment -> {
            if (!existingEnvironments.contains(environment)) {
                String message = format("Undefined environment '%s' in the project '%s'", environment, projectKey);
                throw new IllegalArgumentException(message);
            }
        });
    }

    /**
     * Filters out all default environments (i.e. DEV, PROD) from the specified set.
     * (so they may be skipped from the validation for existence - they always exist implicitly)
     */
    @NonNull
    private Set<String> collectCustomEnvironments(@NonNull Set<String> environments) {
        return environments.stream()
                .filter(environment -> !GLOBAL_ENVIRONMENTS.contains(environment))
                .collect(toSet());
    }

    /**
     * Retrieves custom environments either from the global + project-specific contexts,
     * or from the global context (when the project is undefined).
     * <p>
     * In both cases, the default environments (i.e. DEV, PROD)
     * are NOT fetched by these gRPC requests.
     */
    @NonNull
    private List<String> resolveExistingEnvironments(@Nullable String projectKey) {
        GrpcProjectClient client = getGrpcProjectClient();
        List<String> environments = ofNullable(client.getGlobalEnvironments()).orElseGet(List::of);
        if (!isDefaultProject(projectKey)) {
            List<String> projectEnvironments = ofNullable(client.getEnvironments(projectKey)).orElseGet(List::of);
            environments = Stream.concat(environments.stream(), projectEnvironments.stream()).toList();
        }
        return environments;
    }

    @Override
    @NonNull
    public List<String> getAllProjectsManagedByUser() {
        log.debug("Trying to get all the projectKeys the current user has projectAdmin role on");
        return getUserProjectsFromAccess();
    }

    @Override
    public List<EnvironmentEntity> getEnvironmentEntities(String repoKey) {
        try {
            return getGrpcProjectClient().getEnvironmentsEntity(repoKey, ResourceType.REPO.getKey());
        } catch (Exception e) {
            log.warn("Failed to retrieve environment entities for repoKey: '{}'", repoKey, e);
            return Collections.emptyList();
        }
    }

    @Override
    public @NonNull List<String> getAllProjects() {
        log.debug("Trying to retrieve all the projectKeys");
        try {
            return getGrpcProjectClient().getAllProjectKeys();
        } catch (Exception e) {
            log.warn("Failed to get all the projects keys from Access with an error {}", e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    @Override
    public @NonNull List<ProjectEntity> getAllProjectsEntities() {
        log.debug("Trying to retrieve to retrieve all the ProjectsEntities");
        try {
            return getGrpcProjectClient().getAllProjects();
        } catch (Exception e) {
            log.warn("Failed to get all the projects entities from Access with an error {}", e.getMessage(), e);
        }
        return List.of();
    }

    @Override
    public void removeRepositoryFromCurrentProject(@NonNull String repoKey) {
        String currentProject = getProjectKeyOfRepository(repoKey);
        try {
            log.debug("Deleting repository '{}' from project: '{}'", repoKey, currentProject);
            getGrpcProjectClient().removeProjectResource(currentProject, ResourceType.REPO, repoKey);
            log.debug("Deleted repository '{}' from project: '{}'", repoKey, currentProject);
        } catch (AccessClientException e) {
            log.error("Failed to remove repository {} from projectKey {}. {}", repoKey, currentProject,
                    e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public @NonNull Set<String> getProjectRepositoriesKeys(@NonNull String projectKey, boolean ignoreRemoteCacheRepos) {
        try {
            log.debug("Trying to retrieve all the assigned and shared repositories of project {}", projectKey);
            Set<String> projectRepoKeys = new HashSet<>(getGrpcProjectClient()
                    .getProjectResourceEntitiesMap(projectKey, ResourceType.REPO, false).keySet());
            if (ignoreRemoteCacheRepos) {
                return projectRepoKeys;
            }
            return getProjectReposIncludingRemoteCache(projectRepoKeys);
        } catch (Exception e) {
            log.warn("Failed to find projectKey {} resources in Access: {}", projectKey, e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private Set<String> getProjectReposIncludingRemoteCache(Set<String> projectRepoKeys) {
        Set<String> projectRemoteRepos = projectRepoKeys.stream()
                .filter(repoKey -> repositoryService.isRemoteRepoExist(repoKey)).collect(toSet());
        if (!projectRemoteRepos.isEmpty()) {
            Set<String> remoteCachedRepos = addProjectRemoteCacheRepos(projectRemoteRepos);
            projectRepoKeys.addAll(remoteCachedRepos);
        }
        return projectRepoKeys;
    }

    private Set<String> addProjectRemoteCacheRepos(Set<String> projectRemoteRepos) {
        Set<String> remoteCacheRepos = getRemoteCacheRepos();
        if (!remoteCacheRepos.isEmpty()) {
            return projectRemoteRepos.stream()
                    .filter(repoKey -> remoteCacheRepos.contains(repoKey + REMOTE_CACHE_SUFFIX))
                    .map(repoKey -> repoKey + REMOTE_CACHE_SUFFIX)
                    .collect(toSet());
        }
        return Collections.emptySet();
    }

    private Set<String> getRemoteCacheRepos() {
        return repositoryService.getLocalFederatedAndCachedRepoConfigs().stream()
                .filter(LocalConfig::isCache)
                .map(RepoConfig::getKey)
                .collect(toSet());
    }

    @Override
    @NonNull
    public String getProjectKeyOfRepository(@Nullable String repoKey) {
        return getProjectKeyOfRepo(repoKey);
    }

    @NonNull
    private String getProjectKeyOfRepo(@Nullable String repoKey) {
        log.debug("Trying to retrieve the assigned project of repoKey {}", repoKey);
        if (StringUtils.isEmpty(repoKey)) {
            return DEFAULT_PROJECT;
        }
        repoKey = RepoPathUtils.removeDashCache(repoKey);
        return getResourceProjectFromAccess(repoKey, ResourceType.REPO);
    }

    @Override
    @NonNull
    public String getProjectDisplayName(String projectKey) {
        if (isDefaultProject(projectKey)) {
            return DEFAULT_PROJECT;
        }
        try {
            log.debug("Trying to retrieve project display name for projectKey {}", projectKey);
            return getGrpcProjectClient().getProjectDisplayName(projectKey);
        } catch (Exception e) {
            log.warn("Failed to find project display name for projectKey {} in Access: {}", projectKey,
                    e.getMessage(), e);
        }
        return DEFAULT_PROJECT;
    }

    @Override
    @NonNull
    public Map<String, String> getRepoKeyToProjectMap(@NonNull Set<String> repositories, boolean ignoreCache) {
        repositories = repositories.stream()
                .map(RepoPathUtils::removeDashCache)
                .collect(toSet());
        try {
            log.debug(
                    "Trying to retrieve projects assignments for the following repositories {} with ignoreCache set " +
                            "to {}", repositories, ignoreCache);
            return getGrpcProjectClient().getProjectForResourceMap(repositories, ResourceType.REPO, ignoreCache);
        } catch (Exception e) {
            log.warn("Failed to find projects for the following repositories {} in Access: {}", repositories.toArray(),
                    e.getMessage(), e);
        }
        return Map.of();
    }

    @Override
    public void validateRepoConfigIsMatchingToProjectSpec(
            @NonNull String projectKey,
            @NonNull String repoKey,
            @Nullable Set<String> environments) {
        if (isDefaultProject(projectKey)) {
            return;
        }
        ProjectSlimEntity project = getGrpcProjectClient().getProjectSlimEntity(projectKey);
        if (isNull(project)) {
            throw new IllegalArgumentException(format("The project '%s' does not exist", projectKey));
        }
        String prefix = project.getProjectKey().concat("-");
        if (!repoKey.startsWith(prefix)) {
            throw new IllegalArgumentException(format("The repository key '%s' " +
                    "should start with the project key and a dash: '%s'", repoKey, prefix));
        }
    }

    @Override
    public long getProjectStorageQuotaBytes(String projectKey) {
        log.debug("Trying to retrieve the project storage quota of project {}", projectKey);
        if (isDefaultProject(projectKey)) {
            return UNLIMITED_STORAGE;
        }
        ProjectSlimEntity projectEntity = getProjectSpec(projectKey);
        if (projectEntity == null || projectEntity.getStorageQuota() == null) {
            return UNLIMITED_STORAGE;
        }
        Long storageQuotaBytes = projectEntity.getStorageQuota().getStorageQuotaBytes();
        log.debug("Storage quota of project {} is set to {}", projectKey,
                storageQuotaBytes);
        return storageQuotaBytes;
    }

    @Nullable
    @Override
    public ProjectResourceEntity getProjectRepositoryEntity(@NonNull String projectKey, @NonNull String repoKey) {
        try {
            log.debug("Trying to retrieve ProjectResourceEntity of repoKey {} from project {}", repoKey, projectKey);
            return getProjectRepositoriesMap(projectKey, false).getOrDefault(repoKey, null);
        } catch (Exception e) {
            log.warn("Failed to find projectKey {} resources in Access: {}", projectKey, e.getMessage(), e);
        }
        return null;
    }

    @Nullable
    @Override
    public ProjectSlimEntity getProjectSpec(String projectKey) {
        try {
            log.debug("Trying to retrieve ProjectSlimEntity of project {}", projectKey);
            return getGrpcProjectClient().getProjectSlimEntity(projectKey);
        } catch (Exception e) {
            log.warn("Failed to get projectKey {} entity from Access.", projectKey, e);
        }
        return null;
    }

    @Override
    @NonNull
    public Map<String, Map<String, ProjectResourceEntity>> getAllProjectsRepositoriesMap(boolean ignoreCache) {
        log.debug("Trying to retrieve all projects and repositories map with ignoreCache set to {}", ignoreCache);
        return getAllProjects().stream().collect(Collectors.toMap(String::toString,
                project -> getProjectRepositoriesMap(project, ignoreCache)));
    }

    @Override
    @NonNull
    public Map<String, ProjectResourceEntity> getProjectRepositoriesMap(@NonNull String projectKey,
            boolean ignoreCache) {
        try {
            log.debug("Trying to retrieve project resource entities map of project {} with ignoreCache set to {}",
                    projectKey, ignoreCache);
            return getGrpcProjectClient().getProjectResourceEntitiesMap(projectKey, ResourceType.REPO, ignoreCache);
        } catch (Exception e) {
            log.warn("Failed to find project {} resources in Access: {}", projectKey, e.getMessage(), e);
        }
        return Map.of();
    }

    @Override
    public void assertProjectStorageQuota(String repoKey, MutableStatusHolder statusHolder, long contentLength) {
        long projectLimit = getProjectQuotaSizeByRepository(repoKey);
        if (projectLimit == UNLIMITED_STORAGE) {
            return;
        }
        String project = getProjectKeyOfRepository(repoKey);
        long projectTotalSize;
        try {
            projectTotalSize = storageService.getProjectStorageUsage(project);
            log.debug("Project {} has total storage usage of {} bytes out of {} bytes limit.",
                    project, projectTotalSize, projectLimit);
        } catch (CacheUnAvailableException e) {
            projectTotalSize = 0L;
            log.debug("Could not get total storage usage of project {} " +
                    "since Storage Usage Summary cache is temporarily unavailable", project);
        }

        long expectedProjectTotalSize = projectTotalSize + contentLength;
        if (expectedProjectTotalSize > projectLimit) {
            ProjectSlimEntity projectEntity = Objects.requireNonNull(getProjectSpec(project));
            boolean isSoftLimit =
                    ofNullable(projectEntity.getStorageQuota()).map(StorageQuotaEntity::isSoftLimit)
                            .orElse(false);
            ProjectsUtils.handleStatusUpdateByQuotaLimitType(statusHolder, isSoftLimit, log);
            printQuotaExceededOnDebug(projectLimit, project, expectedProjectTotalSize, isSoftLimit);
        }
    }

    private void printQuotaExceededOnDebug(long projectLimit, String project, long expectedProjectTotalSize,
            boolean isSoftLimit) {
        String limitType = isSoftLimit ? "soft" : "hard";
        if (log.isDebugEnabled()) {
            log.debug("Project {} is about to use {} of storage which exceeds the {} limit of {}.",
                    project,
                    FileUtils.byteCountToDisplaySize(expectedProjectTotalSize),
                    limitType,
                    FileUtils.byteCountToDisplaySize(projectLimit));
        }
    }

    @Override
    public long getProjectQuotaSizeByRepository(@NonNull String repoKey) {
        if (!storageService.isProjectsStorageQuotaEnabled()) {
            log.debug("Projects storage quota is disabled.");
            return ProjectsService.UNLIMITED_STORAGE;
        }
        String project = getProjectKeyOfRepository(repoKey);
        if (isDefaultProject(project)) {
            log.debug("Project is either empty or default for repository key: '{}'", repoKey);
            return ProjectsService.UNLIMITED_STORAGE;
        }
        long projectStorageQuotaBytes = getProjectStorageQuotaBytes(project);
        log.debug("Project storage quota is: '{}'", projectStorageQuotaBytes);
        return projectStorageQuotaBytes;
    }

    private void startStorageQuotaNotificationJobIfNeeded() {
        if (isProjectStorageQuotaNotificationJobRunning.get()) {
            return;
        }
        Task task = TaskUtils.createRepeatingTask(ProjectStorageQuotaNotificationJob.class,
                TimeUnit.MINUTES.toMillis(ConstantValues.projectsStorageQuotaNotificationJobIntervalMinutes.getInt()),
                TimeUnit.MINUTES
                        .toMillis(ConstantValues.projectsStorageQuotaNotificationJobInitialDelayMinutes.getInt()),
                "Projects Storage Quota notification emails sender job");
        taskService.startTask(task, false);
        isProjectStorageQuotaNotificationJobRunning.set(true);
        log.info("Scheduling ProjectStorageQuotaNotificationJob to run every {} minutes",
                ConstantValues.projectsStorageQuotaNotificationJobIntervalMinutes.getInt());
    }

    @Override
    public @NonNull Set<User> getProjectAdmins(@NonNull ProjectEntity projectKey) {
        UsersClient usersClient = accessService.getAccessClient().cachedUsers();
        GroupsClient groupsClient = accessService.getAccessClient().groups();

        Set<User> projectAdminUsers = projectKey.getUsers().entrySet().stream()
                .filter(userToRolesEntry -> userToRolesEntry.getValue().getRoles()
                        .contains(RoleConstants.PROJECT_ADMIN))
                .map(Map.Entry::getKey)
                .flatMap(username -> usersClient.findUserByUsername(username).stream())
                .collect(toSet());

        Set<User> projectAdminGroupsUsers = projectKey.getGroups().entrySet().stream()
                .filter(groupToRolesEntry -> groupToRolesEntry.getValue().getRoles()
                        .contains(RoleConstants.PROJECT_ADMIN))
                .map(Map.Entry::getKey)
                .flatMap(group -> groupsClient.findGroupUsers(group).getUsers().stream())
                .collect(toSet());

        return Stream.of(projectAdminUsers, projectAdminGroupsUsers)
                .flatMap(Collection::stream)
                .collect(toSet());
    }

    @Nonnull
    private Optional<AccessPrincipal> getCurrentAccessPrincipal(SimpleUser simpleUser) {
        var accessPrincipalOptional = authorizationService.getCurrentAccessPrincipal();
        if (accessPrincipalOptional.isEmpty() && PermissionsCacheType.isLegacyCacheEnabled()) {
            // We still need to use AccessPrincipal even if Legacy auth mechanism is used. So creating one
            var groups = getUserGroupsNames(simpleUser);
            String userName = simpleUser.getUsername();
            User user = accessService.getAccessClient().cachedUsers().findUserByUsername(userName, true).orElse(null);
            if (user == null && (groups == null || groups.isEmpty())) {
                log.debug("No user or groups found for the current authentication, username is '{}'." +
                          "Returning empty access principal", userName);
                return Optional.empty();
            }
            return Optional.of(AccessPrincipalImpl.create(user, groups));
        }
        if (accessPrincipalOptional.isEmpty()) {
            log.debug("Could not obtain access principal for current authentication. LegacyCacheEnabled is '{}'. " +
                      "Returning empty access principal", PermissionsCacheType.isLegacyCacheEnabled());
        }
        return accessPrincipalOptional;
    }

    @NonNull
    private List<String> getUserProjectsFromAccess() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            log.debug("User is not authenticated");
            return Collections.emptyList();
        }
        SimpleUser user = getSimpleUser(authentication);
        try {
            if (user.isAdmin()) {
                log.debug("User {} is super admin, returning all the projectKeys", user.getUsername());
                return getGrpcProjectClient().getAllProjectKeys();
            }
            List<String> groups = getUserGroupsNames(user);
            log.debug("Trying to retrieve all the projectKeys that user {} with groups {} has project admin role on",
                    user.getUsername(), groups);
            return getGrpcProjectClient().getAllProjectsManagedByUser(user.getUsername(), groups)
                    .stream()
                    .filter(projectKey -> !DEFAULT_PROJECT.equals(projectKey))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to find user {} projects in access. {}", user.getUsername(), e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    @NonNull
    private String getResourceProjectFromAccess(@NonNull String resourceName, ResourceType resourceType) {
        try {
            log.debug("Trying to retrieve the assigned project key of resource name '{}' from resource type {}",
                    resourceName, resourceType);
            return getGrpcProjectClient().getProjectForResource(resourceName, resourceType);

        } catch (AccessClientStateException e) {
            log.warn("Failed to find the project key of '{}' from {} resource type in access: {}", resourceName,
                    resourceType.getKey(), e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to find the project key of '{}' from {} resource type in access: {}", resourceName,
                    resourceType.getKey(), e.getMessage(), e);
        }
        return DEFAULT_PROJECT;
    }

    @Nullable
    private List<String> getUserGroupsNames(SimpleUser simpleUser) {
        Set<UserGroupInfo> userGroups = simpleUser.getDescriptor().getGroups();
        if (userGroups.isEmpty()) {
            return null;
        }
        return userGroups.stream().map(UserGroupInfo::getGroupName).toList();
    }

    @Override
    public boolean isProjectExists(String projectKey) {
        if (StringUtils.isBlank(projectKey)) {
            return false;
        }
        try {
            log.debug("Checking if project exists: {}", projectKey);
            return StringUtils.isNotBlank(getGrpcProjectClient().getProjectDisplayName(projectKey));
        } catch (Exception e) {
            log.debug("Failed to find project {} in Access: {}", projectKey, e.getMessage());
        }
        return false;
    }

    @Override
    public boolean canDoToProjectRepoResource(@Nonnull RepoActionType repoAction, @Nonnull String repoKey,
            @Nullable String projectKey, @Nonnull RepoDetailsType repoType) {
        return checkAuthorizationAndExecute(user ->
                canDoInternal(repoAction, repoKey, projectKey, user, repoType));
    }

    @Override
    public boolean canDoToProjectRepoResource(@Nonnull RepoActionType repoAction, @Nonnull String repoKey,
            @Nonnull RepoDetailsType repoType) {
        return checkAuthorizationAndExecute(user ->
                canDoInternal(repoAction, repoKey, null, user, repoType));
    }

    @Override
    public boolean canXrayIndexProjectRepoResource(@Nonnull String repoKey) {
        return checkAuthorizationAndExecute(
                user -> canProjectAdminDoOnRepo(repoKey, List.of(RoleActions.TRIGGER_SECURITY), user));
    }

    @Override
    public boolean canManageProjectResources(@Nullable String projectKey) {
        return checkAuthorizationAndExecute(user -> canManageProjectResourcesInternal(projectKey, user));
    }

    @Override
    public boolean canReadFullRepoConfig(@Nonnull String repoKey, @Nonnull RepoDetailsType repoType) {
        return checkAuthorizationAndExecute(user -> canReadFullRepoConfigInternal(repoKey, user, repoType));
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private String getUsernameIfExists(SimpleUser simpleUser) {
        return simpleUser.isAppliedPermissionsGroups() && !simpleUser.isIdentity() ? "" : simpleUser.getUsername();
    }

    private void startSyncResourcesWithAccessJob() {
        if (!ConstantValues.syncResourcesWithAccessJobEnabled.getBoolean()) {
            log.info("SyncRepositoriesWithAccessJob is disabled, not scheduling");
            return;
        }
        Task deleteReposFromAccessTask =
                TaskUtils.createRepeatingTask(SyncRepositoriesWithAccessJob.class,
                        ConstantValues.syncResourcesWithAccessJobInterval.getLong(), 0, "Delete repos from Access");
        taskService.startTask(deleteReposFromAccessTask, false);
    }

    @Override
    public boolean isAnyProjectAdmin(@NonNull JwtAccessToken jwtAccessToken) {
        return getGrpcProjectClient().isAnyProjectAdminByRoleScope(jwtAccessToken);
    }

    @Override
    public boolean isProjectAdmin(@NonNull JwtAccessToken jwtAccessToken, String projectKey) {
        return getGrpcProjectClient().isProjectAdminByRoleScopeAndProjectKey(jwtAccessToken, projectKey);
    }

    @Override
    public boolean isProjectAdmin(String projectKey) {
        if (securityService.isAdmin()) {
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return isProjectAdmin(projectKey, getSimpleUser(authentication));
    }

    @Override
    public Set<RepoDescriptor> getIneligibleRepos(String repoKey, String projectKey) {
        if (projectKey.equalsIgnoreCase(DEFAULT_PROJECT)) {
            return Collections.emptySet();
        }
        Set<RepoDescriptor> ineligibleReposKeys = new HashSet<>();
        try {
            log.debug("Retrieving unshared repositories of project {} with repoKey {}", projectKey, repoKey);
            VirtualRepoDescriptor virtualRepoDescriptor = repositoryService.virtualRepoDescriptorByKey(repoKey);
            if (virtualRepoDescriptor != null) {
                Set<String> projectAssignedAndSharedRepositories = getProjectRepositoriesKeys(projectKey, true);
                if (!projectAssignedAndSharedRepositories.isEmpty()) {
                    ineligibleReposKeys =
                            collectIneligibleReposKeys(virtualRepoDescriptor, projectAssignedAndSharedRepositories);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find unshared repositories of project {} with repoKey {}. due to {}", projectKey,
                    repoKey, e.getMessage(), e);
        }
        return ineligibleReposKeys;
    }

    @VisibleForTesting
    boolean canDoInternal(@Nonnull RepoActionType repoAction, @Nonnull String repoKey,
            @Nullable String projectKeyParam, @Nonnull SimpleUser user, @Nonnull RepoDetailsType repoType) {
        String projectKey = fetchProjectKeyIfNeeded(projectKeyParam, repoKey);
        try {
            if (RepoActionType.CREATE == repoAction && isDefaultProject(projectKey)) {
                log.debug("Only platform admins are allowed to create repos in 'default' project");
                return false;
            }
            if (ConstantValues.enableGranularProjectRepoPermissions.getBoolean()) {
                var accessPrincipalOptional = getCurrentAccessPrincipal(user);
                if (accessPrincipalOptional.isPresent()) {
                    AccessPrincipal accessPrincipal = accessPrincipalOptional.get();
                    // Federated repos should be mapped to Local to be correctly processed by Access
                    RepoDetailsType accessRepoType = repoType == RepoDetailsType.FEDERATED ?
                            RepoDetailsType.LOCAL : repoType;
                    var attributes = Map.of("repoType", List.of(accessRepoType.typeNameLowercase()));
                    Action accessAction = mapToAccessAction(repoAction);
                    PermissionScope permissionScope = PermissionScope.create(PermissionScopeResourceType.REPOSITORY,
                            repoKey, null, accessAction, projectKey, attributes);
                    log.debug("Checking if current user can '{}' repo '{}' and project '{}'. Access principal '{}', " +
                              "permission scope '{}'",
                            repoAction, repoKey, projectKey, accessPrincipal, permissionScope);
                    return accessService.getAccessClient().cachedAuth()
                            .principalCanDo(accessPrincipal, permissionScope);
                }
                log.info("Could not obtain access principal for current authentication. Returning false.");
                return false;
            }
            return legacyManageResourcesCheck(repoAction, repoKey, projectKey, user, repoType);
        } catch (AccessClientException e) {
            log.warn("Failed to check if user '{}' can '{}' repo resource '{}' in project '{}'. {}", user.getUsername(),
                    repoAction, repoKey, projectKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Key considerations and implementation details:
     * <ol>
     *   <li>Product's decision is that only users with "update" or "delete" permissions are allowed to "read" full
     *       repo config. May be changed in the future if correlated with how "read" permission is configured per
     *       repo type (remote, local, virtual, etc.). Should be aligned with UI and Access.</li>
     *   <li>There's no need to check for "delete" permission if "granular permissions" feature is turned off,
     *       because in this case any {@link RepoActionType} is eventually mapped to access'
     *       {@code Action.WRITE} permission.</li>
     * </ol>
     */
    @VisibleForTesting
    boolean canReadFullRepoConfigInternal(@Nonnull String repoKey, @Nonnull SimpleUser user,
            @Nonnull RepoDetailsType repoType) {
        String projectKey = getProjectKeyOfRepo(repoKey);
        try {
            if (!ConstantValues.enableGranularProjectRepoPermissions.getBoolean()) {
                log.debug("Fallback to legacy check for 'read full repo config' permission");
                return legacyManageResourcesCheck(RepoActionType.UPDATE, repoKey, projectKey, user, repoType);
            }
            log.debug("Checking for 'read' permission based on 'update' or 'delete' permissions");
            return canDoInternal(RepoActionType.UPDATE, repoKey, projectKey, user, repoType)
                    || canDoInternal(RepoActionType.DELETE, repoKey, projectKey, user, repoType);
        } catch (AccessClientException e) {
            log.warn("Failed to check if user '{}' can read full config for repo resource '{}' in project '{}'. {}",
                    user.getUsername(), repoKey, projectKey, e.getMessage(), e);
            return false;
        }
    }

    @VisibleForTesting
    boolean canManageProjectResourcesInternal(@Nullable String projectKey, @NonNull SimpleUser user) {
        if (!isProjectAdmin(projectKey, user)) {
            return false;
        }
        try {
            String username = getUsernameIfExists(user);
            List<String> groups = getUserGroupsNames(user);
            return getGrpcProjectClient().canDoAction(username, projectKey, groups, RoleActions.MANAGE_RESOURCES);
        } catch (AccessClientException e) {
            log.warn("Failed to check if user '{}' can manage resources in project '{}'", user.getUsername(),
                    projectKey, e);
            return false;
        }
    }

    @VisibleForTesting
    RoleActions getManageRepoRoleActions(RepoDetailsType repoType) {
        return ConstantValues.restrictRemoteRepositoryManagement.getBoolean() &&
                RepoDetailsType.REMOTE == repoType ?
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE : RoleActions.MANAGE_RESOURCES;
    }

    @Nonnull
    private String fetchProjectKeyIfNeeded(@Nullable String projectKeyFromInput, @Nonnull String repoKey) {
        if (StringUtils.isBlank(projectKeyFromInput)) {
            // Try fetching project key for existing repos
            return getProjectKeyOfRepo(repoKey);
        }
        return projectKeyFromInput;
    }

    private boolean canDoToRepoByAccessToken(String projectKey, String repoKey, RepoActionType repoAction) {
        Action accessAction = ConstantValues.enableGranularProjectRepoPermissions.getBoolean() ?
                mapToAccessAction(repoAction) : Action.WRITE; // fallback to legacy check if the feature is off
        PermissionScope permissionScope = PermissionScope.create(PermissionScopeResourceType.REPOSITORY, repoKey, null,
                accessAction, projectKey);
        if (authorizationService.isGrantedByAccessToken(permissionScope)) {
            log.debug("Current user is allowed to '{}' repo '{}' in project '{}' based on access token " +
                      "permissions", repoAction, repoKey, projectKey);
            return true;
        }
        log.debug("Current user is not allowed to '{}' repo '{}' in project '{}' based on access token " +
                  "permissions", repoAction, repoKey, projectKey);
        return false;
    }

    private boolean legacyManageResourcesCheck(RepoActionType repoAction, String repoKey, String projectKey,
            SimpleUser user, RepoDetailsType repoType) {
        String username = getUsernameIfExists(user);
        List<String> groups = getUserGroupsNames(user);
        log.debug("Checking if user '{}' with groups '{}' can manage repo resource '{}' for project '{}'",
                username, groups, repoKey, projectKey);
        RoleActions roleActions = getManageRepoRoleActions(repoType);
        if (RepoActionType.CREATE == repoAction) {
            return getGrpcProjectClient().canDoAction(username, projectKey, groups, roleActions) ||
                   canDoToRepoByAccessToken(projectKey, repoKey, repoAction);
        }
        return canProjectAdminDoOnRepo(repoKey, List.of(roleActions), user) ||
                canDoToRepoByAccessToken(projectKey, repoKey, repoAction);
    }

    /**
     * Checks authorization and executes the provided function
     *
     * @return true if authorized, false otherwise
     */
    private boolean checkAuthorizationAndExecute(Predicate<SimpleUser> internalPermissionsCheckFunction) {
        if (securityService.isAdmin()) {
            return true;
        }
        SimpleUser user = checkAuthenticationAndGetUser();
        if (user == null) {
            return false;
        }
        return internalPermissionsCheckFunction.test(user);
    }

    @Nullable
    private SimpleUser checkAuthenticationAndGetUser() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return null;
        }
        return getSimpleUser(authentication);
    }

    private boolean isProjectAdmin(@NonNull SimpleUser simpleUser, String projectKey) {
        String username = getUsernameIfExists(simpleUser);
        List<String> groups = getUserGroupsNames(simpleUser);
        return getGrpcProjectClient().isProjectAdmin(username, projectKey, groups);
    }

    private boolean canProjectAdminDoOnRepo(String repoKey, List<RoleActions> actions, SimpleUser user) {
        String username = getUsernameIfExists(user);
        List<String> groups = getUserGroupsNames(user);
        return getGrpcProjectClient().canProjectAdminDoOnResource(repoKey, ResourceType.REPO, actions, username,
                groups);
    }

    private Set<RepoDescriptor> collectIneligibleReposKeys(VirtualRepoDescriptor virtualRepoDescriptor,
            Set<String> projectAssignedAndSharedRepositories) {
        return virtualRepoDescriptor.getRepositories().stream()
                .filter(innerRepoDescriptor -> !projectAssignedAndSharedRepositories.contains(
                        innerRepoDescriptor.getKey()))
                .collect(Collectors.toSet());
    }

    private Action mapToAccessAction(RepoActionType repoAction) {
        return switch (repoAction) {
            case CREATE, UPDATE -> Action.WRITE;
            case DELETE -> Action.DELETE;
        };
    }
}