package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.EventErrorStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Test
public class NodeEventsErrorDaoMirrorsTest extends DbBaseTest {

    @Autowired
    private NodeEventsErrorsDAO nodeEventErrorDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/federated_status/node_events_error_insert.sql");
    }


    @AfterMethod
    @Override
    public void unbindArtifactoryHome() {
        importSql("/sql/federated_status/node_events_error_delete.sql");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampPositive1() throws SQLException {
        // in the setup I created 2 errors event with same cursor and excecute the API  with timestamp that that
        // bigger then the first_error_time in node_events errors should count the errors from cursors and return 4
        // errors
        long timestampAfterTheEventsError = 1658735277;
        String eventsCursor = "repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursor);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampAfterTheEventsError);
        assertCursorsStatus(stringEventErrorStatusInfoMap, 1, eventsCursor, 4);

    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampPositive2() throws SQLException {
        // in the setup I created 2 errors event with same cursor and excecute the API with timestamp that which is
        // in the middle of the two first_error_time in events errors should count the error count from cursors and
        // return 2 errors
        long timestampBeforeOneOfTheEventsError = 1658735276;
        String eventsCursor = "repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursor);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampBeforeOneOfTheEventsError);
        assertCursorsStatus(stringEventErrorStatusInfoMap, 1, eventsCursor, 2);
    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampPositive3() throws SQLException {
        // in the setup I created 2 errors event with same cursor and excecute the API with timestamp that which
        //  first_error_time in events errors before the event errors then it should return empty array
        long timestampBeforeOneOfTheEventsError = 1658735270;
        String eventsCursor = "repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursor);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampBeforeOneOfTheEventsError);
        Assert.assertTrue(stringEventErrorStatusInfoMap.size() == 0);
    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampPositive4() throws SQLException {
        // in the setup I created 2 errors event with cursor and another error event with different cursor and
        // excecute the API with timestamp that which
        //  first_error_time in events errors after all the event errors then it should return 2 keys.
        long timestampBeforeOneOfTheEventsError = 1658735277;
        String eventsCursor = "repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor";
        String eventsCursor2 =
                "repo-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursor);
        operationKeys.add(eventsCursor2);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampBeforeOneOfTheEventsError);
        Assert.assertTrue(stringEventErrorStatusInfoMap.size() == 2);
        assertCursorsStatus(stringEventErrorStatusInfoMap, 2, eventsCursor, 4);
        assertCursorsStatus(stringEventErrorStatusInfoMap, 2, eventsCursor2, 2);
    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampPositive5() throws SQLException {
        // same as the positive4 test with two cursors
        long timestampBeforeOneOfTheEventsError = 1658735273;
        String eventsCursor = "repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor";
        String eventsCursor2 =
                "repo-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursor);
        operationKeys.add(eventsCursor2);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampBeforeOneOfTheEventsError);
        Assert.assertTrue(stringEventErrorStatusInfoMap.size() == 0);
    }

    @Test
    public void testCountEventErrorsByOperationKeyAndTimestampNegetive4() throws SQLException {
        // in the setup I created 2 errors event with cursor and another error event with different cursor and
        // excecute the API with cursors the not exist in any events errors should return empty array
        long timestampBeforeOneOfTheEventsError = 1658735277;
        // domain 10104 not exit in node event error
        String eventsCursorThatNotExist =
                "repo-federated_replicationhttp://docker.for.mac.localhost:10104/artifactory/repo-cursor";
        List<String> operationKeys = new ArrayList<>();
        operationKeys.add(eventsCursorThatNotExist);
        Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap =
                nodeEventErrorDao.countEventErrorsByOperationKey(operationKeys, timestampBeforeOneOfTheEventsError);
        Assert.assertTrue(stringEventErrorStatusInfoMap.size() == 0);
    }

    private void assertCursorsStatus(Map<String, EventErrorStatusInfo> stringEventErrorStatusInfoMap,
            long stringEventErrorStatusInfoMapSize, String eventCursor, long errorsCount) {
        Assert.assertTrue(stringEventErrorStatusInfoMap.size() == stringEventErrorStatusInfoMapSize);
        Assert.assertEquals(stringEventErrorStatusInfoMap.containsKey(eventCursor), true);
        Assert.assertEquals(stringEventErrorStatusInfoMap.get(eventCursor).getCountEventErrors(), errorsCount);
    }

}