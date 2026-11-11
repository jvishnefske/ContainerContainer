package com.jfrog.build.dao;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.model.BuildNumberRecord;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.model.BuildReleaseBundle;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.dao.util.BuildsDaoUtils;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildEntityRecord;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.entity.BuildPromotionStatus;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.querybuilder.BaseQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.stubbing.OngoingStubbing;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildsDaoTest {

    private BuildsDao buildsDao;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildSysConfigService sysConfigService;
    private JdbcHelper jdbcHelper;

    @BeforeEach
    public void init() throws InvocationTargetException, IllegalAccessException {
        jdbcHelper = mock(JdbcHelper.class);
        sysConfigService = mock(BuildSysConfigService.class);
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(1);
        when(sysConfigService.isBuildArtifactsQueryByBuildDateEnabled()).thenReturn(true);
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        when(bridgeToArtifactory.getJdbcHelper()).thenReturn(jdbcHelper);
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(POSTGRESQL);
        buildsDao = new BuildsDao(bridgeToArtifactory, sysConfigService);
    }

    @Test
    void addPromotionStatus_Success() throws SQLException {
        BuildPromotionStatus promotionStatus = mock(BuildPromotionStatus.class);
        assertDoesNotThrow(() -> buildsDao.addPromotionStatus(1L, promotionStatus, "admin", 123L));
        verify(jdbcHelper, times(2)).executeUpdate(anyString(), any(Object[].class));
    }

    @ParameterizedTest
    @MethodSource("getPublishedModule_Success_Provider")
    void getPublishedModule_Success(
            @Nullable String id,
            @Nullable String type,
            @Nullable PublishedModule expected) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(id);
        when(rs.getString(2)).thenReturn("0");
        when(rs.getString(3)).thenReturn("1");
        when(rs.getString(4)).thenReturn(type);
        assertEquals(expected, buildsDao.getPublishedModule(rs));
    }

    @NonNull
    static Stream<Arguments> getPublishedModule_Success_Provider() {
        return Stream.of(
                Arguments.of("id", null, buildPublishedModule("id", null, null, null)),
                Arguments.of("id/1", "build", buildPublishedModule("id/1", "build", "id", "1")),
                Arguments.of("id/1", "Build", buildPublishedModule("id/1", "build", "id", "1")),
                Arguments.of("id", "Build", buildPublishedModule("id", "build", "", "")),
                Arguments.of("id/", "Build", buildPublishedModule("id/", "build", "id", "")),
                Arguments.of("/", "Build", buildPublishedModule("/", "build", "", "")),
                Arguments.of("/2", "Build", buildPublishedModule("/2", "build", "", "2")),
                Arguments.of("a/b/c/99", "build", buildPublishedModule("a/b/c/99", "build", "a/b/c", "99")),
                Arguments.of("", "Build", buildPublishedModule("", "build", "", "")),
                Arguments.of("", "Ivy", buildPublishedModule("", "Ivy", null, null))
        );
    }

    @Test
    void selectModuleDependencies_Success() throws SQLException {
        String sha1 = "some-sha1";
        String buildName = "some-name";
        String buildNumber = "some-number";
        long buildStarted = 123456789;

        ResultSet rsDependency = mock(ResultSet.class);
        when(rsDependency.getString("id")).thenReturn("id");
        when(rsDependency.getString("type")).thenReturn("type");
        when(rsDependency.getString("scope")).thenReturn("scope");
        when(rsDependency.getString("sha1")).thenReturn(sha1);
        when(rsDependency.next()).thenReturn(true, false);

        ResultSet rsBuildId = mock(ResultSet.class);
        when(rsBuildId.getLong("build_id")).thenReturn(123L);
        when(rsBuildId.next()).thenReturn(true, false);

        ResultSet rsBuild = mock(ResultSet.class);
        when(rsBuild.getLong(1)).thenReturn(123L);
        when(rsBuild.getString(2)).thenReturn(buildName);
        when(rsBuild.getString(3)).thenReturn(buildNumber);
        when(rsBuild.getLong(4)).thenReturn(buildStarted);
        when(rsBuild.getString(5)).thenReturn(EMPTY);
        when(rsBuild.getLong(6)).thenReturn(buildStarted);
        when(rsBuild.next()).thenReturn(true, false);

        ResultSet rsBuildProperties = mock(ResultSet.class);
        ResultSet rsBuildPromotions = mock(ResultSet.class);

        ResultSet rsNode = mock(ResultSet.class);
        when(rsNode.getString("sha1_actual")).thenReturn(sha1);
        when(rsNode.getString("repo")).thenReturn("some-node-repo");
        when(rsNode.getString("node_path")).thenReturn("some-path");
        when(rsNode.getString("node_name")).thenReturn("some-name");
        when(rsNode.next()).thenReturn(true, false);

        when(jdbcHelper.executeSelect(anyString(), any(Object[].class)))
                .thenReturn(rsDependency)
                .thenReturn(rsBuildId)
                .thenReturn(rsBuild)
                .thenReturn(rsBuildProperties)
                .thenReturn(rsBuildPromotions)
                .thenReturn(rsNode);

        List<ModuleDependency> dependencies = buildsDao.selectModuleDependencies(
                buildName,
                buildNumber,
                Long.toString(buildStarted),
                "some-build-repo",
                "some-module-id"
        );
        assertEquals(1, dependencies.size());

        ModuleDependency dependency = dependencies.getFirst();
        assertNull(dependency.getRepoKey());
        assertNull(dependency.getPath());
        assertEquals("id", dependency.getName());
        assertEquals("type", dependency.getType());
        assertEquals("scope", dependency.getScope());
        assertEquals(sha1, dependency.getSha1());
    }

    @Test
    void selectLatestBuildIdsByNameCaseInsensitive_Success() throws SQLException {
        MockedStatic<BuildsDaoUtils> mocked = mockStatic(BuildsDaoUtils.class);
        BaseQueryBuilder queryBuilder = mock(BaseQueryBuilder.class);
        mocked.when(() -> BuildsDaoUtils.createSqlBuilder(POSTGRESQL)).thenReturn(queryBuilder);
        when(queryBuilder.build(anyString(), anyString(), anyLong(), anyLong())).thenReturn("some-query");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("build_id")).thenReturn(1L);
        when(rs.getString("build_name")).thenReturn("buildName");
        when(rs.getString("build_number")).thenReturn("buildNumber");
        when(rs.getLong("build_date")).thenReturn(1234L);
        when(rs.getString("repo")).thenReturn("buildRepo");
        when(rs.next()).thenReturn(true, false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name("someName")
                .buildRepo("someRepo")
                .after(10L)
                .before(100L)
                .orderBy("name")
                .sortOrder("asc")
                .offset(0L)
                .build();

        List<BuildIdEntity> buildIds = buildsDao.selectLatestBuildIdsByNameCaseInsensitive(filter);
        assertEquals(1, buildIds.size());

        BuildIdEntity be = buildIds.getFirst();
        assertEquals(1L, be.getBuildId());
        assertEquals("buildName", be.getBuildName());
        assertEquals("buildNumber", be.getBuildNumber());
        assertEquals(1234L, be.getBuildDate());
        assertEquals("buildRepo", be.getBuildRepo());
        mocked.close();
    }

    @Test
    void selectBuildNumbersByName_Success() throws SQLException {
        try (MockedStatic<BuildsDaoUtils> mocked = mockStatic(BuildsDaoUtils.class)) {
            BaseQueryBuilder queryBuilder = mock(BaseQueryBuilder.class);
            mocked.when(() -> BuildsDaoUtils.createSqlBuilder(POSTGRESQL)).thenReturn(queryBuilder);
            when(queryBuilder.build(anyString(), anyString(), anyLong(), anyLong())).thenReturn("some-query");

            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("build_id")).thenReturn(42L);
            when(rs.getString("build_number")).thenReturn("7");
            when(rs.getLong("build_date")).thenReturn(1234L);
            when(rs.getString("ci_url")).thenReturn("http://ci/7");
            when(rs.next()).thenReturn(true, false);

            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            when(jdbcHelper.executeSelect(eq("some-query"), paramsCaptor.capture())).thenReturn(rs);

            BuildsSearchFilter filter = BuildsSearchFilter.builder()
                    .name("my-build")
                    .buildRepo("my-repo")
                    .after(10L)
                    .before(100L)
                    .orderBy("number")
                    .sortOrder("asc")
                    .offset(0L)
                    .limit(25L)
                    .build();

            List<BuildEntityRecord> result = buildsDao.selectBuildNumbersByName(filter);

            assertEquals(1, result.size());
            BuildEntityRecord record = result.getFirst();
            assertEquals(42L, record.getBuildId());
            assertEquals("my-build", record.getBuildName());
            assertEquals("my-repo", record.getRepo());
            assertEquals("7", record.getBuildNumber());
            assertEquals(1234L, record.getBuildDate());
            assertEquals("http://ci/7", record.getCiUrl());

            // name, repo, then the after (>) and before (<) bounds are bound in this order
            assertEquals(List.of("my-build", "my-repo", 10L, 100L), List.of(paramsCaptor.getValue()));
        }
    }

    @Test
    void selectBuildNumbersByName_WhenNoDateBounds_BindsOnlyNameAndRepo() throws SQLException {
        try (MockedStatic<BuildsDaoUtils> mocked = mockStatic(BuildsDaoUtils.class)) {
            BaseQueryBuilder queryBuilder = mock(BaseQueryBuilder.class);
            mocked.when(() -> BuildsDaoUtils.createSqlBuilder(POSTGRESQL)).thenReturn(queryBuilder);
            when(queryBuilder.build(anyString(), anyString(), anyLong(), anyLong())).thenReturn("some-query");

            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(false);

            ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
            when(jdbcHelper.executeSelect(eq("some-query"), paramsCaptor.capture())).thenReturn(rs);

            BuildsSearchFilter filter = BuildsSearchFilter.builder()
                    .name("my-build")
                    .buildRepo("my-repo")
                    .orderBy("date")
                    .sortOrder("desc")
                    .offset(0L)
                    .limit(10L)
                    .build();

            List<BuildEntityRecord> result = buildsDao.selectBuildNumbersByName(filter);

            assertTrue(result.isEmpty());
            assertEquals(List.of("my-build", "my-repo"), List.of(paramsCaptor.getValue()));
        }
    }

    @Test
    void selectLatestPromotionStatusForBuildIds_Success() throws SQLException {
        // dbOperationsBulkSize is 1 in setup, so [1L, 2L] partitions into two single-id queries
        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.next()).thenReturn(true, false);
        when(rs1.getLong("build_id")).thenReturn(1L);
        when(rs1.getString("status")).thenReturn("Released");

        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.next()).thenReturn(true, false);
        when(rs2.getLong("build_id")).thenReturn(2L);
        when(rs2.getString("status")).thenReturn("Staging");

        when(jdbcHelper.executeSelect(anyString(), eq(List.of(1L)))).thenReturn(rs1);
        when(jdbcHelper.executeSelect(anyString(), eq(List.of(2L)))).thenReturn(rs2);

        Map<Long, String> result = buildsDao.selectLatestPromotionStatusForBuildIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("Released", result.get(1L));
        assertEquals("Staging", result.get(2L));
    }

    @Test
    void selectLatestPromotionStatusForBuildIds_WhenNoBuildIds_ReturnsEmptyMap() throws SQLException {
        Map<Long, String> result = buildsDao.selectLatestPromotionStatusForBuildIds(List.of());

        assertTrue(result.isEmpty());
        verify(jdbcHelper, never()).executeSelect(anyString(), any(List.class));
    }

    @Test
    void findBuildsForArtifactChecksum_Success() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(1L);
        when(rs.getString(2)).thenReturn("buildName");
        when(rs.getString(3)).thenReturn("buildNumber");
        when(rs.getLong(4)).thenReturn(1234L);
        when(rs.getString(5)).thenReturn("ciUrl");
        when(rs.getLong(6)).thenReturn(233L);
        when(rs.getString(7)).thenReturn("created-by");
        when(rs.getLong(8)).thenReturn(7837832748L);
        when(rs.getString(9)).thenReturn("modified-by");
        when(rs.getString(10)).thenReturn("some-repo");
        when(rs.next()).thenReturn(true, false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        Collection<BuildEntity> builds = buildsDao.findBuildsForArtifactChecksum(SHA1, "", "buildRepo");
        assertEquals(1, builds.size());

        BuildEntity be = builds.iterator().next();
        assertEquals(1L, be.getBuildId());
        assertEquals("buildName", be.getBuildName());
        assertEquals("buildNumber", be.getBuildNumber());
        assertEquals(1234L, be.getBuildDate());
        assertEquals("ciUrl", be.getCiUrl());
        assertEquals(233L, be.getCreated());
        assertEquals("created-by", be.getCreatedBy());
        assertEquals(7837832748L, be.getModified());
        assertEquals("modified-by", be.getModifiedBy());
        assertEquals("some-repo", be.getRepo());
    }

    @Test
    void populateReleaseBundleNames_Success() throws SQLException {
        BuildIdImpl buildA = new BuildIdImpl(1L, "A", "1", 123456L, "X");
        BuildIdImpl buildB = new BuildIdImpl(2L, "B", "2", 123457L, "X");

        ResultSet rsA = mock(ResultSet.class);
        when(rsA.next()).thenReturn(true).thenReturn(false);
        when(rsA.getString("repo")).thenReturn("X");
        when(rsA.getString("build_name")).thenReturn("A");
        when(rsA.getString("bundle_repository")).thenReturn("repoA");
        when(rsA.getString("bundle_name")).thenReturn("nameA");

        ResultSet rsB = mock(ResultSet.class);
        when(rsB.next()).thenReturn(true).thenReturn(false);
        when(rsB.getString("repo")).thenReturn("X");
        when(rsB.getString("build_name")).thenReturn("B");
        when(rsB.getString("bundle_repository")).thenReturn("repoB");
        when(rsB.getString("bundle_name")).thenReturn("nameB");

        when(jdbcHelper.executeSelect(anyString(), eq(List.of("A")))).thenReturn(rsA);
        when(jdbcHelper.executeSelect(anyString(), eq(List.of("B")))).thenReturn(rsB);

        buildsDao.populateReleaseBundleNames(List.of(buildA, buildB));

        assertEquals(1, buildA.getReleaseBundles().size());
        BuildReleaseBundle bundleA = buildA.getReleaseBundles().iterator().next();
        assertEquals("repoA", bundleA.repoKey());
        assertEquals("nameA", bundleA.name());

        assertEquals(1, buildB.getReleaseBundles().size());
        BuildReleaseBundle bundleB = buildB.getReleaseBundles().iterator().next();
        assertEquals("repoB", bundleB.repoKey());
        assertEquals("nameB", bundleB.name());
    }

    @Test
    void insertBuildRelToReleaseBundle_Success() throws SQLException {
        long id = 1L;
        long buildId = 123L;
        String repoKey = "A";
        String bundleName = "B";
        String bundleVersion = "C";
        when(bridgeToArtifactory.getNextDatabaseId()).thenReturn(id);
        buildsDao.insertBuildRelToReleaseBundle(buildId, repoKey, bundleName, bundleVersion);
        verify(jdbcHelper).executeUpdate(anyString(), eq(id), eq(buildId),
                eq(repoKey), eq(bundleName), eq(bundleVersion));
    }

    @Test
    void deleteBuildRelToReleaseBundle_Success() throws SQLException {
        String repoKey = "A";
        String bundleName = "B";
        String bundleVersion = "C";
        buildsDao.deleteBuildRelToReleaseBundle(repoKey, bundleName, bundleVersion);
        verify(jdbcHelper).executeUpdate(anyString(), eq(repoKey), eq(bundleName), eq(bundleVersion));
    }

    @Test
    void isImmutable_Success() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getInt(anyString())).thenReturn(1);
        when(jdbcHelper.executeSelect(any(), any(Object[].class))).thenReturn(rs);
        assertTrue(buildsDao.isImmutable("build-name", "build-number", 123L));
    }

    @Test
    void isImmutable_NotFound() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(), any(Object[].class))).thenReturn(rs);
        assertThrows(NotFoundException.class, () -> buildsDao.isImmutable("build-name", "build-number", 123L));
    }

    @NonNull
    private static PublishedModule buildPublishedModule(
            @Nullable String id,
            @Nullable String type,
            @Nullable String buildId,
            @Nullable String buildNumber) {
        return PublishedModule.builder()
                .id(id)
                .type(type)
                .buildId(buildId)
                .buildNumber(buildNumber)
                .numOfArtifact("0")
                .numOfDependencies("1")
                .build();
    }

    @ParameterizedTest
    @MethodSource("selectDependencyNodesBySha1_QueryGeneration_Provider")
    void selectDependencyNodesBySha1_QueryGeneration(
            @NonNull DbType dbType,
            @NonNull String expectedDistinctOnClause) throws SQLException {
        // Arrange - use single SHA1 to avoid bulk partitioning (dbOperationsBulkSize is 1 in test setup)
        List<String> sha1s = List.of("sha1_value1");
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(dbType);

        String expectedQuery = getExpectedSelectDependencyNodesBySha1Query(expectedDistinctOnClause);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(eq(expectedQuery), any(Object[].class))).thenReturn(rs);

        // Act
        buildsDao.selectDependencyNodesBySha1(sha1s);

        // Assert
        verify(jdbcHelper).executeSelect(eq(expectedQuery), any(Object[].class));
    }

    @Test
    void selectDependencyNodesBySha1_Success() throws SQLException {
        // Arrange
        List<String> sha1s = List.of("abc123sha1", "xyz789sha1");
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);

        String expectedQuery = getExpectedSelectDependencyNodesBySha1Query("");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("sha1_actual")).thenReturn("abc123sha1", "xyz789sha1");
        when(rs.getString("repo")).thenReturn("repo1", "repo2");
        when(rs.getString("node_path")).thenReturn("path1", "path2");
        when(rs.getString("node_name")).thenReturn("artifact1.jar", "artifact2.jar");
        when(rs.next()).thenReturn(true, true, false);

        when(jdbcHelper.executeSelect(eq(expectedQuery), any(Object[].class))).thenReturn(rs);

        // Act
        var results = buildsDao.selectDependencyNodesBySha1(sha1s);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.containsKey("abc123sha1"));
        assertTrue(results.containsKey("xyz789sha1"));
        assertEquals("artifact1.jar", results.get("abc123sha1").getFirst().getName());
        assertEquals("artifact2.jar", results.get("xyz789sha1").getFirst().getName());
    }

    @Test
    void selectDependencyNodesBySha1_Postgres_Success() throws SQLException {
        // Arrange
        List<String> sha1s = List.of("abc123sha1", "xyz789sha1");
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(POSTGRESQL);

        String expectedQuery = getExpectedSelectDependencyNodesBySha1Query(
                "ON (nodes.sha1_actual, nodes.repo, nodes.node_name)");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("sha1_actual")).thenReturn("abc123sha1", "xyz789sha1");
        when(rs.getString("repo")).thenReturn("pg-repo1", "pg-repo2");
        when(rs.getString("node_path")).thenReturn("pg/path1", "pg/path2");
        when(rs.getString("node_name")).thenReturn("pg-artifact1.jar", "pg-artifact2.jar");
        when(rs.next()).thenReturn(true, true, false);

        when(jdbcHelper.executeSelect(eq(expectedQuery), any(Object[].class))).thenReturn(rs);

        // Act
        var results = buildsDao.selectDependencyNodesBySha1(sha1s);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.containsKey("abc123sha1"));
        assertTrue(results.containsKey("xyz789sha1"));
        assertEquals("pg-artifact1.jar", results.get("abc123sha1").getFirst().getName());
        assertEquals("pg-artifact2.jar", results.get("xyz789sha1").getFirst().getName());
        assertEquals("pg-repo1", results.get("abc123sha1").getFirst().getRepo());
        assertEquals("pg/path1", results.get("abc123sha1").getFirst().getPath());
    }

    @Test
    void selectDependencyNodesBySha1_WithPartitioning() throws SQLException {
        // Arrange - override dbOperationsBulkSize to 2, so 3 SHA1s will result in 2 partitions/calls
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);

        List<String> sha1s = List.of("sha1_1", "sha1_2", "sha1_3");
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);

        String expectedQuery = getExpectedSelectDependencyNodesBySha1Query("");

        // Mock 2 separate ResultSets for 2 partitions: [sha1_1, sha1_2] and [sha1_3]
        // First partition returns sha1_1 and sha1_2
        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.getString("sha1_actual")).thenReturn("sha1_1", "sha1_2");
        when(rs1.getString("repo")).thenReturn("repo1", "repo2");
        when(rs1.getString("node_path")).thenReturn("path1", "path2");
        when(rs1.getString("node_name")).thenReturn("artifact1.jar", "artifact2.jar");
        when(rs1.next()).thenReturn(true, true, false);

        // Second partition returns sha1_3
        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.getString("sha1_actual")).thenReturn("sha1_3");
        when(rs2.getString("repo")).thenReturn("repo3");
        when(rs2.getString("node_path")).thenReturn("path3");
        when(rs2.getString("node_name")).thenReturn("artifact3.jar");
        when(rs2.next()).thenReturn(true, false);

        when(jdbcHelper.executeSelect(eq(expectedQuery), any(Object[].class)))
                .thenReturn(rs1, rs2);

        // Act
        var results = buildsDao.selectDependencyNodesBySha1(sha1s);

        // Assert
        assertEquals(3, results.size());
        assertTrue(results.containsKey("sha1_1"));
        assertTrue(results.containsKey("sha1_2"));
        assertTrue(results.containsKey("sha1_3"));
        assertEquals("artifact1.jar", results.get("sha1_1").getFirst().getName());
        assertEquals("artifact2.jar", results.get("sha1_2").getFirst().getName());
        assertEquals("artifact3.jar", results.get("sha1_3").getFirst().getName());
        // Verify 2 database calls were made (one per partition: [sha1_1, sha1_2] and [sha1_3])
        verify(jdbcHelper, times(2)).executeSelect(eq(expectedQuery), any(Object[].class));
    }

    @Test
    void selectDependencyNodesBySha1_EmptyList() throws SQLException {
        // Arrange
        List<String> sha1s = List.of();

        // Act
        var results = buildsDao.selectDependencyNodesBySha1(sha1s);

        // Assert
        assertEquals(0, results.size());
    }

    @Test
    void getBuildsCountFromSpecificRepo_shouldReturnCount_whenRepoGiven() throws SQLException {
        when(jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM builds WHERE repo = ?", "my-repo")).thenReturn(7);

        int count = buildsDao.getBuildsCountFromSpecificRepo("my-repo");

        assertEquals(7, count);
    }

    @Test
    void getUniqueBuildsCountFromSpecificRepo_shouldReturnCount_whenRepoGiven() throws SQLException {
        when(jdbcHelper.executeSelectCount(
                "SELECT COUNT(DISTINCT build_name) FROM builds WHERE repo = ?", "my-repo")).thenReturn(3);

        int count = buildsDao.getUniqueBuildsCountFromSpecificRepo("my-repo");

        assertEquals(3, count);
    }

    @Test
    void getTotalBuildsCount_shouldReturnCount() throws SQLException {
        when(jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM builds")).thenReturn(100);

        int count = buildsDao.getTotalBuildsCount();

        assertEquals(100, count);
    }

    @Test
    void getBuildStoringRepo_shouldReturnRepo_whenBuildExists() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn("artifactory-build-info");
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"), eq("1"), eq(1000L))).thenReturn(rs);

        String repo = buildsDao.getBuildStoringRepo("my-build", "1", 1000L);

        assertEquals("artifactory-build-info", repo);
    }

    @Test
    void getBuildStoringRepo_shouldReturnNull_whenBuildNotFound() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"), eq("1"), eq(1000L))).thenReturn(rs);

        String repo = buildsDao.getBuildStoringRepo("my-build", "1", 1000L);

        assertNull(repo);
    }

    @Test
    void deleteAllBuilds_shouldExecuteThreeDeletes_andReturnSum() throws SQLException {
        when(jdbcHelper.executeUpdate("DELETE FROM build_props")).thenReturn(10);
        when(jdbcHelper.executeUpdate("DELETE FROM build_promotions")).thenReturn(5);
        when(jdbcHelper.executeUpdate("DELETE FROM builds")).thenReturn(7);

        int result = buildsDao.deleteAllBuilds();

        assertEquals(22, result);
        verify(jdbcHelper).executeUpdate("DELETE FROM build_props");
        verify(jdbcHelper).executeUpdate("DELETE FROM build_promotions");
        verify(jdbcHelper).executeUpdate("DELETE FROM builds");
    }

    @Test
    void deleteBuild_shouldExecuteThreeDeletesByBuildId_andReturnSum() throws SQLException {
        when(jdbcHelper.executeUpdate("DELETE FROM build_props WHERE build_id=?", 42L)).thenReturn(2);
        when(jdbcHelper.executeUpdate("DELETE FROM build_promotions WHERE build_id=?", 42L)).thenReturn(1);
        when(jdbcHelper.executeUpdate("DELETE FROM builds WHERE build_id=?", 42L)).thenReturn(1);

        int result = buildsDao.deleteBuild(42L);

        assertEquals(4, result);
        verify(jdbcHelper).executeUpdate("DELETE FROM build_props WHERE build_id=?", 42L);
        verify(jdbcHelper).executeUpdate("DELETE FROM build_promotions WHERE build_id=?", 42L);
        verify(jdbcHelper).executeUpdate("DELETE FROM builds WHERE build_id=?", 42L);
    }

    @Test
    void findBuildIds_shouldReturnIds_whenBuildNameGiven() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong(1)).thenReturn(1L, 2L);
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"))).thenReturn(rs);

        List<Long> ids = buildsDao.findBuildIds("my-build");

        assertEquals(List.of(1L, 2L), ids);
    }

    @Test
    void findBuildIds_shouldReturnIds_whenBuildNameAndRepoGiven() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong(1)).thenReturn(10L);
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"), eq("my-repo"))).thenReturn(rs);

        List<Long> ids = buildsDao.findBuildIds("my-build", "my-repo");

        assertEquals(List.of(10L), ids);
    }

    @Test
    void findBuildIds_shouldReturnIds_whenBuildNameNumberAndRepoGiven() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong(1)).thenReturn(5L, 6L);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("2"), eq("repo"))).thenReturn(rs);

        List<Long> ids = buildsDao.findBuildIds("b", "2", "repo");

        assertEquals(List.of(5L, 6L), ids);
    }

    @Test
    void findLatestBuild_shouldReturnBuildRun_whenBuildExists() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong(1)).thenReturn(2000L);
        when(jdbcHelper.executeSelect(
                anyString(), eq("my-build"), eq("1"), eq("my-repo"))).thenReturn(rs);

        BuildRun result = buildsDao.findLatestBuild("my-build", "1", "my-repo");

        assertInstanceOf(BuildRunImpl.class, result);
        assertEquals("my-build", result.getName());
        assertEquals("1", result.getNumber());
        assertEquals("my-repo", result.getBuildRepo());
    }

    @Test
    void findLatestBuild_shouldReturnNull_whenNoBuildFound() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"), eq("1"), eq("my-repo"))).thenReturn(rs);

        BuildRun result = buildsDao.findLatestBuild("my-build", "1", "my-repo");

        assertNull(result);
    }

    @Test
    void insertBuild_shouldExecuteInsertWithBuildFields() throws SQLException {
        BuildEntity build = new BuildEntity(1L, "my-build", "1", 1700000000000L, "http://ci", 1700000001000L,
                "user", 1700000002000L, "admin", "my-repo");
        buildsDao.insertBuild(build);
        verify(jdbcHelper).executeUpdate(anyString(), eq(1L), eq("my-build"), eq("1"), eq(1700000000000L),
                eq("http://ci"), eq(1700000001000L), eq("user"), any(), eq("admin"), eq("my-repo"));
    }

    @Test
    void createBuild_shouldCallInsertBuildAndCreatePropertiesAndPromotions() throws SQLException {
        BuildEntity build = new BuildEntity(1L, "b", "1", 1700000000000L, "url", 1700000001000L, "u", 0L, null,
                "repo", ImmutableSet.of(), ImmutableSortedSet.of());
        buildsDao.createBuild(build);
        verify(jdbcHelper).executeUpdate(anyString(), eq(1L), eq("b"), eq("1"), eq(1700000000000L), eq("url"),
                eq(1700000001000L), eq("u"), any(), eq(null), eq("repo"));
    }

    @Test
    void findBuildId_shouldReturnZero_whenNoRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("n"), eq("1"), eq(1700000000000L))).thenReturn(rs);
        assertEquals(0L, buildsDao.findBuildId("n", "1", 1700000000000L));
    }

    @Test
    void findBuildId_shouldReturnBuildId_whenRowExists() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("build_id")).thenReturn(99L);
        when(jdbcHelper.executeSelect(anyString(), eq("n"), eq("1"), eq(1700000000000L))).thenReturn(rs);
        assertEquals(99L, buildsDao.findBuildId("n", "1", 1700000000000L));
    }

    @Test
    void findBuild_shouldReturnNull_whenFindBuildIdReturnsZero() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("n"), eq("1"), eq(1700000000000L))).thenReturn(rs);
        assertNull(buildsDao.findBuild("n", "1", 1700000000000L));
    }

    @Test
    void getBuild_shouldReturnNull_whenBuildNotFound() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq(1L))).thenReturn(rs);
        assertNull(buildsDao.getBuild(1L));
    }

    @Test
    void getBuildForName_shouldReturnEmptyList_whenLimitZeroOrNegative() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(0L);
        List<?> result = buildsDao.getBuildForName("b", "repo", filter);
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void getPrevBuildsList_shouldReturnEmptyList_whenNoRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("repo"), eq(12345L))).thenReturn(rs);
        List<GeneralBuild> result = buildsDao.getPrevBuildsList("b", "12345", "repo");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getPrevBuildsList_shouldReturnGeneralBuildList_whenRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong(1)).thenReturn(1L);
        when(rs.getString("build_name")).thenReturn("my-build");
        when(rs.getString("build_number")).thenReturn("1");
        when(rs.getLong("build_date")).thenReturn(1700000000000L);
        when(rs.getString("ci_url")).thenReturn("http://ci");
        when(rs.getLong("created")).thenReturn(1700000001000L);
        when(rs.getString("created_by")).thenReturn("user");
        when(rs.getLong("modified")).thenReturn(1700000002000L);
        when(rs.getString("modified_by")).thenReturn("admin");
        when(rs.getString("repo")).thenReturn("repo");
        when(rs.getString(10)).thenReturn("released");
        when(jdbcHelper.executeSelect(anyString(), eq("my-build"), eq("repo"), eq(1700000000000L))).thenReturn(rs);
        List<GeneralBuild> result = buildsDao.getPrevBuildsList("my-build", "1700000000000", "repo");
        assertEquals(1, result.size());
        assertEquals("my-build", result.getFirst().getBuildName());
        assertEquals("released", result.getFirst().getStatus());
    }

    @Test
    void getBuildForName_shouldReturnList_whenLimitPositiveAndDaoReturnsRows() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(10L);
        when(filter.getContinueBuildId()).thenReturn(null);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("build_id")).thenReturn(1L);
        when(rs.getString("build_name")).thenReturn("b");
        when(rs.getString("build_number")).thenReturn("1");
        when(rs.getLong("build_date")).thenReturn(1700000000000L);
        when(rs.getString("ci_url")).thenReturn(null);
        when(rs.getLong("created")).thenReturn(1L);
        when(rs.getString("created_by")).thenReturn(null);
        when(rs.getLong("modified")).thenReturn(1L);
        when(rs.getString("modified_by")).thenReturn(null);
        when(rs.getString("repo")).thenReturn("repo");
        when(rs.getInt("module_cnt")).thenReturn(0);
        when(rs.getInt("artifact_cnt")).thenReturn(0);
        when(rs.getInt("dependency_cnt")).thenReturn(0);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<GeneralBuild> result = buildsDao.getBuildForName("b", "repo", filter);
        assertEquals(1, result.size());
        assertEquals("b", result.getFirst().getBuildName());
    }

    @Test
    void getModuleArtifactsForDiffWithPaging_shouldReturnEmptyList_whenNoRows() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<ModuleArtifact> result = buildsDao.getModuleArtifactsForDiffWithPaging(params, false);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildPropsList_withBuildParams_shouldReturnEmptyList_whenGetBuildIdReturnsNull() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        when(params.getBuildName()).thenReturn("b");
        when(params.getCurrBuildNum()).thenReturn("1");
        when(params.getCurrBuildDate()).thenReturn(1700000000000L);
        when(params.isEnvProps()).thenReturn(false);
        ResultSet rsId = mock(ResultSet.class);
        when(rsId.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rsId);
        List<BuildProps> result = buildsDao.getBuildPropsList(params);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildPropsList_withQueryAndBuildId_shouldReturnEmptyList_whenBuildIdNull() {
        List<BuildProps> result = buildsDao.getBuildPropsList("SELECT * FROM build_props WHERE build_id = ?", null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildPropsList_withQueryAndBuildId_shouldReturnList_whenRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString(1)).thenReturn("key1");
        when(rs.getString(2)).thenReturn("value1");
        when(jdbcHelper.executeSelect(anyString(), eq(1L))).thenReturn(rs);
        List<BuildProps> result = buildsDao.getBuildPropsList("SELECT prop_key, prop_value FROM build_props", 1L);
        assertEquals(1, result.size());
        assertEquals("key1", result.getFirst().getKey());
        assertEquals("value1", result.getFirst().getValue());
    }

    @Test
    void getBuildId_shouldReturnNull_whenNoRow() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        when(params.getBuildName()).thenReturn("b");
        when(params.getCurrBuildNum()).thenReturn("1");
        when(params.getCurrBuildDate()).thenReturn(1700000000000L);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        Long result = buildsDao.getBuildId(params);
        assertNull(result);
    }

    @Test
    void getBuildId_shouldReturnId_whenRowExists() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        when(params.getBuildName()).thenReturn("b");
        when(params.getCurrBuildNum()).thenReturn("1");
        when(params.getCurrBuildDate()).thenReturn(1700000000000L);
        when(params.getBuildRepo()).thenReturn("artifactory-build-info");
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong(1)).thenReturn(99L);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        when(jdbcHelper.executeSelect(anyString(), paramsCaptor.capture())).thenReturn(rs);
        Long result = buildsDao.getBuildId(params);
        assertEquals(99L, result);
        // RTDEV-94069: repo must be part of the lookup key so props are scoped to the authorized repo.
        assertEquals(List.of("b", "1", 1700000000000L, "artifactory-build-info"),
                List.of(paramsCaptor.getValue()));
    }

    @Test
    void getModuleDependencyForDiffWithPaging_shouldReturnEmptyList_whenNoRows() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<ModuleDependency> result = buildsDao.getModuleDependencyForDiffWithPaging(params);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getModuleDependencyForDiffWithPaging_shouldReturnList_whenRowsExist() throws SQLException {
        BuildParams params = mock(BuildParams.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString(1)).thenReturn("dep-id");
        when(rs.getString(2)).thenReturn("jar");
        when(rs.getString(3)).thenReturn("sha1val");
        when(rs.getString(4)).thenReturn("compile");
        when(rs.getString(5)).thenReturn("Added");
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<ModuleDependency> result = buildsDao.getModuleDependencyForDiffWithPaging(params);
        assertEquals(1, result.size());
        assertEquals("dep-id", result.getFirst().getName());
        assertEquals("Added", result.getFirst().getStatus());
    }

    @Test
    void getLatestBuild_shouldReturnNull_whenMaxBuildDateZero() throws SQLException {
        ResultSet rsMax = mock(ResultSet.class);
        when(rsMax.next()).thenReturn(true).thenReturn(false);
        when(rsMax.getLong(1)).thenReturn(0L);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("repo"))).thenReturn(rsMax);
        BuildEntity result = buildsDao.getLatestBuild("b", "repo");
        assertNull(result);
    }

    @Test
    void getLatestBuild_shouldReturnEntity_whenBuildExists() throws SQLException {
        ResultSet rsMax = mock(ResultSet.class);
        when(rsMax.next()).thenReturn(true).thenReturn(false);
        when(rsMax.getLong(1)).thenReturn(1700000000000L);
        ResultSet rsBuild = mock(ResultSet.class);
        when(rsBuild.next()).thenReturn(true).thenReturn(false);
        when(rsBuild.getLong(1)).thenReturn(1L);
        when(rsBuild.getString(2)).thenReturn("b");
        when(rsBuild.getString(3)).thenReturn("1");
        when(rsBuild.getLong(4)).thenReturn(1700000000000L);
        when(rsBuild.getString(5)).thenReturn("http://ci");
        when(rsBuild.getLong(6)).thenReturn(1L);
        when(rsBuild.getString(7)).thenReturn("u");
        when(rsBuild.getLong(8)).thenReturn(1L);
        when(rsBuild.getString(9)).thenReturn("a");
        when(rsBuild.getString(10)).thenReturn("repo");
        ResultSet rsProps = mock(ResultSet.class);
        when(rsProps.next()).thenReturn(false);
        ResultSet rsPromo = mock(ResultSet.class);
        when(rsPromo.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("repo"))).thenReturn(rsMax);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq(1700000000000L), eq("repo"))).thenReturn(rsBuild);
        when(jdbcHelper.executeSelect(anyString(), eq(1L))).thenReturn(rsProps).thenReturn(rsPromo);
        BuildEntity result = buildsDao.getLatestBuild("b", "repo");
        assertNotNull(result);
        assertEquals("b", result.getBuildName());
        assertEquals("1", result.getBuildNumber());
    }

    @Test
    void getAllBuildNames_shouldReturnList_whenRepoGiven() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("build_name")).thenReturn("my-build");
        when(rs.getString("repo")).thenReturn("my-repo");
        when(jdbcHelper.executeSelect(anyString(), eq("repo"))).thenReturn(rs);
        List<BuildNameAndRepo> result = buildsDao.getAllBuildNames("repo");
        assertEquals(1, result.size());
        assertEquals("my-build", result.getFirst().getName());
        assertEquals("my-repo", result.getFirst().getRepo());
    }

    @Test
    void getAllBuildNames_shouldReturnList_whenRepoNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString())).thenReturn(rs);
        List<BuildNameAndRepo> result = buildsDao.getAllBuildNames(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildNames_shouldReturnList_whenDaoReturnsRows() throws SQLException {
        MockedStatic<BuildsDaoUtils> mocked = mockStatic(BuildsDaoUtils.class);
        BaseQueryBuilder queryBuilder = mock(BaseQueryBuilder.class);
        mocked.when(() -> BuildsDaoUtils.createSqlBuilder(POSTGRESQL)).thenReturn(queryBuilder);
        when(queryBuilder.build(anyString(), anyString(), anyLong(), anyLong())).thenReturn("SELECT * FROM builds");
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("build_name")).thenReturn("b");
        when(rs.getString("repo")).thenReturn("r");
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<BuildNameAndRepo> result = buildsDao.getBuildNames(List.of("r"), "b", 0L, 10L);
        assertEquals(1, result.size());
        assertEquals("b", result.getFirst().getName());
        assertEquals("r", result.getFirst().getRepo());
        mocked.close();
    }

    @Test
    void getBuildNumbers_shouldReturnEmptyList_whenNoRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<BuildNumberRecord> result = buildsDao.getBuildNumbers(
                "repo", "b", 1700000000000L, false, 0L, 10L, 5L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void selectBuildNumbersNotInRepo_shouldReturnList_whenRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("repo")).thenReturn("other-repo");
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("repo"))).thenReturn(rs);
        List<String> result = buildsDao.selectBuildNumbersNotInRepo("b", "1", "repo");
        assertEquals(1, result.size());
        assertEquals("other-repo", result.getFirst());
    }

    @Test
    void selectBuildNumbersNotInRepo_shouldReturnEmptyList_whenNoRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), anyString(), anyString(), anyString())).thenReturn(rs);
        List<String> result = buildsDao.selectBuildNumbersNotInRepo("b", "1", "repo");
        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildModule_shouldReturnList_whenRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString(1)).thenReturn("mod/1");
        when(rs.getString(2)).thenReturn("2");
        when(rs.getString(3)).thenReturn("3");
        when(rs.getString(4)).thenReturn("build");
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq(1700000000000L), eq("repo")))
                .thenReturn(rs);
        List<PublishedModule> result = buildsDao.getBuildModule("b", "1", "1700000000000", "repo");
        assertEquals(1, result.size());
        assertEquals("mod/1", result.getFirst().getId());
        assertEquals("build", result.getFirst().getType());
    }

    @Test
    void getModuleArtifact_shouldReturnEmptyList_whenNoArtifacts() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1"))).thenReturn(rs);
        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", false);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * RTDEV-71186: getPaginatedArtifact should use GROUP BY + COUNT(*) (not SELECT DISTINCT) so that the
     * un-collapsed cardinality survives as the {@code occurrences} column for the matching loop's cap.
     */
    @Test
    void getModuleArtifact_paginatedArtifactQuery_usesGroupByWithCount() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcHelper.executeSelect(sqlCaptor.capture(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(rs);

        buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("COUNT(*)"), "Query should expose un-collapsed cardinality via COUNT(*)");
        assertTrue(sql.contains("GROUP BY"), "Query should GROUP BY (name, type, sha1) instead of SELECT DISTINCT");
        assertTrue(sql.contains("AS occurrences"), "COUNT(*) should be aliased as 'occurrences'");
        assertFalse(sql.toLowerCase().contains("select distinct"),
                "Query must no longer use SELECT DISTINCT; the row count is needed for the fan-out cap");
    }

    /**
     * RTDEV-71186 single-node case: occurrences=1 and one matching node -> exactly one ModuleArtifact.
     * Establishes the no-regression baseline for the typical single-artifact scenario.
     */
    @Test
    void getModuleArtifact_singleNode_emitsOneArtifact() throws SQLException {
        ResultSet artifactRs = mockArtifactRows(List.of(new MockArtifactRow("foo.jar", "jar", "sha-A", 1)));
        ResultSet nodesRs = mockNodeRows(List.of(new MockNodeRow("sha-A", "repo-x", "path/to", "foo.jar")));

        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(artifactRs);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("auto-trashcan"))).thenReturn(nodesRs);

        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        assertEquals(1, result.size());
        assertEquals("foo.jar", result.getFirst().getName());
        assertEquals("repo-x", result.getFirst().getRepoKey());
        assertEquals("path/to/foo.jar", result.getFirst().getPath());
    }

    /**
     * RTDEV-71186 customer repro: occurrences=2 and two name-matching nodes (same name+SHA, different paths)
     * -> emit one ModuleArtifact per node. This is the bug being fixed.
     */
    @Test
    void getModuleArtifact_sameNameMultiplePaths_fansOutPerNode() throws SQLException {
        ResultSet artifactRs = mockArtifactRows(List.of(new MockArtifactRow("test.txt", "generic", "sha-A", 2)));
        ResultSet nodesRs = mockNodeRows(List.of(
                new MockNodeRow("sha-A", "generic-local", "moduleA", "test.txt"),
                new MockNodeRow("sha-A", "generic-local", "moduleB", "test.txt")));

        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(artifactRs);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("auto-trashcan"))).thenReturn(nodesRs);

        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        assertEquals(2, result.size(), "Both deployment paths should surface");
        assertEquals(List.of("moduleA/test.txt", "moduleB/test.txt"),
                result.stream().map(ModuleArtifact::getPath).toList());
    }

    /**
     * RTDEV-71186 multi-module cap: occurrences=1 (one row in the current module) but the SHA-scoped node
     * lookup returns nodes from BOTH modules. The cap must keep the result at 1 -- no phantom row from the
     * sibling module.
     */
    @Test
    void getModuleArtifact_multiModuleCollision_capPreventsPhantom() throws SQLException {
        ResultSet artifactRs = mockArtifactRows(List.of(new MockArtifactRow("lib.jar", "jar", "sha-A", 1)));
        ResultSet nodesRs = mockNodeRows(List.of(
                new MockNodeRow("sha-A", "libs-release", "moduleA", "lib.jar"),
                new MockNodeRow("sha-A", "libs-release", "moduleB", "lib.jar")));

        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(artifactRs);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("auto-trashcan"))).thenReturn(nodesRs);

        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        assertEquals(1, result.size(), "Cap must clip the result to the current module's occurrences count");
        assertTrue(List.of("moduleA/lib.jar", "moduleB/lib.jar").contains(result.getFirst().getPath()),
                "The picked path must be a real deployment path, not a phantom synthesis");
    }

    /**
     * RTDEV-71186 lower-bounded cap: occurrences=2 but only 1 name-matching node exists. Emit only what
     * actually exists (1 row) -- the cap is min(occurrences, nameMatchingNodes.size()), not max.
     */
    @Test
    void getModuleArtifact_fewerNodesThanOccurrences_emitsOnlyExistingNodes() throws SQLException {
        ResultSet artifactRs = mockArtifactRows(List.of(new MockArtifactRow("foo.jar", "jar", "sha-A", 2)));
        ResultSet nodesRs = mockNodeRows(List.of(new MockNodeRow("sha-A", "repo-x", "path/to", "foo.jar")));

        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(artifactRs);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("auto-trashcan"))).thenReturn(nodesRs);

        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        assertEquals(1, result.size(), "Should emit only the nodes that actually exist");
    }

    /**
     * RTDEV-71186 fallback path: no node has a matching name. Preserve today's "first by SHA" fallback so
     * we don't regress existing behaviour for cases where manifest name and node name disagree.
     */
    @Test
    void getModuleArtifact_noNameMatchingNode_usesShaFallback() throws SQLException {
        ResultSet artifactRs = mockArtifactRows(List.of(new MockArtifactRow("foo.jar", "jar", "sha-A", 1)));
        ResultSet nodesRs = mockNodeRows(List.of(
                new MockNodeRow("sha-A", "repo-x", "path/to", "different-name.jar")));

        when(jdbcHelper.executeSelect(anyString(), eq("1"), eq(1700000000000L), eq("repo"), eq("mod-1")))
                .thenReturn(artifactRs);
        when(jdbcHelper.executeSelect(anyString(), eq("b"), eq("1"), eq("auto-trashcan"))).thenReturn(nodesRs);

        List<ModuleArtifact> result =
                buildsDao.getModuleArtifact("b", "1", "1700000000000", "mod-1", "repo", true);

        assertEquals(1, result.size());
        assertEquals("foo.jar", result.getFirst().getName(), "ModuleArtifact name is from build_artifacts");
        assertEquals("repo-x", result.getFirst().getRepoKey(), "Fallback sets repo from the first SHA-matching node");
        assertEquals("path/to/different-name.jar", result.getFirst().getPath());
    }

    private record MockArtifactRow(String name, String type, String sha1, int occurrences) { }

    private record MockNodeRow(String sha1, String repo, String nodePath, String nodeName) { }

    private static ResultSet mockArtifactRows(List<MockArtifactRow> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        stubNextSequence(rs, rows.size());
        stubColumnSequence(when(rs.getString("name")), rows.stream().map(MockArtifactRow::name).toList());
        stubColumnSequence(when(rs.getString("type")), rows.stream().map(MockArtifactRow::type).toList());
        stubColumnSequence(when(rs.getString("sha1")), rows.stream().map(MockArtifactRow::sha1).toList());
        stubIntColumnSequence(when(rs.getInt("occurrences")),
                rows.stream().map(MockArtifactRow::occurrences).toList());
        return rs;
    }

    private static ResultSet mockNodeRows(List<MockNodeRow> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        stubNextSequence(rs, rows.size());
        stubColumnSequence(when(rs.getString("sha1_actual")), rows.stream().map(MockNodeRow::sha1).toList());
        stubColumnSequence(when(rs.getString("repo")), rows.stream().map(MockNodeRow::repo).toList());
        stubColumnSequence(when(rs.getString("node_path")), rows.stream().map(MockNodeRow::nodePath).toList());
        stubColumnSequence(when(rs.getString("node_name")), rows.stream().map(MockNodeRow::nodeName).toList());
        return rs;
    }

    private static void stubNextSequence(ResultSet rs, int trueCount) throws SQLException {
        OngoingStubbing<Boolean> stub = when(rs.next());
        for (int i = 0; i < trueCount; i++) {
            stub = stub.thenReturn(true);
        }
        stub.thenReturn(false);
    }

    private static void stubColumnSequence(OngoingStubbing<String> stub, List<String> values) {
        OngoingStubbing<String> chain = stub;
        for (String value : values) {
            chain = chain.thenReturn(value);
        }
    }

    private static void stubIntColumnSequence(OngoingStubbing<Integer> stub, List<Integer> values) {
        OngoingStubbing<Integer> chain = stub;
        for (Integer value : values) {
            chain = chain.thenReturn(value);
        }
    }

    @Test
    void getLatestBuildIds_shouldReturnEmptyList_whenLimitZeroOrNegative() {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(0L);
        List<BuildIdEntity> result = buildsDao.getLatestBuildIds(filter);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLatestBuildIds_shouldReturnList_whenDaoReturnsRows() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(10L);
        when(filter.getContinueBuildId()).thenReturn(null);
        when(filter.getBuildRepo()).thenReturn("repo");
        when(filter.getOrderBy()).thenReturn(ContinueBuildFilter.OrderBy.BUILD_DATE);
        when(filter.getDirection()).thenReturn(org.jfrog.commons.continues.util.Direction.DESC);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("build_id")).thenReturn(1L);
        when(rs.getString("build_name")).thenReturn("b");
        when(rs.getString("build_number")).thenReturn("1");
        when(rs.getLong("build_date")).thenReturn(1700000000000L);
        when(rs.getString("repo")).thenReturn("repo");
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<BuildIdEntity> result = buildsDao.getLatestBuildIds(filter);
        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().getBuildId());
        assertEquals("b", result.getFirst().getBuildName());
    }

    @Test
    void getDistinctBuildNames_shouldReturnEmptyList_whenLimitZeroOrNegative() {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(0L);
        List<String> result = buildsDao.getDistinctBuildNames(filter);
        assertTrue(result.isEmpty());
    }

    @Test
    void getDistinctBuildNames_shouldReturnList_whenDaoReturnsRows() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getLimit()).thenReturn(10L);
        when(filter.getContinueBuildId()).thenReturn(null);
        when(filter.getBuildRepo()).thenReturn("repo");
        when(filter.getOrderBy()).thenReturn(ContinueBuildFilter.OrderBy.BUILD_NAME);
        when(filter.getDirection()).thenReturn(org.jfrog.commons.continues.util.Direction.ASC);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("build_name")).thenReturn("build-a");
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);
        List<String> result = buildsDao.getDistinctBuildNames(filter);
        assertEquals(1, result.size());
        assertEquals("build-a", result.getFirst());
    }

    @Test
    void getDistinctBuildNamesCount_shouldReturnCount_whenDaoReturns() throws SQLException {
        ContinueBuildFilter filter = mock(ContinueBuildFilter.class);
        when(filter.getContinueBuildId()).thenReturn(null);
        when(filter.getBuildRepo()).thenReturn(null);
        when(filter.getSearchStr()).thenReturn(null);
        when(jdbcHelper.executeSelectCount(anyString(), any(Object[].class))).thenReturn(42);
        int result = buildsDao.getDistinctBuildNamesCount(filter);
        assertEquals(42, result);
    }

    @NonNull
    static Stream<Arguments> selectDependencyNodesBySha1_QueryGeneration_Provider() {
        return Stream.of(
                // Non-PostgreSQL - tests basic query without DISTINCT ON
                Arguments.of(DbType.MYSQL, ""),
                // PostgreSQL - tests query with DISTINCT ON clause
                Arguments.of(POSTGRESQL, "ON (nodes.sha1_actual, nodes.repo, nodes.node_name)")
        );
    }

    @NonNull
    private static String getExpectedSelectDependencyNodesBySha1Query(@NonNull String distinctOnClause) {
        return """
                SELECT DISTINCT %s
                    nodes.repo,
                    nodes.node_path,
                    nodes.node_name,
                    nodes.sha1_actual
                FROM nodes
                WHERE nodes.repo != ?
                  AND nodes.sha1_actual IN (#)
                """.formatted(distinctOnClause);
    }
}