package org.artifactory.storage.db.devopsagent.requestlog.itest.dao;

import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Cross-DB integration tests for chunked retention DELETE on {@code request_logs} and
 * {@code outbound_request_logs}.
 *
 * <p>PostgreSQL retention-sweep coverage lives in {@link RequestLogDaoRetentionPostgresqlTest}.</p>
 */
public class RequestLogDaoRetentionTest extends RequestLogDaoRetentionTestBase {

    @Test
    public void testChunkedDeleteInbound() throws SQLException {
        long t0 = 100_000L;
        List<RequestLogRow> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(inboundRow(t0 + i));
        }
        inboundDao.insertBatch(rows);

        int deleted = inboundDao.deleteOlderThanChunked(t0 + 20, 10, 0L);
        assertTrue(deleted >= 20, "Expected at least 20 rows deleted, got " + deleted);
        assertTrue(countRows(RequestLogDao.TABLE_NAME) <= 5,
                "Expected at most 5 rows remaining after chunked delete");
    }

    @Test
    public void testChunkedDeleteOutbound() throws SQLException {
        long t0 = 200_000L;
        List<OutboundRequestLogRow> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(outboundRow(t0 + i));
        }
        outboundDao.insertBatch(rows);

        int deleted = outboundDao.deleteOlderThanChunked(t0 + 20, 10, 0L);
        assertTrue(deleted >= 20, "Expected at least 20 rows deleted, got " + deleted);
        assertTrue(countRows(OutboundRequestLogDao.TABLE_NAME) <= 5,
                "Expected at most 5 rows remaining after chunked delete");
    }
}
