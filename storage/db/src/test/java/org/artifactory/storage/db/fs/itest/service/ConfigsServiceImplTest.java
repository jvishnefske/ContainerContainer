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

package org.artifactory.storage.db.fs.itest.service;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.dao.ConfigsDao;
import org.artifactory.storage.db.fs.dao.VersionConfigDao;

import static org.artifactory.storage.db.fs.dao.VersionConfigDao.CONFIG_DATA_COLUMN;
import static org.artifactory.storage.db.fs.dao.VersionConfigDao.LOG_CONFIG_DATA_COLUMN;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the {@link org.artifactory.storage.fs.service.ConfigsService}
 *
 * @author Yossi Shaul
 */
public class ConfigsServiceImplTest extends DbBaseTest {

    @Autowired
    private ConfigsService configsService;

    public void addConfig() {
        addConfig("confName", "confinput");
    }

    private void addConfig(String name, String data) {
        configsService.addConfig(name, data, System.currentTimeMillis());
    }

    @Test(dependsOnMethods = "addConfig")
    public void hasConfig() {
        assertTrue(configsService.hasConfig("confName"));
    }

    @Test(dependsOnMethods = "addConfig")
    public void getConfig() throws SQLException {
        String data = configsService.getConfig("confName");
        assertNotNull(data);
        assertEquals(data, "confinput");
    }

    @Test(dependsOnMethods = "getConfig")
    public void updateConfig() {
        configsService.updateConfig("confName", "newdata", System.currentTimeMillis());
        String result = configsService.getConfig("confName");
        assertNotNull(result);
        assertEquals(result, "newdata");
    }

    public void hasConfigNoConfig() {
        assertFalse(configsService.hasConfig("nosuchconfig"));
    }

    public void loadConfigNoConfig() {
        assertNull(configsService.getConfig("nosuchconfig"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void updateConfigNoConfig() {
        configsService.updateConfig("nosuchconfig", "newdata", System.currentTimeMillis());
    }

    @Test(dependsOnMethods = "addConfig", expectedExceptions = IllegalStateException.class)
    public void addConfigExistingConfig() {
        configsService.addConfig("confName", "jojjoh", System.currentTimeMillis());
    }

    public void addOrUpdateConfig() {
        boolean created = configsService.addOrUpdateConfig("artifactory", "somedata", System.currentTimeMillis());
        assertTrue(created);
        assertEquals(configsService.getConfig("artifactory"), "somedata");
        created = configsService.addOrUpdateConfig("artifactory", "newdata", System.currentTimeMillis());
        assertFalse(created);
        assertEquals(configsService.getConfig("artifactory"), "newdata");
    }

    @Test(dependsOnMethods = "addConfig", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Can not create a backup: config name can not be empty")
    public void backupConfigWhenConfigNameIsEmpty() {
        configsService.backupConfig("", System.currentTimeMillis());
    }

    @Test
    public void backupConfigWhenConfigDataIsEmpty() {
        String configName = "emptyConfig";
        long lastModified = System.currentTimeMillis();
        configsService.backupConfig(configName, lastModified);

        String backupName = String.join("_", configName, "backup", Long.toString(lastModified));
        assertFalse(configsService.hasConfig(backupName));
    }

    @Test(dependsOnMethods = "addConfig", expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Can not create a backup with name .* because it already exist.")
    public void backupConfigWhenBackupIsPresent() {
        String configName = "confName";
        long timestamp = System.currentTimeMillis();
        assertTrue(configsService.hasConfig(configName));

        configsService.backupConfig(configName, timestamp);
        String backupName = String.join("_", configName, "backup", Long.toString(timestamp));
        assertTrue(configsService.hasConfig(backupName));

        configsService.backupConfig(configName, timestamp);
    }

    @Test(dependsOnMethods = "addConfig")
    public void backupConfig() {
        String configName = "confName";
        String configData = "confinput";
        long timestamp = System.currentTimeMillis();
        assertTrue(configsService.hasConfig(configName));

        configsService.backupConfig(configName, timestamp);

        String backupName = String.join("_", configName, "backup", Long.toString(timestamp));
        String backupData = configsService.getConfig(backupName);
        assertFalse(StringUtils.isBlank(backupData));
        assertEquals(backupData, configData);
    }

    @Test(dependsOnMethods = "addConfig")
    public void getStreamConfig() {
        String configName = "confName";
        String configData = "confinput";
        assertTrue(configsService.hasConfig(configName));

        try (InputStream streamConfig = configsService.getStreamConfig(configName)) {
            String text = new BufferedReader(
                    new InputStreamReader(streamConfig, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            assertEquals(text, configData);
        } catch (IOException e) {
            fail("Should not have failed", e);
        }
    }

    @Test(dependsOnMethods = "addConfig", expectedExceptions = StorageException.class)
    public void getStreamConfigFail() throws SQLException {
        String configName = "confName";
        assertTrue(configsService.hasConfig(configName));

        ConfigsDao configsDaoOrig = (ConfigsDao) ReflectionTestUtils.getField(configsService, "configsDao");
        try {
            ConfigsDao configsDaoMock = mock(ConfigsDao.class);
            when(configsDaoMock.loadStreamConfig(anyString())).thenThrow(new SQLException("TEST"));
            ReflectionTestUtils.setField(configsService, "configsDao", configsDaoMock);

            configsService.getStreamConfig(configName);
            fail("Should fail");
        } finally {
            ReflectionTestUtils.setField(configsService, "configsDao", configsDaoOrig);
        }
    }


    @Test(dependsOnMethods = "addConfig")
    public void deleteConfig() {
        String configName = "SomeName";
        addConfig(configName, "SomeData");
        assertTrue(configsService.hasConfig(configName));

        int res = configsService.deleteConfig(configName);
        assertEquals(res, 1);
        res = configsService.deleteConfig(configName);
        assertEquals(res, 0);
    }

    @Test(dependsOnMethods = "addConfig", expectedExceptions = StorageException.class)
    public void deleteConfigFail() throws SQLException {
        String configName = "confName";
        assertTrue(configsService.hasConfig(configName));

        ConfigsDao configsDaoOrig = (ConfigsDao) ReflectionTestUtils.getField(configsService, "configsDao");
        try {
            ConfigsDao configsDaoMock = mock(ConfigsDao.class);
            when(configsDaoMock.deleteConfig(anyString())).thenThrow(new SQLException("TEST"));
            ReflectionTestUtils.setField(configsService, "configsDao", configsDaoMock);

            configsService.deleteConfig(configName);
            fail("Should fail");
        } finally {
            ReflectionTestUtils.setField(configsService, "configsDao", configsDaoOrig);
        }
    }

    @Test
    public void testConfigUsingKeys() {
        ConfigsServiceKey keyToUse = ConfigsServiceKey.ARTIFACTORY_GC_ITERATION;
        assertNull(configsService.getConfig(keyToUse), "checking doesn't exist before starts");
        String value = "10";
        assertTrue(configsService.addOrUpdateConfig(keyToUse, value, System.currentTimeMillis()),
                "add config for 1st time using key");
        String result = configsService.getConfig(keyToUse);
        assertNotNull(result, "verify get using key works");
        assertEquals(value, result, "verify getConfig using ConfigsServiceKey works");
        //update value and try again
        value = "11";
        assertFalse(configsService.addOrUpdateConfig(keyToUse, value, System.currentTimeMillis()),
                "modify value and update");
        String result2 = configsService.getConfig(keyToUse);
        assertNotNull(result2, "verify get using key works");
        assertEquals(value, result2, "verify getConfig using ConfigsServiceKey works");
        //delete record
        configsService.deleteConfig(keyToUse.getKeyName());
        assertNull(configsService.getConfig(keyToUse), "verify doesn't exist after delete");
    }

    @Test
    void testGetConfigHistory() throws SQLException, IOException {
        String version = "1.1.1";
        String configData = "configData";
        VersionConfigDao versionConfigDao = mock(VersionConfigDao.class);
        ReflectionTestUtils.setField(configsService, "versionConfigDao", versionConfigDao);

        when(versionConfigDao.getConfigDataByVersion(version, CONFIG_DATA_COLUMN)).thenReturn(configData);
        String config = configsService.getConfigHistory(version);
        assertEquals(configData, config);
    }

    @Test
    void testGetConfigHistoryNull() throws SQLException, IOException {
        String version = "1.1.1";
        VersionConfigDao versionConfigDao = mock(VersionConfigDao.class);
        ReflectionTestUtils.setField(configsService, "versionConfigDao", versionConfigDao);

        when(versionConfigDao.getConfigDataByVersion(version, CONFIG_DATA_COLUMN)).thenReturn(null);
        String config = configsService.getConfigHistory(version);
        assertNull(config);
    }

    @Test
    void testGetConfigHistoryError() throws SQLException, IOException {
        String version = "1.1.1";
        VersionConfigDao versionConfigDao = mock(VersionConfigDao.class);
        ReflectionTestUtils.setField(configsService, "versionConfigDao", versionConfigDao);

        when(versionConfigDao.getConfigDataByVersion(version, CONFIG_DATA_COLUMN))
                .thenThrow(new StorageException("Can't get config"));
        assertThrows(StorageException.class, () -> configsService.getConfigHistory(version));
    }

    @Test
    void testGetLogConfigHistory() throws SQLException, IOException {
        String version = "1.1.1";
        String logConfigData = "logConfigData";
        VersionConfigDao versionConfigDao = mock(VersionConfigDao.class);
        ReflectionTestUtils.setField(configsService, "versionConfigDao", versionConfigDao);

        when(versionConfigDao.getConfigDataByVersion(version, LOG_CONFIG_DATA_COLUMN)).thenReturn(logConfigData);
        String config = configsService.getLogConfigHistory(version);
        assertEquals(logConfigData, config);
    }

    @Test
    void testGetLogConfigHistoryError() throws SQLException, IOException {
        String version = "1.1.1";
        VersionConfigDao versionConfigDao = mock(VersionConfigDao.class);
        ReflectionTestUtils.setField(configsService, "versionConfigDao", versionConfigDao);

        when(versionConfigDao.getConfigDataByVersion(version, LOG_CONFIG_DATA_COLUMN))
                .thenThrow(new SQLException("Can't get log config"));
        assertThrows(StorageException.class, () -> configsService.getLogConfigHistory(version));
    }
}
