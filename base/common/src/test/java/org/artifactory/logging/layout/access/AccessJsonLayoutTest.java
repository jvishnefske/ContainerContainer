package org.artifactory.logging.layout.access;

import ch.qos.logback.classic.spi.LoggingEvent;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * @author Tomer Slutsky
 */
class AccessJsonLayoutTest {
    AccessJsonLayout accessJsonLayout = new AccessJsonLayout();

    @Test
    void accessJsonLayout_shouldLogAllFields() throws JSONException {
        LoggingEvent event = LoggingTestUtils
                .createEvent(Array.of("ACCEPTED", "LOGIN", "path", "msg", "jffe@000", "127.0.0.71", "token"),
                        AccessJsonLayoutTest.class);
        event.setTimeStamp(1716527713000L);

        String actual = accessJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-access.log",
                  "access": {
                    "datetime": "2024-05-24T05:15:13.000Z",
                    "traceid": "traceid",
                    "action": "LOGIN",
                    "response": "ACCEPTED",
                    "path": "path",
                    "msg": "msg",
                    "username": "jffe@000",
                    "userip": "127.0.0.71",
                    "type": "token"
                  }
                }
                """, actual, true);
    }

    @Test
    void accessJsonLayout_shouldOmitEmptyFields() throws JSONException {
        LoggingEvent event = LoggingTestUtils
                .createEvent(Array.of("ACCEPTED", "LOGIN", "", "", "jffe@000", "127.0.0.71", ""),
                        AccessJsonLayoutTest.class);
        event.setTimeStamp(1716527713000L);

        String actual = accessJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-access.log",
                  "access": {
                    "datetime": "2024-05-24T05:15:13.000Z",
                    "traceid": "traceid",
                    "action": "LOGIN",
                    "response": "ACCEPTED",
                    "username": "jffe@000",
                    "userip": "127.0.0.71"
                  }
                }
                """, actual, true);
    }
}