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

import com.jfrog.sysconf.SysConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.home.TestArtifactoryHome;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.db.ConfigWithTimestamp;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.security.file.SecurityFolderHelper;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import static org.jfrog.config.ConfigurationManagerImpl.BASE_CONFIG_PREFIX;

/**
 * @author gidis
 */
class ConfigurationManagerTestHelper {
    private static final String BINARY_STORE_XML_CONTENT = "<config version=\"v1\">\n" +
            "    <chain>\n" +
            "        <provider id=\"cache-fs\" type=\"cache-fs\">\n" +
            "            <test-mw>test-me</test-mw> " +
            "            <dynamic-provider id=\"file-system\" type=\"file-system\"/>\n" +
            "            <provider id=\"blob\" type=\"blob\"/>\n" +
            "        </provider>\n" +
            "    </chain>\n" +
            "\n" +
            "</config>";

    static ArtifactoryHome createProEnvironmentWithDb() throws SQLException, IOException {
        ArtifactoryHome home = ArtifactoryHome.get();
        cleanHome(home);
        initPro(home);
        home.initArtifactorySystemProperties();
        ArtifactoryHome.bind(home);
        return home;
    }

    static ArtifactoryHome createProEnvironmentWithoutDb() throws SQLException, IOException {
        ArtifactoryHome home = new TestArtifactoryHome(new File("Configuration"));
        cleanHome(home);
        initPro(home);
        assertDefaultFilesNotExist(home);
        return home;
    }

    static void assertDefaultConfigsReady(ArtifactoryHome home) {
        Assert.assertTrue(home.getLogbackConfig().exists());
        Assert.assertTrue(home.getMimeTypesFile().exists());
        Assert.assertTrue(home.getBinaryStoreXmlFile().exists());
        Assert.assertTrue(home.getArtifactorySystemPropertiesFile().exists());
    }

    static void assertDefaultFilesNotExist(ArtifactoryHome home) {
        Assert.assertFalse(home.getLogbackConfig().exists());
        Assert.assertFalse(home.getMimeTypesFile().exists());
        Assert.assertFalse(home.getBinaryStoreXmlFile().exists());
        Assert.assertFalse(home.getArtifactorySystemPropertiesFile().exists());
    }

    static void cleanHome(ArtifactoryHome home) throws SQLException {
        home.getArtifactoryKey().delete();
        home.getLicenseFile().delete();
        home.getLogbackConfig().delete();
        home.getMimeTypesFile().delete();
        home.getBinaryStoreXmlFile().delete();
        home.getArtifactorySystemPropertiesFile().delete();
        home.getArtifactoryHaNodePropertiesFile().delete();
    }

    static void cleanDb(ArtifactoryHome home) throws SQLException {
        ArtifactoryDbProperties dbProperties = home.initDBProperties();
        TemporaryDBChannel chanel = new TemporaryDBChannel(dbProperties);
        Connection connection = chanel.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement("delete from configs");
        preparedStatement.executeUpdate();
        connection.close();
    }

    //static void createDBPropertiesFile(ArtifactoryHome home) throws IOException {
    //    FileUtils.writeStringToFile(home.getDBPropertiesFile(),
    //            "type=MSSQL\n" +
    //                    "url=jdbc:derby:{db.home};create=true\n" +
    //                    "driver=org.apache.derby.jdbc.EmbeddedDriver");
    //}

    static void createBinaryStoreXmlFile(ArtifactoryHome home) throws IOException {
        FileUtils.writeStringToFile(home.getBinaryStoreXmlFile(), BINARY_STORE_XML_CONTENT, "utf-8");
    }

    static void assertProvidedBinaryStoreXmlIsValid(ArtifactoryHome home) throws IOException {
        Assert.assertTrue(FileUtils.readFileToString(home.getBinaryStoreXmlFile()).
                contains(BINARY_STORE_XML_CONTENT));
    }

    static void createArtifactoryKey(ArtifactoryHome home) throws IOException {
        SecurityFolderHelper.createKeyFile(home.getArtifactoryKey());
    }

    static String getArtifactoryKeyFromDb(ArtifactoryHome home, ConfigurationManager configurationManager)
            throws IOException {
        ConfigurationManagerImpl iml = (ConfigurationManagerImpl) configurationManager;
        String artifactoryKeyPrefix = BASE_CONFIG_PREFIX + "security" + File.separator + "artifactory.key";
        ConfigWithTimestamp config = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                true, home);
        return IOUtils.toString(config.getBinaryStream());
    }

    static String getArtifactorySystemPropertiesFromDb(ArtifactoryHome home, ConfigurationManager configurationManager)
            throws IOException {
        ConfigurationManagerImpl iml = (ConfigurationManagerImpl) configurationManager;
        String artifactoryKeyPrefix = BASE_CONFIG_PREFIX + "artifactory.system.properties";
        ConfigWithTimestamp config = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                true, home);
        return IOUtils.toString(config.getBinaryStream());
    }

    static void assertArtifactoryKeyExist(ArtifactoryHome home, ConfigurationManager configurationManager)
            throws IOException {
        Assert.assertTrue(home.getArtifactoryKey().exists());
        ConfigurationManagerImpl iml = (ConfigurationManagerImpl) configurationManager;
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String artifactoryKeyPrefix = BASE_CONFIG_PREFIX + "security" + File.separator + "artifactory.key";
        while (!success && startTime + 1000 * 85 > System.currentTimeMillis()) {
            Long configTimestamp = iml.getConfigsDao().getConfigTimestamp(
                    artifactoryKeyPrefix);
            success = configTimestamp != null;
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Assert.assertTrue(success);
        // Sending false in the encryption to get encypted file
        ConfigWithTimestamp encypted = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                false, home);
        ConfigWithTimestamp decypted = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                true, home);
        String decyptedText = IOUtils.toString(decypted.getBinaryStream());
        String encryptText = IOUtils.toString(encypted.getBinaryStream());
        String original = FileUtils.readFileToString(home.getArtifactoryKey());
        Assert.assertTrue(original.equals(decyptedText));
        Assert.assertFalse(original.equals(encryptText));
    }

    static void assertArtifactorySystemPropertiesExists(ArtifactoryHome home, ConfigurationManager configurationManager,
            String content) throws IOException {
        ConfigurationManagerImpl iml = (ConfigurationManagerImpl) configurationManager;
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String artifactoryKeyPrefix = BASE_CONFIG_PREFIX + "artifactory.system.properties";
        while (!success && startTime + 1000 * 30 > System.currentTimeMillis()) {
            ConfigWithTimestamp config = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                    false, home);
            String dbContent = IOUtils.toString(config.getBinaryStream());
            success = content.contains(dbContent) || dbContent.contains(content);
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Assert.assertTrue(success);
    }

    static void assertExpectedSystemPropertiesExist(ArtifactoryHome home, ConfigurationManager configurationManager,
            String expected) throws IOException {
        Assert.assertTrue(home.getArtifactorySystemPropertiesFile().exists());
        ConfigurationManagerImpl iml = (ConfigurationManagerImpl) configurationManager;
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String artifactoryKeyPrefix = BASE_CONFIG_PREFIX + "artifactory.system.properties";
        while (!success && startTime + 1000 * 30 > System.currentTimeMillis()) {
            ConfigWithTimestamp config = iml.getConfigsDao().getConfig(artifactoryKeyPrefix,
                    false, home);
            String dbContent = config != null ? IOUtils.toString(config.getBinaryStream()) : "";
            success = dbContent.equals(expected);
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Assert.assertTrue(success);
    }

    static void initPro(ArtifactoryHome home) throws IOException {
        ReflectionTestUtils.setField(home, "haNodeProperties", null);
        SysConfig sysConfig = (SysConfig) ReflectionTestUtils.getField(home, "sysConfig");
        Map map = (Map) ReflectionTestUtils.getField(sysConfig, "defaults");
        map.put("shared.security.masterKey", "1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a");
    }
}