package org.artifactory.logging.layout.request;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.artifactory.logging.SaasLogNames;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_GROUP;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_PACKAGE;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_KEY;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_TYPE;

/**
 * @author Tomer Slutsky
 */
class OutboundRequestJsonLayoutTest {
    OutboundRequestJsonLayout outboundRequestJsonLayout = new OutboundRequestJsonLayout();

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void testDoLayout() {
        MDC.put("uber-trace-id", "traceid");
        Map<String, Object> metadata = Map.of("auth", true, "host", "localhost");
        var timestamp = Instant.ofEpochMilli(System.currentTimeMillis()).toString();
        String s = outboundRequestJsonLayout.doLayout(
                LoggingTestUtils.createEvent(
                        Array.of(timestamp, "traceid", "remote-repo", "", "HEAD", "http://localhost:8081", "200",
                                1996, 2010, 25, metadata),
                        OutboundRequestJsonLayoutTest.class));
        Assertions.assertTrue(s.contains("\"" + SaasLogNames.OUTBOUND_REQUEST_LOG.getLogKey() + "\":\"" +
                SaasLogNames.OUTBOUND_REQUEST_LOG.getLogName() + "\""));
        Assertions.assertTrue(s.contains("\"traceid\":\"traceid\""));
        Assertions.assertTrue(s.contains("\"remote_repository\":\"remote-repo\""));
        Assertions.assertTrue(s.contains("\"path\":\"http://localhost:8081\""));
        Assertions.assertTrue(s.contains("\"user\":\"\""));
        Assertions.assertTrue(s.contains("\"method\":\"HEAD\""));
        Assertions.assertTrue(s.contains("\"request\":{"));
        Assertions.assertTrue(s.contains("\"response\":{"));
        Assertions.assertTrue(s.contains("\"datetime\":"));
        Assertions.assertTrue(s.contains("\"length\":2010"));
        Assertions.assertTrue(s.contains("\"length\":1996"));
        Assertions.assertTrue(s.contains("\"duration\":25"));
        Assertions.assertTrue(s.contains("\"auth\":true"));
        Assertions.assertTrue(s.contains("\"host\":\"localhost\""));
    }

    @Test
    void testContextualFields() throws JSONException {
        Map<String, Object> metadata = Map.of("auth", true, "host", "localhost");
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z", "traceid", "test-npm-remote",
                        "me@jfrog.com", "GET", "https://registry.npmjs.org/accepts", "200", 0, 109612, 157, metadata),
                RequestJsonLayoutTest.class, Level.INFO,
                Map.of(ACTIVITY_PROP_GROUP, "download",
                        ACTIVITY_PROP_PACKAGE, "npm",
                        ACTIVITY_PROP_REPO_KEY, "test-npm-virtual",
                        ACTIVITY_PROP_REPO_TYPE, "virtual")
        );
        String actual = outboundRequestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                   "log_name": "artifactory-request-out.log",
                   "request": {
                     "user": "me@jfrog.com",
                     "method": "GET",
                     "path": "https://registry.npmjs.org/accepts",
                     "length": 0,
                     "remote_repository": "test-npm-remote"
                   },
                   "response": {
                     "datetime": "2024-05-13T11:44:08.739Z",
                     "length": 109612,
                     "status": "200",
                     "duration": 157
                   },
                   "package": "npm",
                   "group": "download",
                   "repo": "test-npm-virtual",
                   "repo_type": "v",
                   "auth": true,
                   "host": "localhost"
                }
                """, actual, true);
    }
}