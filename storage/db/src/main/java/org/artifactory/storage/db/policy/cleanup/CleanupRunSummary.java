package org.artifactory.storage.db.policy.cleanup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Builder(toBuilder = true)
public record CleanupRunSummary(
        String policyKey,
        int policyRunId,
        boolean dryRun,
        long startedDate,
        long finishedDate,
        Status status,
        Type type,
        boolean tempNodesCleaned,
        long deletedPackagesCount,
        long failedPackagesCount,
        long deletedArtifactsCount,
        long failedArtifactsCount,
        long originalBinarySize,
        long estimatedDeletedBinarySize,
        String triggeredBy,
        long totalBinarySize,
        long rejectedPackagesCount,
        CleanupRunConfig runConfig
) {

    @AllArgsConstructor
    @Getter
    public enum Status {
        IN_PROGRESS("inProgress"),
        IN_PROGRESS_WITH_ERRORS("inProgressWithErrors"),
        COMPLETED("completed"),
        COMPLETED_WITH_ERRORS("completedWithErrors"),
        STOPPED("stopped"),
        STOPPING("stopping"),
        FAILED("failed");

        private final String name;

        private static final Map<String, Status> CODE_TO_ENUM = new HashMap<>();

        static {
            CODE_TO_ENUM.put(IN_PROGRESS.name.toLowerCase(), IN_PROGRESS);
            CODE_TO_ENUM.put(IN_PROGRESS_WITH_ERRORS.name.toLowerCase(), IN_PROGRESS_WITH_ERRORS);
            CODE_TO_ENUM.put(COMPLETED.name.toLowerCase(), COMPLETED);
            CODE_TO_ENUM.put(COMPLETED_WITH_ERRORS.name.toLowerCase(), COMPLETED_WITH_ERRORS);
            CODE_TO_ENUM.put(STOPPED.name.toLowerCase(), STOPPED);
            CODE_TO_ENUM.put(STOPPING.name.toLowerCase(), STOPPING);
            CODE_TO_ENUM.put(FAILED.name.toLowerCase(), FAILED);
        }

        public static Status fromString(String name) {
            return CODE_TO_ENUM.get(name.toLowerCase());
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
        public String getNameSnakeCase() {
            return this.toString().toLowerCase().replace("_", "-");
        }
    }
}