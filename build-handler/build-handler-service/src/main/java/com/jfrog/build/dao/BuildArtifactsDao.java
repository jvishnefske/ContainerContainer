package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.dao.util.BuildsDaoUtils;
import com.jfrog.build.entity.BuildArtifact;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.partition;
import static com.jfrog.build.dao.util.BuildQueries.BUILD_ARTIFACTS_QUERY;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getBuildRepoPath;
import static com.jfrog.build.dao.util.BuildsDaoUtils.safeGetLongOrNull;
import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.jfrog.storage.DbType.POSTGRESQL;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BuildArtifactsDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;
    private final BuildSysConfigService sysConfigService;

    public void createBuildArtifacts(@NonNull List<BuildArtifact> artifacts) throws SQLException {
        if (artifacts.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<BuildArtifact> partition : partition(artifacts, bulkSize)) {
            List<Object[]> params = mapBuildArtifactParams(partition);
            bridgeToArtifactory.getJdbcHelper().executeBulkUpdate("""
                    INSERT INTO build_artifacts (
                        artifact_id,
                        module_id,
                        artifact_name,
                        artifact_type,
                        sha1,
                        md5
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    """, params);
        }
    }

    public int deleteBuildArtifacts(List<Long> moduleIds) throws SQLException {
        return BuildsDaoUtils.executeLimitedSizedQuery(
                bridgeToArtifactory.getJdbcHelper(),
                "DELETE FROM build_artifacts WHERE module_id IN (#)",
                moduleIds,
                sysConfigService.getDbOperationsBulkSize());
    }

    public int deleteAllBuildArtifacts() throws SQLException {
        return bridgeToArtifactory.getJdbcHelper().executeUpdate("DELETE FROM build_artifacts");
    }

    @NonNull
    private List<Object[]> mapBuildArtifactParams(@NonNull List<BuildArtifact> artifacts) {
        return artifacts.stream().map(artifact -> new Object[] {
                artifact.getArtifactId(),
                artifact.getModuleId(),
                artifact.getArtifactName(),
                artifact.getArtifactType(),
                artifact.getSha1(),
                artifact.getMd5()
        }).toList();
    }

    @NonNull
    public List<BuildFileInfo> selectBuildArtifacts(
            @NonNull BuildWrapper build,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) throws SQLException {
        List<BuildFileInfo> artifacts = new ArrayList<>();
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        Set<String> artifactsSha1 = selectBuildArtifactsSha1(build.getName(), build.getNumber(), build.getBuildRepo());
        for (List<String> sha1Partition : partition(new LinkedList<>(artifactsSha1), bulkSize)) {
            List<Object> params = new ArrayList<>();
            String query = formatSelectBuildArtifactsQuery(params, sha1Partition, sourceRepos, excludedRepos);
            try (ResultSet rs = getJdbcHelper().executeSelect(query, params.toArray())) {
                while (rs.next()) {
                    getBuildRepoPath(rs).ifPresent(buildRepoPath -> {
                        BuildFileInfo fileInfo = buildArtifactFileInfo(rs, buildRepoPath);
                        artifacts.add(fileInfo);
                    });
                }
            }
        }
        return artifacts;
    }

    @NonNull
    private String formatSelectBuildArtifactsQuery(
            @NonNull List<Object> params,
            @NonNull List<String> artifactSha1,
            @Nullable List<String> includedRepos,
            @Nullable List<String> excludedRepos) {
        String distinctOnClause = "";
        String orderByColumns = "updated";
        if (getDatabaseType() == POSTGRESQL) {
            distinctOnClause = "ON (sha1_actual, repo, node_name)";
            orderByColumns =  "sha1_actual, repo, node_name, updated";
        }
        String includeRepoFilter = "";
        String excludeRepoFilter = "";
        if (isNotEmpty(includedRepos)) {
            includeRepoFilter = "repo IN (#) AND ";
            params.add(includedRepos);
        }
        if (isNotEmpty(excludedRepos)) {
            excludeRepoFilter = "repo NOT IN (#) AND ";
            params.add(excludedRepos);
        }
        params.add(artifactSha1);
        return """
                SELECT
                  DISTINCT %s
                  node_id,
                  sha1_actual,
                  sha1_original,
                  md5_actual,
                  md5_original,
                  created,
                  created_by,
                  modified,
                  modified_by,
                  updated,
                  bin_length,
                  repo,
                  node_path,
                  node_name
                FROM nodes
                WHERE %s %s sha1_actual IN (#)
                ORDER BY %s DESC
                """.formatted(distinctOnClause, includeRepoFilter, excludeRepoFilter, orderByColumns);
    }

    @NonNull
    @SneakyThrows
    private BuildFileInfo buildArtifactFileInfo(
            @NonNull ResultSet rs,
            @NonNull BuildRepoPath repoPath) {
        return BuildFileInfo.builder()
                .repoPath(repoPath)
                .nodeId(safeGetLongOrNull(rs, "node_id"))
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
                .build();
    }

    @NonNull
    private Set<String> selectBuildArtifactsSha1(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) throws SQLException {
        Set<String> shaValues = new HashSet<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT DISTINCT sha1
                FROM builds b
                INNER JOIN build_modules bm ON bm.build_id = b.build_id
                INNER JOIN build_artifacts ba ON ba.module_id = bm.module_id
                WHERE b.repo = ?
                  AND b.build_name = ?
                  AND b.build_number = ?
                """, buildRepo, buildName, buildNumber)) {
            while (rs.next()) {
                shaValues.add(rs.getString("sha1"));
            }
        }
        return shaValues;
    }

    @NonNull
    private Set<String> selectBuildArtifactsSha1(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo,
            long buildStarted) throws SQLException {
        Set<String> shaValues = new HashSet<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT DISTINCT sha1
                FROM builds b
                INNER JOIN build_modules bm ON bm.build_id = b.build_id
                INNER JOIN build_artifacts ba ON ba.module_id = bm.module_id
                WHERE b.repo = ?
                  AND b.build_name = ?
                  AND b.build_number = ?
                  AND b.build_date = ?
                """, buildRepo, buildName, buildNumber, buildStarted)) {
            while (rs.next()) {
                shaValues.add(rs.getString("sha1"));
            }
        }
        return shaValues;
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo,
            long publishedEpoch) throws SQLException {
        if (sysConfigService.isBuildArtifactsQueryByBuildDateEnabled()) {
            return getBuildArtifactsByBuildDate(buildName, buildNumber, buildRepo, publishedEpoch);
        }
        return getBuildArtifactsByProperty(buildName, buildNumber, buildRepo, publishedEpoch);
    }

    @NonNull
    private List<BuildNodeRecord> getBuildArtifactsByBuildDate(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo,
            long publishedEpoch) throws SQLException {
        log.debug("Getting build artifacts by querying build_date for build {}:{}:{}:{}",
                buildName, buildNumber, buildRepo, publishedEpoch);

        Set<String> shaValues = selectBuildArtifactsSha1(buildName, buildNumber, buildRepo, publishedEpoch);
        if (shaValues.isEmpty()) {
            log.debug("No artifacts found in build_artifacts table for build {}:{}:{}:{}",
                    buildName, buildNumber, buildRepo, publishedEpoch);
            return new ArrayList<>();
        }

        List<BuildNodeRecord> buildNodes =
                buildNodesFromResultSetWithEpoch(buildName, buildNumber, publishedEpoch, shaValues);
        if (!buildNodes.isEmpty()) {
            log.debug("Found {} artifacts using build.timestamp property", buildNodes.size());
            return buildNodes;
        }

        // Fallback: If no results with property, query by SHA only (less precise but works)
        log.debug("No artifacts found with build.timestamp property, falling back to SHA-only query");
        return buildNodesFromShaValues(buildName, buildNumber, shaValues);
    }

    @NonNull
    private List<BuildNodeRecord> getBuildArtifactsByProperty(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo,
            long publishedEpoch) throws SQLException {
        log.debug("Getting build artifacts by querying build.timestamp property for build {}:{}:{}:{}",
                buildName, buildNumber, buildRepo, publishedEpoch);
        Set<String> shaValues = selectBuildArtifactsSha1(buildName, buildNumber, buildRepo);
        List<BuildNodeRecord> artifacts = buildNodesFromResultSetWithEpoch(buildName, buildNumber, publishedEpoch, shaValues);
        log.debug("Found {} artifacts for build {}:{}:{}:{}",
                artifacts.size(), buildName, buildNumber, buildRepo, publishedEpoch);
        return artifacts;
    }

    @NonNull
    private List<BuildNodeRecord> buildNodesFromResultSetWithEpoch(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long publishedEpoch,
            @NonNull Set<String> shaValues) throws SQLException {
        List<BuildNodeRecord> artifacts = new ArrayList<>();
        try (ResultSet rs = getArtifactNodes(buildName, buildNumber, publishedEpoch)) {
            while (rs.next()) {
                String sha1Value = rs.getString("sha1");
                if (shaValues.contains(sha1Value)) {
                    artifacts.add(buildNodeFromResultSet(rs));
                }
            }
        }
        return artifacts;
    }

    /**
     * get module artifact info with publish epoch
     */
    @NonNull
    private ResultSet getArtifactNodes(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long publishedEpoch) throws SQLException {
        final String publishPart = """
                (exists(select 1
                            from node_props npsub
                            where npsub.node_id = n.node_id
                              and npsub.prop_key = 'build.timestamp'
                              and npsub.prop_value = ?))
                        and
                """;
        String published = publishedEpoch.toString();
        String query = format(BUILD_ARTIFACTS_QUERY, publishPart);
        return getJdbcHelper().executeSelect(query, buildName, buildNumber, published);
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) throws SQLException {
        Set<String> shaValues = selectBuildArtifactsSha1(buildName, buildNumber, buildRepo);
        return buildNodesFromShaValues(buildName, buildNumber, shaValues);
    }

    @NonNull
    private List<BuildNodeRecord> buildNodesFromShaValues(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Set<String> shaValues) throws SQLException {
        List<BuildNodeRecord> artifacts = new ArrayList<>();
        try (ResultSet rs = getArtifactNodes(buildName, buildNumber)) {
            while (rs.next()) {
                String sha1Value = rs.getString("sha1");
                if (shaValues.contains(sha1Value)) {
                    artifacts.add(buildNodeFromResultSet(rs));
                }
            }
        }
        return artifacts;
    }

    @NonNull
    private BuildNodeRecord buildNodeFromResultSet(
            @NonNull ResultSet rs) throws SQLException {
        return new BuildNodeRecord(
                rs.getString("repo"),
                rs.getString("path"),
                rs.getString("name"),
                rs.getString("sha1"),
                rs.getString("md5"),
                rs.getLong("bin_length")
        );
    }

    @NonNull
    private ResultSet getArtifactNodes(
            @NonNull String buildName,
            @NonNull String buildNumber) throws SQLException {
        return getJdbcHelper().executeSelect("""
                    SELECT
                        n.repo as repo,
                        n.node_path as path,
                        n.node_name as name,
                        n.sha1_actual as sha1,
                        n.md5_actual as md5,
                        n.bin_length as bin_length
                    FROM nodes n
                    WHERE exists (
                        SELECT 1
                        FROM node_props npsub
                        WHERE npsub.node_id = n.node_id
                          AND npsub.prop_key = 'build.name'
                          AND npsub.prop_value = ?
                    ) AND exists (
                        SELECT 1
                        FROM node_props npsub
                        WHERE npsub.node_id = n.node_id
                          AND npsub.prop_key = 'build.number'
                          AND npsub.prop_value = ?
                    ) AND n.node_type = 1
                    AND n.repo <> 'auto-trashcan'
                    """, buildName, buildNumber);
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
