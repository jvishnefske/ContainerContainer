package org.artifactory.devopsagent.errorcatalog.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.DbType;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Daily retention sweep for {@code error_catalog} + {@code error_catalog_templates}.
 *
 * <p>Cluster-singleton — exactly one node runs the sweep per cluster, coordinated by Quartz's HA lock.
 * Raw rows older than the retention cutoff are removed via chunked DELETE (uniform across all
 * supported DB engines) and templates are swept <em>after</em> raw cleanup so no surviving raw row
 * can point to a deleted template.</p>
 */
@JobCommand(jobName = JobName.DEVOPS_AGENT_ERROR_CATALOG_RETENTION,
        singleton = true,
        clusterSingleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Daily retention sweep for error_catalog and error_catalog_templates")
@Slf4j
public class ErrorCatalogRetentionJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null || !context.isReady()) {
            log.debug("Skipping {}, server is not ready yet", ErrorCatalogRetentionJob.class.getSimpleName());
            return;
        }
        ErrorCatalogTemplateDao templatesDao = context.beanForType(ErrorCatalogTemplateDao.class);
        ErrorCatalogDao rawDao = context.beanForType(ErrorCatalogDao.class);
        ArtifactoryDbProperties dbProperties = context.beanForType(ArtifactoryDbProperties.class);
        DbType dbType = dbProperties.getDbType();

        long nowMs = ClockUtils.epochMillis();
        long retentionDays = ConstantValues.errorCatalogRetentionDays.getLong();
        long cutoffMs = nowMs - (retentionDays * 86_400_000L);

        log.info("error_catalog retention run starting: dbType={} retentionDays={} cutoffMs={}",
                dbType, retentionDays, cutoffMs);

        try {
            int batchSize = ConstantValues.errorCatalogRetentionDeleteBatchSize.getInt();
            long perBatchSleepMs = ConstantValues.errorCatalogRetentionDeleteBatchSleepMs.getLong();
            int rawDeleted = rawDao.deleteOlderThanChunked(cutoffMs, batchSize, perBatchSleepMs);
            int templatesDeleted = templatesDao.deleteOlderThan(cutoffMs);
            log.info("error_catalog retention run complete: rawDeleted={} templatesDeleted={}",
                    rawDeleted, templatesDeleted);
        } catch (RuntimeException ex) {
            log.error("error_catalog retention run failed: {}", ex.toString(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
