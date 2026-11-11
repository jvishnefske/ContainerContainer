package org.artifactory.addon.replication;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.replication.ReplicationDetails;
import org.artifactory.api.replication.ReplicationMetaData;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.exception.http.ContentTooLargeException;
import org.jfrog.common.JsonSensitiveDataException;
import org.jfrog.common.JsonUtils;

import static org.artifactory.addon.replication.ReplicationStrategy.EVENT_BASED;
import static org.artifactory.addon.replication.ReplicationStrategy.FULL;
import static org.artifactory.addon.replication.ReplicationType.PULL;
import static org.artifactory.addon.replication.ReplicationType.PUSH;
import static org.artifactory.common.ConstantValues.jobsTableAdditionalDetailsMaxSize;
import static org.artifactory.common.ConstantValues.jobsTableAdditionalDetailsStatisticsBuffer;

/**
 * Utility class for validating replication configuration against database constraints.
 * Specifically validates that replication metadata will fit within the jobs table
 * additional_details column size limit.
 *
 * @author Tomer Slutsky
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ReplicationConfigUtil {

    /**
     * Creates and serializes ReplicationDetails from the provided parameters.
     * This is the common logic for creating the JSON that will be stored in the jobs table.
     *
     * @param sourceRepo          the source repository key
     * @param includePattern      the include path prefix pattern (comma-separated or null)
     * @param excludePattern      the exclude path prefix pattern (comma-separated or null)
     * @param targetUrl           the target URL (for push replications, null for pull)
     * @param replicationStrategy the replication strategy (EVENT_BASED, FULL, INCREMENTAL, etc.)
     * @param replicationType     the replication type (PUSH or PULL)
     * @return the serialized JSON string
     */
    public static String createReplicationDetailsBlob(String sourceRepo, String includePattern, String excludePattern,
            String targetUrl, ReplicationStrategy replicationStrategy, String replicationType) {
        ReplicationMetaData replicationMetaData = new ReplicationMetaData();
        replicationMetaData.setSourceRepo(sourceRepo);
        replicationMetaData.setIncludePathPrefixPattern(includePattern);
        replicationMetaData.setExcludePathPrefixPattern(excludePattern);
        replicationMetaData.setTargetUrl(targetUrl);
        replicationMetaData.setReplicationStrategy(replicationStrategy.name());
        replicationMetaData.setReplicationType(replicationType);

        ReplicationDetails replicationDetails = new ReplicationDetails();
        replicationDetails.setReplicationMetaData(replicationMetaData);
        String detailsBlob = null;
        try {
            detailsBlob = JsonUtils.getInstance().writeValueAsString(replicationDetails);
        } catch (JsonSensitiveDataException e) {
            String error = String
                    .format("Failed to parse replication job details %s. Aborting monitoring of new replication job",
                            replicationDetails);
            log.error(error, e);
        }
        return detailsBlob;
    }

    /**
     * Validates that the replication descriptor will not exceed the database column size limit when stored.
     * This method simulates what will be stored in the jobs table to ensure the configuration is valid.
     *
     * @param descriptor the replication descriptor to validate
     * @param targetUrl  the target URL for the replication (used for local/push replications, null for remote/pull)
     * @throws ContentTooLargeException if the descriptor exceeds the size limit
     */
    public static void validateReplicationDescriptorSize(ReplicationBaseDescriptor descriptor, String targetUrl) {
        ReplicationStrategy replicationStrategy = descriptor.isEnableEventReplication() ? EVENT_BASED : FULL;
        String replicationType = targetUrl != null ? PUSH.toString() : PULL.toString();

        String detailsBlob = createReplicationDetailsBlob(
                descriptor.getRepoKey(),
                descriptor.getIncludePathPrefixPattern(),
                descriptor.getExcludePathPrefixPattern(),
                targetUrl,
                replicationStrategy,
                replicationType
        );
        validateJobDetailsSize(detailsBlob, descriptor.getReplicationKey());
    }

    /**
     * Validates that the serialized replication details will not exceed the database column size limit.
     * This is useful when you already have the JSON string and want to validate it directly.
     *
     * @param detailsBlob the JSON string of the replication details
     * @throws ContentTooLargeException if the details size exceeds the limit
     */
    public static void validateJobDetailsSize(String detailsBlob, String replicationKey) {
        if (detailsBlob == null) {
            return;
        }
        int maxSize = jobsTableAdditionalDetailsMaxSize.getInt();
        int statisticsBuffer = jobsTableAdditionalDetailsStatisticsBuffer.getInt();
        int currentSize = detailsBlob.length();
        int requiredSize = currentSize + statisticsBuffer;

        if (requiredSize > maxSize) {
            int availableSizeForPatterns = maxSize - statisticsBuffer;
            throw new ContentTooLargeException(
                    String.format(
                            "Replication %s configuration exceeds the maximum allowed size. " +
                                    "Please reduce the number of include/exclude patterns. " +
                                    "Available size for patterns and metadata: ~%d characters. actual size: %d " +
                                    "characters.",
                            replicationKey, availableSizeForPatterns, currentSize));

        }
    }
}
