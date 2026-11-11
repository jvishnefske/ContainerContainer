package org.artifactory.storage.db.devopsagent.requestlog.itest.dao;

import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertEquals;

/**
 * PostgreSQL-only retention sweep integration test. Exercises the PG subquery {@code DELETE … LIMIT}
 * path used by {@link org.artifactory.devopsagent.requestlog.job.RequestLogsRetentionJob}.
 *
 * <p>Run with: {@code mvnw test -Dtest=RequestLogDaoRetentionPostgresqlTest
 * -Dartifactory.db.config.name=postgresql-docker-unitests}</p>
 */
public class RequestLogDaoRetentionPostgresqlTest extends RequestLogDaoRetentionTestBase {

    @BeforeMethod
    public void requirePostgresql() {
        if (!dbProperties.isPostgres()) {
            throw new SkipException("RequestLogDaoRetentionPostgresqlTest requires PostgreSQL. "
                    + "Run: mvnw test -Dtest=RequestLogDaoRetentionPostgresqlTest "
                    + "-Dartifactory.db.config.name=postgresql-docker-unitests");
        }
    }

    @Test
    public void testRetentionSweepBothTables() throws SQLException {
        configureOneDayRetentionBatchTen();

        long now = System.currentTimeMillis();
        seedFortyInboundRows(now);
        seedFortyOutboundRows(now);

        runRetentionSweepLikeJob();

        assertEquals(countRows(RequestLogDao.TABLE_NAME), ROWS_INSIDE_RETENTION,
                "Inbound: expected rows within retention window to survive sweep");
        assertEquals(countRows(OutboundRequestLogDao.TABLE_NAME), ROWS_INSIDE_RETENTION,
                "Outbound: expected rows within retention window to survive sweep");
    }
}
