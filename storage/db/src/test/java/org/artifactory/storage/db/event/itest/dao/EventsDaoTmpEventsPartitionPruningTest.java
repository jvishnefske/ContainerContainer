package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.itest.PostgresOnlyDbTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Regression guard for the {@code DELETE FROM node_events_tmp} partition-pruning fix.
 * <p>
 * Background: PostgreSQL partitions {@code node_events_tmp} by hour-of-day derived from
 * {@code timestamp}. The historical DELETE used by the shift-events flow only filtered by
 * {@code tmp_event_id}, so the planner had to scan all four partitions on every batch — slow
 * under bloat and a major source of DB load.
 * <p>
 * The fix carries the partition-key expression in a row-wise tuple {@code IN} predicate
 * ({@code WHERE (tmp_event_id, timestamp, (timestamp % (24 * 3600000) / 3600000)) IN ((..), (..))}).
 * The legacy (buggy) DELETE shape is exercised here too, as a control, to demonstrate that
 * the missing predicate was indeed the root cause.
 * <p>
 * PostgreSQL only — the partitioning DDL exists only for PostgreSQL (converter v182/v183).
 *
 * @see org.artifactory.storage.db.event.dao.EventsDao#DELETE_TEMP_EVENTS_SQL
 */
@Test
public class EventsDaoTmpEventsPartitionPruningTest extends PostgresOnlyDbTest {

    private static final Logger log = LoggerFactory.getLogger(EventsDaoTmpEventsPartitionPruningTest.class);

    private static final String[] ALL_PARTITIONS = {
            "node_events_tmp_p0_5",
            "node_events_tmp_p6_11",
            "node_events_tmp_p12_17",
            "node_events_tmp_p18_23"
    };

    /** Legacy buggy DELETE shape — kept as a control to demonstrate that no pruning happens without partition key. */
    private static final String DELETE_BY_ID_ONLY =
            "DELETE FROM node_events_tmp WHERE tmp_event_id IN (%s)";

    /**
     * Production-fixed DELETE shape — mirrors
     * {@code EventsDao.DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX}. Each row being deleted
     * contributes a literal {@code (tmp_event_id, timestamp, partition-key expression)} tuple to the
     * {@code IN} list (these are raw {@code EXPLAIN} statements, so values are inlined).
     */
    private static final String DELETE_BY_TUPLE_IN_PREFIX =
            "DELETE FROM node_events_tmp WHERE (tmp_event_id, timestamp, "
                    + "(timestamp % (24 * 3600000) / 3600000)) IN ";

    private static final String INSERT_SQL =
            "INSERT INTO node_events_tmp (tmp_event_id, timestamp, event_type, path) VALUES (?, ?, ?, ?)";

    private long dayStartUtcMillis;

    /**
     * The schema created by {@link DbBaseTest#springTestContextPrepareTestInstance()} uses the
     * <em>base</em> {@code postgresql.sql} DDL, where {@code node_events_tmp} is a plain (non-partitioned)
     * table. Production's partitioned layout is produced by the v182 converter (creates the partitions)
     * and then v273 (drops the redundant {@code *_idx_time_id} unique indexes and widens each partition
     * PK to {@code (timestamp, tmp_event_id)}). Apply both once per class so all assertions exercise the
     * same schema layout that production uses.
     */
    @BeforeClass
    public void applyPartitioningConverters() throws SQLException {
        if (!isPostgresqlDb()) {
            return;
        }
        applyConverter(ArtifactoryDBVersion.v182);
        applyConverter(ArtifactoryDBVersion.v273);
    }

    private void applyConverter(ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), DbType.POSTGRESQL, version)) {
            version.converter().convert(context);
        } catch (Exception e) {
            throw new SQLException("Failed to apply " + version.version() + " converter on node_events_tmp", e);
        }
    }

    @BeforeMethod
    public void setup() throws SQLException {
        if (!isPostgresqlDb()) {
            return;
        }
        jdbcHelper.executeUpdate("DELETE FROM node_events_tmp");
        dayStartUtcMillis = (System.currentTimeMillis() / 86_400_000L) * 86_400_000L;
    }

    @AfterMethod
    public void cleanup() throws SQLException {
        if (isPostgresqlDb()) {
            jdbcHelper.executeUpdate("DELETE FROM node_events_tmp");
        }
    }

    /**
     * Main regression guard for the fix: the production DELETE shape
     * ({@code WHERE (tmp_event_id, timestamp, (timestamp % (24 * 3600000) / 3600000)) IN ((..), (..))} — see
     * {@link org.artifactory.storage.db.event.dao.EventsDao#DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX})
     * must let the planner prune down to exactly the partition holding the target rows.
     * <p>
     * Here both deleted rows are in hour 2 (partition {@code p0_5}), so the plan must mention
     * only that partition. A future regression that drops the partition-key expression from the
     * tuple would cause this assertion to fail with {@code size = 4}.
     */
    @Test
    public void testFixedDeleteByTupleInPrunesToSinglePartition() throws SQLException {
        long idA = insertEventInPartitionHour(2);
        long idB = insertEventInPartitionHour(2);
        insertEventInPartitionHour(8);
        insertEventInPartitionHour(14);
        insertEventInPartitionHour(20);

        long tsHour2 = partitionHourTimestamp(2);
        String plan = explain(DELETE_BY_TUPLE_IN_PREFIX
                + "(" + tuple(idA, tsHour2) + ", " + tuple(idB, tsHour2) + ")");

        List<String> touched = partitionsMentionedIn(plan);

        assertEquals(touched.size(), 1,
                "Expected planner to prune to a single partition with the fixed DELETE shape. Plan was:\n" + plan);
        assertEquals(touched.get(0), "node_events_tmp_p0_5",
                "Expected only the p0_5 partition to be touched. Plan was:\n" + plan);
    }

    /**
     * Control: documents <em>why</em> the fix is needed. Without the partition-key predicate the
     * planner cannot prune partitions and ends up visiting all four — exactly the production
     * symptom. Kept green so that if someone ever drops the partition-key expression from the
     * tuple {@code IN} predicate in {@link org.artifactory.storage.db.event.dao.EventsDao}, the
     * failure of {@link #testFixedDeleteByTupleInPrunesToSinglePartition()} is easier to interpret.
     */
    @Test
    public void testLegacyDeleteByIdAloneCannotBePruned() throws SQLException {
        long idP0  = insertEventInPartitionHour(2);
        long idP6  = insertEventInPartitionHour(8);
        long idP12 = insertEventInPartitionHour(14);
        long idP18 = insertEventInPartitionHour(20);

        String plan = explain(String.format(DELETE_BY_ID_ONLY,
                ids(idP0, idP6, idP12, idP18)));

        List<String> touched = partitionsMentionedIn(plan);

        assertEquals(touched.size(), 4,
                "Without a partition-key predicate the planner cannot prune; all four partitions should be scanned. "
                        + "Plan was:\n" + plan);
        for (String partition : ALL_PARTITIONS) {
            assertTrue(touched.contains(partition),
                    "Expected partition " + partition + " to appear in plan. Plan was:\n" + plan);
        }
    }

    private long insertEventInPartitionHour(int hour) throws SQLException {
        long timestamp = partitionHourTimestamp(hour);
        long tmpEventId = dbService.nextId();
        jdbcHelper.executeUpdate(INSERT_SQL,
                tmpEventId, timestamp, 1, "repo1/test/path-" + hour);
        return tmpEventId;
    }

    private long partitionHourTimestamp(int hour) {
        return dayStartUtcMillis + ((long) hour) * 3_600_000L;
    }

    private static String ids(long... ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ids[i]);
        }
        return sb.toString();
    }

    /** Builds a single {@code (tmp_event_id, timestamp, partition-key expression)} literal tuple. */
    private static String tuple(long id, long timestamp) {
        return "(" + id + ", " + timestamp + ", (" + timestamp + " % (24 * 3600000) / 3600000))";
    }

    private String explain(String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (ResultSet rs = jdbcHelper.executeSelect("EXPLAIN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
        }
        String planText = plan.toString();
        log.info("EXPLAIN for SQL:\n{}\nPlan:\n{}", sql, planText);
        return planText;
    }

    private static List<String> partitionsMentionedIn(String plan) {
        List<String> found = new ArrayList<>();
        for (String partition : ALL_PARTITIONS) {
            if (plan.contains(partition)) {
                found.add(partition);
            }
        }
        return found;
    }
}
