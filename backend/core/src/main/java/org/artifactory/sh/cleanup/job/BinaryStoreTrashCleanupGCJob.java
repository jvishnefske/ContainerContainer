package org.artifactory.sh.cleanup.job;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.ScheduleJobEnum;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.storage.DeletedGCCandidate;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.spring.StorageContextHelper;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.artifactory.schedule.StopStrategy.PAUSE;

/**
 * @author Oleksandr Legenkiy
 */
@JobCommand(jobName = JobName.BINARY_STORE_TRASH_CLEANUP_GC,
        singleton = true, clusterSingleton = false, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM,
        allowedAffinity = false,
        description = "Garbage Collector For Binaries Deleted From Trash",
        commandsToStop = {
                @StopCommand(strategy = PAUSE, commandName = ScheduleJobEnum.SHA256_MIGRATION_JOB),
        })
public class BinaryStoreTrashCleanupGCJob extends QuartzCommand {

    private static final Logger log = LoggerFactory.getLogger(BinaryStoreTrashCleanupGCJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("BinaryStoreTrashCleanupGCJob started");
        TrashService trashService = StorageContextHelper.get().beanForType(TrashService.class);
        List<GCCandidate> gcCandidates = trashService.getTrashBinariesMarkedForGC();
        GarbageCollectorInfo gcResult = createAndInitGcResult(gcCandidates);
        GCService gcService = StorageContextHelper.get().beanForType(GCService.class);
        List<DeletedGCCandidate> deletedGCCandidates = runCleanup(gcService, gcCandidates, gcResult);
        finalizeGcResult(gcResult);
        gcResult.printCollectionInfo();
        deleteBlobInfos(gcService, deletedGCCandidates);
        log.debug("BinaryStoreTrashCleanupGCJob finished");
    }

    private List<DeletedGCCandidate> runCleanup(GCService gcService, List<GCCandidate> gcCandidates,
            GarbageCollectorInfo gcResult) {
        List<DeletedGCCandidate> deletedGCCandidates = new ArrayList<>(gcCandidates.size());
        for (GCCandidate gcCandidate : gcCandidates) {
            boolean binaryDeleted = false;
            try {
                binaryDeleted = gcService.executeBinaryCleaner(gcCandidate, gcResult);
            } catch (Exception e) {
                log.error("Unable to execute GC action on GC candidate sha1: '{}'", gcCandidate.sha1());
                log.debug("Exception: ", e);
            }
            if (binaryDeleted) {
                deletedGCCandidates.add(new DeletedGCCandidate(gcCandidate));
            }
        }
        return deletedGCCandidates;
    }

    private GarbageCollectorInfo createAndInitGcResult(List<GCCandidate> gcCandidates) {
        GarbageCollectorInfo gcResult =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_BINARIES_MARKED_FOR_GC.name(),
                        ConstantValues.gcFailCountThreshold.getInt());
        gcResult.setCandidatesForDeletion(gcCandidates.size());
        return gcResult;
    }

    private void finalizeGcResult(GarbageCollectorInfo gcResult) {
        gcResult.setGcEndTime(System.currentTimeMillis());
        gcResult.calcStatus();
    }

    private void deleteBlobInfos(GCService gcService, List<DeletedGCCandidate> deletedGCCandidates) {
        List<String> deletedBinariesSha2 = deletedGCCandidates.stream()
                .map(DeletedGCCandidate::getDeletedCandidate)
                .filter(Objects::nonNull)
                .map(GCCandidate::sha2)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        gcService.deleteBlobInfos(deletedBinariesSha2);
    }
}
