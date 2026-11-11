package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.VersionHistoryDao;
import org.artifactory.storage.db.fs.entity.VersionHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.converter.DbSchemaVersionLogDao;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.model.SchemaVersionLog;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class VersionHistoryDaoTest extends DbBaseTest {

    private VersionHistoryDao versionHistoryDao;
    private ConfigHistoryDao configHistoryDao;
    private DbSchemaVersionLogDao schemaVersionLogDao;

    private static final String VERSION = "8.1.1";

    private static final String TO_VERSION = "8.1.2";

    private static final String NON_BACKWARD_COMPATIBLE_TO_VERSION = "8.1.3";

    private static final String FIRST_CONVERTER = "v197";
    private static final String SECOND_CONVERTER = "v198";
    private static final String NON_BACKWARD_COMPATIBLE_CONVERTER = "v201";

    @BeforeClass
    public void setup() throws SQLException, UnsupportedEncodingException {
        ArtifactoryHome home = new ArtifactoryHome("homeDir");
        home.initDBProperties();
        versionHistoryDao = new VersionHistoryDao(jdbcHelper, home);
        configHistoryDao = new ConfigHistoryDao(jdbcHelper);
        schemaVersionLogDao = new DbSchemaVersionLogDao(jdbcHelper,
                ArtifactoryHome.get().getDBProperties().getDbType());
        configHistoryDao.insert("test", "test", "test");
        SchemaVersionLog firstSchemaVersionLog = new SchemaVersionLog(FIRST_CONVERTER, "desc",
                System.currentTimeMillis(), System.currentTimeMillis(), ConverterApplyStatus.COMPLETED,
                ConverterExecutionType.SEQ_SYNC, TO_VERSION, "init_version", "serverId");
        SchemaVersionLog secondSchemaVersionLog = new SchemaVersionLog(SECOND_CONVERTER, "desc",
                System.currentTimeMillis(), System.currentTimeMillis(), ConverterApplyStatus.COMPLETED,
                ConverterExecutionType.SEQ_SYNC,
                TO_VERSION, "init_version", "serverId");

        schemaVersionLogDao.save(firstSchemaVersionLog);
        schemaVersionLogDao.save(secondSchemaVersionLog);
    }

    @AfterMethod
    public void clear() throws SQLException {
        versionHistoryDao.deleteAll();
    }

    @Test
    public void insert() throws SQLException {
        boolean saved = versionHistoryDao.insert(VERSION, TO_VERSION);
        assertTrue(saved);
    }

    @Test
    public void nonBackwardCompatible() throws SQLException {
        assertFalse(versionHistoryDao.nonBackwardCompatible(TO_VERSION));
    }

    @Test
    public void getLatest() throws SQLException {
        // Insert 3 records, latest version will be 5
        versionHistoryDao.insert("1", "2");
        versionHistoryDao.insert("3", "4");
        versionHistoryDao.insert("5","6");
        VersionHistoryRecord actual = versionHistoryDao.getLatestActive();

        assertEquals(actual.version(), "5");
        assertEquals(actual.id(), 3);
        assertEquals(actual.configVersionId(), 1);
        assertTrue(actual.backwardCompatible());
    }
    @Test
    public void getLatestEmpty() throws SQLException {
        assertNull(versionHistoryDao.getLatestActive());
    }

    @Test
    public void updateBackwardCompatible() throws SQLException {
        versionHistoryDao.insert(VERSION, TO_VERSION);
        assertTrue(versionHistoryDao.getLatestActive().backwardCompatible());

        versionHistoryDao.updateBackwardCompatible(VERSION, TO_VERSION);
        assertTrue(versionHistoryDao.getLatestActive().backwardCompatible());
    }

    @Test
    public void updateBackwardCompatibleWhenNonBackwardCompatible() throws SQLException {
        SchemaVersionLog nonBackwardCompatibleConverterLog = new SchemaVersionLog(
                NON_BACKWARD_COMPATIBLE_CONVERTER, "desc",
                System.currentTimeMillis(), System.currentTimeMillis(), ConverterApplyStatus.COMPLETED,
                ConverterExecutionType.SEQ_SYNC,
                NON_BACKWARD_COMPATIBLE_TO_VERSION, "init_version", "serverId");
        schemaVersionLogDao.save(nonBackwardCompatibleConverterLog);
        assertTrue(versionHistoryDao.nonBackwardCompatible(NON_BACKWARD_COMPATIBLE_TO_VERSION));

        versionHistoryDao.insert(VERSION, NON_BACKWARD_COMPATIBLE_TO_VERSION);
        assertTrue(versionHistoryDao.getLatestActive().backwardCompatible());

        versionHistoryDao.updateBackwardCompatible(VERSION, NON_BACKWARD_COMPATIBLE_TO_VERSION);
        assertFalse(versionHistoryDao.getLatestActive().backwardCompatible());
    }

    @Test
    public void update() throws SQLException {
        versionHistoryDao.insert("1", "2");
        // Set version as inactive
        versionHistoryDao.update("1");
        // Check there are no active versions
        assertNull(versionHistoryDao.getLatestActive());
    }
}
