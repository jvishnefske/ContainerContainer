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

package org.artifactory.support.config.bundle;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.Date;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Michael Pasternak
 */
public class BundleConfigurationTest {
    @Test
    public void testDefaultConstructor_shouldInitializeAllConfigurationsWithDefaults() {
        BundleConfigurationImpl bc = new BundleConfigurationImpl();

        assertTrue(bc.isCollectSystemLogs());
        assertTrue(bc.isCollectSystemInfo());
        assertTrue(bc.isCollectSecurityConfig());
        assertTrue(bc.isCollectConfigurationFiles());
        assertTrue(bc.isCollectThreadDump());
        assertTrue(bc.isCollectStorageSummary());
        assertTrue(bc.isCollectFederation());
        assertEquals(bc.getBundleSize(), BundleConfigurationImpl.DEFAULT_BUNDLE_SIZE);
    }

    @Test
    public void testBundleDatesConfiguration() {
        Date startDate = DateTime.now().minusDays(3).toDate();
        Date endDate = DateTime.now().toDate();

        BundleConfiguration bc =
                new BundleConfigurationBuilder(startDate, endDate)
                        .collectSystemInfo()
                        .collectSecurityConfig(false)
                        .collectConfigurationFiles()
                        .collectThreadDump()
                        .collectStorageSummary()
                        .build();

        assertTrue(bc.isCollectSystemLogs());
        assertTrue(bc.isCollectSecurityConfig());
        assertTrue(bc.isCollectSystemInfo());
        assertTrue(bc.isCollectThreadDump());
        assertTrue(bc.isCollectStorageSummary());

        assertFalse(bc.getSecurityInfoConfiguration().isHideUserDetails());
        assertFalse(bc.getSystemLogsConfiguration().isScrubIPs());
        assertFalse(bc.getSystemLogsConfiguration().isScrubUserNames());
        assertFalse(bc.getConfigFilesConfiguration().isScrubIPs());
        assertFalse(bc.getConfigFilesConfiguration().isScrubUserNames());

        assertEquals(bc.getSystemLogsConfiguration().getStartDate(), startDate);
        assertEquals(bc.getSystemLogsConfiguration().getEndDate(), endDate);
    }

    @Test
    public void testBundleDateOffsetConfiguration() {
        BundleConfiguration bc =
                new BundleConfigurationBuilder(5)
                        .collectSystemInfo()
                        .collectSecurityConfig(false)
                        .collectConfigurationFiles()
                        .collectThreadDump()
                        .collectStorageSummary()
                        .collectFederation(true)
                        .build();

        assertTrue(bc.isCollectSystemLogs());
        assertTrue(bc.isCollectSecurityConfig());
        assertTrue(bc.isCollectSystemInfo());
        assertTrue(bc.isCollectThreadDump());
        assertTrue(bc.isCollectStorageSummary());

        assertFalse(bc.getSecurityInfoConfiguration().isHideUserDetails());
        assertFalse(bc.getSystemLogsConfiguration().isScrubIPs());
        assertFalse(bc.getSystemLogsConfiguration().isScrubUserNames());
        assertFalse(bc.getConfigFilesConfiguration().isScrubIPs());
        assertFalse(bc.getConfigFilesConfiguration().isScrubUserNames());

        assertEquals(bc.getSystemLogsConfiguration().getDaysCount(), Integer.valueOf(5));
    }
}
