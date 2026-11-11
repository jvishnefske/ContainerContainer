package org.artifactory.traffic.summary;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * This job collects aggregated traffic summary data and saves to database.</br>
 * Frequency: ConstantValues.trafficSummaryCollectionJobCron</br>
 * Configured by: TrafficSummaryServiceImpl.setupAggregationJob
 *
 * @author kunalm
 */
@Slf4j
@JobCommand(jobName = JobName.TRAFFIC_SUMMARY_COLLECTION,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Collects aggregated traffic summary data and saves to database")
public class TrafficSummaryCollectionJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.debug("Starting to collect traffic summary data");
            ContextHelper.get().beanForType(TrafficSummaryService.class).saveAggregatedTrafficSnapshot();
            log.info("Aggregated traffic summary data has been collected successfully");
        } catch (Exception e) {
            log.warn("Aggregated traffic summary data could not be collected", e);
        }
    }
}
