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

package org.artifactory.repo.db;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.snapshot.MavenSnapshotVersionAdapter;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * A remote cache repository implementation backed by db.
 *
 * @author Yossi Shaul
 */
public class DbCacheRepo extends DbLocalRepo<LocalCacheRepoDescriptor, LocalCacheRepoConfig>
        implements LocalCacheRepo {
    private static final Logger log = LoggerFactory.getLogger(DbCacheRepo.class);

    private final RemoteRepo<? extends RemoteRepoDescriptor, ? extends RemoteRepoConfig>
            remoteRepo;
    private AddonsManager addonsManager;

    public DbCacheRepo(RemoteRepo<? extends RemoteRepoDescriptor, ? extends RemoteRepoConfig> remoteRepo,
            DbCacheRepo oldCacheRepo, CentralConfigService configService) {
        super(createCacheDescriptorFromRemote(remoteRepo.getDescriptor()),
                createCacheConfigFromRemote(remoteRepo.getRepoConfig()), remoteRepo.getRepositoryService(),
                oldCacheRepo, configService);
        this.remoteRepo = remoteRepo;
        this.addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
    }

    private static LocalCacheRepoDescriptor createCacheDescriptorFromRemote(RemoteRepoDescriptor remoteDescriptor) {
        // create descriptor on-the-fly since this repo is created by a remote repo
        LocalCacheRepoDescriptor descriptor = new LocalCacheRepoDescriptor();
        if (remoteDescriptor.getDescription() != null &&
                !remoteDescriptor.getDescription().contains("(local file cache)")) {
            remoteDescriptor.setDescription(remoteDescriptor.getDescription() + " (local file cache)");
        }

        descriptor.setKey(remoteDescriptor.getKey() + LocalCacheRepoDescriptor.PATH_SUFFIX);
        descriptor.setRemoteRepo(remoteDescriptor);
        descriptor.setRepoLayout(remoteDescriptor.getRepoLayout());
        return descriptor;
    }

    private boolean isBlockDownloadEnabled() {
        return getCurationAddon().isBlockDownloadEnabled(getRemoteRepoConfig());
    }

    private static LocalCacheRepoConfig createCacheConfigFromRemote(RemoteRepoConfig remoteRepoConfig) {
        // create descriptor on-the-fly since this repo is created by a remote repo
        return LocalCacheRepoConfig.create(remoteRepoConfig);
    }

    @Override
    public boolean isCache() {
        return true;
    }

    @Override
    public InputStream getFileContent(String path) {
        RepoPath repoPath = RepoPathFactory.create(getKey(), path);
        try {
            VfsFile<?> file = mixin.getImmutableFile(repoPath);
            return file.getStream();
        } catch (FileExpectedException e) {
            log.error("Invalid request trying to get content from directory", e);
        } catch (Exception e) {
            log.error("Failed to retrieve content for for file in:" + repoPath, e);
        }
        return null;
    }

    @Override
    public RepoResource getInfo(InternalRequestContext context) throws FileExpectedException {
        RepoResource repoResource = super.getInfo(context);
        if (repoResource.isFound()) {
            //Check for expiry
            RepoRequests.logToContext("Found the resource in the cache - checking for expiry");
            boolean forceDownloadIfNewer = false;
            Request request = context.getRequest();
            if (request != null) {
                String forcePropValue = request.getParameter(ArtifactoryRequest.PARAM_FORCE_DOWNLOAD_IF_NEWER);
                if (StringUtils.isNotBlank(forcePropValue)) {
                    forceDownloadIfNewer = Boolean.parseBoolean(forcePropValue);
                    RepoRequests.logToContext("Found request parameter {}=%s",
                            ArtifactoryRequest.PARAM_FORCE_DOWNLOAD_IF_NEWER, forceDownloadIfNewer);
                }
            }

            if (forceDownloadIfNewer || context.isForceExpiryCheck() || isExpired(repoResource)) {
                RepoRequests.logToContext("Returning resource as expired");
                repoResource = new ExpiredRepoResource(repoResource);
            } else {
                RepoRequests.logToContext("Returning cached resource");
            }
        }
        return repoResource;
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, RepoResource res)
            throws IOException {
        ResourceStreamHandle resourceStreamHandle = super.getResourceStreamHandle(requestContext, res);
        if (isBlockDownloadEnabled()) {
            log.debug("Block download enabled - skipping CachedPackageDownloaded event");
        } else {
            log.debug("Block download disabled - sending CachedPackageDownloaded event");
            notifyCachedPackageDownloadedForCuration(res.getRepoPath());
        }
        return resourceStreamHandle;
    }

    private void notifyCachedPackageDownloadedForCuration(RepoPath repoPath) {
        getCurationAddon().sendCachedPackageEvent(getRemoteRepoConfig(), repoPath);
    }

    private CurationAddon getCurationAddon() {
        return addonsManager.addonByType(CurationAddon.class);
    }

    @Override
    public void undeploy(DeleteContext ctx) {
        undeploy(ctx, new BasicStatusHolder());
    }

    @Override
    public void undeploy(DeleteContext ctx, BasicStatusHolder statusHolder) {
        // change from remote key to cache key and never calculate maven metadata on cache
        RepoPath cacheRepoPath = new RepoPathImpl(this.getKey(), ctx.getRepoPath().getPath());
        mixin.undeploy(new DeleteContext(cacheRepoPath).triggerMetadataCalculation(ctx.isTriggerMetadataCalculation()),
                statusHolder);
    }

    @Override
    public RemoteRepo<? extends RemoteRepoDescriptor, ? extends RemoteRepoConfig> getRemoteRepo() {
        return remoteRepo;
    }

    @Override
    public ChecksumPolicy getChecksumPolicy() {
        return remoteRepo.getChecksumPolicy();
    }

    @Override
    public RemoteRepoDescriptor getRemoteRepoDescriptor() {
        return remoteRepo.getDescriptor();
    }

    @Override
    public RemoteRepoConfig getRemoteRepoConfig() {
        return remoteRepo.getRepoConfig();
    }

    @Override
    public void unexpire(String path) {
        //Reset the resource age so it is kept being cached
        MutableVfsItem mutableItem = getMutableFsItem(getRepoPath(path));
        if (mutableItem != null) {
            mutableItem.setUpdated(System.currentTimeMillis());
        } else {
            log.error("Attempt to unexpire non existent resource: {}", path);
        }
    }

    @Override
    public SnapshotVersionBehaviorDescriptor getMavenSnapshotVersionBehavior() {
        return SnapshotVersionBehaviorDescriptor.valueOf(
                getRepoConfig().getRepoTypeConfig().getSnapshotVersionBehavior().name());
    }

    @Override
    public MavenSnapshotVersionAdapter getMavenSnapshotVersionAdapter() {
        throw new UnsupportedOperationException("Local cache repositories doesn't have snapshot version adapter");
    }

    /**
     * Check that the item has not expired yet, unless it's a release which never expires or a unique snapshot.
     *
     * @param repoResource The resource to check for expiry
     * @return boolean - True if resource is expired. False if not
     */
    protected boolean isExpired(RepoResource repoResource) {
        String path = repoResource.getRepoPath().getPath();
        CacheExpiry cacheExpiry = ContextHelper.get().beanForType(CacheExpiry.class);
        RepoType packageType = getRepoConfig().getPackageType();
        if (!remoteRepo.isOffline() && (repoResource.isExpirable() ||
                cacheExpiry.isExpirable(packageType, this.getKey(), path))) {
            long retrievalCachePeriodMillis = getRetrievalCachePeriodMillis(path);
            long cacheAge = repoResource.getCacheAge();
            return cacheAge > retrievalCachePeriodMillis || cacheAge == -1;
        }
        return false;
    }

    private long getRetrievalCachePeriodMillis(String path) {
        long retrievalCachePeriodMillis;
        if (MavenNaming.isIndex(path) &&
                remoteRepo.getUrl().contains(ConstantValues.mvnCentralHostPattern.getString())) {
            //If it is a central maven index use the hardcoded cache value
            long centralMaxQueryIntervalSecs = ConstantValues.mvnCentralIndexerMaxQueryIntervalSecs.getLong();
            retrievalCachePeriodMillis = centralMaxQueryIntervalSecs * 1000L;
        } else {
            //It is a non-unique snapshot or snapshot metadata
            retrievalCachePeriodMillis = remoteRepo.getRetrievalCachePeriodSecs() * 1000L;
        }
        return retrievalCachePeriodMillis;
    }
}
