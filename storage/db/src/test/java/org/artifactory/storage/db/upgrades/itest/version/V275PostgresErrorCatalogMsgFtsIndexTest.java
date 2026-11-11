package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v274;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v275;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * v275 builds the PG GIN index on {@code message_tsv} asynchronously via
 * {@link org.artifactory.storage.db.version.converter.SafeIndexConcurrentCreator}, which already
 * runs a post-creation validity check.
 *
 * <p>Mirrors the V241 / V257 / V262 pattern for async-index tests: extend
 * {@link AsyncUpgradeBaseTest} so {@code ArtifactoryContext} is bound and
 * {@code SafeIndexConcurrentCreator} can resolve {@code ContextHelper.get().beanForType(JdbcHelper)}.
 * v274 (the sync converter that creates {@code error_catalog_templates}) is applied explicitly
 * here as well because {@code AsyncUpgradeBaseTest} rolls the schema back to v10 between its sync
 * and async passes, and {@code ArtifactoryDbVersionUtil.versionsBetween} filters {@code SEQ_SYNC}
 * only — so v275 would not run on its own. Both converters are idempotent (v274 via
 * {@code tableNotExists}, v275 via {@code indexNotExists}), so re-applying them per test method
 * is safe.</p>
 */
public class V275PostgresErrorCatalogMsgFtsIndexTest extends AsyncUpgradeBaseTest {

    private static final String INDEX = "error_catalog_templates_msg_fts_idx";

    @BeforeMethod
    public void ensureV274AndV275Applied() throws SQLException {
        // v274 is sync and creates the templates table on every DB engine; idempotent.
        try (ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), v274)) {
            v274.converter().convert(context);
        }
        // v275 is PG-only (dbSpecific predicate); on non-PG it's a no-op SKIPPED status, on PG it
        // synchronously creates the GIN index via CREATE INDEX CONCURRENTLY.
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            try (ChangeLogContext context = new ChangeLogContext(
                    jdbcHelper.getDataSource().getConnection(), POSTGRESQL, v275)) {
                v275.converter().convert(context);
            }
        }
    }

    @Test
    public void testGinIndexExistsOnPg() throws SQLException {
        testOnlyWithPostgresql();
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT am.amname FROM pg_index i "
                                + "JOIN pg_class c ON c.oid = i.indexrelid "
                                + "JOIN pg_am am ON am.oid = c.relam "
                                + "WHERE c.relname = ?")) {
            ps.setString(1, INDEX);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "GIN index " + INDEX + " is missing on PG");
                assertEquals(rs.getString("amname"), "gin");
            }
        }
    }

    @Test
    public void testGinIndexIsValidOnPg() throws SQLException {
        testOnlyWithPostgresql();
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT indisvalid FROM pg_index i "
                                + "JOIN pg_class c ON c.oid = i.indexrelid "
                                + "WHERE c.relname = ?")) {
            ps.setString(1, INDEX);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), INDEX + " not found");
                assertTrue(rs.getBoolean("indisvalid"),
                        "GIN index is INVALID — CONCURRENTLY build failed and left a useless index");
            }
        }
    }

    @Test
    public void testGinIndexAbsentOnNonPg() throws SQLException {
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            throw new SkipException("Verifying absence only on non-PG");
        }
        assertFalse(indexExist("error_catalog_templates", "template_message", INDEX));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v274;
    }
}
