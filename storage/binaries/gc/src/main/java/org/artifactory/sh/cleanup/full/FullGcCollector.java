package org.artifactory.sh.cleanup.full;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.sh.cleanup.GcBinariesCleaner;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.StorageException;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author TamirH
 */
@Slf4j
public class FullGcCollector {
    private final GcBinariesCleaner gcService;
    private final StorageService storageService;
    private final BinariesGcDao binariesGcDao;
    private final int gcFailCountThreshold;
    private final long gcBinariesToDeleteIterationAmount;
    private final long gcMaxRunTimeMilliseconds;
    private final long gcSleepBetweenIterationsSeconds;
    private final boolean gcCheckBalanceAfterFullGc;
    private final ArtifactoryExecutor executorService;

    public FullGcCollector(GcBinariesCleaner gcService, StorageService storageService,
            BinariesGcDao binariesGcDao) {
        this.gcService = gcService;
        this.storageService = storageService;
        this.binariesGcDao = binariesGcDao;
        this.gcFailCountThreshold = ConstantValues.gcFailCountThreshold.getInt();
        this.gcBinariesToDeleteIterationAmount = ConstantValues.gcBinariesToDeleteIterationAmount.getLong();
        this.gcMaxRunTimeMilliseconds = TimeUnit.MINUTES.toMillis(ConstantValues.gcMaxRunTimeMinutes.getLong());
        this.gcSleepBetweenIterationsSeconds = ConstantValues.gcSleepBetweenIterationsSeconds.getLong();
        this.gcCheckBalanceAfterFullGc = ConstantValues.gcCheckBalanceAfterFullGc.getBoolean();
        int numOfThreads = Math.max(ConstantValues.gcNumberOfWorkersThreads.getInt(), 1);
        this.executorService =
                ArtifactoryExecutorFactory.newClassicThreadPool(
                        ThreadPoolConfig.builder()
                                .queue(new LinkedBlockingQueue<>())
                                .corePoolSize(numOfThreads)
                                .maxThreads(numOfThreads)
                                .threadNameFormat("art-gc-%s")
                                .keepAliveTimeout(1L)
                                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                                .build(),
                        ContextHelper.get(), true);
    }

    @NonNull
    public GarbageCollectorInfo start() {
        final GarbageCollectorInfo result = new GarbageCollectorInfo("FULL", gcFailCountThreshold);
        int previousCheckSumsCleaned = 0;
        int iteration = 1;
        int prevIterDeletedBinaries = Integer.MAX_VALUE;
        long maxEndTime = result.getGcStartTime() + gcMaxRunTimeMilliseconds;
        try {
            List<BinaryElement> binsToDelete = this.findPotentialDeletion(null);
            while (shouldRunNextBatch(binsToDelete.size(), prevIterDeletedBinaries, iteration, maxEndTime, result)) {
                sleepBetweenGcIterations(iteration);
                log.info("Found {} candidates for deletion, current iteration count is {}, deleted on previous " +
                        "iterations {}", binsToDelete.size(), iteration, result.getBinariesCleaned().get());
                result.addCandidatesForDeletion(binsToDelete.size());
                Collection<BinaryElement> deletedBinaries = deleteBinaries(result, binsToDelete);
                gcService.deleteBlobInfos(deletedBinaries.stream().map(BinaryElement::getSha2).toList());
                if (result.getChecksumsCleaned().get() - previousCheckSumsCleaned > 0) {
                    previousCheckSumsCleaned = result.getChecksumsCleaned().get();
                }
                if (result.maxFailuresReached()) {
                    log.warn("The GC is stopping because max amount of failures was reached");
                    break;
                }
                iteration++;
                prevIterDeletedBinaries = deletedBinaries.size();
                binsToDelete = this.findPotentialDeletion(binsToDelete.getLast().getSha1());
            }
        } catch (BinaryDbException e) {
            throw new StorageException(e);
        }

        log.info("Found {} candidates for deletion, iteration count is {}, deleted {} binaries, total size of {} bytes",
                result.getCandidatesForDeletion(), iteration - 1, result.getBinariesCleaned().get(),
                result.getTotalSizeCleaned().get());

        result.setGcEndTime(System.currentTimeMillis());
        result.calcStatus();
        result.printCollectionInfo();
        return result;
    }

    private List<BinaryElement> findPotentialDeletion(@Nullable String startFromSha1)
            throws BinaryDbException {
        try {
            return binariesGcDao.findPotentialDeletion(startFromSha1).stream().map(this::toBinaryElement).toList();
        } catch (SQLException e) {
            log.error("Could not find potential binaries to delete: {}", e.getMessage());
            log.trace("", e);
            throw new BinaryDbException("Could not find potential binaries to delete!", e);
        }
    }


    private BinaryElement toBinaryElement(BinariesGcDao.BinaryEntity bd) {
        return new BinaryElementImpl(bd.sha1(), bd.sha2(), bd.md5(), bd.length());
    }

    private boolean shouldRunNextBatch(int binsToDelete, int prevDeletedBinaries, int currIteration, long endTime,
            GarbageCollectorInfo result) {
        if (currIteration > Math.max(1, gcBinariesToDeleteIterationAmount) &&
                binsToDelete != 0 && prevDeletedBinaries != 0) {
            log.warn("The GC is stopping because the maximum iterations count threshold ({}) was exceeded",
                    gcBinariesToDeleteIterationAmount);
            result.setInterruptedByMaxNumberOfIterations(true);
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime >= endTime && binsToDelete != 0 && prevDeletedBinaries != 0) {
            log.warn("The GC is stopping because the max run time was reached");
            result.setInterruptedByMaxRunTime(true);
        }
        return binsToDelete != 0 && prevDeletedBinaries != 0 &&
                currIteration <= Math.max(1, gcBinariesToDeleteIterationAmount) &&
                currentTime < endTime;
    }


    private void sleepBetweenGcIterations(int iteration) {
        if (iteration <= 1 || gcSleepBetweenIterationsSeconds < 1) {
            return;
        }
        try {
            log.debug("Entering a sleep between GC iterations for {} seconds", gcSleepBetweenIterationsSeconds);
            Thread.sleep(TimeUnit.SECONDS.toMillis(gcSleepBetweenIterationsSeconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("The GC thread was interrupted during the sleep between iterations: {}", e.getMessage());
            log.trace("", e);
            throw new GcOperationException("The GC thread was interrupted during the sleep between iterations", e);
        }
    }

    private Collection<BinaryElement> deleteBinaries(GarbageCollectorInfo result,
            Collection<BinaryElement> binsToDelete) {
        final AtomicInteger totalFailuresCount = result.getFailures();
        final Queue<BinaryElement> deletedBinaries = new ConcurrentLinkedQueue<>();
        List<Callable<Boolean>> deleteTasks = Lists.newArrayList();
        for (final BinaryElement be : binsToDelete) {
            deleteTasks.add(() -> {
                final int failuresCount = totalFailuresCount.get();
                if (failuresCount >= gcFailCountThreshold) {
                    return false;
                }
                log.trace("Candidate for deletion: {}", be);
                try {
                    boolean deleted = gcService.executeBinaryCleaner(be, result);
                    if (deleted) {
                        // Need to collect the binaries delete here and not on the future get for cases the
                        // invokeAll is interrupted
                        deletedBinaries.add(be);
                        return true;
                    }
                } catch (Exception e) {
                    int currentFailuresCount = totalFailuresCount.incrementAndGet();
                    String msg = "Caught Exception, trying to clean %s :".formatted(be.getSha1());
                    ExceptionLogUtils.debugOrWarn(log, msg, e);
                    log.info("Current exceptions count is: {}", currentFailuresCount);
                }
                return false;
            });
        }
        try {
            // waits for all tasks
            executorService.invokeAll(deleteTasks);
        } catch (InterruptedException e) {
            log.error("Failed while trying to invoke delete binaries: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        if (totalFailuresCount.get() >= gcFailCountThreshold) {
            log.error("Stopping binaries deletion. Maximum amount of failures was reached: {}",
                    gcFailCountThreshold);
        }
        return deletedBinaries;
    }
}
