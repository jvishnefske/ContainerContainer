/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.service;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.Request;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.ReleaseBundlesRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.local.ValidDeployPathContext;
import org.artifactory.repo.service.versioning.RepositoriesCache;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.Lock;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.spring.RepoConfigChangeListener;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.service.ItemMetaInfo;
import org.artifactory.storage.jobs.migration.sha256.ChecksumCalculationWorkItem;
import org.artifactory.storage.jobs.migration.sha256.Sha256MigrationJob;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.security.util.Pair;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * A repository service to be used by the core
 * <p/>
 * User: freds Date: Jul 31, 2008 Time: 5:50:18 PM
 */
public interface InternalRepositoryService extends RepositoryService, ReloadableBean, ContextReadinessListener,
        ContextCreationListener, RepoConfigChangeListener {

    boolean isAnonAccessEnabled();

    /**
     * Count number of files with name equal to filename argument
     *
     * @param repoKey  - the repokey to search for
     * @param fileName - the fileName to count
     * @return the number of files with name equals to filename or -1 in case exception was thrown
     */
    int countFiles(String repoKey, String fileName);

    /**
     * @param key The repository key
     * @return Repository with the exact given key (no special meaning for remote/cache repo keys). Null if not found.
     */
    @Nullable
    Repo repositoryByKey(String key);

    @Nullable
    VirtualRepo virtualRepositoryByKey(String key);

    /**
     * @param key The repository key
     * @return Local or remote repository with the exact given key (no special meaning for remote/cache repo keys).
     * Null if not found, will also return null if virtual repo.
     */
    @Nullable
    LocalRepo localFederatedOrCachedRepositoryByKey(String key);

    default boolean isRemoteOrCacheFlow(String repoKey) {
        LocalRepo<?, ?> repo = localFederatedOrCachedRepositoryByKey(repoKey);
        return repo != null && repo.isCache();
    }

    /**
     * Get the holder object that holds the actual repository as well as the path.
     *
     * @param repoPath The repo path
     * @param <R>      The type of repository.
     * @return The holder object that holds the actual repository as well as the path.
     */
    <R extends Repo> RepoRepoPath<R> getRepoRepoPath(RepoPath repoPath);

    /**
     * Get a repository that can store artifacts. Will get either a local repo (regular or cache) or a virtual repo. If
     * remote repository key is given, the cache repository is returned.
     */
    StoringRepo storingRepositoryByKey(String key);

    List<RealRepo> getLocalFederatedAndRemoteRepositories();

    List<VirtualRepo> getVirtualRepositories();

    /**
     * Returns local, remote cache and federated repos.
     */
    List<LocalRepo> getLocalFederatedAndCachedRepositories();

    /**
     * This function to reduce access to the repoService cache in cases we need only repoConfig.
     * The data for localCache, trashcan, supportBundlesRepo still fetched from repoService cache as we do not fetch
     * them from repoConfigService.
     */
    RepoConfig getRepositoryConfigByKey(String key);

    /**
     * Returns a local non-cache repository by key
     *
     * @param key The repository key
     * @return Local non-cache repository or null if not found
     */
    LocalRepo localOrFederatedRepositoryByKey(String key);

    FederatedRepo federatedRepositoryByKey(String key);

    RemoteRepo remoteRepositoryByKey(String key);

    ReleaseBundlesRepo releaseBundleRepositoryByKey(String key);

    /**
     * This will verify the permission to deploy to the path
     *
     * @param validDeployPathContext Context for encapsulating all the methods parameters
     */
    void assertValidDeployPathAndPermissions(ValidDeployPathContext validDeployPathContext) throws RepoRejectException;

    <T extends RemoteRepoDescriptor, S extends RemoteRepoConfig> ResourceStreamHandle downloadAndSave(
            InternalRequestContext requestContext,
            RemoteRepo<T, S> remoteRepo, RepoResource res) throws IOException, RepoRejectException;

    @Lock
    RepoResource unexpireIfExists(LocalRepo<?, ?> localCacheRepo, String path);

    /**
     * Unexpire file in virtual cache if exists.
     *
     * @param path path to file (does not work on folders).
     * @return FileResource or UnfoundRepoResource
     */
    @Lock
    @Nonnull
    RepoResource unexpireIfExists(VirtualRepo virtualRepo, String path);

    @Lock
    ResourceStreamHandle unexpireAndRetrieveIfExists(InternalRequestContext requestContext, LocalRepo localCacheRepo,
            String path) throws IOException, RepoRejectException;

    ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, Repo repo, RepoResource res)
            throws IOException, RepoRejectException, BinaryRejectedException;

    /**
     * Expires expirable file from a virtual cache.
     *
     * @param repoPath path of a file in virtual repository cache.
     * @return the updated resource
     * @throws IllegalArgumentException in case the repoPath is not to a virtual repository.
     */
    @Lock
    RepoResource zapFileFromVirtual(RepoPath repoPath);

    /**
     * Saves the given resource in the input repository.
     *
     * @param repo        The repo to save the resource in. Must be a storage repo (local/local cache/virtual cache)
     * @param saveContext Resource details
     * @return Repository resource with details of the saved resource
     * @throws IOException             On any IO related exception
     * @throws RepoRejectException     If the target repository rejected the resource
     * @throws BinaryRejectedException If the Binary failed validation at the storage layer
     */
    RepoResource saveResource(StoringRepo repo, SaveResourceContext saveContext)
            throws IOException, RepoRejectException, BinaryRejectedException;

    /**
     * Internal, transactional method to save a resource. Don't use directly.
     *
     * @see InternalRepositoryService#saveResource(StoringRepo, SaveResourceContext)
     */
    @Transactional
    RepoResource saveResourceInTransaction(StoringRepo repo, SaveResourceContext saveContext)
            throws IOException, RepoRejectException, BinaryRejectedException;

    @Override
    void exportTo(ExportSettings settings);

    boolean isRepoExistInCache(RepoPath repoPath);

    /**
     * Returns a local or local cache repository. Throws an exception if not found
     *
     * @param repoPath A repo path in the repository
     * @return Local/cache repository matching the repo path repo key
     * @throws IllegalArgumentException if repository not found
     */
    @Nonnull
    LocalRepo getLocalRepository(RepoPath repoPath);

    /**
     * Perform repo config reload as the original node receiving the change
     *
     * @param repoConfigChangesReload contains repository configuration changes including the action, old and new config
     */
    @Override
    @Lock
    void onRepoConfigReload(RepoConfigChangesReload repoConfigChangesReload);


    /**
     * Perform repo config reload as the destination node receiving the propagated changes
     *
     * @param configChangesReload Repository configuration changes including the action, old and new config
     */
    void onPropagatedRepoConfigReload(RepoConfigChangesReload configChangesReload);


    @Override
    void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff);

    /**
     * A convenient method for saving internally generated files to the repositories.
     *
     * @param fileRepoPath Repository path to store the input stream
     * @param is           The input stream to store. Will be closed before returning from this method
     * @see InternalRepositoryService#saveResource(StoringRepo, SaveResourceContext)
     */
    void saveFileInternal(RepoPath fileRepoPath, InputStream is) throws RepoRejectException, IOException;

    /**
     * A convenient method for saving internally generated files to the repositories.
     *
     * @param fileRepoPath  Repository path to store the input stream
     * @param is            The input stream to store. Will be closed before returning from this method
     * @param contentLength The input stream content length. if unkonwn, use the saveFileInternal(filePath,is) or -1
     * @see InternalRepositoryService#saveResource(StoringRepo, SaveResourceContext)
     */
    void saveFileInternal(RepoPath fileRepoPath, InputStream is, long contentLength)
            throws RepoRejectException, IOException;

    /**
     * A convenient method for saving internally generated files to the repositories.
     * The files are saved atomically in a single transaction.
     * In case of an error, we continue to handling the next file (failfast == false).
     *
     * @param files a list containing pairs of file repository path to store in, and the file to store.
     * @see InternalRepositoryService#saveResource(StoringRepo, SaveResourceContext)
     */
    @Lock
    void saveFilesInternal(@Nonnull List<Pair<RepoPath, File>> files);

    /**
     * @param itemInfo The item.
     * @return The meta info of the given repo path. Null if not found.
     */
    @Nullable
    ItemMetaInfo getItemMetaInfo(ItemInfo itemInfo);

    /**
     * Moves repository path (pointing to a folder) to another absolute target. The move will only move paths the user
     * has permissions to move and paths that are accepted by the target repository.
     *
     * @param from            The source path to move.
     * @param to              The target path to move to.
     * @param dryRun          If true the method will just report the expected result but will not move any file
     * @param suppressLayouts If true, path translation across different layouts should be suppressed.
     * @param failFast        If true, the operation should fail upon encountering an error.
     * @return MoveMultiStatusHolder holding the errors and warnings
     */
    @Lock
    MoveMultiStatusHolder moveWithoutMavenMetadata(RepoPath from, RepoPath to, boolean dryRun, boolean suppressLayouts,
            boolean failFast);

    /**
     * Sets the client checksum on the target file. Fails if the target doesn't exist or not a file.
     *
     * @param repo               The repository holding the file
     * @param checksumType       The {@link org.artifactory.checksum.ChecksumType}
     * @param targetFileRepoPath Target file
     * @param checksum           Checksum value
     * @return Checksum info for the given type
     */
    @Lock
    ChecksumInfo setClientChecksum(LocalRepo repo, ChecksumType checksumType, RepoPath targetFileRepoPath,
            String checksum);

    @Request(aggregateEventsByTimeWindow = true)
    BasicStatusHolder undeployMultiTransaction(RepoPath repoPath, BasicStatusHolder statusHolder);

    /**
     * Undeploy (deletes) the given path in a transaction.
     *
     * @param repoPath The path to delete
     * @return true if the given repoPath was successfully deleted
     */
    @Lock
    boolean undeployInTx(@Nonnull RepoPath repoPath, boolean triggerMetadataCalculation,
            @Nonnull BasicStatusHolder statusHolder);

    @Lock
    boolean undeployInTx(@Nonnull RepoPath repoPath, boolean triggerMetadataCalculation,
            @Nonnull BasicStatusHolder statusHolder, boolean fullRepoContentDeletion);

    @Lock
    RepositoriesCache rebuildRepositoriesInTransaction();

    //TODO: [by YS] cannot be transactional by itself should be transactional mandatory
    MutableVfsItem getMutableItem(RepoPath repoPath);

    /**
     * Updates the {@param path}'s row in the nodes table with {@param sha2}
     * BEWARE - this method is meant for a very specific usage - the sha2 db conversion, and for that reason i've made
     * it as un-generic as I could to stop you from using it for anything else.
     * If you think it's a good idea to change a node's checksum - stop.
     * you have no business changing the checksums calculated and verified by the Binarystore - it will always lead to
     * inconsistencies and security issues!
     */
    @Async(workQueue = @WorkQueueConfig(name = Sha256MigrationJob.WORK_QUEUE_NAME))
    void updateSha2(ChecksumCalculationWorkItem workItem);

    /**
     * BEWARE - this method is meant for a very specific usage - the sha2 db conversion --> see above.
     * INTERNAL USE ONLY
     */
    @Lock
    void updateSha2ForPath(RepoPath path, String sha2);

    /**
     * BEWARE - this method is meant for a very specific usage - the sha2 db conversion --> see above.
     * INTERNAL USE ONLY
     */
    @Lock
    void updateSha2ForPathNotFound(RepoPath path, String sha2);

    Set<String> getAllLocalRepoKeys();

    Set<RepoConfig> getLocalCacheRepoConfigsByProject(String projectKey);

    /**
     * Retrieve the default deployment repo key for {@param localOrVirtualRepoKey} in case of virtual.
     * In case the given repo key is local it is returned.
     **/
    String getDefaultDeploymentRepoKey(String localOrVirtualRepoKey);

    /**
     * Delete repository content only.
     * To completely delete a repository use: {@link RepositoryService#removeRepository(String)} instead
     */
    void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder);

    void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder, boolean fullRepoContentDeletion);

    @Nonnull
    List<RepoPath> getExistingItems(List<RepoPath> repoPats);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void initRepo(Repo repo, boolean existenceValidated);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void initRepo(Repo repo, @Nonnull StoringRepoInitContext initContext, boolean existenceValidated);

    /**
     * Checks whether a repository contains any real content (files or folders beyond the root folder).
     * For virtual repositories, always returns false, as virtual repositories have no real content.
     *
     * @param repoKey the repository key to check
     * @return true if the repository contains content beyond its root folder, false otherwise
     */
    boolean containsRealContent(String repoKey);

    /**
     * Cleans up orphaned nodes for the given repository by fetching remaining nodes
     * and undeploying each one through the standard pipeline.
     * Errors and warnings are accumulated in the provided status holder.
     *
     * @return the number of successfully undeployed nodes
     */
    int deleteOrphanNodes(String repoKey, BasicStatusHolder statusHolder);

    int deleteOrphanNodes(String repoKey, BasicStatusHolder statusHolder, boolean fullRepoContentDeletion);

    /**
     * Asserts that adding contentLength to the current storage does not exceed the configured storage quota limit.
     * Checks in 2 levels:
     * 1. System level - if the system quota is enabled, checks that the total storage used + contentLength does not
     * exceed the system quota limit.
     * 2. Project level - if the project quota is enabled, checks that the given repository project's
     * storage used + contentLength does not exceed the project quota limit.
     */
    void assertStorageQuota(String repoKey, BasicStatusHolder status, long contentLength);

    /**
     * Atomically removes {@code childRepoKey} from the virtual repo identified by {@code virtualRepoKey} under lock.
     * No-op if the virtual repo does not exist or no longer references the child.
     *
     * @see org.artifactory.api.config.RepositoryConfigService#removeDanglingChildRefFromVirtual
     */
    void removeDanglingChildRefFromVirtual(String virtualRepoKey, String childRepoKey,
            @Nullable RepoConfig removedRepo);
}