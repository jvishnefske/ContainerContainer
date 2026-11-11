package org.artifactory.devopsagent.errorcatalog.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogEventBuffer;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogLogbackAppender;
import org.artifactory.devopsagent.errorcatalog.job.ErrorCatalogFlushJob;
import org.artifactory.devopsagent.errorcatalog.job.ErrorCatalogGinReindexJob;
import org.artifactory.devopsagent.errorcatalog.job.ErrorCatalogRetentionJob;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.jfrog.storage.DbType;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Wires the error_catalog pipeline at application startup:
 * <ul>
 *   <li>Installs the in-memory buffer into the static {@link ErrorCatalogLogbackAppender} bridge.</li>
 *   <li>Schedules the three Quartz jobs: flush (every 5s), retention (daily), GIN reindex (weekly PG-only).</li>
 * </ul>
 *
 * <p>The entire feature is gated by {@code devops-agent.troubleshooting.enabled} (default {@code false}).
 * When disabled, the buffer is not installed and no jobs are scheduled.</p>
 */
@Service
@Slf4j
public class ErrorCatalogService {

    private final TaskService taskService;
    private final ErrorCatalogEventBuffer buffer;
    private final ArtifactoryDbProperties dbProperties;

    @Autowired
    public ErrorCatalogService(TaskService taskService,
            ErrorCatalogEventBuffer buffer,
            ArtifactoryDbProperties dbProperties) {
        this.taskService = taskService;
        this.buffer = buffer;
        this.dbProperties = dbProperties;
    }

    @PostConstruct
    void initialize() {
        if (!ConstantValues.devopsTroubleshootingEnabled.getBoolean()) {
            log.debug("Devops troubleshooting feature is disabled" +
                    "- skipping error_catalog buffer install and job scheduling");
            return;
        }

        // 1. Wire the static appender bridge to the bean-managed buffer.
        ErrorCatalogLogbackAppender.installBuffer(buffer);

        // 2. Schedule jobs (flush, retention, GIN reindex). Idempotent per Quartz HA wiring.
        scheduleJob(ErrorCatalogFlushJob.class,
                ConstantValues.errorCatalogFlushCron.getString(), "error_catalog flush");
        scheduleJob(ErrorCatalogRetentionJob.class,
                ConstantValues.errorCatalogRetentionCron.getString(), "error_catalog retention");
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            scheduleJob(ErrorCatalogGinReindexJob.class,
                    ConstantValues.errorCatalogGinReindexCron.getString(), "error_catalog GIN reindex");
        }
    }

    private void scheduleJob(Class<? extends QuartzCommand> taskClass,
            String cron, String description) {
        if (cron == null || cron.isBlank()) {
            log.info("Skipping schedule for {} — cron is blank", description);
            return;
        }
        if (!CronExpression.isValidExpression(cron)) {
            log.warn("Skipping schedule for {} — invalid cron '{}'", description, cron);
            return;
        }
        try {
            Task task = TaskUtils.createCronTask(taskClass, cron);
            taskService.startTask(task, false);
            log.info("Scheduled {} with cron '{}'", description, cron);
        } catch (RuntimeException ex) {
            log.error("Failed to schedule {} with cron '{}': {}", description, cron, ex.toString(), ex);
        }
    }
}
