package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusEntryWithDifferentLogLevel;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.common.repo.RepoType;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.jfrog.client.util.PathUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.artifactory.mirror.MirrorConstants.COPY_MOVE_ARTIFACT;
import static org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.BaseCopyMoveArtifactService.OperationType.COPY;

@Slf4j
public abstract class BaseCopyMoveArtifactService implements RestService {

    static final int TIMEOUT = 5;

    enum OperationType {
        COPY, MOVE
    }

    boolean isUserAuthorized(RestResponse response, String currentUser, BooleanSupplier authorizationCondition) {
        if (authorizationCondition.getAsBoolean()) {
            return true;
        }
        response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
        return false;
    }

    /**
     * Blocks copy/move whose target is inside the {@code .jfrog/} internal-metadata folder.
     * Guarded by a feature flag for safe rollback.
     * <p>
     * The check is performed on the same {@link RepoPath#getPath()} the write will actually use,
     * so path variants that normalize to {@code .jfrog/...} (e.g. a leading slash or backslash) are
     * caught. Checking the raw request string would let {@code /.jfrog/...} slip through while the
     * artifact still lands under {@code .jfrog/}.
     */
    boolean isInternalMetadataTargetBlocked(RestResponse<?> response, String targetRepoKey, String targetPath) {
        if (ConstantValues.moveCopyBlockInternalMetadataTarget.getBoolean()) {
            RepoPath targetRepoPath = InternalRepoPathFactory.create(targetRepoKey, PathUtils.formatPath(targetPath));
            if (InternalMetadataChecker.isInternalMetadata(targetRepoPath.getPath())) {
                response.responseCode(HttpServletResponse.SC_NOT_FOUND).buildResponse();
                return true;
            }
        }
        return false;
    }

    @NonNull
    ArtifactoryExecutor createExecutorService(String threadName) {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat(threadName)
                .build(), ContextHelper.get(), false);
    }

    abstract MoveMultiStatusHolder doOperation(CopyMoveArtifact artifact, RepoPath sourcePath, RepoPath targetPath);

    abstract OperationType getOperationType();

    /**
     * Populate the current activity MDC (repo, package, repo_type, group) from the source repo,
     * so the request log line for this move/copy is attributed correctly instead of inheriting
     * stale values from a previous request handled by the same Tomcat worker thread.
     * <p>
     * See RTDEV-85931.
     */
    void populateMdcFromSource(String sourceRepoKey, RepositoryService repositoryService) {
        CurrentActivity.GROUP.put(getOperationType().name().toLowerCase());
        try {
            RepoDescriptor descriptor = sourceRepoKey == null
                    ? null
                    : repositoryService.repoDescriptorByKey(sourceRepoKey);
            if (descriptor == null) {
                return;
            }
            CurrentActivity.REPO.put(sourceRepoKey);
            CurrentActivity.PACKAGE.put(descriptor.getType());
            RepoType repoType = resolveRepoType(descriptor);
            if (repoType != null) {
                CurrentActivity.REPO_TYPE.put(repoType);
            }
        } catch (Exception e) {
            log.debug("Failed to populate activity context for repo='{}': {}", sourceRepoKey, e.getMessage());
        }
    }

    private static RepoType resolveRepoType(RepoDescriptor descriptor) {
        if (descriptor instanceof VirtualRepoDescriptor) {
            return RepoType.VIRTUAL;
        }
        if (descriptor instanceof RemoteRepoDescriptor) {
            return RepoType.REMOTE;
        }
        if (descriptor instanceof FederatedRepoDescriptor || descriptor instanceof LocalRepoDescriptor) {
            return RepoType.LOCAL;
        }
        return null;
    }

    @AllArgsConstructor
    class DoInBackground implements Callable<MoveMultiStatusHolder> {

        private CopyMoveArtifact artifact;
        private RepoPath sourcePath;
        private RepoPath targetPath;

        @Override
        public MoveMultiStatusHolder call() throws Exception {
            try {
                EventLogHintManager.put(EventHintType.CopyMoveArtifact, COPY_MOVE_ARTIFACT);
                // Force suppressing layouts, we want to be able to copy stuff between layouts
                artifact.setSuppressLayouts(true);
                MoveMultiStatusHolder status = doOperation(artifact, sourcePath, targetPath);

                reportToLog(status, sourcePath, targetPath);
                return status;
            } finally {
                EventLogHintManager.remove(EventHintType.CopyMoveArtifact);
            }
        }

        private void reportToLog(MoveMultiStatusHolder status, RepoPath sourcePath, RepoPath targetPath) {
            if (isSuccessStatus(status)) {
                String isDry = artifact.isDryRun() ? "Dry run for " : "";
                boolean isCopy = COPY.equals(getOperationType());
                status.status(
                        String.format("%s %s %s to %s completed successfully, %s artifacts and %s folders were %s",
                                isDry, isCopy ? "copying" : "moving", sourcePath, targetPath,
                                status.getMovedArtifactsCount(), status.getMovedFoldersCount(),
                                isCopy ? "copied" : "moved"), log);
            } else {
                writeErrorsAndWarningsToLog(status, sourcePath, targetPath);
            }
        }

        private boolean isSuccessStatus(MoveMultiStatusHolder status) {
            return !status.isError() && !status.hasWarnings();
        }

        private void writeErrorsAndWarningsToLog(MoveMultiStatusHolder statusHolder, RepoPath sourcePath,
                RepoPath targetPath) {
            if (!isSuccessStatus(statusHolder)) {
                String isDry = artifact.isDryRun() ? "dry-run" : "";
                boolean isCopy = COPY.equals(getOperationType());
                log.info(String.format("Listing errors and warnings that happened during %s %s from %s to %s: ",
                        isDry, isCopy ? "copy" : "move", sourcePath, targetPath));
                statusHolder.getErrors().forEach(this::logErrorAtLogLevel);
                statusHolder.getWarnings().forEach(warning -> log.warn(warning.getMessage()));
            }
        }

        @VisibleForTesting
        void logErrorAtLogLevel(StatusEntry entry) {
            Level level = entry instanceof StatusEntryWithDifferentLogLevel entryWithLevel
                    ? entryWithLevel.getLogLevel() : Level.ERROR;
            String message = entry.getMessage();
            // If level is null or not a valid level, default to ERROR
            if (Level.WARN.equals(level)) {
                log.warn(message);
            } else {
                log.error(message);
            }
        }
    }
}
