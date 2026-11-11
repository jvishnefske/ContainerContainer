package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v187;
import static org.jfrog.storage.DbType.ORACLE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class V187OracleNodeEventsSequenceTest extends UpgradeBaseTest {

    @Test
    public void testSequenceChangedFalse() throws SQLException {
        testOnlyWithOracle();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                ORACLE, v187)) {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.oracleNodeEventsSequenceIsNoCache.getPropertyName(), "false");
            v187.converter().convert(context);
            assertFalse(checkSequenceDefinition());
        }
    }

    @Test
    public void testSequenceChangedTrue() throws SQLException {
        testOnlyWithOracle();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                ORACLE, v187)) {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.oracleNodeEventsSequenceIsNoCache.getPropertyName(), "true");
            v187.converter().convert(context);
            assertTrue(checkSequenceDefinition());
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v187;
    }

    private boolean checkSequenceDefinition() throws SQLException {
        try (Connection con = jdbcHelper.getDataSource().getConnection(); Statement stmt = con.createStatement()) {
            ResultSet resultSet = stmt.executeQuery(
                    "select CACHE_SIZE from  USER_SEQUENCES where SEQUENCE_NAME = 'NODE_EVENTS_EVENT_ID_SEQ'");
            if (resultSet.next()) {
                long cacheSize = resultSet.getLong(1);
                return cacheSize == 0L;
            }
            return false;
        }
    }
}
