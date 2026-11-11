package org.artifactory.storage.db.support.database.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseActivityTest {

    private DatabaseActivity databaseActivity;

    @Test
    void testDatabaseStatisticsActivityDao() {
        databaseActivity = new DatabaseActivity(116, "00:05:00", "artifactory",
                "active","Client", "ClientRead",
                "select timestamp, event_type, path, hints, temp_event_id from node_events_temp order by timestamp, " +
                        "temp_event_id limit  1000 ;");
        assertEquals(116, databaseActivity.getPid());
        assertEquals("00:05:00", databaseActivity.getAge());
        assertEquals("artifactory", databaseActivity.getUserName());
        assertEquals("active", databaseActivity.getState());
        assertEquals("Client", databaseActivity.getWaitEventType());
        assertEquals("ClientRead", databaseActivity.getWaitEvent());
        assertEquals(
                "select timestamp, event_type, path, hints, temp_event_id from node_events_temp order by timestamp, " +
                        "temp_event_id limit  1000 ;",
                databaseActivity.getQuery());
    }
}