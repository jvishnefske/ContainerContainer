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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.traffic.TrafficAction;
import org.slf4j.MDC;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.artifactory.traffic.entry.TokenizedTrafficEntry.COLUMN_SEPARATOR;
import static org.testng.Assert.assertEquals;

/**
 * Unit test for the traffic's TransferEntry object
 *
 * @author Noam Tenne
 */
@Test
public class TransferEntryTest {
    private static final String TRACE_ID = "620a731101f1fe78";

    @BeforeClass
    public void setup() {
        MDC.put("uber-trace-id", TRACE_ID);
    }

    /**
     * Creates a new entry with the data constructor, and compares it's string representation with it's equivalent entry
     * Expression
     */
    public void uploadEntryToString() {
        RepoPath repoPath = InfoFactoryHolder.get()
                .createRepoPathFromId("libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom");
        int contentLength = 632;
        String clientRemoteAddress = "127.0.0.1";
        UploadEntry uploadEntry = new UploadEntry(repoPath.getId(), contentLength, 10, clientRemoteAddress);
        String expected = uploadEntry.getFormattedDate() + "|" + TRACE_ID
                + "|10|UPLOAD|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|632";
        assertEntry(uploadEntry, expected, 10, TrafficAction.UPLOAD, repoPath.getId(), contentLength,
                clientRemoteAddress, TRACE_ID);
    }

    /**
     * Creates a new entry using an entry expression, and validates it
     */
    public void stringToUploadEntry() {
        String entry =
                "20050318162747|1234567890abcdef|10|UPLOAD|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7" +
                        ".7.pom|456";
        UploadEntry uploadEntry = new UploadEntry(entry);
        assertEntry(uploadEntry, entry, 10, TrafficAction.UPLOAD,
                InfoFactoryHolder.get().createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom")
                        .getId(), 456, "127.0.0.1", "1234567890abcdef");
    }

    public void stringToUploadEntryWithNoIP() {
        String entry =
                "20050318162747|1234567890abcdef|10|UPLOAD||libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|0";
        UploadEntry uploadEntry = new UploadEntry(entry);
        assertEntry(uploadEntry, entry, 10, TrafficAction.UPLOAD,
                InfoFactoryHolder.get().createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom")
                        .getId(), 0, "", "1234567890abcdef");
    }

    /**
     * Creates a new entry "deprecatedstyle" using an entry expression, and validates it
     */
    public void deprecatedUploadNoTraceIdEntry() {
        String entryPrefix = "20050318162747|";
        String entrySuffix = "10|UPLOAD|10.20.30.40|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|456";
        UploadEntry uploadEntry = new UploadEntry(entryPrefix + entrySuffix);

        String expectedEntry = entryPrefix + "|" + entrySuffix; // toString adds the extra pipe to the entry

        assertEntry(uploadEntry, expectedEntry, 10, TrafficAction.UPLOAD,
                InfoFactoryHolder.get().createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom")
                        .getId(), 456, "10.20.30.40", "");
    }

    public void deprecatedUploadNoTraceIdNoIPEntry() {
        String entryPrefix = "20050318162747|";
        String entrySuffix = "10|UPLOAD||libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|0";
        UploadEntry uploadEntry = new UploadEntry(entryPrefix + entrySuffix);

        String expectedEntry = entryPrefix + "|" + entrySuffix; // toString adds the extra pipe to the entry

        assertEntry(uploadEntry, expectedEntry, 10, TrafficAction.UPLOAD,
                InfoFactoryHolder.get().createRepoPath("libs-releases-local", "antlr/antlr/2.7.7/antlr-2.7.7.pom")
                        .getId(), 0, "", "");
    }

    public void testDownloadEntry() {
        RepoPathImpl repoPath = new RepoPathImpl("my-test-repo", "path/to/repo");
        String remoteAddress = "127.0.0.1";
        DownloadEntry downloadEntry = new DownloadEntry(repoPath.getId(), 10L, 1000L, remoteAddress, "");

        String[] downloadEntryParts = StringUtils.split(downloadEntry.toString(), COLUMN_SEPARATOR);
        assertEquals(downloadEntryParts.length, 7);
        assertEquals(downloadEntryParts[2], "1000");
        assertEquals(downloadEntryParts[3], TrafficAction.DOWNLOAD.name());
        assertEquals(downloadEntryParts[4], remoteAddress);
        assertEquals(downloadEntryParts[5], repoPath.getId());
        assertEquals(downloadEntryParts[6], "10");
    }

    public void testValidDeprecatedDownloadNoTraceIdEntry() {
        String entryPrefix = "20200518110747|";
        String entrySuffix = "1|DOWNLOAD|192.168.1.5|docker-local:ubuntu/latest/manifest.json|1152";

        DownloadEntry downloadEntry = new DownloadEntry(entryPrefix + entrySuffix);

        String expectedEntry = entryPrefix + "|" + entrySuffix; // toString adds the extra pipe to the entry
        assertEntry(downloadEntry, expectedEntry, 1, TrafficAction.DOWNLOAD,
                InfoFactoryHolder.get().createRepoPath("docker-local", "ubuntu/latest/manifest.json").getId(),
                1152, "192.168.1.5", "");
    }

    public void testValidDownloadEntry() {
        String entryPrefix = "20200518144028|";
        String entrySuffix = "fa77243b29c57219|0|DOWNLOAD|192.168.1.5|docker-local:ubuntu/latest/manifest.json|1152";

        DownloadEntry downloadEntry = new DownloadEntry(entryPrefix + entrySuffix);

        String expectedEntry = entryPrefix + entrySuffix; // toString adds the extra pipe to the entry
        assertEntry(downloadEntry, expectedEntry, 0, TrafficAction.DOWNLOAD,
                InfoFactoryHolder.get().createRepoPath("docker-local", "ubuntu/latest/manifest.json").getId(),
                1152, "192.168.1.5", "fa77243b29c57219");
    }

    public void testValidRedirectEntry() {
        RepoPathImpl repoPath = new RepoPathImpl("my-test-repo", "path/to/repo");
        String remoteAddress = "127.0.0.1";
        RedirectEntry redirectEntry = new RedirectEntry(repoPath.getId(), 10L, 1000L, remoteAddress);

        String[] redirectEntryParts = StringUtils.split(redirectEntry.toString(), COLUMN_SEPARATOR);
        assertEquals(redirectEntryParts.length, 7);
        assertEquals(redirectEntryParts[2], "1000");
        assertEquals(redirectEntryParts[3], TrafficAction.REDIRECT.name());
        assertEquals(redirectEntryParts[4], remoteAddress);
        assertEquals(redirectEntryParts[5], repoPath.getId());
        assertEquals(redirectEntryParts[6], "10");
    }

    public void testValidDeprecatedRedirectNoTraceIdEntry() {
        String entryPrefix = "20200518105438|";
        String entrySuffix = "1589788478794|REDIRECT|0:0:0:0:0:0:0:1|example-repo-local:6.17.0 (1).mov|2539659";
        RedirectEntry redirectEntry = new RedirectEntry(entryPrefix + entrySuffix);

        String expectedEntry = entryPrefix + "|" + entrySuffix; // toString adds the extra pipe to the entry
        assertEntry(redirectEntry, expectedEntry, 1589788478794L, TrafficAction.REDIRECT,
                InfoFactoryHolder.get().createRepoPath("example-repo-local", "6.17.0 (1).mov").getId(), 2539659,
                "0:0:0:0:0:0:0:1", "");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptyAddressEntry() {
        String entry = "";
        UploadEntry uploadEntry = new UploadEntry(entry);
        uploadEntry.toString();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void giberishEntry() {
        String entry =
                "20050318162747|fdkfsadfsjhkdfasjkhdashjlkdfashjdfsahjkdsaFSDA|DFSA|DSF|DSFdsdfsafdsafdsadfs|DFSA|" +
                        "DFSA|DSFD|SF10|UPLOAD|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|456";
        UploadEntry uploadEntry = new UploadEntry(entry);
        uploadEntry.toString();
    }

    /**
     * Creates a new entry using an entry expression with an invalid content length
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidUploadEntryContentLength() {
        new UploadEntry("20090318162747|10|MOO|127.0.0.1|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|63a2");
    }

    private void assertEntry(TransferEntry entry, String expectedEntry, long expectedDuration,
            TrafficAction expectedAction, String expectedPath,
            long expectedContentLength, String expectedAddress, String expectedTraceId) {
        assertEquals(entry.toString(), expectedEntry, "Upload entry should be equal to the expression that created it");
        assertEquals(entry.getDuration(), expectedDuration, "Duration should be equal");
        assertEquals(entry.getAction(), expectedAction, "Action should be equal");
        assertEquals(entry.getRepoPath(), expectedPath, "Entry repo paths should be equal");
        assertEquals(entry.getContentLength(), expectedContentLength, "Entry content lengths should be equal");
        assertEquals(entry.getUserAddress(), expectedAddress, "Entry content lengths should be equal");
        assertEquals(entry.getTraceId(), expectedTraceId, "Trace ID should be equal");
    }
}
