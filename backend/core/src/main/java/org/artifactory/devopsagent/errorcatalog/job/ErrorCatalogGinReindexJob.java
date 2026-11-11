package org.artifactory.devopsagent.errorcatalog.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;

/**
 * Weekly GIN reindex for {@code error_catalog_templates_msg_fts_idx}.
 *
 * <p>PG GIN under UPSERT-heavy load accumulates bloat in the pending list and dead tuples in the
 * tree. {@code REINDEX INDEX CONCURRENTLY} rebuilds without locking writers. On non-PG DBs this
 * job is a no-op.</p>
 */
@JobCommand(jobName = JobName.DEVOPS_AGENT_ERROR_CATALOG_GIN_REINDEX,
        singleton = true,
        clusterSingleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Weekly REINDEX of the error_catalog_templates GIN FTS index (PG only)")
@Slf4j
public class ErrorCatalogGinReindexJob extends QuartzCommand {

    private static final String INDEX_NAME = "error_catalog_templates_msg_fts_idx";

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null || !context.isReady()) {
            log.debug("Skipping {}, server is not ready yet", ErrorCatalogGinReindexJob.class.getSimpleName());
            return;
        }
        ArtifactoryDbProperties dbProperties = context.beanForType(ArtifactoryDbProperties.class);
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            log.debug("Skipping GIN reindex on {}", dbProperties.getDbType());
            return;
        }
        ArtifactoryJdbcHelper jdbcHelper = context.beanForType(ArtifactoryJdbcHelper.class);
        try {
            log.info("Starting REINDEX INDEX CONCURRENTLY {}", INDEX_NAME);
            long startMs = System.currentTimeMillis();
            jdbcHelper.executeUpdate("REINDEX INDEX CONCURRENTLY " + INDEX_NAME);
            log.info("Finished REINDEX INDEX CONCURRENTLY {} in {} ms", INDEX_NAME,
                    System.currentTimeMillis() - startMs);
        } catch (SQLException ex) {
            log.error("REINDEX of {} failed", INDEX_NAME, ex);
            throw new JobExecutionException(ex);
        }
    }
}
