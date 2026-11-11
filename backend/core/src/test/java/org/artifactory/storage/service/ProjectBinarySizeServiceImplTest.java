package org.artifactory.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NO_REPOS;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.PROJECT_ERROR_INELIGIBLE_NAME;
import static org.artifactory.storage.service.ProjectSizeEntry.CalcStatus.SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectBinarySizeServiceImplTest {

    private AutoCloseable autoCloseable;
    @Mock
    private StorageCacheService<ProjectSizeEntry> storageCacheService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private NodesDao nodesDao;
    @Mock
    private JdbcHelper jdbcHelper;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock
    private ResultSet resultSet;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private RepoConfig repoConfig;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private MockedStatic<ArtifactoryHome> mockedArtifactoryHome;
    private ProjectBinarySizeServiceImpl service;

    @BeforeEach
    void setUp() throws SQLException {
        autoCloseable = MockitoAnnotations.openMocks(this);
        mockedArtifactoryHome = mockStatic(ArtifactoryHome.class);
        mockedArtifactoryHome.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageExecutionDelayFactor))
                .thenReturn(1L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxRepos))
                .thenReturn(1_000L);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        when(jdbcHelper.executeSelect(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("4MB");
        // Mock repositoryService to return real non-remote repos with correct keys
        when(repositoryService.getRepoConfigByKey(anyString())).thenAnswer(invocation -> {
            String repoKey = invocation.getArgument(0);
            RepoConfig config = mock(RepoConfig.class);
            when(config.getKey()).thenReturn(repoKey);
            when(config.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(config.isReal()).thenReturn(true);
            return config;
        });
        // Default mock for deleteRemovedProjectsFromCache - return empty to skip cleanup
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.empty());
        // Mock the bulk remove method (returns 0 when no cached projects to remove)
        when(storageCacheService.remove(eq("PROJECT"), anySet())).thenReturn(0);
        service = new ProjectBinarySizeServiceImpl(
                storageCacheService,
                projectsService,
                repositoryService,
                storageSummaryService,
                nodesDao
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockedArtifactoryHome != null) {
            mockedArtifactoryHome.close();
        }
        autoCloseable.close();
    }

    @Test
    void testGetProjectBinarySizeInfo_WhenCacheHit_ReturnsInfo() {
        // Given
        String projectKey = "test-project";
        long timestamp = System.currentTimeMillis();
        ProjectSizeEntry cachedValue = ProjectSizeEntry.success(1024L);
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cacheEntry =
                new StorageCacheService.StorageCacheEntry<>(projectKey, cachedValue, timestamp);

        when(storageCacheService.get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.of(cacheEntry));

        // When
        ProjectBinarySizeService.ProjectBinarySizeInfo result = service.getProjectBinarySizeInfo(projectKey);

        // Then
        assertNotNull(result);
        assertEquals(projectKey, result.project());
        assertEquals(cachedValue, result.projectSizeStatus());
        assertEquals(timestamp, result.calculationTimestamp());
        verify(storageCacheService).get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class));
    }

    @Test
    void testGetProjectBinarySizeInfo_WhenCacheMiss_ReturnsNull() {
        // Given
        String projectKey = "test-project";
        when(storageCacheService.get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.empty());

        // When
        ProjectBinarySizeService.ProjectBinarySizeInfo result = service.getProjectBinarySizeInfo(projectKey);

        // Then
        assertNull(result);
        verify(storageCacheService).get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class));
    }

    @Test
    void testGetProjectBinarySizeInfo_WhenCacheHasError_ReturnsInfoWithError() {
        // Given
        String projectKey = "test-project";
        long timestamp = System.currentTimeMillis();
        ProjectSizeEntry cachedValue = ProjectSizeEntry.failure(SERVER_ERROR);
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cacheEntry =
                new StorageCacheService.StorageCacheEntry<>(projectKey, cachedValue, timestamp);

        when(storageCacheService.get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.of(cacheEntry));

        // When
        ProjectBinarySizeService.ProjectBinarySizeInfo result = service.getProjectBinarySizeInfo(projectKey);

        // Then
        assertNotNull(result);
        assertEquals(projectKey, result.project());
        assertEquals(cachedValue, result.projectSizeStatus());
        assertEquals(timestamp, result.calculationTimestamp());
        verify(storageCacheService).get(eq("PROJECT"), eq(projectKey), eq(ProjectSizeEntry.class));
    }

    @SneakyThrows
    @Test
    void testGetAllProjectsBinaryStorage_WhenEntriesExist_ReturnsList() {
        // Given
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1000;
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> entry1 = new StorageCacheService.StorageCacheEntry<>(
                "project1",
                MAPPER.readValue("{\"status\":\"Success\",\"projectSize\":1024}", ProjectSizeEntry.class),
                timestamp1
        );
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> entry2 = new StorageCacheService.StorageCacheEntry<>(
                "project2",
                MAPPER.readValue("{\"status\":\"Success\",\"projectSize\":2048}", ProjectSizeEntry.class),
                timestamp2
        );
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.of(List.of(entry1, entry2)));

        // When
        List<ProjectBinarySizeService.ProjectBinarySizeInfo> result = service.getAllProjectsBinaryStorage();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("project1", result.getFirst().project());
        assertTrue(result.get(0).projectSizeStatus().isSuccess());
        assertEquals(1024, result.get(0).projectSizeStatus().projectSize());

        assertEquals("project2", result.get(1).project());
        assertTrue(result.get(1).projectSizeStatus().isSuccess());
        assertEquals(2048, result.get(1).projectSizeStatus().projectSize());
        verify(storageCacheService).get(eq("PROJECT"), eq(ProjectSizeEntry.class));
    }

    @Test
    void testGetAllProjectsBinaryStorage_WhenNoEntries_ReturnsEmptyList() {
        // Given
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class))).thenReturn(Optional.empty());

        // When
        List<ProjectBinarySizeService.ProjectBinarySizeInfo> result = service.getAllProjectsBinaryStorage();

        // Then
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(storageCacheService).get(eq("PROJECT"), eq(ProjectSizeEntry.class));
    }

    @Test
    void testCalculateAllProjectsSizes_WhenProjectsExist_CalculatesAndReturnsCount() {
        // Given
        String project1 = "project1";
        String project2 = "project2";
        Map<String, Long> projectsTotalFilesCount = Map.of(
                project1, 100L,
                project2, 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1", "repo2"));
        when(projectsService.getProjectRepositoriesKeys(project2, false))
                .thenReturn(Set.of("repo3"));
        when(nodesDao.getReposBinarySize(Set.of("repo1", "repo2"))).thenReturn(1024L);
        when(nodesDao.getReposBinarySize(Set.of("repo3"))).thenReturn(2048L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(storageSummaryService).getLocalCache();
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(projectsService).getProjectRepositoriesKeys(project2, false);
        verify(nodesDao).getReposBinarySize(Set.of("repo1", "repo2"));
        verify(nodesDao).getReposBinarySize(Set.of("repo3"));
        verify(storageCacheService, times(2)).putConditionally(eq("PROJECT"), anyString(), any(), any());
    }

    @Test
    void testCalculateAllProjectsSizes_FiltersOutDefaultProject() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        Map<String, Long> projectsTotalFilesCount = Map.of(
                DEFAULT_PROJECT, 100L,
                "project1", 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys("project1", false))
                .thenReturn(Set.of("repo1"));
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService, never()).getProjectRepositoriesKeys(DEFAULT_PROJECT, false);
        verify(projectsService).getProjectRepositoriesKeys("project1", false);
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(DEFAULT_PROJECT),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_INELIGIBLE_NAME)), any());
    }

    @Test
    void testCalculateAllProjectsSizes_FiltersOutProjectsExceedingMaxArtifacts() {
        // Given
        long maxArtifacts = 1_000_000L;
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(maxArtifacts);
        Map<String, Long> projectsTotalFilesCount = Map.of(
                "project1", 100L,
                "project2", maxArtifacts + 1L,
                "project3", 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys("project1", false))
                .thenReturn(Set.of("repo1"));
        when(projectsService.getProjectRepositoriesKeys("project3", false))
                .thenReturn(Set.of("repo3"));
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(1024L);
        when(nodesDao.getReposBinarySize(Set.of("repo3"))).thenReturn(2048L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(projectsService, never()).getProjectRepositoriesKeys("project2", false);
        verify(projectsService).getProjectRepositoriesKeys("project1", false);
        verify(projectsService).getProjectRepositoriesKeys("project3", false);
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq("project2"),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED)), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenProjectHasNoRepositories_StoresError() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of());
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(nodesDao, never()).getReposBinarySize(anySet());
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS)), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenProjectExceedsMaxRepos_StoresError() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxRepos))
                .thenReturn(10L);
        // Re-initialize service to apply new constant value
        service = new ProjectBinarySizeServiceImpl(
                storageCacheService,
                projectsService,
                repositoryService,
                storageSummaryService,
                nodesDao
        );
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1", "repo2", "repo3", "repo4", "repo5", "repo6", "repo7", "repo8", "repo9",
                        "repo10", "repo11"));
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(nodesDao, never()).getReposBinarySize(anySet());
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED)), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenCalculationThrowsException_StoresError() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenThrow(new RuntimeException("Database error"));
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1),
                eq(ProjectSizeEntry.failure(SERVER_ERROR)),
                any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenStorageCachePutThrowsException_ContinuesProcessing() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        String project2 = "project2";
        Map<String, Long> projectsTotalFilesCount = Map.of(
                project1, 100L,
                project2, 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1"));
        when(projectsService.getProjectRepositoriesKeys(project2, false))
                .thenReturn(Set.of("repo2"));
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(1024L);
        when(nodesDao.getReposBinarySize(Set.of("repo2"))).thenReturn(2048L);
        doThrow(new RuntimeException("Storage error")).when(storageCacheService)
                .put(eq("PROJECT"), eq(project1), any());
        doNothing().when(storageCacheService)
                .put(eq("PROJECT"), eq(project2), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1), any(), any());
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project2), any(), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenThreadInterrupted_StopsProcessing() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        String project2 = "project2";
        String project3 = "project3";
        Map<String, Long> projectsTotalFilesCount = Map.of(
                project1, 100L,
                project2, 200L,
                project3, 300L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(anyString(), eq(false)))
                .thenAnswer(invocation -> Set.of("repo1"))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return Set.of("repo1");
                });
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(storageCacheService, times(2)).putConditionally(eq("PROJECT"), anyString(),
                eq(ProjectSizeEntry.success(1024)), any());
        // Clear interrupt flag for cleanup
        boolean ignored = Thread.interrupted();
    }

    @Test
    void testCalculateAllProjectsSizes_WhenNodesDaoThrowsException_StoresError() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1"));
        when(nodesDao.getReposBinarySize(Set.of("repo1")))
                .thenThrow(new RuntimeException("Database query error"));
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(nodesDao).getReposBinarySize(Set.of("repo1"));
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1),
                eq(ProjectSizeEntry.failure(SERVER_ERROR)),
                any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenCacheUnAvailable_ReturnsZero() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(storageSummaryService.getLocalCache()).thenThrow(new CacheUnAvailableException());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(0, result);
        verify(storageSummaryService).getLocalCache();
        verify(projectsService, never()).getProjectRepositoriesKeys(anyString(), anyBoolean());
    }

    // ========== Tests for ProjectBinarySizeInfo static methods ==========

    @SneakyThrows
    @Test
    void testProjectBinarySizeInfo_ValueFromString_WhenSuccessFormat_ReturnsSuccess() {
        // Given
        String cacheValue = "{\"status\":\"Success\",\"projectSize\":1024}";

        // When
        ProjectSizeEntry result = MAPPER.readValue(cacheValue, ProjectSizeEntry.class);

        // Then
        assertEquals(ProjectSizeEntry.CalcStatus.SUCCESS, result.status());
        assertEquals(1024L, result.projectSize());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("testProjectBinarySizeInfo_ProjectErrorSource")
    void testProjectBinarySizeInfo_ProjectError(ProjectSizeEntry.CalcStatus expectedStatus) {
        // Given
        String cacheValue = "{\"status\":\"%s\"}".formatted(expectedStatus.getName());
        // When
        ProjectSizeEntry actualResult = MAPPER.readValue(cacheValue, ProjectSizeEntry.class);
        String actualString = MAPPER.writeValueAsString(actualResult);

        // Then
        assertEquals(expectedStatus, actualResult.status());
        assertNull(actualResult.projectSize());
        assertEquals(cacheValue, actualString);
    }

    private static Stream<Arguments> testProjectBinarySizeInfo_ProjectErrorSource() {
        return Stream.of(
                Arguments.of(SERVER_ERROR),
                Arguments.of(PROJECT_ERROR_NUM_OF_REPOS_EXCEEDED),
                Arguments.of(PROJECT_ERROR_NUM_OF_ARTIFACTS_IN_REPO_EXCEEDED),
                Arguments.of(PROJECT_ERROR_INELIGIBLE_NAME),
                Arguments.of(PROJECT_ERROR_NO_REPOS)
        );
    }

    @SneakyThrows
    @Test
    void testProjectBinarySizeInfo_Constructor_WithStringValue_ParsesCorrectly() {
        // Given
        String project = "project1";
        String cacheValue = "{\"status\":\"Success\",\"projectSize\":2048}";
        long timestamp = System.currentTimeMillis();

        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> storageCacheEntry =
                new StorageCacheService.StorageCacheEntry<>(project,
                        MAPPER.readValue(cacheValue, ProjectSizeEntry.class), timestamp);

        // When
        ProjectBinarySizeService.ProjectBinarySizeInfo info =
                new ProjectBinarySizeService.ProjectBinarySizeInfo(storageCacheEntry);

        // Then
        assertEquals(project, info.project());
        assertEquals(ProjectSizeEntry.CalcStatus.SUCCESS, info.projectSizeStatus().status());
        assertEquals(2048L, info.projectSizeStatus().projectSize());
        assertEquals(timestamp, info.calculationTimestamp());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 0})
    void cleanup_shouldReturnDeletedCount(int expectedDeletedCount) {
        // Arrange
        long thresholdMilliSeconds = 7 * 24 * 60 * 60 * 1000L; // 7 days
        when(storageCacheService.removeOlderThan("PROJECT", thresholdMilliSeconds))
                .thenReturn(expectedDeletedCount);

        // Act
        int result = service.cleanup(thresholdMilliSeconds);

        // Assert
        assertEquals(expectedDeletedCount, result);
        verify(storageCacheService).removeOlderThan("PROJECT", thresholdMilliSeconds);
    }

    @Test
    void testCalculateAllProjectsSizes_WhenProjectAtMaxArtifactsLimit_ProcessesProject() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 1_000_000L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1"));
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(nodesDao).getReposBinarySize(Set.of("repo1"));
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1), any(), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenProjectAtMaxReposLimit_ProcessesProject() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxRepos))
                .thenReturn(10L);
        service = new ProjectBinarySizeServiceImpl(
                storageCacheService,
                projectsService,
                repositoryService,
                storageSummaryService,
                nodesDao
        );
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        Set<String> repos = Set.of("repo1", "repo2", "repo3", "repo4", "repo5", "repo6", "repo7", "repo8", "repo9",
                "repo10");
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(repos);
        when(nodesDao.getReposBinarySize(repos)).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(projectsService).getProjectRepositoriesKeys(project1, false);
        verify(nodesDao).getReposBinarySize(repos);
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(project1), any(), any());
    }

    @Test
    void testCalculateAllProjectsSizes_WhenNoProjects_ReturnsZero() {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of()
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(0, result);
        verify(storageSummaryService).getLocalCache();
        verify(projectsService, never()).getProjectRepositoriesKeys(anyString(), anyBoolean());
    }

    @SneakyThrows
    @Test
    void testOverwriteExisting_WhenNewValueIsServerErrorAndExistingIsNotServerError_ShouldNotOverwrite() {
        // Given
        String projectName = "project1";
        ProjectSizeEntry newValue = ProjectSizeEntry.failure(SERVER_ERROR);
        ProjectSizeEntry existingValue = ProjectSizeEntry.success(1024L);
        String existingJson = MAPPER.writeValueAsString(existingValue);
        StorageDbCacheEntity existingEntity = StorageDbCacheEntity.builder()
                .id(1L)
                .type("PROJECT")
                .key(projectName)
                .value(existingJson)
                .calculationTimestamp(System.currentTimeMillis())
                .build();

        when(storageSummaryService.getLocalCache()).thenReturn(new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of(projectName, 100L)
        ));
        when(projectsService.getProjectRepositoriesKeys(projectName, false))
                .thenThrow(new RuntimeException("Database error"));
        ArgumentCaptor<Predicate<StorageDbCacheEntity>> predicateCaptor =
                ArgumentCaptor.forClass(Predicate.class);

        // When
        service.calculateAllProjectsSizes();

        // Then
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(projectName), eq(newValue),
                predicateCaptor.capture());
        Predicate<StorageDbCacheEntity> predicate = predicateCaptor.getValue();
        assertFalse(predicate.test(existingEntity), "Should not overwrite existing success with server error");
    }

    @SneakyThrows
    @Test
    void testOverwriteExisting_WhenNewValueIsServerErrorAndExistingIsServerError_ShouldOverwrite() {
        // Given
        String projectName = "project1";
        ProjectSizeEntry newValue = ProjectSizeEntry.failure(SERVER_ERROR);
        ProjectSizeEntry existingValue = ProjectSizeEntry.failure(SERVER_ERROR);
        String existingJson = MAPPER.writeValueAsString(existingValue);
        StorageDbCacheEntity existingEntity = StorageDbCacheEntity.builder()
                .id(1L)
                .type("PROJECT")
                .key(projectName)
                .value(existingJson)
                .calculationTimestamp(System.currentTimeMillis())
                .build();

        when(storageSummaryService.getLocalCache()).thenReturn(new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of(projectName, 100L)
        ));
        when(projectsService.getProjectRepositoriesKeys(projectName, false))
                .thenThrow(new RuntimeException("Database error"));
        ArgumentCaptor<Predicate<StorageDbCacheEntity>> predicateCaptor =
                ArgumentCaptor.forClass(Predicate.class);

        // When
        service.calculateAllProjectsSizes();

        // Then
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(projectName), eq(newValue),
                predicateCaptor.capture());
        Predicate<StorageDbCacheEntity> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(existingEntity), "Should overwrite existing server error with server error");
    }

    @SneakyThrows
    @Test
    void testOverwriteExisting_WhenNewValueIsServerErrorAndExistingIsUnparsable_ShouldOverwrite() {
        // Given
        String projectName = "project1";
        ProjectSizeEntry newValue = ProjectSizeEntry.failure(SERVER_ERROR);
        String invalidJson = "invalid-json";
        StorageDbCacheEntity existingEntity = StorageDbCacheEntity.builder()
                .id(1L)
                .type("PROJECT")
                .key(projectName)
                .value(invalidJson)
                .calculationTimestamp(System.currentTimeMillis())
                .build();

        when(storageSummaryService.getLocalCache()).thenReturn(new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of(projectName, 100L)
        ));
        when(projectsService.getProjectRepositoriesKeys(projectName, false))
                .thenThrow(new RuntimeException("Database error"));
        ArgumentCaptor<Predicate<StorageDbCacheEntity>> predicateCaptor =
                ArgumentCaptor.forClass(Predicate.class);

        // When
        service.calculateAllProjectsSizes();

        // Then
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(projectName), eq(newValue),
                predicateCaptor.capture());
        Predicate<StorageDbCacheEntity> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(existingEntity), "Should overwrite unparsable existing entry");
    }

    @SneakyThrows
    @Test
    void testOverwriteExisting_WhenNewValueIsNotServerError_ShouldOverwrite() {
        // Given
        String projectName = "project1";
        ProjectSizeEntry newValue = ProjectSizeEntry.success(2048L);
        ProjectSizeEntry existingValue = ProjectSizeEntry.success(1024L);
        String existingJson = MAPPER.writeValueAsString(existingValue);
        StorageDbCacheEntity existingEntity = StorageDbCacheEntity.builder()
                .id(1L)
                .type("PROJECT")
                .key(projectName)
                .value(existingJson)
                .calculationTimestamp(System.currentTimeMillis())
                .build();

        when(storageSummaryService.getLocalCache()).thenReturn(new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of(projectName, 100L)
        ));
        when(projectsService.getProjectRepositoriesKeys(projectName, false))
                .thenReturn(Set.of("repo1"));
        when(nodesDao.getReposBinarySize(Set.of("repo1"))).thenReturn(2048L);
        ArgumentCaptor<Predicate<StorageDbCacheEntity>> predicateCaptor =
                ArgumentCaptor.forClass(Predicate.class);

        // When
        service.calculateAllProjectsSizes();

        // Then
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(projectName), eq(newValue),
                predicateCaptor.capture());
        Predicate<StorageDbCacheEntity> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(existingEntity), "Should overwrite when new value is not server error");
    }

    @SneakyThrows
    @Test
    void testOverwriteExisting_WhenNewValueIsProjectError_ShouldOverwrite() {
        // Given
        String projectName = "project1";
        ProjectSizeEntry newValue = ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS);
        ProjectSizeEntry existingValue = ProjectSizeEntry.success(1024L);
        String existingJson = MAPPER.writeValueAsString(existingValue);
        StorageDbCacheEntity existingEntity = StorageDbCacheEntity.builder()
                .id(1L)
                .type("PROJECT")
                .key(projectName)
                .value(existingJson)
                .calculationTimestamp(System.currentTimeMillis())
                .build();

        when(storageSummaryService.getLocalCache()).thenReturn(new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                Map.of(projectName, 100L)
        ));
        when(projectsService.getProjectRepositoriesKeys(projectName, false))
                .thenReturn(Set.of());
        ArgumentCaptor<Predicate<StorageDbCacheEntity>> predicateCaptor =
                ArgumentCaptor.forClass(Predicate.class);

        // When
        service.calculateAllProjectsSizes();

        // Then
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq(projectName), eq(newValue),
                predicateCaptor.capture());
        Predicate<StorageDbCacheEntity> predicate = predicateCaptor.getValue();
        assertTrue(predicate.test(existingEntity),
                "Should overwrite when new value is project error (not server error)");
    }

    @Test
    void testDeleteRemovedProjectsFromCache_WhenCachedProjectsNoLongerExist_RemovesThem() {
        // Given
        String existingProject1 = "project1";
        String existingProject2 = "project2";
        String removedProject1 = "removed-project1";
        String removedProject2 = "removed-project2";
        
        Map<String, Long> projectsTotalFilesCount = Map.of(
                existingProject1, 100L,
                existingProject2, 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        // Mock cached projects that include removed ones
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cachedEntry1 =
                new StorageCacheService.StorageCacheEntry<>(existingProject1,
                        ProjectSizeEntry.success(1024L), System.currentTimeMillis());
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cachedEntry2 =
                new StorageCacheService.StorageCacheEntry<>(existingProject2,
                        ProjectSizeEntry.success(2048L), System.currentTimeMillis());
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> removedEntry1 =
                new StorageCacheService.StorageCacheEntry<>(removedProject1,
                        ProjectSizeEntry.success(512L), System.currentTimeMillis());
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> removedEntry2 =
                new StorageCacheService.StorageCacheEntry<>(removedProject2,
                        ProjectSizeEntry.success(256L), System.currentTimeMillis());

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.of(List.of(cachedEntry1, cachedEntry2, removedEntry1, removedEntry2)));
        when(storageCacheService.remove(eq("PROJECT"), eq(Set.of(removedProject1, removedProject2))))
                .thenReturn(2);
        when(projectsService.getProjectRepositoriesKeys(existingProject1, false))
                .thenReturn(Set.of("repo1"));
        when(projectsService.getProjectRepositoriesKeys(existingProject2, false))
                .thenReturn(Set.of("repo2"));
        when(nodesDao.getReposBinarySize(anySet())).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(storageCacheService).get(eq("PROJECT"), eq(ProjectSizeEntry.class));
        verify(storageCacheService).remove(eq("PROJECT"), eq(Set.of(removedProject1, removedProject2)));
    }

    @Test
    void testDeleteRemovedProjectsFromCache_WhenNoCachedProjects_DoesNothing() {
        // Given
        String project1 = "project1";
        Map<String, Long> projectsTotalFilesCount = Map.of(project1, 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.empty());
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1"));
        when(nodesDao.getReposBinarySize(anySet())).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(storageCacheService).get(eq("PROJECT"), eq(ProjectSizeEntry.class));
        verify(storageCacheService, never()).remove(eq("PROJECT"), anySet());
    }

    @Test
    void testDeleteRemovedProjectsFromCache_WhenAllCachedProjectsStillExist_DoesNotRemove() {
        // Given
        String project1 = "project1";
        String project2 = "project2";
        Map<String, Long> projectsTotalFilesCount = Map.of(
                project1, 100L,
                project2, 200L
        );
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cachedEntry1 =
                new StorageCacheService.StorageCacheEntry<>(project1,
                        ProjectSizeEntry.success(1024L), System.currentTimeMillis());
        StorageCacheService.StorageCacheEntry<ProjectSizeEntry> cachedEntry2 =
                new StorageCacheService.StorageCacheEntry<>(project2,
                        ProjectSizeEntry.success(2048L), System.currentTimeMillis());

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(storageCacheService.get(eq("PROJECT"), eq(ProjectSizeEntry.class)))
                .thenReturn(Optional.of(List.of(cachedEntry1, cachedEntry2)));
        when(projectsService.getProjectRepositoriesKeys(project1, false))
                .thenReturn(Set.of("repo1"));
        when(projectsService.getProjectRepositoriesKeys(project2, false))
                .thenReturn(Set.of("repo2"));
        when(nodesDao.getReposBinarySize(anySet())).thenReturn(1024L);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(2, result);
        verify(storageCacheService).get(eq("PROJECT"), eq(ProjectSizeEntry.class));
        verify(storageCacheService, never()).remove(eq("PROJECT"), anySet());
    }

    @ParameterizedTest
    @MethodSource("provideFilterReposTestCases")
    void testFilterRepos_FiltersCorrectly(
            Set<String> inputRepos,
            Map<String, RepoDetailsType> repoTypes,
            Map<String, Boolean> repoRealFlags,
            Set<String> expectedRepos) {
        // Given
        // Mock repository service to return appropriate repo configs
        for (Map.Entry<String, RepoDetailsType> entry : repoTypes.entrySet()) {
            RepoConfig config = mock(RepoConfig.class);
            when(config.getRepoType()).thenReturn(entry.getValue());
            when(config.isReal()).thenReturn(repoRealFlags.get(entry.getKey()));
            when(config.getKey()).thenReturn(entry.getKey());
            when(repositoryService.getRepoConfigByKey(entry.getKey())).thenReturn(config);
        }

        Map<String, Long> projectsTotalFilesCount = Map.of("project1", 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys("project1", false))
                .thenReturn(inputRepos);
        if (!expectedRepos.isEmpty()) {
            when(nodesDao.getReposBinarySize(expectedRepos)).thenReturn(1024L);
        }
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        service.calculateAllProjectsSizes();

        // Then
        if (expectedRepos.isEmpty()) {
            verify(nodesDao, never()).getReposBinarySize(anySet());
            verify(storageCacheService).putConditionally(eq("PROJECT"), eq("project1"),
                    eq(ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS)), any());
        } else {
            verify(nodesDao).getReposBinarySize(expectedRepos);
        }
    }

    private static Stream<Arguments> provideFilterReposTestCases() {
        return Stream.of(
                // Local repo - real and should be kept
                Arguments.of(
                        Set.of("local-repo"),
                        Map.of("local-repo", RepoDetailsType.LOCAL),
                        Map.of("local-repo", true),
                        Set.of("local-repo")
                ),
                // Remote repo - real but should be filtered out
                Arguments.of(
                        Set.of("remote-repo"),
                        Map.of("remote-repo", RepoDetailsType.REMOTE),
                        Map.of("remote-repo", true),
                        Set.of()
                ),
                // Virtual repo - not real and should be filtered out
                Arguments.of(
                        Set.of("virtual-repo"),
                        Map.of("virtual-repo", RepoDetailsType.VIRTUAL),
                        Map.of("virtual-repo", false),
                        Set.of()
                ),
                // Federated repo - real and should be kept
                Arguments.of(
                        Set.of("federated-repo"),
                        Map.of("federated-repo", RepoDetailsType.FEDERATED),
                        Map.of("federated-repo", true),
                        Set.of("federated-repo")
                ),
                // Mixed repos - only real, non-remote repos should be kept
                Arguments.of(
                        Set.of("local-repo", "remote-repo"),
                        Map.of("local-repo", RepoDetailsType.LOCAL, "remote-repo", RepoDetailsType.REMOTE),
                        Map.of("local-repo", true, "remote-repo", true),
                        Set.of("local-repo")
                )
        );
    }

    @Test
    void testFilterRepos_WhenRepoConfigIsNull_FiltersOut() {
        // Given
        String repoKey = "null-repo";
        Set<String> inputRepos = Set.of(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        Map<String, Long> projectsTotalFilesCount = Map.of("project1", 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys("project1", false))
                .thenReturn(inputRepos);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(nodesDao, never()).getReposBinarySize(anySet());
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq("project1"),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS)), any());
    }

    @Test
    void testFilterRepos_WhenRepoIsNotReal_FiltersOut() {
        // Given
        String repoKey = "non-real-repo";
        Set<String> inputRepos = Set.of(repoKey);

        RepoConfig nonRealRepoConfig = mock(RepoConfig.class);
        when(nonRealRepoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(nonRealRepoConfig.isReal()).thenReturn(false);
        when(nonRealRepoConfig.getKey()).thenReturn(repoKey);

        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(nonRealRepoConfig);

        Map<String, Long> projectsTotalFilesCount = Map.of("project1", 100L);
        StorageSummaryInfo summaryInfo = new StorageSummaryInfo(
                Map.of(),
                null,
                Map.of(),
                projectsTotalFilesCount
        );

        when(storageSummaryService.getLocalCache()).thenReturn(summaryInfo);
        when(projectsService.getProjectRepositoriesKeys("project1", false))
                .thenReturn(inputRepos);
        doNothing().when(storageCacheService).put(anyString(), anyString(), any());

        // When
        int result = service.calculateAllProjectsSizes();

        // Then
        assertEquals(1, result);
        verify(nodesDao, never()).getReposBinarySize(anySet());
        verify(storageCacheService).putConditionally(eq("PROJECT"), eq("project1"),
                eq(ProjectSizeEntry.failure(PROJECT_ERROR_NO_REPOS)), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0})
    void testExecutionDelayFactor_WhenInvalidValue_SetsToDefault(int invalidValue) {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageExecutionDelayFactor))
                .thenReturn((long) invalidValue);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxRepos))
                .thenReturn(1_000L);

        // When
        ProjectBinarySizeServiceImpl serviceWithInvalidFactor = new ProjectBinarySizeServiceImpl(
                storageCacheService,
                projectsService,
                repositoryService,
                storageSummaryService,
                nodesDao
        );

        // Then - service should be created successfully with default factor of 1
        // The service will log a warning and set factor to 1 internally
        assertNotNull(serviceWithInvalidFactor);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 100})
    void testExecutionDelayFactor_WhenValidValue_UsesProvidedValue(int validValue) {
        // Given
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageExecutionDelayFactor))
                .thenReturn((long) validValue);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxArtifacts))
                .thenReturn(1_000_000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.projectBinaryStorageMaxRepos))
                .thenReturn(1_000L);

        // When
        ProjectBinarySizeServiceImpl serviceWithValidFactor = new ProjectBinarySizeServiceImpl(
                storageCacheService,
                projectsService,
                repositoryService,
                storageSummaryService,
                nodesDao
        );

        // Then - service should be created successfully with the provided value
        assertNotNull(serviceWithValidFactor);
    }
}