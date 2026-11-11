package org.artifactory.sh.cleanup.minor;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.cleanup.GCFunction;
import org.artifactory.sh.cleanup.GCProvider;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.sh.cleanup.GcBinariesCleaner;
import org.artifactory.sh.cleanup.events.EventsGcConfiguration;
import org.artifactory.storage.GCCandidate;
import org.jfrog.common.ExceptionLogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author dudim
 */
public class TrashAndBinariesGCProvider implements GCProvider {

    private static final Logger log = LoggerFactory.getLogger(TrashAndBinariesGCProvider.class);

    private final RepositoryService repoService;
    private final TrashService trashService;
    private final GcBinariesCleaner gcService;
    private final boolean useEventsGC;

    private final AtomicInteger errorCount = new AtomicInteger(0);
    private boolean isEnabled = true;

    public TrashAndBinariesGCProvider(RepositoryService repoService, TrashService trashService,
            GcBinariesCleaner gcService) {
        this.trashService = trashService;
        this.repoService = repoService;
        this.gcService = gcService;
        this.useEventsGC = EventsGcConfiguration.useEventGCinMinorGC();
    }

    @Override
    public List<GCCandidate> getBatch() {
        if (isEnabled) {
            List<GCCandidate> trashCandidates = trashService.getGCCandidatesFromTrash();
            log.debug("Found {} GC candidates from Trash", trashCandidates.size());
            return new ArrayList<>(trashCandidates);
        }
        return List.of();
    }

    @VisibleForTesting
    public GCFunction actionProvider() {
        return (gcCandidate, result) -> {
            trashService.undeployFromTrash(gcCandidate);
            if (this.useEventsGC) {
                result.getEventsGcCandidates().incrementAndGet();
            }
            if (!useEventsGC && !repoService.existsBySha1(gcCandidate.sha1())) {
                return gcService.executeBinaryCleaner(gcCandidate, result);
            }
            // item was not garbage collected as another reference exists to this node or events gc is on
            return false;
        };
    }

    public GCFunction getAction() {
        return (gcCandidate, result) -> {
            try {
                return this.actionProvider().accept(gcCandidate, result);
            } catch (Exception e) {
                ExceptionLogUtils.debugOrWarn(log, "Error occurred while performing cleanup action", e);
                if (errorCount.incrementAndGet() >= ConstantValues.gcFailCountThreshold.getInt()) {
                    log.error("Provider reached maximum error count: {}, disabling provider", errorCount);
                    isEnabled = false;
                }
            }
            result.setFailures(errorCount);
            return false;
        };
    }

    @Override
    public boolean shouldReportAfterBatch() {
        return true;
    }

    @Override
    public String getName() {
        return GarbageCollectorStrategy.TRASH_AND_BINARIES.name();
    }
}
