package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.dao.util.BuildsDaoUtils;
import com.jfrog.build.entity.BuildDependency;
import com.jfrog.build.service.BuildSysConfigService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.partition;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getBuildRepoPath;
import static com.jfrog.build.dao.util.BuildsDaoUtils.safeGetLongOrNull;
import static org.jfrog.storage.DbType.POSTGRESQL;

@Repository
@AllArgsConstructor
public class BuildDependenciesDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;
    private final BuildSysConfigService sysConfigService;

    public void createBuildDependencies(@NonNull List<BuildDependency> dependencies) throws SQLException {
        if (dependencies.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<BuildDependency> partition : partition(dependencies, bulkSize)) {
            List<Object[]> params = mapBuildDependencyParams(partition);
            bridgeToArtifactory.getJdbcHelper().executeBulkUpdate("""
                    INSERT INTO build_dependencies (
                        dependency_id,
                        module_id,
                        dependency_name_id,
                        dependency_scopes,
                        dependency_type,
                        sha1,
                        md5
                    ) VALUES(?, ?, ?, ?, ?, ?, ?)
                    """, params);
        }
    }

    public int deleteBuildDependencies(List<Long> moduleIds) throws SQLException {
        return BuildsDaoUtils.executeLimitedSizedQuery(
                bridgeToArtifactory.getJdbcHelper(),
                "DELETE FROM build_dependencies WHERE module_id IN (#)",
                moduleIds,
                sysConfigService.getDbOperationsBulkSize());
    }

    public int deleteAllBuildDependencies() throws SQLException {
        return bridgeToArtifactory.getJdbcHelper().executeUpdate("DELETE FROM build_dependencies");
    }

    @NonNull
    private List<Object[]> mapBuildDependencyParams(@NonNull List<BuildDependency> dependencies) {
        return dependencies.stream().map(dependency -> new Object[] {
                dependency.getDependencyId(),
                dependency.getModuleId(),
                dependency.getDependencyNameId(),
                dependency.getDependencyScopes(),
                dependency.getDependencyType(),
                dependency.getSha1(),
                dependency.getMd5()
        }).toList();
    }

    @NonNull
    public List<BuildFileInfo> selectBuildDependencies(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) throws SQLException {
        String distinctOnClause = "";
        String orderByColumns = "n.updated";
        if (getDatabaseType() == POSTGRESQL) {
            distinctOnClause = "ON (n.sha1_actual, n.repo, n.node_name)";
            orderByColumns = "n.sha1_actual, n.repo, n.node_name, n.updated";
        }
        List<BuildFileInfo> dependencies = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT DISTINCT %s
                    n.node_id,
                    n.sha256,
                    n.sha1_actual,
                    n.sha1_original,
                    n.md5_actual,
                    n.md5_original,
                    n.created,
                    n.created_by,
                    n.modified,
                    n.modified_by,
                    n.updated,
                    n.bin_length,
                    n.repo,
                    n.node_path,
                    n.node_name,
                    bd.dependency_name_id
                FROM builds b
                LEFT OUTER JOIN build_modules bm ON bm.build_id = b.build_id
                LEFT OUTER JOIN build_dependencies bd ON bd.module_id = bm.module_id
                LEFT OUTER JOIN nodes n ON n.sha1_actual = bd.sha1
                WHERE b.build_name = ?
                  AND b.build_number = ?
                  AND b.repo = ?
                ORDER BY %s ASC
                """.formatted(distinctOnClause, orderByColumns),
                buildName, buildNumber, buildRepo)) {
            while (rs.next()) {
                getBuildRepoPath(rs).ifPresent(buildRepoPath ->
                        dependencies.add(buildDependencyFileInfo(rs, buildRepoPath)));
            }
        }
        return dependencies;
    }

    @NonNull
    @SneakyThrows
    private BuildFileInfo buildDependencyFileInfo(
            @NonNull ResultSet rs,
            @NonNull BuildRepoPath repoPath) {
        return BuildFileInfo.builder()
                .repoPath(repoPath)
                .nodeId(safeGetLongOrNull(rs, "node_id"))
                .sha2(rs.getString("sha256"))
                .actualSha1(rs.getString("sha1_actual"))
                .originalSha1(rs.getString("sha1_original"))
                .actualMd5(rs.getString("md5_actual"))
                .originalMd5(rs.getString("md5_original"))
                .created(rs.getLong("created"))
                .createdBy(rs.getString("created_by"))
                .modified(rs.getLong("modified"))
                .modifiedBy(rs.getString("modified_by"))
                .updated(rs.getLong("updated"))
                .size(rs.getLong("bin_length"))
                .buildDependencyName(rs.getString("dependency_name_id"))
                .build();
    }

    @NonNull
    private JdbcHelper getJdbcHelper() {
        return bridgeToArtifactory.getJdbcHelper();
    }

    @NonNull
    private DbType getDatabaseType() {
        return bridgeToArtifactory.getDatabaseType();
    }
}
