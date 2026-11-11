package org.artifactory.sh.cleanup.minor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.sh.cleanup.GCProvider;
import org.artifactory.sh.cleanup.GcBinariesCleaner;
import org.artifactory.storage.DeletedGCCandidate;
import org.artifactory.storage.GCCandidate;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.artifactory.sh.cleanup.GarbageCollectorStrategy.TRASH_AND_BINARIES;

/**
 * @author Uriah Levy
 */
public class MinorGcCollector {
    private static final Logger log = LoggerFactory.getLogger(MinorGcCollector.class);
    private final GCProvider gcProvider;
    private final GcBinariesCleaner gcService;
    private final ArtifactoryExecutor executor;
    private final SecurityService securityService;
    private final int expectedBatchSize;
    int numberOfThreads;

    public MinorGcCollector(GCProvider gcProvider, SecurityService securityService,
            GcBinariesCleaner gcService, DbType databaseType) {
        this.securityService = securityService;
        this.gcProvider = gcProvider;
        this.gcService = gcService;
        numberOfThreads = ConstantValues.gcNumberOfWorkersThreads.getInt();
        if (databaseType == DbType.MSSQL) {
            // Deadlock might occurs specifically on mssql with multi threaded handling the same checksums.
            numberOfThreads = 1;
        }
        executor = getExecutor();
        expectedBatchSize = ConstantValues.trashcanMaxSearchResults.getInt();
    }

    public GarbageCollectorInfo start() {
        GarbageCollectorInfo garbageCollectorInfo = null;
        try {
            log.info("Starting GC strategy '{}'", gcProvider.getName());
            garbageCollectorInfo = garbageCollect();
            log.info("Finished GC Strategy '{}'", gcProvider.getName());

        } catch (Exception e) {
            ExceptionLogUtils.debugOrError(log, "Error occurred during Garbage Collection.", e);
        } finally {
            destroy();
        }
        return garbageCollectorInfo;
    }

    private void destroy() {
        executor.shutdownNow();
        try {
            if(!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                log.warn("Garbage Collector executor service's shut down has timed out");
            }
        } catch (InterruptedException e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed shutting down Garbage Collector executor service", e);
            Thread.currentThread().interrupt();
        }
    }

    private GarbageCollectorInfo garbageCollect() {
        List<GCCandidate> batch;
        boolean shouldStop = false;
        GarbageCollectorInfo result =
                new GarbageCollectorInfo(TRASH_AND_BINARIES.name(), ConstantValues.gcFailCountThreshold.getInt());
        while (!shouldStop && !((batch = gcProvider.getBatch()).isEmpty())) {
            shouldStop = batch.size() < expectedBatchSize;
            result.addCandidatesForDeletion(batch.size());
            List<DeletedGCCandidate> deletedGCCandidates = doWork(gcProvider, batch, result);
            deleteBlobInfos(deletedGCCandidates);
            if (!shouldStop) {
                reportAfterBatch(gcProvider, result);
            }
        }
        result.setGcEndTime(System.currentTimeMillis());
        result.calcStatus();
        result.printCollectionInfo();
        return result;
    }

    private List<DeletedGCCandidate> doWork(GCProvider gcProvider, List<GCCandidate> batch,
            GarbageCollectorInfo result) {
        log.debug("Starting to work on GC batch for provider '{}'", gcProvider.getName());
        List<Future<DeletedGCCandidate>> futures = new ArrayList<>();
        submitWorkers(gcProvider, batch, futures, result);
        List<DeletedGCCandidate> deletedGCCandidates = waitForWorkers(futures);
        log.debug("Finished working on GC batch for provider '{}'.", gcProvider.getName());
        return deletedGCCandidates;
    }

    void deleteBlobInfos(List<DeletedGCCandidate> deletedCandidates) {
        List<String> deletedBinariesSha2 = deletedCandidates.stream()
                .map(DeletedGCCandidate::getDeletedCandidate)
                .filter(Objects::nonNull)
                .map(GCCandidate::sha2)
                .filter(StringUtils::isNotBlank)
                .toList();
        gcService.deleteBlobInfos(deletedBinariesSha2);
    }

    private void submitWorkers(GCProvider gcProvider, List<GCCandidate> batch,
            List<Future<DeletedGCCandidate>> futures, GarbageCollectorInfo result) {
        for (GCCandidate candidate : batch) {
            futures.add(executor.submit(() -> executeTask(gcProvider, candidate, result)));
        }
    }

    List<DeletedGCCandidate> waitForWorkers(List<Future<DeletedGCCandidate>> futures) {
        log.debug("Waiting for GC workers to finish batch execution");
        List<DeletedGCCandidate> deletedGCCandidates = new ArrayList<>();
        for (Future<DeletedGCCandidate> future : futures) {
            try {
                DeletedGCCandidate deletedGCCandidate = future.get();
                if (deletedGCCandidate != null) {
                    deletedGCCandidates.add(deletedGCCandidate);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Waiting interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        log.debug("Workers finished one batch.");
        return deletedGCCandidates;
    }

    private void reportAfterBatch(GCProvider gcProvider, GarbageCollectorInfo result) {
        if (gcProvider.shouldReportAfterBatch()) {
            result.setGcEndTime(System.currentTimeMillis());
            result.printCollectionInfo();
        }
    }

    @VisibleForTesting
    DeletedGCCandidate executeTask(GCProvider gcProvider, GCCandidate gcCandidate, GarbageCollectorInfo result) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication originalAuthentication = securityContext.getAuthentication();
        try {
            securityService.authenticateAsSystem();
            try {
                log.debug("Starting GC action on '{}'", gcCandidate);
                boolean binaryDeleted = gcProvider.getAction().accept(gcCandidate, result);
                if (binaryDeleted) {
                    return new DeletedGCCandidate(gcCandidate);
                }
            } catch (Exception e) {
                log.debug("Unable to execute GC action on GC candidate '{}'", gcCandidate, e);
            }
        } finally {
            securityContext.setAuthentication(originalAuthentication);
        }
        return null;
    }

    @VisibleForTesting
    ArtifactoryExecutor getExecutor() {
        return ArtifactoryExecutorFactory.newClassicThreadPool(
                ThreadPoolConfig.builder()
                        .queue(new LinkedBlockingQueue<>(ConstantValues.trashcanMaxSearchResults.getInt() * 2))
                        .corePoolSize(numberOfThreads)
                        .maxThreads(numberOfThreads)
                        .threadNameFormat("art-GC-worker-%s")
                        .keepAliveTimeout(5L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.MINUTES)
                        .daemon(true)
                        .build(),
                ContextHelper.get(), true);
    }

}
