package org.artifactory.policy.cleanup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Builder
public record CleanupPolicyRunViewEntry(int runId,
                                        Type itemType,
                                        String policyKey,
                                        String displayProject,
                                        long startTime,
                                        long endTime,
                                        long totalItems,
                                        long cleanedSize,
                                        long cleanedItems,
                                        Status status,
                                        String triggeredBy,
                                        boolean manual,
                                        boolean dryRun,
                                        int policyTaskId,
                                        List<String> includedProjects) {

    @AllArgsConstructor
    @Getter
    public enum Status {
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

        private static final Map<String, Status> NAME_TO_ENUM = new HashMap<>();

        static {
            NAME_TO_ENUM.put(IN_PROGRESS.name.toLowerCase(), IN_PROGRESS);
            NAME_TO_ENUM.put(IN_PROGRESS_WITH_ERRORS.name.toLowerCase(), IN_PROGRESS_WITH_ERRORS);
            NAME_TO_ENUM.put(COMPLETED.name.toLowerCase(), COMPLETED);
            NAME_TO_ENUM.put(COMPLETED_WITH_ERRORS.name.toLowerCase(), COMPLETED_WITH_ERRORS);
            NAME_TO_ENUM.put(STOPPED.name.toLowerCase(), STOPPED);
            NAME_TO_ENUM.put(STOPPING.name.toLowerCase(), STOPPING);
            NAME_TO_ENUM.put(FAILED.name.toLowerCase(), FAILED);
            NAME_TO_ENUM.put(PENDING_WARM.name.toLowerCase(), PENDING_WARM);
            NAME_TO_ENUM.put(PARTIAL_WARMED.name.toLowerCase(), PARTIAL_WARMED);
            NAME_TO_ENUM.put(WARMED.name.toLowerCase(), WARMED);
        }

        public static Status fromString(String name) {
            return NAME_TO_ENUM.get(name.toLowerCase());
        }
    }

    @AllArgsConstructor
    @Getter
    public enum Type {

        PACKAGE("package"),
        ARTIFACT("artifact"),
        BUILD("build"),
        RELEASE_BUNDLE("releaseBundle");

        private final String name;

        private static final Map<String, Type> CODE_TO_ENUM = new HashMap<>();

        static {
            CODE_TO_ENUM.put(PACKAGE.name.toLowerCase(), PACKAGE);
            CODE_TO_ENUM.put(ARTIFACT.name.toLowerCase(), ARTIFACT);
            CODE_TO_ENUM.put(BUILD.name.toLowerCase(), BUILD);
            CODE_TO_ENUM.put(RELEASE_BUNDLE.name.toLowerCase(), RELEASE_BUNDLE);
        }

        public static Type fromString(String name) {
            return CODE_TO_ENUM.get(name.toLowerCase());
        }
    }

    public static final Set<Status> COLD_INSTANCE_WARMED_RUN_STATUSES =
            Set.of(Status.WARMED, Status.COMPLETED_WITH_ERRORS);
}