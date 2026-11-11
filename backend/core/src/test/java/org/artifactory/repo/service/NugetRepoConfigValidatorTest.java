package org.artifactory.repo.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.util.DoesNotExistException;
import org.artifactory.util.RemoteRepoCachePeriodValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.service.NugetRepoConfigValidator.validateNugetPackageTypeConfig;
import static org.artifactory.repo.service.NugetRepoConfigValidator.validateNugetRemoteCachePeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class has the test cases to validate nuget package type
 *
 * @author prasannasudharsanr
 */
@ExtendWith(MockitoExtension.class)
class NugetRepoConfigValidatorTest {

    @Mock
    private RepositoryConfigService repositoryConfigService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        logAppender = addAppender();
    }

    @AfterEach
    void tearDown() {
        detachLogAppender(logAppender);
    }

    /**
     * To test the skipping of virtual repo validation for nuget package type,
     * when virtualRepoConfig is null
     */
    @Test
    void testValidNugetRepoConfigWhenVirtualConfigIsNull() {
        validateNugetPackageTypeConfig(null, repositoryConfigService);

        verify(repositoryConfigService, never()).getRepoConfigByKey(anyString());
        asserNullLogMessage(logAppender, "Validation skipped because, Virtual repo config is null");
    }

    /**
     * To test the skipping of virtual repo validation for nuget package type,,
     * when RepositoryConfigService is null
     */
    @Test
    void testValidNugetRepoConfigWhenRepositoryConfigServiceIsNull() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        validateNugetPackageTypeConfig(virtualRepoConfig, null);

        verify(repositoryConfigService, never()).getRepoConfigByKey(anyString());
        asserNullLogMessage(logAppender, "Validation skipped because, RepositoryConfigService is null");
    }

    /**
     * To test the virtual repo validation for nuget package type, whether, the validator throws
     * Invalid Names exception, when any one selected repo is not enabled for normalization
     */
    @Test
    void testHandleNormalisedVirtualForNugetThrowsInvalidNameExceptionWhenAddingNonNormalizedRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");

        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);

        InvalidOperationException invalidOperationException = assertThrows(
                InvalidOperationException.class,
                () -> validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Nuget Repository virtual-repo must have only normalized repositories",
                invalidOperationException.getMessage());
        assertInvalidExceptionLogsForNormalisedVirtual(logAppender);
    }

    /**
     * To test the virtual repo validation for nuget package type, whether, the validator throws
     * Invalid Names exception, when any one selected repo is enabled for normalization during the normalised virtual
     * repo creation
     */
    @Test
    void testHandleNonNormalisedVirtualNugetThrowsInvalidNameExceptionWhenAddingNormalizedRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);

        InvalidOperationException invalidOperationException = assertThrows(
                InvalidOperationException.class,
                () -> validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Nuget Repository virtual-repo must have only non-normalized repositories",
                invalidOperationException.getMessage());
        assertInvalidExceptionLogsForNonNormalisedVirtual(logAppender);
    }

    /**
     * To test the update scenario:
     * Parent Virtual Repo is already present with Normalized version flag as false.
     * But during update, the parameter is passed as true. And the child local repo is also true.
     * In this case, the validator should throw InvalidOperationException as the parent is not normalized during
     * creation.
     */
    @Test
    void testHandleNonNormalisedVirtualNugetThrowsInvalidNameExceptionWhenAddingNormalizedRepoDuringUpdate() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        VirtualRepoConfig existingVirtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        existingVirtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "false");
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.getVirtualRepoConfigByKey("virtual-repo")).thenReturn(existingVirtualRepoConfig);

        InvalidOperationException invalidOperationException = assertThrows(
                InvalidOperationException.class,
                () -> validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Nuget Repository virtual-repo must have only non-normalized repositories",
                invalidOperationException.getMessage());
        assertInvalidExceptionLogsForNonNormalisedVirtual(logAppender);
    }

    /**
     * To test the update scenario:
     * Parent Virtual Repo is already present with Normalized version flag as true.
     * But during update, the parameter is passed as false. And the child local repo is also false.
     * In this case, the validator should throw InvalidOperationException as the parent is normalized during creation.
     */
    @Test
    void testHandleNonNormalisedVirtualNugetThrowsInvalidNameExceptionWhenAddingNonNormalizedRepoDuringUpdate() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "false");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "false");
        VirtualRepoConfig existingVirtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        existingVirtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.getVirtualRepoConfigByKey("virtual-repo")).thenReturn(existingVirtualRepoConfig);

        InvalidOperationException invalidOperationException = assertThrows(
                InvalidOperationException.class,
                () -> validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Nuget Repository virtual-repo must have only normalized repositories",
                invalidOperationException.getMessage());
        assertInvalidExceptionLogsForNormalisedVirtual(logAppender);
    }


    private static void asserNullLogMessage(ListAppender<ILoggingEvent> listAppender, String message) {
        List<ILoggingEvent> logsList = listAppender.list;
        ILoggingEvent iLoggingEvent = logsList.get(0);

        assertEquals(message, iLoggingEvent.getMessage());
        assertEquals(Level.WARN, iLoggingEvent.getLevel());
    }

    private static void assertInvalidExceptionLogsForNormalisedVirtual(ListAppender<ILoggingEvent> listAppender) {
        List<ILoggingEvent> logsList = listAppender.list;
        ILoggingEvent iLoggingEvent = logsList.get(0);
        assertEquals("Failed to create {} nuget repo of key {}, with {} repositories of {}",
                iLoggingEvent.getMessage());
        Object[] arguments = iLoggingEvent.getArgumentArray();

        assertEquals(Level.WARN, iLoggingEvent.getLevel());
        assertEquals("normalized", arguments[0]);
        assertEquals("virtual-repo", arguments[1]);
        assertEquals("non normalized", arguments[2]);
        assertEquals("local-repo", arguments[3]);
    }

    private static void assertInvalidExceptionLogsForNonNormalisedVirtual(ListAppender<ILoggingEvent> listAppender) {
        List<ILoggingEvent> logsList = listAppender.list;
        ILoggingEvent iLoggingEvent = logsList.get(0);
        assertEquals("Failed to create {} nuget repo of key {}, with {} repositories of {}",
                iLoggingEvent.getMessage());
        Object[] arguments = iLoggingEvent.getArgumentArray();

        assertEquals(Level.WARN, iLoggingEvent.getLevel());
        assertEquals("non-normalized", arguments[0]);
        assertEquals("virtual-repo", arguments[1]);
        assertEquals("normalized", arguments[2]);
        assertEquals("local-repo", arguments[3]);
    }

    /**
     * To test the virtual repo validation for nuget package type, whether, the validator throws
     * Does not exists exception, when selected repo is not found in repository config service
     */
    @Test
    void testHandleVirtualForNugetThrowsDoesNotExistExceptionWhenSelectedRepoNotFound() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");

        DoesNotExistException doesNotExistException = assertThrows(
                DoesNotExistException.class,
                () -> validateNugetPackageTypeConfig(virtualRepoConfig,
                        repositoryConfigService));
        assertEquals("Selected Repo keylocal-repo not found", doesNotExistException.getMessage());
        verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    /**
     * To test the virtual repo validation for nuget package type, whether, the selected repos are having
     * Normalized version flag enabled
     */
    @Test
    void testHandleVirtualForNugetPackageType() {
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put("enableNormalizedVersion", "true");

        LocalRepoConfig localRepoConfig = Mockito.mock(LocalRepoConfig.class);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");
        when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(localRepoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);

        validateNugetPackageTypeConfig(virtualRepoConfig, repositoryConfigService);

        verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }


    private ListAppender<ILoggingEvent> addAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(NugetRepoConfigValidator.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(NugetRepoConfigValidator.class);
        logger.detachAppender(appender);
    }

    @Test
    void testisNugetSmartRepoNormalizedForNullValues() {
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(null));
        RemoteRepoConfig repoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(repoConfig.getRepoTypeConfig()).thenReturn(null);
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(null));
        RemoteRepositoryRepoTypeConfig repoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getContentSynchronisation()).thenReturn(null);
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(repoConfig));
        ContentSynchronisation contentSynchronisation = Mockito.mock(ContentSynchronisation.class);
        when(contentSynchronisation.isEnabled()).thenReturn(false);
        when(repoTypeConfig.getContentSynchronisation()).thenReturn(contentSynchronisation);
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(repoConfig));
        when(contentSynchronisation.isEnabled()).thenReturn(true);
        when(repoConfig.getPackageTypeConfig()).thenReturn(null);
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(repoConfig));
    }

    @Test
    void testisNugetSmartRepoNormalizedForNonNugetRepo() {
        RemoteRepoConfig repoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        assertFalse(NugetRepoConfigValidator.isNugetSmartRepoNormalized(repoConfig));
    }

    @Test
    void testisNugetSmartRepoNormalizedForNormalisedRepo() {
        ContentSynchronisation contentSynchronisation = Mockito.mock(ContentSynchronisation.class);
        when(contentSynchronisation.isEnabled()).thenReturn(true);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getContentSynchronisation()).thenReturn(contentSynchronisation);
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put("enableNormalizedVersion", "true");
        RemoteRepoConfig repoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        assertTrue(NugetRepoConfigValidator.isNugetSmartRepoNormalized(repoConfig));
    }

    @Test
    void testValidateNugetPackageTypeConfigWithChangesMapFindsRepoInChanges() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");

        RepoConfigChange change = Mockito.mock(RepoConfigChange.class);
        Mockito.when(change.getNewRepoConfig()).thenReturn(localRepoConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(
                RepoDetailsType.LOCAL, List.of(change)
        );

        NugetRepoConfigValidator.validateNugetPackageTypeConfig(virtualRepoConfig, repositoryConfigService, changes);
        Mockito.verify(repositoryConfigService, Mockito.never()).getRepoConfigByKey("local-repo");
    }

    @Test
    void testValidateNugetPackageTypeConfigWithChangesMapFallsBackToService() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        localRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.NuGet);
        virtualRepoConfig.getPackageTypeConfig().put("enableNormalizedVersion", "true");
        virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().add("local-repo");

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of();

        Mockito.when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);

        NugetRepoConfigValidator.validateNugetPackageTypeConfig(virtualRepoConfig, repositoryConfigService, changes);
        Mockito.verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    @Test
    void testGetRepoConfigFromChangesFindsInChanges() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        RepoConfigChange change = Mockito.mock(RepoConfigChange.class);
        Mockito.when(change.getNewRepoConfig()).thenReturn(localRepoConfig);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(
                RepoDetailsType.LOCAL, List.of(change)
        );

        RepoConfig result =
                NugetRepoConfigValidator.getRepoConfigFromChanges("local-repo", repositoryConfigService, changes);
        assertEquals(localRepoConfig, result);
        Mockito.verify(repositoryConfigService, Mockito.never()).getRepoConfigByKey("local-repo");
    }

    @Test
    void testGetRepoConfigFromChangesFallsBackToService() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.NuGet);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of();

        Mockito.when(repositoryConfigService.getRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);

        RepoConfig result =
                NugetRepoConfigValidator.getRepoConfigFromChanges("local-repo", repositoryConfigService, changes);
        assertEquals(localRepoConfig, result);
        Mockito.verify(repositoryConfigService).getRepoConfigByKey("local-repo");
    }

    // --- validateNugetRemoteCachePeriod ---

    @Test
    void validateNugetRemoteCachePeriodDelegatesToRemoteRepoCachePeriodValidator() {
        RemoteRepositoryRepoTypeConfig repoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getRetrievalCachePeriodSecs()).thenReturn(60L);

        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getUrl()).thenReturn("https://chocolatey.org/api/v2");
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(false);
        when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);

        try (MockedStatic<RemoteRepoCachePeriodValidator> validatorMock =
                     mockStatic(RemoteRepoCachePeriodValidator.class)) {
            validateNugetRemoteCachePeriod(remoteRepoConfig);
            validatorMock.verify(() -> RemoteRepoCachePeriodValidator.assertValidValue(
                    eq("https://chocolatey.org/api/v2"), eq(60L), eq(false), eq(RepoType.NuGet)));
        }
    }

    @Test
    void validateNugetRemoteCachePeriodIsNoOpWhenRemoteRepoConfigIsNull() {
        try (MockedStatic<RemoteRepoCachePeriodValidator> validatorMock =
                     mockStatic(RemoteRepoCachePeriodValidator.class)) {
            validateNugetRemoteCachePeriod(null);
            validatorMock.verify(() -> RemoteRepoCachePeriodValidator.assertValidValue(
                    any(), Mockito.anyLong(), Mockito.anyBoolean(), any()), never());
        }
    }

    @Test
    void validateNugetRemoteCachePeriodIsNoOpWhenRepoTypeConfigIsNull() {
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(null);

        try (MockedStatic<RemoteRepoCachePeriodValidator> validatorMock =
                     mockStatic(RemoteRepoCachePeriodValidator.class)) {
            validateNugetRemoteCachePeriod(remoteRepoConfig);
            validatorMock.verify(() -> RemoteRepoCachePeriodValidator.assertValidValue(
                    any(), Mockito.anyLong(), Mockito.anyBoolean(), any()), never());
        }
    }

    @Test
    void validateNugetRemoteCachePeriodPropagatesIllegalArgumentExceptionFromValidator() {
        RemoteRepositoryRepoTypeConfig repoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getRetrievalCachePeriodSecs()).thenReturn(60L);

        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getUrl()).thenReturn("https://chocolatey.org/api/v2");
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(false);
        when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);

        try (MockedStatic<RemoteRepoCachePeriodValidator> validatorMock =
                     mockStatic(RemoteRepoCachePeriodValidator.class)) {
            validatorMock.when(() -> RemoteRepoCachePeriodValidator.assertValidValue(
                    eq("https://chocolatey.org/api/v2"), eq(60L), eq(false), eq(RepoType.NuGet)))
                    .thenThrow(new IllegalArgumentException("cache period too low"));

            assertThrows(IllegalArgumentException.class, () -> validateNugetRemoteCachePeriod(remoteRepoConfig));
        }
    }
}