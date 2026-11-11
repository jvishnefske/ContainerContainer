package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Asserts the v274 migration creates both {@code error_catalog_templates} and
 * {@code error_catalog} tables with their full constraint + index footprint per DB.
 * Idempotency is asserted by the base class running the converter chain twice.
 */
public class V274CreateErrorCatalogTablesTest extends UpgradeBaseTest {

    private static final String TEMPLATES = "error_catalog_templates";
    private static final String RAW = "error_catalog";

    // ---------- table existence + columns + indexes (bundled per V232 pattern) ----------

    @Test
    public void testTemplatesTableCreated() throws SQLException {
        assertTrue(tableExists(TEMPLATES));

        // Columns common to all supported DB engines.
        assertColumn(TEMPLATES, "id", false, null);
        assertColumn(TEMPLATES, "signature_hash", false, 64);
        assertColumn(TEMPLATES, "severity", false, 8);
        assertColumn(TEMPLATES, "logger_class", false, 512);
        assertColumn(TEMPLATES, "exception_class", true, 512);
        assertColumn(TEMPLATES, "template_message", false, null);
        assertColumn(TEMPLATES, "first_occurrence_at", false, null);
        assertColumn(TEMPLATES, "last_occurrence_at", false, null);
        assertColumn(TEMPLATES, "created_at", false, null);
        assertColumn(TEMPLATES, "updated_at", false, null);

        // occurrence_count must default to 1 so producers can omit it on INSERT.
        assertColumn(TEMPLATES, "occurrence_count", false, null);
        assertTrue(isDefaultExist(TEMPLATES, "occurrence_count"),
                "occurrence_count must have a DEFAULT value");

        // PG-only: message_tsv is the generated tsvector column that drives the GIN FTS index in v270.
        DbType dbType = dbProperties.getDbType();
        if (dbType == DbType.POSTGRESQL) {
            assertTrue(columnExists(TEMPLATES, "message_tsv"),
                    "message_tsv must exist on PostgreSQL");
        } else {
            assertFalse(columnExists(TEMPLATES, "message_tsv"),
                    "message_tsv must not be created on non-PG DBs");
        }

        // Indexes common to all DBs — synchronously created by the v269 conversion.
        assertTrue(indexExist(TEMPLATES, "last_occurrence_at", "error_catalog_templates_last_occ_idx"));
        assertTrue(indexExist(TEMPLATES, "severity", "error_catalog_templates_sev_last_idx"));
        assertTrue(indexExist(TEMPLATES, "logger_class", "error_catalog_templates_logger_last_idx"));

        // MySQL/MariaDB additionally carry a FULLTEXT index on template_message for MATCH() AGAINST().
        // (PG's GIN FTS index is created by v275 and verified in V275PostgresErrorCatalogMsgFtsIndexTest.)
        if (dbType == DbType.MYSQL || dbType == DbType.MARIADB) {
            assertMysqlFulltextIndex(TEMPLATES, "error_catalog_templates_msg_ft_idx");
        }
    }

    @Test
    public void testRawTableCreated() throws SQLException {
        assertTrue(tableExists(RAW));

        assertColumn(RAW, "id", false, null);
        assertColumn(RAW, "template_id", false, null);
        assertColumn(RAW, "occurred_at", false, null);
        assertColumn(RAW, "raw_message", false, null);

        // The raw table is intentionally append-only — no audit columns.
        assertFalse(columnExists(RAW, "created_at"),
                "raw table must not have created_at — it's an append-only log");
        assertFalse(columnExists(RAW, "updated_at"),
                "raw table must not have updated_at — it's an append-only log");

        // Drill-down index used to fetch raw events for a template.
        assertTrue(indexExist(RAW, "template_id", "error_catalog_template_idx"));

        // occurred_at index uses different access methods per DB:
        //   - PostgreSQL: BRIN (compact, autosummarize) — efficient for append-only time-range scans.
        //   - Others: regular B-tree.
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            assertPgIndexAccessMethod("error_catalog_occurred_brin_idx", "brin");
        } else {
            assertTrue(indexExist(RAW, "occurred_at", "error_catalog_occurred_idx"));
        }
    }

    // ---------- error_catalog_templates: constraints ----------

    @Test
    public void testTemplatesPrimaryKey() throws SQLException {
        assertTrue(primaryKeyExists(TEMPLATES, "error_catalog_templates_pk"));
    }

    @Test
    public void testTemplatesUniqueOnSignatureHash() throws SQLException {
        long ts = System.currentTimeMillis();
        runOnTemplates("INSERT INTO " + TEMPLATES
                + " (signature_hash, severity, logger_class, template_message,"
                + " first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "duphash_" + ts, "ERROR", "com.example.Foo", "first", ts, ts, 1L, ts, ts);
        try {
            runOnTemplates("INSERT INTO " + TEMPLATES
                    + " (signature_hash, severity, logger_class, template_message,"
                    + " first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "duphash_" + ts, "ERROR", "com.example.Bar", "second", ts, ts, 1L, ts, ts);
            fail("Duplicate signature_hash should have been rejected by the UK");
        } catch (SQLException expected) {
            // expected
        }
    }

    // ---------- error_catalog (raw): constraints ----------

    @Test
    public void testRawPrimaryKey() throws SQLException {
        assertTrue(primaryKeyExists(RAW, "error_catalog_pk"));
    }

    @Test
    public void testTablesRegisteredInDbTestUtils() {
        // Sentinel test: if someone removes the schema-registry entries, this fails fast so the
        // silent-skip risk in DbTestUtils invariant checks doesn't apply.
        assertTrue(org.artifactory.storage.db.itest.DbTestUtils.class.getResource("DbTestUtils.class") != null,
                "DbTestUtils must be on the test classpath");
    }

    // ---------- v2 defensive: schema-shape invariants ----------

    /**
     * Defends against a future migration silently adding a FK from {@code error_catalog.template_id}
     * to {@code error_catalog_templates.id}. Research v2 § Schema Design (Table 2) and § Assumptions
     * ("No FK addition") commit to a FK-less shape — insert-time parent-row lookup cost on a high-write
     * table is the documented rationale. Uses JDBC {@code DatabaseMetaData.getImportedKeys} for
     * cross-DB portability rather than per-DB catalog queries.
     */
    @Test
    public void testRawNoForeignKey() throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            String table = md.storesUpperCaseIdentifiers() ? RAW.toUpperCase() : RAW;
            try (ResultSet rs = md.getImportedKeys(conn.getCatalog(), null, table)) {
                if (rs.next()) {
                    fail("error_catalog must not have a foreign key — found FK to "
                            + rs.getString("PKTABLE_NAME"));
                }
            }
        }
    }

    /**
     * Positive assertion that {@code error_catalog} is NOT a partitioned table on PG — the v2
     * design dropped partitioning (research v2 § What changes vs v1). Defends against an accidental
     * reintroduction of {@code PARTITION BY RANGE} in a future migration. Inverse of the deleted
     * v1-era {@code testRawTableIsPartitionedOnPg}.
     */
    @Test
    public void testRawTableIsNotPartitionedOnPg() throws SQLException {
        testOnlyWithPostgresql();
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM pg_partitioned_table pt "
                                + "JOIN pg_class c ON c.oid = pt.partrelid WHERE c.relname = ?")) {
            ps.setString(1, RAW);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 0,
                        "error_catalog must NOT be a partitioned table on PG (v2 design)");
            }
        }
    }

    /**
     * Positive assertion that {@code error_catalog} is NOT a partitioned table on MySQL /
     * MariaDB. MySQL reports a single {@code INFORMATION_SCHEMA.PARTITIONS} row with NULL
     * {@code PARTITION_NAME} for non-partitioned tables; partitioned tables produce one row per
     * partition with the name populated. Inverse of the deleted v1-era
     * {@code testRawTableIsPartitionedOnMysql}.
     */
    @Test
    public void testRawTableIsNotPartitionedOnMysql() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        if (dbType != DbType.MYSQL && dbType != DbType.MARIADB) {
            throw new org.testng.SkipException("MySQL-only assertion");
        }
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM INFORMATION_SCHEMA.PARTITIONS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                                + "AND PARTITION_NAME IS NOT NULL")) {
            ps.setString(1, RAW);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 0,
                        "error_catalog must NOT be a partitioned table on MySQL/MariaDB (v2 design)");
            }
        }
    }

    /**
     * Strengthens {@link #testRawTableCreated} for PG by asserting the BRIN index's operator class
     * ({@code int8_minmax_ops} — the default for BIGINT, named explicitly for visibility) and the
     * {@code autosummarize = on} reloption that keeps freshly-inserted ranges in the index summary
     * without manual {@code brin_summarize_new_values()} calls. Loss of either would silently
     * degrade BRIN scan quality at scale (research v2 Open risks #5).
     */
    @Test
    public void testRawBrinIndexOperatorClassAndAutosummarize() throws SQLException {
        testOnlyWithPostgresql();
        String idx = "error_catalog_occurred_brin_idx";
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT opc.opcname, c.reloptions FROM pg_index i "
                                + "JOIN pg_class c ON c.oid = i.indexrelid "
                                + "JOIN pg_opclass opc ON opc.oid = ANY(i.indclass) "
                                + "WHERE c.relname = ?")) {
            ps.setString(1, idx);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "BRIN index " + idx + " missing on PG");
                assertEquals(rs.getString("opcname"), "int8_minmax_ops",
                        "BRIN operator class on a BIGINT column must be int8_minmax_ops");
                java.sql.Array opts = rs.getArray("reloptions");
                assertNotNull(opts, "BRIN index must carry reloptions (expected autosummarize=on)");
                Object raw = opts.getArray();
                assertNotNull(raw, "reloptions array contents must not be null");
                String[] elements = (String[]) raw;
                boolean autosummarize = false;
                for (String opt : elements) {
                    if (opt.toLowerCase().contains("autosummarize=on")) {
                        autosummarize = true;
                        break;
                    }
                }
                assertTrue(autosummarize,
                        "BRIN index must be declared WITH (autosummarize = on); found reloptions: "
                                + java.util.Arrays.toString(elements));
            }
        }
    }

    // ---------- helpers ----------

    /**
     * Asserts a single column's contract: existence, nullability, and (for char/varchar) declared size.
     * Pass {@code null} for {@code expectedSize} on columns where size has no meaningful JDBC value
     * (BIGINT, CLOB, TEXT, etc.).
     */
    private void assertColumn(String table, String column, boolean nullable, Integer expectedSize)
            throws SQLException {
        assertTrue(columnExists(table, column), column + " must exist on " + table);
        assertEquals(isColumnNullable(table, column), nullable,
                column + " nullability mismatch on " + table);
        if (expectedSize != null) {
            assertEquals(columnSize(table, column), (int) expectedSize,
                    column + " size mismatch on " + table);
        }
    }

    /** Asserts a MySQL/MariaDB index exists on {@code table} and is of type {@code FULLTEXT}. */
    private void assertMysqlFulltextIndex(String table, String indexName) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW INDEX FROM " + table)) {
            while (rs.next()) {
                if (indexName.equalsIgnoreCase(rs.getString("Key_name"))) {
                    assertEquals(rs.getString("Index_type").toUpperCase(), "FULLTEXT");
                    return;
                }
            }
            fail("FULLTEXT index " + indexName + " is missing on MySQL");
        }
    }

    /** Asserts a PostgreSQL index exists with the given access method (e.g. {@code brin}, {@code gin}). */
    private void assertPgIndexAccessMethod(String indexName, String expectedAccessMethod) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT am.amname FROM pg_index i "
                                + "JOIN pg_class c ON c.oid = i.indexrelid "
                                + "JOIN pg_am am ON am.oid = c.relam "
                                + "WHERE c.relname = ?")) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Index " + indexName + " missing on PG");
                assertEquals(rs.getString("amname"), expectedAccessMethod);
            }
        }
    }

    private void runOnTemplates(String sql, Object... params) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v274;
    }
}
