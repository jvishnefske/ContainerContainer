package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * Covers verification checklist items #11–#24 (param parsing + validation prefixes), at the
 * resource layer. Every error path asserts the byte-stable agent-branching prefix.
 */
public class RequestLogQueryParamParserTest {

    @Test
    public void parsesAllParameters() {
        SearchCriteria c = RequestLogQueryParamParser.parse(
                "1000", "2000", "50",
                "npm-remote,maven-remote",
                "foo bar", "true",
                "50", "10", null);
        assertEquals((long) c.timeFrom(), 1000L);
        assertEquals((long) c.timeTo(), 2000L);
        assertEquals(c.minDurationMs(), 50L);
        assertEquals(c.repoNames().size(), 2);
        assertEquals(c.searchQuery(), "foo&bar");  // AND mode -> '&'
        assertEquals(c.limit(), 50);
        assertEquals(c.offset(), 10);
        assertNull(c.cursor());
    }

    @Test
    public void blankInputsYieldDefaults() {
        SearchCriteria c = RequestLogQueryParamParser.parse(
                null, null, null, null, null, null, null, null, null);
        assertNull(c.timeFrom());
        assertNull(c.timeTo());
        assertEquals(c.minDurationMs(), 0L);
        assertEquals(c.repoNames().size(), 0);
        assertNull(c.searchQuery());
        assertEquals(c.limit(), RequestLogQueryParamParser.DEFAULT_LIMIT);
        assertEquals(c.offset(), 0);
    }

    @Test
    public void limitOutOfRangeThrowsPrefix() {
        for (String bad : new String[]{"0", "-1", "2001", "100000"}) {
            RequestLogException ex = expectThrows(RequestLogException.class,
                    () -> RequestLogQueryParamParser.parse(
                            null, null, null, null, null, null, bad, null, null));
            assertEquals(ex.getStatusCode(), 400);
            assertTrue(ex.getMessage().startsWith("'limit' must be between 1 and 2000"),
                    "got: " + ex.getMessage());
        }
    }

    @Test
    public void offsetAboveCapThrowsCursorPrefix() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> RequestLogQueryParamParser.parse(
                        null, null, null, null, null, null, "50", "10001", null));
        assertTrue(ex.getMessage().startsWith("'offset' exceeds maximum 10000"));
    }

    @Test
    public void cursorMalformedThrowsPrefix() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> RequestLogQueryParamParser.parse(
                        null, null, null, null, null, null, null, null, "not-base64!!!"));
        assertTrue(ex.getMessage().startsWith("'cursor' is malformed"));
    }

    @Test
    public void matchAllInvalidThrowsPrefix() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> RequestLogQueryParamParser.parse(
                        null, null, null, null, null, "yes", null, null, null));
        assertTrue(ex.getMessage().startsWith("'match_all' must be true or false"));
    }

    @Test
    public void searchTooLongThrowsPrefix() {
        String tooLong = "a".repeat(257);
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> RequestLogQueryParamParser.parse(
                        null, null, null, null, tooLong, null, null, null, null));
        assertTrue(ex.getMessage().startsWith("'search' length exceeds 256"));
    }

    @Test
    public void timeFromMustParse() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> RequestLogQueryParamParser.parse(
                        "not-a-number", null, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("'time_from' must be an integer"));
    }

    @Test
    public void emptyCsvProducesEmptyList() {
        SearchCriteria c = RequestLogQueryParamParser.parse(
                null, null, null, ", ,", null, null, null, null, null);
        assertNotNull(c.repoNames());
        assertEquals(c.repoNames().size(), 0);
    }
}
