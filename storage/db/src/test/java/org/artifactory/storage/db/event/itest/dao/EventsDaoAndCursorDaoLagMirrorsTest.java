package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.NodeEventsAndNodeEventCursorDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.cursor.RawMirrorLagInfo;
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
public class EventsDaoAndCursorDaoLagMirrorsTest extends DbBaseTest {

    @Autowired
    private NodeEventsAndNodeEventCursorDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/federated_status/mirrors_lag_status.sql");
    }


    @AfterMethod
    @Override
    public void unbindArtifactoryHome() {
        importSql("/sql/federated_status/mirrors_lag_status_delete.sql");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @Test
    public void testGetMirrorsLag() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        Assert.assertTrue(federatedRawMirrorLagInfos.size() > 0);
        Assert.assertEquals(federatedRawMirrorLagInfos.size(), 4);
    }

    @Test
    public void testGetMirrorsLagWithEarliestEvent() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        Assert.assertTrue(federatedRawMirrorLagInfos.size() > 0);
        Assert.assertEquals(federatedRawMirrorLagInfos.size(), 4);
        long valueForCursorTest1 = federatedRawMirrorLagInfos.get(0).getEventRegistrationTimeStamp();
        long valueForCursorTest2 = federatedRawMirrorLagInfos.get(1).getEventRegistrationTimeStamp();
        Assert.assertEquals(valueForCursorTest1, 1658735275L); // earliest event
        Assert.assertEquals(valueForCursorTest2, 1658735281L); // earliest event
    }

    @Test
    public void testGetOnlyFederatedMirrors() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        Set<String> local_repo_keys =
                federatedRawMirrorLagInfos.stream().map(cursorRecord -> {
                    String cursor = cursorRecord.getCursor();
                    return cursor.substring(0, cursor.indexOf("-federated_replication"));
                }).collect(
                        Collectors.toSet());
        Assert.assertTrue(!local_repo_keys.contains("non-federated-repo"));
    }

    @Test
    public void testGetMirrorsLagAreOrderedByTimeStamp() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        Assert.assertTrue(federatedRawMirrorLagInfos.size() > 0);
        Assert.assertEquals(federatedRawMirrorLagInfos.size(), 4);
        Assert.assertTrue(federatedRawMirrorLagInfos.get(0).getEventRegistrationTimeStamp() <
                federatedRawMirrorLagInfos.get(1).getEventRegistrationTimeStamp());
    }

    @Test
    public void testNoEventsReturnForMirrorsWhereHintIsNotNull() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        RawMirrorLagInfo rawMirrorLagInfoWithOutNextEvent =
                federatedRawMirrorLagInfos.stream()
                        .filter(rawMirrorLagInfo -> rawMirrorLagInfo.getCursor().contains("test3-repo"))
                        .toList().get(0);
        // verify that event registration is 0 - because there is no event with null hint to replicate
        Assert.assertEquals(rawMirrorLagInfoWithOutNextEvent.getEventRegistrationTimeStamp(), 0);
    }

    @Test
    public void testMirrorsWithOutNextEventsDoesNotHaveLag() throws SQLException {
        List<RawMirrorLagInfo> federatedRawMirrorLagInfos = dao.getMirrorsLag();
        RawMirrorLagInfo rawMirrorLagInfoWithOutNextEvent =
                federatedRawMirrorLagInfos.stream()
                        .filter(rawMirrorLagInfo -> rawMirrorLagInfo.getCursor().contains("test4-repo"))
                        .toList().get(0);
        // verify that event registration is 0 - because there is no event after to replicate
        Assert.assertEquals(rawMirrorLagInfoWithOutNextEvent.getEventRegistrationTimeStamp(), 0);
    }
}
