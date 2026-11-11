package org.artifactory.storage.db.policy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.COMPLETED;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.COMPLETED_WITH_ERRORS;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.FAILED;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.STOPPED;

@Builder(toBuilder = true)
public record RetentionRunSummary(
        String id,
        String policyKey,
        int runId,
        boolean dryRun,
        long startedDate,
        long finishedDate,
        RetentionRunSummary.RetentionType retentionType,
        RetentionRunSummary.Status status,
        RetentionRunSummary.ItemType itemType,
        long processedItemsCount,
        long failedItemsCount,
        long totalItemsCount,
        long rejectedItemsCount,
        long processedPhysicalBinarySize,
        long processedVirtualBinarySize,
        long totalBinarySize,
        String triggeredBy,
        boolean tempNodesCleaned,
        PackageRunSummaryConfig runConfig
) {
    @AllArgsConstructor
    @Getter
    public enum Status {
        NOT_INITIALIZED("notInitialized"),
        IN_PROGRESS("inProgress"),
        IN_PROGRESS_WITH_ERRORS("inProgressWithErrors"),
        COMPLETED("completed"),
        COMPLETED_WITH_ERRORS("completedWithErrors"),
        STOPPED("stopped"),
        STOPPING("stopping"),
        FAILED("failed"),
        PENDING_WARM("pendingWarm"),
        PARTIAL_WARMED("partialWarmed"),
        WARMED("warmed");

        private final String name;
        private static final Map<String, RetentionRunSummary.Status> CODE_TO_ENUM = new HashMap<>();


        static {
            CODE_TO_ENUM.put(NOT_INITIALIZED.name.toLowerCase(), NOT_INITIALIZED);
            CODE_TO_ENUM.put(IN_PROGRESS.name.toLowerCase(), IN_PROGRESS);
            CODE_TO_ENUM.put(IN_PROGRESS_WITH_ERRORS.name.toLowerCase(), IN_PROGRESS_WITH_ERRORS);
            CODE_TO_ENUM.put(COMPLETED.name.toLowerCase(), COMPLETED);
            CODE_TO_ENUM.put(COMPLETED_WITH_ERRORS.name.toLowerCase(), COMPLETED_WITH_ERRORS);
            CODE_TO_ENUM.put(STOPPED.name.toLowerCase(), STOPPED);
            CODE_TO_ENUM.put(STOPPING.name.toLowerCase(), STOPPING);
            CODE_TO_ENUM.put(FAILED.name.toLowerCase(), FAILED);
            CODE_TO_ENUM.put(PENDING_WARM.name.toLowerCase(), PENDING_WARM);
            CODE_TO_ENUM.put(PARTIAL_WARMED.name.toLowerCase(), PARTIAL_WARMED);
            CODE_TO_ENUM.put(WARMED.name.toLowerCase(), WARMED);
        }

        public static RetentionRunSummary.Status fromString(String name) {
            return CODE_TO_ENUM.get(name.toLowerCase());
        }
    }

    @AllArgsConstructor
    @Getter
    public enum ItemType {

        PACKAGE("package"),
        RELEASE_BUNDLE("release_bundle"),
        BUILD("build");

        private final String name;

        private static final Map<String, RetentionRunSummary.ItemType> CODE_TO_ENUM = new HashMap<>();

        static {
            CODE_TO_ENUM.put(PACKAGE.name.toLowerCase(), PACKAGE);
            CODE_TO_ENUM.put(RELEASE_BUNDLE.name.toLowerCase(), RELEASE_BUNDLE);
            CODE_TO_ENUM.put(BUILD.name.toLowerCase(), BUILD);
        }

        public static RetentionRunSummary.ItemType fromString(String name) {
            return CODE_TO_ENUM.get(name.toLowerCase());
        }
    }

    @AllArgsConstructor
    @Getter
    public enum RetentionType {

        CLEANUP("cleanup"),
        ARCHIVE("archive"),
        RESTORE("restore");

        private final String name;

        private static final Map<String, RetentionRunSummary.RetentionType> CODE_TO_ENUM = new HashMap<>();

        static {
            CODE_TO_ENUM.put(CLEANUP.name.toLowerCase(), CLEANUP);
            CODE_TO_ENUM.put(ARCHIVE.name.toLowerCase(), ARCHIVE);
            CODE_TO_ENUM.put(RESTORE.name.toLowerCase(), RESTORE);
        }

        public static RetentionRunSummary.RetentionType fromString(String name) {
            return CODE_TO_ENUM.get(name.toLowerCase());
        }
    }

    public static RetentionRunSummary.ItemType fromCleanupSummaryType(CleanupRunSummary.Type cleanupType) {
        if (cleanupType == null) {
            throw new IllegalArgumentException("Cleanup type cannot be null.");
        }

        try {
            return RetentionRunSummary.ItemType.fromString(cleanupType.name());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CleanupRunSummary.Type: " + cleanupType, e);
        }
    }

    public static final Set<Status>
            FINISHED_RESTORE_WARM_RUN_STATUSES = Set.of(COMPLETED, COMPLETED_WITH_ERRORS, STOPPED, FAILED);
}
