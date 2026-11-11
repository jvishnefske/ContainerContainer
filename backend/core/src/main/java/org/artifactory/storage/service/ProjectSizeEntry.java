package org.artifactory.storage.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents the status, error message, and project size for binary storage calculation.
 * This record is used by {@linkplain StorageCacheService} to cache the results of project binary size calculations.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProjectSizeEntry {
    private final @NonNull ProjectSizeEntry.CalcStatus status;
    private final @Nullable Long projectSize;

    /**
     * Create a success status with project size
     *
     * @param projectSize the project size in bytes
     * @return ProjectSizeHolder with SUCCESS status
     */
    public static ProjectSizeEntry success(long projectSize) {
        return new ProjectSizeEntry(CalcStatus.SUCCESS, projectSize);
    }

    /**
     * Create a ProjectSizeEntry with specified error status
     *
     * @param status the error status
     * @return ProjectSizeEntry with the specified error status
     */
    public static ProjectSizeEntry failure(@NonNull ProjectSizeEntry.CalcStatus status) {
        return new ProjectSizeEntry(status, null);
    }

    /**
     * Check if the project size calculation was successful
     *
     * @return true if status is SUCCESS, false otherwise
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.status == CalcStatus.SUCCESS;
    }

    @JsonProperty("status")
    public @NonNull ProjectSizeEntry.CalcStatus status() {
        return status;
    }

    @JsonProperty("projectSize")
    public Long projectSize() {
        return projectSize;
    }

    /**
     * Status of project binary size calculation.
     *
     * @author Evgeny Kurtser
     */
    public enum CalcStatus {
        /**
         * Success - project size was calculated successfully
         */
        SUCCESS("Success"),
        /**
         * Server error - error occurred during calculation (database errors, exceptions, interruptions)
         */
        SERVER_ERROR("Server error"),
        /**
         * Error indicating that the number of repositories in the project exceeded the configured maximum
         */
        PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED("Number of repositories exceeded"),
        /**
         * Error indicating that the number of artifacts in the repository exceeded the configured maximum
         */
        PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED("Number of artifacts in repository exceeded"),
        /**
         * Error indicating that the project name is ineligible
         */
        PROJECT_ERROR_INELIGIBLE_NAME("Ineligible project name"),
        /**
         * Error indicating that the project has no repositories
         */
        PROJECT_ERROR_NO_REPOS("No repositories found");

        private final String name;

        CalcStatus(String name) {
            this.name = name;
        }

        @JsonValue
        public String getName() {
            return name;
        }
    }
}

