package org.artifactory.addon.observability;

import lombok.NonNull;
import org.artifactory.common.addon.Addon;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.repo.RepoPath;
import org.artifactory.util.UnsupportedByLicenseException;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * This addon is used to handle observability system repos related requests
 *
 * @author Tomer Slutsky
 */
public interface ObservabilityAddon extends Addon {
    UnsupportedByLicenseException OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION = new UnsupportedByLicenseException(
            "Observability is only available on Pro+ licensed Artifactory instances.");

    /**
     * Adds the log to an observability repo
     */
    default Response addLogToRepo(AddLogRequest request) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Gets the logs and their metadata from the New folder, and the number of successful/failed logs
     */
    default Response getLogInfo(RepoPath newPath, RepoPath failurePath, RepoPath successPath, int maxResults) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Download a requested logFile based on its path
     */
    default void downloadLogFile(RepoPath repoPath, ArtifactoryResponse res) throws IOException {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Move a log file from New folder to success/failure folder
     */
    default void moveLogFileToNewStatusFolder(RepoPath pathTo, RepoPath pathFrom) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Delete a logfile from requested path
     */
    default void deleteLogFile(RepoPath path) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Deletes files by paths
     *
     * @param repoKey - repo key to delete from
     * @param paths   - path to a file
     */
    default void deleteLogFiles(@NonNull String repoKey, @NonNull List<String> paths) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }

    /**
     * Identifies and deletes outdated log files based on specified parameters
     *
     * @param logType   a logType of the log files to be deleted
     * @param logStatus a status of the log files to be deleted
     * @param numOfDays a number of days before the current date. Files older than this number of days will be deleted
     */
    default void cleanupLogFiles(@NonNull String logType, @NonNull String logStatus, int numOfDays) {
        throw OBSERVABILITY_UNSUPPORTED_OPERATION_EXCEPTION;
    }
}
