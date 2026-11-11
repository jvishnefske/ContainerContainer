package org.artifactory.devopsagent.requestlog;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.devopsagent.requestlog.job.RequestLogFlushJob;
import org.artifactory.devopsagent.requestlog.job.RequestLogsGinReindexJob;
import org.artifactory.devopsagent.requestlog.job.RequestLogsRetentionJob;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.jfrog.storage.DbType;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.springframework.stereotype.Component;

/**
 * Registers the request-log flush, retention, and GIN-reindex tasks on context startup,
 * gated on the feature flag via {@link RequestLogService#isFeatureEnabled()}.
 */
@Slf4j
@Component
public class RequestLogFlushScheduler implements ContextCreationListener {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final TaskService taskService;
    private final RequestLogService requestLogService;
    private final ArtifactoryDbProperties dbProperties;

    public RequestLogFlushScheduler(TaskService taskService, RequestLogService requestLogService,
            ArtifactoryDbProperties dbProperties) {
        this.taskService = taskService;
        this.requestLogService = requestLogService;
        this.dbProperties = dbProperties;
    }

    @Override
    public void onContextCreated() {
        if (!requestLogService.isFeatureEnabled()) {
            log.debug("agentic_api.request_logs.flush_scheduler.skipped reason=feature_disabled");
            return;
        }
        long flushIntervalMs = ConstantValues.devopsAgentRequestLogFlushIntervalMs.getLong();
        Task flushTask = TaskUtils.createRepeatingTask(RequestLogFlushJob.class, flushIntervalMs, flushIntervalMs);
        taskService.startTask(flushTask, false);
        log.info("agentic_api.request_logs.flush_scheduler.started interval_ms={}", flushIntervalMs);

        Task retentionTask = TaskUtils.createRepeatingTask(RequestLogsRetentionJob.class, DAY_MS, DAY_MS);
        taskService.startTask(retentionTask, false);
        log.info("agentic_api.request_logs.retention_scheduler.started interval_ms={}", DAY_MS);

        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            long reindexIntervalMs = ConstantValues.devopsAgentRequestLogGinReindexIntervalMs.getLong();
            Task reindexTask = TaskUtils.createRepeatingTask(
                    RequestLogsGinReindexJob.class, reindexIntervalMs, reindexIntervalMs);
            taskService.startTask(reindexTask, false);
            log.info("agentic_api.request_logs.gin_reindex_scheduler.started interval_ms={}", reindexIntervalMs);
        }
    }
}
