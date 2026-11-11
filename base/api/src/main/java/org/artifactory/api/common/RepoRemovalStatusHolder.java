package org.artifactory.api.common;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.common.RepoRemovalStatus.CONTENT_PARTIALLY_DELETED;
import static org.artifactory.api.common.RepoRemovalStatus.REPO_DELETE_FAILED;

@ToString(callSuper = true)
@NoArgsConstructor
public class RepoRemovalStatusHolder extends BasicStatusHolder {

    @VisibleForTesting
    static final int ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX =
            ConstantValues.artifactDeleteReportFailureExamplesMax.getInt();

    public RepoRemovalStatusHolder(String repoKey) {
        this.repoKey = repoKey;
    }

    @Getter
    private String repoKey = "N/A";
    private volatile String mainStatusMessage;
    private volatile int mainStatusCode;
    private final AtomicLong deletedArtifactsCount = new AtomicLong(0);
    private final AtomicLong deleteArtifactsFailureCount = new AtomicLong(0);
    private volatile boolean hasWarnings;
    private final List<ArtifactDeleteFailure> artifactDeleteFailureExamples =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void warn(String statusMsg, Throwable throwable, @Nonnull Logger logger) {
        addArtifactDeleteFailure(SC_INTERNAL_SERVER_ERROR, statusMsg);
        super.warn(statusMsg, throwable, logger);
    }

    @Override
    public void warn(String statusMsg, int statusCode, Throwable throwable, @Nonnull Logger logger) {
        addArtifactDeleteFailure(statusCode, statusMsg);
        super.warn(statusMsg, statusCode, throwable, logger);
    }

    @Override
    public void warn(String statusMsg, @Nonnull Logger logger) {
        addArtifactDeleteFailure(SC_INTERNAL_SERVER_ERROR, statusMsg);
        super.warn(statusMsg, logger);
    }

    @Override
    public void warn(String statusMsg, int statusCode, @Nonnull Logger logger) {
        addArtifactDeleteFailure(statusCode, statusMsg);
        super.warn(statusMsg, statusCode, logger);
    }

    @Override
    public void error(String statusMsg, int statusCode, Throwable throwable, Logger logger) {
        addArtifactDeleteFailure(statusCode, statusMsg);
        super.error(statusMsg, statusCode, throwable, logger);
    }

    @Override
    public void error(String statusMsg, int statusCode, Logger logger, Marker marker) {
        addArtifactDeleteFailure(statusCode, statusMsg);
        super.error(statusMsg, statusCode, logger, marker);
    }

    @Override
    public void error(String statusMsg, int statusCode, Logger logger, Throwable throwable, Marker marker) {
        addArtifactDeleteFailure(statusCode, statusMsg);
        super.error(statusMsg, statusCode, logger, throwable, marker);
    }

    @Override
    public void errorWithDifferentLogLevel(String message, int statusCode, Throwable throwable, @Nonnull Logger logger,
            Level level) {
        addArtifactDeleteFailure(statusCode, message);
        super.errorWithDifferentLogLevel(message, statusCode, throwable, logger, level);
    }

    @Override
    public synchronized String getStatusMsg() {
        if (StringUtils.isNotBlank(mainStatusMessage)) {
            return mainStatusMessage;
        }
        if (allOK()) {
            mainStatusMessage = RepoRemovalStatus.getSuccessMsg(repoKey);
        } else if (allFailed()) {
            mainStatusMessage = REPO_DELETE_FAILED.getReason(repoKey);
        } else {
            mainStatusMessage = CONTENT_PARTIALLY_DELETED.getReason(repoKey);
        }
        return mainStatusMessage;
    }

    @Override
    public synchronized int getStatusCode() {
        if (mainStatusCode != 0) {
            return mainStatusCode;
        }
        if (allOK()) {
            return SC_OK;
        }
        if (allFailed()) {
            return getMostImportantStatusCode();
        }
        return SC_MULTI_STATUS;
    }

    private int getMostImportantStatusCode() {
        if (hasErrors()) {
            return getMostImportantErrorStatusCode().getStatusCode();
        }
        return getMostImportantWarningsStatusCode().getStatusCode();
    }

    private boolean allFailed() {
        return deletedArtifactsCount.get() == 0 && deleteArtifactsFailureCount.get() > 0;
    }

    private boolean allOK() {
        return deleteArtifactsFailureCount.get() == 0;
    }

    @Override
    public boolean hasWarnings() {
        return hasWarnings || super.hasWarnings();
    }

    public void incrementDeletedCount() {
        deletedArtifactsCount.incrementAndGet();
    }

    public synchronized void mainStatus(String mainStatusMessage, Logger logger) {
        this.mainStatusMessage = mainStatusMessage;
        this.mainStatusCode = SC_OK;
        logger.info(mainStatusMessage);
    }

    public synchronized void mainStatus(String mainStatusMessage, int statusCode, Logger logger) {
        this.mainStatusMessage = mainStatusMessage;
        this.mainStatusCode = statusCode;
        logger.info(mainStatusMessage);
    }

    public synchronized void mainWarning(String mainStatusMessage, int statusCode, Logger logger) {
        this.mainStatusMessage = mainStatusMessage;
        this.mainStatusCode = statusCode;
        this.hasWarnings = true;
        logger.warn(mainStatusMessage);
    }

    public RemoveRepositoryReport getReport() {
        return hasErrorsOrWarnings() ? getFailureReport() : getSuccessReport();
    }

    public RemoveRepositoryReport getSuccessReport() {
        if (hasErrorsOrWarnings()) {
            return null;
        }
        return new RemoveRepositoryReport(repoKey, mainStatusMessage, deletedArtifactsCount.get());
    }

    public RemoveRepositoryFailureReport getFailureReport() {
        if (!hasErrorsOrWarnings()) {
            return null;
        }
        // Create a snapshot of the list to avoid concurrent modification issues
        List<ArtifactDeleteFailure> failureExamplesSnapshot;
        synchronized (artifactDeleteFailureExamples) {
            failureExamplesSnapshot = new ArrayList<>(artifactDeleteFailureExamples);
        }
        return new RemoveRepositoryFailureReport(repoKey, getStatusMsg(), deletedArtifactsCount.get(),
                deleteArtifactsFailureCount.get(),
                failureExamplesSnapshot);
    }

    public boolean isSuccess() {
        return !hasErrorsOrWarnings();
    }

    /**
     * Mutate the status holder to a repo level status and clear all lower level statuses
     */
    public void mutateToRepoLevelStatus() {
        this.mainStatusCode = getStatusCode();
        this.mainStatusMessage = super.getStatusMsg();
        this.hasWarnings = true;
        this.deleteArtifactsFailureCount.set(0);
        synchronized (artifactDeleteFailureExamples) {
            this.artifactDeleteFailureExamples.clear();
        }
        clear();
    }

    private synchronized void addArtifactDeleteFailure(int statusCode, String reason) {
        incrementDeleteFailureCount();
        addArtifactDeleteFailureExample(new ArtifactDeleteFailure(statusCode, reason));
        hasWarnings = true;
    }

    private void addArtifactDeleteFailureExample(ArtifactDeleteFailure failure) {
        synchronized (artifactDeleteFailureExamples) {
            if (artifactDeleteFailureExamples.size() < ARTIFACT_DELETE_FAILURES_EXAMPLES_MAX) {
                artifactDeleteFailureExamples.add(failure);
            }
        }
    }

    private void incrementDeleteFailureCount() {
        deleteArtifactsFailureCount.incrementAndGet();
    }

    public record ArtifactDeleteFailure(int status, String message) {

        @Override
        public String toString() {
            return "{ statusCode=%d, reason=%s }".formatted(status, message);
        }
    }

    @Getter
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class RemoveRepositoryReport {
        private final String repoKey;
        private final String statusMsg;
        private final long deletedArtifactsCount;

        public boolean isSuccess() {
            return true;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class RemoveRepositoryFailureReport extends RemoveRepositoryReport {
        private final long deleteArtifactsFailureCount;
        private final List<ArtifactDeleteFailure> errors;

        RemoveRepositoryFailureReport(String repoKey, String statusMsg, long deletedArtifactsCount,
                long deletedArtifactsFailureCount, List<ArtifactDeleteFailure> artifactDeleteFailureExamples) {
            super(repoKey, statusMsg, deletedArtifactsCount);
            this.deleteArtifactsFailureCount = deletedArtifactsFailureCount;
            this.errors = artifactDeleteFailureExamples;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }
}