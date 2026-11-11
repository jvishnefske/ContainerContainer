package org.artifactory.sh.acl;

import lombok.NonNull;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.StorageException;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.jfrog.storage.binstore.exceptions.federation.FederationClientException;
import org.jfrog.storage.binstore.ifc.BinaryStoreFederatedMember;
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

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author TamirH
 */
public interface ArtifactoryBinaryService extends ContextCreationListener, ReloadableBean, MultipartUpload {
    /**
     * Retrieve the readable stream with the bytes associated with the provided SHA1 checksum.
     *
     * @param sha1    the checksum key
     * @param headers headers
     * @return the stream that should be closed by the user
     * @throws BinaryNotFoundException If the checksum does not exist in this store
     */
    @Deprecated
    InputStream getBinary(String sha1, Map<String, String> headers) throws BinaryNotFoundException;

    /**
     * Retrieve the readable stream with the bytes associated with the provided SHA1 checksum.
     *
     * @param element {@link BinaryElement} representing the required binary
     * @return the stream that should be closed by the user
     * @throws BinaryNotFoundException If the checksum does not exist in this store
     */
    InputStream getBinary(BinaryElement element) throws BinaryNotFoundException;

    /**
     * Retrieve the binary info without bytes associated with the provided SHA1 checksum for the purpose of
     * checksumDeploy.
     * If required by checkInFilestore flag it will try to create the missing binary in the DB before
     * returning the stream wrapper
     *
     * @param element          {@link BinaryElement} representing the required binary info
     * @param checkInFilestore if true will try to create binary if not found in DB
     * @return a stream wrapper which contains required info without stream by itself
     * @throws BinaryNotFoundException If the checksum does not exist
     * @throws StorageException        If an error occurred during this process
     */
    @NonNull
    InputStream getBinaryForChecksumDeploy(BinaryElement element, boolean checkInFilestore);

    /**
     * Retrieves the download signed url from binary provider
     * <p>
     * **Note**: Should be used only for Enterprise+ license.
     * Currently we don't check that inside, as its not the binary provider scope, and the current hack
     * is disgusting.
     * Please make sure to check that in the caller method.
     *
     * @throws SignedUrlException If couldn't generate signed url
     */
    @NonNull
    SignedUrl getSignedUrl(@NonNull BinaryElement binaryElementRequest) throws SignedUrlException;

    /**
     * Retrieve all the information (sha1, sha2, MD5, length) for this SHA1 key.
     *
     * @param checksumType type of checksum to search by (md5 / sha1 / sha2)
     * @param checksum     the checksum key
     * @return The info if exists, null otherwise
     * @throws StorageException if an error happen accessing the binary store
     */
    @Nullable
    BinaryElement findBinary(ChecksumType checksumType, String checksum);

    /**
     * Retrieve a collection of binary information stored in this binary store,
     * based on a list of checksums keys.
     * The checksum type, SHA1 or MD5, will be determined by validation.
     * Any value that are invalid for all checksum type will be ignored.
     *
     * @param checksums list of checksum keys to look for
     * @return A distinct set of binaries information found, may be empty if nothing found
     * @throws StorageException if an error happen accessing the binary store
     */
    @NonNull
    Set<BinaryElement> findBinaries(@Nullable Collection<String> checksums);

    BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo();

    BinaryProvidersInfo<Map<String, String>> getFullBinaryProvidersInfo();

    /**
     * we now create the stream if needed on the add method itself.
     * Mainly used for avoiding calculating checksums when using the storage service.
     */
    @NonNull
    BinaryElement addBinary(InputStream in, Map<String, String> headers) throws IOException, BinaryRejectedException;

    /**
     * Check the sanity of this binary store.
     * DB, Filesystem or network access depending on the implementation.
     * Will throw exceptions if this binary store is not in full healthy state.
     *
     * @param localOnly when set to 'true' the ping will be done for local providers only and will not cascade to remote
     *                  providers if there are any.
     */
    void ping(boolean localOnly);

    BinariesInfo getBinariesInfo();

    /**
     * Return the total size managed by this binary store.
     *
     * @return The total size or -1 if the binary does not supports fetch of the full size.
     */
    long getStorageSize();

    /**
     * Factory method for creating this interface's data model, {@link BinaryElement}
     */
    BinaryElement createBinaryElement(String sha1, String sha2, String md5, long length,
            Map<String, String> headers);

    /**
     * @return true if instance has one of following types: Enterprise, Enterprise+ or Edge and deployed to AWS
     */
    boolean isCdnAllowed();

    void updateFederateRepoBinaryProvider(@NonNull Collection<BinaryStoreFederatedMember> membersToAdd,
            @NonNull Collection<BinaryStoreFederatedMember> membersToDelete,
            AuthenticationTokenProvider authenticationTokenProvider);

    /**
     * Add the binary data line to this data store based on all params after verification
     * from binary provider delegate.
     * Verification steps depends on configuration and delegates answers.
     * Method will return null if no binary object with specified info is found,
     * or no filestore delegate was configured.
     *
     * @param sha1   The SHA1 key value
     * @param md5    The md5 of this new entry
     * @param length The length of the binary
     * @return The full binary info object created and stored, or null if no object
     * @throws BinaryNotFoundException if verification failed
     */
    @Nullable
    BinaryElement addBinaryRecord(Map<String, String> headers, String sha1, String sha2, String md5, long length)
            throws BinaryNotFoundException;

    /**
     * Delete all leftover folders and files in the binaries directory that are not declared
     * at all in this binary store.
     *
     * @return instance that summarize the Pruner activity
     */
    PruneStatusReport prune(@Nullable PruneRequestModel pruneRequestModel);

    /**
     * The info about actual local directory where checksum binary files will be stored.
     *
     * @return info about the folder for this binary store
     */
    @Nullable
    BinariesDirInfo getBinariesDirInfo();

    /**
     * Indicates if the current binary provider is a cloud based and supports redirection
     */
    boolean isCloudProviderSupportingRedirectionConfigured();

    /**
     * Map each file provider to it's filestore folder
     *
     * @return A map of each filestore folder mapped to it's provider
     */
    StorageInfo getStorageInfoSummary();

    Collection<BinaryElement> findAllBinaries();

    /**
     * BEWARE - this method is meant for a very specific usage - the sha2 db conversion.
     * NEVER EVER touch the binaries table outside of normal flow!
     */
    boolean updateSha2ForSha1(String targetSha1, String newSha2) throws SQLException;

    /**
     * Checks whether the cloud provider can generate redirections and is allowed in configuration.
     */
    boolean canCloudProviderGenerateRedirection();

    /**
     * This method is meant for retention purposes only - restoring files from cold storage.
     * <p>
     * Checks if file is restored from cold storage.
     * This will populate return object with a dedicated archive attribute to indicate the binary restore
     * state.
     *
     * @param request The binary request containing the restore candidate sha1
     * @return object with a dedicated archive attribute to indicate the binary restore state.
     */
    BinaryProvidersResponseMap isFileRestoredFromColdStorage(BinaryElementRequest request);

    /**
     * This method is meant for retention purposes only - restoring files from cold storage.
     * <p>
     * Initiates the restore process in the scope of the archive binary provider.
     *
     * @param request The binary request containing the restore candidate sha1
     */
    void initiateRestoreFromColdStorage(BinaryElementRequest request);

    List<PersistedRemoteTask> getNextRemoteTaskFromDataDb(long batchSize, int maxExecutionsCount,
            Duration firstRetryDelay, int retryBackoffBase, long maximumExecTimeMs) throws SQLException;

    boolean isCloudProviderConfigured();

    /**
     * @return binaries tasks status of the {@param repoKey} in the given {@param currentTime}
     */
    BinariesTasksInfo getBinariesTasksRepoStatus(String repoKey, long currentTime);

    /**
     * @return binaries tasks status
     */
    Map<String, BinariesTasksInfo> getBinariesTasksStatusPerRepo();

    List<String> getAndManageErrors();

    //sha2 migration methods

    int getSha1ForMissingSha2Count() throws BinaryDbException;

    List<String> getSha1ForMissingSha2(int queryLimit, int size) throws BinaryDbException;

    /**
     * Gets persisted remote tasks, which failed to be processed and number of retries is maximum
     *
     * @param repoName name of the repository
     * @param limit    limit of tasks in result, default is 1000
     * @param page     number of batch for cases when limit is less then amount of tasks
     * @return list of persisted remote tasks
     */
    List<StorageRemoteTaskInfo> getFailingTasks(@NonNull String repoName, @Nullable Integer limit,
            @Nullable Integer page);

    /**
     * Replays failed tasks by setting to 0 a failures counter
     *
     * @param repoName name of the repository, required
     * @param sha1     sha1 of a binary, optional, if not set then tasks of the repo will be replayed
     * @return number of replayed persisted remote tasks
     */
    int replayTasks(@NonNull String repoName, @Nullable String sha1);

    /**
     * Deletes failed tasks where failures counter is maximum
     *
     * @param repoName name of the repository, required
     * @param sha1     sha1 of a binary, optional, if not set then tasks of the repo will be deleted
     * @return number of deleted persisted remote tasks
     */
    int deleteRepoFailedTasksByBinary(@NonNull String repoName, @Nullable String sha1);

    int deleteAllTasksPerRepo(@NonNull String repoName);

    void tryToOptimize();

    boolean deleteBinary(String sha1);

    boolean isNeedOptimization();

    void handleFederatedTask(FederationTaskModel federationTask) throws FederationClientException;

    boolean isFederatedV2Activated();

    boolean isRtfsEnabled();
}
