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

import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.traffic.TrafficAction;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit test for the TrafficEntryBuilder object
 *
 * @author Noam Tenne
 */
@Test
public class LegacyTrafficEntryBuilderTest {

    /**
     * Create an entry with an expression using the entry builder
     */
    public void testValidEntry() throws Exception {
        String entry = "20050318162747|10|UPLOAD|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|456";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        assertNotNull(trafficEntry, "Entry should not be null");
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD, "Entry action should be - upload");
    }

    /**
     * Create an entry with an expression using the entry builder
     */
    public void testValidEntryWithTraceId() throws Exception {
        String entry =
                "20050318162747|1234567890abcdef|11|REDIRECT|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2" +
                        ".7.7.pom|456";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        assertNotNull(trafficEntry, "Entry should not be null");
        assertEquals(trafficEntry.getAction(), TrafficAction.REDIRECT, "Entry action should be redirect");
        assertEquals(trafficEntry.getDuration(), 11, "Duration should be equal");
        assertTrue(trafficEntry instanceof RedirectEntry);
        RedirectEntry redirectEntry = (RedirectEntry) trafficEntry;
        assertEquals(redirectEntry.getRepoPath(),
                InfoFactoryHolder.get()
                        .createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom").getId(),
                "Entry repo paths should be equal");
        assertEquals(redirectEntry.getContentLength(), 456, "Entry content lengths should be equal");
        assertEquals(redirectEntry.getUserAddress(), "127.0.0.1", "Entry user address should be equal");
        assertEquals(redirectEntry.getTraceId(), "1234567890abcdef", "Trace ID should be equal");
    }

    /**
     * Create an entry with an expression using the entry builder
     */
    public void testEntryWithNoIp() throws Exception {
        String entry =
                "20050318162747|1234567890abcdef|11|UPLOAD||libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|0";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        assertNotNull(trafficEntry, "Entry should not be null");
        assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD, "Entry action should be upload");
        assertEquals(trafficEntry.getDuration(), 11, "Duration should be equal");
        assertTrue(trafficEntry instanceof UploadEntry);
        UploadEntry uploadEntry = (UploadEntry) trafficEntry;
        assertEquals(uploadEntry.getRepoPath(),
                InfoFactoryHolder.get()
                        .createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom").getId(),
                "Entry repo paths should be equal");
        assertEquals(uploadEntry.getContentLength(), 0, "Entry content lengths should be equal");
        assertEquals(uploadEntry.getUserAddress(), "", "Entry user address should be equal");
        assertEquals(uploadEntry.getTraceId(), "1234567890abcdef", "Trace ID should be equal");
        assertEquals(uploadEntry.toString(), entry);
    }

    /**
     * Create an entry with a null expression
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullEntry() throws Exception {
        TokenizedTrafficEntryFactory.newTrafficEntry(null);
    }

    /**
     * Create an entry with an empty expression
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyEntry() throws Exception {
        TokenizedTrafficEntryFactory.newTrafficEntry("");
    }

    /**
     * Create an entry with an expression that has no data
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEntryNoData() throws Exception {
        TokenizedTrafficEntryFactory.newTrafficEntry("|");
    }


    /**
     * test malformed (sliced) entries caused by newline
     */
    @Test(expectedExceptions = Exception.class, dataProvider = "slicedEntries")
    public void testEntryNoData(String entry) throws Exception {
        TokenizedTrafficEntryFactory.newTrafficEntry(entry);
    }

    @DataProvider
    public Object[][] slicedEntries() {
        return new Object[][] {
                {"20050318162747|10|UPLOAD|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.po"},
                {"20050318162747|10|UPLOAD|127.0.0.1|libs-releases-local:antlr/antlr/2."},
                {"20050318162747|10|UPLOAD|127.0.0.1|li"},
        };
    }


}
