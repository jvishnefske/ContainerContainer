package org.artifactory.sh.cleanup;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.cleanup.full.FullGcCollector;
import org.artifactory.sh.cleanup.minor.MinorGcCollector;
import org.artifactory.sh.cleanup.minor.TrashAndBinariesGCProvider;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.artifactory.storage.fs.service.ConfigsService;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.events.DeletedBinariesEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_GC_ITERATION;

/**
 * @author TamirH
 */
@Slf4j
public class GCServiceImpl implements GCService {

    private final AtomicInteger fallbackGcIteration = new AtomicInteger(0);
    private final ArtifactoryBinaryService binaryService;
    private final TrashService trashService;
    private final int skipFullGcCount;
    private final GarbageCollectionDatastoreService garbageCollectionDatastoreService;
    private final ConfigsService configsService;

    private final BinariesGcDao binariesGcDao;
    private final ApplicationEventPublisher eventPublisher;
    private final RepositoryService repoService;
    private final SecurityService securityService;
    private final DbService dbService;
    private final StorageService storageService;
    private final boolean gcCheckBalanceAfterFullGc;
    @Getter
    @Setter
    @VisibleForTesting
    private FullGcCollector fullGcCollector;

    public GCServiceImpl(ArtifactoryBinaryService binaryService,
            TrashService trashService,
            GarbageCollectionDatastoreService garbageCollectionDatastoreService, ConfigsService configsService,
            BinariesGcDao binariesGcDao,
            ApplicationEventPublisher eventPublisher,
            RepositoryService repoService,
            SecurityService securityService, DbService dbService, StorageService storageService) {
        this.binaryService = binaryService;
        this.trashService = trashService;
        this.garbageCollectionDatastoreService = garbageCollectionDatastoreService;
        this.configsService = configsService;
        this.eventPublisher = eventPublisher;
        this.repoService = repoService;
        this.securityService = securityService;
        this.dbService = dbService;
        this.storageService = storageService;
        this.skipFullGcCount = ConstantValues.gcSkipFullGcBetweenMinorIterations.getInt();
        this.binariesGcDao = binariesGcDao;
        this.gcCheckBalanceAfterFullGc = ConstantValues.gcCheckBalanceAfterFullGc.getBoolean();
    }

    @PostConstruct
    public void postConstruct() {
        this.fullGcCollector = new FullGcCollector(this, storageService, binariesGcDao);
    }

    @Override
    public GarbageCollectorInfo startGarbageCollect() {
        log.info("Triggering Garbage Collection");
        IterationResult iteration = getGCIteration();
        log.info("Running {} iteration of GC", iteration.value);
        GarbageCollectorInfo garbageCollectorInfo;
        try {
            if (this.trashService.isTrashcanEnabled()) {
                garbageCollectorInfo = executeTrashStrategy(iteration.isFullGc());
            } else {
                garbageCollectorInfo = fullGcCollector.start();
            }
        } finally {
            updateConfigIteration(iteration);
        }
        saveReportToMetric(garbageCollectorInfo);
        return garbageCollectorInfo;
    }

    public void runShardingBalancerTask() {
        if (!gcCheckBalanceAfterFullGc) {
            return;
        }
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        storageService.callManualShardingBalancer(statusHolder);
        if (statusHolder.isError()) {
            String logMessage = "Sharding balancer task failed to run from gc operation: ";
            if (statusHolder.getException() != null) {
                ExceptionLogUtils.debugOrWarn(log, logMessage,
                        statusHolder.getException());
            } else {
                ExceptionLogUtils.debugOrWarn(log, logMessage,
                        new IllegalStateException(statusHolder.getLastError().getMessage()));
            }
        }
    }

    @Override
    @VisibleForTesting
    public GarbageCollectorInfo forceFull() {
        GarbageCollectorInfo garbageCollectorInfo;
        if (this.trashService.isTrashcanEnabled()) {
            garbageCollectorInfo = executeTrashStrategy(true);
        } else {
            garbageCollectorInfo = fullGcCollector.start();
        }
        saveReportToMetric(garbageCollectorInfo);
        return garbageCollectorInfo;
    }

    private void saveReportToMetric(GarbageCollectorInfo garbageCollectorInfo) {
        if (garbageCollectorInfo != null) {
            garbageCollectionDatastoreService.saveLastGCRunInfo(garbageCollectorInfo);
        } else {
            log.info("GC finished without report");
        }
    }

    private GarbageCollectorInfo executeTrashStrategy(boolean shouldExecuteFull) {
        if (shouldExecuteFull) {
            GarbageCollectorInfo minorGCReport = this.runTrashAndBinariesGarbageCollect();
            //Although a new report might override this one very soon, we do our best effort to report this minor GC
            //before the fullGC (if the fullGC will take more than a minute to run, we made it!)
            saveReportToMetric(minorGCReport);
            return fullGcCollector.start();
        } else {
            return runTrashAndBinariesGarbageCollect();
        }
    }

    @VisibleForTesting
    protected GarbageCollectorInfo runTrashAndBinariesGarbageCollect() {
        return new MinorGcCollector(new TrashAndBinariesGCProvider(repoService, trashService, this),
                securityService, this,
                dbService.getDatabaseType()).start();
    }

    private IterationResult getGCIteration() {
        int nextIteration;
        boolean success;
        try {
            String persistedGcIteration = configsService.getConfig(ARTIFACTORY_GC_ITERATION);
            int prevIteration = persistedGcIteration != null ? Integer.parseInt(persistedGcIteration) : 0;
            nextIteration = prevIteration + 1 >= skipFullGcCount ? 0 : prevIteration + 1;
            success = true;
        } catch (Exception e) {
            log.warn("Couldn't read gc iteration from configs, continue with previous state: {}", e.getMessage());
            log.debug("", e);
            nextIteration = fallbackGcIteration.updateAndGet(n -> (n + 1 >= skipFullGcCount) ? 0 : n + 1);
            success = false;
        }
        fallbackGcIteration.set(nextIteration);
        return new IterationResult(success, nextIteration, System.currentTimeMillis());
    }

    private void updateConfigIteration(IterationResult iteration) {
        try {
            //do not update config if it wasn't retrieved from the database configs
            if (iteration.isSuccessful) {
                configsService.addOrUpdateConfig(ARTIFACTORY_GC_ITERATION, iteration.value + "", iteration.startTime);
            }
        } catch (Exception e) {
            log.warn("GC iteration config wasn't updated: {}", e.getMessage());
            log.debug("", e);
        }
    }

    @Override
    public void deleteBlobInfos(List<String> deletedBinaries) {
        if (deletedBinaries != null && !deletedBinaries.isEmpty()) {
            this.eventPublisher.publishEvent(new DeletedBinariesEvent(this, deletedBinaries));
        }
    }

    @Override
    public boolean executeBinaryCleaner(GCCandidate candidate, GarbageCollectorInfo result) {
        BinaryElementImpl element =
                new BinaryElementImpl(candidate.sha1(), candidate.sha2(), candidate.md5(), candidate.length());
        // Unlike the old code, not going to open transaction from archive entry deletion.
        // The deleteBinary will be protected by transaction
        return executeBinaryCleaner(element, result);
    }

    @Override
    public boolean executeBinaryCleaner(BinaryElement element, GarbageCollectorInfo result) {
        // Unlike the old code, not going to open transaction from archive entry deletion.
        // The deleteBinary will be protected by transaction
        return clean(element, result);
    }

    private boolean clean(BinaryElement bd, GarbageCollectorInfo result) {
        boolean binaryDeleted = false;
        String sha1 = bd.getSha1();
        log.debug("Targeting '{}' for deletion as it not seems to be used", sha1);
        if (binaryService.deleteBinary(sha1)) {
            result.getChecksumsCleaned().incrementAndGet();
            binaryDeleted = true;
            log.trace("Deleted {} binary", sha1);
            result.getBinariesCleaned().incrementAndGet();
            result.getTotalSizeCleaned().addAndGet(bd.getLength());
        } else {
            log.trace("Could not delete binary '{}'", sha1);
        }
        return binaryDeleted;
    }

    private record IterationResult(boolean isSuccessful, int value, long startTime) {
        boolean isFullGc() {
            return value == 0;
        }
    }
}
