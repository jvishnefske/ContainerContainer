package org.artifactory.traffic;

import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.traffic.entry.DownloadEntry;
import org.artifactory.traffic.entry.TransferEntry;
import org.artifactory.traffic.stats.RepoTrafficStatsService;
import org.artifactory.traffic.summary.TrafficSummaryService;
import org.artifactory.traffic.v2.TrafficLoggerV2;
import org.artifactory.traffic.v3.TrafficLoggerV3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.io.File;

import static org.artifactory.common.ConstantValues.systemFolderContractEnabled;
import static org.artifactory.common.ConstantValues.trafficCollectionActive;
import static org.artifactory.common.ConstantValues.trafficOriginFromMdcEnabled;
import static org.artifactory.common.ConstantValues.trafficVersion2Enabled;
import static org.artifactory.common.ConstantValues.trafficVersion3Enabled;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author dudim
 */
class TrafficServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ArtifactoryServersCommonService serversService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRequest artifactoryRequest;
    @Mock
    private TrafficSummaryService trafficSummaryService;
    @Mock
    private RepoTrafficStatsService repoTrafficStatsService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void beforeEach() {
        autoCloseable = openMocks(this);
        homeStub.setProperty(trafficCollectionActive, "true");
        homeStub.setProperty(trafficVersion2Enabled, "true");
        homeStub.setProperty(trafficVersion3Enabled, "true");
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(RepoType.Docker);
        when(repositoryService.remoteRepoDescriptorByKey(any())).thenReturn(null);
        when(repositoryService.virtualRepoDescriptorByKey(any())).thenReturn(null);
        doReturn(repositoryService).when(artifactoryContext).beanForType(RepositoryService.class);
        RepoConfig repoConfigDockerLocal = mock(RepoConfig.class);
        doReturn(repoConfigDockerLocal).when(repositoryService).getRepoConfigByKey("cool-docker-local");
        doReturn(LOCAL).when(repoConfigDockerLocal).getRepoType();
        doReturn(Docker).when(repoConfigDockerLocal).getPackageType();
        RepoConfig repoConfigDockerVirtual = mock(RepoConfig.class);
        doReturn(repoConfigDockerVirtual).when(repositoryService).getRepoConfigByKey("jfrt-docker-virtual");
        doReturn(VIRTUAL).when(repoConfigDockerVirtual).getRepoType();
        doReturn(Docker).when(repoConfigDockerVirtual).getPackageType();
        doReturn(projectsService).when(artifactoryContext).beanForType(ProjectsService.class);
        doReturn("cool").when(projectsService).getProjectKeyOfRepository("cool-docker-local");
        doReturn("jfrt").when(projectsService).getProjectKeyOfRepository("jfrt-docker-virtual");
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        doReturn("jfrt-docker-virtual").when(artifactoryRequest).getRepoKey();
        TrafficContextThreadLocal.bind("jfrt-docker-virtual");
        doReturn(trafficSummaryService).when(artifactoryContext).beanForType(TrafficSummaryService.class);
    }

    @AfterEach
    void afterEach() throws Exception {
        homeStub.setProperty(trafficCollectionActive, trafficCollectionActive.getDefValue());
        homeStub.setProperty(trafficOriginFromMdcEnabled, trafficOriginFromMdcEnabled.getDefValue());
        homeStub.setProperty(trafficVersion2Enabled, trafficVersion2Enabled.getDefValue());
        homeStub.setProperty(trafficVersion3Enabled, trafficVersion3Enabled.getDefValue());
        homeStub.setProperty(systemFolderContractEnabled, systemFolderContractEnabled.getDefValue());
        CurrentActivity.REPO.remove();
        TrafficContextThreadLocal.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testGetDefaultTrafficLogDir() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        doReturn(new File("originalPath")).when(trafficServiceSpy).getDefaultTrafficLogDir();
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("originalPath", logDirPath);
    }

    @Test
    void testGetNotExistingPathTrafficLogDir() {
        setTrafficLogsDirSystemPropertyProperty("NotExistPath");
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        doReturn(new File("originalPath")).when(trafficServiceSpy).getDefaultTrafficLogDir();
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("originalPath", logDirPath);
    }

    @Test
    void testGetNewTrafficLogDir() {
        setTrafficLogsDirSystemPropertyProperty("/");
        TrafficServiceImpl trafficServiceSpy = new TrafficServiceImpl();
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("/", logDirPath);
    }

    @Test
    void testGetEmptyDirectoryTrafficLogDir() {
        setTrafficLogsDirSystemPropertyProperty("");
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        doReturn(new File("originalPath")).when(trafficServiceSpy).getDefaultTrafficLogDir();
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("originalPath", logDirPath);
    }

    @Test
    void testGetSpaceDirectoryTrafficLogDir() {
        setTrafficLogsDirSystemPropertyProperty(" ");
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        doReturn(new File("originalPath")).when(trafficServiceSpy).getDefaultTrafficLogDir();
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("originalPath", logDirPath);
    }

    @Test
    void testGetNullDirectoryTrafficLogDir() {
        setTrafficLogsDirSystemPropertyProperty(null);
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setServersService(serversService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        doReturn(new File("originalPath")).when(trafficServiceSpy).getDefaultTrafficLogDir();
        String logDirPath = trafficServiceSpy.getTrafficLogDir().getPath();
        assertEquals("originalPath", logDirPath);
    }

    private void setTrafficLogsDirSystemPropertyProperty(String dirPath) {
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.trafficLogsDirectory.getPropertyName(), dirPath);
    }

    @Test
    void testHandleMetricsWhenRepoConfigIsNull() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        assertDoesNotThrow(() -> trafficServiceSpy.handleMetrics(null, "DOWNLOAD"));
    }

    @Test
    void testGetRepoClassByRepoConfig() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        assertEquals("remote", trafficServiceSpy.getRepoClassByRepoConfig(repoConfig));
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        assertEquals("virtual", trafficServiceSpy.getRepoClassByRepoConfig(repoConfig));
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        assertEquals("local", trafficServiceSpy.getRepoClassByRepoConfig(repoConfig));
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        assertEquals("local", trafficServiceSpy.getRepoClassByRepoConfig(repoConfig));
    }

    @Test
    void testSetExtendedInfoWhenRepoConfigIsNull() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        ProjectsService projectsService = mock(ProjectsService.class);
        when(projectsService.getProjectKeyOfRepository(any())).thenReturn("projectname");
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("cool-user");
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setRepositoryService(repositoryService);
        TrafficEntry entryV2 = TrafficEntry.builder()
                .repoPath("a:b")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();
        trafficServiceSpy.setExtendedInfo(entryV2, null, "a");

        assertEquals("projectname", entryV2.getProjectKey());
        assertEquals("a", entryV2.getRepoKey());
        assertEquals("cool-user", entryV2.getUsername());
        assertNull(entryV2.getRepoType());
        assertNull(entryV2.getPackageType());
    }

    @Test
    void testMetricsInfoCreation() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        assertDoesNotThrow(() -> trafficServiceSpy.handleMetrics(repoConfig, "DOWNLOAD"));
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, false, true",
            "false, true, false, true",
            "false, false, true, true",
            "false, false, false, false"
    })
    void testIsActive(boolean v1, boolean v2, boolean v3, boolean expected) {
        homeStub.setProperty(trafficCollectionActive, String.valueOf(v1));
        homeStub.setProperty(trafficVersion2Enabled, String.valueOf(v2));
        homeStub.setProperty(trafficVersion3Enabled, String.valueOf(v3));
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        assertEquals(expected, trafficService.isActive());
    }

    @Test
    void testLoggingTrafficEntryV1() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        TransferEntry transferEntry = new DownloadEntry("cool-repo:cool-path", 1L, 0L, "127.0.0.1");
        try (
                MockedStatic<TrafficLogger> loggerMockV1 = mockStatic(TrafficLogger.class);
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntry(transferEntry);
            loggerMockV1.verify(() -> TrafficLogger.logTransferEntry(transferEntry));
            loggerMockV2.verify(() -> TrafficLoggerV2.logEntry(any()), never());
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(any()), never());
        }
    }

    @Test
    void testLoggingTrafficEntryV2() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setTrafficSummaryService(trafficSummaryService);
        trafficServiceSpy.setRepoTrafficStatsService(mock(RepoTrafficStatsService.class));
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:b")
                .build();
        try (
                MockedStatic<TrafficLogger> loggerMockV1 = mockStatic(TrafficLogger.class);
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntry(entry);
            loggerMockV1.verify(() -> TrafficLogger.logTransferEntry(any()), never());
            loggerMockV2.verify(() -> TrafficLoggerV2.logEntry(entry));
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(entry));
        }
    }

    private TrafficServiceImpl newV3TrafficServiceSpy() {
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setTrafficSummaryService(trafficSummaryService);
        trafficServiceSpy.setRepoTrafficStatsService(mock(RepoTrafficStatsService.class));
        return trafficServiceSpy;
    }

    @Test
    void testLoggingTrafficEntryV3() {
        TrafficServiceImpl trafficServiceSpy = newV3TrafficServiceSpy();
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:b")
                .build();
        try (
                MockedStatic<TrafficLogger> loggerMockV1 = mockStatic(TrafficLogger.class);
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntryV3(entry);
            loggerMockV1.verify(() -> TrafficLogger.logTransferEntry(any()), never());
            loggerMockV2.verify(() -> TrafficLoggerV2.logEntry(entry), never());
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(entry));
        }
    }

    @Test
    void jfrogTrafficIsExcludedFromV3() {
        TrafficServiceImpl trafficServiceSpy = newV3TrafficServiceSpy();
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:.jfrog/repodata/metadata.json")
                .build();
        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntryV3(entry);
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(any()), never());
        }
        verify(trafficSummaryService, never()).collectTrafficEntry(any());
    }

    @Test
    void evidenceTrafficIsBilledToV3() {
        TrafficServiceImpl trafficServiceSpy = newV3TrafficServiceSpy();
        // .evidence is a system folder flagged isBilled=true -> billed and logged to V3 as usual.
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:.evidence/sha256/sig.json")
                .build();
        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntryV3(entry);
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(entry));
        }
        verify(trafficSummaryService).collectTrafficEntry(entry);
    }

    @Test
    void jfrogPrefixedPathIsExcludedFromV3WhenContractEnabled() {
        TrafficServiceImpl trafficServiceSpy = newV3TrafficServiceSpy();
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:.jfrogbackup/file")
                .build();
        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntryV3(entry);
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(any()), never());
        }
        verify(trafficSummaryService, never()).collectTrafficEntry(any());
    }

    @Test
    void contractDisabledStillExcludesJfrogFromV3() {
        homeStub.setProperty(systemFolderContractEnabled, "false");
        TrafficServiceImpl trafficServiceSpy = newV3TrafficServiceSpy();
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:.jfrog/repodata/metadata.json")
                .build();
        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntryV3(entry);
            loggerMockV3.verify(() -> TrafficLoggerV3.logEntry(any()), never());
        }
        verify(trafficSummaryService, never()).collectTrafficEntry(any());
    }

    @Test
    void resolveOriginRepoKey_shouldReturnMdcValue_whenMdcIsSet() {
        // Arrange
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        CurrentActivity.REPO.put("my-virtual-repo");

        // Act
        String result = trafficService.resolveOriginRepoKey("my-local-repo");

        // Assert
        assertEquals("my-virtual-repo", result);
    }

    @Test
    void resolveOriginRepoKey_shouldReturnTrafficContextValue_whenMdcIsEmpty() {
        // Arrange
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        CurrentActivity.REPO.remove();

        // Act - TrafficContext was bound with "jfrt-docker-virtual" in beforeEach
        String result = trafficService.resolveOriginRepoKey("my-local-repo");

        // Assert
        assertEquals("jfrt-docker-virtual", result);
    }

    @Test
    void resolveOriginRepoKey_shouldReturnDefault_whenMdcAndTrafficContextAreEmpty() {
        // Arrange
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        CurrentActivity.REPO.remove();
        TrafficContextThreadLocal.unbind();

        // Act
        String result = trafficService.resolveOriginRepoKey("fallback-repo");

        // Assert
        assertEquals("fallback-repo", result);
    }

    @Test
    void resolveOriginRepoKey_shouldIgnoreMdc_whenFeatureFlagDisabled() {
        // Arrange
        homeStub.setProperty(trafficOriginFromMdcEnabled, "false");
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        CurrentActivity.REPO.put("mdc-repo");

        // Act - TrafficContext was bound with "jfrt-docker-virtual" in beforeEach
        String result = trafficService.resolveOriginRepoKey("my-local-repo");

        // Assert - MDC skipped, falls through to TrafficContext
        assertEquals("jfrt-docker-virtual", result);
    }

    @Test
    void handleTrafficEntry_shouldSetVirtualOrigin_whenMdcSetAndTrafficContextUnbound() {
        // Simulates the Jersey callback scenario: TrafficContext is already unbound
        // but MDC still holds the virtual repo key from RequestFilter.
        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setTrafficSummaryService(trafficSummaryService);
        trafficServiceSpy.setRepoTrafficStatsService(repoTrafficStatsService);

        // Arrange - unbind TrafficContext (simulating post-unbind callback),
        // but keep MDC set to virtual repo
        TrafficContextThreadLocal.unbind();
        CurrentActivity.REPO.put("jfrt-docker-virtual");

        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:some/path")
                .build();

        // Act
        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 = mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 = mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntry(entry);
        }

        // Assert - origin should be from MDC (virtual), not from the entry's local repo
        assertEquals("jfrt-docker-virtual", entry.getOriginRepoKey());
        assertEquals("jfrt", entry.getOriginProjectKey());
        assertEquals("virtual", entry.getOriginRepoType());
        assertEquals("docker", entry.getOriginPackageType());
    }

    @Test
    void handleTrafficEntry_shouldUseLegacyOrigin_whenMdcFeatureFlagDisabled() {
        homeStub.setProperty(trafficOriginFromMdcEnabled, "false");

        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setTrafficSummaryService(trafficSummaryService);
        trafficServiceSpy.setRepoTrafficStatsService(repoTrafficStatsService);

        // MDC is set but should be ignored when flag is off
        CurrentActivity.REPO.put("should-be-ignored");

        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:some/path")
                .build();

        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 =
                        mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 =
                        mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntry(entry);
        }

        // MDC is ignored; origin resolved from TrafficContext instead
        assertEquals("jfrt-docker-virtual", entry.getOriginRepoKey());
        assertEquals("jfrt", entry.getOriginProjectKey());
        assertEquals("virtual", entry.getOriginRepoType());
        assertEquals("docker", entry.getOriginPackageType());
    }

    @Test
    void handleTrafficEntry_shouldFallbackToDefault_whenLegacyAndContextUnbound() {
        homeStub.setProperty(trafficOriginFromMdcEnabled, "false");

        TrafficServiceImpl trafficServiceSpy = spy(new TrafficServiceImpl());
        trafficServiceSpy.setRepositoryService(repositoryService);
        trafficServiceSpy.setProjectsService(projectsService);
        trafficServiceSpy.setSecurityService(securityService);
        trafficServiceSpy.setTrafficSummaryService(trafficSummaryService);
        trafficServiceSpy.setRepoTrafficStatsService(repoTrafficStatsService);

        TrafficContextThreadLocal.unbind();
        CurrentActivity.REPO.put("should-be-ignored");

        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .repoPath("cool-docker-local:some/path")
                .build();

        try (
                MockedStatic<TrafficLoggerV2> loggerMockV2 =
                        mockStatic(TrafficLoggerV2.class);
                MockedStatic<TrafficLoggerV3> loggerMockV3 =
                        mockStatic(TrafficLoggerV3.class)
        ) {
            trafficServiceSpy.handleTrafficEntry(entry);
        }

        // Legacy path without TrafficContext falls back to default repo info
        assertEquals("cool-docker-local", entry.getOriginRepoKey());
        assertEquals("cool", entry.getOriginProjectKey());
        assertEquals("local", entry.getOriginRepoType());
        assertEquals("docker", entry.getOriginPackageType());
    }
}
