package org.artifactory.logging.layout.request;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.vavr.collection.Array;
import org.artifactory.logging.LoggingTestUtils;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.MDC;

import java.util.Map;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_GROUP;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_PACKAGE;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_KEY;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_TYPE;

/**
 * @author Tomer Slutsky
 */
class RequestJsonLayoutTest {
    RequestJsonLayout requestJsonLayout = new RequestJsonLayout();

    @BeforeEach
    void setUp() {
        // The following value does NOT match the actual value of the trace-id. We put
        // it here on purpose to make sure that the layout takes the value from the
        // event object and not from the MDC.
        MDC.put("uber-trace-id", "fake-value");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testDoLayout() throws JSONException {
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z",
                        "traceid", "127.0.0.1", "", "HEAD", "http://localhost:8081", "200", 1996, 2010, 25, "savior",
                        Map.of("ttfb", 25,
                                "xid", "123e4567-e89b-12d3-a456-426614174000")),
                RequestJsonLayoutTest.class);
        String actual = requestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-request.log",
                  "request": {
                    "user": "",
                    "method": "HEAD",
                    "path": "http://localhost:8081",
                    "length": 1996,
                    "client": "127.0.0.1",
                    "useragent": "savior",
                    "traceid": "traceid"
                  },
                  "response": {
                    "datetime": "2024-05-13T11:44:08.739Z",
                    "length": 2010,
                    "status": "200",
                    "duration": 25
                  },
                  "ttfb": 25,
                  "xid": "123e4567-e89b-12d3-a456-426614174000"
                }
                """, actual, true);
    }

    @Test
    void testContextualFields() throws JSONException {
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z",
                        "traceid", "127.0.0.1", "", "HEAD", "http://localhost:8081", "200", 1996, 2010, 25, "savior"),
                RequestJsonLayoutTest.class, Level.INFO,
                Map.of("uber-trace-id", "traceid",
                        ACTIVITY_PROP_GROUP, "download",
                        ACTIVITY_PROP_PACKAGE, "maven",
                        "xid", "123e4567-e89b-12d3-a456-426614174000")
        );
        String actual = requestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-request.log",
                  "request": {
                    "user": "",
                    "method": "HEAD",
                    "path": "http://localhost:8081",
                    "length": 1996,
                    "client": "127.0.0.1",
                    "useragent": "savior",
                    "traceid":"traceid"
                  },
                  "response": {
                    "datetime": "2024-05-13T11:44:08.739Z",
                    "length": 2010,
                    "status": "200",
                    "duration": 25
                  },
                  "package": "maven",
                  "group": "download",
                  "xid": "123e4567-e89b-12d3-a456-426614174000"
                }
                """, actual, true);
    }

    @Test
    void doLayout_shouldIncludeRepoKeyInJson_whenMdcHasRepoKey() throws JSONException {
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z",
                        "traceid", "127.0.0.1", "", "GET", "/artifactory/my-npm-repo/package.tgz", "200",
                        0, 1024, 50, "curl/7.64.0"),
                RequestJsonLayoutTest.class, Level.INFO,
                Map.of("uber-trace-id", "traceid", ACTIVITY_PROP_REPO_KEY, "my-npm-repo"));
        String actual = requestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-request.log",
                  "request": {
                    "user": "",
                    "method": "GET",
                    "path": "/artifactory/my-npm-repo/package.tgz",
                    "length": 0,
                    "client": "127.0.0.1",
                    "useragent": "curl/7.64.0",
                    "traceid": "traceid"
                  },
                  "response": {
                    "datetime": "2024-05-13T11:44:08.739Z",
                    "length": 1024,
                    "status": "200",
                    "duration": 50
                  },
                  "repo": "my-npm-repo"
                }
                """, actual, true);
    }

    @ParameterizedTest
    @CsvSource({
            "remote, r",
            "virtual, v",
            "local, l"
    })
    void doLayout_shouldIncludeRepoTypeFirstCharInJson_whenMdcHasRepoType(String repoTypeValue, String expectedChar)
            throws JSONException {
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z",
                        "traceid", "127.0.0.1", "", "GET", "/artifactory/repo/path", "200", 0, 100, 10, "agent"),
                RequestJsonLayoutTest.class, Level.INFO,
                Map.of("uber-trace-id", "traceid",
                        ACTIVITY_PROP_REPO_KEY, "some-repo",
                        ACTIVITY_PROP_REPO_TYPE, repoTypeValue));
        String actual = requestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-request.log",
                  "request": {
                    "user": "",
                    "method": "GET",
                    "path": "/artifactory/repo/path",
                    "length": 0,
                    "client": "127.0.0.1",
                    "useragent": "agent",
                    "traceid": "traceid"
                  },
                  "response": {
                    "datetime": "2024-05-13T11:44:08.739Z",
                    "length": 100,
                    "status": "200",
                    "duration": 10
                  },
                  "repo": "some-repo",
                  "repo_type": "%s"
                }
                """.formatted(expectedChar), actual, true);
    }

    @Test
    void doLayout_shouldOmitRepoAndRepoTypeFromJson_whenMdcHasNoRepoKeyOrRepoType() throws JSONException {
        LoggingEvent event = LoggingTestUtils.createEvent(
                Array.of("2024-05-13T11:44:08.739Z",
                        "traceid", "127.0.0.1", "", "GET", "/api/system/ping", "200", 0, 0, 5, "agent"),
                RequestJsonLayoutTest.class, Level.INFO,
                Map.of("uber-trace-id", "traceid"));
        String actual = requestJsonLayout.doLayout(event);
        JSONAssert.assertEquals("""
                {
                  "log_name": "artifactory-request.log",
                  "request": {
                    "user": "",
                    "method": "GET",
                    "path": "/api/system/ping",
                    "length": 0,
                    "client": "127.0.0.1",
                    "useragent": "agent",
                    "traceid": "traceid"
                  },
                  "response": {
                    "datetime": "2024-05-13T11:44:08.739Z",
                    "length": 0,
                    "status": "200",
                    "duration": 5
                  }
                }
                """, actual, true);
    }
}