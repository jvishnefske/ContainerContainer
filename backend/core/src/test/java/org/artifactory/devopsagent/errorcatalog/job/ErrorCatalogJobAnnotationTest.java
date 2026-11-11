package org.artifactory.devopsagent.errorcatalog.job;

import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the @JobCommand contracts on the three error_catalog jobs. The behavior of each
 * job's {@code onExecute} body is covered by the DAO tests; this guard is a fast-failing trip-wire
 * for the cluster-singleton / per-node / job-name contracts that are non-obvious from the code.
 */
public class ErrorCatalogJobAnnotationTest {

    @Test
    public void testFlushJobIsPerNode() {
        JobCommand annotation = ErrorCatalogFlushJob.class.getAnnotation(JobCommand.class);
        assertNotNull(annotation);
        assertEquals(annotation.jobName(), JobName.DEVOPS_AGENT_ERROR_CATALOG_FLUSH);
        assertTrue(annotation.singleton(),
                "Flush job must be a per-JVM singleton (only one flusher per JVM)");
        assertFalse(annotation.clusterSingleton(),
                "Flush job must NOT be cluster-singleton — each JVM flushes its own in-memory buffer");
    }

    @Test
    public void testRetentionJobIsClusterSingleton() {
        JobCommand annotation = ErrorCatalogRetentionJob.class.getAnnotation(JobCommand.class);
        assertNotNull(annotation);
        assertEquals(annotation.jobName(), JobName.DEVOPS_AGENT_ERROR_CATALOG_RETENTION);
        assertTrue(annotation.clusterSingleton(),
                "Retention sweep must be cluster-singleton — only one node performs the daily drop");
    }

    @Test
    public void testGinReindexJobIsClusterSingleton() {
        JobCommand annotation = ErrorCatalogGinReindexJob.class.getAnnotation(JobCommand.class);
        assertNotNull(annotation);
        assertEquals(annotation.jobName(), JobName.DEVOPS_AGENT_ERROR_CATALOG_GIN_REINDEX);
        assertTrue(annotation.clusterSingleton(),
                "GIN reindex must be cluster-singleton — only one node performs REINDEX CONCURRENTLY");
    }
}
