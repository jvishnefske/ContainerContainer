package org.artifactory.traffic;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.context.TrafficContext;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.traffic.entry.TransferEntry;
import org.artifactory.traffic.entry.UploadEntry;
import org.artifactory.traffic.stats.RepoTrafficStatsService;
import org.artifactory.traffic.summary.TrafficSummaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.artifactory.common.ConstantValues.trafficCollectionActive;
import static org.artifactory.common.ConstantValues.trafficVersion2Enabled;
import static org.artifactory.common.ConstantValues.trafficVersion3Enabled;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

class TrafficServiceAddExtendedInfoTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryRequest artifactoryRequest;
    @Mock
    private TrafficSummaryService trafficSummaryService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        doReturn("cool-user").when(securityService).currentUsername();
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(trafficCollectionActive.getPropertyName(), "true");
        artifactoryProperties.setProperty(trafficVersion2Enabled.getPropertyName(), "true");
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "true");
        doReturn(projectsService).when(artifactoryContext).beanForType(ProjectsService.class);
        doReturn("cool").when(projectsService).getProjectKeyOfRepository("cool-docker-local");
        doReturn("jfrt").when(projectsService).getProjectKeyOfRepository("jfrt-docker-virtual");
        doReturn("origin-project").when(projectsService).getProjectKeyOfRepository("origin-repo");
        doReturn(repositoryService).when(artifactoryContext).beanForType(RepositoryService.class);
        RepoConfig repoConfigDockerLocal = mock(RepoConfig.class);
        doReturn(repoConfigDockerLocal).when(repositoryService).getRepoConfigByKey("cool-docker-local");
        doReturn(LOCAL).when(repoConfigDockerLocal).getRepoType();
        doReturn(Docker).when(repoConfigDockerLocal).getPackageType();
        RepoConfig repoConfigDockerVirtual = mock(RepoConfig.class);
        doReturn(repoConfigDockerVirtual).when(repositoryService).getRepoConfigByKey("jfrt-docker-virtual");
        doReturn(VIRTUAL).when(repoConfigDockerVirtual).getRepoType();
        doReturn(Docker).when(repoConfigDockerVirtual).getPackageType();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        doReturn("jfrt-docker-virtual").when(artifactoryRequest).getRepoKey();
        TrafficContextThreadLocal.bind("jfrt-docker-virtual");
        doReturn(trafficSummaryService).when(artifactoryContext).beanForType(TrafficSummaryService.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        TrafficContextThreadLocal.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void v3smoke() {
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));
        TrafficEntry entryV3 = TrafficEntry.builder()
                .repoPath("cool-docker-local:b")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();
        trafficService.handleTrafficEntryV3(entryV3);
        assertNotNull(trafficService.trafficEntryV3);
        assertEquals("cool-docker-local", trafficService.trafficEntryV3.getRepoKey());
        assertEquals("cool", trafficService.trafficEntryV3.getProjectKey());
        assertEquals("cool-user", trafficService.trafficEntryV3.getUsername());
        assertEquals("local", trafficService.trafficEntryV3.getRepoType());
        assertEquals("docker", trafficService.trafficEntryV3.getPackageType());
        assertEquals("jfrt-docker-virtual", trafficService.trafficEntryV3.getOriginRepoKey());
        assertEquals("jfrt", trafficService.trafficEntryV3.getOriginProjectKey());
        assertEquals("virtual", trafficService.trafficEntryV3.getOriginRepoType());
        assertEquals("docker", trafficService.trafficEntryV3.getOriginPackageType());
        verify(trafficService).handleMetrics(any(), any());

        TrafficContextThreadLocal.unbind();
        trafficService.handleTrafficEntryV3(entryV3);
        assertNotNull(trafficService.trafficEntryV3);
        assertEquals("cool-docker-local", trafficService.trafficEntryV3.getRepoKey());
        assertEquals("cool", trafficService.trafficEntryV3.getProjectKey());
        assertEquals("cool-user", trafficService.trafficEntryV3.getUsername());
        assertEquals("local", trafficService.trafficEntryV3.getRepoType());
        assertEquals("docker", trafficService.trafficEntryV3.getPackageType());
        assertEquals("cool-docker-local", trafficService.trafficEntryV3.getOriginRepoKey());
        assertEquals("cool", trafficService.trafficEntryV3.getOriginProjectKey());
        assertEquals("local", trafficService.trafficEntryV3.getOriginRepoType());
        assertEquals("docker", trafficService.trafficEntryV3.getOriginPackageType());
        verify(trafficService, times(2)).handleMetrics(any(), any());
    }

    @Test
    void v2smoke() {
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));
        TrafficEntry entryV2 = TrafficEntry.builder()
                .repoPath("cool-docker-local:b")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();
        trafficService.handleTrafficEntry(entryV2);
        assertNotNull(trafficService.trafficEntryV2);
        assertEquals("cool-docker-local", trafficService.trafficEntryV2.getRepoKey());
        assertEquals("cool", trafficService.trafficEntryV2.getProjectKey());
        assertEquals("cool-user", trafficService.trafficEntryV2.getUsername());
        assertEquals("local", trafficService.trafficEntryV2.getRepoType());
        assertEquals("docker", trafficService.trafficEntryV2.getPackageType());
        assertEquals("jfrt-docker-virtual", trafficService.trafficEntryV2.getOriginRepoKey());
        assertEquals("jfrt", trafficService.trafficEntryV2.getOriginProjectKey());
        assertEquals("virtual", trafficService.trafficEntryV2.getOriginRepoType());
        assertEquals("docker", trafficService.trafficEntryV2.getOriginPackageType());
        verify(trafficService).handleMetrics(any(), any());

        TrafficContextThreadLocal.unbind();
        trafficService.handleTrafficEntry(entryV2);
        assertNotNull(trafficService.trafficEntryV2);
        assertEquals("cool-docker-local", trafficService.trafficEntryV2.getRepoKey());
        assertEquals("cool", trafficService.trafficEntryV2.getProjectKey());
        assertEquals("cool-user", trafficService.trafficEntryV2.getUsername());
        assertEquals("local", trafficService.trafficEntryV2.getRepoType());
        assertEquals("docker", trafficService.trafficEntryV2.getPackageType());
        assertEquals("cool-docker-local", trafficService.trafficEntryV2.getOriginRepoKey());
        assertEquals("cool", trafficService.trafficEntryV2.getOriginProjectKey());
        assertEquals("local", trafficService.trafficEntryV2.getOriginRepoType());
        assertEquals("docker", trafficService.trafficEntryV2.getOriginPackageType());
        verify(trafficService, times(2)).handleMetrics(any(), any());
    }

    @Test
    void v1smoke() {
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService);
        UploadEntry uploadEntry = new UploadEntry("cool-docker-local:b", 10, 1000, "1.1.1.1", "cool-user-agent");
        trafficService.handleTrafficEntry(uploadEntry);

        assertNotNull(trafficService.trafficEntryV1);
        assertEquals("cool-docker-local:b", trafficService.trafficEntryV1.getRepoPath());
        assertEquals(10, trafficService.trafficEntryV1.getContentLength());
        assertEquals(1000, trafficService.trafficEntryV1.getDuration());
        assertEquals("1.1.1.1", trafficService.trafficEntryV1.getUserAddress());
        assertEquals("cool-user-agent", trafficService.trafficEntryV1.getUserIdentifier());
    }

    @Test
    void testSetOriginInfo_whenTrafficContextIsNull_shouldUseDefaultValues() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService);

        TrafficEntry trafficEntry = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        RepoConfig defaultRepoConfig = mock(RepoConfig.class);
        doReturn(LOCAL).when(defaultRepoConfig).getRepoType();
        doReturn(Docker).when(defaultRepoConfig).getPackageType();

        // Ensure TrafficContext is null
        TrafficContextThreadLocal.unbind();

        // When
        trafficService.setExtendedInfo(trafficEntry, defaultRepoConfig, "cool-docker-local");

        // Then
        assertEquals("cool-docker-local", trafficEntry.getOriginRepoKey());
        assertEquals("cool", trafficEntry.getOriginProjectKey());
        assertEquals("local", trafficEntry.getOriginRepoType());
        assertEquals("docker", trafficEntry.getOriginPackageType());
    }

    @Test
    void testSetOriginInfo_whenTrafficContextIsNull_andDefaultRepoConfigIsNull_shouldSetNullTypes() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService);

        TrafficEntry trafficEntry = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        // Ensure TrafficContext is null
        TrafficContextThreadLocal.unbind();

        // When
        trafficService.setExtendedInfo(trafficEntry, null, "cool-docker-local");

        // Then
        assertEquals("cool-docker-local", trafficEntry.getOriginRepoKey());
        assertEquals("cool", trafficEntry.getOriginProjectKey());
        assertNull(trafficEntry.getOriginRepoType());
        assertNull(trafficEntry.getOriginPackageType());
    }

    @Test
    void testSetOriginInfo_withTrafficContextBound_shouldUseContextValues() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService);

        TrafficEntry trafficEntry = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        RepoConfig defaultRepoConfig = mock(RepoConfig.class);
        doReturn(LOCAL).when(defaultRepoConfig).getRepoType();
        doReturn(Docker).when(defaultRepoConfig).getPackageType();

        // When - traffic context is already bound to "jfrt-docker-virtual" in setUp()
        trafficService.setExtendedInfo(trafficEntry, defaultRepoConfig, "cool-docker-local");

        // Then - should use origin values from traffic context
        assertEquals("jfrt-docker-virtual", trafficEntry.getOriginRepoKey());
        assertEquals("jfrt", trafficEntry.getOriginProjectKey());
        assertEquals("virtual", trafficEntry.getOriginRepoType());
        assertEquals("docker", trafficEntry.getOriginPackageType());
    }

    @Test
    void testSetOriginInfo_withMockedTrafficContext_shouldTestFallbackScenarios() {
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService);

        // Scenario 1: Null origin repo key - fallback to default
        TrafficEntry trafficEntry1 = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        RepoConfig defaultRepoConfig = mock(RepoConfig.class);
        doReturn(LOCAL).when(defaultRepoConfig).getRepoType();
        doReturn(Docker).when(defaultRepoConfig).getPackageType();

        TrafficContext trafficContext1 = mock(TrafficContext.class);
        doReturn(null).when(trafficContext1).getOriginRepoKey(); // null origin repo key
        doReturn("origin-project").when(trafficContext1).getOriginProjectKey();
        doReturn(null).when(trafficContext1).getOriginRepoConfig();

        try (var mockedStatic = mockStatic(TrafficContextThreadLocal.class)) {
            mockedStatic.when(TrafficContextThreadLocal::getTrafficContext).thenReturn(trafficContext1);
            mockedStatic.when(TrafficContextThreadLocal::getUsername).thenReturn(null);

            trafficService.setExtendedInfo(trafficEntry1, defaultRepoConfig, "cool-docker-local");

            assertEquals("cool-docker-local", trafficEntry1.getOriginRepoKey()); // fallback to default
            assertEquals("cool", trafficEntry1.getOriginProjectKey()); // fallback to default (derived from repo key)
            assertEquals("local", trafficEntry1.getOriginRepoType()); // from default config
            assertEquals("docker", trafficEntry1.getOriginPackageType()); // from default config
        }

        // Scenario 2: Null origin project key - fallback to default
        TrafficEntry trafficEntry2 = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        RepoConfig defaultRepoConfig2 = mock(RepoConfig.class);
        doReturn(VIRTUAL).when(defaultRepoConfig2).getRepoType();
        doReturn(Maven).when(defaultRepoConfig2).getPackageType();

        TrafficContext trafficContext2 = mock(TrafficContext.class);
        doReturn("origin-repo").when(trafficContext2).getOriginRepoKey();
        doReturn(null).when(trafficContext2).getOriginProjectKey(); // null origin project key
        doReturn(null).when(trafficContext2).getOriginRepoConfig();

        try (var mockedStatic = mockStatic(TrafficContextThreadLocal.class)) {
            mockedStatic.when(TrafficContextThreadLocal::getTrafficContext).thenReturn(trafficContext2);
            mockedStatic.when(TrafficContextThreadLocal::getUsername).thenReturn(null);

            trafficService.setExtendedInfo(trafficEntry2, defaultRepoConfig2, "cool-docker-local");

            assertEquals("origin-repo", trafficEntry2.getOriginRepoKey()); // from context
            assertEquals("origin-project", trafficEntry2.getOriginProjectKey()); // derived from origin repo key
            assertEquals("virtual", trafficEntry2.getOriginRepoType()); // from default config
            assertEquals("maven", trafficEntry2.getOriginPackageType()); // from default config
        }

        // Scenario 3: Null origin repo config and null default - leave types null
        TrafficEntry trafficEntry3 = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        TrafficContext trafficContext3 = mock(TrafficContext.class);
        doReturn("origin-repo").when(trafficContext3).getOriginRepoKey();
        doReturn("origin-project").when(trafficContext3).getOriginProjectKey();
        doReturn(null).when(trafficContext3).getOriginRepoConfig(); // null origin repo config

        try (var mockedStatic = mockStatic(TrafficContextThreadLocal.class)) {
            mockedStatic.when(TrafficContextThreadLocal::getTrafficContext).thenReturn(trafficContext3);
            mockedStatic.when(TrafficContextThreadLocal::getUsername).thenReturn(null);

            trafficService.setExtendedInfo(trafficEntry3, null, "cool-docker-local"); // null default config

            assertEquals("origin-repo", trafficEntry3.getOriginRepoKey());
            assertEquals("origin-project", trafficEntry3.getOriginProjectKey());
            assertNull(trafficEntry3.getOriginRepoType()); // null because no config available
            assertNull(trafficEntry3.getOriginPackageType()); // null because no config available
        }

        // Scenario 4: Null origin repo config with valid default - use default config
        TrafficEntry trafficEntry4 = TrafficEntry.builder()
                .repoPath("cool-docker-local:test-path")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        RepoConfig defaultRepoConfig4 = mock(RepoConfig.class);
        doReturn(REMOTE).when(defaultRepoConfig4).getRepoType();
        doReturn(Maven).when(defaultRepoConfig4).getPackageType();

        TrafficContext trafficContext4 = mock(TrafficContext.class);
        doReturn("origin-repo").when(trafficContext4).getOriginRepoKey();
        doReturn("origin-project").when(trafficContext4).getOriginProjectKey();
        doReturn(null).when(trafficContext4).getOriginRepoConfig(); // null origin repo config

        try (var mockedStatic = mockStatic(TrafficContextThreadLocal.class)) {
            mockedStatic.when(TrafficContextThreadLocal::getTrafficContext).thenReturn(trafficContext4);
            mockedStatic.when(TrafficContextThreadLocal::getUsername).thenReturn(null);

            trafficService.setExtendedInfo(trafficEntry4, defaultRepoConfig4, "cool-docker-local");

            assertEquals("origin-repo", trafficEntry4.getOriginRepoKey());
            assertEquals("origin-project", trafficEntry4.getOriginProjectKey());
            assertEquals("remote", trafficEntry4.getOriginRepoType()); // from default config
            assertEquals("maven", trafficEntry4.getOriginPackageType()); // from default config
        }
    }

    @Test
    void testHandleTrafficEntryV3_whenV3Inactive_shouldNotLog() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));

        // Disable V3
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "false");

        TrafficEntry entryV3 = TrafficEntry.builder()
                .repoPath("cool-docker-local:b")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        // When
        trafficService.handleTrafficEntryV3(entryV3);

        // Then
        assertNull(trafficService.trafficEntryV3); // Should not log when V3 is inactive
        verify(trafficService).handleMetrics(any(), any()); // Should still handle metrics

        // Reset V3 to enabled
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "true");
    }

    @Test
    void testHandleTrafficEntryV3_withJfrogFolderPath_shouldNotLog() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));

        // Test with JFROG folder in repo path (assuming .jfrog is the folder name)
        TrafficEntry entryV3 = TrafficEntry.builder()
                .repoPath("cool-docker-local:.jfrog/some-file")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        // When
        trafficService.handleTrafficEntryV3(entryV3);

        // Then
        assertNull(trafficService.trafficEntryV3); // Should not log when path contains JFROG folder
        verify(trafficService).handleMetrics(any(), any()); // Should still handle metrics
    }

    @Test
    void testHandleTrafficEntryV3_withDifferentActions_shouldLogAll() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));

        // Test UPLOAD action
        TrafficEntry uploadEntry = TrafficEntry.builder()
                .repoPath("cool-docker-local:upload-file")
                .action(TrafficEntry.TrafficAction.UPLOAD)
                .build();

        trafficService.handleTrafficEntryV3(uploadEntry);
        assertNotNull(trafficService.trafficEntryV3);
        assertEquals(TrafficEntry.TrafficAction.UPLOAD, trafficService.trafficEntryV3.getAction());

        // Test REDIRECT action
        TrafficEntry redirectEntry = TrafficEntry.builder()
                .repoPath("cool-docker-local:redirect-file")
                .action(TrafficEntry.TrafficAction.REDIRECT)
                .build();

        trafficService.handleTrafficEntryV3(redirectEntry);
        assertNotNull(trafficService.trafficEntryV3);
        assertEquals(TrafficEntry.TrafficAction.REDIRECT, trafficService.trafficEntryV3.getAction());

        verify(trafficService, times(2)).handleMetrics(any(), any());
    }

    @Test
    void testHandleTrafficEntryV3_withNullRepoConfig_shouldStillLog() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceForTest trafficService =
                spy(new TrafficServiceForTest(serversService, repositoryService, projectsService, securityService,
                        trafficSummaryService));

        // Mock repository service to return null for repo config
        doReturn(null).when(repositoryService).getRepoConfigByKey("unknown-repo");
        doReturn(null).when(projectsService).getProjectKeyOfRepository("unknown-repo");

        TrafficEntry entryV3 = TrafficEntry.builder()
                .repoPath("unknown-repo:some-file")
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .build();

        // When
        trafficService.handleTrafficEntryV3(entryV3);

        // Then
        assertNotNull(trafficService.trafficEntryV3); // Should still log even with null repo config
        assertEquals("unknown-repo", trafficService.trafficEntryV3.getRepoKey());
        assertNull(trafficService.trafficEntryV3.getProjectKey());
        assertEquals("cool-user", trafficService.trafficEntryV3.getUsername());
        assertNull(trafficService.trafficEntryV3.getRepoType()); // null because repo config is null
        assertNull(trafficService.trafficEntryV3.getPackageType()); // null because repo config is null
        verify(trafficService).handleMetrics(any(), any());
    }

    @Test
    void testIsV3Active_shouldReturnCorrectValue() {
        // Given
        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        TrafficServiceImpl trafficService = new TrafficServiceImpl();
        trafficService.setServersService(serversService);

        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();

        // Test when V3 is enabled
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "true");
        assertTrue(trafficService.isV3Active());

        // Test when V3 is disabled
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "false");
        assertFalse(trafficService.isV3Active());

        // Reset to enabled
        artifactoryProperties.setProperty(trafficVersion3Enabled.getPropertyName(), "true");
    }

    private static class TrafficServiceForTest extends TrafficServiceImpl {

        private TrafficEntry trafficEntryV3;
        private TrafficEntry trafficEntryV2;
        private TransferEntry trafficEntryV1;

        public TrafficServiceForTest(ArtifactoryServersCommonService serversService,
                RepositoryService repositoryService,
                ProjectsService projectsService,
                SecurityService securityService,
                TrafficSummaryService trafficSummaryService) {
            super();
            setServersService(serversService);
            setRepositoryService(repositoryService);
            setProjectsService(projectsService);
            setSecurityService(securityService);
            setTrafficSummaryService(trafficSummaryService);
            setRepoTrafficStatsService(mock(RepoTrafficStatsService.class));
        }

        @Override
        protected void handleMetrics(RepoConfig repoConfig, String name) {
            // empty
        }

        @Override
        protected void logTrafficEntryV3(TrafficEntry trafficEntryV3) {
            this.trafficEntryV3 = trafficEntryV3;
        }

        @Override
        protected void logTrafficEntryV2(TrafficEntry trafficEntryV2) {
            this.trafficEntryV2 = trafficEntryV2;
        }

        @Override
        protected void logTransferEntry(TransferEntry transferEntry) {
            this.trafficEntryV1 = transferEntry;
        }
    }
}
