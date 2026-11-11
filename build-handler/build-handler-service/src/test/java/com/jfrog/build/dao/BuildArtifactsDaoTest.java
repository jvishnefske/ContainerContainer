package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.entity.BuildArtifact;
import com.jfrog.build.service.BuildSysConfigService;
import lombok.NonNull;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildArtifactsDaoTest {

    private BuildArtifactsDao buildArtifactsDao;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildSysConfigService sysConfigService;
    private JdbcHelper jdbcHelper;

    @BeforeEach
    void setUp() {
        jdbcHelper = mock(JdbcHelper.class);
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        when(bridgeToArtifactory.getJdbcHelper()).thenReturn(jdbcHelper);
        sysConfigService = mock(BuildSysConfigService.class);
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);
        buildArtifactsDao = new BuildArtifactsDao(bridgeToArtifactory, sysConfigService);
    }

    @Test
    void createBuildArtifacts_shouldDoNothing_whenListIsEmpty() throws SQLException {
        buildArtifactsDao.createBuildArtifacts(List.of());
        verify(bridgeToArtifactory, never()).getJdbcHelper();
    }

    @Test
    void createBuildArtifacts_shouldCallExecuteBulkUpdateOnce_whenOneArtifact() throws SQLException {
        BuildArtifact artifact = new BuildArtifact(1L, 2L, "artifact.jar", "jar", "sha1", "md5", null);
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);

        buildArtifactsDao.createBuildArtifacts(List.of(artifact));
        verify(sysConfigService).getDbOperationsBulkSize();
        verify(jdbcHelper).executeBulkUpdate(anyString(), any());
    }

    @Test
    void createBuildArtifacts_shouldCallExecuteBulkUpdateTwice_whenMoreThanBulkSize() throws SQLException {
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);
        List<BuildArtifact> artifacts = List.of(
                new BuildArtifact(1L, 10L, "a1", "jar", "s1", "m1", null),
                new BuildArtifact(2L, 10L, "a2", "jar", "s2", "m2", null),
                new BuildArtifact(3L, 10L, "a3", "jar", "s3", "m3", null)
        );

        buildArtifactsDao.createBuildArtifacts(artifacts);
        verify(jdbcHelper, times(2)).executeBulkUpdate(anyString(), any());
    }

    @Test
    void deleteBuildArtifacts_shouldDelegateToExecuteLimitedSizedQuery_andReturnSum() throws SQLException {
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);
        when(jdbcHelper.executeUpdate(anyString(), any(List.class))).thenReturn(2, 1);

        int result = buildArtifactsDao.deleteBuildArtifacts(List.of(1L, 2L, 3L));

        verify(jdbcHelper).executeUpdate(
                eq("DELETE FROM build_artifacts WHERE module_id IN (#)"),
                eq(List.of(1L, 2L)));
        verify(jdbcHelper).executeUpdate(
                eq("DELETE FROM build_artifacts WHERE module_id IN (#)"),
                eq(List.of(3L)));
        assertEquals(3, result);
    }

    @Test
    void deleteAllBuildArtifacts_shouldCallExecuteUpdateOnce_andReturnValue() throws SQLException {
        when(jdbcHelper.executeUpdate("DELETE FROM build_artifacts")).thenReturn(10);

        int result = buildArtifactsDao.deleteAllBuildArtifacts();

        verify(jdbcHelper).executeUpdate("DELETE FROM build_artifacts");
        assertEquals(10, result);
    }

    @Test
    void getBuildArtifacts_shouldReturnOneRecord_whenArtifactShaMatchesNode() throws SQLException {
        String buildName = "buildName";
        String buildNumber = "11";
        String buildRepo = "artifactory-build-info";
        String sha1 = "b471d63e6fc568db5858ea2d27d318099fe7dc8e";

        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(true, false);
        when(shaRs.getString("sha1")).thenReturn(sha1);

        ResultSet nodeRs = mock(ResultSet.class);
        when(nodeRs.next()).thenReturn(true, false);
        when(nodeRs.getString("sha1")).thenReturn(sha1);
        when(jdbcHelper.executeSelect(anyString(), eq(buildRepo), eq(buildName), eq(buildNumber))).thenReturn(shaRs);
        when(jdbcHelper.executeSelect(anyString(), eq(buildName), eq(buildNumber))).thenReturn(nodeRs);

        List<BuildNodeRecord> buildNodes = buildArtifactsDao.getBuildArtifacts(buildName, buildNumber, buildRepo);

        assertEquals(1, buildNodes.size());
    }

    @Test
    void getBuildArtifacts_shouldReturnEmpty_whenNoArtifactSha1() throws SQLException {
        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(false);
        ResultSet nodeRs = mock(ResultSet.class);
        when(nodeRs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("repo"), eq("name"), eq("1"))).thenReturn(shaRs);
        when(jdbcHelper.executeSelect(anyString(), eq("name"), eq("1"))).thenReturn(nodeRs);

        List<BuildNodeRecord> buildNodes = buildArtifactsDao.getBuildArtifacts("name", "1", "repo");

        assertTrue(buildNodes.isEmpty());
    }

    @Test
    void getBuildArtifacts_shouldPropagateSQLException_whenShaQueryFails() throws SQLException {
        when(jdbcHelper.executeSelect(anyString(), eq("repo"), eq("name"), eq("1")))
                .thenThrow(new SQLException("connection error"));

        assertThrows(SQLException.class, () -> buildArtifactsDao.getBuildArtifacts("name", "1", "repo"));
    }

    @Test
    void getBuildArtifacts_withEpoch_shouldReturnOneRecord_whenNodeMatchesSha() throws SQLException {
        String buildName = "test-build";
        String buildNumber = "1";
        String buildRepo = "artifactory-build-info";
        long publishedEpoch = 1767774284174L;
        String sha1 = "testsha1";

        when(sysConfigService.isBuildArtifactsQueryByBuildDateEnabled()).thenReturn(false);

        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(true, false);
        when(shaRs.getString("sha1")).thenReturn(sha1);

        ResultSet nodeRs = mock(ResultSet.class);
        when(nodeRs.next()).thenReturn(true, false);
        when(nodeRs.getString("sha1")).thenReturn(sha1);

        when(jdbcHelper.executeSelect(anyString(), eq(buildRepo), eq(buildName), eq(buildNumber))).thenReturn(shaRs);
        when(jdbcHelper.executeSelect(anyString(), eq(buildName), eq(buildNumber), anyString())).thenReturn(nodeRs);

        List<BuildNodeRecord> buildNodes =
                buildArtifactsDao.getBuildArtifacts(buildName, buildNumber, buildRepo, publishedEpoch);

        assertEquals(1, buildNodes.size());
    }

    @Test
    void getBuildArtifacts_withEpoch_shouldReturnEmpty_whenNoArtifactSha1() throws SQLException {
        when(sysConfigService.isBuildArtifactsQueryByBuildDateEnabled()).thenReturn(false);

        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(false);
        ResultSet nodeRs = mock(ResultSet.class);
        when(nodeRs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("repo"), eq("name"), eq("1"))).thenReturn(shaRs);
        when(jdbcHelper.executeSelect(anyString(), eq("name"), eq("1"), anyString())).thenReturn(nodeRs);

        List<BuildNodeRecord> buildNodes = buildArtifactsDao.getBuildArtifacts("name", "1", "repo", 123L);

        assertTrue(buildNodes.isEmpty());
    }

    @Test
    void selectBuildArtifacts_shouldReturnOne_whenNodeRowPresent() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("test-build");
        when(build.getNumber()).thenReturn("1");
        when(build.getBuildRepo()).thenReturn("test-repo");
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);

        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(true, false);
        when(shaRs.getString("sha1")).thenReturn("abc123sha1");

        ResultSet nodeRs = stubSelectBuildArtifactNodeRow();
        when(jdbcHelper.executeSelect(anyString(), eq("test-repo"), eq("test-build"), eq("1"))).thenReturn(shaRs);
        when(jdbcHelper.executeSelect(anyString(), any())).thenReturn(nodeRs);

        List<BuildFileInfo> results = buildArtifactsDao.selectBuildArtifacts(build, null, null);

        assertEquals(1, results.size());
    }

    @Test
    void selectBuildArtifacts_shouldReturnEmpty_whenNoArtifactSha1() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("test-build");
        when(build.getNumber()).thenReturn("1");
        when(build.getBuildRepo()).thenReturn("test-repo");

        ResultSet shaRs = mock(ResultSet.class);
        when(shaRs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("test-repo"), eq("test-build"), eq("1"))).thenReturn(shaRs);

        List<BuildFileInfo> results = buildArtifactsDao.selectBuildArtifacts(build, null, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void selectBuildArtifacts_shouldPropagateSQLException_whenShaQueryFails() throws SQLException {
        BuildWrapper build = mock(BuildWrapper.class);
        when(build.getName()).thenReturn("error-build");
        when(build.getNumber()).thenReturn("1");
        when(build.getBuildRepo()).thenReturn("error-repo");
        when(jdbcHelper.executeSelect(anyString(), eq("error-repo"), eq("error-build"), eq("1")))
                .thenThrow(new SQLException("connection error"));

        assertThrows(SQLException.class, () -> buildArtifactsDao.selectBuildArtifacts(build, null, null));
    }

    @NonNull
    private static ResultSet stubSelectBuildArtifactNodeRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("repo")).thenReturn("libs-release");
        when(rs.getString("node_path")).thenReturn("com/example");
        when(rs.getString("node_name")).thenReturn("lib.jar");
        when(rs.getLong("node_id")).thenReturn(1L);
        when(rs.getString("sha1_actual")).thenReturn("abc123sha1");
        when(rs.getString("sha1_original")).thenReturn("abc123sha1");
        when(rs.getString("md5_actual")).thenReturn("md5");
        when(rs.getString("md5_original")).thenReturn("md5");
        when(rs.getLong("created")).thenReturn(1L);
        when(rs.getString("created_by")).thenReturn("user");
        when(rs.getLong("modified")).thenReturn(2L);
        when(rs.getString("modified_by")).thenReturn("admin");
        when(rs.getLong("updated")).thenReturn(3L);
        when(rs.getLong("bin_length")).thenReturn(100L);
        return rs;
    }
}
