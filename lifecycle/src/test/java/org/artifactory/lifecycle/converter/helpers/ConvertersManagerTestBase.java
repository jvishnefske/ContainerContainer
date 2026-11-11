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

package org.artifactory.lifecycle.converter.helpers;

import org.apache.commons.io.FileUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.FederatedRepoConfigEnrichmentService;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactoryConverter;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.lifecycle.webapp.servlet.BasicConfigurationManager;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.RepoPath;
import org.artifactory.security.access.AccessService;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.db.converter.markers.ConverterManagerImpl;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.TestUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.common.ResourceUtils;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.config.watch.FileWatcher;
import org.jfrog.security.common.KeyUtils;
import org.jfrog.storage.DbProperties;
import org.joda.time.DateTimeUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import static org.artifactory.common.ConstantValues.artifactoryServiceRevision;
import static org.artifactory.common.ConstantValues.artifactoryServiceTimestamp;
import static org.artifactory.common.ConstantValues.artifactoryServiceVersion;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_HA_NODE_PROPERTIES_FILE;
import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.MIME_TYPES_FILE_NAME;
import static org.jfrog.storage.DbProperties.DEFAULT_POOL_TYPE;
import static org.jfrog.storage.DbType.DERBY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Helper static methods for the ConverterManagerTest.
 *
 * @author Gidi Shabat
 */
public class ConvertersManagerTestBase {
    private static final String DEFAULT_MASTER_KEY = "0123456789abcdef0123456789abcdef";

    protected ConfigurationManager configurationManager;

    @AfterMethod
    public void cleanup() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    protected void createEnvironment(File home, ArtifactoryServiceVersion homeVersion,
            ArtifactoryServiceVersion dbVersion) throws IOException {
        createEnvironment(home, homeVersion, dbVersion, new MockServiceConverter(home + "/.history/db.test", null));
    }

    /**
     * Creates Complete environment files and database simulator
     */
    protected void createEnvironment(File home, ArtifactoryServiceVersion homeVersion,
            ArtifactoryServiceVersion dbVersion, ArtifactoryConverter artifactoryConverter) throws IOException {
        if ((homeVersion == null || homeVersion.before(ArtifactoryServiceVersionProvider.v700m001.get())) ||
                (dbVersion == null || dbVersion.before(
                        ArtifactoryServiceVersionProvider.v700m001.get()))) {
            // By setting the allowed version to 7.0.0-m001 we skip both validation of
            //V550UpgradePrerequisiteVerifier and V700UpgradePrerequisiteVerifier
            System.setProperty(ConstantValues.allowAnyUpgrade.getPropertyName(), "7.0.0-m001");
        }
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        ArtifactoryHome artifactoryHome = new MockArtifactoryHome(home);
        BasicConfigurationManager bcm =
                createBasicConfigurationManager(home, homeVersion, dbProperties, artifactoryHome, true);

        ((ConverterManagerImpl) bcm.convertersManager).addPreInitConverter(
                new MockHomeLocalConverter(home + "/.history/preInitFiles.test"));
        ((ConverterManagerImpl) bcm.convertersManager).addSyncFilesConverter(
                new MockHomeSharedConverter(home + "/.history/syncFiles.test"));
        ((ConverterManagerImpl) bcm.convertersManager).addHomeConverter(
                new MockHomeSharedConverter(home + "/.history/homeFiles.test"));
        ((ConverterManagerImpl) bcm.convertersManager).setDatabaseConverter(
                new MockHomeSharedConverter(home + "/.history/databaseScriptsFiles.test"));
        // Do environment conversion
        configurationManager.initDefaultFiles();
        bcm.initialize();
        if (dbVersion != null) {
            TestUtils.setField(bcm.versionProvider, "originalDbVersion",
                    new CompoundVersionDetails(dbVersion, "FROM_DB", 0L));
        }

        ArtifactoryContext artifactoryContext = initArtifactoryContext(dbVersion, bcm.artifactoryHome, bcm);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(bcm.artifactoryHome);
        bcm.convertersManager.serviceConvert(artifactoryConverter);
        bcm.convertersManager.convertPostInit();
        bcm.convertersManager.afterContextReady();
    }

    /**
     * Creates BasicConfigurationManager
     *
     * @throws IOException
     */
    protected BasicConfigurationManager createBasicConfigurationManager(File home,
            ArtifactoryServiceVersion homeVersion, ArtifactoryDbProperties dbProperties,
            ArtifactoryHome artifactoryHome, boolean withFlag) throws IOException {
        createHomeEnvironment(home, homeVersion);
        // Create ArtifactoryHome
        when(dbProperties.getDbType()).thenReturn(DERBY);
        when(dbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE)).thenReturn("tomcat-jdbc");
        when(dbProperties.getProperty(DbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE)).thenReturn("tomcat-jdbc");
        ReflectionTestUtils.setField(artifactoryHome, "dbProperties", dbProperties);
        ArtifactoryHome.bind(artifactoryHome);
        artifactoryHome.initArtifactorySystemProperties();
        KeyUtils.initKey(new File(artifactoryHome.getSysConfig().helper().getMasterKeyFile()), DEFAULT_MASTER_KEY);
        // Create configuration manager
        FileWatcher fileWatchingManager = mock(FileWatcher.class);
        configurationManager = ConfigurationManagerImpl.create(
                new ArtifactoryConfigurationAdapter(artifactoryHome),
                fileWatchingManager);
        artifactoryHome.initDBProperties();
        if (withFlag) {
            ReflectionTestUtils.setField(artifactoryHome, "dbProperties", new ArtifactoryDbProperties(artifactoryHome) {
                public Optional<Boolean> allowNonPgFlag() {
                    return Optional.of(true);
                }
            });
        }
        return new BasicConfigurationManager(artifactoryHome, configurationManager);
    }

    protected void createHomeEnvironment(File home, ArtifactoryServiceVersion homeVersion) throws IOException {
        File artDir = new File(home, ".artifactory");
        File artHa = new File(home, ".artifactory-ha");

        FileUtils.deleteDirectory(artDir);
        FileUtils.deleteDirectory(artHa);
        FileUtils.deleteDirectory(new File(home, ".history"));
        FileUtils.deleteQuietly(new File(home, ".artifactory/var/data/artifactory.properties"));

        home.mkdir();
        Assert.assertTrue(artDir.mkdir());
        Assert.assertTrue(artHa.mkdir());
        Assert.assertTrue(new File(home, ".history").mkdir());
        Assert.assertTrue(new File(artHa, "ha-data").mkdir());
        Assert.assertTrue(new File(artHa, "ha-etc").mkdir());
        if (homeVersion != null) {
            File dataDir = Paths.get(artDir.toString(), "var", "data", "artifactory").toFile();
            File etcDir = Paths.get(artDir.toString(), "var", "etc", "artifactory").toFile();
            Assert.assertTrue(dataDir.mkdirs());
            Assert.assertTrue(etcDir.mkdirs());
            Properties artifactoryProperties = createArtifactoryProperties(homeVersion);
            String basePath = "/converters/templates/home/" + homeVersion.getVersion();
            try (FileOutputStream out = new FileOutputStream(new File(dataDir, "artifactory.properties"))) {
                artifactoryProperties.store(out, "");
            }
            try (FileOutputStream out = new FileOutputStream(new File(etcDir, ARTIFACTORY_HA_NODE_PROPERTIES_FILE))) {
                Properties haNodeProperties = createHaNodeProperties(home);
                haNodeProperties.store(out, "");
            }
            if (!homeVersion.isCurrent()) {
                safeResourceCopy(etcDir, basePath, LOGBACK_CONFIG_FILE_NAME);
                safeResourceCopy(etcDir, basePath, MIME_TYPES_FILE_NAME);
            }

            // Update cluster files
            artifactoryProperties = createArtifactoryProperties(homeVersion);
            try (FileOutputStream out = new FileOutputStream(
                    home + "/.artifactory-ha/ha-data/" + "artifactory.properties")) {
                artifactoryProperties.store(out, "");
            }
            if (!homeVersion.isCurrent()) {
                // HA
                File haEtc = new File(home + "/.artifactory-ha/ha-etc/");
                safeResourceCopy(haEtc, basePath, LOGBACK_CONFIG_FILE_NAME);
                safeResourceCopy(haEtc, basePath, MIME_TYPES_FILE_NAME);
            }
        }
    }

    protected boolean isArtifactoryLocalHomePropertiesHasBeenUpdated(File home) {
        return isFileChanged(home + "/.history/homeFiles.test");
    }

    protected boolean isArtifactorySharedHomePropertiesHasBeenUpdated(File home) {
        return isFileChanged(home + "/.history/syncFiles.test");
    }

    protected boolean isServiceConvertWasRun(File home) {
        return isFileChanged(home + "/.history/db.test");
    }

    protected boolean isArtifactoryPropertiesHasBeenUpdated(File home) {
        return isFileChanged(home + "/.artifactory/var/etc/artifactory/artifactory.properties");
    }

    private boolean isFileChanged(String path) {
        File file = new File(path);
        return file.exists();
    }

    private void safeResourceCopy(File etcDir, String basePath, String fileName) {
        String resourcePath = basePath + "/" + fileName;
        try {
            String logbackValue = ResourceUtils.getResourceAsString(resourcePath);
            File destFile = new File(etcDir, fileName);
            FileUtils.write(destFile, logbackValue);
        } catch (Exception e) {
            // Not too much of an issue
            System.out.println("Could not copy the file from " + resourcePath + " due to " + e.getMessage());
        }
    }

    private Properties createHaNodeProperties(File home) {
        Properties properties = new Properties();
        properties.put(HaNodeProperties.PROP_NODE_ID, "pom");
        properties.put("cluster.home", new File(home, ".artifactory-ha").getAbsolutePath());
        // TODO: Test block when conf as slave
        properties.put(HaNodeProperties.PROP_PRIMARY, "true");
        properties.put(HaNodeProperties.PROP_ENABLED, "true");
        return properties;
    }

    protected Properties createArtifactoryProperties(ArtifactoryServiceVersion version) {
        Properties properties = new Properties();
        properties.put(artifactoryServiceVersion.getPropertyName(), version.getVersion());
        properties.put(artifactoryServiceRevision.getPropertyName(), "" + version.getRevision());
        properties.put(artifactoryServiceTimestamp.getPropertyName(), "" + DateTimeUtils.currentTimeMillis());
        return properties;
    }

    protected void createHaBackupFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File backupDir = new File(artHaDir, "ha-backup");
        FileUtils.forceMkdir(backupDir);
        File backupFile = new File(backupDir, "backup.txt");
        FileUtils.touch(backupFile);
    }

    protected void createArtifactorySystemPropertiesFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File artifactorySystemPropertiesFile = new File(etcDir, "artifactory.system.properties");
        FileUtils.writeStringToFile(artifactorySystemPropertiesFile, "test=just_test");
    }

    protected static void createBinaryStoreXmlFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File binaryStoreXml = new File(etcDir, "binarystore.xml");
        FileUtils.writeStringToFile(binaryStoreXml, "<config version=\"2\">\n" +
                "    <chain template=\"file-system\"/>\n" +
                "</config>");
    }

    protected static void createMimeTypeXmlFile(File home) throws IOException {
        File artHaDir = new File(home, ".artifactory-ha");
        File etcDir = new File(artHaDir, "ha-etc");
        FileUtils.forceMkdir(etcDir);
        File mimeTypeXml = new File(etcDir, "mimetypes.xml");
        String mimeTypesString = ResourceUtils.getResourceAsString("/converters/templates/home/3.1.0/mimetypes.xml");
        FileUtils.writeStringToFile(mimeTypeXml, mimeTypesString);
    }

    private ArtifactoryContext initArtifactoryContext(ArtifactoryServiceVersion dbVersion,
            ArtifactoryHome artifactoryHome, BasicConfigurationManager bcm) {
        artifactoryHome.getArtifactoryProperties().setProperty(ConstantValues.test.getPropertyName(), "true");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.databaseConvertersSkip.getPropertyName(), "true");

        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);

        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        SecurityService securityService = mock(SecurityService.class);
        AccessService accessService = mock(AccessService.class);
        GrpcPlatformConfigClient platformConfigClient = mock(GrpcPlatformConfigClient.class);
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        when(addonsManager.lockdown()).thenReturn(false);
        CentralConfigService centralConfigService = spy(CentralConfigService.class);

        when(addonsManager.lockdown()).thenReturn(false);

        when(artifactoryContext.beanForType(ArtifactoryCommonDbPropertiesService.class))
                .thenReturn(new MockDbPropertiesService(dbVersion, 1));
        when(artifactoryContext.beanForType(ArtifactoryStateManager.class))
                .thenReturn(new MockArtifactoryStateManager());
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class))
                .thenReturn(new MockArtifactoryServersCommonService(dbVersion));
        when(artifactoryContext.beanForType(AddonsManager.class))
                .thenReturn(addonsManager);
        when(artifactoryContext.beanForType(AccessService.class))
                .thenReturn(accessService);
        when(accessService.getPlatformConfigClient()).thenReturn(platformConfigClient);
        when(platformConfigClient.getConfig("general",ConfigurationModel.class))
                .thenReturn(null);
        when(artifactoryContext.beanForType(ArtifactoryHome.class)).thenReturn(artifactoryHome);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);

        when(repositoryService.exists(any(RepoPath.class))).thenReturn(false);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(centralConfigService.getMutableDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(artifactoryContext.getConverterManager()).thenReturn(bcm.convertersManager);
        when(artifactoryContext.getVersionProvider()).thenReturn(bcm.versionProvider);
        when(artifactoryContext.beanForType(ArtifactoryHome.class)).thenReturn(artifactoryHome);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(artifactoryContext.beanForType(SecurityService.class))
                .thenReturn(securityService);
        when(artifactoryContext.getConverterManager()).thenReturn(bcm.convertersManager);
        when(artifactoryContext.getVersionProvider()).thenReturn(bcm.versionProvider);
        when(artifactoryContext.beanForType(CentralConfigService.class))
                .thenReturn(centralConfigService);

        when(artifactoryContext.getConverterManager()).thenReturn(bcm.convertersManager);
        when(artifactoryContext.getVersionProvider()).thenReturn(bcm.versionProvider);
        InternalRepositoryConfigService internalRepositoryConfigService =
                mock(InternalRepositoryConfigService.class);
        when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(
                internalRepositoryConfigService);
        when(artifactoryContext.beanForType(InternalRepositoryConfigService.class)).thenReturn(
                internalRepositoryConfigService);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class))
                .thenReturn(artifactoryTracer);
        when(artifactoryContext.beanForType(FederatedRepoConfigEnrichmentService.class))
                .thenReturn(mock(FederatedRepoConfigEnrichmentService.class));
        return artifactoryContext;
    }
}
