package org.artifactory.storage.db.event.itest.dao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.storage.db.event.dao.NodeEventsAndNodeEventCursorDao;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class EventsDaoAndCursorDaoRepoStatusTest extends DbBaseTest {

    @Autowired
    private NodeEventsAndNodeEventCursorDao dao;

    @AfterMethod
    @Override
    public void unbindArtifactoryHome() {
        importSql("/sql/federated_status/repo_status_samples/repo_status_delete.sql");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @Test
    public void testGetReposStatuses() throws SQLException {
        importSql("/sql/federated_status/repo_status_samples/repo_status.sql");
        Map<String, Integer> repoStatusCountsInfo = dao.getFederatedRepoStatuses();
        Assert.assertFalse(repoStatusCountsInfo.isEmpty());
        Assert.assertEquals(repoStatusCountsInfo.size(), NodeEventCursorStatus.values().length);

    }

    @Test
    public void testStatusCountValid() throws SQLException {
        importSql("/sql/federated_status/repo_status_samples/repo_status.sql");
        Map<String, Integer> currentStatusCountMap = dao.getFederatedRepoStatuses();
        Assert.assertEquals(currentStatusCountMap.get("HEALTHY"), 4);
        Assert.assertEquals(currentStatusCountMap.get("PENDING_FS"), 2);
        Assert.assertEquals(currentStatusCountMap.get("FS_RUNNING"), 3);
        Assert.assertEquals(currentStatusCountMap.get("OUT_OF_SYNC"), 1);
        Assert.assertEquals(currentStatusCountMap.get("EXHAUSTED"), 1);
        Assert.assertEquals(currentStatusCountMap.get("DISABLED"), 2);
    }

    @Test
    public void testStatusNotExistValid() throws SQLException {
        importSql("/sql/federated_status/repo_status_samples/repo_status_partial.sql");
        Map<String, Integer> currentStatusCountMap = dao.getFederatedRepoStatuses();
        Assert.assertEquals(currentStatusCountMap.get("HEALTHY"), 4);
        Assert.assertEquals(currentStatusCountMap.get("PENDING_FS"), 0);
        Assert.assertEquals(currentStatusCountMap.get("FS_RUNNING"), 3);
        Assert.assertEquals(currentStatusCountMap.get("OUT_OF_SYNC"), 1);
        Assert.assertEquals(currentStatusCountMap.get("EXHAUSTED"), 0);
        Assert.assertEquals(currentStatusCountMap.get("DISABLED"), 2);
    }

    @Test
    public void testInvalidStatus() throws SQLException {
        importSql("/sql/federated_status/repo_status_samples/repo_illegal_status.sql");
        MemoryAppender appender = MemoryAppenderUtil
                .createMemoryAppender(NodeEventsAndNodeEventCursorDao.class, Level.ERROR);

        try {
            Map<String, Integer> repoStatusCountsInfo = dao.getFederatedRepoStatuses();
            List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
            Assert.assertEquals(loggedEvents.size(), 1);
            Assert.assertEquals(loggedEvents.get(0).getFormattedMessage(),
                    "Invalid status returned for repo statuses query, status: Mock_ERROR");
        } finally {
            appender.reset();
        }

    }
}
