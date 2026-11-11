package com.jfrog.build.test.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.ModuleProperty;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class TestBuildModulesDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @NonNull
    public List<BuildModule> findModulesForBuild(long buildId) throws SQLException {
        List<BuildModule> modules = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM build_modules WHERE build_id = ?", buildId)) {
            while (rs.next()) {
                BuildModule module = resultSetToBuildModule(rs);
                module.setProperties(findModuleProperties(module.getModuleId()));
                modules.add(module);
            }
        }
        return modules;
    }

    @NonNull
    private Set<ModuleProperty> findModuleProperties(long moduleId) throws SQLException {
        Set<ModuleProperty> props = new HashSet<>(3);
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM module_props WHERE module_id = ?", moduleId)) {
            while (rs.next()) {
                props.add(resultSetToModuleProperty(rs));
            }
        }
        return props;
    }

    @NonNull
    private BuildModule resultSetToBuildModule(@NonNull ResultSet rs) throws SQLException {
        return new BuildModule(
                rs.getLong("module_id"),
                rs.getLong("build_id"),
                rs.getString("module_name_id"),
                rs.getString("type")
        );
    }

    @NonNull
    private ModuleProperty resultSetToModuleProperty(@NonNull ResultSet rs) throws SQLException {
        return new ModuleProperty(
                rs.getLong("prop_id"),
                rs.getLong("module_id"),
                rs.getString("prop_key"),
                rs.getString("prop_value")
        );
    }
}
