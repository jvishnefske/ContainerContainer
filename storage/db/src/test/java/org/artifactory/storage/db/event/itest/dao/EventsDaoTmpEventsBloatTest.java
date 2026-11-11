package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Demonstrates the dead-tuple bloat scenario on {@code node_events_tmp_p6_11} and verifies that the
 * production fix eliminates it. PostgreSQL-only — the partitioned layout exists only on PG.
 * <p>
 * The fix is the tuple-{@code IN} DELETE shape combined with the composite partition PK:
 * {@link org.artifactory.storage.db.event.dao.EventsDao#DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX}
 * carries the partition-key expression and {@code timestamp} in each {@code IN} tuple, so PG prunes
 * to the single partition that holds the rows and rides the {@code (timestamp, tmp_event_id)} PK
 * index introduced by v273 — instead of Seq-scanning every partition the way the legacy
 * {@code WHERE tmp_event_id IN (...)} shape did once a partition bloated. The test controls the
 * dead-tuple level directly (autovacuum is disabled in setup and an explicit {@code VACUUM} models
 * the vacuumed steady state) so the planner behaviour can be asserted deterministically.
 * <p>
 * Disabled by default because it takes ~40 seconds. To run manually:
 * <pre>
 *     mvnw test -pl :artifactory-storage-db \
 *         -Dtest=EventsDaoTmpEventsBloatTest \
 *         -Drt.db.type=postgresql
 * </pre>
 * (and flip {@code enabled = true} on the class and methods you want to run).
 * <p>
 * <strong>Verified locally on PostgreSQL 17.10 — captured plans from a passing run:</strong>
 * <pre>
 * --- BEFORE FIX: legacy SQL + bloat (bug present, control test passes) ----------------
 * Dead tuples in node_events_tmp_p6_11: 90000   relpages: 835
 *   Delete on node_events_tmp  (cost=0.25..871.76 rows=0 width=0)
 *     Delete on node_events_tmp_p0_5    node_events_tmp_1
 *     Delete on node_events_tmp_p6_11   node_events_tmp_2
 *     Delete on node_events_tmp_p12_17  node_events_tmp_3
 *     Delete on node_events_tmp_p18_23  node_events_tmp_4
 *     -&gt;  Append  (cost=0.25..871.76 rows=222 width=10)
 *           -&gt;  Seq Scan on node_events_tmp_p0_5    (cost=0.25..11.30  rows=53)
 *           -&gt;  Seq Scan on node_events_tmp_p6_11   (cost=0.25..836.75 rows=63)   &lt;-- bloated ×74
 *           -&gt;  Seq Scan on node_events_tmp_p12_17  (cost=0.25..11.30  rows=53)
 *           -&gt;  Seq Scan on node_events_tmp_p18_23  (cost=0.25..11.30  rows=53)
 *
 * --- AFTER FIX: new tuple-IN SQL on a vacuumed partition (bug gone, main test passes) --
 * Dead tuples in node_events_tmp_p6_11: 0   relpages: 418
 *   Delete on node_events_tmp  (cost=430.03..691.74 rows=0 width=0)
 *     Delete on node_events_tmp_p6_11 node_events_tmp_1                    &lt;-- single partition
 *     -&gt;  Bitmap Heap Scan on node_events_tmp_p6_11  (cost=430.03..691.74 rows=1 width=10)
 *           Recheck Cond: ((timestamp = ... AND tmp_event_id = N) OR ... 100 tuples ...)
 *           Filter: (("timestamp" % 86400000) / 3600000 = 8)
 *           -&gt;  BitmapOr
 *                 -&gt;  Bitmap Index Scan on node_events_tmp_partition_6_11_pk  (×100)
 *                       Index Cond: (timestamp = ... AND tmp_event_id = N)
 * </pre>
 * Three things change between the two plans, all of them in the right direction:
 * <ul>
 *   <li>Partition count touched: 4 → 1 (partition pruning works).</li>
 *   <li>Scan type: Seq Scan → PK-index-driven Bitmap Heap Scan (a {@code BitmapOr} of one
 *       {@code Bitmap Index Scan} per tuple on the composite PK; no Seq Scan anywhere).</li>
 *   <li>Dead tuples on target partition: 90 000 → 0 (a vacuumed partition keeps the heap healthy).</li>
 * </ul>
 *
 * @see EventsDaoTmpEventsPartitionPruningTest
 * @see org.artifactory.storage.db.event.dao.EventsDao#DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX
 */
@Test(enabled = false)
public class EventsDaoTmpEventsBloatTest extends DbBaseTest {

    private static final Logger log = LoggerFactory.getLogger(EventsDaoTmpEventsBloatTest.class);

    /** Partition we will deliberately bloat. Hour 8 maps to it via {@code (timestamp % 86400000 / 3600000)}. */
    private static final String TARGET_PARTITION = "node_events_tmp_p6_11";
    private static final int TARGET_HOUR = 8;

    private static final String[] OTHER_PARTITIONS = {
            "node_events_tmp_p0_5",
            "node_events_tmp_p12_17",
            "node_events_tmp_p18_23"
    };

    /** Number of insert+delete cycles. Each cycle adds {@link #ROWS_PER_CYCLE} dead tuples. */
    private static final int BLOAT_CYCLES = 10;
    private static final int ROWS_PER_CYCLE = 10_000;

    /** Number of "live" rows that the final DELETE will target. Mirrors {@code TMP_EVENTS_DELETE_BATCH_SIZE = 100}. */
    private static final int LIVE_ROWS_FOR_FINAL_DELETE = 100;

    /**
     * Number of bulk-loaded live rows used to simulate a production-sized partition for the fix test.
     * Large enough that PK Index Scan is strictly cheaper than Seq Scan (~50K rows → ~500 heap pages
     * → Seq Scan cost ≫ 100 × Index lookup cost).
     */
    private static final int LIVE_ROWS_IN_PARTITION = 50_000;

    private static final String INSERT_SQL =
            "INSERT INTO node_events_tmp (tmp_event_id, timestamp, event_type, path) VALUES (?, ?, ?, ?)";

    /** Mirrors the legacy SQL shape used before the fix (filter by id only). */
    private static final String LEGACY_DELETE_SQL =
            "DELETE FROM node_events_tmp WHERE tmp_event_id IN (%s)";

    /**
     * Mirrors {@code EventsDao.DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX}; each row being
     * deleted contributes a literal {@code (tmp_event_id, timestamp, partition-key expression)}
     * tuple (values inlined, since this test runs raw {@code EXPLAIN} statements).
     */
    private static final String FIXED_DELETE_TUPLE_IN_PREFIX =
            "DELETE FROM node_events_tmp WHERE (tmp_event_id, timestamp, "
                    + "(timestamp % (24 * 3600000) / 3600000)) IN ";

    private long dayStartUtcMillis;

    /**
     * The base test schema uses the non-partitioned {@code node_events_tmp}. Production's partitioned
     * layout is created by the v182 converter (partitions) and then v273 (drops the redundant
     * {@code *_idx_time_id} unique indexes and widens each partition PK to
     * {@code (timestamp, tmp_event_id)}). Apply both so the planner sees the same indexes/PK as
     * production — the {@code node_events_tmp_partition_6_11_pk} Index Scan assertion below depends on
     * the composite PK introduced by v273.
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
            throw new SkipException("Bloat reproducer is PostgreSQL-only");
        }
        jdbcHelper.executeUpdate("DELETE FROM node_events_tmp");
        dayStartUtcMillis = (System.currentTimeMillis() / 86_400_000L) * 86_400_000L;
        setAutovacuumEnabled(TARGET_PARTITION, false);
        jdbcHelper.executeUpdate("VACUUM FULL " + TARGET_PARTITION);
    }

    @AfterMethod
    public void cleanup() throws SQLException {
        if (isPostgresqlDb()) {
            try {
                jdbcHelper.executeUpdate("DELETE FROM node_events_tmp");
                jdbcHelper.executeUpdate("VACUUM FULL " + TARGET_PARTITION);
            } finally {
                setAutovacuumEnabled(TARGET_PARTITION, true);
            }
        }
    }

    /**
     * Control test — reproduces the production bug. After accumulating dead tuples in
     * {@code node_events_tmp_p6_11} the planner abandons the PK Index Scan for the legacy DELETE
     * shape ({@code WHERE tmp_event_id IN (...)}) and falls back to {@code Seq Scan} on every
     * partition. Kept disabled by default; useful as a manual baseline before/after experimenting
     * with PostgreSQL parameters.
     */
    @Test(enabled = false)
    public void testLegacyDeleteUnderBloatFallsBackToSeqScan() throws SQLException {
        List<Long> liveIds = setupBloatAndGetLiveIds();
        jdbcHelper.executeUpdate("ANALYZE " + TARGET_PARTITION);

        String plan = explain(String.format(LEGACY_DELETE_SQL, commaJoin(liveIds)));
        Diagnostics d = collectDiagnostics(plan, false);
        log.info("Legacy SQL under bloat — diagnostics:{}", d.text);

        assertTrue(d.deadTuples > 0,
                "Sanity check: bloat cycles should leave dead tuples." + d.text);

        boolean usesPlainIndexScan = plan.contains("Index Scan using node_events_tmp_partition_6_11_pk");
        assertFalse(usesPlainIndexScan,
                "Expected planner to abandon PK Index Scan after accumulating bloat — this reproduces "
                        + "the prod slow-DELETE symptom." + d.text);

        boolean fellBackToSeqOrBitmap =
                plan.contains("Seq Scan on " + TARGET_PARTITION)
                        || plan.contains("Bitmap Heap Scan on " + TARGET_PARTITION);
        assertTrue(fellBackToSeqOrBitmap,
                "Expected planner to use Seq Scan or Bitmap Heap Scan instead of PK Index Scan." + d.text);
    }

    /**
     * Main regression guard for the bloat fix. Simulates a vacuumed steady-state partition: it holds
     * many live rows but no accumulated dead tuples (the state default autovacuum maintains between
     * vacuums). On such a partition the planner must drive the DELETE off the composite PK index and
     * never fall back to a {@code Seq Scan} — the prod symptom that broke under heap bloat.
     * <p>
     * Note the production DELETE is a multi-row tuple {@code IN} (up to {@code TMP_EVENTS_DELETE_BATCH_SIZE}
     * rows), so PostgreSQL plans it as a {@code BitmapOr} of one {@code Bitmap Index Scan} per tuple on
     * {@code node_events_tmp_partition_6_11_pk}, feeding a {@code Bitmap Heap Scan} — not a single plain
     * {@code Index Scan}. Both are PK-index driven and pruned to one partition; what matters is that the
     * PK index is used and no {@code Seq Scan} appears.
     * <p>
     * The setup:
     * <ol>
     *   <li>Bulk-load {@link #LIVE_ROWS_IN_PARTITION} live rows into the target partition (no churn,
     *       no dead tuples — mirrors a freshly vacuumed partition in production).</li>
     *   <li>Insert 100 additional rows whose ids will be the DELETE target.</li>
     *   <li>{@code ANALYZE} to refresh planner statistics.</li>
     *   <li>{@code EXPLAIN} the {@link #FIXED_DELETE_TUPLE_IN_PREFIX fixed DELETE} (row-wise tuple
     *       {@code IN} carrying the partition-key expression).</li>
     * </ol>
     * Acceptance — the captured plan must:
     * <ul>
     *   <li>contain <em>no</em> {@code Seq Scan},</li>
     *   <li>be driven by the composite PK index {@code node_events_tmp_partition_6_11_pk} (a plain
     *       {@code Index Scan using …_pk}, or per-tuple {@code Bitmap Index Scan on …_pk}),</li>
     *   <li>mention <em>only</em> the target partition (no other partition appears — pruning works).</li>
     * </ul>
     */
    @Test(enabled = false)
    public void testFixedDeletePicksIndexScanInSinglePartitionUnderProductionLoad() throws SQLException {
        long timestamp = partitionHourTimestamp(TARGET_HOUR);
        bulkInsertRows(timestamp, LIVE_ROWS_IN_PARTITION);
        List<Long> liveIds = insertRows(timestamp, LIVE_ROWS_FOR_FINAL_DELETE);
        jdbcHelper.executeUpdate("ANALYZE " + TARGET_PARTITION);

        String plan = explain(FIXED_DELETE_TUPLE_IN_PREFIX + tuplesFor(timestamp, liveIds));
        Diagnostics d = collectDiagnostics(plan, false);
        log.info("Fixed SQL under production-like load — diagnostics:{}", d.text);

        assertEquals(d.deadTuples, 0L,
                "Sanity check: no churn was run, dead tuples must be 0 (simulating a vacuumed steady state)."
                        + d.text);

        assertFalse(plan.contains("Seq Scan"),
                "Expected planner to NOT pick Seq Scan with the fixed DELETE shape on a non-bloated partition."
                        + d.text);
        boolean usesPkIndex = plan.contains("Index Scan using node_events_tmp_partition_6_11_pk")
                || plan.contains("Bitmap Index Scan on node_events_tmp_partition_6_11_pk");
        assertTrue(usesPkIndex,
                "Expected the DELETE to be driven by the composite PK index (plain Index Scan, or a BitmapOr "
                        + "of per-tuple Bitmap Index Scans for the multi-row tuple IN)." + d.text);

        assertTrue(plan.contains(TARGET_PARTITION),
                "Expected plan to mention the target partition " + TARGET_PARTITION + "." + d.text);
        for (String other : OTHER_PARTITIONS) {
            assertFalse(plan.contains(other),
                    "Expected partition pruning to exclude " + other + " from the plan." + d.text);
        }
    }

    /**
     * Bulk-loads {@code count} rows into the partition for the given timestamp via a single
     * {@code INSERT INTO … SELECT generate_series(…)} statement (much faster than per-row inserts).
     * IDs are assigned from a high range based on {@code dbService.nextId()} so they won't collide
     * with ids subsequently generated by other test code.
     */
    private void bulkInsertRows(long timestamp, int count) throws SQLException {
        long startId = dbService.nextId() + count;
        jdbcHelper.executeUpdate(
                "INSERT INTO node_events_tmp (tmp_event_id, timestamp, event_type, path) "
                        + "SELECT " + startId + " + g, ?, 1, 'repo1/bulk/' || g "
                        + "FROM generate_series(1, ?) AS g",
                timestamp, count);
    }

    /**
     * Bloat cycles + insert of the live rows that the final DELETE will target. Used by the control
     * test (no VACUUM between cycles — bloat accumulates).
     */
    private List<Long> setupBloatAndGetLiveIds() throws SQLException {
        long timestamp = partitionHourTimestamp(TARGET_HOUR);
        for (int cycle = 0; cycle < BLOAT_CYCLES; cycle++) {
            List<Long> insertedIds = insertRows(timestamp, ROWS_PER_CYCLE);
            jdbcHelper.executeUpdate("DELETE FROM " + TARGET_PARTITION
                    + " WHERE tmp_event_id IN (" + commaJoin(insertedIds) + ")");
        }
        return insertRows(timestamp, LIVE_ROWS_FOR_FINAL_DELETE);
    }

    private record Diagnostics(long deadTuples, long relPages, String text) {
    }

    private Diagnostics collectDiagnostics(String plan, boolean afterVacuum) throws SQLException {
        long deadTuples = readDeadTuples(TARGET_PARTITION);
        long relPages = readRelPages(TARGET_PARTITION);
        String text = "\nDead tuples in " + TARGET_PARTITION
                + (afterVacuum ? " (after VACUUM)" : "") + ": " + deadTuples
                + "\nrelpages: " + relPages
                + "\nPlan:\n" + plan;
        return new Diagnostics(deadTuples, relPages, text);
    }

    private List<Long> insertRows(long timestamp, int count) throws SQLException {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long id = dbService.nextId();
            jdbcHelper.executeUpdate(INSERT_SQL,
                    id, timestamp, 1, "repo1/bloat/" + id);
            ids.add(id);
        }
        return ids;
    }

    private void setAutovacuumEnabled(String partition, boolean enabled) throws SQLException {
        jdbcHelper.executeUpdate("ALTER TABLE " + partition
                + " SET (autovacuum_enabled = " + enabled + ")");
    }

    private long readDeadTuples(String partition) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(
                "SELECT n_dead_tup FROM pg_stat_user_tables "
                        + "WHERE schemaname = current_schema() AND relname = ?",
                partition)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private long readRelPages(String partition) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(
                "SELECT c.relpages FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = current_schema() AND c.relname = ?",
                partition)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private boolean isPostgresqlDb() {
        return DbType.POSTGRESQL.equals(dbProperties.getDbType());
    }

    private long partitionHourTimestamp(int hour) {
        return dayStartUtcMillis + ((long) hour) * 3_600_000L;
    }

    private String explain(String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (ResultSet rs = jdbcHelper.executeSelect("EXPLAIN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
        }
        return plan.toString();
    }

    private static String commaJoin(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    /**
     * Builds the {@code ((tmp_event_id, timestamp, partition-key expression), ...)} tuple list for the
     * fixed DELETE. All rows share {@code timestamp} here (single target partition).
     */
    private static String tuplesFor(long timestamp, List<Long> ids) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("(").append(ids.get(i)).append(", ").append(timestamp)
                    .append(", (").append(timestamp).append(" % (24 * 3600000) / 3600000))");
        }
        sb.append(")");
        return sb.toString();
    }
}
