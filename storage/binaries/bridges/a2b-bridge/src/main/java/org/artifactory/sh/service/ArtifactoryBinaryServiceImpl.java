package org.artifactory.sh.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.jfrog.storage.exceptions.StorageServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.ClusterOperationsService;
import org.artifactory.addon.ha.interceptor.ClusterTopologyListener;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.common.InternalEmptyInputStream;
import org.jfrog.storage.binstore.common.PostProcessChecksumBinaryStream;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.jfrog.storage.binstore.ifc.BinaryStoreFederatedMember;
import org.jfrog.storage.binstore.ifc.BinaryStream;
import org.jfrog.storage.binstore.ifc.ClusterNode;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;
import org.jfrog.storage.binstore.ifc.model.BinaryElementRequest;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersResponseMap;
import org.jfrog.storage.binstore.ifc.model.StorageInfo;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.jfrog.storage.binstore.providers.base.StorageRemoteTaskInfo;
import org.jfrog.storage.common.FederationTaskModel;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.jfrog.storage.detector.openinputstream.OpenInputStreamDetector;
import org.jfrog.storage.mpu.MultipartUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author TamirH
 */
@Slf4j
@Service
@Reloadable(beanClass = ArtifactoryBinaryService.class, initAfter = {ClusterOperationsService.class},
        listenOn = CentralConfigKey.none)
public class ArtifactoryBinaryServiceImpl
        implements ArtifactoryBinaryService, ClusterTopologyListener, ContextCreationListener {

    @Delegate(types = {MultipartUpload.class})
    @VisibleForTesting
    InternalBinaryService binaryService;
    private final OpenInputStreamDetector openInputStreamDetector = new OpenInputStreamDetector();
    @VisibleForTesting
    AddonsManager addonsManager;
    private final Cache<String, Boolean> cache =
            Caffeine.newBuilder()
                    .expireAfterWrite(ConstantValues.rtfsStatusRefreshIntervalSecs.getInt(), TimeUnit.SECONDS).build();

    @Autowired
    public void setBinaryService(InternalBinaryService binaryService) {
        this.binaryService = binaryService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void destroy() {
        this.binaryService.contextDestroyed();
    }

    // Upload / Download
    @Override
    @Deprecated
    public InputStream getBinary(String sha1, Map<String, String> headers) throws BinaryNotFoundException {
        try {
            addFederatedHeaders(headers);
            addRangeHeaders(headers);
            return openInputStreamDetector.wrap(binaryService.getBinary(sha1, headers));
        } catch (StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getBinary(BinaryElement element) throws BinaryNotFoundException {
        try {
            addFederatedHeaders(element.getHeaders());
            addRangeHeaders(element.getHeaders());
            return openInputStreamDetector.wrap(binaryService.getBinary(element));
        } catch (StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @NonNull
    public InputStream getBinaryForChecksumDeploy(BinaryElement element, boolean checkInFilestore) {
        try {
            addFederatedHeaders(element.getHeaders());
            return binaryService.getBinaryForChecksumDeploy(element, checkInFilestore);
        } catch (BinaryDbException | StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    @NonNull
    public SignedUrl getSignedUrl(@NonNull BinaryElement binaryElementRequest) throws SignedUrlException {
        try {
            return binaryService.getSignedUrl(binaryElementRequest);
        } catch (StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    @NonNull
    public BinaryElement addBinary(InputStream in, @NonNull Map<String, String> headers)
            throws IOException, BinaryRejectedException {
        getStreamHeaders(in, headers);
        try {
            addFederatedHeaders(headers);
            return binaryService.addBinary(in, headers);
        } catch (BinaryDbException | org.jfrog.storage.StorageException | StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    private void getStreamHeaders(InputStream in, @NonNull Map<String, String> headers) {
        addRetentionHeader(headers);
        addInternalEmptyFileHeaderIfNeeded(in, headers);
    }

    private void addInternalEmptyFileHeaderIfNeeded(InputStream in, @NonNull Map<String, String> headers) {
        if (in instanceof InternalEmptyInputStream) {
            headers.put(BinaryElementHeaders.INTERNAL_EMPTY_FILE.getHeaderName(), "true");
        }
    }

    private void addRetentionHeader(@NonNull Map<String, String> headers) {
        String repositoryKey = headers.get(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName());
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        addHeadersIfNeeded(artifactRetentionAddon.getCreateBinaryHeaders(repositoryKey), headers);
    }

    private void addHeadersIfNeeded(@Nonnull Map<String, String> headersToAdd,
            @Nonnull Map<String, String> currentHeaders) {
        headersToAdd.forEach(
                (key, value) -> currentHeaders.put(BinaryElementHeaders.fromHeaderName(key).getHeaderName(), value));
    }

    @Override
    @NonNull
    public BinaryElement addBinaryRecord(Map<String, String> headers, String sha1, String sha2, String md5, long length)
            throws BinaryNotFoundException {
        try {
            addFederatedHeaders(headers);
            return binaryService.addBinaryRecord(headers, sha1, sha2, md5, length);
        } catch (BinaryDbException e) {
            throw new StorageException(e);
        }
    }


    // Search

    @Override
    public Collection<BinaryElement> findAllBinaries() {
        try {
            return binaryService.findAllBinaries();
        } catch (BinaryDbException e) {
            throw new StorageException(e);
        }
    }

    @Override
    @NonNull
    public BinaryElement findBinary(ChecksumType checksumType, String checksum) {
        try {
            return binaryService.findBinary(org.jfrog.storage.common.ChecksumType.valueOf(checksumType.name()),
                    checksum);
        } catch (BinaryDbException | StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    @NonNull
    public Set<BinaryElement> findBinaries(@Nullable Collection<String> checksums) {
        if (CollectionUtils.isEmpty(checksums)) {
            return Set.of();
        }
        try {
            return binaryService.findBinaries(checksums);
        } catch (BinaryDbException | StorageServiceException e) {
            throw new StorageException(e);
        }
    }


    // Providers
    @Override
    public BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo() {
        return binaryService.getBinaryProvidersInfo();
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getFullBinaryProvidersInfo() {
        return binaryService.getFullBinaryProvidersInfo();
    }

    // Binaries Meta

    @Override
    public BinariesInfo getBinariesInfo() {
        try {
            return binaryService.getBinariesInfo();
        } catch (BinaryDbException | StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public long getStorageSize() {
        try {
            return binaryService.getStorageSize();
        } catch (BinaryDbException e) {
            throw new StorageException(e);
        }
    }

    @Override
    @Nullable
    public BinariesDirInfo getBinariesDirInfo() {
        return binaryService.getBinariesDirInfo();
    }

    @Override
    public boolean isCloudProviderSupportingRedirectionConfigured() {
        return binaryService.isCloudProviderSupportingRedirectionConfigured();
    }

    @Override
    public StorageInfo getStorageInfoSummary() {
        return binaryService.getStorageInfoSummary();
    }

    @Override
    public boolean isCdnAllowed() {
        return binaryService.isCdnAllowed();
    }


    @Override
    public boolean canCloudProviderGenerateRedirection() {
        return binaryService.canCloudProviderGenerateRedirection();
    }

    @Override
    public BinaryProvidersResponseMap isFileRestoredFromColdStorage(BinaryElementRequest request) {
        addFederatedHeaders(request.getHeaders());
        return binaryService.isFileRestoredFromColdStorage(request);
    }

    @Override
    public void initiateRestoreFromColdStorage(BinaryElementRequest request) {
        binaryService.initiateRestoreFromColdStorage(request);
    }

    // Actions
    @Override
    public void ping(boolean localOnly) {
        binaryService.ping(localOnly);
    }

    @Override
    public PruneStatusReport prune(@Nullable PruneRequestModel pruneRequestModel) {
        try {
            return binaryService.prune(pruneRequestModel);
        } catch (org.jfrog.storage.StorageException se) {
            throw new StorageException(se);
        }
    }

    // Utils
    @Override
    public BinaryElement createBinaryElement(String sha1, String sha2, String md5, long length,
            Map<String, String> headers) {
        final BinaryElementImpl binaryElement = new BinaryElementImpl(sha1, sha2, md5, length);
        if (headers != null) {
            binaryElement.getHeaders().putAll(headers);
        }
        return binaryElement;
    }

    @VisibleForTesting
    static BinaryStream createBinaryStream(@NonNull InputStream in, @Nullable Map<String, String> headers) {
        if (in instanceof BinaryStream response) {
            return response;
        }
        Map<String, String> streamHeader = headers == null ? Maps.newHashMap() : headers;
        return new PostProcessChecksumBinaryStream(in, streamHeader);
    }


    // Federations
    @Override
    public void updateFederateRepoBinaryProvider(@NonNull Collection<BinaryStoreFederatedMember> membersToAdd,
            @NonNull Collection<BinaryStoreFederatedMember> membersToDelete,
            AuthenticationTokenProvider authenticationTokenProvider) {
        binaryService.updateFederateRepoBinaryProvider(membersToAdd, membersToDelete, authenticationTokenProvider);
    }

    @Override
    public boolean updateSha2ForSha1(String targetSha1, String newSha2) throws SQLException {
        try {
            return binaryService.updateSha2ForSha1(targetSha1, newSha2);
        } catch (BinaryDbException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public List<PersistedRemoteTask> getNextRemoteTaskFromDataDb(long batchSize, int maxExecutionsCount,
            Duration firstRetryDelay, int retryBackoffBase, long maximumExecTimeMs) throws SQLException {
        return binaryService.getNextRemoteTaskFromDataDb(batchSize, maxExecutionsCount, firstRetryDelay,
                retryBackoffBase, maximumExecTimeMs);
    }

    @Override
    public boolean isCloudProviderConfigured() {
        return binaryService.isCloudProviderConfigured();
    }

    @Override
    public BinariesTasksInfo getBinariesTasksRepoStatus(String repoKey, long currentTime) {
        return binaryService.getBinariesTasksRepoStatus(repoKey, currentTime);
    }

    @Override
    public Map<String, BinariesTasksInfo> getBinariesTasksStatusPerRepo() {
        long currentTime = System.currentTimeMillis();
        return binaryService.getBinariesTasksStatusPerRepo(currentTime);
    }

    @Override
    public List<String> getAndManageErrors() {
        return binaryService.getAndManageErrors();
    }

    @Override
    public int getSha1ForMissingSha2Count() {
        return binaryService.getSha1ForMissingSha2Count();
    }

    @Override
    public List<String> getSha1ForMissingSha2(int queryLimit, int size) {
        return binaryService.getSha1ForMissingSha2(queryLimit, size);
    }

    @Override
    public List<StorageRemoteTaskInfo> getFailingTasks(@NonNull String repoName, @Nullable Integer limit,
            @Nullable Integer page) {
        return binaryService.getFailingTasks(repoName, limit, page);
    }

    @Override
    public int replayTasks(@NonNull String repoName, @Nullable String sha1) {
        return binaryService.replayTasks(repoName, sha1);
    }

    /**
     * Deletes failed tasks for a specific repository and binary based on the configured maximum retry count.
     *
     * @param repoName The name of the repository.
     * @param sha1     The SHA-1 hash of the binary. Can be {@code null} to
     *                 include failures for all binaries in the repository.
     * @return The number of failed tasks deleted.
     */
    @Override
    public int deleteRepoFailedTasksByBinary(@NonNull String repoName, @Nullable String sha1) {
        return binaryService.deleteRepoFailedTasksByBinary(repoName, sha1);
    }

    /**
     * Deletes all tasks associated with a specific repository.
     *
     * @param repoName The name of the repository.
     */
    @Override
    public int deleteAllTasksPerRepo(@NonNull String repoName) {
        return binaryService.deleteAllTasksPerRepo(repoName);
    }

    @Override
    public void clusterTopologyChanged(List<ArtifactoryServer> activeNodes) {
        List<ClusterNode> active = activeNodes.stream()
                .filter(Objects::nonNull)
                .map(this::clusterNodeFromArtServer)
                .toList();
        binaryService.clusterTopologyChanged(active);
    }

    private ClusterNode clusterNodeFromArtServer(ArtifactoryServer artifactoryServer) {
        return new ClusterNode(artifactoryServer.getContextUrl(), artifactoryServer.getServerId(),
                artifactoryServer.getArtifactoryVersion(), artifactoryServer.getArtifactoryRevision());
    }

    @Override
    public void onContextCreated() {
        this.binaryService.contextCreated();
    }

    @Override
    public void tryToOptimize() {
        binaryService.tryToOptimize();
    }

    @Override
    public boolean deleteBinary(String sha1) {
        try {
            return binaryService.deleteBinary(sha1);
        } catch (StorageServiceException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public boolean isNeedOptimization() {
        return binaryService.isNeedOptimization();
    }

    @Override
    public void handleFederatedTask(FederationTaskModel federationTask) {
        binaryService.handleFederatedTask(federationTask);
    }

    @Override
    public boolean isFederatedV2Activated() {
        return binaryService.isFederatedV2Activated();
    }

    @Override
    public boolean isRtfsEnabled() {
        return Boolean.TRUE.equals(cache.get("isRtfsEnabled", key -> binaryService.isRtfsEnabled()));
    }

    @VisibleForTesting
    void addFederatedHeaders(Map<String, String> headers) {
        if (isFederatedV2Activated() && !isRtfsEnabled()) {
            headers.put(BinaryElementHeaders.SKIP_FEDERATED.getHeaderName(), "true");
        }

        boolean isFederatedRepo =
                ContextHelper.get().getRepositoryConfigService().existsByType(RepoDetailsType.FEDERATED,
                        headers.get(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName()));

        if (isFederatedRepo) {
            headers.put(BinaryElementHeaders.FEDERATED_REPO.getHeaderName(), "true");
        } else {
            headers.put(BinaryElementHeaders.FEDERATED_REPO.getHeaderName(), "false");
        }
    }

    /**
     * Adds Range and If-Range headers from the current HTTP request to the storage headers map.
     * This enables range request support in the binary provider layer.
     */
    @VisibleForTesting
    void addRangeHeaders(Map<String, String> headers) {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        if (request != null && ConstantValues.httpRangeSupport.getBoolean()) {
            String range = request.getHeader(HttpHeaders.RANGE);
            if (StringUtils.isNotBlank(range)) {
                headers.put(HttpHeaders.RANGE, range);
                String ifRange = request.getHeader(HttpHeaders.IF_RANGE);
                if (StringUtils.isNotBlank(ifRange)) {
                    headers.put(HttpHeaders.IF_RANGE, ifRange);
                }
            }
        }
    }
}
