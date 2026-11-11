/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.sh.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.jfrog.sh.commons.rest.models.CloudInfo;
import com.jfrog.storage.StorageClient;
import com.jfrog.storage.StorageClientConfig;
import com.jfrog.storage.StorageClientImpl;
import com.jfrog.storage.exceptions.StorageServiceException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.artifactory.security.access.AccessService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.storage.StorageException;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.BinaryServiceImpl;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.common.PostProcessChecksumBinaryStream;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.jfrog.storage.binstore.ifc.BinaryStoreFederatedMember;
import org.jfrog.storage.binstore.ifc.BinaryStream;
import org.jfrog.storage.binstore.ifc.ClusterNode;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementRequest;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersResponseMap;
import org.jfrog.storage.binstore.ifc.model.StorageInfo;
import org.jfrog.storage.binstore.manager.BinaryProviderManager;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.jfrog.storage.binstore.providers.base.StorageRemoteTaskInfo;
import org.jfrog.storage.common.ChecksumType;
import org.jfrog.storage.common.FederationTaskModel;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.jfrog.storage.config.BinaryStoreConfigService;
import org.jfrog.storage.mpu.MultipartUploadInitiateResponse;
import org.jfrog.storage.mpu.MultipartUploadParameters;
import org.jfrog.storage.mpu.entity.MultipartUploadTask;
import org.jfrog.storage.mpu.exceptions.MultipartUploadException;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.artifactory.common.ConstantValues.providerCloudInfoCacheEvictionTime;
import static org.artifactory.common.ConstantValues.storageClientConnectionTimeoutSecs;
import static org.artifactory.common.ConstantValues.storageClientMaxConnections;
import static org.artifactory.common.ConstantValues.storageClientSocketTimeoutSecs;
import static org.artifactory.common.ConstantValues.storageClientTokenExpirySeconds;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ACTUAL_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.ARTIFICIAL_INPUT_STREAM;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_MD5;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA1;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA256;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.EVENT_SOURCE;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;

/**
 * The main binary store of Artifactory that delegates to the BinaryProvider chain.
 *
 * @author Evgeny Kurtzer
 */
@Slf4j
public class StorageBinaryServiceImpl implements InternalBinaryService {
    private final AccessService accessService;
    private final ArtifactoryTracer tracer;
    private final StorageClientMetricsProvider storageClientMetricsProvider;
    private final Supplier<CloudInfo> cloudInfoSupplier;
    private final BinaryStoreConfigService configService;

    @VisibleForTesting
    StorageClient storageClient;

    public StorageBinaryServiceImpl(AccessService accessService, ArtifactoryTracer tracer,
            StorageClientMetricsProvider storageClientMetricsProvider, BinaryStoreConfigService configService) {
        this.accessService = accessService;
        this.tracer = tracer;
        this.storageClientMetricsProvider = storageClientMetricsProvider;
        this.configService = configService;
        // Since the provider info is not expected to be amended frequently, we'll cache this data.
        cloudInfoSupplier = Suppliers.memoizeWithExpiration(
                () -> storageClient.getCloudProviderInfo(),
                providerCloudInfoCacheEvictionTime.getInt(), TimeUnit.MINUTES);
    }

    @PostConstruct
    public void after() {
        int socketTimeoutSecs = storageClientSocketTimeoutSecs.getInt();
        final StorageClientConfig storageClientConfig = StorageClientConfig.builder()
                .traceIdSupplier(tracer::getFullContextTracingId)
                .accessClientProvider(accessService::getAccessClient)
                .maxConnections(storageClientMaxConnections.getInt())
                .connectionTimeoutMillis((int) TimeUnit.SECONDS.toMillis(storageClientConnectionTimeoutSecs.getInt()))
                .socketTimeoutMillis(socketTimeoutSecs > -1 ? (int) TimeUnit.SECONDS.toMillis(socketTimeoutSecs) : -1)
                .clientTokenTTLSeconds(storageClientTokenExpirySeconds.getInt())
                .build();
        storageClient = new StorageClientImpl(storageClientConfig);
        HTTPConnectionPool pool = new HTTPConnectionPool(storageClient.getConnPoolControl());
        storageClientMetricsProvider.monitorPool(pool);
    }

    @Nullable
    @Override
    public BinaryElement addBinaryRecord(Map<String, String> headers, String sha1, String sha2, String md5,
            long length) {
        String repositoryKey = headers.get(REPOSITORY_KEY.getHeaderName());
        return storageClient.addBinaryRecord(sha1, sha2, md5, repositoryKey, length);
    }

    @NonNull
    @Override
    public BinaryElement insertRecordInDb(String sha1, String sha2, String md5, long length) throws StorageException {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public PruneStatusReport prune(@Nullable PruneRequestModel pruneRequestModel) {
        return storageClient.prune(pruneRequestModel);
    }

    @Nullable
    public File getBinariesDir() {
        return null;
    }

    @Nullable
    @Override
    public BinariesDirInfo getBinariesDirInfo() {
        return storageClient.getBinariesDirInfo();
    }

    @Override
    public boolean isCloudProviderSupportingRedirectionConfigured() {
        //we'll need this to be calling the storageClient.getCloudProviderInfo
        return false;
    }

    @Override
    public StorageInfo getStorageInfoSummary() {
        return storageClient.getStorageInfoSummary();
    }

    @Override
    public BinaryProviderManager getBinaryProviderManager() {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public Collection<BinaryElement> findAllBinaries() {
        throw new UnsupportedOperationException("Used only in tests");
    }

    @Override
    public boolean updateSha2ForSha1(String targetSha1, String newSha2) {
        throw new UnsupportedOperationException(
                "Low priority since most (all?) of the cloud customers have been performed sha2 -> sha1 migration");
    }

    @Override
    public boolean canCloudProviderGenerateRedirection() {
        return cloudInfoSupplier.get().redirectAllowed();
    }

    @Override
    public BinaryProvidersResponseMap isFileRestoredFromColdStorage(BinaryElementRequest request) {
        return null;
    }

    @Override
    public void initiateRestoreFromColdStorage(BinaryElementRequest request) {
        // Not currently implemented. Will be done in one of the next stages.
    }

    @Override
    public List<PersistedRemoteTask> getNextRemoteTaskFromDataDb(long batchSize, int maxExecutionsCount,
            Duration firstRetryDelay, int retryBackoffBase, long maximumExecTimeMs) {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public List<PersistedRemoteTask> getRemoteTasksBatch(long batchSize, int maxExecutionsCount,
            Duration firstRetryDelay, int retryBackoffBase, long maximumExecTimeMs) {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public void offerRemoteTask(PersistedRemoteTask persistedRemoteTask) {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public boolean isFileExist(Map<String, String> headers, String sha1) {
        return storageClient.exists(sha1);
    }

    @Override
    public List<PersistedRemoteTask> getTasksBySha1(String sha1) {
        throw new UnsupportedOperationException("Not part of ArtifactoryBinaryServiceImpl");
    }

    @Override
    public void clusterTopologyChanged(List<ClusterNode> activeNodes) {
        // Not for now, we will need it for the on prem version
    }

    @Override
    public int getSha1ForMissingSha2Count() throws BinaryDbException {
        throw new UnsupportedOperationException(
                "Low priority since most (all?) of the cloud customers have been performed sha2 -> sha1 migration");
    }

    @Override
    public List<String> getSha1ForMissingSha2(int queryLimit, int size) throws BinaryDbException {
        throw new UnsupportedOperationException(
                "Low priority since most (all?) of the cloud customers have been performed sha2 -> sha1 migration");
    }

    @Override
    public List<Metric> collectMetrics() {
        return List.of();//Storage has its own metrics, not needed for now
    }

    @Override
    public boolean deleteBinary(String sha1) {
        try {
            return storageClient.deleteBinary(sha1, true);
        } catch (StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Override
    public boolean isFederatedV2Activated() {
        return true;
    }

    @Override
    public boolean isRtfsEnabled() {
        return configService.shouldUseFederationV2();
    }

    @Override
    public void tryToOptimize() {
        // Not needed until we decide to support sharding
    }

    @Override
    public InputStream getBinary(String sha1, Map<String, String> headers) throws BinaryNotFoundException {
        try {
            return storageClient.getBinary(sha1, headers);
        } catch (StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Override
    public InputStream getBinary(BinaryElement element) throws BinaryNotFoundException {
        try {
            return storageClient.getBinary(element);
        } catch (StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Override
    public @NonNull InputStream getBinaryForChecksumDeploy(BinaryElement element, boolean checkInFilestore) {
        // Need to warp with BinaryServiceInputStreamWrapper for later check of instanceof
        try {
            return new BinaryServiceImpl.BinaryServiceInputStreamWrapper(
                    storageClient.searchInFilestore(element, checkInFilestore));
        } catch (BinaryDbException | StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Override
    public @NonNull SignedUrl getSignedUrl(@NonNull BinaryElement binaryElementRequest) throws SignedUrlException {
        try {
            return storageClient.getBinaryDownloadRedirect(binaryElementRequest);
        } catch (StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Nullable
    @Override
    public BinaryElement findBinary(ChecksumType checksumType, String checksum) {
        final ChecksumType convertedChecksumType = ChecksumType.valueOf(checksumType.name());
        return storageClient.searchBinary(checksum, convertedChecksumType);
    }

    @NonNull
    @Override
    public Set<BinaryElement> findBinaries(@Nullable Collection<String> checksums) {
        if (CollectionUtils.isEmpty(checksums)) {
            return Set.of();
        }
        try {
            return storageClient.searchBinaries(Set.copyOf(checksums));
        } catch (BinaryDbException | StorageServiceException e) {
            throw new org.artifactory.storage.StorageException(e);
        }
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo() {
        return storageClient.getProviderInfo(false);
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getFullBinaryProvidersInfo() {
        return storageClient.getProviderInfo(true);
    }

    @NonNull
    @Override
    public BinaryElement addBinary(BinaryStream in) throws IOException, BinaryRejectedException {
        addHeaderForArtificialInputStream(in.getStream(), in.getHeaders());
        return storageClient.addBinary(in);
    }

    @NonNull
    @Override
    public BinaryElement addBinary(InputStream in, Map<String, String> headers)
            throws IOException, BinaryRejectedException {
        addHeaderForArtificialInputStream(in, headers);
        return storageClient.addBinary(in, headers);
    }

    @Override
    public void ping(boolean localOnly) {
        // not needed for now, we have our own liveness probe
    }

    @Override
    public BinariesInfo getBinariesInfo() {
        return storageClient.getBinariesInfo();
    }

    @Override
    public long getStorageSize() {
        return storageClient.getStorageSize();
    }

    @Override
    public List<String> getAndManageErrors() {
        // Not needed until we decide to support sharding. After Task*
        return List.of();
    }

    @Override
    public BinaryElement createBinaryElement(String sha1, String sha2, String md5, long length,
            Map<String, String> headers) {
        BinaryElementImpl binaryElement = new BinaryElementImpl(sha1, sha2, md5, length);
        if (MapUtils.isNotEmpty(headers)) {
            binaryElement.getHeaders().putAll(headers);
        }
        return binaryElement;
    }

    @Override
    public BinaryStream createBinaryStream(InputStream in, Map<String, String> headers) {
        if (in instanceof BinaryStream response) {
            return response;
        } else {
            Map<String, String> streamHeader = Objects.requireNonNullElse(headers, Map.of());
            return new PostProcessChecksumBinaryStream(in, streamHeader);
        }
    }

    @Override
    public boolean isCloudProviderConfigured() {
        return cloudInfoSupplier.get().cloud();
    }

    @Override
    public boolean isCdnAllowed() {
        return cloudInfoSupplier.get().cdn();
    }

    @Override
    public void updateFederateRepoBinaryProvider(@NonNull Collection<BinaryStoreFederatedMember> membersToAdd,
            @NonNull Collection<BinaryStoreFederatedMember> membersToDelete,
            AuthenticationTokenProvider authenticationTokenProvider) {
        // Not currently implemented. It may be done in one of the next stages.
    }

    @Override
    public BinariesTasksInfo getBinariesTasksRepoStatus(String repoKey, long currentTime) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public Map<String, BinariesTasksInfo> getBinariesTasksStatusPerRepo(long currentTime) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public List<StorageRemoteTaskInfo> getFailingTasks(@NonNull String repoName, Integer limit,
            @Nullable Integer page) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public int replayTasks(@NonNull String repoName, @Nullable String sha1) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public int deleteRepoFailedTasksByBinary(@NonNull String repoName, @Nullable String sha1) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public int deleteAllTasksPerRepo(@NonNull String repoName) {
        throw new UnsupportedOperationException("Federation tasks don't belong to Storage Service");
    }

    @Override
    public boolean isNeedOptimization() {
        return false;
    }

    @Override
    public void handleFederatedTask(@NonNull FederationTaskModel taskModel) {
        storageClient.handleFederatedTask(taskModel);
    }

    @Override
    public void contextCreated() {
        // Not currently implemented. Will be checked if we need to implement it at all.
    }

    @Override
    public void contextDestroyed() {
        // Not currently implemented. Will be checked if we need to implement it at all.
    }

    @Override
    public void cleanupMultipartUploads() throws MultipartUploadException {
        storageClient.cleanupMultipartUploads();
    }

    @Override
    public Pair<MultipartUploadTask, String> getMultipartUploadStatus(String uploadId) throws MultipartUploadException {
        return storageClient.getMultipartUploadStatus(uploadId);
    }

    @Override
    public boolean isMultipartUploadSupported() {
        return storageClient.isMultipartUploadSupported();
    }

    @Override
    public MultipartUploadInitiateResponse initiateMultipartUpload(String repoKey, int partSizeMB)
            throws MultipartUploadException {
        return storageClient.initiateMultipartUpload(repoKey, partSizeMB);
    }

    @Override
    public String generateUploadUrl(String path, MultipartUploadParameters parameters) throws MultipartUploadException {
        return storageClient.generateUploadUrl(path, parameters);
    }

    @Override
    public Map<Integer, String> generateUploadUrls(int fromPartNumber, int toPartNumber, String path,
            MultipartUploadParameters uploadParameters) throws MultipartUploadException {
        throw new UnsupportedOperationException("This method should not be exposed");
    }

    @Override
    public void completeMultipartUpload(String uploadId, String path, String sha1) throws MultipartUploadException {
        storageClient.completeMultipartUpload(uploadId, path, sha1);
    }

    @Override
    public void abortMultipartUpload(String uploadId, String path) throws MultipartUploadException {
        storageClient.abortMultipartUpload(uploadId, path);
    }

    private void addHeaderForArtificialInputStream(InputStream in, Map<String, String> headers) {
        // Add relevant headers to create the artificial input stream again in storage side
        if (in instanceof ArtificialInputStream artificialInputStream) {
            headers.put(ARTIFICIAL_INPUT_STREAM.getHeaderName(), String.valueOf(true));
            headers.put(ACTUAL_LENGTH.getHeaderName(), String.valueOf(artificialInputStream.getLength()));
            headers.put(CHECKSUM_SHA1.getHeaderName(), artificialInputStream.getSha1());
            headers.put(CHECKSUM_SHA256.getHeaderName(), artificialInputStream.getSha2());
            headers.put(CHECKSUM_MD5.getHeaderName(), artificialInputStream.getMd5());
            headers.put(EVENT_SOURCE.getHeaderName(),
                    artificialInputStream.getHeaders().get(EVENT_SOURCE.getHeaderName()));
        }
    }
}
