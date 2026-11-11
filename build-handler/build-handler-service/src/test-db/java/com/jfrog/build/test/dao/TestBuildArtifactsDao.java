package com.jfrog.build.test.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildChecksumType;
import com.jfrog.build.entity.BuildArtifact;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

@RequiredArgsConstructor
public class TestBuildArtifactsDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @NonNull
    public List<BuildArtifact> findBuildArtifactsForModules(@NonNull List<Long> moduleIds) throws SQLException {
        List<BuildArtifact> artifacts = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper().executeSelect(
                "SELECT * FROM build_artifacts WHERE module_id IN (#)", moduleIds)) {
            while (rs.next()) {
                artifacts.add(resultSetToBuildArtifact(rs));
            }
        }
        return artifacts;
    }

    @NonNull
    public List<BuildArtifact> findBuildArtifactsForModule(long moduleId) throws SQLException {
        List<BuildArtifact> artifacts = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper()
                .executeSelect("SELECT * FROM build_artifacts WHERE module_id = ?", moduleId)) {
            while (rs.next()) {
                artifacts.add(resultSetToBuildArtifact(rs));
            }
        }
        return artifacts;
    }

    @NonNull
    public List<BuildArtifact> findBuildArtifactsForChecksum(
            @NonNull BuildChecksumType type,
            @NonNull String checksum) throws SQLException {
        List<BuildArtifact> artifacts = new LinkedList<>();
        try (ResultSet rs = bridgeToArtifactory.getJdbcHelper().executeSelect(
                "SELECT * FROM build_artifacts WHERE " + type.name() + " = ?", checksum)) {
            while (rs.next()) {
                artifacts.add(resultSetToBuildArtifact(rs));
            }
        }
        return artifacts;
    }

    @NonNull
    private BuildArtifact resultSetToBuildArtifact(@NonNull ResultSet rs) throws SQLException {
        return new BuildArtifact(
                rs.getLong("artifact_id"),
                rs.getLong("module_id"),
                rs.getString("artifact_name"),
                rs.getString("artifact_type"),
                rs.getString("sha1"),
                rs.getString("md5"),
                null
        );
    }
}
