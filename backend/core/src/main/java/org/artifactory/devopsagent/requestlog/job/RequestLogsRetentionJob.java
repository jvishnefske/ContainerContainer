package org.artifactory.devopsagent.requestlog.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;

/**
 * Daily retention sweep that removes rows older than
 * {@code devops-agent.request.log.retention.days} from both {@code request_logs} and
 * {@code outbound_request_logs}. Uses chunked DELETE to avoid long-running statements.
 * Cluster-singleton: exactly one node sweeps per cluster per day.
 */
@Slf4j
@JobCommand(jobName = JobName.DEVOPS_AGENT_REQUEST_LOG_RETENTION,
        singleton = true,
        clusterSingleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Daily retention sweep for request_logs and outbound_request_logs")
public class RequestLogsRetentionJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null || !context.isReady()) {
            log.debug("Skipping {}, server is not ready yet",
                    RequestLogsRetentionJob.class.getSimpleName());
            return;
        }
        RequestLogDao inboundDao = context.beanForType(RequestLogDao.class);
        OutboundRequestLogDao outboundDao = context.beanForType(OutboundRequestLogDao.class);

        long retentionDays = ConstantValues.devopsAgentRequestLogRetentionDays.getLong();
        int batchSize = ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSize.getInt();
        long perBatchSleepMs = ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSleepMs.getLong();
        long cutoffMs = System.currentTimeMillis() - (retentionDays * 86_400_000L);

        log.info("agentic_api.request_logs.retention.start retentionDays={} cutoffMs={}",
                retentionDays, cutoffMs);
        try {
            int inboundDeleted = inboundDao.deleteOlderThanChunked(cutoffMs, batchSize, perBatchSleepMs);
            int outboundDeleted = outboundDao.deleteOlderThanChunked(cutoffMs, batchSize, perBatchSleepMs);
            log.info("agentic_api.request_logs.retention.complete inboundDeleted={} outboundDeleted={}",
                    inboundDeleted, outboundDeleted);
        } catch (SQLException ex) {
            log.error("agentic_api.request_logs.retention.failed reason={}", ex.toString(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
