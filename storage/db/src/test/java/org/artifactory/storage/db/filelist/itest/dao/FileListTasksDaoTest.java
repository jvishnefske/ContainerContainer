package org.artifactory.storage.db.filelist.itest.dao;

import org.artifactory.storage.db.filelist.FileListInfo;
import org.artifactory.storage.db.filelist.FileListTasksDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.common.ClockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * @author Shahar Palmor
 */

public class FileListTasksDaoTest extends DbBaseTest {

    @Autowired
    private FileListTasksDao dao;

    @Test
    void testCreateFileListTask() throws SQLException {
        FileListInfo fileListInfo = new FileListInfo("repo", "path", null);
        dao.create(fileListInfo);
        FileListInfo retrieved = dao.retrieveFileListTask("repo");
        assertEquals(retrieved.getPath(), "path");
        dao.delete("repo");
    }

    @Test
    void testUpdateInactiveFileListTask() throws SQLException {
        long now = ClockUtils.epochMillis();
        long future = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        FileListInfo fileListInfo = new FileListInfo("repo", "path", null);
        dao.create(fileListInfo);
        FileListInfo retrieved = dao.retrieveFileListTask("repo");
        assertEquals(retrieved.getPath(), "path");
        assertEquals(retrieved.getHeartbeat(), 0);
        dao.updateInactiveTask("repo", future);
        FileListInfo retrieved2 = dao.retrieveFileListTask("repo");
        assertNotEquals(retrieved2.getHeartbeat(), retrieved.getHeartbeat());
        dao.updateInactiveTask("repo", now);
        FileListInfo retrieved3 = dao.retrieveFileListTask("repo");
        assertEquals(retrieved3.getHeartbeat(), future);
        dao.delete("repo");
    }

    @Test
    void testUpdate() throws SQLException {
        FileListInfo fileListInfo = new FileListInfo("repo", "path", null);
        dao.create(fileListInfo);
        FileListInfo retrieved = dao.retrieveFileListTask("repo");
        assertEquals(retrieved.getPath(), "path");
        long currentHeartBeat = ClockUtils.epochMillis();
        dao.update("repo", currentHeartBeat);
        FileListInfo retrieved2 = dao.retrieveFileListTask("repo");
        assertNotEquals(retrieved.getHeartbeat(), retrieved2.getHeartbeat());
        dao.delete("repo");
    }

    @Test
    void testGetActiveTaskCount() throws SQLException {
        long future = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        FileListInfo fileListInfo = new FileListInfo("repo", "path", null);
        dao.create(fileListInfo);
        FileListInfo retrieved = dao.retrieveFileListTask("repo");
        assertEquals(retrieved.getPath(), "path");
        assertEquals(retrieved.getHeartbeat(), 0);
        dao.updateInactiveTask("repo", future);
        assertEquals(dao.getActiveTasksCount(), 1);
        dao.delete("repo");
    }

    @Test
    void testGetInActiveTasks() throws SQLException {
        FileListInfo fileListInfo = new FileListInfo("repo", "path", null);
        dao.create(fileListInfo);
        FileListInfo retrieved = dao.retrieveFileListTask("repo");
        assertEquals(retrieved.getPath(), "path");
        assertEquals(dao.getInactiveTasks().size(), 1);
        dao.delete("repo");
    }
}