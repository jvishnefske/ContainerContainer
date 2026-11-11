package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.NodeEventsCursorDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.cursor.RawMirrorStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Test
public class EventCursorDaoNonSyncCursorsTest extends DbBaseTest {

    @Autowired
    private NodeEventsCursorDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/federated_status/non_sync_cursors.sql");
    }


    @AfterMethod
    @Override
    public void unbindArtifactoryHome() {
        importSql("/sql/federated_status/non_sync_cursors_delete.sql");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @Test
    public void testGetNonSyncFederatedQueues() throws SQLException {
        List<RawMirrorStatusInfo> mirrorStatusInfos =
                dao.getMirrorsByTypeAndDifferentProvidedStatus("FEDERATED_REPLICATION", "HEALTHY");
        Assert.assertTrue(mirrorStatusInfos.size() > 0);
        Assert.assertEquals(mirrorStatusInfos.size(), 3);
        Set<String> cursors =
                mirrorStatusInfos.stream().map(RawMirrorStatusInfo::getCursor).collect(Collectors.toSet());
        Assert.assertFalse(cursors.contains(
                "t1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t1-repo-cursor"));
        Assert.assertFalse(cursors.contains(
                "non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor"));
        Set<String> statuses =
                mirrorStatusInfos.stream().map(RawMirrorStatusInfo::getStatus).collect(Collectors.toSet());
        Assert.assertFalse(statuses.contains("SYNC"));
        Assert.assertEquals(statuses.size(), 1);
    }
}
