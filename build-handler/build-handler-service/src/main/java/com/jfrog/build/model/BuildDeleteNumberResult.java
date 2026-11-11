package com.jfrog.build.model;

import lombok.NonNull;
import lombok.Value;

@Value
public class BuildDeleteNumberResult {

    public enum Status {
        FAILURE_ARTIFACTS, // represents partial success or complete failure, depends on the deletedArtifactsCount
        FAILURE_BUILD_INFO,
        SUCCESS;
    }

    public static final BuildDeleteNumberResult ZERO_ARTIFACTS =
            new BuildDeleteNumberResult(Status.SUCCESS, 0, 0, 0);
    public static final BuildDeleteNumberResult FAILED_BUILD_INFO =
            new BuildDeleteNumberResult(Status.FAILURE_BUILD_INFO, 0, 0, 0);

    Status status;
    int totalArtifactsCount;
    int deletedArtifactsCount;
    long deletedArtifactsVirtualBytes;

    @NonNull
    public static BuildDeleteNumberResult failedArtifacts(
            int totalArtifactsCount,
            int deletedArtifactsCount,
            long deletedArtifactsVirtualBytes) {
        return new BuildDeleteNumberResult(
                Status.FAILURE_ARTIFACTS, totalArtifactsCount, deletedArtifactsCount, deletedArtifactsVirtualBytes);
    }

    @NonNull
    public static BuildDeleteNumberResult successArtifacts(
            int totalArtifactsCount,
            int deletedArtifactsCount,
            long deletedArtifactsVirtualBytes) {
        return new BuildDeleteNumberResult(
                Status.SUCCESS, totalArtifactsCount, deletedArtifactsCount, deletedArtifactsVirtualBytes);
    }

    @NonNull
    public BuildDeleteNumberResult toBuildInfoFailure() {
        return new BuildDeleteNumberResult(Status.FAILURE_BUILD_INFO, totalArtifactsCount, deletedArtifactsCount,
                deletedArtifactsVirtualBytes);
    }
}
