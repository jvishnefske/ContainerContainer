package com.jfrog.build.service;

import com.google.common.collect.ImmutableSortedSet;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.dao.BuildArtifactsDao;
import com.jfrog.build.dao.BuildDependenciesDao;
import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildEntityRecord;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.service.BuildDatabaseService.BuildPropertyAsKeyValue;
import org.artifactory.exception.http.InternalServerException;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.PromotionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static com.jfrog.build.acl.model.BuildSearchCriteria.IN_ARTIFACTS;
import static com.jfrog.build.acl.model.BuildSearchCriteria.IN_DEPENDENCIES;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDatabaseServiceTest {

    private BuildDatabaseService databaseService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    private BuildsDao buildsDao;
    private BuildArtifactsDao buildArtifactsDao;
    private BuildDependenciesDao buildDependenciesDao;

    @BeforeEach
    void setUp() {
        buildsDao = mock(BuildsDao.class);
        buildArtifactsDao = mock(BuildArtifactsDao.class);
        buildDependenciesDao = mock(BuildDependenciesDao.class);
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        databaseService = new BuildDatabaseService(
                bridgeToArtifactory,
                buildsDao,
                mock(BuildModulesDao.class),
                buildArtifactsDao,
                buildDependenciesDao
        );
    }

    @Test
    void addPromotionStatus_Success() throws SQLException {
        long buildId = 1L;
        String currentUser = "admin";

        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getStarted()).thenReturn("2025-01-01T00:00:00.000Z");

        PromotionStatus promotionStatus = mock(PromotionStatus.class);
        when(promotionStatus.getStatus()).thenReturn("DEV");
        when(promotionStatus.getTimestampDate()).thenReturn(new Date(1234567890));

        BuildEntity buildEntity = mock(BuildEntity.class);
        when(buildEntity.getBuildId()).thenReturn(buildId);
        when(buildEntity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.findBuild(any(), any(), anyLong())).thenReturn(buildEntity);

        assertDoesNotThrow(() -> databaseService.addPromotionStatus(build, promotionStatus, currentUser));
        verify(buildsDao).addPromotionStatus(eq(buildId), any(), eq(currentUser), anyLong());
    }

    @Test
    void buildIdFromEntity() {
        long id = 123L;
        long buildDate = 1L;
        String repoName = "A";
        String buildName = "B";
        String buildNumber = "C";
        BuildIdEntity entity = mock(BuildIdEntity.class);
        when(entity.getBuildId()).thenReturn(id);
        when(entity.getBuildDate()).thenReturn(buildDate);
        when(entity.getBuildRepo()).thenReturn(repoName);
        when(entity.getBuildName()).thenReturn(buildName);
        when(entity.getBuildNumber()).thenReturn(buildNumber);
        BuildIdImpl buildId = databaseService.buildIdFromEntity(entity);
        assertEquals(id, buildId.getBuildId());
        assertEquals(formatBuildTime(buildDate), buildId.getStarted());
        assertEquals(repoName, buildId.getBuildRepo());
        assertEquals(buildName, buildId.getName());
        assertEquals(buildNumber, buildId.getNumber());
    }

    @Test
    void populateReleaseBundleNames() throws SQLException {
        databaseService.populateReleaseBundleNames(List.of());
        verify(buildsDao, times(1)).populateReleaseBundleNames(List.of());
    }

    @Test
    void populateReleaseBundleNames_SQLException() throws SQLException {
        doThrow(new SQLException()).when(buildsDao).populateReleaseBundleNames(any());
        Executable target = () -> databaseService.populateReleaseBundleNames(List.of());
        assertThrows(InternalServerException.class, target);
    }

    @Test
    void getBuildNames_SQLException() throws SQLException {
        List<String> buildRepos = List.of("A", "B");
        String buildName = "C";
        long offset = 1L;
        long limit = 2L;
        when(buildsDao.getBuildNames(buildRepos, buildName, offset, limit)).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildNames(buildRepos, buildName, offset, limit));
        verify(buildsDao, times(1)).getBuildNames(buildRepos, buildName, offset, limit);
    }

    @Test
    void getBuildNames() throws SQLException {
        List<String> buildRepos = List.of("A", "B");
        String buildName = "C";
        long offset = 1L;
        long limit = 2L;
        databaseService.getBuildNames(buildRepos, buildName, offset, limit);
        verify(buildsDao, times(1)).getBuildNames(buildRepos, buildName, offset, limit);
    }

    @Test
    void getBuildNumbers_SQLException() throws SQLException {
        long startedBefore = System.currentTimeMillis();
        when(buildsDao.getBuildNumbers("repo", "buildName", startedBefore, false, 1, 2, 2))
                .thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildNumbers("repo", "buildName", startedBefore, false, 1, 2, 2));
        verify(buildsDao, times(1)).getBuildNumbers("repo", "buildName", startedBefore, false, 1, 2, 2);
    }

    @Test
    void getBuildNumbers() throws SQLException {
        long startedBefore = System.currentTimeMillis();
        databaseService.getBuildNumbers("repo", "buildName", startedBefore, false, 1, 2, 2);
        verify(buildsDao, times(1)).getBuildNumbers("repo", "buildName", startedBefore, false, 1, 2, 2);
    }

    @Test
    void findBuildsByName_SQLException() throws SQLException {
        String buildName = "testBuild";
        String buildRepo = "testRepo";
        when(buildsDao.findBuildIds(buildName, buildRepo)).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.findBuildsByName(buildName, buildRepo));
        verify(buildsDao, times(1)).findBuildIds(buildName, buildRepo);
    }

    @Test
    void findBuildsByNameWithoutRepo_SQLException() throws SQLException {
        String buildName = "testBuild";
        when(buildsDao.findBuildIds(buildName)).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.findBuildsByName(buildName));
        verify(buildsDao, times(1)).findBuildIds(buildName);
    }

    @Test
    void getBuildDependencies_Success() throws SQLException {
        // Given
        String buildName = "test-build";
        String buildNumber = "1";
        String buildRepo = "test-repo";

        BuildFileInfo dependency = new BuildFileInfo(
                123L,
                new BuildRepoPath(
                        "libs-release",
                        "com/example/lib",
                        "lib-1.0.jar"
                ),
                "user1",
                "admin",
                "orig456md5",
                "def456md5",
                "orig123sha1",
                "abc123sha1",
                "sha256value",
                1024000L,
                1699000000000L,
                1699500000000L,
                1700000000000L,
                "dependency-1"
        );

        List<BuildFileInfo> expectedDependencies = List.of(dependency);
        when(buildDependenciesDao.selectBuildDependencies(buildName, buildNumber, buildRepo))
                .thenReturn(expectedDependencies);

        // When
        List<BuildFileInfo> result = databaseService.getBuildDependencies(buildName, buildNumber, buildRepo);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("dependency-1", result.get(0).getBuildDependencyName());
        verify(buildDependenciesDao, times(1)).selectBuildDependencies(buildName, buildNumber, buildRepo);
    }

    @Test
    void getBuildDependencies_Failure() throws SQLException {
        // Given
        String buildName = "error-build";
        String buildNumber = "1";
        String buildRepo = "error-repo";

        when(buildDependenciesDao.selectBuildDependencies(buildName, buildNumber, buildRepo))
                .thenThrow(new SQLException("Database connection error"));

        // When & Then
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildDependencies(buildName, buildNumber, buildRepo));
        verify(buildDependenciesDao, times(1)).selectBuildDependencies(buildName, buildNumber, buildRepo);
    }

    @Test
    public void buildPropertyAsKeyValue_Equals() {
        BuildPropertyAsKeyValue kv1 = new BuildPropertyAsKeyValue("a", "b");
        BuildPropertyAsKeyValue kv2 = new BuildPropertyAsKeyValue("a", "b");
        BuildPropertyAsKeyValue kv3 = new BuildPropertyAsKeyValue("b", "a");
        BuildPropertyAsKeyValue kv4 = new BuildPropertyAsKeyValue("c", "b");
        BuildPropertyAsKeyValue kv5 = new BuildPropertyAsKeyValue("a", "c");
        assertEquals(kv1, kv1);
        assertEquals(kv1, kv2);
        assertNotEquals(kv1, kv3);
        assertNotEquals(kv1, kv4);
        assertNotEquals(kv1, kv5);
    }

    @Test
    public void buildPropertyAsKeyValue_HashCode() {
        BuildPropertyAsKeyValue kv1 = new BuildPropertyAsKeyValue("a", "b");
        BuildPropertyAsKeyValue kv2 = new BuildPropertyAsKeyValue("a", "b");
        BuildPropertyAsKeyValue kv3 = new BuildPropertyAsKeyValue("b", "a");
        BuildPropertyAsKeyValue kv4 = new BuildPropertyAsKeyValue("c", "b");
        BuildPropertyAsKeyValue kv5 = new BuildPropertyAsKeyValue("a", "c");
        assertEquals(kv1.hashCode(), kv1.hashCode());
        assertEquals(kv1.hashCode(), kv2.hashCode());
        assertNotEquals(kv1.hashCode(), kv3.hashCode());
        assertNotEquals(kv1.hashCode(), kv4.hashCode());
        assertNotEquals(kv1.hashCode(), kv5.hashCode());
    }

    @Test
    void parseStringToDate_shouldReturnDate_whenValidStartedString() {
        String started = "2025-01-01T12:00:00.000+0000";
        Date result = BuildDatabaseService.parseStringToDate(started);
        assertNotNull(result);
    }

    @Test
    void getBuildRun_shouldReturnNull_whenBuildNotFound() throws SQLException {
        when(buildsDao.findBuild(anyString(), anyString(), anyLong())).thenReturn(null);
        BuildRun result = databaseService.getBuildRun("name", "1", formatBuildTime(1700000000000L));
        assertNull(result);
    }

    @Test
    void getBuildRun_shouldReturnBuildRun_whenBuildFound() throws SQLException {
        BuildEntity entity = mock(BuildEntity.class);
        when(entity.getBuildId()).thenReturn(10L);
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getCiUrl()).thenReturn("http://ci");
        when(entity.getRepo()).thenReturn("repo");
        when(entity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.findBuild(eq("build-name"), eq("1"), eq(1700000000000L))).thenReturn(entity);
        BuildRun result = databaseService.getBuildRun("build-name", "1", 1700000000000L);
        assertNotNull(result);
        assertEquals("build-name", result.getName());
        assertEquals("1", result.getNumber());
    }

    @Test
    void getBuildRun_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.findBuild(anyString(), anyString(), anyLong())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildRun("build-name", "1", 1700000000000L));
    }

    @Test
    void addPromotionStatus_shouldThrow_whenBuildNotFound() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(build.getName()).thenReturn("build-name");
        when(build.getNumber()).thenReturn("1");
        when(buildsDao.findBuild(any(), any(), anyLong())).thenReturn(null);
        assertThrows(InternalServerException.class,
                () -> databaseService.addPromotionStatus(build, mock(PromotionStatus.class), "admin"));
    }

    @Test
    void exists_shouldReturnTrue_whenBuildIdPositive() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("build-name");
        when(build.getNumber()).thenReturn("1");
        when(build.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(buildsDao.findBuildId(anyString(), anyString(), anyLong())).thenReturn(5L);
        assertTrue(databaseService.exists(build));
    }

    @Test
    void exists_shouldReturnFalse_whenBuildIdZero() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("build-name");
        when(build.getNumber()).thenReturn("1");
        when(build.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(buildsDao.findBuildId(anyString(), anyString(), anyLong())).thenReturn(0L);
        assertFalse(databaseService.exists(build));
    }

    @Test
    void exists_shouldThrow_whenSqlException() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("build-name");
        when(build.getNumber()).thenReturn("1");
        when(build.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(buildsDao.findBuildId(anyString(), anyString(), anyLong())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class, () -> databaseService.exists(build));
    }

    @Test
    void getLatestBuildRun_shouldReturnBuildRun_whenDaoReturns() throws SQLException {
        BuildRun run = new BuildRunImpl("build-name", "1", formatBuildTime(1700000000000L), "repo");
        when(buildsDao.findLatestBuild("build-name", "1", "repo")).thenReturn(run);
        assertEquals(run, databaseService.getLatestBuildRun("build-name", "1", "repo"));
    }

    @Test
    void getLatestBuildRun_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.findLatestBuild(anyString(), anyString(), anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getLatestBuildRun("build-name", "1", "repo"));
    }

    @Test
    void deleteBuild_shouldDelegateToDeleteBuildFromBuildRun() throws SQLException {
        when(buildsDao.findBuildId(anyString(), anyString(), anyLong())).thenReturn(0L);
        databaseService.deleteBuild("build-name", "1", formatBuildTime(1700000000000L), "repo");
        verify(buildsDao).findBuildId(eq("build-name"), eq("1"), anyLong());
    }

    @Test
    void deleteBuildFromBuildRun_shouldNotCallDeleteBuilds_whenBuildIdZero() throws SQLException {
        BuildRun run = mock(BuildRun.class);
        when(run.getName()).thenReturn("build-name");
        when(run.getNumber()).thenReturn("1");
        when(run.getStartedDate()).thenReturn(new Date(1700000000000L));
        when(buildsDao.findBuildId(anyString(), anyString(), anyLong())).thenReturn(0L);
        assertDoesNotThrow(() -> databaseService.deleteBuildFromBuildRun(run));
    }

    @Test
    void deleteAllBuilds_shouldThrow_whenSqlException() throws SQLException {
        doThrow(new SQLException()).when(buildsDao).deleteAllBuilds();
        assertThrows(InternalServerException.class, () -> databaseService.deleteAllBuilds());
    }

    @Test
    void getBuildStoringRepo_shouldReturnRepo_whenDaoReturns() throws SQLException {
        when(buildsDao.getBuildStoringRepo(anyString(), anyString(), anyLong())).thenReturn("my-repo");
        String result = databaseService.getBuildStoringRepo("build-name", "1", formatBuildTime(1700000000000L));
        assertEquals("my-repo", result);
    }

    @Test
    void getBuildStoringRepo_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.getBuildStoringRepo(anyString(), anyString(), anyLong())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildStoringRepo("build-name", "1", formatBuildTime(1700000000000L)));
    }

    @Test
    void getBuildsCountFromSpecificRepo_shouldReturnCount_whenDaoReturns() throws SQLException {
        when(buildsDao.getBuildsCountFromSpecificRepo("repo")).thenReturn(1);
        assertEquals(1, databaseService.getBuildsCountFromSpecificRepo("repo"));
    }

    @Test
    void getBuildsCountFromSpecificRepo_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.getBuildsCountFromSpecificRepo(anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildsCountFromSpecificRepo("repo"));
    }

    @Test
    void getUniqueBuildsCountFromSpecificRepo_shouldReturnCount_whenDaoReturns() throws SQLException {
        when(buildsDao.getUniqueBuildsCountFromSpecificRepo("repo")).thenReturn(7);
        assertEquals(7, databaseService.getUniqueBuildsCountFromSpecificRepo("repo"));
    }

    @Test
    void getTotalBuildsCount_shouldReturnCount_whenDaoReturns() throws SQLException {
        when(buildsDao.getTotalBuildsCount()).thenReturn(100);
        assertEquals(100, databaseService.getTotalBuildsCount());
    }

    @Test
    void getTotalBuildsCount_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.getTotalBuildsCount()).thenThrow(new SQLException());
        assertThrows(InternalServerException.class, databaseService::getTotalBuildsCount);
    }

    @Test
    void findBuildsByName_shouldReturnSet_whenDaoReturnsIds() throws SQLException {
        when(buildsDao.findBuildIds("build-name", "repo")).thenReturn(List.of(1L));
        BuildEntity entity = mock(BuildEntity.class);
        when(entity.getBuildId()).thenReturn(1L);
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getCiUrl()).thenReturn(null);
        when(entity.getRepo()).thenReturn("repo");
        when(entity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.getBuild(1L)).thenReturn(entity);
        Set<BuildRun> result = databaseService.findBuildsByName("build-name", "repo");
        assertEquals(1, result.size());
    }

    @Test
    void getAllBuildsInfo_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.getAllBuildNames(null)).thenThrow(new SQLException());
        assertThrows(InternalServerException.class, () -> databaseService.getAllBuildsInfo(null));
    }

    @Test
    void buildNameExists_shouldReturnDaoResult() throws SQLException {
        when(buildsDao.existsBuildByName("build-name", "repo")).thenReturn(true);
        assertTrue(databaseService.buildNameExists("build-name", "repo"));

        when(buildsDao.existsBuildByName("missing", "repo")).thenReturn(false);
        assertFalse(databaseService.buildNameExists("missing", "repo"));

        verify(buildsDao).existsBuildByName("build-name", "repo");
        verify(buildsDao).existsBuildByName("missing", "repo");
    }

    @Test
    void buildNameExists_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.existsBuildByName(anyString(), anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class, () -> databaseService.buildNameExists("build-name", "repo"));
    }

    @Test
    void findBuildsByNameAndNumbers_shouldReturnSet_whenDaoReturns() throws SQLException {
        BuildRun run = new BuildRunImpl("build-name", "1", formatBuildTime(1700000000000L), "repo");
        when(buildsDao.findBuildsByNameAndNumbers("build-name", List.of("1"), "repo")).thenReturn(List.of(run));

        Set<BuildRun> result = databaseService.findBuildsByNameAndNumbers("build-name", List.of("1"), "repo");

        assertEquals(1, result.size());
        assertTrue(result.contains(run));
        verify(buildsDao).findBuildsByNameAndNumbers("build-name", List.of("1"), "repo");
    }

    @Test
    void findBuildsByNameAndNumbers_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.findBuildsByNameAndNumbers(anyString(), any(), anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.findBuildsByNameAndNumbers("build-name", List.of("1"), "repo"));
    }

    @Test
    void distinctBuildProps_shouldDeduplicateByKeyAndValue() {
        List<BuildProps> input = List.of(
                new BuildProps("k1", "v1", null),
                new BuildProps("k1", "v1", null),
                new BuildProps("k2", "v2", null)
        );
        List<BuildProps> result = BuildDatabaseService.distinctBuildProps(input);
        assertEquals(2, result.size());
    }

    @Test
    void getBuildForName_shouldThrow_whenSqlException() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(buildsDao.getBuildForName(anyString(), anyString(), eq(filter))).thenThrow(new SQLException());
        Executable target = () -> databaseService.getBuildForName("build-name", "repo", filter);
        assertThrows(InternalServerException.class, target);
    }

    @Test
    void addBuild_shouldCreateBuild_whenWrapperWithEmptyModules() throws SQLException {
        Build build = mock(Build.class);
        when(build.getUrl()).thenReturn("http://ci");
        when(build.getArtifactoryPrincipal()).thenReturn("admin");
        when(build.getStatuses()).thenReturn(null);
        when(build.getProperties()).thenReturn(null);
        when(build.getModules()).thenReturn(null);
        BuildWrapper wrapper = mock(BuildWrapper.class);
        when(wrapper.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(wrapper.getName()).thenReturn("my-build");
        when(wrapper.getNumber()).thenReturn("1");
        when(wrapper.getBuildRepo()).thenReturn("repo");
        when(wrapper.getBuild()).thenReturn(build);
        when(bridgeToArtifactory.getNextDatabaseId()).thenReturn(1L);

        databaseService.addBuild(wrapper);

        verify(buildsDao).createBuild(any());
    }

    @Test
    void addBuild_shouldThrow_whenSqlException() throws SQLException {
        Build build = mock(Build.class);
        when(build.getUrl()).thenReturn("http://ci");
        when(build.getArtifactoryPrincipal()).thenReturn("admin");
        when(build.getStatuses()).thenReturn(null);
        when(build.getProperties()).thenReturn(null);
        when(build.getModules()).thenReturn(null);
        BuildWrapper wrapper = mock(BuildWrapper.class);
        when(wrapper.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(wrapper.getName()).thenReturn("build-name");
        when(wrapper.getNumber()).thenReturn("1");
        when(wrapper.getBuildRepo()).thenReturn("repo");
        when(wrapper.getBuild()).thenReturn(build);
        when(bridgeToArtifactory.getNextDatabaseId()).thenReturn(1L);
        doThrow(new SQLException()).when(buildsDao).createBuild(any());

        assertThrows(InternalServerException.class, () -> databaseService.addBuild(wrapper));
    }

    @Test
    void populateMissingChecksums_shouldDoNothing_whenModulesNull() {
        Build build = mock(Build.class);
        when(build.getModules()).thenReturn(null);
        assertDoesNotThrow(() -> databaseService.populateMissingChecksums(build));
    }

    @Test
    void populateMissingChecksums_shouldDoNothing_whenModulesEmpty() {
        Build build = mock(Build.class);
        when(build.getModules()).thenReturn(List.of());
        assertDoesNotThrow(() -> databaseService.populateMissingChecksums(build));
    }

    @Test
    void findBuildsForChecksum_shouldDelegateToDaoAndReturnBuildRuns_whenSearchInDependencies() throws SQLException {
        BuildEntity entity = mock(BuildEntity.class);
        when(entity.getBuildId()).thenReturn(1L);
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getCiUrl()).thenReturn(null);
        when(entity.getRepo()).thenReturn("repo");
        when(entity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.findBuildsForDependencyChecksum(eq(SHA1), eq("a".repeat(40)), eq("repo")))
                .thenReturn(List.of(entity));
        Set<BuildRun> result = databaseService.findBuildsForChecksum(IN_DEPENDENCIES, SHA1, "a".repeat(40), "repo");
        assertEquals(1, result.size());
        verify(buildsDao).findBuildsForDependencyChecksum(SHA1, "a".repeat(40), "repo");
    }

    @Test
    void findBuildsForChecksum_shouldDelegateToDaoAndReturnBuildRuns_whenSearchInArtifacts() throws SQLException {
        BuildEntity entity = mock(BuildEntity.class);
        when(entity.getBuildId()).thenReturn(1L);
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getCiUrl()).thenReturn(null);
        when(entity.getRepo()).thenReturn("repo");
        when(entity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.findBuildsForArtifactChecksum(eq(SHA1), eq("a".repeat(40)), eq("repo")))
                .thenReturn(List.of(entity));
        Set<BuildRun> result = databaseService.findBuildsForChecksum(IN_ARTIFACTS, SHA1, "a".repeat(40), "repo");
        assertEquals(1, result.size());
        verify(buildsDao).findBuildsForArtifactChecksum(SHA1, "a".repeat(40), "repo");
    }

    @Test
    void findBuildsForChecksum_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.findBuildsForDependencyChecksum(any(), anyString(), anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.findBuildsForChecksum(IN_DEPENDENCIES, SHA1, "a".repeat(40), "repo"));
    }

    @Test
    void getPublishedModules_shouldReturnList_whenDaoReturns() throws SQLException {
        List<PublishedModule> modules = new ArrayList<>();
        when(buildsDao.getBuildModule("build-name", "1", "1700000000000", "repo")).thenReturn(modules);
        List<PublishedModule> result = databaseService.getPublishedModules(
                "build-name", "1", "1700000000000", "repo");
        assertEquals(modules, result);
        verify(buildsDao).getBuildModule("build-name", "1", "1700000000000", "repo");
    }

    @Test
    void getPublishedModules_shouldReturnNull_whenDaoThrows() throws SQLException {
        when(buildsDao.getBuildModule(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new SQLException());
        List<PublishedModule> result = databaseService.getPublishedModules("build-name", "1", "date", "repo");
        assertNull(result);
    }

    @Test
    void getModuleArtifact_shouldReturnList_whenDaoReturns() throws SQLException {
        List<ModuleArtifact> artifacts = new ArrayList<>();
        when(buildsDao.getModuleArtifact("build-name", "1", "date", "mod-1", "repo", false)).thenReturn(artifacts);
        List<ModuleArtifact> result = databaseService.getModuleArtifact(
                "build-name", "1", "mod-1", "date", "repo", false);
        assertEquals(artifacts, result);
        verify(buildsDao).getModuleArtifact("build-name", "1", "date", "mod-1", "repo", false);
    }

    @Test
    void getModuleArtifact_shouldReturnNull_whenDaoThrows() throws SQLException {
        when(buildsDao.getModuleArtifact(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new SQLException());
        List<ModuleArtifact> result = databaseService.getModuleArtifact("build-name", "1", "m", "d", "repo", true);
        assertNull(result);
    }

    @Test
    void getBuildArtifacts_shouldReturnList_whenDaoReturnsThreeArgs() throws SQLException {
        List<BuildNodeRecord> records = new ArrayList<>();
        when(buildArtifactsDao.getBuildArtifacts("build-name", "1", "repo")).thenReturn(records);
        List<BuildNodeRecord> result = databaseService.getBuildArtifacts("build-name", "1", "repo");
        assertEquals(records, result);
        verify(buildArtifactsDao).getBuildArtifacts("build-name", "1", "repo");
    }

    @Test
    void getBuildArtifacts_shouldReturnEmptyList_whenDaoThrowsThreeArgs() throws SQLException {
        when(buildArtifactsDao.getBuildArtifacts(anyString(), anyString(), anyString())).thenThrow(new SQLException());
        List<BuildNodeRecord> result = databaseService.getBuildArtifacts("build-name", "1", "repo");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildArtifacts_shouldReturnList_whenDaoReturnsFourArgs() throws SQLException {
        List<BuildNodeRecord> records = new ArrayList<>();
        when(buildArtifactsDao.getBuildArtifacts("build-name", "1", "repo", 1700000000000L)).thenReturn(records);
        List<BuildNodeRecord> result = databaseService.getBuildArtifacts("build-name", "1", "repo", 1700000000000L);
        assertEquals(records, result);
        verify(buildArtifactsDao).getBuildArtifacts("build-name", "1", "repo", 1700000000000L);
    }

    @Test
    void getBuildArtifacts_shouldReturnEmptyList_whenDaoThrowsFourArgs() throws SQLException {
        when(buildArtifactsDao.getBuildArtifacts(anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new SQLException());
        List<BuildNodeRecord> result = databaseService.getBuildArtifacts("build-name", "1", "repo", 1700000000000L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getModuleDependency_shouldReturnList_whenDaoReturns() throws SQLException {
        List<ModuleDependency> deps = new ArrayList<>();
        when(buildsDao.selectModuleDependencies("build-name", "1", "started", "repo", "mod-1")).thenReturn(deps);
        List<ModuleDependency> result = databaseService.getModuleDependency(
                "build-name", "1", "mod-1", "started", "repo");
        assertEquals(deps, result);
        verify(buildsDao).selectModuleDependencies("build-name", "1", "started", "repo", "mod-1");
    }

    @Test
    void getModuleDependency_shouldReturnNull_whenDaoThrows() throws SQLException {
        when(buildsDao.selectModuleDependencies(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new SQLException());
        List<ModuleDependency> result = databaseService.getModuleDependency("build-name", "1", "m", "s", "repo");
        assertNull(result);
    }

    @Test
    void getBuildArtifacts_withWrapper_shouldReturnList_whenDaoReturns() throws SQLException {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        when(wrapper.getBuildRepo()).thenReturn("repo");
        when(wrapper.getName()).thenReturn("build-name");
        when(wrapper.getNumber()).thenReturn("1");
        List<BuildFileInfo> list = new ArrayList<>();
        when(buildArtifactsDao.selectBuildArtifacts(eq(wrapper), any(), any())).thenReturn(list);
        List<BuildFileInfo> result = databaseService.getBuildArtifacts(wrapper, null, null);
        assertEquals(list, result);
        verify(buildArtifactsDao).selectBuildArtifacts(wrapper, null, null);
    }

    @Test
    void getBuildArtifacts_withWrapper_shouldThrow_whenDaoThrows() throws SQLException {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        when(wrapper.getBuildRepo()).thenReturn("repo");
        when(wrapper.getName()).thenReturn("build-name");
        when(wrapper.getNumber()).thenReturn("1");
        when(buildArtifactsDao.selectBuildArtifacts(any(), any(), any())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildArtifacts(wrapper, List.of(), List.of()));
    }

    @Test
    void getModuleArtifactsForDiffWithPaging_shouldDelegateToDao() {
        BuildParams params = mock(BuildParams.class);
        List<ModuleArtifact> list = new ArrayList<>();
        when(buildsDao.getModuleArtifactsForDiffWithPaging(params, true)).thenReturn(list);
        List<ModuleArtifact> result = databaseService.getModuleArtifactsForDiffWithPaging(params, true);
        assertEquals(list, result);
        verify(buildsDao).getModuleArtifactsForDiffWithPaging(params, true);
    }

    @Test
    void getPrevBuildsList_shouldDelegateToDao() {
        List<GeneralBuild> list = new ArrayList<>();
        when(buildsDao.getPrevBuildsList("build-name", "1700000000000", "repo")).thenReturn(list);
        List<GeneralBuild> result = databaseService.getPrevBuildsList(
                "build-name", "1700000000000", "repo");
        assertEquals(list, result);
        verify(buildsDao).getPrevBuildsList("build-name", "1700000000000", "repo");
    }

    @Test
    void getBuildPropsData_shouldDelegateToDaoAndDeduplicate() {
        BuildParams params = mock(BuildParams.class);
        List<BuildProps> daoList = List.of(new BuildProps("k", "v", null), new BuildProps("k", "v", null));
        when(buildsDao.getBuildPropsList(params)).thenReturn(daoList);
        List<BuildProps> result = databaseService.getBuildPropsData(params);
        assertEquals(1, result.size());
        assertEquals("k", result.getFirst().getKey());
        assertEquals("v", result.getFirst().getValue());
        verify(buildsDao).getBuildPropsList(params);
    }

    @Test
    void getModuleDependencyForDiffWithPaging_shouldDelegateToDao() {
        BuildParams params = mock(BuildParams.class);
        List<ModuleDependency> list = new ArrayList<>();
        when(buildsDao.getModuleDependencyForDiffWithPaging(params)).thenReturn(list);
        List<ModuleDependency> result = databaseService.getModuleDependencyForDiffWithPaging(params);
        assertEquals(list, result);
        verify(buildsDao).getModuleDependencyForDiffWithPaging(params);
    }

    @Test
    void getLatestBuildsByName_shouldReturnSet_whenDaoReturnsBuilds() throws SQLException {
        BuildNameAndRepo nameAndRepo = new BuildNameAndRepo("build-name", "repo");
        when(buildsDao.getAllBuildNames("repo")).thenReturn(List.of(nameAndRepo));
        BuildEntity entity = mock(BuildEntity.class);
        when(entity.getBuildId()).thenReturn(1L);
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getCiUrl()).thenReturn(null);
        when(entity.getRepo()).thenReturn("repo");
        when(entity.getPromotions()).thenReturn(ImmutableSortedSet.of());
        when(buildsDao.getLatestBuild("build-name", "repo")).thenReturn(entity);
        Set<BuildRun> result = databaseService.getLatestBuildsByName("repo");
        assertEquals(1, result.size());
        verify(buildsDao).getAllBuildNames("repo");
        verify(buildsDao).getLatestBuild("build-name", "repo");
    }

    @Test
    void getLatestBuildsByName_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.getAllBuildNames(anyString())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class, () -> databaseService.getLatestBuildsByName("repo"));
    }

    @Test
    void getLatestBuildIDsPaging_shouldDelegateToDaoAndMapToBuildIds() {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        BuildIdEntity entity = mock(BuildIdEntity.class);
        when(entity.getBuildId()).thenReturn(1L);
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getBuildRepo()).thenReturn("repo");
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(buildsDao.getLatestBuildIds(filter)).thenReturn(List.of(entity));
        List<BuildId> result = databaseService.getLatestBuildIDsPaging(filter);
        assertEquals(1, result.size());
        assertEquals("build-name", result.getFirst().getName());
        assertEquals("1", result.getFirst().getNumber());
        verify(buildsDao).getLatestBuildIds(filter);
    }

    @Test
    void getDistinctBuildNamesPaging_shouldDelegateToDao() {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(buildsDao.getDistinctBuildNames(filter)).thenReturn(List.of("build-a", "build-b"));
        List<String> result = databaseService.getDistinctBuildNamesPaging(filter);
        assertEquals(List.of("build-a", "build-b"), result);
        verify(buildsDao).getDistinctBuildNames(filter);
    }

    @Test
    void getDistinctBuildNamesCount_shouldDelegateToDao() {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(buildsDao.getDistinctBuildNamesCount(filter)).thenReturn(1);
        int result = databaseService.getDistinctBuildNamesCount(filter);
        assertEquals(1, result);
        verify(buildsDao).getDistinctBuildNamesCount(filter);
    }

    @Test
    void getLatestBuildIDsByNameCaseInsensitive_shouldDelegateToDaoAndMapToBuildIds() throws SQLException {
        BuildsSearchFilter searchFilter = BuildsSearchFilter.builder().name("build-name").buildRepo("repo").build();
        BuildIdEntity entity = mock(BuildIdEntity.class);
        when(entity.getBuildId()).thenReturn(2L);
        when(entity.getBuildDate()).thenReturn(1700000000000L);
        when(entity.getBuildRepo()).thenReturn("repo");
        when(entity.getBuildName()).thenReturn("build-name");
        when(entity.getBuildNumber()).thenReturn("1");
        when(buildsDao.selectLatestBuildIdsByNameCaseInsensitive(searchFilter)).thenReturn(List.of(entity));
        List<BuildId> result = databaseService.getLatestBuildIDsByNameCaseInsensitive(searchFilter);
        assertEquals(1, result.size());
        assertEquals("build-name", result.getFirst().getName());
        assertEquals("1", result.getFirst().getNumber());
        verify(buildsDao).selectLatestBuildIdsByNameCaseInsensitive(searchFilter);
    }

    @Test
    void getLatestBuildIDsByNameCaseInsensitive_shouldThrow_whenSqlException() throws SQLException {
        BuildsSearchFilter searchFilter = BuildsSearchFilter.builder().name("build-name").build();
        when(buildsDao.selectLatestBuildIdsByNameCaseInsensitive(any())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getLatestBuildIDsByNameCaseInsensitive(searchFilter));
    }

    @Test
    void getBuildNumbersByBuildName_shouldDelegateToDaoAndMapToGeneralBuilds() throws SQLException {
        BuildsSearchFilter filter = BuildsSearchFilter.builder().name("build-name").buildRepo("repo").build();
        BuildEntityRecord record = BuildEntityRecord.builder()
                .buildName("build-name")
                .buildNumber("7")
                .buildDate(1700000000000L)
                .ciUrl("http://ci/7")
                .repo("repo")
                .build();
        when(buildsDao.selectBuildNumbersByName(filter)).thenReturn(List.of(record));

        List<GeneralBuild> result = databaseService.getBuildNumbersByBuildName(filter);

        assertEquals(1, result.size());
        GeneralBuild build = result.getFirst();
        assertEquals("build-name", build.getBuildName());
        assertEquals("7", build.getBuildNumber());
        assertEquals(1700000000000L, build.getBuildDate());
        assertEquals("http://ci/7", build.getCiUrl());
        assertEquals("repo", build.getBuildRepo());
        verify(buildsDao).selectBuildNumbersByName(filter);
    }

    @Test
    void getBuildNumbersByBuildName_shouldThrow_whenSqlException() throws SQLException {
        BuildsSearchFilter filter = BuildsSearchFilter.builder().name("build-name").build();
        when(buildsDao.selectBuildNumbersByName(any())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getBuildNumbersByBuildName(filter));
    }

    @Test
    void getLatestPromotionStatusForBuildIds_shouldDelegateToDao() throws SQLException {
        Map<Long, String> statuses = Map.of(1L, "Released", 2L, "Staging");
        when(buildsDao.selectLatestPromotionStatusForBuildIds(List.of(1L, 2L))).thenReturn(statuses);
        Map<Long, String> result = databaseService.getLatestPromotionStatusForBuildIds(List.of(1L, 2L));
        assertEquals(statuses, result);
        verify(buildsDao).selectLatestPromotionStatusForBuildIds(List.of(1L, 2L));
    }

    @Test
    void getLatestPromotionStatusForBuildIds_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.selectLatestPromotionStatusForBuildIds(any())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.getLatestPromotionStatusForBuildIds(List.of(1L)));
    }

    @Test
    void selectDependencyNodesBySha1_shouldDelegateToDaoAndReturnMap() throws SQLException {
        Map<String, List<BuildNode>> map = Map.of("sha1", List.of(mock(BuildNode.class)));
        when(buildsDao.selectDependencyNodesBySha1(List.of("abc"))).thenReturn(map);
        Map<String, List<BuildNode>> result = databaseService.selectDependencyNodesBySha1(List.of("abc"));
        assertEquals(map, result);
        verify(buildsDao).selectDependencyNodesBySha1(List.of("abc"));
    }

    @Test
    void selectDependencyNodesBySha1_shouldThrow_whenSqlException() throws SQLException {
        when(buildsDao.selectDependencyNodesBySha1(any())).thenThrow(new SQLException());
        assertThrows(InternalServerException.class,
                () -> databaseService.selectDependencyNodesBySha1(List.of("sha1")));
    }
}