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
package org.artifactory.storage.db.config.itest;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.ifc.ConfigurationManager;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.assertArtifactoryKeyExist;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.assertDefaultConfigsReady;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.assertExpectedSystemPropertiesExist;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.cleanDb;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.createArtifactoryKey;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.createProEnvironmentWithDb;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.getArtifactoryKeyFromDb;
import static org.artifactory.storage.db.config.itest.ConfigurationManagerTestHelper.getArtifactorySystemPropertiesFromDb;

/**
 * @author gidis
 */
@Test(enabled = false, description = "Flaky - see RTFACT-22468")
public class ArtifactoryConfigurationManagerTest extends DbBaseTest {

    private ArtifactoryConfigurationAdapter adapter;
    private ConfigurationManager configurationManager;
    private ArtifactoryHome home;

    @BeforeMethod
    public void setUp() throws IOException, SQLException {
        home = createProEnvironmentWithDb();
        adapter = new ArtifactoryConfigurationAdapter(home);
        configurationManager = ConfigurationManagerImpl.create(adapter);
    }

    @AfterMethod
    public void tearDown() {
        configurationManager.destroy();
        ArtifactoryHome.unbind();
    }

    public void encryptionConfigOnPro() throws IOException, SQLException {
        cleanDb(home);
        home.getMasterEncryptionWrapper();
        configurationManager.startSync();
        createArtifactoryKey(home);
        assertDefaultConfigsReady(home);
        assertArtifactoryKeyExist(home, configurationManager);
    }

    public void configIsDownloadedLocalyOnPro() throws IOException, SQLException {
        cleanDb(home);
        configurationManager.startSync();
        home.unsetArtifactoryEncryptionWrapper();
        createArtifactoryKey(home);
        assertDefaultConfigsReady(home);
        assertArtifactoryKeyExist(home, configurationManager);
        FileUtils.deleteQuietly(home.getArtifactoryKey());
        Assert.assertFalse(home.getArtifactoryKey().exists());
        configurationManager = ConfigurationManagerImpl.create(adapter);
        configurationManager.startSync();
        Assert.assertTrue(home.getArtifactoryKey().exists());
    }

    public void protectedOnPro() throws IOException, SQLException {
        cleanDb(home);
        configurationManager.startSync();
        home.unsetArtifactoryEncryptionWrapper();
        createArtifactoryKey(home);
        // Keep original file;
        String originalKey = FileUtils.readFileToString(home.getArtifactoryKey(), "utf-8");
        assertDefaultConfigsReady(home);
        assertArtifactoryKeyExist(home, configurationManager);
        String corruptedFile = "Some content";
        FileUtils.writeStringToFile(home.getArtifactoryKey(), corruptedFile, "utf-8");
        Assert.assertTrue(home.getArtifactoryKey().exists());
        String dbContent = getArtifactoryKeyFromDb(home, configurationManager);
        String fileContent = FileUtils.readFileToString(home.getArtifactoryKey(), "utf-8");
        Assert.assertNotEquals(dbContent, fileContent);
        Assert.assertEquals(dbContent, originalKey);
        ArtifactoryHome.unbind();
    }

    @Test(enabled = false, description = "Flaky - see RTFACT-22468")
    public void notEncryptionConfigOnPro() throws IOException, SQLException, InterruptedException {
        cleanDb(home);
        configurationManager.startSync();
        home.unsetArtifactoryEncryptionWrapper();
        // Keep original file;
        String originalConfig = FileUtils.readFileToString(home.getArtifactorySystemPropertiesFile());
        assertDefaultConfigsReady(home);
        String corruptedFile = "Some content";
        Thread.sleep(1000);
        FileUtils.writeStringToFile(home.getArtifactorySystemPropertiesFile(), corruptedFile, "utf-8", false);
        assertExpectedSystemPropertiesExist(home, configurationManager, corruptedFile);
        Assert.assertTrue(home.getArtifactorySystemPropertiesFile().exists());
        String dbContent = getArtifactorySystemPropertiesFromDb(home, configurationManager);
        String fileContent = FileUtils.readFileToString(home.getArtifactorySystemPropertiesFile(), "utf-8");
        Assert.assertEquals(dbContent, fileContent);
        Assert.assertNotEquals(dbContent, originalConfig);
    }

}