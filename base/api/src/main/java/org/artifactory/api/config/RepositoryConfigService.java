package org.artifactory.api.config;

import jakarta.annotation.Nonnull;
import org.artifactory.async.Async;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ExtendedRepositoryConfig;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;

import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Tamar Rosen
 */
public interface RepositoryConfigService extends RepoExistenceFunction, RepoConfigBackwardsCompatibleImportProcessor,
        VirtualRepoConfigResolverService {

    void insertRepoConfig(RepoConfig repositoryConfig);

    void upsertRepoConfig(RepoConfig repositoryConfig);

    void updateRepoConfig(RepoConfig repositoryConfig);

    /**
     * Updates the repository config without any checks. This method should be used with caution and only
     * for internal use. One of possible use cases is to update the repository config without running interceptors that
     * can forbid the update (interceptor that forbids property updates once it was set).
     * <p>Skips only the BEFORE interceptors; AFTER interceptors still fire (so cache reloads, event emission, etc.
     * still take place). Use {@link #updateRepoConfigSilently} when the update is purely internal plumbing that
     * downstream observers must not see.
     */
    void updateRepoConfigWithoutChecks(RepoConfig repositoryConfig);

    /**
     * Persists the repository config while skipping both BEFORE and AFTER interceptors. Use for internal plumbing
     * updates that must not surface as user-visible repo changes: e.g. flipping {@code blackedOut} just before a
     * delete, or rewriting persisted secrets during master-key rotation. Downstream observers (caches, event
     * dispatchers, federated propagators, etc.) will not see the update.
     */
    void updateRepoConfigSilently(RepoConfig repositoryConfig);

    /**
     * Removes the repository config with the specified key. Will also remove any references to this
     * repository from config descriptor entities (backup, indexer, replication and reverse proxy) and virtual repos.
     *
     * @param repoKey The key of the repository to remove.
     * @return The removed repository config or null if not found.
     */
    @Nullable
    RepoConfig deleteRepoConfig(String repoKey);

    void bulkInsertRepoConfigs(List<? extends RepoConfig> repositoryConfigs,
            boolean reload, boolean propagateChanges, boolean doBackups);

    void bulkUpsertRepoConfigs(Collection<? extends RepoConfig> repositoryConfigs, boolean reload);

    void callOnRepoConfigsReload(RepoConfigChangesReload typeToChanges, boolean fromPropagation);

    @Async
    void callOnRepoConfigsReloadAsync(RepoConfigChangesReload repoConfigChangesReload,
            boolean fromPropagation);

    void bulkDeleteRepoConfigs(List<String> repositoryConfigKeys, boolean reload, boolean propagateChanges);

    @Nullable
    RepoConfig getRepoConfigByKey(String repoKey);

    @Nullable
    RealRepoConfig getRealRepoConfigByKey(String repoKey);

    @Nullable
    VirtualRepoConfig getVirtualRepoConfigByKey(String repoKey);

    @Nonnull
    List<VirtualRepoConfig> getVirtualRepoConfigs();

    @Nonnull
    List<RemoteRepoConfig> getRemoteRepoConfigs();

    @Nonnull
    List<FederatedRepoConfig> getFederatedRepoConfigs();

    @Nonnull
    List<ReleaseBundleRepoConfig> getReleaseBundleRepoConfigs();

    @Nonnull
    List<FederatedRepoConfig> getAllFederatedRepoConfigsWithSystemRepo();

    /**
     * @return Gets a list of local and remote repositories, no caches
     */
    @Nonnull
    List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigs();

    @Nonnull
    List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigsByProject(@Nullable String projectKey);

    @Nullable
    RemoteRepoConfig getRemoteRepoConfigByKey(String repoKey);

    boolean existsByRepoKey(String repoKey);

    boolean existsByKeyIgnoreCase(String repoKey);

    boolean existsByType(RepoDetailsType repoType, String repoKey);

    boolean existsByTypeAndPackageType(RepoDetailsType repoDetailsType, RepoType repoType, String repoKey);

    /**
     * @param repoType - type of the repository to check
     * @param repoKey  - key of the repository to check
     * @return true if a repository with the specified type and key
     * exists in the in memory repository config cache without hitting the database
     * and without reloading the loading cache
     */
    boolean existsByPackageType(RepoType repoType, String repoKey);

    Collection<RepoConfig> getAllRepoConfigs();

    /**
     * Returns lightweight metadata for all cached repositories
     * without deep-copying full config objects. Suitable for
     * aggregation/counting where only key, type, and package type
     * are needed.
     */
    Collection<BasicRepoConfig> getAllBasicRepoConfigsView();

    Set<String> getAllRepoConfigKeys();

    Set<RepoConfig> getRepoConfigsByKeys(Collection<String> repoKeys);

    LinkedHashSet<RepoConfig> getRepoConfigsByKeysInSpecificOrder(VirtualRepoConfig virtualRepoConfig,
            Set<String> allRepoKeys);

    LinkedHashSet<RepoConfig> getRepoConfigsByKeysInSpecificOrder(VirtualRepoConfig virtualRepoConfig);

    <T> ExtendedRepositoryConfig<T> getExtendedRepoConfigByKey(String repoKey, Class<T> type);

    Map<String, String> getPackageConfigByRepoKey(String repoKey);

    /**
     * @param repoKey key of the repository to return
     * @return Local or Federated repository config by repository key
     */
    @Nullable
    LocalConfig getLocalOrFederatedRepoConfigByKey(String repoKey);

    /**
     * @param repoKey key of the repository to return
     * @return Local repository config by repository key
     */
    @Nullable
    LocalRepoConfig getLocalRepoConfigByKey(String repoKey);

    /**
     * We skip validation of -cache in the repokey suffix
     *
     * @param repoKey key of the repository to return
     * @return Local repository config by repository key
     */
    @Nullable
    LocalRepoConfig getLocalRepoConfigByKeyForDelete(String repoKey);

    @Nullable
    FederatedRepoConfig getFederatedRepoConfigByKey(String repoKey);

    /**
     * Retrieves a list of {@link RepoConfig} based on the specified repository type.
     *
     * @param repoType the {@link RepoDetailsType} for which repoConfigs are to be retrieved
     * @return a list of {@link RepoConfig} with the specified repository type
     */
    List<RepoConfig> getRepoConfigsByType(RepoDetailsType repoType);

    /**
     * Retrieves a list of {@link RepoConfig} based on the specified repository types.
     * Note: The returned list is not necessarily ordered by type.
     *
     * @param reposTypes a set of {@link RepoDetailsType} for which repoConfigs are to be retrieved
     * @return a list of {@link RepoConfig} with the specified repository types
     */
    List<RepoConfig> getRepoConfigsByTypes(Set<RepoDetailsType> reposTypes);

    List<RepoConfig> getRepoConfigsByTypesAndProject(Set<RepoDetailsType> reposTypes, String projectKey);

    /**
     * Retrieves a list of {@link RepoConfig} based on the specified repository types,
     * ordered according to the provided {@code reposTypes} order.
     *
     * @param reposTypes an ordered set of {@link RepoDetailsType} for which repoConfigs are to be retrieved
     * @return a list of {@link RepoConfig} ordered according to the specified repository types
     */
    List<RepoConfig> getOrderedRepoConfigsByTypes(LinkedHashSet<RepoDetailsType> reposTypes);

    List<RepoConfig> getRepoConfigsByPackageType(RepoType packageType);

    List<RepoConfig> getRepoConfigsByTypeAndPackageType(Set<RepoDetailsType> type, RepoType packageType);

    Set<String> getRepoConfigKeysByTypeAndPackageType(Set<RepoDetailsType> type, RepoType packageType);

    Set<String> getRepoConfigKeysByType(Set<RepoDetailsType> type);

    @Nullable
    <T> T getPackageConfigByRepoKey(String repoKey, Class<T> type);

    void insertPackageConfigByKey(String repoKey, Map<String, String> packageConfig);

    boolean hasRepositoriesByProjectAndTypes(String projectKey, Set<RepoDetailsType> types);

    int countAll();

    List<VirtualRepoConfig> getVirtualRepoConfigsContainingRepoConfig(RepoConfig repoConfig, int depth);

    List<VirtualRepoConfig> getVirtualRepoConfigsContainingRepoConfig(RepoConfig repoConfig);

    /**
     * Retrieves a list of {@link RepoConfig} based on the specified project filter and repository types.
     * Note: The returned list is not necessarily ordered by type.
     *
     * @param projectFilter a function to filter repositories by project
     * @param types         a set of {@link RepoDetailsType} for which repoConfigs are to be retrieved
     * @return a list of {@link RepoConfig} with the specified project filter and repository types
     */
    List<RepoConfig> getRepoConfigsByProjectAndType(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> types);

    /**
     * Retrieves a list of {@link RepoConfig} keys based on the provided project filter and specified repository types.
     * Note: The returned list is not necessarily ordered by type.
     *
     * @param projectFilter a function to filter repositories by project
     * @param types         a set of {@link RepoDetailsType} for which to retrieve repoConfig keys
     * @return a list of {@link RepoConfig} keys that match the specified project filter and repository types
     */
    List<String> getRepoConfigKeysByProjectAndType(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> types);

    void reloadRepoConfigs();

    @Async
    void calculateCacheDiffAndReloadAsync(Map<String, BasicRepoConfig> oldBasicRepoConfigs,
            Map<String, BasicRepoConfig> newBasicRepoConfigs);

    void reloadRepoConfigs(RepoConfigPropagationModel model);

    void importFrom(ImportSettings settings);

    void exportTo(ExportSettings settings);

    void writeRepoConfigsToFile(File baseDir, Collection<RepoConfig> allRepositoryConfigs);

    @Nullable
    RepoType getRepoType(String repoKey);

    /**
     * @return the list of local and federated repo keys excluding the build info and pipe info repos. Does not
     * include cache repos
     */
    List<String> getLocalAndFederatedRepoConfigsExcludingInfoRepoKeys();

    /**
     * @return the list of local repos including the build info and pipe info repos. Does not include cache repos.
     */
    List<LocalConfig> getLocalRepoConfigsIncludingInfoRepos();

    /**
     * @return the list of local repos including the build info and pipe info repos. Does not include cache and
     * hidden repos
     */
    List<LocalConfig> getLocalRepoConfigsIncludingInfoReposByProject(@Nullable String projectKey);

    /**
     * @return the list of local repos excluding the build info and pipe info repos. Does not include cache and
     * hidden repos
     */
    List<LocalConfig> getLocalRepoConfigsExcludingInfoRepos();

    boolean isInfoOrHiddenRepo(RepoConfig repoConfig);

    /**
     * @param includeShippingOrBillingLogsRepos if true, the shipping and billing logs hidden repos will be included
     *                                          in the result.
     * @return the list of local repos excluding the build info and pipe info repos. Does not include cache repos.
     * Optionally includes the shipping and billing logs hidden repos.
     */
    List<LocalConfig> getLocalRepoConfigsExcludingInfoRepos(boolean includeShippingOrBillingLogsRepos);

    /**
     * @param projectFilter the function to filter repositories by project
     * @return the list of local repos excluding the build info and pipe info repos filtered by project. Does not
     * include cache repos.
     */
    List<LocalConfig> getLocalRepoConfigsExcludingInfoReposByProject(Function<String, Boolean> projectFilter);

    /**
     * @return the list of local repo config keys excluding the build info and pipe info repos. Does not include
     * cache repos
     */
    List<String> getLocalRepoConfigKeysExcludingInfoRepos();

    /**
     * @param includeShippingOrBillingLogsRepos if true, the shipping and billing logs hidden repos will be included
     * @return the list of local repo config keys excluding the build info and pipe info repos. Does not include
     * cache repos. Optionally includes the shipping and billing logs hidden repos.
     */
    List<String> getLocalRepoConfigKeysExcludingInfoRepos(boolean includeShippingOrBillingLogsRepos);

    /**
     * @return the list of local and federated repo configs excluding the build info and pipe info repos. Does not
     * include cache repos.
     */
    List<RepoConfig> getLocalAndFederatedRepoConfigsExcludingInfoRepos();

    /**
     * @return the list of local and federated repo configs including the build info and pipe info repos. Does not
     * include cache repos.
     */
    List<RepoConfig> getLocalAndFederatedRepoConfigsIncludingInfoRepos();

    boolean hasDirectlyAssociatedVirtualRepos(RepoConfig repoConfig);

    /**
     * Returns a list of {@link RepoConfig} that contain the specified search pattern in their keys.
     *
     * @param search the pattern to filter configurations by.
     * @return a list of matching configurations, or an empty list if none match.
     */
    List<RepoConfig> getAllRepoConfigsBySearchPattern(String search);

    /**
     * Returns a list of {@link RepoConfig} that match the specified criteria:
     * - The repository key contains the given search pattern.
     * - The repository belongs to the specified {@code projectKey}.
     * - The repository type is included in the provided set of required {@link RepoDetailsType}.
     *
     * @param repoDetailsTypes A set of repository types to filter by.
     * @param projectKey       The project key to filter repositories.
     * @param search           A pattern to match against repository keys.
     * @return A list of matching {@link RepoConfig}, or an empty list if none match.
     */
    List<RepoConfig> getRepoConfigsBySearchPattern(Set<RepoDetailsType> repoDetailsTypes, String projectKey,
            String search);

    /**
     * Returns a list of {@link RepoConfig} that match the specified criteria:
     * - The repository key contains the given search pattern.
     * - The repository belongs to the specified {@code projectKey}.
     * - The repository type is included in the provided set of required {@link RepoDetailsType}.
     * - The repository package type is included in the provided set of required {@link RepoType}.
     *
     * @param type         A set of repository types to filter by.
     * @param packageTypes A set of package types to filter by.
     * @param projectKey   The project key to filter repositories.
     * @param search       A pattern to match against repository keys.
     * @return A list of matching {@link RepoConfig}, or an empty list if none match.
     */
    List<RepoConfig> getRepoConfigsBySearchPattern(Set<RepoDetailsType> type, Set<RepoType> packageTypes,
            String projectKey, String search);

    /**
     * Clears virtual repo refs that point to a removed repository, using the removed repo type: resets
     * default deployment repo ref only when the removed repo is a local repo, and external dependencies
     * config only when the removed repo is a remote repo. Use after removing a child from a virtual
     * repo's repositoryRefs so the config does not retain dangling references.
     *
     * @param virtualRepoConfig the virtual repo config to update in place
     * @param removedRepo      the repository that was removed (type determines which refs are cleared)
     * @return true if any ref was cleared
     */
    boolean clearVirtualRepoRefsForRemovedChild(VirtualRepoConfig virtualRepoConfig, RepoConfig removedRepo);

    /**
     * Filters the provided list of {@link RepoConfig} to include only those that are allowed for import/ export.
     * The filtering is based on the repo config type and the import settings.
     * @param repoConfigs
     * @return List of {@link RepoConfig} that are allowed for import/export.
     */
    List<RepoConfig> filterRepoConfigsAllowedForImport(List<RepoConfig> repoConfigs);

    /**
     * Filters the given list of repository keys and returns only those that are allowed for import.
     * The filtering is based on the repository type and the import settings.
     *
     * @param repoKeys List of repository keys to filter
     * @return List of repository keys that are allowed for import
     */
    List<String> filterReposAllowedForImport(List<String> repoKeys);

    /**
     * Atomically removes {@code childRepoKey} from the virtual repo identified by {@code virtualRepoKey} and
     * persists the change — all under the repo update lock. Safe to call concurrently for different children of
     * the same virtual: each caller re-reads the freshest config inside the lock, so no lost-update races occur.
     * No-op if the virtual repo does not exist or no longer references the child.
     *
     * @param virtualRepoKey key of the virtual repo to update
     * @param childRepoKey   key of the child repo ref to remove
     * @param removedRepo    the removed repo descriptor (used to clear dependent refs such as default deployment
     *                       and external dependency); pass {@code null} when the type is unknown
     */
    void removeDanglingChildRefFromVirtual(String virtualRepoKey, String childRepoKey,
            @Nullable RepoConfig removedRepo);

}
