package org.artifactory.event;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.jfrog.common.ClockUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;
import java.util.Optional;

/**
 * @author Tomer Slutsky
 */
@JobCommand(jobName = JobName.TMP_EVENT_LOG_TRUNCATE, singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Truncate old events from node_events_tmp partitions")
@Slf4j
public class TmpEventLogTruncateJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet",
                    TmpEventLogTruncateJob.class.getName());
            return;
        }
        Optional<TmpEventPartitions> partition = TmpEventPartitions.getPartitionsToTruncate(getCurrentHour());
        partition.ifPresent(this::truncateTmpPartition);
    }

    private void truncateTmpPartition(TmpEventPartitions partition) {
        partition.getPartitionsToTruncate().forEach(this::actOnPartition);
    }

    private void actOnPartition(String partition) {
        try {
            EventsDao eventsDao = ContextHelper.get().beanForType(EventsDao.class);
            if (eventsDao.hasPartition(partition)) {
                log.info("Truncating partition: {}", partition);
                eventsDao.truncateTmpPartition(partition);
            }
        } catch (SQLException e) {
            log.error("Failed to perform node_events_tmp partition truncate due to: ", e);
        }
    }

    @VisibleForTesting
    int getCurrentHour() {
        return (int) (ClockUtils.epochMillis() % (24 * 3600000) / 3600000);
    }
}
