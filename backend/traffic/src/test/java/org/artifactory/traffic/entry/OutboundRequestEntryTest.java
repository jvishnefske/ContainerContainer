package org.artifactory.traffic.entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.artifactory.traffic.entry.TokenizedTrafficEntry.COLUMN_SEPARATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
class OutboundRequestEntryTest {

    @ParameterizedTest
    @MethodSource("testOutboundRequestEntryToStringDataProvider")
    void testOutboundRequestEntryToString(OutboundRequestEntry outboundRequestEntry, String expectedString) {
        assertTrue(outboundRequestEntry.toString().endsWith(expectedString));
    }

    /**
     * Verifies the formatter also includes millis when it's 000.
     */
    @Test
    void zeroMillis() {
        MDC.put("uber-trace-id", "myTraceId");
        OutboundRequestEntry outboundRequestEntry = OutboundRequestEntry.builder().repoName("cool-repo")
                .userName("admin").method("GET")
                .url("http://cool.com/").statusCode(200).requestContentLength(10)
                .responseContentLength(50).duration(324).build();
        ReflectionTestUtils.setField(outboundRequestEntry, "currentTime", 1663575171000L);
        String expectedEntry = "2022-09-19T08:12:51.000Z" + COLUMN_SEPARATOR + "myTraceId" + COLUMN_SEPARATOR +
                "cool-repo" + COLUMN_SEPARATOR + "admin" + COLUMN_SEPARATOR + "GET" +
                COLUMN_SEPARATOR + "http://cool.com/" + COLUMN_SEPARATOR + "200" +
                COLUMN_SEPARATOR + "10" + COLUMN_SEPARATOR + "50" + COLUMN_SEPARATOR + "324";
        assertEquals(expectedEntry, outboundRequestEntry.toString());
    }

    private static Object[][] testOutboundRequestEntryToStringDataProvider() {
        return new Object[][] {
                {OutboundRequestEntry.builder().repoName("cool-repo").userName("cooluser").method("GET")
                        .url("http://cool.com/").statusCode(200).requestContentLength(10)
                        .responseContentLength(50).duration(324).build(),
                        COLUMN_SEPARATOR + "cool-repo" + COLUMN_SEPARATOR + "cooluser" + COLUMN_SEPARATOR + "GET" +
                                COLUMN_SEPARATOR + "http://cool.com/" + COLUMN_SEPARATOR + "200" +
                                COLUMN_SEPARATOR + "10" + COLUMN_SEPARATOR + "50" + COLUMN_SEPARATOR + "324"},
                {OutboundRequestEntry.builder().repoName("cool-repo").userName(null).method("HEAD")
                        .url("http://cool.com/cool/page").statusCode(404).requestContentLength(0)
                        .responseContentLength(0).duration(3242).build(),
                        COLUMN_SEPARATOR + "cool-repo" + COLUMN_SEPARATOR + "" + COLUMN_SEPARATOR + "HEAD" +
                                COLUMN_SEPARATOR + "http://cool.com/cool/page" + COLUMN_SEPARATOR + "404" +
                                COLUMN_SEPARATOR + "0" + COLUMN_SEPARATOR + "0" + COLUMN_SEPARATOR + "3242"}
        };
    }
}
