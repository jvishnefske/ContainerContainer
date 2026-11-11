/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.traffic.entry;

import org.artifactory.traffic.TrafficAction;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Unit test for the traffic's RequestEntry object
 *
 * @author Noam Tenne
 */
@Test
public class RequestEntryTest {

    @BeforeMethod
    public void beforeMethod() {
        MDC.remove("uber-trace-id");
    }

    @AfterMethod
    public void afterMethod() {
        MDC.remove("uber-trace-id");
    }
    /**
     * Creates a new entry with the data constructor, and compares it's string representation with it's equivalent entry
     * Expression
     */
    public void requestEntryToString() {
        String userAddress = "127.0.0.1";
        String username = "admin";
        String method = "GET";
        String path = "/remote-repos/antlr/antlr/2.7.6/antlr-2.7.6.jar";
        int returnCode = 200;
        int resContentLength = 443432;
        int reqContentLength = 0;
        String userAgent = "Ari Gold";
        RequestEntry requestEntry = new RequestEntry(userAddress, username, method,
                path, returnCode, resContentLength, reqContentLength, 10, userAgent);

        assertEquals(requestEntry.getDuration(), 10, "Duration should be equal");
        assertEquals(requestEntry.getAction(), TrafficAction.REQUEST, "Entry action should be equal");
        assertEquals(requestEntry.getUserAddress(), userAddress, "Entry user addresses should be equal");
        assertEquals(requestEntry.getUsername(), username, "Entry usernames should be equal");
        assertEquals(requestEntry.getMethod(), method, "Entry methods should be equal");
        assertEquals(requestEntry.getPath(), path, "Entry paths should be equal");
        assertEquals(requestEntry.getReturnCode(), returnCode, "Entry return codes should be equal");
        assertEquals(requestEntry.getReqContentLength(), reqContentLength, "Entry return codes should be equal");
        assertEquals(requestEntry.getResContentLength(), resContentLength, "Entry return codes should be equal");
        assertEquals(requestEntry.getUserAgent(), userAgent, "Entry return codes should be equal");

        String expected = requestEntry.getFormattedDate()
                + "|null|127.0.0.1|admin|GET|/remote-repos/antlr/antlr/2.7.6/antlr-2.7.6.jar|200|0|443432|10|Ari Gold";

        assertEquals(requestEntry.toString(), expected, "Entry expressions should be equal");
    }

    /**
     * Creates a new entry using an entry expression, and validates it
     */
    public void stringToRequestEntry() {
        MDC.put("uber-trace-id", "myTraceId");
        String entry =
                "2019-08-13T15:53:15.995Z|myTraceId|127.0.0.1|admin|GET|/repo/path/artifact|200|-1|15397|179|curl/7" +
                        ".54.0";
        RequestEntry requestEntry = new RequestEntry(entry);
        assertEquals(entry, requestEntry.toString(), "Entry expressions should be equal");
        MDC.remove("uber-trace-id");
        assertEquals(requestEntry.getUserAddress(), "127.0.0.1", "Entry user addresses should be equal");
        assertEquals(requestEntry.getUsername(), "admin", "Entry usernames should be equal");
        assertEquals(requestEntry.getMethod(), "GET", "Entry methods should be equal");
        assertEquals(requestEntry.getPath(), "/repo/path/artifact", "Entry paths should be equal");
        assertEquals(requestEntry.getReturnCode(), 200, "Entry return codes should be equal");
        assertEquals(requestEntry.getReqContentLength(), -1, "Entry return codes should be equal");
        assertEquals(requestEntry.getResContentLength(), 15397, "Entry return codes should be equal");
        assertEquals(requestEntry.getDuration(), 179, "Duration should be equal");
        assertEquals(requestEntry.getUserAgent(), "curl/7.54.0", "Entry return codes should be equal");
        assertEquals(requestEntry.getAction(), TrafficAction.REQUEST, "Entry action should be equal");
    }

    /**
     * Verifies the formatter also includes millis when it's 000.
     */
    public void zeroMillis() {
        MDC.put("uber-trace-id", "myTraceId");
        String entry =
                "2019-08-13T15:53:15.000Z|myTraceId|127.0.0.1|admin|GET|/repo/path/artifact|200|-1|15397|179|curl/7" +
                        ".54.0";
        RequestEntry requestEntry = new RequestEntry(entry);
        assertEquals(entry, requestEntry.toString(), "Entry expressions should be equal");
    }

    /**
     * Creates a new entry using an entry expression with an invalid content length
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidReturnCode() {
        new UploadEntry("20090319110249|REQUEST|127.0.0.1|admin|GET|/remote-repos/antlr/antlr/2.7.6/antlr-2.7.6.jar|" +
                "HTTP/1.1|2a00z|443432");
    }

    /**
     * Creates a new entry using an entry expression with an invalid content length
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidContentLength() {
        new UploadEntry("20090319110249|REQUEST|127.0.0.1|admin|GET|/remote-repos/antlr/antlr/2.7.6/antlr-2.7.6.jar|" +
                "HTTP/1.1|200|4434a3a2");
    }
}
