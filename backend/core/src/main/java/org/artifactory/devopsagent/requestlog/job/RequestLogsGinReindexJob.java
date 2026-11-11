package org.artifactory.devopsagent.requestlog.job;

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
 * Weekly {@code REINDEX INDEX CONCURRENTLY} for the GIN FTS indexes on
 * {@code request_logs} and {@code outbound_request_logs}. No-op on non-Postgres deployments.
 * Cluster-singleton: exactly one node reindexes per cluster per week.
 * <p>
 * On PG 14+ a single REINDEX on the parent partitioned index rebuilds all attached
 * partition indexes concurrently without write locks.
 */
@Slf4j
@JobCommand(jobName = JobName.DEVOPS_AGENT_REQUEST_LOG_GIN_REINDEX,
        singleton = true,
        clusterSingleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Weekly REINDEX of request_logs and outbound_request_logs GIN FTS indexes (PG only)")
public class RequestLogsGinReindexJob extends QuartzCommand {

    private static final String INBOUND_INDEX = "request_logs_search_vector_idx";
    private static final String OUTBOUND_INDEX = "outbound_rl_search_vector_idx";

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null || !context.isReady()) {
            log.debug("Skipping {}, server is not ready yet",
                    RequestLogsGinReindexJob.class.getSimpleName());
            return;
        }
        ArtifactoryDbProperties dbProperties = context.beanForType(ArtifactoryDbProperties.class);
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            log.debug("agentic_api.request_logs.gin_reindex.skipped reason=non_pg dbType={}",
                    dbProperties.getDbType());
            return;
        }
        ArtifactoryJdbcHelper jdbcHelper = context.beanForType(ArtifactoryJdbcHelper.class);
        reindex(jdbcHelper, INBOUND_INDEX);
        reindex(jdbcHelper, OUTBOUND_INDEX);
    }

    private static void reindex(ArtifactoryJdbcHelper jdbcHelper, String indexName)
            throws JobExecutionException {
        try {
            log.info("agentic_api.request_logs.gin_reindex.start index={}", indexName);
            long startMs = System.currentTimeMillis();
            jdbcHelper.executeUpdate("REINDEX INDEX CONCURRENTLY " + indexName);
            log.info("agentic_api.request_logs.gin_reindex.complete index={} durationMs={}",
                    indexName, System.currentTimeMillis() - startMs);
        } catch (SQLException ex) {
            log.error("agentic_api.request_logs.gin_reindex.failed index={} reason={}",
                    indexName, ex.toString(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
