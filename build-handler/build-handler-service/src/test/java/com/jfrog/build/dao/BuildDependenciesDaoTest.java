package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.entity.BuildDependency;
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

class BuildDependenciesDaoTest {

    private BuildDependenciesDao buildDependenciesDao;

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
        buildDependenciesDao = new BuildDependenciesDao(bridgeToArtifactory, sysConfigService);
    }

    @Test
    void createBuildDependencies_shouldDoNothing_whenListIsEmpty() throws SQLException {
        buildDependenciesDao.createBuildDependencies(List.of());

        verify(bridgeToArtifactory, never()).getJdbcHelper();
    }

    @Test
    void createBuildDependencies_shouldCallExecuteBulkUpdateOnce_whenOneDependency() throws SQLException {
        BuildDependency dep = new BuildDependency(1L, 2L, "dep-name", "compile", "jar", "sha1", "md5", null);
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);

        buildDependenciesDao.createBuildDependencies(List.of(dep));
        verify(sysConfigService).getDbOperationsBulkSize();
        verify(jdbcHelper).executeBulkUpdate(anyString(), any());
    }

    @Test
    void createBuildDependencies_shouldCallExecuteBulkUpdateTwice_whenMoreThanBulkSize() throws SQLException {
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);
        List<BuildDependency> deps = List.of(
                new BuildDependency(1L, 10L, "d1", "compile", "jar", "s1", "m1", null),
                new BuildDependency(2L, 10L, "d2", "compile", "jar", "s2", "m2", null),
                new BuildDependency(3L, 10L, "d3", "runtime", "jar", "s3", "m3", null)
        );

        buildDependenciesDao.createBuildDependencies(deps);
        verify(jdbcHelper, times(2)).executeBulkUpdate(anyString(), any());
    }

    @Test
    void deleteBuildDependencies_shouldDelegateToExecuteLimitedSizedQuery_andReturnSum() throws SQLException {
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);
        when(jdbcHelper.executeUpdate(anyString(), any(List.class))).thenReturn(2, 1);

        int result = buildDependenciesDao.deleteBuildDependencies(List.of(1L, 2L, 3L));

        verify(jdbcHelper).executeUpdate(
                eq("DELETE FROM build_dependencies WHERE module_id IN (#)"),
                eq(List.of(1L, 2L)));
        verify(jdbcHelper).executeUpdate(
                eq("DELETE FROM build_dependencies WHERE module_id IN (#)"),
                eq(List.of(3L)));
        assertEquals(3, result);
    }

    @Test
    void deleteAllBuildDependencies_shouldCallExecuteUpdateOnce_andReturnValue() throws SQLException {
        when(jdbcHelper.executeUpdate("DELETE FROM build_dependencies")).thenReturn(5);

        int result = buildDependenciesDao.deleteAllBuildDependencies();

        verify(jdbcHelper).executeUpdate("DELETE FROM build_dependencies");
        assertEquals(5, result);
    }

    @Test
    void selectBuildDependencies_shouldReturnOne_whenNodeRowPresent() throws SQLException {
        String buildName = "test-build";
        String buildNumber = "1";
        String buildRepo = "test-repo";
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);

        ResultSet nodeRs = stubSelectBuildDependencyNodeRow();
        when(jdbcHelper.executeSelect(anyString(), eq(buildName), eq(buildNumber), eq(buildRepo))).thenReturn(nodeRs);

        List<BuildFileInfo> dependencies =
                buildDependenciesDao.selectBuildDependencies(buildName, buildNumber, buildRepo);

        assertEquals(1, dependencies.size());
    }

    @Test
    void selectBuildDependencies_shouldReturnEmpty_whenNoRows() throws SQLException {
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("name"), eq("1"), eq("repo"))).thenReturn(rs);

        List<BuildFileInfo> dependencies = buildDependenciesDao.selectBuildDependencies("name", "1", "repo");

        assertTrue(dependencies.isEmpty());
    }

    @Test
    void selectBuildDependencies_shouldPropagateSQLException_whenQueryFails() throws SQLException {
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(DbType.MYSQL);
        when(jdbcHelper.executeSelect(anyString(), eq("name"), eq("1"), eq("repo")))
                .thenThrow(new SQLException("connection error"));

        assertThrows(SQLException.class,
                () -> buildDependenciesDao.selectBuildDependencies("name", "1", "repo"));
    }

    @NonNull
    private static ResultSet stubSelectBuildDependencyNodeRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("repo")).thenReturn("libs-release");
        when(rs.getString("node_path")).thenReturn("com/example");
        when(rs.getString("node_name")).thenReturn("lib.jar");
        when(rs.getLong("node_id")).thenReturn(1L);
        when(rs.getString("sha256")).thenReturn("sha256");
        when(rs.getString("sha1_actual")).thenReturn("dep-sha1");
        when(rs.getString("sha1_original")).thenReturn("dep-sha1");
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
