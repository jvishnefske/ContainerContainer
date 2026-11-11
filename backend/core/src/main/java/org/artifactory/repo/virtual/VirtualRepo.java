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

package org.artifactory.repo.virtual;

import com.google.common.collect.Sets;
import com.jfrog.ph.acl.exception.PackageVirtualTraversalException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.VirtualDownloadStrategyType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.io.checksum.policy.ChecksumPolicyIgnoreAndGenerate;
import org.artifactory.md.Properties;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.ph.PackageVirtualDownloadStrategy;
import org.artifactory.ph.PackageVirtualResolutionService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoBase;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.db.DbStoringRepoMixin;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.StoringRepoInitContext;
import org.artifactory.repo.virtual.interceptor.VirtualRepoInterceptor;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.fscStorageEnabled;

/**
 * @author Fred Simon
 */
@Slf4j
public class VirtualRepo extends RepoBase<VirtualRepoDescriptor, VirtualRepoConfig>
        implements StoringRepo<VirtualRepoDescriptor, VirtualRepoConfig> {
    public static final String PACKAGE_HANDLER_ENABLED = "packageHandlerEnabled";

    /**
     * Separate key sets per repo type (instead of a single aggregated set) so that the same key can
     * exist in different categories without false duplicate detection. For example, a legacy local
     * repo named "foo-cache" and a remote repo "foo" with a local cache repo key "foo-cache" must
     * both be present; a single set would treat the second as a duplicate and drop it, breaking
     * virtual resolution.
     */
    private final Set<String> localKeys = new HashSet<>();
    private final Set<String> remoteKeys = new HashSet<>();
    private final Set<String> localCacheKeys = new HashSet<>();
    private final Set<String> virtualKeys = new HashSet<>();
    private final List<LocalRepo> localRepositories = new ArrayList<>();
    private final List<RemoteRepo> remoteRepositories = new ArrayList<>();
    private final List<LocalCacheRepo> localCacheRepositories = new ArrayList<>();
    private final List<VirtualRepo> virtualRepositories = new ArrayList<>();
    protected final VirtualRepositoriesResolver virtualRepositoriesResolver;
    private PackageVirtualResolutionService packageVirtualResolutionService;

    DbStoringRepoMixin<VirtualRepoDescriptor, VirtualRepoConfig> dbStorageMixin;

    //Use a final policy that always generates checksums
    private final ChecksumPolicy defaultChecksumPolicy = new ChecksumPolicyIgnoreAndGenerate();
    protected Map<VirtualDownloadStrategyType, VirtualDownloadStrategy> downloadStrategy = new HashMap<>();


    // List of interceptors for various download resolution points
    private Collection<VirtualRepoInterceptor> interceptors;

    public VirtualRepo(VirtualRepoDescriptor descriptor, VirtualRepoConfig repoConfig,
            InternalRepositoryService repositoryService, CentralConfigService configService) {
        super(descriptor, repoConfig, repositoryService, configService);
        this.downloadStrategy.put(VirtualDownloadStrategyType.artifactoryResolution,
                new ArtifactoryVirtualDownloadStrategy(this));
        this.dbStorageMixin = new DbStoringRepoMixin<>(descriptor, repoConfig, null);
        this.virtualRepositoriesResolver = new VirtualRepositoriesResolver(this);
    }

    /**
     * In order to create virtual repo we need to provide the list of the repositories in Artifactory.
     * The problem with that is that the virtual repositories are not ready at this stage therefore we need to pass
     * the list of new virtual (non initialized list) repositories by this method
     *
     * @param newVirtualMap  map of virtual repositories which are still in initialization
     * @param localRepos     map of local repositories which initialized
     * @param federatedRepos map of federated repositories which initialized
     * @param remoteRepos    map of remote repositories which initialized
     */
    public void updateRepos(Map<String, VirtualRepo> newVirtualMap,
            Map<String, LocalRepo> localRepos,
            Map<String, FederatedRepo> federatedRepos,
            Map<String, RemoteRepo> remoteRepos) {
        List<String> repositoryRefs = getRepoConfig().getRepoTypeConfig().getRepositoryRefs();
        repositoryRefs.stream()
                .map(repoRef -> aggregatedRepoByRef(newVirtualMap, localRepos, federatedRepos, remoteRepos, repoRef))
                .filter(Objects::nonNull)
                .forEach(this::handleAggregatedRepo);
    }

    private void handleAggregatedRepo(Repo<?, ?> repo) {
        if (repo.isReal()) {
            handleRealRepo(repo);
        } else {
            handleVirtualRepo((VirtualRepo) repo);
        }
    }

    private Repo<?, ?> aggregatedRepoByRef(Map<String, VirtualRepo> newVirtualMap, Map<String, LocalRepo> localRepos,
            Map<String, FederatedRepo> federatedRepos, Map<String, RemoteRepo> remoteRepos, String aggregatedRepoRef) {
        Repo<?, ?> repo = localRepos.get(aggregatedRepoRef);
        if (repo == null) {
            repo = federatedRepos.get(aggregatedRepoRef);
        }
        if (repo == null) {
            repo = remoteRepos.get(aggregatedRepoRef);
        }
        if (repo == null) {
            repo = newVirtualMap.get(aggregatedRepoRef);
        }
        if (repo == null) {
            log.warn("`{}` aggregated repo could not be found for `{}` virtual repository", aggregatedRepoRef,
                    getKey());
        }
        return repo;
    }

    private void handleRealRepo(Repo<?, ?> repo) {
        String key = repo.getKey();
        log.debug("Resolving real repositories from RepositoryCache repo maps");
        // this includes federated repositories; use per-category sets so the same key can exist in
        // different categories (e.g. legacy local "foo-cache" vs remote cache "foo-cache")
        if (repo instanceof LocalRepo localRepo) {
            addIfNewByKey(localKeys, localRepositories, key, localRepo);
        } else if (repo instanceof RemoteRepo remoteRepo) {
            addIfNewByKey(remoteKeys, remoteRepositories, key, remoteRepo);
            if (remoteRepo.isStoreArtifactsLocally()) {
                LocalCacheRepo localCacheRepo = remoteRepo.getLocalCacheRepo();
                if (localCacheRepo != null) {
                    addIfNewByKey(localCacheKeys, localCacheRepositories, localCacheRepo.getKey(), localCacheRepo);
                } else {
                    log .warn("Cannot find local cache repo for remote repo: '{}'", remoteRepo.getKey());
                }
            }
        }
    }

    private void handleVirtualRepo(VirtualRepo repo) {
        addIfNewByKey(virtualKeys, virtualRepositories, repo.getKey(), repo);
    }

    private static <R> void addIfNewByKey(Set<String> keys, List<R> list, String key, R repo) {
        if (keys.add(key)) {
            list.add(repo);
        }
    }

    /**
     * Must be called after all repositories were built because we save references to other repositories.
     */
    @Override
    public void init(boolean existenceValidated) {
        interceptors = ContextHelper.get().beansForType(VirtualRepoInterceptor.class).values();
        this.packageVirtualResolutionService =
                ContextHelper.get().beanForType(PackageVirtualResolutionService.class);
        this.downloadStrategy.put(VirtualDownloadStrategyType.packageResolution,
                new PackageVirtualDownloadStrategy(this, this.packageVirtualResolutionService));
        initStorage(existenceValidated);
    }

    @Override
    public void init(@NonNull StoringRepoInitContext initContext, boolean existenceValidated) {
        interceptors = initContext.virtualRepoInterceptors();
        this.packageVirtualResolutionService = initContext.packageVirtualResolutionService();
        this.downloadStrategy.put(VirtualDownloadStrategyType.packageResolution,
                new PackageVirtualDownloadStrategy(this, this.packageVirtualResolutionService));
        dbStorageMixin.init(initContext, existenceValidated);
    }

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public boolean isWriteLocked(RepoPath repoPath) {
        return dbStorageMixin.isWriteLocked(repoPath);
    }

    @NonNull
    @Override
    public VfsItem<ItemInfo> getImmutableFsItem(@NonNull ItemInfo itemInfo) {
        return dbStorageMixin.getImmutableItem(itemInfo);
    }

    public void initStorage() {
        initStorage(false);
    }

    public void initStorage(boolean existenceValidated) {
        dbStorageMixin.init(existenceValidated);
    }

    public List<VirtualRepo> getVirtualRepositories() {
        return new ArrayList<>(virtualRepositories);
    }

    public List<RemoteRepo> getRemoteRepositories() {
        return new ArrayList<>(remoteRepositories);
    }

    public List<LocalCacheRepo> getLocalCaches() {
        return new ArrayList<>(localCacheRepositories);
    }

    public List<LocalRepo> getLocalRepositories() {
        return new ArrayList<>(localRepositories);
    }

    public boolean isArtifactoryRequestsCanRetrieveRemoteArtifacts() {
        return getRepoConfig().getRepoTypeConfig().isArtifactoryRequestsCanRetrieveRemoteArtifacts();
    }

    public boolean isResolveDockerTagsByTimestamp() {
        // TODO: 06/02/2022 fixme
        return getDescriptor().isResolveDockerTagsByTimestamp();
    }

    public VirtualRepositoriesResolver getVirtualRepositoriesResolver() {
        return virtualRepositoriesResolver;
    }

    @Nullable
    public VirtualRepoItem getVirtualRepoItem(RepoPath repoPath) {
        return fscStorageEnabled.getBoolean() ?
                ReposItemsThreadLocalCacheService.wrap(getKey(), () -> getVirtualRepoItemInternal(repoPath))
                : getVirtualRepoItemInternal(repoPath);
    }

    private VirtualRepoItem getVirtualRepoItemInternal(RepoPath repoPath) {
        Set<LocalRepo> localFederatedAndCachedRepos = getResolvedLocalFederatedAndCachedRepos();
        //Add paths from all children virtual repositories
        VirtualRepoItem item = null;
        for (LocalRepo repo : localFederatedAndCachedRepos) {
            RepoPathImpl localRepoPath = new RepoPathImpl(repo.getKey(), repoPath.getPath());
            if (getRepositoryService().exists(localRepoPath)) {
                if (item == null) {
                    ItemInfo itemInfo = getRepositoryService().getItemInfo(localRepoPath);
                    // use the item info of the first found item
                    item = new VirtualRepoItem(itemInfo);
                }
                item.addRepoKey(repo.getKey());
            }
        }
        return item;

    }

    public Set<String> getChildrenNamesDeeply(RepoPath folderPath) {
        String path = folderPath.getPath();
        Set<LocalRepo> localFederatedAndCachedRepos = getResolvedLocalFederatedAndCachedRepos();
        Set<String> children = Sets.newHashSet();

        for (LocalRepo repo : localFederatedAndCachedRepos) {
            if (!repo.itemExists(path)) {
                continue;
            }
            ItemInfo itemInfo = getRepositoryService().getItemInfo(new RepoPathImpl(repo.getKey(), path));
            if (!itemInfo.isFolder()) {
                log.warn("Expected folder but got file: {}", InternalRepoPathFactory.create(repo.getKey(), path));
                continue;
            }
            List<ItemInfo> items = getRepositoryService().getChildren(itemInfo.getRepoPath());
            for (ItemInfo item : items) {
                if (!MavenNaming.NEXUS_INDEX_DIR.equals(item.getName()) ||
                        MavenNaming.isIndex(item.getRelPath())) {  // don't include the index dir in the listing
                    children.add(item.getName());
                }
            }
        }
        return children;
    }

    public List<VirtualRepo> getResolvedVirtualRepos() {
        //Add items from contained virtual repositories
        List<VirtualRepo> virtualRepos = new ArrayList<>();
        //Assemble the virtual repo deep search lists
        resolveVirtualRepos(virtualRepos);
        return virtualRepos;
    }

    private void resolveVirtualRepos(List<VirtualRepo> repos) {
        if (repos.contains(this)) {
            return;
        }
        repos.add(this);
        List<VirtualRepo> childrenVirtualRepos = getVirtualRepositories();
        for (VirtualRepo childVirtualRepo : childrenVirtualRepos) {
            if (!repos.contains(childVirtualRepo)) {
                childVirtualRepo.resolveVirtualRepos(repos);
            }
        }
    }

    public Set<RemoteRepo> getResolvedRemoteRepos() {
        List<RemoteRepo<?, ?>> repositories = virtualRepositoriesResolver.getResolvedRemoteRepos();
        return new LinkedHashSet<>(repositories);
    }

    /**
     * @return Recursively resolved list of all the local non-cache and federated repos of this virtual repo and nested
     * virtual repos.
     */
    public Set<LocalRepo> getResolvedLocalAndFederatedRepos() {
        List<LocalRepo<?, ?>> repositories = virtualRepositoriesResolver.getResolvedLocalAndFederatedRepos();
        return new LinkedHashSet<>(repositories);
    }

    /**
     * @return Recursively resolved list of all the cache repos of this virtual repo and nested virtual repos.
     */
    public Set<LocalCacheRepo> getResolvedLocalCachedRepos() {
        List<LocalCacheRepo> repositories = virtualRepositoriesResolver.getResolvedLocalCacheRepos();
        return new LinkedHashSet<>(repositories);
    }

    /**
     * @return Recursively resolved list of all the local and cache repos of this virtual repo and nested virtual
     * repos.
     */
    public Set<LocalRepo> getResolvedLocalFederatedAndCachedRepos() {
        Set<LocalRepo> localFederatedAndCachedRepos = Sets.newLinkedHashSet();
        localFederatedAndCachedRepos.addAll(getResolvedLocalAndFederatedRepos());
        localFederatedAndCachedRepos.addAll(getResolvedLocalCachedRepos());
        return localFederatedAndCachedRepos;
    }

    /**
     * Inidicates if the given path exists
     *
     * @param relPath Relative path to item
     * @return True if repo path exists, false if not
     */
    public boolean virtualItemExists(String relPath) {
        //Check if item exists in each repo
        List<LocalRepo> repoList = getLocalAndCachedRepositories();
        for (LocalRepo localRepo : repoList) {
            if (localRepo.itemExists(relPath)) {
                return true;
            }
        }

        return false;
    }

    public boolean virtualItemExistsIncludingNested(String relPath) {
        var repoList = getResolvedLocalFederatedAndCachedRepos();
        return repoList.stream()
                .anyMatch(repo -> repo.itemExists(relPath));
    }

    private List<LocalRepo> getLocalAndCachedRepositories() {
        List<LocalRepo> localRepos = getLocalRepositories();
        List<LocalCacheRepo> localCaches = getLocalCaches();
        List<LocalRepo> repos = new ArrayList<>(localRepos);
        repos.addAll(localCaches);
        return repos;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public InputStream getFileContent(String path) {
        RepoPath repoPath = RepoPathFactory.create(getKey(), path);
        try {
            VfsFile<?> file = dbStorageMixin.getImmutableFile(repoPath);
            return file.getStream();
        } catch (FileExpectedException e) {
            log.error("Invalid request trying to get content from directory", e);
        } catch (Exception e) {
            log.error("Failed to retrieve content for for file in:" + repoPath, e);
        }
        return null;
    }

    @Override
    public boolean isPriorityResolution() {
        return false;
    }

    /**
     * This method is called after the retrieval of {@param retrievedResource} from the virtual cache, it may or may not
     * have been found (thus nullable) - it provides interceptors with the ability to immediately return the cached
     * resource without going through the normal flow of the virtual download strategy.
     *
     * @param requestContext The context used to retrieve this {@param retrievedResource}
     */
    boolean interceptShouldReturnCachedResource(InternalRequestContext requestContext) {
        for (VirtualRepoInterceptor interceptor : interceptors) {
            RepoRequests.logToContext("Intercepting cached virtual resource with '%s'",
                    interceptor.getClass().getSimpleName());
            if (interceptor.shouldReturnCachedResource(this, requestContext.getResourcePath(),
                    requestContext.isFromAnotherArtifactory())) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is called before the retrieval of {@param retrievedResource} from the virtual cache,
     * it provides interceptors with the ability to initiate virtual metadata calculation.
     */
    void interceptPreCalculateVirtualMetadata(InternalRequestContext requestContext) {
        for (VirtualRepoInterceptor interceptor : interceptors) {
            RepoRequests.logToContext("Intercepting cached virtual resource with '%s'",
                    interceptor.getClass().getSimpleName());
            interceptor.preCalculateVirtualMetadata(this, requestContext.getResourcePath(),
                    requestContext.isFromAnotherArtifactory());
        }
    }

    /**
     * This method is called when a resource was found in the searchable repositories, before returning it to the
     * client. This method will call a list of interceptors that might alter the returned resource and cache it
     * locally.
     *
     * @param context       The request context
     * @param foundResource The resource that was found in the searchable repositories.
     * @return Original or transformed resource
     */
    protected RepoResource interceptBeforeReturn(InternalRequestContext context, RepoResource foundResource) {
        for (VirtualRepoInterceptor interceptor : interceptors) {
            RepoRequests.logToContext("Intercepting found resource with '%s'", interceptor.getClass().getSimpleName());
            foundResource = interceptor.onBeforeReturn(this, context, foundResource);
        }
        return foundResource;
    }

    RepoResource interceptGetInfo(InternalRequestContext context, RepoPath repoPath,
            List<RealRepo<?, ?>> repositories) {
        for (VirtualRepoInterceptor interceptor : interceptors) {
            RepoRequests.logToContext("Intercepting info request with '%s'", interceptor.getClass().getSimpleName());
            RepoResource repoResource = interceptor.interceptGetInfo(this, context, repoPath, repositories);
            if (repoResource != null) {
                RepoRequests
                        .logToContext("Info request was intercepted by '%s'", interceptor.getClass().getSimpleName());
                return repoResource;
            }
        }
        return null;
    }


    /**
     * Returns the repo resource cached in the virtual repository cache.
     */
    public RepoResource getInfoFromVirtualCache(InternalRequestContext context) {
        return dbStorageMixin.getInfo(context);
    }

    //STORING REPO MIXIN

    @Override
    public ChecksumPolicy getChecksumPolicy() {
        return defaultChecksumPolicy;
    }

    @Override
    public void undeploy(DeleteContext ctx) {
        undeploy(ctx, new BasicStatusHolder());
    }

    @Override
    public void undeploy(DeleteContext ctx, BasicStatusHolder statusHolder) {
        dbStorageMixin.undeploy(ctx, statusHolder);
    }

    @Override
    public RepoResource saveResource(SaveResourceContext context) throws IOException, RepoRejectException {
        return dbStorageMixin.saveResource(context);
    }

    @Override
    public boolean shouldProtectPathDeletion(PathDeletionContext pathDeletionContext) {
        // permissions only apply for real repositories. other methods (i.e., webdav delete) shouldn't allow virtual
        // repo deletion from the outside.
        return false;
    }

    @Override
    public ModuleInfo getArtifactModuleInfo(String artifactPath) {
        return super.getArtifactModuleInfo(artifactPath);
    }

    @Override
    public boolean itemExists(String relPath) {
        return dbStorageMixin.itemExists(relPath);
    }

    @Override
    public @NonNull List<RepoPath> getExistingItems(@NonNull List<RepoPath> repoPaths) {
        return dbStorageMixin.filterExistingRepoPaths(repoPaths);
    }

    @Override
    public RepoResource getInfo(InternalRequestContext context) throws FileExpectedException {
        VirtualDownloadStrategyType downloadStrategyType = chooseDownloadStrategy(context);
        try {
            RepoResource res = downloadStrategy.get(downloadStrategyType).getInfo(context);
            // Decorate the response if necessary
            checkAndMarkExpirableResource(res, context.getRequest().getRepoPath());
            return res;
        } catch (PackageVirtualTraversalException e) {
            String msg = String.format("Failed to resolve artifact from virtual repo:%s, path:%s, status:%s, reason:%s",
                    context.getRepoKey(), context.getResourcePath(), e.getStatus(), e.getInternalReason());
            RepoPath repoPath = RepoPathFactory.create(context.getRepoKey(), context.getPath());
            if (e.getStatus() == HttpStatus.SC_NOT_FOUND) {
                return new UnfoundRepoResource(repoPath, e.getInternalReason(), HttpStatus.SC_NOT_FOUND);
            }
            log.error(msg, e);
            return new UnfoundRepoResource(repoPath, e.getInternalReason(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private VirtualDownloadStrategyType chooseDownloadStrategy(InternalRequestContext context) {
        // Set default download strategy
        VirtualDownloadStrategyType downloadStrategyType = VirtualDownloadStrategyType.artifactoryResolution;
        if (packageVirtualResolutionService != null
                && packageVirtualResolutionService.shouldUsePackageVirtualResolution(context)) {
            downloadStrategyType = VirtualDownloadStrategyType.packageResolution;
        }
        return downloadStrategyType;
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, RepoResource res)
            throws IOException, RepoRejectException {
        return dbStorageMixin.getResourceStreamHandle(requestContext, res);
    }

    @Override
    public MutableVfsFile getMutableFile(RepoPath repoPath) {
        return dbStorageMixin.getMutableFile(repoPath);
    }

    @Override
    @NonNull
    public MutableVfsFile createOrGetFile(RepoPath repoPath) {
        return dbStorageMixin.createOrGetFile(repoPath);
    }

    @Override
    public MutableVfsFolder getMutableFolder(RepoPath repoPath) {
        return dbStorageMixin.getMutableFolder(repoPath);
    }

    @Override
    @NonNull
    public MutableVfsFolder createOrGetFolder(RepoPath repoPath) {
        return dbStorageMixin.createOrGetFolder(repoPath);
    }

    @Override
    public VfsItem getImmutableFsItem(RepoPath repoPath) {
        return dbStorageMixin.getImmutableItem(repoPath);
    }

    @NonNull
    @Override
    public Map<RepoPath, Optional<VfsItem<ItemInfo>>> getImmutableFsItems(@NonNull List<RepoPath> repoPaths) {
        return dbStorageMixin.getImmutableItems(repoPaths);
    }

    @Override
    public MutableVfsItem getMutableFsItem(RepoPath repoPath) {
        return dbStorageMixin.getMutableItem(repoPath);
    }

    @Nullable
    @Override
    public MutableVfsItem getMutableFsItem(VfsItem vfsItem, ItemInfo originalItemInfo, Properties originalProperties) {
        throw new UnsupportedOperationException("NOT IMPLEMENTED");
    }

    @Override
    public VfsFile getImmutableFile(RepoPath repoPath) {
        return dbStorageMixin.getImmutableFile(repoPath);
    }

    @Override
    public VfsFolder getImmutableFolder(RepoPath repoPath) {
        return dbStorageMixin.getImmutableFolder(repoPath);
    }

    public boolean isCachedResourceExpired(RepoResource virtualCachedIndex) {
        long retrievalCachePeriodMillis = getVirtualCacheTimeoutMillis();
        long cacheAge = virtualCachedIndex.getCacheAge();
        return cacheAge > retrievalCachePeriodMillis || cacheAge == -1;
    }

    public long getVirtualCacheTimeoutMillis() {
        VirtualCacheConfig cacheConfig =
                Optional.ofNullable(getRepoConfig().getRepoTypeConfig().getVirtualCacheConfig())
                        .orElse(new VirtualCacheConfig());
        return TimeUnit.SECONDS.toMillis(cacheConfig.getVirtualRetrievalCachePeriodSecs());
    }
}
