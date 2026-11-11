package org.artifactory.storage.db.devopsagent.requestlog.itest.dao;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for request-log retention integration tests.
 */
abstract class RequestLogDaoRetentionTestBase extends DbBaseTest {

    static final long ONE_DAY_MS = 86_400_000L;
    static final long TWO_DAYS_MS = 2L * ONE_DAY_MS;
    static final long ONE_MINUTE_MS = 60_000L;
    static final int ROWS_OUTSIDE_RETENTION = 20;
    static final int ROWS_INSIDE_RETENTION = 20;

    @Autowired
    RequestLogDao inboundDao;

    @Autowired
    OutboundRequestLogDao outboundDao;

    @AfterMethod
    public void tearDownRetentionTables() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM request_logs");
        jdbcHelper.executeUpdate("DELETE FROM outbound_request_logs");
    }

    /**
     * Mirrors {@link org.artifactory.devopsagent.requestlog.job.RequestLogsRetentionJob}
     * retention sweep (cutoff + dual chunked DELETE). Kept here to avoid a storage-db → core test dependency.
     */
    void runRetentionSweepLikeJob() throws SQLException {
        long retentionDays = ConstantValues.devopsAgentRequestLogRetentionDays.getLong();
        int batchSize = ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSize.getInt();
        long perBatchSleepMs = ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSleepMs.getLong();
        long cutoffMs = System.currentTimeMillis() - (retentionDays * ONE_DAY_MS);
        inboundDao.deleteOlderThanChunked(cutoffMs, batchSize, perBatchSleepMs);
        outboundDao.deleteOlderThanChunked(cutoffMs, batchSize, perBatchSleepMs);
    }

    void seedFortyInboundRows(long now) throws SQLException {
        List<RequestLogRow> rows = new ArrayList<>();
        for (int i = 0; i < ROWS_OUTSIDE_RETENTION; i++) {
            rows.add(inboundRow(now - TWO_DAYS_MS - i));
        }
        for (int i = 0; i < ROWS_INSIDE_RETENTION; i++) {
            rows.add(inboundRow(now - ONE_MINUTE_MS - i));
        }
        inboundDao.insertBatch(rows);
    }

    void seedFortyOutboundRows(long now) throws SQLException {
        List<OutboundRequestLogRow> rows = new ArrayList<>();
        for (int i = 0; i < ROWS_OUTSIDE_RETENTION; i++) {
            rows.add(outboundRow(now - TWO_DAYS_MS - i));
        }
        for (int i = 0; i < ROWS_INSIDE_RETENTION; i++) {
            rows.add(outboundRow(now - ONE_MINUTE_MS - i));
        }
        outboundDao.insertBatch(rows);
    }

    void configureOneDayRetentionBatchTen() {
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.devopsAgentRequestLogRetentionDays, "1");
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSize, "10");
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.devopsAgentRequestLogRetentionDeleteBatchSleepMs, "0");
    }

    static RequestLogRow inboundRow(long requestTime) {
        return RequestLogRow.builder()
                .id(0L)
                .requestTime(requestTime)
                .traceId("trace-in")
                .username("user")
                .httpMethod("GET")
                .requestPath("/api/inbound")
                .statusCode(200)
                .durationMs(10L)
                .build();
    }

    static OutboundRequestLogRow outboundRow(long requestTime) {
        return OutboundRequestLogRow.builder()
                .id(0L)
                .requestTime(requestTime)
                .traceId("trace-out")
                .repoKey("libs-release")
                .httpMethod("GET")
                .requestUrl("https://remote.example/artifact")
                .statusCode(200)
                .durationMs(15L)
                .build();
    }

    long countRows(String table) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
