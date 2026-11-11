package com.jfrog.build.test.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildChecksumType;
import com.jfrog.build.entity.BuildDependency;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

@RequiredArgsConstructor
public class TestBuildDependenciesDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @NonNull
    public List<BuildDependency> findDependenciesForModules(@NonNull List<Long> moduleIds) throws SQLException {
        List<BuildDependency> dependencies = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM build_dependencies WHERE module_id IN (#)", moduleIds)) {
            while (rs.next()) {
                dependencies.add(resultSetToBuildDependency(rs));
            }
        }
        return dependencies;
    }

    @NonNull
    public List<BuildDependency> findDependenciesForModule(long moduleId) throws SQLException {
        List<BuildDependency> dependencies = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM build_dependencies WHERE module_id = ?", moduleId)) {
            while (rs.next()) {
                dependencies.add(resultSetToBuildDependency(rs));
            }
        }
        return dependencies;
    }

    @NonNull
    public List<BuildDependency> findDependenciesForChecksum(
            @NonNull BuildChecksumType type,
            @NonNull String checksum) throws SQLException {
        List<BuildDependency> dependencies = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM build_dependencies WHERE " + type.name() + " = ?", checksum)) {
            while (rs.next()) {
                dependencies.add(resultSetToBuildDependency(rs));
            }
        }
        return dependencies;
    }

    @NonNull
    private BuildDependency resultSetToBuildDependency(@NonNull ResultSet rs) throws SQLException {
        return new BuildDependency(
                rs.getLong("dependency_id"),
                rs.getLong("module_id"),
                rs.getString("dependency_name_id"),
                rs.getString("dependency_scopes"),
                rs.getString("dependency_type"),
                rs.getString("sha1"),
                rs.getString("md5"),
                null
        );
    }
}
