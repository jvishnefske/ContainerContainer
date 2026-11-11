package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v182;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v271;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v273;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Verifies that the v273 converter widens each {@code node_events_tmp_*} partition primary key from
 * the single-column {@code (tmp_event_id)} to the composite {@code (timestamp, tmp_event_id)} and
 * drops the now-redundant {@code *_idx_time_id} unique indexes.
 * <p>
 * The composite PK lets the shift-events tuple-{@code IN} DELETE
 * ({@link org.artifactory.storage.db.event.dao.EventsDao#DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX})
 * use the PK index while pruning to the partitions that actually hold the rows. PostgreSQL-only —
 * the partitioned layout exists only on PG (created by v182).
 * <p>
 * Both v182 (creates the partitions) and v273 are {@code SEQ_ASYNC}, so they are not run by
 * {@link UpgradeBaseTest}'s sync chain; the test runs them explicitly, mirroring
 * {@link V182NodeEventsTmpPartitionsTest}.
 */
public class V273AdjustNodeEventsTmpPartitionPkTest extends UpgradeBaseTest {

    private static final List<String> PARTITIONS = List.of(
            "node_events_tmp_p0_5",
            "node_events_tmp_p6_11",
            "node_events_tmp_p12_17",
            "node_events_tmp_p18_23");

    private static final List<String> OLD_TIME_ID_INDEXES = List.of(
            "node_events_tmp_p0_5_idx_time_id",
            "node_events_tmp_p6_11_idx_time_id",
            "node_events_tmp_p12_17_idx_time_id",
            "node_events_tmp_p18_23_idx_time_id");

    private static final List<String> EXPECTED_PK_COLUMNS = List.of("timestamp", "tmp_event_id");

    @Test
    public void testV273WidensPartitionPkAndDropsTimeIdIndexes() throws SQLException {
        testOnlyWithPostgresql();

        runConverter(v182);
        runConverter(v273);

        for (String index : OLD_TIME_ID_INDEXES) {
            assertFalse(indexExists(index),
                    "v273 should have dropped the redundant unique index " + index);
        }

        for (String partition : PARTITIONS) {
            assertEquals(primaryKeyColumns(partition), EXPECTED_PK_COLUMNS,
                    "Partition " + partition + " should have a composite PK on (timestamp, tmp_event_id)");
        }
    }

    /**
     * Runs the given converter and asserts that it did not error out. The actual {@link ConverterApplyStatus}
     * is intentionally not asserted: with shared test DB state across suite classes a converter may return
     * {@code SKIPPED} if its preconditions detect it has already been applied — what matters for this test
     * is the post-condition (composite PK present, old indexes gone), not which exact path the converter took.
     */
    private void runConverter(ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), POSTGRESQL, version)) {
            DbConverterResult result = version.converter().convert(context);
            assertEquals(result.getStatus() == ConverterApplyStatus.COMPLETED
                            || result.getStatus() == ConverterApplyStatus.SKIPPED, true,
                    "Converter " + version.version() + " ended in unexpected status: " + result.getStatus());
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(
                "SELECT count(1) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                indexName)) {
            return rs.next() && rs.getLong(1) > 0;
        }
    }

    /**
     * Returns the primary-key columns of the given partition in PK definition order, read from the
     * PostgreSQL catalog so the column ordering (not just membership) is verified.
     */
    private List<String> primaryKeyColumns(String partition) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = jdbcHelper.executeSelect(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                        + "WHERE i.indrelid = ?::regclass AND i.indisprimary "
                        + "ORDER BY array_position(i.indkey, a.attnum)",
                partition)) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        return columns;
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v271;
    }
}
