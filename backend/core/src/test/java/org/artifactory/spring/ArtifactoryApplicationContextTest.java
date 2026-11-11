package org.artifactory.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.WebstartAddon;
import org.artifactory.addon.keys.KeysAddon;
import org.artifactory.addon.license.LicensesAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.keys.TrustedKeysService;
import org.artifactory.api.release.bundle.ReleaseBundleService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.CentralConfigServiceImpl;
import org.artifactory.converter.ConverterManager;
import org.artifactory.importexport.ImportIsDisabledException;
import org.artifactory.logging.LoggingService;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.missioncontrol.MissionControlService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.access.AccessService;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class ArtifactoryApplicationContextTest extends ArtifactoryHomeBoundTest {

    @Test
    public void testContextCreated() {
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        Map<String, ContextCreationListener> contextCreationListeners = new HashMap<>();
        ContextCreationListener contextCreationListener = mock(ContextCreationListener.class);
        contextCreationListeners.put("test", contextCreationListener);
        when(applicationContext.beansForType(ContextCreationListener.class))
                .thenReturn(contextCreationListeners);
        doCallRealMethod().when(applicationContext).contextCreated();
        ConverterManager converterManager = mock(ConverterManager.class);
        doNothing().when(converterManager).convertAsync();
        when(applicationContext.getConverterManager()).thenReturn(converterManager);
        when(applicationContext.getArtifactoryHome()).thenReturn(homeStub);
        applicationContext.contextCreated();
        verify(converterManager, times(1)).convertAsync();
    }

    @Test(dataProvider = "shouldActOnMissionControlProvider")
    public void testImportFromWithMC(boolean shouldActOnMissionControl) throws IOException {
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        ImportSettings settings = new ImportSettingsImpl(homeStub.getBackupDir());
        settings.setShouldActOnMissionControl(shouldActOnMissionControl);
        prepareArtifactoryPropertiesFile();
        doCallRealMethod().when(applicationContext).importFrom(settings);
        prepareApplicationContext(applicationContext);
        settings.setExcludeContent(true);
        MissionControlService missionControlService = mock(MissionControlService.class);
        when(applicationContext.beanForType(MissionControlService.class)).thenReturn(missionControlService);
        applicationContext.importFrom(settings);
        if (shouldActOnMissionControl) {
            verify(missionControlService, times(1)).importFrom(settings);
        } else {
            verify(missionControlService, times(0)).importFrom(settings);
        }
    }

    @Test(dataProvider = "shouldActOnMissionControlProvider")
    public void testExportToWithMC(boolean shouldActOnMissionControl) throws IOException {
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        ExportSettings settings = new ExportSettingsImpl(homeStub.getBackupDir());
        settings.setExcludeBuilds(true);
        settings.setIncludeMetadata(false);
        settings.setIncremental(true);
        settings.setShouldActOnMissionControl(shouldActOnMissionControl);
        prepareArtifactoryPropertiesFile();
        doCallRealMethod().when(applicationContext).exportTo(settings);
        prepareApplicationContext(applicationContext);
        settings.setExcludeContent(true);
        MissionControlService missionControlService = mock(MissionControlService.class);
        when(applicationContext.beanForType(MissionControlService.class)).thenReturn(missionControlService);
        applicationContext.exportTo(settings);
        if (shouldActOnMissionControl) {
            verify(missionControlService, times(1)).exportTo(any(ExportSettings.class));
        } else {
            verify(missionControlService, times(0)).exportTo(any(ExportSettings.class));
        }
    }

    @Test(expectedExceptions = ImportIsDisabledException.class)
    public void testImportWithExpiredLicenseAndEnabledContent() throws IOException {
        //given
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        ImportSettings settings = new ImportSettingsImpl(homeStub.getBackupDir());
        prepareArtifactoryPropertiesFile();
        doCallRealMethod().when(applicationContext).importFrom(settings);
        AddonsManager addonsManager = prepareAddonManager();
        when(addonsManager.isReadOnlyLicense()).thenReturn(true);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        // when
        applicationContext.importFrom(settings);
    }

    @Test
    public void testImportWithExpiredLicenseAndExcludeContent() throws IOException {
        // given
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        prepareApplicationContext(applicationContext);
        ImportSettings settings = new ImportSettingsImpl(homeStub.getBackupDir());
        settings.setExcludeContent(true);
        prepareArtifactoryPropertiesFile();
        doCallRealMethod().when(applicationContext).importFrom(settings);
        AddonsManager addonsManager = prepareAddonManager();
        when(addonsManager.isReadOnlyLicense()).thenReturn(true);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(applicationContext.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        // when
        applicationContext.importFrom(settings);
        // then
        verify(repositoryService, times(0)).importFrom(settings);
        assertFalse(settings.getStatusHolder().isError());
    }

    @Test
    public void testImportWithValidLicenseAndContentEnabled() throws IOException {
        // given
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        prepareApplicationContext(applicationContext);
        ImportSettings settings = new ImportSettingsImpl(homeStub.getBackupDir());
        prepareArtifactoryPropertiesFile();
        doCallRealMethod().when(applicationContext).importFrom(settings);
        AddonsManager addonsManager = prepareAddonManager();
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(applicationContext.getRepositoryService()).thenReturn(repositoryService);
        // when
        applicationContext.importFrom(settings);
        // then
        verify(repositoryService).importFrom(settings);
        assertFalse(settings.getStatusHolder().isError());
    }

    private void prepareApplicationContext(ArtifactoryApplicationContext applicationContext) {
        TaskService taskService = mock(TaskService.class);
        when(applicationContext.getTaskService()).thenReturn(taskService);
        AccessService accessService = mock(AccessService.class);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);
        AddonsManager addonsManager = prepareAddonManager();
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ReleaseBundleService releaseBundleService = mock(ReleaseBundleService.class);
        when(applicationContext.beanForType(ReleaseBundleService.class)).thenReturn(releaseBundleService);
        TrustedKeysService trustedKeysService = mock(TrustedKeysService.class);
        when(applicationContext.beanForType(TrustedKeysService.class)).thenReturn(trustedKeysService);
        SignedUrlService signedUrlService = mock(SignedUrlService.class);
        BuildFacade buildFacade = mock(BuildFacade.class);
        LoggingService loggingService = mock(LoggingService.class);
        MetadataEventService metadataEventService = mock(MetadataEventService.class);
        when(applicationContext.beanForType(SignedUrlService.class)).thenReturn(signedUrlService);
        when(applicationContext.beanForType(BuildFacade.class)).thenReturn(buildFacade);
        when(applicationContext.beanForType(LoggingService.class)).thenReturn(loggingService);
        when(applicationContext.beanForType(MetadataEventService.class)).thenReturn(metadataEventService);
        CentralConfigService centralConfigService = mock(CentralConfigServiceImpl.class);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        SecurityService securityService = mock(SecurityService.class);
        when(applicationContext.getSecurityService()).thenReturn(securityService);
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(applicationContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(applicationContext.getArtifactoryHome()).thenReturn(homeStub);
    }

    @Nonnull
    private AddonsManager prepareAddonManager() {
        AddonsManager addonsManager = mock(AddonsManager.class);
        KeysAddon keysAddon = mock(KeysAddon.class);
        WebstartAddon webstartAddon = mock(WebstartAddon.class);
        LicensesAddon licensesAddon = mock(LicensesAddon.class);
        PluginsAddon pluginsAddon = mock(PluginsAddon.class);
        SupportAddon supportAddon = mock(SupportAddon.class);
        ReleaseBundleAddon releaseBundleAddon = mock(ReleaseBundleAddon.class);
        when(addonsManager.addonByType(KeysAddon.class)).thenReturn(keysAddon);
        when(addonsManager.addonByType(WebstartAddon.class)).thenReturn(webstartAddon);
        when(addonsManager.addonByType(LicensesAddon.class)).thenReturn(licensesAddon);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);
        return addonsManager;
    }

    private void prepareArtifactoryPropertiesFile() throws IOException {
        Path path = Paths.get(homeStub.getBackupDir().getAbsolutePath(), "artifactory.properties");
        File file = path.toFile();
        if (file.exists()) {
            assertTrue(file.delete());
        }
        assertTrue(file.createNewFile());
        file.deleteOnExit();
        Files.write(path, Collections.singleton("artifactory.version=7.x-SNAPSHOT"), StandardCharsets.UTF_8);
    }

    @DataProvider
    public Object[][] shouldActOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false},
        };
    }

    @Test
    void refresh_shouldCatchException_whenSetReadyThrowsException() {
        // Arrange
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        doCallRealMethod().when(applicationContext).refresh();
        doThrow(new RuntimeException("TEST")).when(applicationContext).setReady(false, "refresh");
        ListAppender<ILoggingEvent> listAppender = createLoggerAppender();
        // Act & Assert
        assertThrows("TEST", RuntimeException.class, applicationContext::refresh);
        verify(applicationContext).setReady(false, "refresh");
    }

    private ListAppender<ILoggingEvent> createLoggerAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ArtifactoryApplicationContext.class);
        logger.setLevel(Level.ERROR);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
        return listAppender;
    }
}