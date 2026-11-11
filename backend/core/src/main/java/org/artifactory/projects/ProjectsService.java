package org.artifactory.projects;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.SimpleUser;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.common.EnvironmentsConstants;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.token.JwtAccessToken;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author BarakH
 */
public interface ProjectsService extends ReloadableBean, ContextCreationListener {

    String PROJECT = "project";
    String PROJECT_KEY = "projectKey";
    String DEFAULT_PROJECT = "default";
    String UNASSIGNED = "unassigned";
    String ENVIRONMENTS = "environments";
    String ENV_DEV = EnvironmentsConstants.DEV;
    String ENV_PROD = EnvironmentsConstants.PROD;
    String DEFAULT_ENVIRONMENT = ENV_DEV;
    Set<String> GLOBAL_ENVIRONMENTS = Set.of(ENV_PROD, ENV_DEV);
    long UNLIMITED_STORAGE = -1L;

    void syncProjectResources(ResourceType resourceType, Set<String> resources);

    /**
     * @deprecated Use {@link #upsertProjectResource} instead.
     * Adds a new repository to the specified project with the environments.
     * <p>
     * The newly created repositories must have a project-specific prefix in the repository key.
     * If they do not - the Access errors out with a respective message.
     */
    @Deprecated(since = "Artifactory v7.9")
    void addProjectRepository(String repoKey, String projectKey, Set<String> environments)
            throws AccessClientException;

    /**
     * Adds a new resource to the specified project with the ProjectResourceEntity.
     * <p>
     * this ProjectResourceEntity contains the resource name, the resource type, shared status and the environments.
     */
    void upsertProjectResource(String projectKey, ResourceType resourceType, ProjectResourceEntity resource,
            boolean isNewResource)
            throws AccessClientException;

    /**
     * Removes a resource from the specified project.
     */
    void removeProjectResource(String projectKey, ResourceType resourceType, String resourceName)
            throws AccessClientException;

    /**
     * Returns The projectKey of the resource
     */
    String getProjectForResource(String resourceName, ResourceType resourceType)
            throws AccessClientException;

    /**
     * Returns The projectKey for a set of resources given their names for a resource type
     */
    Map<String, String> getProjectForResourceMap(Set<String> resourceNames, ResourceType resourceType,
            boolean refreshCache)
            throws AccessClientException;

    /**
     * Returns a predicate that filters out virtual repositories belonging to projects
     * the current user is not a member of.
     */
    Predicate<RepoConfig> createVirtualReposVisibilityFilter(@Nullable String projectKey)
            throws AccessClientException;

    /**
     * Returns resource entities of a type for a given projectKey
     */
    Map<String, ProjectResourceEntity> getProjectResourceEntitiesMap(String projectKey, ResourceType resourceType,
            boolean ignoreCache)
            throws AccessClientException;

    /**
     * @deprecated Use {@link #upsertProjectResource} instead.
     * Updates an existing or adds a new repository to the specified project with the environments.
     * <p>
     * Unlike in the {@link #addProjectRepository}, the newly created repositories
     * may have and may have no project-specific prefix in the repository key.
     */
    @Deprecated(since = "Artifactory v7.9")
    void upsertProjectRepository(String repoKey, String projectKey, Set<String> environments)
            throws AccessClientException;

    /**
     * Updates an existing repository to the specified project with the environments.
     * <p>
     * A repository must be already assigned to the specified project.
     * Like in the {@link #upsertProjectRepository}, the existing repositories
     * may have and may have no project-specific prefix in the repository key.
     */
    void updateProjectRepository(String repoKey, Set<String> environments)
            throws AccessClientException;

    /**
     * Remove a repo resource from its current projectKey
     */
    void removeRepositoryFromCurrentProject(String repoKey) throws AccessClientException;

    /**
     * @return A list with all the projects keys the user has ProjectAdmin role on (including
     * {@link #DEFAULT_PROJECT} for super admin).
     */
    @NonNull
    List<String> getAllProjectsManagedByUser();

    /**
     * @return a list of all the environments entities configured for the given repository.
     */
    List<EnvironmentEntity> getEnvironmentEntities(String repoKey);

    /**
     * @return A list with all the projects keys
     */
    @NonNull
    List<String> getAllProjects();

    /**
     * @return A list of all the projects models from the DB.
     * The ProjectEntity contains all the projectKey configuration including the members, resources and roles.
     */
    @NonNull
    List<ProjectEntity> getAllProjectsEntities();

    /**
     * @return A set of all the projectKey related repositories keys (Assigned and shared with)
     */
    @NonNull
    Set<String> getProjectRepositoriesKeys(String projectKey, boolean ignoreRemoteCacheRepos);

    /**
     * @return The repository projectKey or {@link #DEFAULT_PROJECT} if none
     */
    @NonNull
    String getProjectKeyOfRepository(String repoKey);

    /**
     * @return The projectName or {@link #DEFAULT_PROJECT} if none
     */
    @NonNull
    String getProjectDisplayName(String projectKey);

    /**
     * @return Map of <repoKey , projectKey>
     */
    @NonNull
    Map<String, String> getRepoKeyToProjectMap(@NonNull Set<String> repositories, boolean ignoreCache);

    /**
     * Each repository can be assigned to one projectKey and meanwhile shared with many others.
     * The ProjectResourceEntity holds all the relevant information about the repository state withing the projectKey.
     *
     * @return The ProjectResourceEntity of the given repository within the given projectKey or null.
     */
    @Nullable
    ProjectResourceEntity getProjectRepositoryEntity(@NonNull String projectKey, @NonNull String repoKey);

    /**
     * @return Project general configuration
     */
    @Nullable
    ProjectSlimEntity getProjectSpec(String projectKey);

    /**
     * @return Map of <projectKey , <repoKey , ProjectResourceEntity>>
     */
    @NonNull
    Map<String, Map<String, ProjectResourceEntity>> getAllProjectsRepositoriesMap(boolean ignoreCache);

    /**
     * @return Map of <repoKey , ProjectResourceEntity>
     */
    @NonNull
    Map<String, ProjectResourceEntity> getProjectRepositoriesMap(@NonNull String projectKey, boolean ignoreCache);

    /**
     * @throws IllegalArgumentException if the repo key is not matching to the given projectKey prefix or the
     *                                  environments are not configured for the projectKey.
     */
    void validateRepoConfigIsMatchingToProjectSpec(String projectKey, String repoKey, Set<String> environments);

    /**
     * @return The projectKey storage quota limit in bytes or {@link #UNLIMITED_STORAGE} if failed to find the
     * projectKey
     */
    long getProjectStorageQuotaBytes(String projectKey);

    /**
     * *** DO NOT USE IT ***
     * It does not verify authentication and super admin users
     * You should use {@link org.artifactory.api.security.AuthorizationService#isProjectAdmin}
     *
     * @param projectKey - Nullable for cases of default projectKey
     * @return True if the given user is an admin of the given projectKey
     */
    boolean isProjectAdmin(@Nullable String projectKey, @NonNull SimpleUser user);

    /**
     * Verifies if a user has "Manage Resources" enabled on their profile.
     * Currently, there isn't a clean way to verify if a user has "Manage Resources" enabled, because this properly is
     * not exposed on UserBase and UserImpl objects.
     * Manage Resource is translated to DEFAULT project admin, and therefore this is its current implementation,
     * until Access exposes this option on the BaseUser class.
     *
     * @param user - The user we want to verify
     * @return Whether the user has Manage Resources enabled
     */
    boolean isResourceManager(@NonNull SimpleUser user);

    default void assertProjectStorageQuota(String repoKey, MutableStatusHolder statusHolder, long contentLength) {
    }

    /**
     * @return The repository's owned project storage quota limit in bytes. {@link ProjectsService#UNLIMITED_STORAGE}
     * serves as false for other cases
     */
    long getProjectQuotaSizeByRepository(@NonNull String repoKey);

    @NonNull
    Set<User> getProjectAdmins(@NonNull ProjectEntity projectKey);

    boolean isAnyProjectAdmin(@NonNull JwtAccessToken jwtAccessToken);

    boolean isProjectAdmin(@NonNull JwtAccessToken jwtAccessToken, String projectKey);

    /**
     * Checks the existence of a project based on its key.
     *
     * This method verifies whether a project with the specified key exists in Access.
     * It returns {@code true} if the project is found, and {@code false} if the project key is blank
     * or if the project is not found. In case Access throws an exception, it catches
     * the exception and returns {@code false}.
     *
     * @param projectKey The key of the project to check for existence.
     * @return {@code true} if the project exists in the access system, {@code false} otherwise.
     */
    boolean isProjectExists(String projectKey);

    /**
     * Checks if the current user can perform the given action on a repository within a specified project.
     * Only the specified project is checked — shared membership in other projects is not considered.
     * <p>
     * When {@code enableGranularProjectRepoPermissions} is enabled and an {@code AccessPrincipal} can be obtained
     * for the current authentication, the check is delegated to Access via
     * {@code accessClient.auth().principalCanDo(accessPrincipal, permissionScope)} with a {@code PermissionScope}
     * of type {@code REPOSITORY} that includes the repo key, project key, action, and a {@code repoType} attribute.
     * <p>
     * When {@code enableGranularProjectRepoPermissions} is disabled, uses legacy check:
     * {@code grpcProjectClient.canDoAction()} with a {@code RoleActions}
     * derived from the repo type (e.g. {@code MANAGE_REMOTE_REPOSITORY_RESOURCE} for remote repos when
     * {@code restrictRemoteRepositoryManagement} is enabled, {@code MANAGE_RESOURCES} for all others)
     * <p>
     * Returns {@code false} for the default project ({@code null} or {@code "default"}).
     *
     * @param repoAction the action to check (CREATE, UPDATE, DELETE)
     * @param repoKey the key of the repository
     * @param projectKey the key of the project. {@code null} is treated as the default project
     * @param repoType the repository type (local, remote, virtual, etc.)
     * @return {@code true} if the current user is allowed to perform the action, {@code false} otherwise
     */
    boolean canDoToProjectRepoResource(@Nonnull RepoActionType repoAction, @Nonnull String repoKey,
            @Nullable String projectKey, @Nonnull RepoDetailsType repoType);

    /**
     * Checks if the current user can perform the given action on a repository, resolving the project from the
     * repository's current assignment. Unlike
     * {@link #canDoToProjectRepoResource(RepoActionType, String, String, RepoDetailsType)}, this overload does
     * not require the caller to supply a project key — it is looked up from the repository's metadata.
     * <p>
     * When {@code enableGranularProjectRepoPermissions} is enabled and an {@code AccessPrincipal} can be obtained,
     * the project key is resolved via {@code getProjectKeyOfRepo(repoKey)} and the check is delegated to Access via
     * {@code accessClient.auth().principalCanDo(accessPrincipal, permissionScope)}.
     * <p>
     * When {@code enableGranularProjectRepoPermissions} is disabled, uses legacy check:
     * {@code grpcProjectClient.canProjectAdminDoOnResource()} with a {@code RoleActions} derived from the repo type.
     *
     * @param repoAction the action to check (CREATE, UPDATE, DELETE)
     * @param repoKey the key of the repository
     * @param repoType the repository type (local, remote, virtual, etc.)
     * @return {@code true} if the current user is allowed to perform the action, {@code false} otherwise
     */
    boolean canDoToProjectRepoResource(@Nonnull RepoActionType repoAction, @Nonnull String repoKey,
            @Nonnull RepoDetailsType repoType);

    /**
     * @return True if the current user can trigger xray indexing on the given repository in project scope,
     * checked via {@link org.jfrog.access.client.project.RoleActions#TRIGGER_SECURITY}
     */
    boolean canXrayIndexProjectRepoResource(@Nonnull String repoKey);

    /**
     * @return True if the current user can {@link org.jfrog.access.client.project.RoleActions#MANAGE_RESOURCES} in
     * the given projectKey </br>
     * NOTE: <code>null</code> projectKey is counted as {@link org.artifactory.projects.ProjectsService#DEFAULT_PROJECT}
     * and therefore requires platform admin permissions.
     */
    boolean canManageProjectResources(@Nullable String projectKey);

    /**
     * Checks if the current user is allowed to read the <i>full</i> repository configuration (including
     * sensitive fields such as remote-repo passwords, custom HTTP header values, proxy credentials, etc.).
     * <p>
     * Authorization rules:
     * <ul>
     *   <li>Platform admins are always allowed.</li>
     *   <li>When {@code enableGranularProjectRepoPermissions} is <b>enabled</b>: the user must have
     *       {@code UPDATE} or {@code DELETE} permission on the repo. For repos in the {@code default} project
     *       any grant (role, group, or access token) authorising {@code UPDATE}/{@code DELETE} is accepted.</li>
     *   <li>When {@code enableGranularProjectRepoPermissions} is <b>disabled</b>: legacy
     *       {@code MANAGE_RESOURCES} project-admin check, or an access token granting {@code WRITE}.</li>
     * </ul>
     *
     * @param repoKey  the key of the repository
     * @param repoType the repository type (local, remote, virtual, etc.)
     * @return {@code true} if the current user is allowed to view the full repo configuration,
     * {@code false} otherwise
     */
    boolean canReadFullRepoConfig(@Nonnull String repoKey, @Nonnull RepoDetailsType repoType);

    /**
     * @return True if the current user is {@link org.jfrog.access.client.project.RoleType#ADMIN} for
     * the given projectKey
     * NOTE will return true if the user is a system admin also.
     */
    boolean isProjectAdmin(String projectKey);

    /**
     * Returns a set of repository keys that are ineligible for a given project.
     * Ineligible repository keys are those that are part of the specified virtual repository but are not shared with
     * the specified project.
     */
    Set<RepoDescriptor> getIneligibleRepos(String repoKey, String projectKey);
}
