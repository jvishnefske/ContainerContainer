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

package org.artifactory.repo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.RestCoreAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatusPayload;
import org.artifactory.addon.curation.SecurityDecision;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResourceStreamCtx;
import org.artifactory.addon.plugin.download.AltRemoteContentAction;
import org.artifactory.addon.plugin.download.AltRemotePathAction;
import org.artifactory.addon.plugin.download.PathCtx;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.api.search.exception.InvalidChecksumException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.ChecksumPolicyTypeDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.download.RateLimiterException;
import org.artifactory.engine.ChecksumDeployPermissionValidator;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.exceptions.IllegalUrlPathException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.RemoteResourceStreamHandle;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.io.checksum.policy.ChecksumPolicyBase;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.md.Properties;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.properties.PropertiesImplWrapper;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.db.DbStoringRepoMixin;
import org.artifactory.repo.local.ValidDeployPathContext;
import org.artifactory.repo.multipart.ParallelDownloadService;
import org.artifactory.repo.multipart.PreChecksummedResourceStreamHandle;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.remote.interceptor.ExpiryAction;
import org.artifactory.repo.remote.interceptor.LastModifiedExpiryRemoteInterceptor;
import org.artifactory.repo.remote.interceptor.RemoteRepoInterceptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.AlteredDownloadRequestContext;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.request.UrlVerifier;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.ResourceWithChecksumStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason;
import org.artifactory.resource.UnfoundRepoResourceReason.Reason;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.statistics.CacheStatisticsPrintScheduledLogger;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.entry.UploadEntry;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.HttpClientUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.StreamUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.storage.StorageException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Boolean.parseBoolean;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.api.request.InternalRequestFactory.createInternalRequestDisableRedirect;
import static org.artifactory.repo.db.DbStoringRepoMixin.ORIGIN_REMOTE_PATH_PROP_KEY;
import static org.artifactory.request.ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL;
import static org.artifactory.request.ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_SITE_URL;
import static org.artifactory.request.ArtifactoryRequest.PARAM_REPLICATION_DOWNLOAD_REQUEST;
import static org.artifactory.request.ArtifactoryRequest.PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST;
import static org.artifactory.request.ArtifactoryRequest.PARAM_SEARCH_FOR_EXISTING_RESOURCE_ON_REMOTE_REQUEST;
import static org.artifactory.util.RepoPathUtils.addTrailingSlashIfNeeded;
import static org.artifactory.util.RepoResourceUtils.isContentEncoded;
import static org.artifactory.util.UploadServiceUtils.fireUploadTrafficEventV2;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CONTENT_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;

/**
 * @author yoavl
 */
public abstract class RemoteRepoBase<T extends RemoteRepoDescriptor,
        S extends RemoteRepoConfig> extends RealRepoBase<T, S> implements RemoteRepo<T, S> {
    private static final String REMOTE_DOWNLOADERS = "remoteDownloaders";
    private static final String BRIDGE_PROPERTIES_STORAGE_QUERY = "propertiesXml";
    private static final String MSG_CURATION_NO_LOCAL_CACHE_HEAD_ONLY =
            "Package permitted: no local cache repository for HEAD-only request";
    private static final Logger log = LoggerFactory.getLogger(RemoteRepoBase.class);
    private static final Logger debLog = LoggerFactory.getLogger("DEBLOG");
    private final ChecksumPolicy checksumPolicy;
    final OnlineMonitorService onlineMonitorService;
    /**
     * Flags this repository as assumed offline. The repository enters this state when a download request fails with
     * exception.
     */
    volatile AtomicBoolean assumedOffline = new AtomicBoolean(false);
    protected LocalCacheRepo localCacheRepo;
    private RemoteRepoBase oldRemoteRepo;

    /**
     * Cache of resources not found on the remote machine. Keyed by resource path.
     * Lazily initialized on first access via {@link #getMissedRetrievalsCache()}.
     */
    private volatile Cache<String, RepoResource> missedRetrievalsCache;

    /**
     * Cache of remote directories listing.
     * Lazily initialized on first access via {@link #getRemoteResourceCache()}.
     */
    private volatile Cache<String, List<RemoteItem>> remoteResourceCache;

    private final Object missedCacheLock = new Object();
    private final Object remoteCacheLock = new Object();

    private long missedRetrievalsCacheExpirationSecs;
    private long remoteResourceCacheExpirationSecs;
    private boolean shouldRecordCacheStats;
    // List of interceptors for various download resolution points
    private Collection<RemoteRepoInterceptor> remoteRepoInterceptors;
    private final CacheStatisticsPrintScheduledLogger missedRetrievalsCacheStatisticsLogger;

    private final PackagesCurationCompliantVersionService curationCompliantVersionService;

    protected RemoteRepoBase(T descriptor, S repoConfig, InternalRepositoryService repositoryService,
            RemoteRepo oldRemoteRepo, OnlineMonitorService onlineMonitorService,
            CentralConfigService configService,
            @Nullable PackagesCurationCompliantVersionService curationCompliantVersionService) {
        super(descriptor, repoConfig, repositoryService, configService);
        this.onlineMonitorService = onlineMonitorService;
        org.artifactory.repo.ChecksumPolicyType checksumPolicyTypeConfig =
                repoConfig.getRepoTypeConfig().getChecksumPolicyType();
        ChecksumPolicyTypeDescriptor checksumPolicyType =
                ChecksumPolicyTypeDescriptor.valueOf(checksumPolicyTypeConfig.name());
        checksumPolicy = ChecksumPolicyBase.getByType(checksumPolicyType);
        if (oldRemoteRepo instanceof RemoteRepoBase) {
            this.oldRemoteRepo = (RemoteRepoBase) oldRemoteRepo;
        } else {
            this.oldRemoteRepo = null;
        }
        if (isNonMavenRepo()) {
            excludes.addAll(Lists.newArrayList("**/*.pom", "**/*.jar", "**/maven-metadata.xml"));
        }
        String repoKey = getRepoConfig().getKey();
        this.missedRetrievalsCacheStatisticsLogger =
                new CacheStatisticsPrintScheduledLogger("Remote missed retrievals cache for " + repoKey,
                        ConstantValues.remoteRepoMissedCacheStatsPrintIntervalMillis.getLong());
        patternExcludes = createPattern(excludes);
        this.curationCompliantVersionService = curationCompliantVersionService;
    }

    protected RemoteRepoBase(T descriptor, S repoConfig, InternalRepositoryService repositoryService,
            RemoteRepo<?, ?> oldRemoteRepo, OnlineMonitorService onlineMonitorService,
            CentralConfigService configService) {
        this(descriptor, repoConfig, repositoryService, oldRemoteRepo, onlineMonitorService, configService, null);
    }

    @Override
    public void init(boolean existenceValidated) {
        if (isStoreArtifactsLocally()) {
            DbCacheRepo oldCacheRepo = null;
            if (oldRemoteRepo != null) {
                oldCacheRepo = (DbCacheRepo) oldRemoteRepo.localCacheRepo;
            }
            //Initialize the local cache
            localCacheRepo = new DbCacheRepo(this, oldCacheRepo, configService);
            localCacheRepo.init(existenceValidated);
        }
        initCaches();
        logCacheInfo();
        // Clean the old repo not needed anymore
        oldRemoteRepo = null;
        remoteRepoInterceptors = ContextHelper.get().beansForType(RemoteRepoInterceptor.class).values();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (isStoreArtifactsLocally()) {
            localCacheRepo.destroy();
        }
        clearCaches();
    }

    protected void initCaches() {
        RemoteRepositoryRepoTypeConfig repoTypeConfig = getRepoConfig().getRepoTypeConfig();
        this.shouldRecordCacheStats = ConstantValues.remoteRepoCacheStatisticsEnabled.getBoolean();
        this.missedRetrievalsCacheExpirationSecs = repoTypeConfig.getMissedRetrievalCachePeriodSecs();
        this.remoteResourceCacheExpirationSecs = repoTypeConfig.getRetrievalCachePeriodSecs();
    }

    private Cache<String, RepoResource> getMissedRetrievalsCache() {
        Cache<String, RepoResource> cache = missedRetrievalsCache;
        if (cache == null) {
            synchronized (missedCacheLock) {
                cache = missedRetrievalsCache;
                if (cache == null) {
                    cache = buildCache(missedRetrievalsCacheExpirationSecs, false);
                    missedRetrievalsCache = cache;
                }
            }
        }
        return cache;
    }

    private Cache<String, List<RemoteItem>> getRemoteResourceCache() {
        Cache<String, List<RemoteItem>> cache = remoteResourceCache;
        if (cache == null) {
            synchronized (remoteCacheLock) {
                cache = remoteResourceCache;
                if (cache == null) {
                    cache = buildCache(remoteResourceCacheExpirationSecs, true);
                    remoteResourceCache = cache;
                }
            }
        }
        return cache;
    }

    private <V> Cache<String, V> buildCache(long expirationSeconds, boolean softValues) {
        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        if (expirationSeconds >= 0) {
            cacheBuilder.expireAfterWrite(expirationSeconds, TimeUnit.SECONDS);
        }
        if (softValues) {
            cacheBuilder.softValues();
        }
        if (shouldRecordCacheStats) {
            cacheBuilder.recordStats();
        }
        return cacheBuilder.build();
    }

    private void logCacheInfo() {
        long retrievalCachePeriodSecs = getRepoConfig().getRepoTypeConfig().getRetrievalCachePeriodSecs();
        if (retrievalCachePeriodSecs > 0) {
            log.debug("{}: Retrieval cache will be enabled with period of {} seconds",
                    this, retrievalCachePeriodSecs);
        } else {
            log.debug("{}: Retrieval cache will be disabled.", this);
        }
        long missedRetrievalCachePeriodSecs = getRepoConfig().getRepoTypeConfig().getMissedRetrievalCachePeriodSecs();
        if (missedRetrievalCachePeriodSecs > 0) {
            log.debug("{}: Enabling misses retrieval cache with period of {} seconds",
                    this, missedRetrievalCachePeriodSecs);
        } else {
            log.debug("{}: Disabling misses retrieval cache", this);
        }
    }

    @Override
    public boolean isStoreArtifactsLocally() {
        return getRepoConfig().getRepoTypeConfig().isStoreArtifactsLocally();
    }

    @Override
    public String getUrl() {
        return getRepoConfig().getRepoTypeConfig().getUrl();
    }

    @Override
    public boolean isOffline() {
        return getRepoConfig().getRepoTypeConfig().isOffline() || configService.getDescriptor().isOfflineMode()
                || isAssumedOffline();
    }

    @Override
    public boolean isConfiguredOffline() {
        return getRepoConfig().getRepoTypeConfig().isOffline() || configService.getDescriptor().isOfflineMode();
    }

    @Override
    public boolean isAssumedOffline() {
        boolean offline = assumedOffline.get();
        createRepoMonitorIfNeeded(offline);
        return offline;
    }

    private void createRepoMonitorIfNeeded(boolean assumedOffline) {
        if (assumedOffline && !onlineMonitorService.isRemoteRepoMonitored(getKey())) {
            // in case the remote repository is assumed offline but not being monitored, creating monitor to
            // eventually allow the repository to come back online.
            log.warn("Repository {} is assumed offline, but not being monitored. Creating repo monitor.", getKey());
            putOffline();
        }
    }

    @Override
    public boolean isListRemoteFolderItems() {
        RemoteRepositoryRepoTypeConfig repoTypeConfig = getRepoConfig().getRepoTypeConfig();
        return repoTypeConfig.isListRemoteFolderItems() && !repoTypeConfig.isBlackedOut() && !isOffline();
    }

    @Override
    public long getRetrievalCachePeriodSecs() {
        return getRepoConfig().getRepoTypeConfig().getRetrievalCachePeriodSecs();
    }

    @Override
    public long getAssumedOfflinePeriodSecs() {
        return getRepoConfig().getRepoTypeConfig().getAssumedOfflinePeriodSecs();
    }

    @Override
    public ChecksumPolicy getChecksumPolicy() {
        return checksumPolicy;
    }

    /**
     * Retrieve the (metadata) information about the artifact, unless still cached as failure or miss. Reach this point
     * only if local and cached repo did not find resource or expired.
     *
     * @param context The request context holding additional parameters
     * @return A repository resource updated with the up-to-date metadata
     */
    @Override
    public RepoResource getInfo(InternalRequestContext context) throws FileExpectedException {
        AddonsManager addonsManager = InternalContextHelper.get().beanForType(AddonsManager.class);
        RestCoreAddon restCoreAddon = addonsManager.addonByType(RestCoreAddon.class);
        context = restCoreAddon.getDynamicVersionContext(this, context, true);

        String path = context.getResourcePath();
        // make sure the repo key is of this repository
        RepoPath repoPath = InternalRepoPathFactory.create(getKey(), path);
        if (debLog.isDebugEnabled()) {
            debLog.debug("IP '{}' requesting file '{}'", HttpUtils.getRemoteClientAddress(), repoPath);
        }
        //Skip if in blackout or not accepting/handling or cannot download
        RepoResource repoResource = checkResourceStatusBeforeDownload(context, repoPath);
        if (repoResource != null) {
            return repoResource;
        }
        //Never query remote checksums
        if (NamingUtils.isChecksum(path)) {
            RepoRequests.logToContext("Download denied - checksums are not downloadable");
            return new UnfoundRepoResource(repoPath, "Checksums are not downloadable.");
        }

        //Try to get it from the caches - check for known miss first
        RepoResource res = getMissedResource(path);
        if (res == null) {
            // check for cached
            res = internalGetInfo(repoPath, context);
            if (res.isBlocked()) {
                return res;
            }
        }

        //If we cannot get the resource remotely and an expired (otherwise we would not be
        //attempting the remote repo at all), cache entry exists use it by unexpiring it.
        if (res.isExpired() && isStoreArtifactsLocally()) {
            RepoRequests.logToContext("Hosting repository stores locally and the resource is expired - " +
                    "un-expiring if still exists");

            String actualPath = getResourceOfflineRepoPath(path, res.getRepoPath().getPath());
            res = getRepositoryService().unexpireIfExists(localCacheRepo, actualPath);
        }

        Optional<RepoResource> curatedRepoResource = getCuratedRepoResource(context, res);
        if (curatedRepoResource.isPresent()) {
            RepoResource curatedRes = curatedRepoResource.get();
            checkAndMarkExpirableResource(curatedRes, context.getRequest().getRepoPath());
            return curatedRes;
        }

        return checkResourceStatusAfterDownload(context, res, repoPath);
    }

    @VisibleForTesting
    Optional<RepoResource> getCuratedRepoResource(InternalRequestContext context, RepoResource res) {
        try {
            if (curationCompliantVersionService != null &&
                    curationCompliantVersionService.canCallFilterBlockedVersions(res, this)) {
                RepoResource curatedRepoResource =
                        curationCompliantVersionService.getCuratedMetadataResource(context, this, res);
                if (curatedRepoResource == null) {
                    log.warn("Failed to get curated metadata for resource {}",
                            res.getRepoPath());
                } else {
                    return Optional.of(curatedRepoResource);
                }

            }
        } catch (PackageBlockedByCurationException e) {
            return Optional.of(new BlockedRepoResource(
                    res.getRepoPath(), e.getPublicReason(), BlockedRepoResource.BlockedType.CURATION));
        } catch (Exception e) {
            log.warn("Failed to get curated metadata for resource {} with message: {}",
                    res.getRepoPath(), e.getMessage());
            log.debug("", e);
        }
        return Optional.empty();
    }

    String getResourceOfflineRepoPath(String path, String repoPath) {
        return ConstantValues.useRepoPathOnRemoteOffline.getBoolean() ? repoPath : path;
    }

    RepoResource internalGetInfo(RepoPath repoPath, InternalRequestContext context) {
        String path = repoPath.getPath();
        RepoResource cachedResource = null;
        // first try to get it from the local cache repository
        if (isStoreArtifactsLocally() && localCacheRepo != null) {
            try {
                cachedResource = getInfoFromCache(context);
                if (cachedResource instanceof UnfoundRepoResourceReason &&
                        ((UnfoundRepoResourceReason) cachedResource).getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                    return cachedResource;
                }
            } catch (FileExpectedException e) {
                // rethrow using the remote repo path
                throw new FileExpectedException(repoPath);
            }
        }
        if (shouldReturnCachedResource(context, cachedResource)) {
            return returnCachedResource(repoPath, cachedResource, context);
        }
        boolean foundExpiredInCache = ((cachedResource != null) && cachedResource.isExpired());
        //not found in local cache - try to get it from the remote repository
        if (!isOffline()) {
            CurationCachedPackageStatus packageStatus =
                    checkBlockedByCuration(context.getRequest(), repoPath, null, null, true);
            if (!packageStatus.status().permitted()) {
                return new BlockedRepoResource(repoPath, packageStatus.status().message(),
                        BlockedRepoResource.BlockedType.CURATION);
            }
            boolean remoteDownloadAllowed = isRemoteDownloadAllowed(repoPath);
            if (!remoteDownloadAllowed) {
                RepoRequests.logToContext("Remote resource not allowed based on one of the interceptors");
                return foundExpiredInCache ? returnCachedResource(repoPath, cachedResource, context) :
                        new UnfoundRepoResource(repoPath,
                                "Remote download of " + repoPath + " is not allowed by this repo");
            }

            RepoResource remoteResource = getRemoteResource(context, repoPath, foundExpiredInCache);
            if (!remoteResource.isFound() && foundExpiredInCache) {
                RepoRequests.logToContext("Resource doesn't exist remotely but is expired in the caches - " +
                        "returning expired cached resource");
                remoteResource = returnCachedResource(repoPath, cachedResource, context);
            }
            if (remoteResource.isFound() && context.getRequest().isZipResourceRequest()) {
                // there's a newer remote resource that should be downloaded for zip resources
                return getRemoteZipRepoResource(repoPath, context);
            }

            if (remoteResource instanceof RemoteRepoResource && foundExpiredInCache) {
                RemoteRepoResource remoteRepoResource = (RemoteRepoResource) remoteResource;
                remoteRepoResource.setCorrespondingCachedResource(cachedResource);
                unExpireCachedResourceIfNeeded(remoteRepoResource, context);
            }
            return remoteResource;
        } else if (foundExpiredInCache) {
            RepoRequests.logToContext("Repository is offline but the resource exists in the local cache - " +
                    "returning cached resource");
            //Return the cached resource if remote fetch failed
            return returnCachedResource(repoPath, cachedResource, context);
        } else {
            String offlineMessage = isAssumedOffline() ? "assumed offline" : "offline";
            RepoRequests.logToContext("Repository is " + offlineMessage + " and the resource doesn't exist in the " +
                    "local cache - returning unfound resource");
            RemoteRepoOfflineGuardService offlineGuardService =
                    InternalContextHelper.get().beanForType(RemoteRepoOfflineGuardService.class);
            if (offlineGuardService.isOfflinedByGuard(getKey())) {
                return new UnfoundRepoResource(repoPath,
                        "This request was blocked because traffic is being proxied through a third-party tool in "
                                + "violation of the JFrog EULA. Contact JFrog support for further discussion.",
                        HttpStatus.SC_FORBIDDEN);
            }
            return new UnfoundRepoResource(repoPath,
                    String.format("%s: is %s, '%s' is not found at '%s'.", this, offlineMessage, repoPath, path));
        }
    }

    @VisibleForTesting
    void unExpireCachedResourceIfNeeded(RemoteRepoResource remoteRepoResource,
            RequestContext requestContext) {
        if (requestContext instanceof AlteredDownloadRequestContext) {
            boolean shouldUnExpireResource =
                    ((AlteredDownloadRequestContext) requestContext).shouldUnExpireCachedResource(remoteRepoResource);
            if (shouldUnExpireResource) {
                unexpire(remoteRepoResource);
            }
        }
    }

    boolean shouldReturnCachedResource(InternalRequestContext context, RepoResource cachedResource) {
        if (cachedResource != null) {
            if (cachedResource.isFound()) {
                RepoRequests.logToContext("Found resource in local cache - returning cached resource");
                // found in local cache
                return true;
            } else if (foundInCacheWithPropMismatch(cachedResource)) {
                RepoRequests.logToContext("Found resource in local cache, but property doesn't match.");
                return true;
            } else {
                // Request originated by replication, either return cached (expired or otherwise) resource or nothing
                // at all, so we don't leak internally managed metadata file requests upstream
                return parseBoolean(context.getRequest().getParameter(PARAM_REPLICATION_ORIGINATED_DOWNLOAD_REQUEST));
            }
        }
        return false;
    }

    private boolean foundInCacheWithPropMismatch(RepoResource cachedResource) {
        return cachedResource instanceof UnfoundRepoResourceReason
                && ((UnfoundRepoResourceReason) cachedResource).getReason() == Reason.PROPERTY_MISMATCH;
    }

    private RepoResource getRemoteZipRepoResource(RepoPath repoPath, InternalRequestContext context) {
        try {
            RepoRequests.logToContext(
                    "Main ZIP resource {} exist remotely but is expired or is not present in the caches - " +
                            "doing eager download.", repoPath
            );
            EagerResourcesDownloader eagerResourcesDownloader = InternalContextHelper.get().beanForType(
                    EagerResourcesDownloader.class);
            InternalArtifactoryRequest internalRequest = createInternalRequestDisableRedirect(repoPath);
            Request request = context.getRequest();
            String alternativeDownloadUrl = request.getParameter(PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL);
            if (StringUtils.isNotBlank(alternativeDownloadUrl) && isAllowedQueryParamRewrite(request)) {
                internalRequest.setAlternativeRemoteDownloadUrl(alternativeDownloadUrl);
            }
            eagerResourcesDownloader.downloadNow(repoPath, internalRequest);
            RepoResource cachedResource = localCacheRepo.getInfo(context);
            if (cachedResource != null && cachedResource.isFound()) {
                RepoRequests.logToContext(
                        "Found resource after eager download in local cache - returning cached resource");
                return returnCachedResource(repoPath, cachedResource, context);
            }
            String zipResourcePath = context.getRequest().getZipResourcePath();
            return new UnfoundRepoResource(repoPath,
                    "Could not download '" + zipResourcePath + "' from main zip resource");
        } catch (Exception e) {
            RepoRequests.logToContext(
                    "Main ZIP resource exists remotely but could not be downloaded due to: {}", e.getMessage());
            log.warn("Main ZIP resource {} exists remotely but could not be downloaded due to: {}",
                    repoPath, e.getMessage());
            if (log.isDebugEnabled()) {
                log.warn(e.getMessage(), e);
            }
            return new UnfoundRepoResource(repoPath, "Zip resources download failed due to: " + e.getMessage());
        }
    }

    /**
     * Returns a resource from a remote repository
     *
     * @param context             Download request context
     * @param repoPath            Item repo path
     * @param foundExpiredInCache True if the an expired item was found in the cache    @return Repo resource object
     */
    RepoResource getRemoteResource(RequestContext context, RepoPath repoPath, boolean foundExpiredInCache) {
        String path = repoPath.getPath();
        boolean folder = repoPath.isFolder();
        if (!isSynchronizeProperties() && context.getProperties().hasMandatoryProperty()) {
            RepoRequests.logToContext("Repository doesn't sync properties and the request contains " +
                    "mandatory properties - returning unfound resource");
            return new UnfoundRepoResource(repoPath, this + ": does not synchronize remote properties and request " +
                    "contains mandatory property, '" + repoPath + "' will not be downloaded from '" + path + "'.");
        }

        RepoResource remoteResource;
        path = getAltRemotePath(repoPath);
        if (!repoPath.getPath().equals(path)) {
            RepoRequests.logToContext("Remote resource path was altered by the user plugins to - %s", path);
        }
        try {
            remoteResource = retrieveInfo(path, folder, context);
            if (!remoteResource.isFound() && !foundExpiredInCache) {
                //Update the non-found cache for a miss
                RepoRequests.logToContext("Unable to find resource remotely - adding to the missed retrieval cache.");
                getMissedRetrievalsCache().put(path, remoteResource);
            }
        } catch (RateLimiterException e) {
            RepoRequests.logToContext("Expected file but got rate limit exception");
            throw e;
        } catch (FileExpectedException e) {
            RepoRequests.logToContext("Expected file but got directory, requesting a redirect");
            throw e;
        } catch (IllegalUrlPathException e) {
            RepoRequests.logToContext(e.getMessage());
            throw new CancelException(e.getMessage(), HttpStatus.SC_BAD_REQUEST);
        } catch (WorkerStopException wksException) {
            RepoRequests.logToContext(wksException.getMessage());
            throw wksException;
        } catch (Exception e) {
            remoteResource = handleGenericException(repoPath, foundExpiredInCache, path, e);
        }
        return remoteResource;
    }

    @NotNull
    private RepoResource handleGenericException(
            RepoPath repoPath, boolean foundExpiredInCache, String path, Exception e) {
        RepoResource remoteResource;
        RepoRequests.logToContext("Failed to retrieve information: %s", e.getMessage());
        String reason = this + ": Error in getting information for '" + path + "' (" + e.getMessage() + ").";
        if (log.isDebugEnabled()) {
            log.warn(reason, e);
        } else {
            log.warn(reason);
        }

        putOffline();
        remoteResource = new UnfoundRepoResource(repoPath, reason);
        if (!foundExpiredInCache && getRepoConfig().getRepoTypeConfig().isHardFail()) {
            throw new RuntimeException(this + ": Error in getting information for '" + path + "'.", e);
        }
        if (e.getCause() instanceof RateLimiterException rateLimiterException) {
            throw rateLimiterException;
        }
        return remoteResource;
    }

    protected abstract RepoResource returnCachedResource(RepoPath repoPath, RepoResource cachedResource,
            RequestContext context);

    protected CurationCachedPackageStatus checkBlockedByCuration(Request request, RepoPath repoPath,
            String downloadUrl,
            Header[] responseHeaders, boolean fromCache) {
        if (shouldSkipCurationCheck(request)) {
            log.debug("Skipping curation check - no local cache and HEAD-only request");
            return new CurationCachedPackageStatus(null,
                    new CurationPackageStatus(true, MSG_CURATION_NO_LOCAL_CACHE_HEAD_ONLY,
                            SecurityDecision.CURATION_NO_LOCAL_CACHE_HEAD_ONLY));
        }
        return getCurationAddon()
                .getPackageStatus(getRepoConfig(), repoPath, downloadUrl, responseHeaders, fromCache);
    }

    private boolean shouldSkipCurationCheck(Request request) {
        return localCacheRepo == null && request != null && request.isHeadOnly();
    }

    /**
     * Temporarily puts the repository in an assumed offline mode.
     */
    protected abstract void putOffline();

    protected abstract RepoResource retrieveInfo(String path, boolean folder, @Nullable RequestContext context);

    protected abstract RepoResource getInfoFromCache(InternalRequestContext context);

    @Override
    public StatusHolder checkDownloadIsAllowed(RepoPath repoPath) {
        String path = repoPath.getPath();
        BasicStatusHolder status = assertValidPath(repoPath, true);
        if (status.isError()) {
            return status;
        }

        // permissions are always on the cache repo key
        RepoPath cacheRepoPath = InternalRepoPathFactory.create(getKey() + LocalCacheRepoDescriptor.PATH_SUFFIX, path);
        if (localCacheRepo != null) {
            return localCacheRepo.checkDownloadIsAllowed(cacheRepoPath);
        } else {
            // cache repo doesn't exist so remote has to check the permissions
            assertReadPermissions(cacheRepoPath, status);
            return status;
        }
    }

    @Override
    public StatusHolder checkDownloadIsNotBlocked(RequestContext context, RepoPath repoPath) {
        if (localCacheRepo != null) {
            String path = repoPath.getPath();
            RepoPath cacheRepoPath =
                    InternalRepoPathFactory.create(getKey() + LocalCacheRepoDescriptor.PATH_SUFFIX, path);
            return localCacheRepo.checkDownloadIsNotBlocked(context, cacheRepoPath);
        }
        return new BasicStatusHolder();
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, RepoResource res)
            throws IOException, RepoRejectException {
        // We also change the context here, otherwise if there is something in the cache
        // we will receive it instead of trying to download the latest from the remote
        AddonsManager addonsManager = InternalContextHelper.get().beanForType(AddonsManager.class);
        RestCoreAddon restCoreAddon = addonsManager.addonByType(RestCoreAddon.class);
        requestContext = restCoreAddon.getDynamicVersionContext(this, requestContext, true);
        RepoRequests.logToContext("Creating a resource handle from '%s'", res.getResponseRepoPath().getRepoKey());
        String path = res.getRepoPath().getPath();
        if (isCacheArtifact(requestContext)) {
            RepoRequests.logToContext("Target repository is configured to retain artifacts locally - " +
                    "resource will be stored and then streamed to the user");
            try {
                //Reflect the fact that we return a locally cached resource
                res.setResponseRepoPath(InternalRepoPathFactory.create(localCacheRepo.getKey(), path));
                return getRepositoryService().downloadAndSave(requestContext, this, res);
            } catch (IOException e) {
                RepoRequests.logToContext("Error occurred while downloading artifact: %s", e.getMessage());
                //If we fail on remote fetching and we can get the resource from an expired entry in
                //the local cache - fallback to using it, else rethrow the exception
                if (res.isExpired()) {
                    ResourceStreamHandle result =
                            getRepositoryService().unexpireAndRetrieveIfExists(requestContext, localCacheRepo, path);
                    if (result != null) {
                        RepoRequests.logToContext("Requested artifact is expired and exists in the cache - " +
                                "un-expiring cached and returning it instead");
                        return result;
                    }
                }
                throw e;
            }
        } else {
            RepoRequests.logToContext("Target repository is configured to not retain artifacts locally - " +
                    "resource will be stream directly to the user");
            return downloadResource(path, requestContext);
        }
    }

    private boolean isCacheArtifact(InternalRequestContext requestContext) {
        return isStoreArtifactsLocally() && !isPassThroughEnabled(requestContext);
    }

    private boolean isPassThroughEnabled(InternalRequestContext requestContext) {
        return InternalContextHelper.get()
                .beanForType(AddonsManager.class)
                .addonByType(CurationAddon.class)
                .shouldPerformPassThrough(getRepoConfig(), requestContext);
    }

    @Override
    public ResourceStreamHandle downloadAndSave(InternalRequestContext requestContext, RepoResource remoteResource)
            throws IOException, RepoRejectException {
        assert getLocalCacheRepo() != null;

        boolean offline = isOffline();
        RepoRequests.logToContext("Remote repository is %s", (offline ? "offline" : "online"));

        RepoResource cachedResource = getLocalCacheRepo().getInfo(requestContext);
        boolean shouldExpire = shouldExpire(cachedResource, remoteResource);

        boolean forceExpiryCheck = requestContext.isForceExpiryCheck();
        RepoRequests.logToContext("Force expiration on the cached resource = %s", forceExpiryCheck);

        boolean cachedNotFoundAndNotExpired = notFoundAndNotExpired(cachedResource);
        RepoRequests.logToContext("Resource isn't cached and isn't expired = %s", cachedNotFoundAndNotExpired);

        RepoPath remoteRepoPath = remoteResource.getRepoPath();
        boolean remoteDownloadAllowed = isRemoteDownloadAllowed(remoteRepoPath);
        if (!remoteDownloadAllowed) {
            RepoRequests.logToContext("Remote resource not allowed based on one of the interceptors");
        }

        // Retrieve remote artifact conditionally
        if (!offline && remoteDownloadAllowed &&
                (forceExpiryCheck || shouldExpire || cachedNotFoundAndNotExpired)) {
            // Check for security deploy rights
            RepoRequests.logToContext("Asserting valid deployment path");
            ValidDeployPathContext validDeployPathContext = new ValidDeployPathContext
                    .Builder(localCacheRepo, remoteRepoPath)
                    .contentLength(remoteResource.getInfo().getSize())
                    .forceExpiryCheck(requestContext.isForceExpiryCheck()).build();
            getRepositoryService().assertValidDeployPathAndPermissions(validDeployPathContext);

            // Only 1 remote downloader is allowed
            String pathToLock = cachedResource.getRepoPath().toPath();

            boolean lockAcquired = false;
            boolean shouldServeFromCacheOnTimeout = false;
            ArtifactResourceContext saveContext;
            try {
                if (cachedResource.isExpirable() && cachedResource.isExpired() && isResourceFound(cachedResource)) {
                    // Metadata request, since the file is expired we will return the expired upon timeout failure
                    //(on concurrent requests)
                    shouldServeFromCacheOnTimeout = true;
                    RemoteRepositoryRepoTypeConfig repoTypeConfig = getRepoConfig().getRepoTypeConfig();
                    lockAcquired = acquireLock(pathToLock, repoTypeConfig.getMetadataRetrievalTimeoutSecs());
                } else {
                    lockAcquired =
                            acquireLock(pathToLock, ConstantValues.repoConcurrentDownloadSyncTimeoutSecs.getLong());
                }
                if (lockAcquired) {
                    // Cache might have been updated while waiting on the lock.
                    // Check the ThreadLocalCacheRemoteDownloadInterceptor for more info on invalidateCache
                    ReposItemsThreadLocalCacheService.invalidateCache(cachedResource.getRepoKey());
                    try {
                        if (cachedResource.isExpired()) {
                            // Resource is expired -> check again if expired (after acquiring lock)
                            RepoResource newCachedResource = getLocalCacheRepo().getInfo(requestContext);
                            // If not expired then try to get it fro cache (if fail throw exception) and do download
                            //and save
                            if (!newCachedResource.isExpired()) {
                                String message =
                                        "Resource expired but after acquiring lock it is not expired using prepared " +
                                                "handle";
                                return getHandleFromCache(requestContext, remoteResource, cachedResource, message);
                            }
                        } else {
                            // Unfounded resource -> try to get it fro cache (if fail throw exception) and do
                            //download and save
                            String message = "Found completed concurrent download - using prepared handle";
                            return getHandleFromCache(requestContext, remoteResource, cachedResource, message);
                        }
                    } catch (FileNotFoundException e) {
                        String msg =
                                "Unable to find cached resource stream handle, continuing with actual remote download.";
                        log.debug(msg);
                        RepoRequests.logToContext(msg);
                    }
                } else {
                    //We exited because of a timeout, return timeout error
                    log.info("Timed-out waiting on concurrent download of '{}' in '{}'.", pathToLock, this);
                    RepoRequests.logToContext("Timed-out waiting on concurrent download.");
                    if (shouldServeFromCacheOnTimeout) {
                        // Returning cached resource of the metadata. The file is expired but since failed retrieval,
                        //use old one.
                        return getResourceFromCacheUponTimeout(requestContext, remoteResource, cachedResource);
                    }
                    return null;
                }
                RepoRequests.logToContext("Found no cached resource - starting download");
                saveContext = doDownloadAndSave(requestContext, remoteResource);
                if (shouldExpire) {
                    debLog.debug("IP '{}' refreshed expired file '{}'", HttpUtils.getRemoteClientAddress(), pathToLock);
                }
            } finally {
                if (lockAcquired) {
                    getRemoteDownloaderLockingMap().unlock(pathToLock);
                }
            }
            cachedResource = saveContext.repoResource() != null ? saveContext.repoResource() : cachedResource;
            notifyInterceptorsOnAfterRemoteDownload(cachedResource);
            sendAfterRemoteDownloadUnifiedEvent(saveContext);
        }

        boolean cachedExpiredAndNewerThanRemote = cachedExpiredAndNewerThanRemote(remoteResource, cachedResource);
        RepoRequests.logToContext("Found expired cached resource and is newer than remote = %s",
                cachedExpiredAndNewerThanRemote);
        if (cachedExpiredAndNewerThanRemote) {
            synchronizeExpiredResourceProperties(remoteResource.getRepoPath());
            unexpire(cachedResource);
        }

        curationOnDemand(cachedResource);

        RepoRequests.logToContext("Returning the cached resource");
        //Return the cached result (the newly downloaded or already cached resource)
        return localCacheRepo.getResourceStreamHandle(requestContext, cachedResource);
    }

    void sendAfterRemoteDownloadUnifiedEvent(ArtifactResourceContext context) {
        UnifiedEventService unifiedEventService = ContextHelper.get().beanForType(UnifiedEventService.class);
        unifiedEventService.sendRemoteArtifactEvent(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD, context);
    }

    @VisibleForTesting
    void curationOnDemand(RepoResource repoResource) throws RepoRejectException {
        if (isBlockDownloadEnabled()) {
            RepoPath repoPath = repoResource.getResponseRepoPath();
            String originRemotePath = repoResource.getOriginRemotePath();
            String mimeType = "";
            if (repoResource instanceof RemoteRepoResource remoteRepoResource) {
                log.debug("Remote repo resource");
                mimeType = remoteRepoResource.getContentTypeHeader();
            }
            if (StringUtils.isBlank(mimeType)) {
                mimeType = repoResource.getMimeType();
                log.debug("Could not extract mimeType from response headers, falling back to repo resource mimeType");
            }
            CurationPackageStatus status = getCurationAddon().
                    getCachedPackageStatus(getRepoConfig(), repoPath, originRemotePath, mimeType);
            if (status.permitted()) {
                log.debug("Download permitted for '{}'", repoPath);
            } else {
                log.warn("Download not permitted for '{}'", repoPath);
                throw new RepoRejectException(status.message(), HttpStatus.SC_FORBIDDEN);
            }
        } else {
            log.debug("isBlockDownloadEnabled: {} call skipped", isBlockDownloadEnabled());
        }
    }

    /**
     * Returns the resource from the cache
     *
     * @param requestContext - The request context
     * @param remoteResource - The resource requested
     * @param cachedResource - The cached resource that found
     * @return the resource if found, null otherwise
     */
    @Nullable
    private ResourceStreamHandle getResourceFromCacheUponTimeout(InternalRequestContext requestContext,
            RepoResource remoteResource, RepoResource cachedResource) throws IOException, RepoRejectException {
        String message =
                String.format("Timed-out waiting on concurrent download of %s, returning the previous cached resource.",
                        cachedResource.getRepoPath().toPath());
        try {
            return getHandleFromCache(requestContext, remoteResource, cachedResource, message);
        } catch (FileNotFoundException e) {
            String msg =
                    "Unable to find cached resource stream handle, continuing with actual remote download.";
            log.debug(msg);
            RepoRequests.logToContext(msg);
        }
        return null;
    }

    /**
     * @param resource - The resource to check if its found or not.
     * @return true if the resource is found, false otherwise
     */
    private boolean isResourceFound(RepoResource resource) {
        if (resource instanceof ExpiredRepoResource) {
            return ((ExpiredRepoResource) resource).getWrappedResource().isFound();
        }
        return resource.isFound();
    }

    /**
     * Try's to acquire lock on a given path with the given time.
     *
     * @param pathToLock  - The repository path to lock
     * @param timeoutSecs - The time to wait untill fails to acquire the lock
     * @return - True if lock has been acquired, false otherwise.
     */
    boolean acquireLock(String pathToLock, long timeoutSecs) {
        try {
            log.debug("Trying to acquire lock for path {}", pathToLock);
            return getRemoteDownloaderLockingMap().tryToLock(pathToLock, timeoutSecs
                    , TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.info("Interrupted on concurrent download lock of '{}' due to: {}", pathToLock, e.getMessage());
            log.debug("Interrupted on concurrent download lock", e);
            RepoRequests.logToContext("Interrupted on concurrent download lock of '" + pathToLock + "'");
        }
        return false;
    }

    boolean isAllowedQueryParamRewrite(Request request) {
        return request instanceof InternalArtifactoryRequest || request instanceof TranslatedArtifactoryRequest;
    }

    ConflictsGuard getRemoteDownloaderLockingMap() {
        HaAddon haAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class);
        return haAddon.getConflictsGuard(REMOTE_DOWNLOADERS);
    }

    ResourceStreamHandle getHandleFromCache(InternalRequestContext requestContext, RepoResource remoteResource,
            RepoResource cachedResource, String message) throws IOException, RepoRejectException {
        ResourceStreamHandle cacheHandle = localCacheRepo.getResourceStreamHandle(requestContext, cachedResource);
        // If resource is not found, return null
        if (cacheHandle == null) {
            return null;
        }
        ChecksumsInfo checksumsInfo = null;
        if (cacheHandle instanceof ResourceWithChecksumStreamHandle) {
            // Try first to get the checksum from the found cache
            Optional<ChecksumsInfo> optionalChecksums =
                    ((ResourceWithChecksumStreamHandle) cacheHandle).getChecksumsInfo();
            if (optionalChecksums.isPresent()) {
                checksumsInfo = optionalChecksums.get();
            }
        }
        if (checksumsInfo == null) {
            // If still not found, try via the db.
            checksumsInfo = localCacheRepo.getInfo(requestContext).getInfo().getChecksumsInfo();
        }
        RepoRequests.logToContext(message);
        remoteResource.getInfo().getChecksumsInfo().setChecksums(checksumsInfo.getChecksums());
        return cacheHandle;
    }

    private boolean isRemoteDownloadAllowed(RepoPath remoteRepoPath) {
        for (RemoteRepoInterceptor interceptor : remoteRepoInterceptors) {
            S repoConfig = getRepoConfig();
            if (!interceptor.isRemoteDownloadAllowed(repoConfig, remoteRepoPath)) {
                // one veto is enough to prevent the download
                return false;
            }
        }
        return true;
    }

    private void notifyInterceptorsOnAfterRemoteDownload(RepoResource remoteResource) throws RepoRejectException {
        for (RemoteRepoInterceptor interceptor : remoteRepoInterceptors) {
            interceptor.afterRemoteDownload(remoteResource);
        }
    }

    /**
     * @param outgoingRequest - the request about to get executed by the remote repo.
     * @param incomingRequest - (optional) the client request that initiated this remote download flow
     * @param headers         - headers we would use with the request.
     * @param repoPath        - used by shouldTakeAction in individual interceptors
     */
    void notifyInterceptorsOnBeforeRemoteHttpMethodExecution(HttpRequestBase outgoingRequest,
            @Nullable Request incomingRequest,
            HeadersMultiMap headers, RepoPath repoPath) {
        for (RemoteRepoInterceptor interceptor : remoteRepoInterceptors) {
            interceptor.beforeRemoteHttpMethodExecution(outgoingRequest, incomingRequest, headers, repoPath);
        }
    }

    void retrieveChecksumsFromInterceptors(RepoPath repoPath, CloseableHttpResponse response,
            Set<ChecksumInfo> checksums) {
        if (!checksums.isEmpty()) {
            RepoRequests.logToContext("Found remote resource with checksums - %s", checksums);
        } else {
            //No standard Artifactory checksum headers, let implementation-specific logic retrieve checksum information
            notifyInterceptorsOnRemoteChecksumResolution(repoPath, response, checksums);
            if (!checksums.isEmpty()) {
                RepoRequests.logToContext("Found remote resource with checksums - %s", checksums);
            }
        }
    }

    /**
     * Allow implementation-specific logic to resolve checksum information from a getInfo response
     */
    void notifyInterceptorsOnRemoteChecksumResolution(RepoPath repoPath, CloseableHttpResponse response,
            Set<ChecksumInfo> checksums) {
        for (RemoteRepoInterceptor interceptor : remoteRepoInterceptors) {
            interceptor.resolveChecksumInformation(repoPath, response, checksums);
        }
    }

    void setParallelDownloadAttributes(InternalRequestContext requestContext, RepoResource res) {
        requestContext.setAttribute("fileSize", String.valueOf(res.getSize()));
        RepoResourceInfo info = res.getInfo();
        ChecksumsInfo checksums = info != null ? info.getChecksumsInfo() : null;
        if (checksums != null) {
            ChecksumInfo sha1Info = checksums.getChecksumInfo(ChecksumType.sha1);
            String sha1 = sha1Info != null ? sha1Info.getOriginal() : null;
            if (StringUtils.isNotBlank(sha1)) {
                requestContext.setAttribute(ParallelDownloadService.RCD_EXPECTED_SHA1, sha1);
            }
        }
        if (res instanceof RemoteRepoResource remoteRes) {
            Header[] headers = remoteRes.getAllHeaders();
            boolean supportsRange = headers != null && Arrays.stream(headers)
                    .anyMatch(h -> ArtifactoryHeaders.ACCEPT_RANGES.equalsIgnoreCase(h.getName())
                            && "bytes".equalsIgnoreCase(h.getValue()));
            requestContext.setAttribute(ParallelDownloadService.RCD_SUPPORTS_RANGE, String.valueOf(supportsRange));
        }
    }

    // this is the actual download of the resource
    ArtifactResourceContext doDownloadAndSave(InternalRequestContext requestContext, RepoResource remoteResource)
            throws RepoRejectException, IOException {
        RepoRequests.logToContext("Downloading and saving");
        RepoPath remoteRepoPath = remoteResource.getRepoPath();
        ResourceStreamHandle handle = null;
        long remoteRequestStartTime = 0;
        try {
            beforeResourceDownload(remoteResource, requestContext.getProperties(), requestContext.getRequest());
            Properties properties = getSaveResourceProperties(remoteResource);
            RepoResourceInfo remoteInfo = remoteResource.getInfo();
            Set<ChecksumInfo> remoteChecksums = remoteInfo.getChecksumsInfo().getChecksums();
            boolean receivedRemoteChecksums = CollectionUtils.notNullOrEmpty(remoteChecksums);
            if (receivedRemoteChecksums) {
                RepoRequests.logToContext("Received remote checksums headers - %s", remoteChecksums);
            } else {
                RepoRequests.logToContext("Received no remote checksums headers");
            }
            //Allow plugins to provide an alternate content
            handle = getAltContent(remoteRepoPath);
            if (handle == null && receivedRemoteChecksums &&
                    shouldSearchForExistingResource(requestContext.getRequest())) {
                RepoRequests.logToContext("Received no alternative content, received remote checksums headers" +
                        " and searching for existing resources on download is enabled");
                handle = hasChecksumDeployPermissionOnRepoPath(remoteChecksums) ?
                        getExistingResourceByChecksum(remoteChecksums, remoteResource.getSize()) : null;
            }
            if (!receivedRemoteChecksums) {
                RepoRequests.logToContext("Trying to find remote checksums");
                remoteChecksums = getRemoteChecksums(remoteRepoPath.getPath(), remoteResource);
                if (remoteResource instanceof RemoteRepoResource) {
                    ((RemoteRepoResource) remoteResource).getInfo().setChecksums(remoteChecksums);
                } else {
                    // Cannot set the checksums on non remote repo resource
                    RepoRequests.logToContext("No checksums found on %s and it's not a remote resource!",
                            remoteResource);
                }
            }

            if (handle == null) {
                RepoRequests.logToContext("Received no alternative content or existing resource - " +
                        "downloading resource");
                //If we didn't get an alternate handle do the actual download
                remoteRequestStartTime = System.currentTimeMillis();
                if (ConstantValues.remoteConcurrentDownloadsEnabled.getBoolean()) {
                    setParallelDownloadAttributes(requestContext, remoteResource);
                }
                handle = downloadResource(remoteRepoPath.getPath(), requestContext);
                reconstructResourceFromGetResponseIfNeeded(remoteResource, handle,
                        requestContext.getRequest().getRepoPath());
            }

            properties = updateEtagInProperties(remoteResource, properties);
            properties = setOriginRemotePathProperty(remoteResource, properties);
            //Create/override the resource in the storage cache
            SaveResourceContext.Builder builder = new SaveResourceContext.Builder(remoteResource, handle)
                    .properties(properties);
            if (!isContentEncoded(remoteResource)) {
                builder.contentLength(String.valueOf(handle.getSize()));
            }
            if (handle instanceof PreChecksummedResourceStreamHandle streamHandle) {
                builder.preCalculatedHeaders(streamHandle.getPreCalculatedHeaders());
            }
            SaveResourceContext saveResourceContext = builder.build();
            RepoRequests.logToContext("Saving resource to " + localCacheRepo);
            RepoResource cachedResource = getRepositoryService().saveResource(localCacheRepo, saveResourceContext);
            if (remoteRequestStartTime > 0) {
                addTrafficEntry(remoteResource, handle, remoteRequestStartTime, cachedResource, requestContext);
            }
            return ArtifactResourceContext.builder()
                    .repoResource(cachedResource)
                    .properties(saveResourceContext.getProperties())
                    .artifactoryRequest(requestContext.getRequest())
                    .build();
        } catch (RepoRejectException rre) {
            //Repo rejected artifact - add to missed retrieval cache
            addRejectedResourceToMissedCache(remoteRepoPath, rre.getMessage(), rre.getErrorCode());
            setExceptionOnHandle(handle, rre);
            throw rre;
        } catch (Exception e) {
            // set exception here before the remote stream is closed to signal an error
            Throwable ioCause = ExceptionUtils.getCauseOfType(e, IOException.class);
            if (ioCause != null) {
                log.warn("IO error while trying to download resource '{}': {}: {}", remoteRepoPath,
                        ioCause.getClass().getName(), HttpClientUtils.getErrorMessage(ioCause));
                log.debug("IO error while trying to download resource '{}': {}",
                        remoteResource.getRepoPath(), ioCause.getMessage(), ioCause);
                setExceptionOnHandle(handle, e);
                throw (IOException) ioCause;
            }
            setExceptionOnHandle(handle, e);
            throw e;
        } finally {
            boolean trafficShouldBeLogged =
                    remoteRequestStartTime > 0 && handle instanceof RemoteResourceTrafficContextHolder;
            if (trafficShouldBeLogged) {
                fireUploadTrafficEventV2(remoteResource, requestContext, (RemoteResourceTrafficContextHolder) handle);
            } else {
                String handleClassName = handle == null ? "null" : handle.getClass().getName();
                log.debug("Traffic would not be logged. remoteRequestStartTime {} handle {}",
                        remoteRequestStartTime, handleClassName);
            }
            Closeables.close(handle, false);
        }
    }

    @VisibleForTesting
    Properties setOriginRemotePathProperty(RepoResource repoResource, Properties properties) {
        if (repoResource instanceof RemoteRepoResource remoteResource) {
            String path = remoteResource.getPath();
            String originRemotePath = remoteResource.getOriginRemotePath();
            if (isBlank(originRemotePath)) {
                log.debug("{} Origin remote path is empty", path);
            } else {
                log.debug("{} store {} property {}", path, ORIGIN_REMOTE_PATH_PROP_KEY, originRemotePath);
                if (properties == null) {
                    log.debug("{} Null properties. Creating new", path);
                    properties = (Properties) InfoFactoryHolder.get().createProperties();
                }
                properties.removeAll(ORIGIN_REMOTE_PATH_PROP_KEY);
                properties.put(ORIGIN_REMOTE_PATH_PROP_KEY, originRemotePath);
                log.debug("{} Saved {} property value : {}", path,
                        ORIGIN_REMOTE_PATH_PROP_KEY, originRemotePath);
            }
        } else {
            log.debug("{} repoResource is not instanceof RemoteRepoResource", repoResource.getRepoPath().toPath());
        }
        return properties;
    }

    @VisibleForTesting
    boolean hasChecksumDeployPermissionOnRepoPath(Set<ChecksumInfo> checksumsInfo) {
        ChecksumInfo checksum = checksumsInfo.stream()
                .filter(checksumInfo -> checksumInfo.getType().equals(ChecksumType.sha1))
                .findFirst().orElse(null);
        if (checksum == null) {
            return false;
        }
        ChecksumDeployPermissionValidator checksumPermissionValidator =
                ContextHelper.get().beanForType(ChecksumDeployPermissionValidator.class);
        try {
            return checksumPermissionValidator.hasReadPermission(checksum, null);
        } catch (InvalidChecksumException e) {
            String msg = "Invalid checksum: ";
            RepoRequests.logToContext(msg + e.getMessage());
            log.warn(msg, e);
            return false;
        }
    }

    Properties updateEtagInProperties(RepoResource remoteResource, Properties properties) {
        String etag = remoteResource.getEtag();
        if (StringUtils.isNotBlank(etag)) {
            if (properties == null) {
                properties = (Properties) InfoFactoryHolder.get().createProperties();
            }
            log.debug("Updating etag {} in properties", etag);

            properties.put(DbStoringRepoMixin.ETAG_PROP_KEY, etag);
        }
        return properties;
    }

    @SuppressWarnings("unused")
    protected boolean shouldReconstructResourceFromGetResponse(RepoResource remoteResource,
            ResourceStreamHandle handle) {
        return ConstantValues.saveGetResource.getBoolean();
    }

    protected void reconstructResourceFromGetResponseIfNeeded(RepoResource remoteResource,
            ResourceStreamHandle handle, RepoPath contextRequestRepoPath) {

        if (handle instanceof PreChecksummedResourceStreamHandle) {
            return;
        }

        if (!(handle instanceof RemoteResourceStreamHandle) ||
                !(remoteResource instanceof RemoteRepoResource) ||
                !shouldReconstructResourceFromGetResponse(remoteResource, handle)) {
            return;
        }

        RepoPath repoPath = remoteResource.getRepoPath();
        log.debug("Using resource from get request for repo path '{}'.", repoPath);
        RepoRequests.logToContext("Using resource from get request for repo path '%s'.", repoPath);

        CloseableHttpResponse responseFromGet = ((RemoteResourceStreamHandle) handle).getResponse();
        if (responseFromGet == null) {
            log.warn("GET Response of '{}' is missing from resource stream handle", repoPath);
            return;
        }
        Set<ChecksumInfo> checksums = HttpUtils.getChecksums(responseFromGet);
        retrieveChecksumsFromInterceptors(repoPath, responseFromGet, checksums);

        RemoteRepoResource remoteResourceFromGet =
                createRemoteResourceFromResponse(repoPath, responseFromGet, checksums, null);

        ((RemoteRepoResource) remoteResource).reConstruct(remoteResourceFromGet);
        checkAndMarkExpirableResource(remoteResource, contextRequestRepoPath);
    }

    protected RemoteRepoResource createRemoteResourceFromResponse(RepoPath repoPath, CloseableHttpResponse response,
            Set<ChecksumInfo> checksums, CurationPackageStatusPayload payload) {
        long contentLength = HttpUtils.getContentLength(response);
        if (contentLength != -1) {
            RepoRequests.logToContext("Found remote resource with content length - %s", contentLength);
        }

        long lastModified = HttpUtils.getLastModified(response);
        RepoRequests.logToContext("Found remote resource with last modified time - %s",
                new Date(lastModified).toString());

        String etag = HttpUtils.getEtag(response);
        RepoRequests.logToContext("Found remote resource with ETag - %s", etag);

        RepoRequests.logToContext("Returning found remote resource info");
        return new RemoteRepoResource(repoPath, lastModified, etag, contentLength, checksums,
                response.getAllHeaders());
    }

    /**
     * Prepare properties with etag, sync with remote props if enabled.
     *
     * @return Properties to add to the saved resource
     */
    private Properties getSaveResourceProperties(RepoResource remoteResource) {
        boolean synchronizeProperties = isSynchronizeProperties();

        RepoRequests.logToContext("Remote property synchronization enabled = %s", synchronizeProperties);

        Properties properties = null;
        if (synchronizeProperties) {
            // No check for annotate permissions, since sync props is a configuration flag
            // and file will be deployed here
            RepoRequests.logToContext("Trying to find remote properties");
            properties = getRemoteProperties(remoteResource.getRepoPath().getPath());
        }
        return properties;
    }

    void addTrafficEntry(RepoResource remoteResource, ResourceStreamHandle handle, long remoteRequestStartTime,
            RepoResource cachedResource, InternalRequestContext requestContext) {
        String remoteAddress;
        if (handle instanceof RemoteResourceTrafficContextHolder remoteResourceHandle) {
            remoteAddress = remoteResourceHandle.getRemoteIp();
        } else {
            remoteAddress = StringUtils.EMPTY;
        }

        TrafficService trafficService = ContextHelper.get().beanForType(TrafficService.class);
        UploadEntry uploadEntry = getUploadEntry(remoteResource, remoteRequestStartTime, cachedResource, requestContext,
                remoteAddress, trafficService.isActive());
        trafficService.handleTrafficEntry(uploadEntry);
    }

    private UploadEntry getUploadEntry(RepoResource remoteResource, long remoteRequestStartTime,
            RepoResource cachedResource, InternalRequestContext requestContext, String remoteAddress,
            boolean isTrafficLogActive) {
        String userIdentifier = getUserIdentifier(remoteResource, remoteRequestStartTime, cachedResource,
                requestContext, remoteAddress,
                isTrafficLogActive);
        // fire upload event only if the resource was downloaded from the remote repository
        return new UploadEntry(remoteResource.getRepoPath().getId(),
                cachedResource.getSize(), System.currentTimeMillis() - remoteRequestStartTime, remoteAddress,
                userIdentifier);
    }

    private String getUserIdentifier(RepoResource remoteResource, long remoteRequestStartTime,
            RepoResource cachedResource, InternalRequestContext requestContext, String remoteAddress,
            boolean isTrafficLogActive) {
        String userIdentifier = "";
        String username = ContextHelper.get().beanForType(SecurityService.class).currentUsername();
        if (AuthenticationUtils
                .isXrayAuthenticationUser(username, SecurityContextHolder.getContext().getAuthentication())) {
            if (isTrafficLogActive && requestContext instanceof NullRequestContext) {
                log.warn("Xray user without user agent {} {} {} {}", remoteResource.getRepoPath().getId(),
                        cachedResource.getSize(), System.currentTimeMillis() - remoteRequestStartTime, remoteAddress);

            } else {
                userIdentifier = requestContext.getRequest().getHeader(HttpHeaders.USER_AGENT);
                if (isTrafficLogActive && StringUtils.isEmpty(userIdentifier)) {
                    log.warn("Xray user without user agent {} {} {} {}", remoteResource.getRepoPath().getId(),
                            cachedResource.getSize(), System.currentTimeMillis() - remoteRequestStartTime,
                            remoteAddress);
                }
            }
        }
        return userIdentifier;
    }

    UnfoundRepoResource addRejectedResourceToMissedCache(RepoPath remoteRepoPath, String message, int errorCode) {
        UnfoundRepoResource rejected = new UnfoundRepoResource(remoteRepoPath, Reason.REJECTED, message, errorCode);
        getMissedRetrievalsCache().put(remoteRepoPath.getPath(), rejected);
        return rejected;
    }

    private void setExceptionOnHandle(ResourceStreamHandle handle, Exception e) {
        if (handle instanceof RemoteResourceStreamHandle) {
            ((RemoteResourceStreamHandle) handle).setThrowable(e);
        }
    }

    private boolean shouldSearchForExistingResource(Request request) {
        String searchForExistingResource = request.getParameter(PARAM_SEARCH_FOR_EXISTING_RESOURCE_ON_REMOTE_REQUEST);
        if (StringUtils.isNotBlank(searchForExistingResource)) {
            return Boolean.valueOf(searchForExistingResource);
        }

        return ConstantValues.searchForExistingResourceOnRemoteRequest.getBoolean();
    }

    @VisibleForTesting
    ResourceStreamHandle getExistingResourceByChecksum(Set<ChecksumInfo> checksumsInfo, long size) {
        InputStream data = null;
        String sha1 = getRemoteSha1(checksumsInfo);
        try {
            RepoRequests.logToContext("Searching for existing resource with SHA-1 '%s'", sha1);
            ArtifactoryBinaryService binaryService = ContextHelper.get().beanForType(ArtifactoryBinaryService.class);
            Map<String, String> headers = Maps.newHashMap();
            headers.put(REPOSITORY_KEY.getHeaderName(), getKey());
            headers.put(CONTENT_LENGTH.getHeaderName(), String.valueOf(size));
            BinaryElement element = binaryService.createBinaryElement(sha1, null, null, -1, headers);
            data = binaryService.getBinaryForChecksumDeploy(element, false);
            RepoRequests.logToContext("Found existing resource with the same checksum - " +
                    "returning as normal content handle");
            return new SimpleResourceStreamHandle(data, size);
        } catch (BinaryNotFoundException e) {
            // not found - resume
            StreamUtils.close(data);
            return null;
        } catch (StorageException e) {
            // not found - resume
            StreamUtils.close(data);
            log.error("Error occurred while searching for existing resource: {}", e.getMessage());
            return null;
        }
    }

    private String getRemoteSha1(Set<ChecksumInfo> remoteChecksums) {
        for (ChecksumInfo remoteChecksum : remoteChecksums) {
            if (ChecksumType.sha1.equals(remoteChecksum.getType())) {
                return remoteChecksum.getOriginal();
            }
        }
        return null;
    }

    private void unexpire(RepoResource cachedResource) {
        RepoRequests.logToContext("Un-expiring cached resource if needed");
        String relativePath = cachedResource.getRepoPath().getPath();
        boolean isMetadata = cachedResource.isMetadata();

        RepoRequests.logToContext("Is resource metadata = %s", isMetadata);

        if (!isMetadata) {
            // unexpire the file
            RepoRequests.logToContext("Un-expiring the resource");
            getRepositoryService().unexpireIfExists(localCacheRepo, relativePath);
            // remove it from bad retrieval caches
            RepoRequests.logToContext("Removing the resource from all failed caches");
            removeFromCaches(relativePath, false);
        }
    }

    boolean shouldExpire(RepoResource cachedResource, RepoResource remoteResource) {
        for (RemoteRepoInterceptor interceptor : remoteRepoInterceptors) {
            ExpiryAction expiryAction =
                    interceptor.shouldExpireCachedResource(cachedResource, remoteResource);
            if (expiryAction.equals(ExpiryAction.DONT_EXPIRE_CONTINUE_NEXT)) {
                continue;
            }
            if (expiryAction.equals(ExpiryAction.EXPIRE)) {
                return true;
            }
            if (expiryAction.equals(ExpiryAction.DONT_EXPIRE_STOP)) {
                return false;
            }
        }

        return shouldExpireWithLastModified(cachedResource, remoteResource);
    }

    boolean shouldExpireWithLastModified(RepoResource cachedResource, RepoResource remoteResource) {
        return ExpiryAction.EXPIRE == new LastModifiedExpiryRemoteInterceptor()
                .shouldExpireCachedResource(cachedResource, remoteResource);
    }

    private boolean cachedExpiredAndNewerThanRemote(RepoResource remoteResource, RepoResource cachedResource) {
        return cachedResource.isExpired() && remoteResource.getLastModified() <= cachedResource.getLastModified();
    }

    private boolean notFoundAndNotExpired(RepoResource cachedResource) {
        return !cachedResource.isFound() && !cachedResource.isExpired();
    }

    private Set<ChecksumInfo> getRemoteChecksums(String path, RepoResource remoteResource) {
        Set<ChecksumInfo> checksums = new HashSet<>();
        for (ChecksumType checksumType : ChecksumType.BASE_CHECKSUM_TYPES) {
            String checksum = null;
            try {
                RepoRequests.logToContext("Trying to find remote checksum - %s", checksumType.ext());
                checksum = getRemoteChecksum(path + checksumType.ext(), remoteResource);
            } catch (FileNotFoundException e) {
                RepoRequests.logToContext("Remote checksum file doesn't exist");
            } catch (Exception e) {
                RepoRequests.logToContext("Error occurred while retrieving remote checksum: %s", e.getMessage());
            }
            ChecksumInfo info = new ChecksumInfo(checksumType, null, null);
            if (StringUtils.isNotBlank(checksum)) {
                RepoRequests.logToContext("Found remote checksum with the value - %s", checksum);
                // set the remote checksum only if it is a valid string for that checksum
                if (checksumType.isValid(checksum)) {
                    info = new ChecksumInfo(checksumType, checksum, null);
                } else {
                    RepoRequests.logToContext("Remote checksum is invalid");
                }
            }
            checksums.add(info);
        }
        return checksums;
    }

    @Override
    public String getChecksum(String path, RepoResource res) throws IOException {
        String value = null;
        if (isStoreArtifactsLocally()) {
            //We assume the resource is already contained in the repo-cache
            value = localCacheRepo.getChecksum(path, res);
        } else {
            try {
                value = getRemoteChecksum(path, res);
            } catch (RemoteRequestException e) {
                handleRemoteRequestException(e);
            }
        }

        if (value == null) {
            try {
                value = getChecksumByExternalSha256(path);
            } catch (RemoteRequestException e) {
                handleRemoteRequestException(e);
            }
        }
        return value;
    }

    private void handleRemoteRequestException(RemoteRequestException e) throws RemoteRequestException {
        // ok to fail with 404, just return null (which translates to not exist in higher levels)
        if (e.getRemoteReturnCode() != HttpStatus.SC_NOT_FOUND) {
            throw e;
        }
    }

    private String getChecksumByExternalSha256(String path) throws IOException {
        if (getRepoConfig().getPackageType().equals(RepoType.Generic)
                && getRepoConfig().getRepoTypeConfig().isRetrieveSha256FromServer()) {
            return getRemoteChecksumFromPath(path);
        }
        return null;
    }

    @Override
    @Nullable
    public LocalCacheRepo getLocalCacheRepo() {
        return localCacheRepo;
    }

    @Override
    @Nonnull
    public List<RemoteItem> listRemoteResources(String directoryPath) {
        assert !isOffline() : "Should never be called in offline mode";
        List<RemoteItem> cachedUrls = getRemoteResourceCache().getIfPresent(directoryPath);
        if (CollectionUtils.notNullOrEmpty(cachedUrls)) {
            return cachedUrls;
        }

        UnfoundRepoResource unfoundRepoResource =
                ((UnfoundRepoResource) getMissedRetrievalsCache().getIfPresent(directoryPath));
        missedRetrievalsCacheStatisticsLogger.logStats(getMissedRetrievalsCache());
        if (unfoundRepoResource != null) {
            return Collections.emptyList();
        }

        List<RemoteItem> urls = null;
        String fullDirectoryUrl = directoryPath;
        if (!HttpUtils.isAbsolute(directoryPath)) {
            fullDirectoryUrl = removeApiFromUrlAndAppend(directoryPath);
        } else if (!fullDirectoryUrl.startsWith(getUrl())) {
            return Collections.emptyList();
        }

        try {
            urls = getChildUrls(fullDirectoryUrl);
        } catch (IOException e) {
            log.debug("Error while listing remote resources", e);
            addRemoteListingEntryToMissedCache(directoryPath, e);
            RemoteRequestException remoteError =
                    (RemoteRequestException) ExceptionUtils.getCauseOfType(e, RemoteRequestException.class);
            if (remoteError == null || remoteError.getRemoteReturnCode() != HttpStatus.SC_NOT_FOUND) {
                log.info("Error listing remote resources {}: {}", fullDirectoryUrl, e.getMessage());
            }
        }

        if (CollectionUtils.isNullOrEmpty(urls)) {
            log.debug("No remote URLS were found for: {}", fullDirectoryUrl);
            return Lists.newArrayList();
        }
        getRemoteResourceCache().put(directoryPath, urls);
        return urls;
    }

    /**
     * The upstream url for {@code pathToAppend}, normally by appending it to this repository's configured origin.
     * A value that parses as absolute becomes the destination itself only when
     * {@link #mayReplaceConfiguredOrigin} allows it; deciding that from the shape of the string alone made the
     * outcome depend on two url parsers agreeing about which host they see, and they do not. See RTDEV-97315.
     */
    protected String appendAndGetUrl(String pathToAppend, RequestContext context) {
        boolean isPropertiesRequest = pathToAppend.endsWith(":properties");
        if (HttpUtils.isAbsolute(pathToAppend) && !isPropertiesRequest) {
            if (mayReplaceConfiguredOrigin(context)) {
                return pathToAppend;
            }
            log.warn("Absolute url in the artifact path of repo '{}' will not replace the configured origin: {}",
                    getKey(), pathToAppend);
        }

        if (isPropertiesRequest) {
            return removeApiFromUrlAndAppend(pathToAppend);
        }

        String remoteUrl = getAlternativeRemoteUrl(context);
        if (StringUtils.isBlank(remoteUrl)) {
            remoteUrl = getUrl();
        }
        return addTrailingSlashIfNeeded(pathToAppend, remoteUrl);
    }

    /**
     * Whether the value being sent upstream may be used verbatim as the outbound destination, replacing this
     * repository's configured origin. Granted only to {@code alternativeRemoteDownloadUrl} carried on an internal
     * or translated request: package handlers resolve download urls from upstream metadata that legitimately lives
     * on another host (PyPI serves from {@code files.pythonhosted.org}, npm tarballs from a CDN), and Xray
     * self-healing fetches a fix from elsewhere by design.
     * <p>
     * This authorizes the carrier, not the destination - it establishes that server code put the url on the
     * request, not that the host is permitted by any policy. A handler that composes that url out of client input
     * must validate the input itself: {@code RepoFilter.checkNoUrlInPath} rejects the parameter only on a direct
     * repository request, and {@link TranslatedArtifactoryRequest} forwards the parameters of the request it
     * wraps. See RTDEV-97315.
     */
    private boolean mayReplaceConfiguredOrigin(RequestContext context) {
        if (!ConstantValues.remoteRepoBlockAbsoluteArtifactPath.getBoolean()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        Request request = context.getRequest();
        return request != null
                && isAllowedQueryParamRewrite(request)
                && StringUtils.isNotBlank(request.getParameter(PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL));
    }

    private String getAlternativeRemoteUrl(RequestContext context) {
        if (context == null || !isAllowedQueryParamRewrite(context.getRequest())) {
            return null;
        }

        String remoteUrl = context.getRequest().getParameter(PARAM_ALTERNATIVE_REMOTE_SITE_URL);
        if (StringUtils.isNotBlank(remoteUrl)) {
            boolean blocked = ContextHelper.get()
                    .beanForType(UrlVerifier.class)
                    .isRemoteRepoBlocked(remoteUrl, context.getRequest().getRepoPath().getRepoKey());
            if (blocked) {
                log.warn("Remote repository {} is blocked for URL: {}", getKey(), remoteUrl);
                return null;
            }
        }

        return remoteUrl;
    }

    private String removeApiFromUrlAndAppend(String pathToAppend) {
        //If remote url ends with / it messes up the path builder, since we already add it below it's safe to remove
        String remoteUrl = PathUtils.trimTrailingSlashes(getUrl());
        ArtifactoryStandardUrlResolver artifactoryStandardUrlResolver = new ArtifactoryStandardUrlResolver(remoteUrl);
        String baseUrl = artifactoryStandardUrlResolver.getBaseUrl();
        String repoKey = artifactoryStandardUrlResolver.getRepoKey();

        String bridgePropertiesUrl = buildBridgePropertiesStorageApiUrl(baseUrl, repoKey, pathToAppend);
        if (bridgePropertiesUrl != null) {
            return bridgePropertiesUrl;
        }

        StringBuilder baseUrlBuilder = new StringBuilder(baseUrl).append("/").append(repoKey);
        return addTrailingSlashIfNeeded(pathToAppend, baseUrlBuilder.toString());
    }

    /**
     * For JFrog Bridge base URLs, legacy properties requests use the storage REST API (same pattern as
     * {@code RepoFilter} legacy redirect and {@link NamingUtils#stripMetadataFromPath(String)}) to avoid redirects.
     * <p>
     * Must use the XML properties response ({@code ?propertiesXml}). Do not use the JSON properties API
     * (e.g. {@code ?properties}) here: {@link #getRemoteProperties(String)} and the property stream pipeline expect
     * XML.
     * <p>
     * Remote folder listing must use the HTML index URL ({@code baseUrl/repoKey/path}) instead.
     * <p>
     * Returns the URL with a <b>decoded</b> artifact path so that the downstream
     * {@link HttpRepo#encodeIfNeeded} step (called by {@code downloadResource}) encodes it exactly once.
     * Using pre-encoded paths here would cause double-encoding ({@code %20} → {@code %2520}).
     *
     * @return the storage API URL, or {@code null} when not applicable
     */
    @VisibleForTesting
    String buildBridgePropertiesStorageApiUrl(String baseUrl, String repoKey, String pathToAppend) {
        if (!UrlVerifier.isBridgeUrl(baseUrl) || !NamingUtils.isProperties(pathToAppend)) {
            return null;
        }
        String artifactPath = NamingUtils.stripMetadataFromPath(pathToAppend);
        String decodedPath = HttpUtils.decodeUri(artifactPath);
        return PathUtils.trimTrailingSlashes(baseUrl) + "/api/storage/" + repoKey + "/" + decodedPath
                + "?" + BRIDGE_PROPERTIES_STORAGE_QUERY;
    }

    private void addRemoteListingEntryToMissedCache(String directoryPath, IOException e) {
        if (!getMissedRetrievalsCache().asMap().containsKey(directoryPath)) {
            String message = e.getMessage();
            getMissedRetrievalsCache().put(directoryPath, new UnfoundRepoResource(getRepoPath(directoryPath), message));
        }
    }

    public abstract List<RemoteItem> getChildUrls(String dirUrl) throws IOException;

    @Override
    public void clearCaches() {
        clearCaches(missedRetrievalsCache, remoteResourceCache);
    }

    @Override
    public void removeFromCaches(String path, boolean removeSubPaths) {
        removeFromCaches(path, removeSubPaths, missedRetrievalsCache, remoteResourceCache);
    }

    /**
     * Executed before actual download. May return an alternate handle with its own input stream to circumvent download
     */
    private void beforeResourceDownload(RepoResource resource, Properties properties, Request request) {
        // TODO: 06/03/2022 see where should we move this
        boolean fetchSourcesEagerly =
                Boolean.parseBoolean(getRepoConfig().getPackageTypeConfig().get("fetchSourcesEagerly"));
        boolean fetchJarsEagerly =
                Boolean.parseBoolean(getRepoConfig().getPackageTypeConfig().get("fetchJarsEagerly"));

        RepoRequests.logToContext("Eager source JAR fetching enabled = %s", fetchSourcesEagerly);
        RepoRequests.logToContext("Eager JAR fetching enabled = %s", fetchJarsEagerly);

        if (!fetchSourcesEagerly && !fetchJarsEagerly) {
            // eager fetching is disabled
            RepoRequests.logToContext("Eager JAR and source JAR fetching is disabled");
            return;
        }
        String replicationDownload = request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST);
        if (StringUtils.isNotBlank(replicationDownload) && Boolean.valueOf(replicationDownload)) {
            // Do not perform eager fetching in case of replication download
            RepoRequests.logToContext("Eager JAR and source JAR fetching is disabled for replication download request");
            return;
        }
        RepoPath repoPath = resource.getRepoPath();
        MavenArtifactInfo artifactInfo = MavenArtifactInfo.fromRepoPath(repoPath);
        boolean validMavenArtifactInfo = artifactInfo.isValid();
        boolean artifactHasClassifier = artifactInfo.hasClassifier();

        RepoRequests.logToContext("Valid Maven artifact info = %s", validMavenArtifactInfo);
        RepoRequests.logToContext("Artifact has classifier = %s", artifactHasClassifier);

        if (!validMavenArtifactInfo || artifactHasClassifier) {
            RepoRequests.logToContext("Eager JAR and source JAR fetching is not attempted");
            return;
        }

        String eagerPath;

        boolean artifactIsPom = "pom".equals(artifactInfo.getType());
        boolean artifactIsJar = "jar".equals(artifactInfo.getType());

        MavenArtifactInfo eagerFetchArtifactInfo = new MavenArtifactInfo(artifactInfo);
        if (fetchJarsEagerly && artifactIsPom) {
            eagerFetchArtifactInfo.setType(MavenArtifactInfo.JAR);
            eagerPath = eagerFetchArtifactInfo.getPath();
            RepoRequests.logToContext("Eagerly fetching JAR '%s'", eagerPath);
        } else if (fetchSourcesEagerly && artifactIsJar) {
            eagerFetchArtifactInfo.setClassifier("sources");
            eagerPath = eagerFetchArtifactInfo.getPath();
            RepoRequests.logToContext("Eagerly fetching source JAR '%s'", eagerPath);
        } else {
            RepoRequests.logToContext("Eager JAR and source JAR fetching is not attempted");
            return;
        }

        // Attach matrix params is exist
        eagerPath += buildRequestMatrixParams(properties);

        // pass the repo path to download eagerly
        EagerResourcesDownloader resourcesDownloader =
                InternalContextHelper.get().beanForType(EagerResourcesDownloader.class);
        String repoKey = getRepoConfig().getKey();
        RepoPath eagerRepoPath = InternalRepoPathFactory.create(repoKey, eagerPath);
        resourcesDownloader.downloadAsync(eagerRepoPath);
    }

    /**
     * Returns the checksum value from the given path of a remote checksum file
     *
     * @param path Path to remote checksum
     * @return Checksum value from the remote source
     * @throws IOException If remote checksum is not found or there was a problem retrieving it
     */
    private String getRemoteChecksum(String path, RepoResource remoteResource) throws IOException {
        if (!isMavenFamilyRepo(remoteResource)) {
            return null;
        }
        return getRemoteChecksumFromPath(path);
    }

    private String getRemoteChecksumFromPath(String path) throws IOException {
        try (ResourceStreamHandle handle = downloadResource(path)) {
            InputStream is = handle.getInputStream();
            return ChecksumUtils.checksumStringFromStream(is);
        }
    }

    /**
     * check if it is maven repo
     *
     * @param remoteResource - remote resource
     * @return if true it maven repo
     */
    private boolean isMavenFamilyRepo(RepoResource remoteResource) {
        RemoteRepoDescriptor remoteRepoDescriptor = getRepositoryService().
                remoteRepoDescriptorByKey(remoteResource.getRepoPath().getRepoKey());
        return (remoteRepoDescriptor.getType().equals(RepoType.Maven) ||
                remoteRepoDescriptor.getType().equals(RepoType.Ivy) ||
                remoteRepoDescriptor.getType().equals(RepoType.Gradle) ||
                remoteRepoDescriptor.getType().equals(RepoType.SBT));
    }

    private boolean isNonMavenRepo() {
        return StringUtils.contains(getUrl(), "registry.npmjs.org") || StringUtils.contains(getUrl(), "docker.io");
    }

    /**
     * Returns the remote properties of the given path
     *
     * @param relPath Relative path of artifact properties to synchronize
     * @return Properties if found in remote. Empty if not
     */
    protected Properties getRemoteProperties(String relPath) {
        Properties properties = (Properties) InfoFactoryHolder.get().createProperties();
        ResourceStreamHandle handle = null;
        try {
            RepoRequests.logToContext("Trying to download remote properties");
            handle = downloadResource(relPath + ":" + Properties.ROOT);
            InputStream is = handle.getInputStream();
            if (is != null) {
                RepoRequests.logToContext("Received remote property content");
                Properties remoteProperties = ((PropertiesImplWrapper) InfoFactoryHolder.get()
                        .getFileSystemJaxbAdapter().fromXML(is)).toProperties();
                addRemoteProperties(properties, remoteProperties);
            }
        } catch (WorkerStopException wksException) {
            RepoRequests.logToContext(wksException.getMessage());
            throw wksException;
        } catch (Exception e) {
            properties = null;
            RepoRequests.logToContext("Error occurred while retrieving remote properties: %s", e.getMessage());
        } finally {
            IOUtils.closeQuietly(handle);
        }
        return properties;
    }

    /**
     * To be called when retrieving an artifact which was found expired and it's remote was not newer. Synchronizes the
     * properties of the remote artifact with the local cached one
     *
     * @param repoPath Repo path to synchronize
     */
    private void synchronizeExpiredResourceProperties(RepoPath repoPath) {
        if (!isSynchronizeProperties()) {
            RepoRequests.logToContext("Remote property synchronization is disabled - " +
                    "expired resource property synchronization not attempted");
            return;
        }

        try {
            String artifactRelativePath = repoPath.getPath();
            String propertiesRelativePath = artifactRelativePath + ":" + Properties.ROOT;
            RepoPath propertiesRepoPath = InternalRepoPathFactory.create(repoPath.getRepoKey(), propertiesRelativePath,
                    repoPath.isFolder());
            String remotePropertiesRelativePath = getAltRemotePath(propertiesRepoPath);
            if (!propertiesRepoPath.getPath().equals(remotePropertiesRelativePath)) {
                RepoRequests.logToContext("Remote resource path was altered by the user plugins to - %s",
                        remotePropertiesRelativePath);
            }

            LocalCacheRepo cache = getLocalCacheRepo();
            RepoResource cachedPropertiesResource;
            if (cache != null) {
                cachedPropertiesResource = cache.getInfo(new NullRequestContext(propertiesRepoPath));
            } else {
                RepoRequests.logToContext("Local cache repo was not initialized");
                return;
            }

            Properties properties = (Properties) InfoFactoryHolder.get().createProperties();

            //Send HEAD
            RepoResource remoteResource = retrieveInfo(remotePropertiesRelativePath, propertiesRepoPath.isFolder(),
                    null);
            if (remoteResource.isFound()) {
                RepoRequests.logToContext("Found remote properties");
                if (cachedPropertiesResource.isFound() &&
                        (cachedPropertiesResource.getLastModified() > remoteResource.getLastModified())) {
                    RepoRequests.logToContext("Remote properties were not modified - no changes will be applied");
                    // remote properties are not newer
                    return;
                }

                ResourceStreamHandle resourceStreamHandle = downloadResource(remotePropertiesRelativePath);
                InputStream inputStream = null;
                try {
                    inputStream = resourceStreamHandle.getInputStream();
                    Properties remoteProperties = ((PropertiesImplWrapper) InfoFactoryHolder.get()
                            .getFileSystemJaxbAdapter().fromXML(inputStream)).toProperties();
                    addRemoteProperties(properties, remoteProperties);
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
            } else {
                RepoRequests.logToContext("Found no remote properties");
            }

            RepoPath localCacheRepoPath = InternalRepoPathFactory.create(cache.getKey(), artifactRelativePath);
            PropertiesService propertiesService = ContextHelper.get().beanForType(PropertiesService.class);
            propertiesService.setProperties(localCacheRepoPath, properties, false);
        } catch (Exception e) {
            String repoPathId = repoPath.getId();
            log.warn(LogThrottleMarker.getInstance(),
                    "Unable to synchronize the properties of the item '{}' with the remote resource",
                    repoPathId, e);
            RepoRequests.logToContext("Error occurred while synchronizing the properties: %s", e.getMessage());
        }
    }

    private void addRemoteProperties(Properties properties, Properties remoteProperties) {
        for (String remotePropertyKey : remoteProperties.keySet()) {
            Set<String> values = remoteProperties.get(remotePropertyKey);
            RepoRequests.logToContext("Found remote property key '{}' with values '%s'", remotePropertyKey,
                    values);
            if (!remotePropertyKey.startsWith(ReplicationAddon.PROP_REPLICATION_PREFIX)) {
                properties.putAll(remotePropertyKey, values);
            }
        }
    }

    private RepoResource getMissedResource(String path) {
        RepoResource repoResource = getMissedRetrievalsCache().getIfPresent(path);
        missedRetrievalsCacheStatisticsLogger.logStats(getMissedRetrievalsCache());
        return repoResource;
    }

    /**
     * Allow plugins to override the path
     *
     * @param repoPath The original repo path
     * @return Alternative path from the plugin or the same if no plugin changes it
     */
    private String getAltRemotePath(RepoPath repoPath) {
        RepoRequests.logToContext("Executing any AltRemotePath user plugins that may exist");
        AddonsManager addonsManager = InternalContextHelper.get().beanForType(AddonsManager.class);
        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        PathCtx pathCtx = new PathCtx(repoPath.getPath());
        pluginAddon.execPluginActions(AltRemotePathAction.class, pathCtx, repoPath);
        addonsManager.addonByType(WorkersAddon.class).run(
                SupportedUnifiedEvents.ALT_REMOTE_PATH, pathCtx, repoPath);
        String path = pathCtx.getPath();
        return path;
    }

    /**
     * Allow plugins to override the path
     */
    protected ResourceStreamHandle getAltContent(RepoPath repoPath) {
        RepoRequests.logToContext("Executing any AltRemoteContent user plugins that may exist");
        AddonsManager addonsManager = InternalContextHelper.get().beanForType(AddonsManager.class);
        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        ResourceStreamCtx rsCtx = new ResourceStreamCtx();
        pluginAddon.execPluginActions(AltRemoteContentAction.class, rsCtx, repoPath);
        addonsManager.addonByType(WorkersAddon.class).run(
                SupportedUnifiedEvents.ALT_REMOTE_CONTENT, rsCtx, repoPath);
        InputStream is = rsCtx.getInputStream();
        if (is != null) {
            RepoRequests.logToContext("Received alternative content from a user plugin - " +
                    "using as a normal content handle");
            return new SimpleResourceStreamHandle(is, rsCtx.getSize());
        }

        RepoRequests.logToContext("Received no alternative content handle from a user plugin");
        return null;
    }

    @SafeVarargs
    private void clearCaches(Cache<String, ?>... caches) {
        for (Cache<String, ?> cache : caches) {
            if (cache != null) {
                cache.invalidateAll();
            }
        }
    }

    @SafeVarargs
    private void removeFromCaches(String path, boolean removeSubPaths, Cache<String, ?>... caches) {
        for (Cache<String, ?> cache : caches) {
            if (cache != null && !cache.asMap().isEmpty()) {
                cache.invalidate(path);
                if (removeSubPaths) {
                    removeSubPathsFromCache(path, cache);
                }
            }
        }
    }

    private void removeSubPathsFromCache(String basePath, Cache<String, ?> cache) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(basePath));
    }

    /**
     * Constructs a matrix params string from the given properties ready to attach to an HTTP request
     *
     * @param requestProperties Properties to construct. Can be null
     * @return HTTP request ready property chain
     */
    protected String buildRequestMatrixParams(Properties requestProperties) {
        StringBuilder requestPropertyBuilder = new StringBuilder();
        if (requestProperties != null) {
            for (Map.Entry<String, String> requestPropertyEntry : requestProperties.entries()) {
                requestPropertyBuilder.append(Properties.MATRIX_PARAMS_SEP);

                String key = requestPropertyEntry.getKey();
                boolean isMandatory = false;
                if (key.endsWith(Properties.MANDATORY_SUFFIX)) {
                    key = key.substring(0, key.length() - 1);
                    isMandatory = true;
                }
                requestPropertyBuilder.append(key);
                if (isMandatory) {
                    requestPropertyBuilder.append("+");
                }
                String value = requestPropertyEntry.getValue();
                if (StringUtils.isNotBlank(value)) {
                    requestPropertyBuilder.append("=").append(value);
                }
            }
        }
        return requestPropertyBuilder.toString();
    }

    /**
     * Intercepts response to apply RemoteRepo business logic
     *
     * @param response {@link org.apache.http.client.methods.CloseableHttpResponse}
     */
    protected final CloseableHttpResponse interceptResponse(CloseableHttpResponse response) {
        if (onlineMonitorService.isResourceUnavailable(response.getStatusLine())) {
            putOffline();
        }
        return response;
    }

    @VisibleForTesting
    CurationAddon getCurationAddon() {
        return InternalContextHelper.get()
                .beanForType(AddonsManager.class)
                .addonByType(CurationAddon.class);
    }

    @VisibleForTesting
    boolean isBlockDownloadEnabled() {
        return getCurationAddon().isBlockDownloadEnabled(getRepoConfig());
    }
}
