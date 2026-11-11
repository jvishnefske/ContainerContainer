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

package org.artifactory.lifecycle.webapp.servlet;

import jakarta.servlet.ServletContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.VersionProviderImpl;
import org.artifactory.lifecycle.configuration.helper.EnvContext;
import org.artifactory.lifecycle.configuration.helper.TestEnvContextBuilder;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.service.VersionHistoryServiceImpl;
import org.artifactory.storage.fs.service.VersionHistoryService;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionImpl;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.ClockUtils;
import org.jfrog.config.ifc.ConfigurationManager;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;

import static com.jfrog.sysconf.SysConfigConstants.KEY_BOOTSTRAP_KEY_READ_TIMEOUT;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.assertBinaryStoreFileNotModified;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.assertCorruptedArtifactoryKeyExist;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.assertMasterKeyExist;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.assertMasterKeyNotExists;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.assertValidBinaryStoreFileExists;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.createEnvironment;
import static org.artifactory.lifecycle.configuration.helper.BasicConfigurationManagerTestHelper.defaultFileExists;
import static org.artifactory.lifecycle.configuration.helper.FileCreationStage.afterHome;
import static org.artifactory.lifecycle.configuration.helper.FileCreationStage.beforeHome;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author gidis
 */
@Test
public class BasicConfigurationManagerTest {

    private ConfigurationManager configurationManager;

    private BasicConfigurationManager basicConfigurationManager;
    private static final String FROM = "1.1.1";
    private static final String TO = "1.1.2";

    VersionProvider versionProvider = Mockito.mock(VersionProviderImpl.class);
    VersionHistoryService versionHistoryService = Mockito.mock(VersionHistoryServiceImpl.class);

    @BeforeClass
    void setup() {
        ArtifactoryHome home = new ArtifactoryHomeStub("");
        stubDefaultVersionProviderBehavior();

        ServletContext servletContext = Mockito.mock(ServletContext.class);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(home);
        when(servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_CONVERTER_OBJ)).thenReturn(null);
        when(servletContext.getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(false);
        when(servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_HISTORY_SERVICE_OBJ))
                .thenReturn(versionHistoryService);
        when(servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_TRACER_OBJ)).thenReturn(null);
        basicConfigurationManager = new BasicConfigurationManager(servletContext);
    }

    @BeforeMethod
    void resetMockStubs() {
        Mockito.reset(versionProvider, versionHistoryService);
        stubDefaultVersionProviderBehavior();
    }

    private void stubDefaultVersionProviderBehavior() {
        ArtifactoryServiceVersion fromVersion = new ArtifactoryServiceVersionImpl(FROM, 1L, null);
        CompoundVersionDetails fromVersionDetails = new CompoundVersionDetails(
                fromVersion, null, null, 0L, new HashMap<>());
        ArtifactoryServiceVersion toVersion = new ArtifactoryServiceVersionImpl(TO, 1L, null);
        CompoundVersionDetails toVersionDetails = new CompoundVersionDetails(
                toVersion, null, null, 0L, new HashMap<>());

        when(versionProvider.getOriginalDbVersion()).thenReturn(fromVersionDetails);
        when(versionProvider.getRunningServiceVersion()).thenReturn(toVersionDetails);
    }

    @AfterClass
    public void cleanClass() {
        System.clearProperty("jf." + KEY_BOOTSTRAP_KEY_READ_TIMEOUT);
    }

    @AfterMethod
    public void cleanAfterCreateEnv() {
        System.setProperty("jf." + KEY_BOOTSTRAP_KEY_READ_TIMEOUT, "1");
        Optional.ofNullable(configurationManager).ifPresent(ConfigurationManager::destroy);
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        if (basicConfigurationManager.artifactoryHome instanceof ArtifactoryHomeStub home) {
            home.setProperty(ConstantValues.rollbackLogbackRestoreFailOnError, "false");
        }
    }

    @Test
    public void timeoutWaitingForMasterKey() {
        // Expecting BasicConfiguration to wait until timeout
        try {
            System.setProperty("jf." + KEY_BOOTSTRAP_KEY_READ_TIMEOUT, "1");
            EnvContext envContext = TestEnvContextBuilder.create().build();
            configurationManager = createEnvironment(envContext);
            assertMasterKeyExist(envContext);
            defaultFileExists(envContext);
            Assert.fail();
        } catch (Exception e) {
            System.clearProperty("jf." + KEY_BOOTSTRAP_KEY_READ_TIMEOUT);
            Assert.assertTrue(e.getMessage().contains("Failed resolving master key; Missing master key"));
        }
    }

    @Test
    public void cleanStartWithMasterKey() throws IOException {
        // Expecting successful BasicConfiguration start (master.key exist)
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        // Setting master.key AFTER_HOME creation to simulate Access (providing the master.key file)
        builder.includeMasterKey(afterHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
    }

    @Test
    public void masterKeyChecksumMatch() throws IOException {
        // Expecting BasicConfiguration to fail because of master key checksum mismatch
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        // Setting master.key AFTER_HOME creation to simulate Access (providing the master.key file)
        builder.includeMasterKeyInDb();
        builder.includeMasterKey(afterHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Master key checksum " +
            "mismatch")
    public void masterKeyChecksumMismatch() throws IOException {
        // Expecting BasicConfiguration to fail because of master key checksum mismatch
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        // Setting master.key AFTER_HOME creation to simulate Access (providing the master.key file)
        builder.includeMasterKeyInDb();
        builder.includeMasterKey2(afterHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
        Assert.fail();
    }

    @Test
    public void startExistingServerWithAllConfigFiles() throws IOException {
        // Expecting successful BasicConfiguration start (master.key exist and all other configs are valid)
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        builder.includeMasterKey(beforeHome);
        builder.includeArtifactorySystemProperties(beforeHome);
        builder.includeArtifactoryLogback(beforeHome);
        builder.includeArtifactoryMimeTypes(beforeHome);
        builder.includeArtifactoryProperties(beforeHome);
        builder.includeArtifactoryKey(beforeHome);
        builder.includeArtifactoryServiceId(beforeHome);
        builder.includeArtifactoryRootCert(beforeHome);
        builder.includeArtifactoryBinarystore(beforeHome);
        builder.includeAccessDbProperties(beforeHome);
        builder.includeAccessLogback(beforeHome);
        builder.includeAccessPrivate(beforeHome);
        builder.includeAccessRootCrt(beforeHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
    }

    @Test(enabled = false)
    public void startServerWithCorruptedDbProperties() throws IOException {
        // Expecting BasicConfiguration to fail (master.key exist but db.properties is corrupted)
        try {
            TestEnvContextBuilder builder = TestEnvContextBuilder.create();
            // Setting corrupted db.properties
            builder.includeCorruptedDbProperties(beforeHome);
            // Setting valid configuration files
            builder.includeMasterKey(beforeHome);
            builder.includeArtifactorySystemProperties(beforeHome);
            builder.includeArtifactoryLogback(beforeHome);
            builder.includeArtifactoryMimeTypes(beforeHome);
            builder.includeArtifactoryProperties(beforeHome);
            builder.includeArtifactoryKey(beforeHome);
            builder.includeArtifactoryServiceId(beforeHome);
            builder.includeArtifactoryRootCert(beforeHome);
            builder.includeAccessDbProperties(beforeHome);
            builder.includeArtifactoryBinarystore(beforeHome);
            builder.includeAccessLogback(beforeHome);
            builder.includeAccessPrivate(beforeHome);
            builder.includeAccessRootCrt(beforeHome);
            EnvContext envContext = builder.build();
            configurationManager = createEnvironment(envContext);
            assertMasterKeyExist(envContext);
            defaultFileExists(envContext);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Failed to load artifactory DB properties from"));
        }
    }

    @Test
    public void testProtectedUploadOnEmptyDb() throws IOException {
        // Expecting BasicConfiguration to delete artifactory.file since it is protected
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        // Setting corrupted root.crt
        builder.includeMasterKey(beforeHome);
        builder.includeCorruptedArtifactoryKey(beforeHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
        assertCorruptedArtifactoryKeyExist(envContext);
    }

    @Test
    public void testProtectedImport() throws IOException {
        // Expecting BasicConfiguration to download artifactory.key from DB
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        // Setting corrupted root.crt
        builder.includeMasterKey(beforeHome);
        builder.includeImportArtifactoryKey(beforeHome);
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        assertMasterKeyExist(envContext);
        defaultFileExists(envContext);
    }

    @Test
    public void testNoPullWithoutMasterKey() throws IOException {
        TestEnvContextBuilder builder = TestEnvContextBuilder.create();
        builder.includeMasterKey(beforeHome);
        builder.removeMasterEncryptionWrapper();
        builder.touchArtifactoryBinarystore();
        EnvContext envContext = builder.build();
        configurationManager = createEnvironment(envContext);
        // No master.key
        assertMasterKeyNotExists(envContext);
        // Our binarystore.xml file shouldn't have changed
        assertBinaryStoreFileNotModified(envContext, 0);
        assertValidBinaryStoreFileExists(envContext);
    }

    @Test
    void saveBackwardCompatible() throws SQLException {
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.UPGRADE);
        basicConfigurationManager.saveBackwardCompatible();

        verify(versionHistoryService, Mockito.times(1))
                .saveBackwardCompatible(FROM, TO);
    }

    @Test
    void saveBackwardCompatibleNotUpgrade() {
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        basicConfigurationManager.saveBackwardCompatible();

        verify(versionHistoryService, Mockito.never())
                .saveBackwardCompatible(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void saveUpgradeData() throws SQLException, IOException {
        when(versionHistoryService.saveConfig()).thenReturn(true);
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.UPGRADE);
        basicConfigurationManager.saveUpgradeData();

        verify(versionHistoryService, Mockito.times(1))
                .saveConfig();
        verify(versionHistoryService, Mockito.times(1))
                .saveVersionHistory(FROM, TO);
    }

    @Test
    void saveUpgradeDataConfigError() throws SQLException, IOException {
        when(versionHistoryService.saveConfig()).thenReturn(false);
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.UPGRADE);
        basicConfigurationManager.saveUpgradeData();

        verify(versionHistoryService, Mockito.times(1))
                .saveConfig();
        verify(versionHistoryService, Mockito.times(0))
                .saveVersionHistory(FROM, TO);
    }

    @Test
    void saveUpgradeDataNotUpgrade() throws SQLException, IOException {
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        basicConfigurationManager.saveUpgradeData();

        verify(versionHistoryService, Mockito.times(0))
                .saveConfig();
        verify(versionHistoryService, Mockito.times(0))
                .saveVersionHistory(FROM, TO);
    }

    @Test
    void checkRollbackAndAllowedIfNeeded() {
        when(versionHistoryService.isRollbackAllowed(TO)).thenReturn(false);
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);

        Assert.assertThrows(ArtifactoryRollbackNotAllowedException.class,
                () -> basicConfigurationManager.checkRollbackAllowedIfNeeded());
    }

    @Test
    void restoreRollbackLogbackIfNeeded() {
        ArtifactoryServiceVersion rollbackVersion = new ArtifactoryServiceVersionImpl(FROM, 1L, null);
        CompoundVersionDetails rollbackVersionDetails = new CompoundVersionDetails(
                rollbackVersion, null, null, 0L, new HashMap<>());

        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        when(versionProvider.getRunningServiceVersion()).thenReturn(rollbackVersionDetails);

        basicConfigurationManager.restoreRollbackLogbackIfNeeded();
        verify(versionHistoryService).restoreLogbackFromConfigHistory(FROM);
    }

    @Test
    void restoreRollbackLogbackIfNeededSkipsOnUpgrade() {
        when(versionProvider.getInstallationType()).thenReturn(InstallationType.UPGRADE);
        basicConfigurationManager.restoreRollbackLogbackIfNeeded();
        verify(versionHistoryService, Mockito.never()).restoreLogbackFromConfigHistory(Mockito.anyString());
    }

    @Test
    void restoreRollbackLogbackIfNeededThrowsWhenFailOnErrorEnabledAndRestoreFails() {
        ArtifactoryHomeStub home = (ArtifactoryHomeStub) basicConfigurationManager.artifactoryHome;
        home.setProperty(ConstantValues.rollbackLogbackRestoreFailOnError, "true");

        ArtifactoryServiceVersion rollbackVersion = new ArtifactoryServiceVersionImpl(FROM, 1L, null);
        CompoundVersionDetails rollbackVersionDetails = new CompoundVersionDetails(
                rollbackVersion, null, null, 0L, new HashMap<>());

        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        when(versionProvider.getRunningServiceVersion()).thenReturn(rollbackVersionDetails);
        StorageException cause = new StorageException("restore failed");
        Mockito.doThrow(cause).when(versionHistoryService).restoreLogbackFromConfigHistory(FROM);

        RollbackLogbackRestoreException exception = Assert.expectThrows(RollbackLogbackRestoreException.class,
                () -> basicConfigurationManager.restoreRollbackLogbackIfNeeded());
        Assert.assertEquals(exception.getCause(), cause);
        Assert.assertTrue(exception.getMessage().contains("unable to restore logback.xml"));
        Assert.assertTrue(exception.getMessage().contains(FROM));
        verify(versionHistoryService).restoreLogbackFromConfigHistory(FROM);
    }

    @Test
    void restoreRollbackLogbackIfNeededWarnsWhenFailOnErrorDisabledAndRestoreFails() {
        ArtifactoryHomeStub home = (ArtifactoryHomeStub) basicConfigurationManager.artifactoryHome;
        home.setProperty(ConstantValues.rollbackLogbackRestoreFailOnError, "false");

        ArtifactoryServiceVersion rollbackVersion = new ArtifactoryServiceVersionImpl(FROM, 1L, null);
        CompoundVersionDetails rollbackVersionDetails = new CompoundVersionDetails(
                rollbackVersion, null, null, 0L, new HashMap<>());

        when(versionProvider.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        when(versionProvider.getRunningServiceVersion()).thenReturn(rollbackVersionDetails);
        Mockito.doThrow(new StorageException("restore failed")).when(versionHistoryService)
                .restoreLogbackFromConfigHistory(FROM);

        basicConfigurationManager.restoreRollbackLogbackIfNeeded();
        verify(versionHistoryService).restoreLogbackFromConfigHistory(FROM);
    }

    @Test
    public void testAcquireInitLockInHaAndVersionIsNotCurrent() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);
        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v711m001.get(),
                        String.valueOf(ArtifactoryServiceVersionProvider.v711m001.get().getRevision()),
                        ClockUtils.epochMillis()));
        when(artifactoryHome.isHaConfigured()).thenReturn(true);

        assertTrue(initializationManager.shouldAcquireInitLock());
    }


    public void testAcquireInitLockInHaNewInstallation() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);
        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v711m001.get(),
                        String.valueOf(ArtifactoryServiceVersionProvider.v711m001.get().getRevision()),
                        ClockUtils.epochMillis()));
        when(artifactoryHome.isHaConfigured()).thenReturn(true);

        assertTrue(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testAcquireInitLockInHaWhenVersionIsNull() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.TRUE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);

        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(null);
        when(artifactoryHome.isHaConfigured()).thenReturn(true);

        assertTrue(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testNotAcquireInitLockWhenHaIsNotConfiguredAndVersionIsNotCurrent() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);
        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v711m001.get(),
                        String.valueOf(ArtifactoryServiceVersionProvider.v711m001.get().getRevision()),
                        ClockUtils.epochMillis()));
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        assertFalse(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testNotAcquireInitLockInNewInstallationWhenHaIsNotConfigured() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.TRUE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);
        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(null);
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        assertFalse(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testAcquireInitLockWhenVersionIsNullAndHaNotConfigured() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);
        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(null);
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        assertFalse(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testNotAcquireInitLockInHaAndVersionIsCurrent() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);

        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(compoundVersionDetails);
        when(compoundVersionDetails.isCurrent())
                .thenReturn(true);
        when(artifactoryHome.isHaConfigured()).thenReturn(true);

        assertFalse(initializationManager.shouldAcquireInitLock());
    }

    @Test
    public void testNotAcquireInitLockWhenHaIsNotConfiguredAndVersionIsCurrent() {
        VersionProviderImpl versionProvider = mock(VersionProviderImpl.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ)).thenReturn(versionProvider);
        when(servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED)).thenReturn(Boolean.FALSE);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        BasicConfigurationManager initializationManager =
                new BasicConfigurationManager(servletContext);


        when(versionProvider.getCompoundVersionDetailsFromDbIfExists())
                .thenReturn(compoundVersionDetails);
        when(compoundVersionDetails.isCurrent())
                .thenReturn(true);
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        assertFalse(initializationManager.shouldAcquireInitLock());
    }
}