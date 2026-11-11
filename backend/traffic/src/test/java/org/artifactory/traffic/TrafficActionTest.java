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

package org.artifactory.traffic;

import org.artifactory.traffic.entry.DownloadEntry;
import org.artifactory.traffic.entry.LegacyTrafficEntry;
import org.artifactory.traffic.entry.RedirectEntry;
import org.artifactory.traffic.entry.TokenizedTrafficEntryFactory;
import org.artifactory.traffic.entry.UploadEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit test for the TrafficAction enum
 *
 * @author Noam Tenne
 */
@Test
public class TrafficActionTest {

    /**
     * Construct a request entry with an expression using the TrafficAction
     */
    public void testRedirectAction() throws Exception {
        String entry =
                "20090319110249|670ca1d68dac3d45|10|REDIRECT|127.0.0.1|repo1:antlr/antlr/2.7.6/antlr-2.7.6.jar|443432";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.REDIRECT);
        Assert.assertTrue(trafficEntry instanceof RedirectEntry);
    }

    @Test
    public void deprecatedRedirectNoTraceIdAction() throws Exception {
        String entry =
                "20200518101130|1589785890156|REDIRECT|::1|example-repo-local:application-1.0-SNAPSHOT.jar|44620320";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.REDIRECT);
        Assert.assertTrue(trafficEntry instanceof RedirectEntry);
        Assert.assertEquals(trafficEntry.getDuration(), 1589785890156L);
    }

    /**
     * Construct a download entry with an expression using the TrafficAction
     */
    public void testDownloadAction() throws Exception {
        String entry =
                "20090319110249|670ca1d68dac3d45|10|DOWNLOAD|127.0.0.1|repo1:antlr/antlr/2.7.6/antlr-2.7.6.jar|443432";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.DOWNLOAD);
        Assert.assertTrue(trafficEntry instanceof DownloadEntry);
    }

    public void testDeprecatedDownloadActionNoTraceId() throws Exception {
        String entry = "20200518111430|86|DOWNLOAD|0:0:0:0:0:0:0:1|example-repo-local:pgweb_darwin_amd64.zip|9049647";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.DOWNLOAD);
        Assert.assertEquals(trafficEntry.getDuration(), 86L);
        Assert.assertTrue(trafficEntry instanceof DownloadEntry);
    }

    public void testUploadAction() throws Exception {
        String entry =
                "20200518101034|69f43a08d706d486|6|UPLOAD|192.168.1.5|docker-local:artifactory-pro/latest/manifest" +
                        ".json|3463";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD);
        Assert.assertEquals(trafficEntry.getDuration(), 6L);
        Assert.assertTrue(trafficEntry instanceof UploadEntry);
    }

    /**
     * Construct an upload entry with an expression using the TrafficAction
     */
    public void testDeprecatedUploadActionNoTraceId() throws Exception {
        String entry = "20090319110249|10|UPLOAD|127.0.0.1|repo1-cache:antlr/antlr/2.7.6/antlr-2.7.6.jar|443432";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD);
        Assert.assertTrue(trafficEntry instanceof UploadEntry);
    }

    /**
     * During event based pull replication it is possible that entries with no IP and with content length 0 will be
     * generated
     */
    public void testNoIP() throws Exception {
        String entry = "20200527120002|4c5608e5df3c1326|572|UPLOAD||event-test:a|0";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD);
        Assert.assertTrue(trafficEntry instanceof UploadEntry);
    }

    /**
     * During event based pull replication it is possible that entries with no IP and with content length 0 will be
     * generated
     */
    public void testNoIPNoTraceId() throws Exception {
        String entry = "20200527120002|572|UPLOAD||event-test:a|0";
        LegacyTrafficEntry trafficEntry = TokenizedTrafficEntryFactory.newTrafficEntry(entry);
        Assert.assertNotNull(trafficEntry);
        Assert.assertEquals(trafficEntry.getAction(), TrafficAction.UPLOAD);
        Assert.assertTrue(trafficEntry instanceof UploadEntry);
    }
}
