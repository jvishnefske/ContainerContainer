/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.lifecycle.configuration.helper;

import com.jfrog.sysconf.SysConfigHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.lifecycle.webapp.servlet.BasicConfigurationManager;
import org.jfrog.config.broadcast.TemporaryBroadcastChannelImpl;
import org.jfrog.config.db.DbChannel;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.config.watch.FileEventType;
import org.jfrog.storage.wrapper.BlobWrapper;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.jfrog.common.ResourceUtils.getResourceAsString;
import static org.jfrog.config.ConfigurationManagerImpl.create;

/**
 * @author gidis
 */
public class BasicConfigurationManagerTestHelper {

    public static ConfigurationManager createEnvironment(EnvContext envContext) throws IOException {
        // Do cleanup
        FileUtils.deleteDirectory(envContext.getHomeDir());
        // Create environment before startup
        createHomeConfigurations(envContext, FileCreationStage.beforeHome);
        // Create ArtifactoryHome
        ArtifactoryHome artifactoryHome = new ArtifactoryHome(envContext.getHomeDir().getAbsolutePath());
        // Bind Thread with Artifactory home
        ArtifactoryHome.bind(artifactoryHome);
        // Create and/or generate files after home
        createHomeConfigurations(envContext, FileCreationStage.afterHome);
        // Create mock configuration manager adapter for Artifactory
        MockArtifactoryConfigurationAdapter adapter = new MockArtifactoryConfigurationAdapter(artifactoryHome);
        // We need the adapter to be able to re-use the dbChannel later for validations on the DB
        envContext.setMockConfigurationAdapter(adapter);
        // Create configuration  manager
        ConfigurationManager configurationManager = create(adapter);
        configurationManager.initDefaultFiles();
        // Create basic configuration  manager
        artifactoryHome.initDBProperties();
        ReflectionTestUtils.setField(artifactoryHome, "dbProperties", new ArtifactoryDbProperties(artifactoryHome) {
            public Optional<Boolean> allowNonPgFlag() {
                return Optional.of(true);
            }
        });
        BasicConfigurationManager basicConfigurationManager = new BasicConfigurationManager(artifactoryHome,
                configurationManager);
        // Fill Db with data
        fillDbWithData(envContext, adapter);
        // Do environment conversion
        basicConfigurationManager.initialize();

        ArtifactoryContextThreadBinder.bind(Mockito.mock(ArtifactoryContext.class));
        // Set the encryption wrapper to null and remove the master.key file from the filesystem
        removeEncryptionWrapperIfNeeded(envContext, artifactoryHome);
        // Remove configuration files if needed
        removeSharedPluginGroovyFile(envContext, artifactoryHome);
        // Change the last modified date in the filesystem
        changeLastModifiedToPast(envContext, configurationManager, artifactoryHome);
        return configurationManager;
    }

    private static void removeEncryptionWrapperIfNeeded(EnvContext envContext, ArtifactoryHome artifactoryHome) {
        if (envContext.isRemoveEncryptionWrapper()) {
            SysConfigHelper helper = artifactoryHome.getSysConfig().helper();
            FileUtils.deleteQuietly(new File(helper.getMasterKeyFile()));
            ReflectionTestUtils.setField(artifactoryHome, "masterEncryptionWrapper", null);
            ReflectionTestUtils.setField(helper, "masterKey", "");
        }
    }

    private static void removeSharedPluginGroovyFile(EnvContext envContext, ArtifactoryHome artifactoryHome) {
        if (envContext.isRemovePluginGroovyFile()) {
            new File(artifactoryHome.getPluginsDir().getPath() + "/foo.groovy").delete();
        }
    }

    private static void changeLastModifiedToPast(EnvContext envContext, ConfigurationManager configurationManager,
            ArtifactoryHome artifactoryHome) {
        List<FileMetaData> files = envContext.getModifiedFiles();
        for (FileMetaData data : files) {
            File file = new File(artifactoryHome.getVarDir(), data.getPath());
            // set last modified of file to 1970 to trick the configuration manager to invoke dbToFile
            file.setLastModified(0);
            try {
                configurationManager.forceFileChanged(file, FileEventType.MODIFY);
            } catch (SQLException | IOException e) {
                // don't care
            }
        }
    }

    private static void fillDbWithData(EnvContext envContext, MockArtifactoryConfigurationAdapter adapter) {
        DbChannel dbChannel = adapter.getDbChannel();
        if (envContext.isCreateConfigTable()) {
            createConfigsTable(dbChannel);
        }
        for (ConfigQueryMetData configQueryMetData : envContext.getBlobQueries()) {
            try {
                String blob = configQueryMetData.getBlob();
                BlobWrapper blobWrapper = new BlobWrapper(IOUtils.toInputStream(blob), blob.length());
                dbChannel.executeUpdate("INSERT INTO configs (config_name, last_modified, data) VALUES(?, ?, ?)",
                        configQueryMetData.getPath(), System.nanoTime(), blobWrapper);
            } catch (SQLException e) {
                Assert.fail(e.getMessage(), e);
            }
        }
        if (StringUtils.isNotBlank(envContext.getMasterKeyDBValue())) {
            try {
                insertMasterKeyInDb(envContext, dbChannel);
            } catch (SQLException e) {
                try {
                    tryToCreateTable(dbChannel);
                    insertMasterKeyInDb(envContext, dbChannel);
                } catch (Exception e1) {
                    Assert.fail(e.getMessage(), e1);
                }
            }
        }
    }

    private static void insertMasterKeyInDb(EnvContext envContext, DbChannel dbChannel) throws SQLException {
        dbChannel.executeUpdate(
                "INSERT INTO master_key_status (unique_key, status, set_modified_by,kid, expires) VALUES(?, ?, ?, ?, " +
                        "?)",
                true, "ok", "test", envContext.getMasterKeyDBValue(), 100L);
    }

    private static void tryToCreateTable(DbChannel dbChannel) {
        try {
            int result = dbChannel.executeUpdate("CREATE TABLE master_key_status (" +
                    "            unique_key     SMALLINT      NOT NULL," +
                    "            status            VARCHAR(16)   NOT NULL," +
                    "    set_modified_by   VARCHAR(64)   NOT NULL," +
                    "    kid               CHAR(64)      NOT NULL," +
                    "    expires           BIGINT        NOT NULL," +
                    "    CONSTRAINT kid_pk PRIMARY KEY (kid)," +
                    "    CONSTRAINT unique_key UNIQUE (unique_key))");
            if (result != 0) {
                throw new RuntimeException("Failed to create master_key_status table, reason: 0 result for" +
                        " update execution ");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create master_key_status table, reason: 0 result for" +
                    " update execution ");
        }
    }

    private static void createConfigsTable(DbChannel dbChannel) {
        try {
            int result = dbChannel.executeUpdate("CREATE TABLE configs (\n" +
                    "  config_name         VARCHAR(255) NOT NULL,\n" +
                    "  last_modified       BIGINT        NOT NULL,\n" +
                    "  data                BLOB         NOT NULL,\n" +
                    "  CONSTRAINT configs_pk PRIMARY KEY (config_name)\n" +
                    ")     ");
            if (result != 0) {
                throw new RuntimeException("Failed to create master_key_status table, reason: 0 result for" +
                        " update execution ");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create master_key_status table, reason: 0 result for" +
                    " update execution ");
        }
    }

    private static void createHomeConfigurations(EnvContext envContext, FileCreationStage stage) throws IOException {
        if (envContext.isCreateHome() && FileCreationStage.beforeHome == stage) {
            createHomeDirs(envContext);
        }
        generate(envContext, envContext.getDbProperties(stage));
        generate(envContext, envContext.getArtifactorySystemProperties(stage));
        generate(envContext, envContext.getArtifactoryKey(stage));
        generate(envContext, envContext.getArtifactoryBinarystorexXml(stage));
        generate(envContext, envContext.getArtifactoryProperties(stage));
        generate(envContext, envContext.getArtifactoryProductProperties(stage));
        generate(envContext, envContext.getArtifactoryLogbackXml(stage));
        generate(envContext, envContext.getArtifactoryMimetypes(stage));
        generate(envContext, envContext.getArtifactoryServiceId(stage));
        generate(envContext, envContext.getArtifactoryRootCert(stage));
        generate(envContext, envContext.getAccessPrivate(stage));
        generate(envContext, envContext.getAccessRootCert(stage));
        generate(envContext, envContext.getAccessLogback(stage));
        generate(envContext, envContext.getAccessDbProperties(stage));
        generate(envContext, envContext.getPluginGroovyFile(stage));
        generate(envContext, envContext.getMasterKey(stage));
    }

    private static void createHomeDirs(EnvContext envContext) {
        File homeDir = envContext.getHomeDir();
        Assert.assertTrue(homeDir.mkdir());
        Assert.assertTrue(new File(homeDir, "var").mkdir());
        Assert.assertTrue(new File(homeDir, "var/data").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc/artifactory").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc/plugins").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc/security").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc/security/access").mkdir());
        Assert.assertTrue(new File(homeDir, "var/etc/security/access/keys").mkdir());
        Assert.assertTrue(new File(homeDir, "var/access").mkdir());
        Assert.assertTrue(new File(homeDir, "var/access/etc").mkdir());
        Assert.assertTrue(new File(homeDir, "var/access/etc/keys").mkdir());
    }

    private static void generate(EnvContext envContext, FileMetaData fileMetaData) {
        if (fileMetaData != null && fileMetaData.getContent() != null) {
            doWrite(envContext, fileMetaData);
        }
    }

    private static void doWrite(EnvContext envContext, FileMetaData fileMetaData) {
        try {
            File file = new File(envContext.getHomeDir(), fileMetaData.getPath());
            if (file.exists()) {
                Assert.assertTrue(file.delete());
            }
            file.getParentFile().mkdirs();
            Assert.assertTrue(file.createNewFile());
            FileUtils.writeStringToFile(file, fileMetaData.getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void assertMasterKeyExist(EnvContext envContext) {
        Assert.assertTrue(new File(envContext.getHomeDir(), "/var/etc/security/master.key").exists());
    }

    public static void assertMasterKeyNotExists(EnvContext envContext) {
        Assert.assertTrue(!new File(envContext.getHomeDir(), "/var/etc/security/master.key").exists());
    }

    public static void assertValidBinaryStoreFileExists(EnvContext envContext) {
        try {
            File file = new File(envContext.getHomeDir(), "/var/etc/artifactory/binarystore.xml");
            Assert.assertTrue(file.exists());
            String binaryStoreXml = FileUtils.readFileToString(file);
            String originalContent = getResourceAsString("/basic/valid.artifactory.binarystore.xml");
            Assert.assertEquals(binaryStoreXml, originalContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void assertBinaryStoreFileNotModified(EnvContext envContext, long lastModifiedMillis) {
        try {
            File file = new File(envContext.getHomeDir(), "/var/etc/artifactory/binarystore.xml");
            Assert.assertTrue(file.exists());
            long binaryStoreLastModified = Files.getLastModifiedTime(file.toPath()).toMillis();
            Assert.assertEquals(binaryStoreLastModified, lastModifiedMillis);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void defaultFileExists(EnvContext envContext) {
        Assert.assertTrue(
                new File(envContext.getHomeDir(), "var/etc/artifactory/artifactory.system.properties").exists());
        Assert.assertTrue(new File(envContext.getHomeDir(), "var/etc/artifactory/binarystore.xml").exists());
        Assert.assertTrue(new File(envContext.getHomeDir(), "var/etc/artifactory/mimetypes.xml").exists());
        Assert.assertTrue(new File(envContext.getHomeDir(), "var/etc/artifactory/logback.xml").exists());
        Assert.assertTrue(new File(envContext.getHomeDir(), "var/etc/artifactory/artifactory.properties").exists());
    }

    public static void assertCorruptedArtifactoryKeyExist(EnvContext envContext) {
        try {
            File file = new File(envContext.getHomeDir(), "/etc/security/artifactory.key");
            Assert.assertTrue(file.exists());
            String artifactoryKey = FileUtils.readFileToString(file);
            String originalContent = getResourceAsString("/basic/corrupted.artifactory.key");
            Assert.assertEquals(artifactoryKey, originalContent);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class MockArtifactoryConfigurationAdapter extends ArtifactoryConfigurationAdapter {

        MockArtifactoryConfigurationAdapter(ArtifactoryHome home) {
            super(home);
        }

        @Override
        public void initialize() {
            home.initArtifactorySystemProperties();
            // Create and set broadcast channel
            broadcastChannel = new TemporaryBroadcastChannelImpl();
            ha = false;
        }
    }
}