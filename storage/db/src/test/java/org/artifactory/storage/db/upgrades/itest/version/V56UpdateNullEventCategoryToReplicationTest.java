package org.artifactory.storage.db.upgrades.itest.version;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.util.DbUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.itest.DbTestUtils.createSimpleJdbcInsert;
import static org.artifactory.storage.db.util.ArtifactoryDbVersionUtil.versionsBetween;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v55;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v56;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v64;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertFalse;

/**
 * @author Mor Merhav
 */
@Test
public class V56UpdateNullEventCategoryToReplicationTest extends UpgradeBaseTest {

    private static final String TABLE_NODE_EVENTS_ERRORS = "node_events_errors";
    public static final String TABLE_REPLICATION_ERRORS = "replication_errors";

    private TemporaryDBChannel temporaryDBChannel;
    private JdbcTemplate jdbc;
    private SimpleJdbcInsert simpleJdbcInsert;

    @BeforeMethod
    public void beforeMethod() throws SQLException, IOException {
        temporaryDBChannel = new TemporaryDBChannel(ArtifactoryHome.get().getDBProperties());
        jdbc = temporaryDBChannel.asJdbcTemplate();
        simpleJdbcInsert = createSimpleJdbcInsert(jdbc.getDataSource());
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        if (DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), TABLE_NODE_EVENTS_ERRORS)) {
            jdbc.execute("TRUNCATE TABLE " + TABLE_NODE_EVENTS_ERRORS);
        }

        if (DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), TABLE_REPLICATION_ERRORS)) {
            jdbc.execute("TRUNCATE TABLE " + TABLE_REPLICATION_ERRORS);
        }

        temporaryDBChannel.close();
    }

    @Test
    public void testConvertNullEventCategoryToReplication() throws Exception {
        //insert data to replication_errors (old table) table
        NodeEventErrorsDBO eventErrorsDBO1 = new NodeEventErrorsDBO(1L, null);
        NodeEventErrorsDBO eventErrorsDBO2 = new NodeEventErrorsDBO(2L, null);
        NodeEventErrorsDBO eventErrorsDBO3 = new NodeEventErrorsDBO(3L, null);
        SqlParameterSource[] batch =
                SqlParameterSourceUtils.createBatch(eventErrorsDBO1, eventErrorsDBO2, eventErrorsDBO3);
        simpleJdbcInsert.withTableName(TABLE_REPLICATION_ERRORS).executeBatch(batch);

        final List<DbVersion> dbVersions = versionsBetween(v56, v64);
        for (DbVersion dbVersion : dbVersions) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }

        List<String> eventCategories =
                jdbc.queryForList("SELECT event_category FROM " + TABLE_NODE_EVENTS_ERRORS, String.class);

        Assert.assertNotNull(eventCategories);
        Assert.assertFalse(eventCategories.isEmpty());
        eventCategories.forEach(eventCategory -> Assert.assertEquals("REPLICATION", eventCategory));

        assertFalse(withConnection(jdbcHelper, conn -> DbUtils
                .isColumnNullable(conn.getMetaData(), dbProperties.getDbType(), TABLE_NODE_EVENTS_ERRORS,
                        "event_category")));
    }

    @Data
    @AllArgsConstructor
    static class NodeEventErrorsDBO {
        long errorId;
        String replicationKey = "opKey";
        String eventCategory;
        long firstErrorTime = 1L;
        long lastErrorTime = 1L;
        short errorCount = 1;
        String errorMessage = "errMsg";
        long taskTime = 1L;
        short taskType = 1;
        String taskPath = "path";

        public NodeEventErrorsDBO(long errorId, String eventCategory) {
            this.errorId = errorId;
            this.eventCategory = eventCategory;
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v55;
    }
}
