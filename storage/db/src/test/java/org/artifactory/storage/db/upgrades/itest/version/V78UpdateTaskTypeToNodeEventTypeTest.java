package org.artifactory.storage.db.upgrades.itest.version;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.config.db.TemporaryDBChannel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;

import static org.artifactory.storage.db.itest.DbTestUtils.createSimpleJdbcInsert;
import static org.artifactory.storage.replication.errors.InternalReplicationEventType.DELETE;
import static org.artifactory.storage.replication.errors.InternalReplicationEventType.DEPLOY;
import static org.artifactory.storage.replication.errors.InternalReplicationEventType.MKDIR;
import static org.artifactory.storage.replication.errors.InternalReplicationEventType.PROPERTY_CHANGE;

/**
 * @author Mor Merhav
 */
@Test
public class V78UpdateTaskTypeToNodeEventTypeTest extends UpgradeBaseTest {

    private static final String TABLE_NODE_EVENTS_ERRORS = "node_events_errors";

    private TemporaryDBChannel temporaryDBChannel;
    private JdbcTemplate jdbc;
    private SimpleJdbcInsert simpleJdbcInsert;


    @BeforeMethod
    public void beforeMethod() {
        temporaryDBChannel = new TemporaryDBChannel(dbProperties);
        jdbc = temporaryDBChannel.asJdbcTemplate();
        simpleJdbcInsert = createSimpleJdbcInsert(jdbc.getDataSource());
    }

    @AfterMethod
    public void afterMethod() {
        jdbc.execute("TRUNCATE TABLE " + TABLE_NODE_EVENTS_ERRORS);
        temporaryDBChannel.close();
    }

    @Test
    public void testConvertTaskTypeToEventType() {
        NodeEventErrorsDBO eventErrorsDBO1 = new NodeEventErrorsDBO(1L, DEPLOY.code());
        NodeEventErrorsDBO eventErrorsDBO2 = new NodeEventErrorsDBO(2L, MKDIR.code());
        NodeEventErrorsDBO eventErrorsDBO3 = new NodeEventErrorsDBO(3L, DELETE.code());
        NodeEventErrorsDBO eventErrorsDBO4 = new NodeEventErrorsDBO(4L, PROPERTY_CHANGE.code());
        SqlParameterSource[] batch =
                SqlParameterSourceUtils.createBatch(eventErrorsDBO1, eventErrorsDBO2, eventErrorsDBO3, eventErrorsDBO4);
        simpleJdbcInsert.withTableName(TABLE_NODE_EVENTS_ERRORS).executeBatch(batch);

        String convertQuery = "UPDATE " + TABLE_NODE_EVENTS_ERRORS + " SET event_type = 1 WHERE event_type = 2";
        jdbc.update(convertQuery);
        List<NodeEventErrorsDBO> dbos = jdbc.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS,
                (rs, i) -> new NodeEventErrorsDBO(rs.getLong("error_id"),
                        rs.getInt("event_type")));

        Assert.assertNotNull(dbos);
        Assert.assertFalse(dbos.isEmpty());

        dbos.sort(Comparator.comparing(NodeEventErrorsDBO::getErrorId));

        Assert.assertEquals(dbos.get(0).eventType, EventType.create.code());
        Assert.assertEquals(dbos.get(1).eventType, EventType.create.code());
        Assert.assertEquals(dbos.get(2).eventType, EventType.delete.code());
        Assert.assertEquals(dbos.get(3).eventType, EventType.props.code());
    }

    @Data
    @AllArgsConstructor
    static class NodeEventErrorsDBO {
        long errorId;
        String operationKey = "opKey";
        String eventCategory = "eventCat";
        long firstErrorTime = 1L;
        long lastErrorTime = 1L;
        short errorCount = 1;
        String errorMessage = "errMsg";
        long eventTime = 1L;
        int eventType;
        String path = "path";

        public NodeEventErrorsDBO(long errorId, int eventType) {
            this.errorId = errorId;
            this.eventType = eventType;
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v78;
    }
}
