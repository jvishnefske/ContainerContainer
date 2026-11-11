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

package org.artifactory.logging.sumo.logback;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.logging.sumo.logback.SumoLogbackUpdater.UpdateData;
import org.jfrog.common.ResourceUtils;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;
import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertTrue;

/**
 * @author Shay Yaakov
 */
@Test
public class SumoLogbackUpdaterTest {

    private SumoLogbackUpdater sumoLogbackUpdater;
    private File baseTestDir;
    private static final String COLLECTOR_URL = "the-collector-url";
    private static final String ARTIFACTORY_NODE = "art.node";
    private static final String ARTIFACTORY_HOST = "art.host";
    private static final String DEFAULT_UPDATED_LOGBACK = "/org/artifactory/sumologic/logback_enabled_no_proxy.xml";

    @BeforeMethod
    public void createTempDir() {
        skipOnWindows();
        baseTestDir = new File(System.getProperty("java.io.tmpdir"), "sumologictest");
        baseTestDir.mkdirs();
        assertTrue(baseTestDir.exists(), "Failed to create base test dir");
        sumoLogbackUpdater = new SumoLogbackUpdater();
    }

    private void skipOnWindows() {
        if (SystemUtils.IS_OS_WINDOWS) {
            throw new SkipException("Skipping test on windows OS");
        }
    }

    @AfterMethod
    public void deleteTempDir() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(baseTestDir);
    }

    @Test
    public void testUpdateSumoAppenders() throws IOException {
        File logbackFile = initLogbackFile("/org/artifactory/sumologic/logback.xml");

        // --- Add sumo appenders for the first time ---
        SumoLogicConfigDescriptor sumoConfig = createSumoLogicConfigDescriptor();
        sumoConfig.setCollectorUrl(COLLECTOR_URL);
        sumoConfig.setEnabled(true);
        sumoLogbackUpdater.update(logbackFile, new UpdateData(sumoConfig, ARTIFACTORY_HOST, ARTIFACTORY_NODE));
        assertUpdateAsExpected(logbackFile, DEFAULT_UPDATED_LOGBACK);

        // --- Update sumo appenders - disable and change collector url ---
        sumoConfig = createSumoLogicConfigDescriptor();
        sumoConfig.setCollectorUrl("the-other-collector-url");
        sumoConfig.setEnabled(false);
        sumoLogbackUpdater.update(logbackFile, new UpdateData(sumoConfig, ARTIFACTORY_HOST, ARTIFACTORY_NODE));
        assertUpdateAsExpected(logbackFile, "/org/artifactory/sumologic/logback_disabled_no_proxy.xml");

        // --- Update sumo appenders - enable, change collector url and add proxy ---
        sumoConfig = createSumoLogicConfigDescriptor();
        sumoConfig.setCollectorUrl(COLLECTOR_URL);
        sumoConfig.setEnabled(true);
        sumoConfig.setProxy(createProxy());
        sumoLogbackUpdater.update(logbackFile, new UpdateData(sumoConfig, ARTIFACTORY_HOST, null));
        assertUpdateAsExpected(logbackFile, "/org/artifactory/sumologic/logback_enabled_with_proxy.xml");

        // --- Update sumo appenders - remove proxy ---
        sumoConfig = createSumoLogicConfigDescriptor();
        sumoConfig.setCollectorUrl(COLLECTOR_URL);
        sumoConfig.setEnabled(true);
        sumoConfig.setProxy(null);
        sumoLogbackUpdater.update(logbackFile, new UpdateData(sumoConfig, ARTIFACTORY_HOST, ARTIFACTORY_NODE));
        assertUpdateAsExpected(logbackFile, DEFAULT_UPDATED_LOGBACK);
    }

    /**
     * Test sumo logback update move appenders to the right order.
     *
     * @throws IOException
     */
    @Test
    public void testBadAppendersFix() throws IOException {
        File logbackFile = initLogbackFile("/org/artifactory/sumologic/logback_bad_appenders.xml");
        SumoLogicConfigDescriptor sumoConfig = createSumoLogicConfigDescriptor();
        sumoConfig.setCollectorUrl(COLLECTOR_URL);
        sumoConfig.setEnabled(true);
        sumoLogbackUpdater.update(logbackFile, new UpdateData(sumoConfig, ARTIFACTORY_HOST, ARTIFACTORY_NODE));
        assertUpdateAsExpected(logbackFile, DEFAULT_UPDATED_LOGBACK);
    }

    @Test
    public void clearSumoConfig() throws IOException {
        File logbackFile = initLogbackFile("/org/artifactory/sumologic/logback_bad_appenders.xml");
        SumoLogbackUpdater.removeSumoLogicFromXml(logbackFile);
        assertUpdateAsExpected(logbackFile, "/org/artifactory/sumologic/semi_clean_logback.xml");
    }

    @Test
    public void fixCollectorUrl() throws IOException {
        File logbackFile = initLogbackFile(DEFAULT_UPDATED_LOGBACK);
        SumoLogbackUpdater.verifyAndUpdateCollectorUrl(logbackFile, "test-curl");
        assertUpdateAsExpected(logbackFile, "/org/artifactory/sumologic/fixed_collector_logback.xml");
    }

    private void assertUpdateAsExpected(File logbackFile, String expectedAs) throws IOException {
        File expectedFile = ResourceUtils.getResourceAsFile(expectedAs);
        assertThat(FileUtils.readFileToString(logbackFile, Charset.defaultCharset()))
                .isEqualTo(FileUtils.readFileToString(expectedFile, Charset.defaultCharset()));
    }

    private File initLogbackFile(String resourcePath) throws IOException {
        File logbackFile = new File(baseTestDir, LOGBACK_CONFIG_FILE_NAME);
        FileUtils.copyFile(ResourceUtils.getResourceAsFile(resourcePath), logbackFile);
        return logbackFile;
    }


    private ProxyDescriptor createProxy() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setHost("proxy-host");
        proxy.setPort(8888);
        return proxy;
    }

    private SumoLogicConfigDescriptor createSumoLogicConfigDescriptor() {
        SumoLogicConfigDescriptor sumoConfig = new SumoLogicConfigDescriptor();
        sumoConfig.setClientId("the-client-id-" + System.currentTimeMillis());
        sumoConfig.setSecret("the-secret-" + System.currentTimeMillis());
        sumoConfig.setDashboardUrl("the-dashboard-url-" + System.currentTimeMillis());
        sumoConfig.setBaseUri("the-base-uri-" + System.currentTimeMillis());
        return sumoConfig;
    }
}