package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.NodeEventsAndNodeEventCursorDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.EventsTypesStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

@Test
public class EventsDaoAndCursorsDaoEventTypesTest extends DbBaseTest {

    @Autowired
    private NodeEventsAndNodeEventCursorDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/federated_status/events_types_status.sql");
    }

    @AfterMethod
    @Override
    public void unbindArtifactoryHome() {
        importSql("/sql/federated_status/events_types_status_delete.sql");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    private void assertEventsTypesStatus(EventsTypesStatusInfo eventsTypesStatus, String expectedStatus,
            long createEvents, long updateEvents,
            long deleteEvents, long propsEvents, long eventRegistrationTimeStamp, long currentTimeStamp) {
        Assert.assertEquals(eventsTypesStatus.getStatus(), expectedStatus);
        Assert.assertEquals(eventsTypesStatus.getCreateEvents(), createEvents);
        Assert.assertEquals(eventsTypesStatus.getUpdateEvents(), updateEvents);
        Assert.assertEquals(eventsTypesStatus.getDeleteEvents(), deleteEvents);
        Assert.assertEquals(eventsTypesStatus.getPropsEvents(), propsEvents);
        Assert.assertEquals(eventsTypesStatus.getEventRegistrationTimeStamp(), eventRegistrationTimeStamp);
        Assert.assertEquals(eventsTypesStatus.getLagInMS(), currentTimeStamp - eventRegistrationTimeStamp);
    }

    @Test
    public void testGetMirrorEventsStatus() throws SQLException {
        long currentTimeStamp = System.currentTimeMillis();
        List<EventsTypesStatusInfo> eventsTypesStatus = dao.getEventsTypesStatus("fed1", currentTimeStamp);
        Assert.assertTrue(eventsTypesStatus.size() > 0);
        assertEventsTypesStatus(eventsTypesStatus.get(0), "HEALTHY", 2, 1, 1, 0, 1658735274, currentTimeStamp);
        // test calculation of event for each type and test the result sorted by lag time
        assertEventsTypesStatus(eventsTypesStatus.get(1), "HEALTHY", 1, 1, 1, 0, 1658735275, currentTimeStamp);
        // test that if there is no next event to cursor it will push it in the end of the list
        assertEventsTypesStatus(eventsTypesStatus.get(2), "HEALTHY", 0, 0, 0, 0, 0, 0);
    }

    // fed2 has 'delete' events with both federation and evidence hints, verify that the evidence hint is not breaking
    @Test
    public void testGetMirrorEventsStatusWithEvidence() throws SQLException {
        long currentTimeStamp = System.currentTimeMillis();
        List<EventsTypesStatusInfo> eventsTypesStatus = dao.getEventsTypesStatus("fed2", currentTimeStamp);
        Assert.assertTrue(eventsTypesStatus.size() > 0);
        assertEventsTypesStatus(eventsTypesStatus.get(0), "HEALTHY", 0, 0, 2, 0, 0, 0);
    }

    @Test
    public void testMirrorEventsStatusReturnsEmptyForOutOfHEALTHYCursors() throws SQLException {
        Long currentTimeStamp = System.currentTimeMillis();
        List<EventsTypesStatusInfo> eventsTypesStatus =
                dao.getEventsTypesStatus("out-of-sync", currentTimeStamp);
        Assert.assertEquals(eventsTypesStatus.size(), 2);
        assertEventsTypesStatus(eventsTypesStatus.get(0), "OUT_OF_SYNC", 1, 0, 0, 0, 1658735275, currentTimeStamp);
        assertEventsTypesStatus(eventsTypesStatus.get(1), "HEALTHY", 0, 0, 0, 0, 0, 0);
    }

    @Test
    public void testMirrorEventsStatusEmptyForNonFederatedCursors() throws SQLException {
        List<EventsTypesStatusInfo> eventsTypesStatus =
                dao.getEventsTypesStatus("non-federated", System.currentTimeMillis());
        Assert.assertEquals(eventsTypesStatus.size(), 0);
    }

    @Test
    public void testMirrorEventsStatusEmptyWhereHintNotNull() throws SQLException {
        List<EventsTypesStatusInfo> eventsTypesStatus =
                dao.getEventsTypesStatus("hint-not-null", System.currentTimeMillis());
        Assert.assertTrue(eventsTypesStatus.size() > 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getCreateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getUpdateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getDeleteEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getPropsEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getEventRegistrationTimeStamp(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getLagInMS(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getStatus(), "HEALTHY");
    }

    @Test
    public void testMirrorEventsStatusEmptyWhereNoMatchingEvents() throws SQLException {
        List<EventsTypesStatusInfo> eventsTypesStatus =
                dao.getEventsTypesStatus("no-match", System.currentTimeMillis());
        Assert.assertTrue(eventsTypesStatus.size() > 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getCreateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getUpdateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getDeleteEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getPropsEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getEventRegistrationTimeStamp(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getLagInMS(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getStatus(), "HEALTHY");
    }

    public void testMirrorEventsStatusEmptyIfAllEventsAreAfterTimeOfQuery() throws SQLException {
        List<EventsTypesStatusInfo> eventsTypesStatus = dao.getEventsTypesStatus("events-after", 1658735274);
        Assert.assertTrue(eventsTypesStatus.size() > 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getCreateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getUpdateEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getDeleteEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getPropsEvents(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getEventRegistrationTimeStamp(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getLagInMS(), 0);
        Assert.assertEquals(eventsTypesStatus.get(0).getStatus(), "HEALTHY");
    }
}
