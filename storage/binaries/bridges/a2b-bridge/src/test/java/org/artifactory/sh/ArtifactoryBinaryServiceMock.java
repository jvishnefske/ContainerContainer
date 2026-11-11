package org.artifactory.sh;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.common.PostProcessChecksumBinaryStream;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.jfrog.storage.binstore.ifc.BinaryStoreFederatedMember;
import org.jfrog.storage.binstore.ifc.BinaryStream;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
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
import org.jfrog.storage.mpu.MultipartUpload;
import org.jfrog.storage.mpu.MultipartUploadUnsupported;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.NotSupportedException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class ArtifactoryBinaryServiceMock implements ArtifactoryBinaryService {

    private final ArtifactoryTestBinariesDao binariesDao;

    @Delegate(types = {MultipartUpload.class})
    private final MultipartUpload provider = new MultipartUploadUnsupported();

    @Override
    public @NonNull BinaryElement addBinary(InputStream in, Map<String, String> headers)
            throws BinaryRejectedException, IOException {
        BinaryStream binaryStream =
                createBinaryStream(in, headers);
        try (binaryStream) {
            IOUtils.copyLarge(binaryStream.getStream(), NullOutputStream.NULL_OUTPUT_STREAM);
        }
        binariesDao.create(binaryStream.getSha1(), binaryStream.getSha2(), binaryStream.getMd5(),
                binaryStream.getLength());
        return binaryStream.getBinaryElement();
    }

    protected BinaryStream createBinaryStream(InputStream in, Map<String, String> binaryHeaders) {
        binaryHeaders = binaryHeaders == null ? Map.of() : binaryHeaders;
        return new PostProcessChecksumBinaryStream(in, binaryHeaders);
    }


    @Override
    public void onContextCreated() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public InputStream getBinary(String sha1, Map<String, String> headers) throws BinaryNotFoundException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public InputStream getBinary(BinaryElement element) throws BinaryNotFoundException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public @NonNull InputStream getBinaryForChecksumDeploy(BinaryElement element, boolean checkInFilestore) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public @NonNull SignedUrl getSignedUrl(@NonNull BinaryElement binaryElementRequest) throws SignedUrlException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Nullable
    @Override
    public BinaryElement findBinary(ChecksumType checksumType, String checksum) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public @NonNull Set<BinaryElement> findBinaries(@Nullable Collection<String> checksums) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getFullBinaryProvidersInfo() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public void ping(boolean localOnly) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinariesInfo getBinariesInfo() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public long getStorageSize() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinaryElement createBinaryElement(String sha1, String sha2, String md5, long length,
            Map<String, String> headers) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean isCdnAllowed() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public void updateFederateRepoBinaryProvider(@NonNull Collection<BinaryStoreFederatedMember> membersToAdd,
            @NonNull Collection<BinaryStoreFederatedMember> membersToDelete,
            AuthenticationTokenProvider authenticationTokenProvider) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Nullable
    @Override
    public BinaryElement addBinaryRecord(Map<String, String> headers, String sha1, String sha2, String md5, long length)
            throws BinaryNotFoundException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public PruneStatusReport prune(@Nullable PruneRequestModel pruneRequestModel) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Nullable
    @Override
    public BinariesDirInfo getBinariesDirInfo() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean isCloudProviderSupportingRedirectionConfigured() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public StorageInfo getStorageInfoSummary() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public Collection<BinaryElement> findAllBinaries() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean updateSha2ForSha1(String targetSha1, String newSha2) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean canCloudProviderGenerateRedirection() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinaryProvidersResponseMap isFileRestoredFromColdStorage(BinaryElementRequest request) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public void initiateRestoreFromColdStorage(BinaryElementRequest request) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public List<PersistedRemoteTask> getNextRemoteTaskFromDataDb(long batchSize, int maxExecutionsCount,
            Duration firstRetryDelay, int retryBackoffBase, long maximumExecTimeMs) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean isCloudProviderConfigured() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public BinariesTasksInfo getBinariesTasksRepoStatus(String repoKey, long currentTime) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public Map<String, BinariesTasksInfo> getBinariesTasksStatusPerRepo() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public List<String> getAndManageErrors() {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public int getSha1ForMissingSha2Count() throws BinaryDbException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public List<String> getSha1ForMissingSha2(int queryLimit, int size) throws BinaryDbException {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public List<StorageRemoteTaskInfo> getFailingTasks(@NonNull String repoName, Integer limit, Integer page) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public int replayTasks(@NonNull String repoName, String sha1) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public int deleteRepoFailedTasksByBinary(@NonNull String repoName, String sha1) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public int deleteAllTasksPerRepo(@NonNull String repoName) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public void tryToOptimize() {
        throw new NotSupportedException("Optimization isn't implemented in this mock class");
    }

    @Override
    public boolean deleteBinary(String sha1) {
        return true;
    }

    @Override
    public boolean isNeedOptimization() {
        return false;
    }

    @Override
    public void handleFederatedTask(FederationTaskModel federationTask) {
        throw new NotSupportedException("Mock method wasn't used/implemented until now");
    }

    @Override
    public boolean isFederatedV2Activated() {
        return false;
    }

    @Override
    public boolean isRtfsEnabled() {
        return false;
    }
}
