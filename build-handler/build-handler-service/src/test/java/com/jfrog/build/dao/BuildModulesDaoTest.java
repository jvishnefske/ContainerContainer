package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.ModuleProperty;
import com.jfrog.build.service.BuildSysConfigService;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildModulesDaoTest {

    private BuildModulesDao buildModulesDao;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildSysConfigService sysConfigService;
    private JdbcHelper jdbcHelper;

    @BeforeEach
    void setUp() {
        jdbcHelper = mock(JdbcHelper.class);
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        when(bridgeToArtifactory.getJdbcHelper()).thenReturn(jdbcHelper);
        when(bridgeToArtifactory.getDatabaseType()).thenReturn(POSTGRESQL);
        sysConfigService = mock(BuildSysConfigService.class);
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);
        buildModulesDao = new BuildModulesDao(bridgeToArtifactory, sysConfigService);
    }

    @Test
    void createBuildModules_shouldDoNothing_whenListIsEmpty() throws SQLException {
        buildModulesDao.createBuildModules(List.of());
        verify(bridgeToArtifactory, never()).getJdbcHelper();
    }

    @Test
    void createBuildModules_shouldCallExecuteBulkUpdateOnce_whenOneModule() throws SQLException {
        BuildModule module = new BuildModule(1L, 2L, "mod-name", "jar");
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);

        buildModulesDao.createBuildModules(List.of(module));
        verify(sysConfigService).getDbOperationsBulkSize();
        verify(jdbcHelper).executeBulkUpdate(anyString(), any());
    }

    @Test
    void createBuildModuleProperties_shouldDoNothing_whenListIsEmpty() throws SQLException {
        buildModulesDao.createBuildModuleProperties(List.of());
        verify(bridgeToArtifactory, never()).getJdbcHelper();
    }

    @Test
    void createBuildModuleProperties_shouldCallExecuteBulkUpdateOnce_whenOneProperty() throws SQLException {
        ModuleProperty prop = new ModuleProperty(1L, 2L, "key", "value");
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(500);

        buildModulesDao.createBuildModuleProperties(List.of(prop));
        verify(bridgeToArtifactory).getDatabaseType();
        verify(sysConfigService).getDbOperationsBulkSize();
        verify(jdbcHelper).executeBulkUpdate(anyString(), any());
    }

    @Test
    void deleteBuildModules_shouldCallModulePropsDeleteThenBuildModulesDelete_whenModuleIdsNonEmpty()
            throws SQLException {
        when(sysConfigService.getDbOperationsBulkSize()).thenReturn(2);
        when(jdbcHelper.executeUpdate(anyString(), any(List.class))).thenReturn(2);
        when(jdbcHelper.executeUpdate(eq("DELETE FROM build_modules WHERE build_id = ?"), eq(5L))).thenReturn(1);

        int result = buildModulesDao.deleteBuildModules(List.of(1L, 2L), 5L);

        verify(jdbcHelper).executeUpdate(
                eq("DELETE FROM module_props WHERE module_id IN (#)"),
                eq(List.of(1L, 2L)));
        verify(jdbcHelper).executeUpdate("DELETE FROM build_modules WHERE build_id = ?", 5L);
        assertEquals(3, result);
    }

    @Test
    void deleteBuildModules_shouldOnlyDeleteBuildModules_whenModuleIdsEmpty() throws SQLException {
        when(jdbcHelper.executeUpdate(eq("DELETE FROM build_modules WHERE build_id = ?"), eq(5L))).thenReturn(1);

        int result = buildModulesDao.deleteBuildModules(List.of(), 5L);

        verify(jdbcHelper).executeUpdate("DELETE FROM build_modules WHERE build_id = ?", 5L);
        assertEquals(1, result);
    }

    @Test
    void deleteAllBuildModules_shouldCallExecuteUpdateTwice_andReturnSum() throws SQLException {
        when(jdbcHelper.executeUpdate("DELETE FROM module_props")).thenReturn(10);
        when(jdbcHelper.executeUpdate("DELETE FROM build_modules")).thenReturn(3);

        int result = buildModulesDao.deleteAllBuildModules();

        verify(jdbcHelper).executeUpdate("DELETE FROM module_props");
        verify(jdbcHelper).executeUpdate("DELETE FROM build_modules");
        assertEquals(13, result);
    }

    @Test
    void findModuleIdsForBuild_shouldReturnModuleIdsFromResultSet() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong(1)).thenReturn(1L, 2L);
        when(jdbcHelper.executeSelect(
                eq("SELECT module_id FROM build_modules WHERE build_id = ?"),
                eq(100L)))
                .thenReturn(rs);

        List<Long> result = buildModulesDao.findModuleIdsForBuild(100L);

        assertEquals(List.of(1L, 2L), result);
        verify(jdbcHelper).executeSelect(
                "SELECT module_id FROM build_modules WHERE build_id = ?",
                100L);
    }
}
