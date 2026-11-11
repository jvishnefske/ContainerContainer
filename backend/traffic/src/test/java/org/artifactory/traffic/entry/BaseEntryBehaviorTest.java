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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.format.DateTimeParseException;

/**
 * Unit test for the behavior of the traffic's BaseEntry object
 *
 * @author Noam Tenne
 */
@Test
public class BaseEntryBehaviorTest {

    /**
     * Try to create a download entry with a null expression
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullDownloadEntry() {
        new DownloadEntry(null);
    }

    /**
     * Try to create an upload entry with an empty expression
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyUploadEntry() {
        new UploadEntry("");
    }

    /**
     * Try to create a request entry with an expression that contains no data
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoDataRequestEntry() {
        new RequestEntry("|||");
    }

    /**
     * Try to create a download entry with an expression that doesn't have enough columns
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testShortDownloadEntry() {
        new DownloadEntry(
                "2019-08-13T15:53:15.995Z|null|0:0:0:0:0:0:0:1|admin|GET|/jcenter-cache/org/apache/apache/16/apache" +
                        "-16.pom|200|-1|15397|179");
    }

    /**
     * Try to create an upload entry with an expression that has too many columns
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testLongUploadEntry() {
        new UploadEntry(
                "2019-08-13T15:53:15.995Z|null|0:0:0:0:0:0:0:1|admin|GET|/jcenter-cache/org/apache/apache/16/apache" +
                        "-16.pom|200|-1|15397|179|curl/7.54.0|toomuch");
    }

    /**
     * Try to create a request entry that contains an invalid date
     */
    @Test(expectedExceptions = DateTimeParseException.class)
    public void testInvalidRequestEntryDate() {
        new RequestEntry(
                "20090318162747|null|0:0:0:0:0:0:0:1|admin|GET|/jcenter-cache/org/apache/apache/16/apache-16" +
                        ".pom|200|-1|15397|179|curl/7.54.0");
    }

    /**
     * Try to create a download entry that contains a date in an invalid format
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidDownloadEntryDateFormat() {
        new DownloadEntry(
                "2019-08-13T15:52:17|null|0:0:0:0:0:0:0:1|admin|GET|/jcenter-cache/org/apache/apache/16/apache-16" +
                        ".pom|200|-1|15397|179|curl/7.54.0");
    }

    /**
     * Try to create a download entry that contains a date in an invalid format
     */
    @Test
    public void testValidRequestEntry() {
        RequestEntry entry = new RequestEntry(
                "2019-08-13T15:53:15.995Z|null|0:0:0:0:0:0:0:1|admin|GET|/jcenter-cache/org/apache/apache/16/apache" +
                        "-16.pom|200|-1|15397|179|curl/7.54.0");
        Assert.assertEquals(entry.getFormattedDate(), "2019-08-13T15:53:15.995Z");
    }
}
