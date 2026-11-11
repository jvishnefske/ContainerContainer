package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v143;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v144;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

public class V144SetColumnDateDistributedWithNotNullValueTest extends UpgradeBaseTest {

    @Test
    public void testSetDateDistributedWithValueIfNull() throws SQLException {
        prepareDBForTest();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v144)) {
            v144.converter().convert(context);
        }
        validDBAfterConverter();
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v143;
    }

    private void prepareDBForTest() throws SQLException {
        insertRowsToDB();
        Map<Integer, Integer> dateDistributedValues = getDateDistributedFromDB();
        assertNumberOfNullRows(2);
        assertEquals((int) dateDistributedValues.get(1666522548), 0);
        assertEquals((int) dateDistributedValues.get(1666522617), 1666522625);
        assertEquals((int) dateDistributedValues.get(1666522636), 0);
    }

    private void insertRowsToDB() throws SQLException {
        jdbcHelper.executeUpdate(
                "INSERT INTO artifact_bundles (id, name, version, status, date_created, date_distributed, signature, " +
                        "type, storing_repo, keep) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1, "rb_name", "rb_ver_1", "COMPLETE", 1666522548, null, "signature", "TARGET", "repo", 1);
        jdbcHelper.executeUpdate(
                "INSERT INTO artifact_bundles (id, name, version, status, date_created, date_distributed, signature, " +
                        "type, storing_repo, keep) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                2, "rb_name", "rb_ver_2", "COMPLETE", 1666522617, 1666522625, "signature", "TARGET", "repo", 1);
        jdbcHelper.executeUpdate(
                "INSERT INTO artifact_bundles (id, name, version, status, date_created, date_distributed, signature, " +
                        "type, storing_repo, keep) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                3, "rb_name", "rb_ver_3", "COMPLETE", 1666522636, null, "signature", "SOURCE", "repo", 1);
    }

    private void validDBAfterConverter() throws SQLException {
        Map<Integer, Integer> dateDistributedValues = getDateDistributedFromDB();
        assertNumberOfNullRows(1);
        assertEquals((int) dateDistributedValues.get(1666522548), 1666522548);
        assertEquals((int) dateDistributedValues.get(1666522617), 1666522625);
        assertEquals((int) dateDistributedValues.get(1666522636), 0);
    }

    @NonNull
    private Map<Integer, Integer> getDateDistributedFromDB() throws SQLException {
        try (ResultSet result = jdbcHelper.executeSelect("SELECT COUNT(*) FROM artifact_bundles")) {
            assertTrue(result.next());
            assertEquals(result.getInt(1), 3);
        }
        Map<Integer, Integer> dateDistributedValues = new HashMap<>();
        try (ResultSet result = jdbcHelper.executeSelect("SELECT * FROM artifact_bundles")) {
            while (result.next()) {
                dateDistributedValues.put(
                        result.getInt("date_created"), result.getInt("date_distributed"));
            }
        }
        return dateDistributedValues;
    }

    private void assertNumberOfNullRows(int expected) throws SQLException {
        try (ResultSet result = jdbcHelper.executeSelect(
                "SELECT COUNT(*) FROM artifact_bundles where date_distributed IS NULL")) {
            assertTrue(result.next());
            assertEquals(result.getInt(1), expected);
        }
    }
}
