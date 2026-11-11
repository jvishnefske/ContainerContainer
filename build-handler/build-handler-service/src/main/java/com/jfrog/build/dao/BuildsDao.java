package com.jfrog.build.dao;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildChecksumType;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.model.BuildNumberBundleRecord;
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
import com.jfrog.build.dao.util.BuildQueries;
import com.jfrog.build.dao.util.BuildsDaoUtils;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildEntityRecord;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.entity.BuildPromotionStatus;
import com.jfrog.build.entity.BuildProperty;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.google.common.collect.Lists.partition;
import static com.jfrog.build.acl.util.BuildConstants.TRASH_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.dao.util.BuildQueries.GET_ARTIFACT_NODES;
import static com.jfrog.build.dao.util.BuildQueries.GET_ARTIFACT_NODES_BY_BUILD_TIMESTAMP;
import static com.jfrog.build.dao.util.BuildQueries.SEARCH_BUILD;
import static com.jfrog.build.dao.util.BuildsDaoUtils.createSqlBuilder;
import static com.jfrog.build.dao.util.BuildsDaoUtils.createWhereClauseFromContinueBuildFilter;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getBuildNodePath;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getDbIndexedValueMaxSize;
import static com.jfrog.build.dao.util.BuildsDaoUtils.nullIfZero;
import static com.jfrog.build.dao.util.BuildsDaoUtils.preparePropValue;
import static java.lang.Long.parseLong;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;
import static org.jfrog.storage.DbType.POSTGRESQL;

@Slf4j
@Repository
@AllArgsConstructor
public class BuildsDao {

    public static final String BUILDS_TABLE = "builds";
    private static final String BUILD_TYPE_NAME = "build";
    private static final String BUILD_REPO_FIELD = "repo";
    private static final String BUILD_CI_URL_FIELD = "ci_url";
    private static final String BUILD_ID_FIELD = "build_id";
    private static final String BUILD_NAME_FIELD = "build_name";
    private static final String BUILD_NUMBER_FIELD = "build_number";
    private static final String BUILD_DATE_FIELD = "build_date";
    private static final String BUILD_CREATED_FIELD = "created";
    private static final String BUILD_CREATED_BY_FIELD = "created_by";
    private static final String BUILD_MODIFIED_FIELD = "modified";
    private static final String BUILD_MODIFIED_BY_FIELD = "modified_by";
    private static final String BUILD_IMMUTABLE_FIELD = "immutable";
    private static final String BUILD_PROMOTION_STATUS_FIELD = "status";
    private static final String BUNDLE_REPO_FIELD = "bundle_repository";
    private static final String BUNDLE_NAME_FIELD = "bundle_name";
    private static final String BUNDLE_VERSION_FIELD = "bundle_version";

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildSysConfigService sysConfigService;

    public void createBuild(@NonNull BuildEntity build) throws SQLException {
        insertBuild(build);
        createProperties(build.getProperties().asList());
        createPromotions(build.getPromotions().asList());
    }

    public void insertBuild(@NonNull BuildEntity build) throws SQLException {
        getJdbcHelper().executeUpdate("""
                        INSERT INTO builds (
                            build_id,
                            build_name,
                            build_number,
                            build_date,
                            ci_url,
                            created,
                            created_by,
                            modified,
                            modified_by,
                            repo
                        ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                build.getBuildId(),
                build.getBuildName(),
                build.getBuildNumber(),
                build.getBuildDate(),
                build.getCiUrl(),
                build.getCreated(),
                build.getCreatedBy(),
                nullIfZero(build.getModified()),
                build.getModifiedBy(),
                build.getRepo()
        );
    }

    private void createProperties(@NonNull List<BuildProperty> buildProperties) throws SQLException {
        if (buildProperties.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<BuildProperty> partition : partition(buildProperties, bulkSize)) {
            List<Object[]> params = mapBuildPropertyParams(partition);
            getJdbcHelper().executeBulkUpdate("""
                    INSERT INTO build_props (
                        prop_id,
                        build_id,
                        prop_key,
                        prop_value
                    ) VALUES (?, ?, ?, ?)""", params);
        }
    }

    private void createPromotions(@NonNull List<BuildPromotionStatus> buildPromotions) throws SQLException {
        if (buildPromotions.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<BuildPromotionStatus> partition : partition(buildPromotions, bulkSize)) {
            List<Object[]> params = mapBuildPromotionParams(partition);
            getJdbcHelper().executeBulkUpdate("""
                    INSERT INTO build_promotions (
                        build_id,
                        created,
                        created_by,
                        status,
                        repo,
                        promotion_comment,
                        ci_user
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, params);
        }
    }

    public void addPromotionStatus(
            @NonNull Long buildId,
            @NonNull BuildPromotionStatus promotionStatus,
            @NonNull String currentUser,
            @NonNull Long currentTime) throws SQLException {
        getJdbcHelper().executeUpdate("""
                        UPDATE builds SET
                            modified = ?,
                            modified_by = ?
                        WHERE build_id = ?
                        """,
                currentTime,
                currentUser,
                buildId);
        getJdbcHelper().executeUpdate("""
                        INSERT INTO build_promotions (
                            build_id,
                            created,
                            created_by,
                            status,
                            repo,
                            promotion_comment,
                            ci_user
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                promotionStatus.getBuildId(),
                promotionStatus.getCreated(),
                promotionStatus.getCreatedBy(),
                promotionStatus.getStatus(),
                promotionStatus.getRepository(),
                promotionStatus.getComment(),
                promotionStatus.getCiUser());
    }

    public int deleteAllBuilds() throws SQLException {
        int res = getJdbcHelper().executeUpdate("DELETE FROM build_props");
        res += getJdbcHelper().executeUpdate("DELETE FROM build_promotions");
        res += getJdbcHelper().executeUpdate("DELETE FROM builds");
        return res;
    }

    public int deleteBuild(long buildId) throws SQLException {
        int res = getJdbcHelper().executeUpdate("DELETE FROM build_props WHERE build_id=?", buildId);
        res += getJdbcHelper().executeUpdate("DELETE FROM build_promotions WHERE build_id=?", buildId);
        res += getJdbcHelper().executeUpdate("DELETE FROM builds WHERE build_id=?", buildId);
        return res;
    }

    @Nullable
    public BuildEntity getBuild(long buildId) throws SQLException {
        BuildEntityRecord build = getBuildEntityRecord(buildId);
        if (build == null) {
            return null;
        }
        return new BuildEntity(build,
                findBuildProperties(build.getBuildId()),
                findBuildPromotions(build.getBuildId()));
    }

    /**
     * return only the  build record without properties which aren't set yet.
     */
    @Nullable
    private BuildEntityRecord getBuildEntityRecord(long buildId) throws SQLException {
        BuildEntity build = null;
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT * FROM builds WHERE build_id = ?", buildId)) {
            if (rs.next()) {
                build = resultSetToBuild(rs);
            }
        }
        return build;
    }

    /**
     * returns a list of previous builds
     */
    @NonNull
    public List<GeneralBuild> getPrevBuildsList(
            @NonNull String buildName,
            @NonNull String buildDate,
            @NonNull String buildRepo) {
        String buildsQuery = "SELECT builds.*, build_promotions.status " +
                "FROM builds " +
                "LEFT JOIN build_promotions on build_promotions.build_id = builds.build_id  " +
                "WHERE builds.build_name = ? and builds.repo = ? and builds.build_date < ? " +
                "ORDER BY build_number DESC";

        List<GeneralBuild> buildList = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect(buildsQuery, buildName, buildRepo, parseLong(buildDate))) {
            while (rs.next()) {
                GeneralBuild buildEntity = resultSetToGeneralBuild(rs, rs.getLong(1));
                buildEntity.setStatus(rs.getString(10));
                buildList.add(buildEntity);
            }
        } catch (Exception e) {
            log.error("Failed to get previous builds list, {}", e.getMessage());
            log.debug("Failed to get previous builds list", e);
        }
        return buildList;
    }

    @NonNull
    public List<GeneralBuild> getBuildForName(
            @NonNull String buildName,
            @NonNull String buildRepo,
            @NonNull ContinueBuildFilter continueBuildFilter) throws SQLException {
        List<GeneralBuild> buildList;
        if (continueBuildFilter.getLimit() <= 0) {
            return Collections.emptyList();
        }
        List<Object> params = new ArrayList<>();
        QueryWriter buildsQuery = new QueryWriter(getDatabaseType());
        buildsQuery.select("builds.* , \n" +
                        "         '0' as module_cnt ,\n" +
                        "         '0' as artifact_cnt,\n" +
                        "         '0' as dependency_cnt ")
                .from("builds ")
                .orderBy("build_date DESC, build_number DESC")
                .where("build_name = ? AND repo = ?")
                .limit(continueBuildFilter.getLimit());
        params.add(buildName);
        params.add(buildRepo);
        BuildId continueBuild = continueBuildFilter.getContinueBuildId();
        if (continueBuild != null) {
            String whereAddition = " AND (build_date < ? OR (build_date = ? AND build_number < ? )) ";
            buildsQuery.where("build_name = ? AND repo = ? " + whereAddition);

            params.add(continueBuild.getStartedDate().getTime());
            params.add(continueBuild.getStartedDate().getTime());
            params.add(continueBuild.getNumber());
        }
        try (ResultSet rs = getJdbcHelper().executeSelect(buildsQuery.build(), params.toArray())) {
            buildList = resultSetToGeneralBuildList(rs);
        }
        return of(buildList).orElse(Collections.emptyList());
    }

    @NonNull
    private List<GeneralBuild> resultSetToGeneralBuildList(@NonNull ResultSet rs) throws SQLException {
        List<GeneralBuild> answer = new ArrayList<>();
        while (rs.next()) {
            Long id = rs.getLong(BUILD_ID_FIELD);
            GeneralBuild buildEntity = resultSetToGeneralBuild(rs, id);
            buildEntity.setNumOfModules(Integer.toString(rs.getInt("module_cnt")));
            buildEntity.setNumOfArtifacts(Integer.toString(rs.getInt("artifact_cnt")));
            buildEntity.setNumOfDependencies(Integer.toString(rs.getInt("dependency_cnt")));
            answer.add(buildEntity);
        }
        return answer;
    }

    /**
     * get Module Artifact diff with paging
     */
    @NonNull
    public List<ModuleArtifact> getModuleArtifactsForDiffWithPaging(
            @NonNull BuildParams buildParams,
            boolean onlyDefaultBuildRepoExists) {
        List<ModuleArtifact> artifacts = new ArrayList<>();
        Object[] diffParams = getBuildDependencyParams(buildParams);
        try (ResultSet rs = getJdbcHelper().executeSelect(BuildQueries.MODULE_ARTIFACT_DIFF_QUERY, diffParams)) {
            while (rs.next()) {
                ModuleArtifact artifact =
                        new ModuleArtifact(null, null, rs.getString(1), rs.getString(2), rs.getString(3));
                artifact.setStatus(rs.getString(4));
                artifacts.add(artifact);
            }
            // update artifact repo path data
            if (!artifacts.isEmpty()) {
                final Map<String, BuildNode> artifactNodes =
                        getArtifactNodes(buildParams.getBuildName(), buildParams.getCurrBuildNum(),
                                buildParams.getCurrBuildDate(), onlyDefaultBuildRepoExists);
                for (ModuleArtifact artifact : artifacts) {
                    BuildNode artifactNode = artifactNodes.get(artifact.getSha1());
                    if (nonNull(artifactNode)) {
                        artifact.setRepoKey(artifactNode.getRepo());
                        artifact.setPath(getBuildNodePath(artifactNode));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get module artifacts with paging, {}", e.getMessage());
            log.debug("Failed to get module artifacts with paging", e);
        }
        return artifacts;
    }

    /**
     * get BuildProps through distinct filter.
     */
    @NonNull
    public List<BuildProps> getBuildPropsList(@NonNull BuildParams buildParams) {
        String buildPropsQuery;
        if (buildParams.isEnvProps()) {
            buildPropsQuery = BuildQueries.BUILD_ENV_PROPS_BY_BUILD_ID;
        } else {
            buildPropsQuery = BuildQueries.BUILD_SYSTEM_PROPS_BY_BUILD_ID;
        }
        Long buildId = getBuildId(buildParams);
        return getBuildPropsList(buildPropsQuery, buildId);
    }

    /**
     * get build props - distinct programmatically.
     * would be slow on large list but several thousands should be fine.
     */
    @NonNull
    public List<BuildProps> getBuildPropsList(
            @NonNull String buildPropsQuery,
            @Nullable Long buildId) {
        List<BuildProps> buildPropsList = new ArrayList<>();
        if (buildId == null) {
            return buildPropsList;
        }
        try (ResultSet rs = getJdbcHelper().executeSelect(buildPropsQuery, buildId)) {
            while (rs.next()) {
                buildPropsList.add(resultSetToBuildProps(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get build properties, {}", e.getMessage());
            log.debug("Failed to get build properties", e);
        }
        return buildPropsList;
    }

    @Nullable
    public Long getBuildId(@NonNull BuildParams buildParams) {
        Object[] diffParams = getBuildPropsParam(buildParams);
        try (ResultSet rs = getJdbcHelper().executeSelect(BuildQueries.BUILD_BY_NAME_NUMBER_DATE, diffParams)) {
            if (rs.next()) {
                Long res = rs.getLong(1);
                if (rs.next()) {
                    log.error("More then one build by number: {}  date: {}", diffParams[0], diffParams[1]);
                }
                return res;
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve buildId, {}", e.getMessage());
            log.debug("Failed to retrieve buildId", e);
        }
        return null;
    }

    /**
     * get build props (env or system) param to be included in sql query.
     * The {@code repo} is included so the build is resolved within the same repository that the caller was
     * authorized against; without it a reader of any repo could read another repo's build properties
     * (name/number/date are not unique across repos). See RTDEV-94069.
     */
    @NonNull
    private Object[] getBuildPropsParam(@NonNull BuildParams buildParams) {
        return new Object[] {buildParams.getBuildName(), buildParams.getCurrBuildNum(),
                buildParams.getCurrBuildDate(), buildParams.getBuildRepo()};
    }

    /**
     * get Module Artifact diff with paging
     */
    @NonNull
    public List<ModuleDependency> getModuleDependencyForDiffWithPaging(@NonNull BuildParams buildParams) {
        List<ModuleDependency> dependencies = new ArrayList<>();
        Object[] diffParams = getBuildDependencyParams(buildParams);
        try (ResultSet rs = getJdbcHelper().executeSelect(BuildQueries.MODULE_DEPENDENCY_DIFF_QUERY, diffParams)) {
            Map<String, String> tempDependencyMap = new HashMap<>();
            while (rs.next()) {
                String sha1 = rs.getString(3);
                if (tempDependencyMap.get(sha1) == null) {
                    tempDependencyMap.put(sha1, sha1);
                    ModuleDependency dependency = new ModuleDependency(null, null, rs.getString(1),
                            rs.getString(2), rs.getString(4), sha1);
                    dependency.setStatus(rs.getString(5));
                    dependencies.add(dependency);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get module dependencies with paging, {}", e.getMessage());
            log.debug("Failed to get module dependencies with paging", e);
        }
        return dependencies;
    }

    /**
     * get build dependency query params for all build dependency diff or build module dependency diff
     *
     * @param buildParams - build diff param
     * @return - build dependency param for diff query
     */
    @NonNull
    private Object[] getBuildDependencyParams(@NonNull BuildParams buildParams) {
        return new Object[] {
                buildParams.getCurrBuildNum(), buildParams.getCurrBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getComperedBuildNum(), buildParams.getComperedBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getComperedBuildNum(), buildParams.getComperedBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getCurrBuildNum(), buildParams.getCurrBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getCurrBuildNum(), buildParams.getCurrBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getComperedBuildNum(), buildParams.getComperedBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getCurrBuildNum(), buildParams.getCurrBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId(), /// ===
                buildParams.getComperedBuildNum(), buildParams.getComperedBuildDate(), buildParams.getBuildRepo(),
                buildParams.getBuildModuleId()}; /// ===
    }

    public long findBuildId(
            @NonNull String name,
            @NonNull String number,
            long startDate) throws SQLException {
        try (ResultSet rs = getJdbcHelper().executeSelect(
                "SELECT build_id FROM builds WHERE build_name = ? AND build_number = ? AND build_date = ?", name,
                number, startDate)) {
            if (rs.next()) {
                return rs.getLong(BUILD_ID_FIELD);
            }
        }
        return 0L;
    }

    @Nullable
    public BuildEntity findBuild(
            @NonNull String name,
            @NonNull String number,
            long startDate) throws SQLException {
        long buildId = findBuildId(name, number, startDate);
        if (buildId > 0L) {
            return getBuild(buildId);
        }
        return null;
    }

    public boolean isImmutable(
            @NonNull String buildName,
            @NonNull String buildNumber,
            long startDate) throws SQLException {
        try (ResultSet rs = getJdbcHelper().executeSelect(
                "SELECT immutable FROM builds WHERE build_name = ? AND build_number = ? AND build_date = ?", buildName,
                buildNumber, startDate)) {
            if (rs.next()) {
                return rs.getInt(BUILD_IMMUTABLE_FIELD) == 1;
            }
        }
        throw notFoundException("Not found build, name: %s, number: %s, started: %d",
                buildName, buildNumber, startDate);
    }

    @Nullable
    public BuildEntity getLatestBuild(
            @NonNull String buildName,
            @NonNull String buildRepo) throws SQLException {
        long latestBuildDate = 0L;
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT max(build_date) FROM builds WHERE build_name = ? " +
                "AND repo = ?", buildName, buildRepo)) {
            if (rs.next()) {
                latestBuildDate = rs.getLong(1);
            }
        }

        BuildEntity buildEntity = null;
        if (latestBuildDate > 0L) {
            try (ResultSet rs = getJdbcHelper().executeSelect("SELECT * FROM builds " +
                    "WHERE build_name = ? AND build_date = ? AND repo = ?", buildName, latestBuildDate, buildRepo)) {
                if (rs.next()) {
                    buildEntity = resultSetToBuild(rs);
                }
            }
        }
        if (buildEntity != null) {
            buildEntity.setProperties(findBuildProperties(buildEntity.getBuildId()));
            buildEntity.setPromotions(findBuildPromotions(buildEntity.getBuildId()));
        }
        return buildEntity;
    }

    @Nullable
    public BuildRun findLatestBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String repoKey) throws SQLException {
        long latestBuildDate = 0L;
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT max(build_date) FROM builds WHERE" +
                        " build_name = ? AND build_number = ? AND repo = ?",
                buildName, buildNumber, repoKey)) {
            if (rs.next()) {
                latestBuildDate = rs.getLong(1);
            }
        }
        if (latestBuildDate > 0L) {
            return new BuildRunImpl(buildName, buildNumber, formatBuildTime(latestBuildDate), repoKey);
        }
        return null;
    }

    @NonNull
    public List<Long> findBuildIds(
            @Nullable String buildName,
            @Nullable String buildRepo) throws SQLException {
        List<Long> buildIds = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT build_id FROM builds WHERE" +
                " build_name = ? AND repo = ? ORDER BY build_date DESC", buildName, buildRepo)) {
            while (rs.next()) {
                buildIds.add(rs.getLong(1));
            }
        }
        return buildIds;
    }

    @NonNull
    public List<Long> findBuildIds(@Nullable String buildName) throws SQLException {
        List<Long> buildIds = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT build_id FROM builds WHERE" +
                " build_name = ? ORDER BY build_date DESC", buildName)) {
            while (rs.next()) {
                buildIds.add(rs.getLong(1));
            }
        }
        return buildIds;
    }

    @NonNull
    public List<Long> findBuildIds(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) throws SQLException {
        List<Long> buildIds = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT build_id FROM builds WHERE" +
                        " build_name = ? AND build_number = ? AND repo = ? ORDER BY build_date DESC",
                buildName, buildNumber, buildRepo)) {
            while (rs.next()) {
                buildIds.add(rs.getLong(1));
            }
        }
        return buildIds;
    }

    public boolean existsBuildByName(@NonNull String buildName, @NonNull String buildRepo) throws SQLException {
        return getJdbcHelper().executeSelectCount(
                "SELECT COUNT(build_id) FROM builds WHERE build_name = ? AND repo = ?", buildName, buildRepo) > 0;
    }

    /**
     * Resolves the requested build numbers of a build name in a single batched {@code IN} query per bulk, constructing
     * the {@link BuildRun}s directly from the {@code builds} row.
     */
    @NonNull
    public List<BuildRun> findBuildsByNameAndNumbers(
            @NonNull String buildName,
            @NonNull Collection<String> buildNumbers,
            @NonNull String buildRepo) throws SQLException {
        if (buildNumbers.isEmpty()) {
            return List.of();
        }
        List<BuildRun> builds = new ArrayList<>();
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<String> numbersBulk : partition(new ArrayList<>(buildNumbers), bulkSize)) {
            try (ResultSet rs = getJdbcHelper().executeSelect(
                    "SELECT build_name, build_number, build_date, repo FROM builds "
                            + "WHERE build_name = ? AND repo = ? AND build_number IN (#) ORDER BY build_date DESC",
                    buildName, buildRepo, numbersBulk)) {
                while (rs.next()) {
                    builds.add(new BuildRunImpl(
                            rs.getString(BUILD_NAME_FIELD),
                            rs.getString(BUILD_NUMBER_FIELD),
                            formatBuildTime(rs.getLong(BUILD_DATE_FIELD)),
                            rs.getString(BUILD_REPO_FIELD)));
                }
            }
        }
        return builds;
    }

    @NonNull
    public List<BuildNameAndRepo> getAllBuildNames(@Nullable String buildRepo) throws SQLException {
        ResultSet rs = null;
        List<BuildNameAndRepo> buildNames = new ArrayList<>();
        try {
            if (isNotBlank(buildRepo)) {
                rs = getJdbcHelper().executeSelect(
                        "SELECT build_name, repo, max(build_date) d FROM builds WHERE repo = ? GROUP BY build_name, " +
                                "repo ORDER BY d", buildRepo);
            } else {
                rs = getJdbcHelper().executeSelect(
                        "SELECT build_name, repo, max(build_date) d FROM builds GROUP BY build_name, repo ORDER BY d");
            }
            while (rs.next()) {
                buildNames.add(new BuildNameAndRepo(rs.getString(BUILD_NAME_FIELD), rs.getString("repo")));
            }
        } finally {
            DbUtils.close(rs);
        }
        return buildNames;
    }

    @NonNull
    public List<BuildNameAndRepo> getBuildNames(
            @NonNull List<String> buildRepos,
            @Nullable String buildName,
            long offset,
            long limit) throws SQLException {
        List<BuildNameAndRepo> namesAndRepos = new ArrayList<>();
        List<Object> params = new LinkedList<>();
        String query = "SELECT DISTINCT build_name, repo FROM builds";
        String where = createWhereClause(buildRepos, buildName, params).orElse("");
        query += where;
        query = createSqlBuilder(getDatabaseType()).build(query, "repo, build_name ASC", offset, limit);
        try (ResultSet rs = getJdbcHelper().executeSelect(query, params.toArray())) {
            while (rs.next()) {
                namesAndRepos.add(
                        new BuildNameAndRepo(
                                rs.getString(BUILD_NAME_FIELD),
                                rs.getString(BUILD_REPO_FIELD)));
            }
        }
        return namesAndRepos;
    }

    @NonNull
    @Transactional(readOnly = true)
    public List<BuildNumberRecord> getBuildNumbers(
            @NonNull String repoKey,
            @NonNull String name,
            long startedBefore,
            boolean excludeInReleaseBundles,
            long startedAfter,
            long limit,
            long bundlesLimit) throws SQLException {
        final String query = getBuildNumbersQuery(excludeInReleaseBundles);
        log.debug("Getting build numbers for repoKey={}, name={}, startedBefore={}, excludeInReleaseBundles={}, " +
                        "startedAfter={}, limit={}, bundleLimit={}",
                repoKey, name, startedBefore, excludeInReleaseBundles, startedAfter, limit, bundlesLimit);
        List<BuildNumberRecord> buildNumbers = new ArrayList<>();
        boolean lastQueryHadResults;
        NumberStarted prev = null;
        long offset = 0; // used for internal pagination, while reducing the JOIN to numbers with their bundles
        do {
            String execQuery = createQueryForExecution(query, limit, offset);
            try (ResultSet rs = getJdbcHelper().executeSelect(execQuery, repoKey, name, startedBefore, startedAfter)) {
                lastQueryHadResults = false;
                log.debug("Attempt to start a new internal page with offset={}, lastQueryHadResults={}, " +
                        "buildNumbers={}", offset, lastQueryHadResults, buildNumbers.size());
                while (rs.next()) {
                    offset++;
                    lastQueryHadResults = true;
                    NumbersRowResult
                            res = consumeNumbersRow(repoKey, name, offset, limit, bundlesLimit, prev, rs, buildNumbers);
                    if (res.shouldReturn) {
                        log.info("Reached the limit of {} numbers with {}; return {} numbers, last has {} bundles",
                                limit, res.curr, buildNumbers.size(), getLastBundlesSize(buildNumbers));
                        return buildNumbers;
                    }
                    prev = res.curr;
                }
            }
            // if last query we had results, and we still have room for another page, or we actually have enough -
            // but the last number had bundles, we need to continue to the next page - to pick up its bundles
        } while (lastQueryHadResults &&
                (buildNumbers.size() < limit || !getLastBundles(buildNumbers).isEmpty()));
        log.debug("Finished getting build numbers; return {} numbers", buildNumbers.size());
        return buildNumbers;
    }

    @NonNull
    public List<String> selectBuildNumbersNotInRepo(
            @NonNull String name,
            @NonNull String number,
            @NonNull String repoKey) throws SQLException {
        List<String> repoKeys = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT DISTINCT repo
                FROM builds
                WHERE build_name = ?
                  AND build_number = ?
                  AND repo != ?
                ORDER BY repo ASC
                """, name, number, repoKey)) {
            while (rs.next()) {
                repoKeys.add(rs.getString(BUILD_REPO_FIELD));
            }
        }
        return repoKeys;
    }

    public int getBuildsCountFromSpecificRepo(@NonNull String repo) throws SQLException {
        return getJdbcHelper().executeSelectCount("SELECT COUNT(*) FROM builds WHERE repo = ?", repo);
    }

    public int getUniqueBuildsCountFromSpecificRepo(@NonNull String repo) throws SQLException {
        return getJdbcHelper().executeSelectCount("SELECT COUNT(DISTINCT build_name) FROM builds WHERE repo = ?", repo);
    }

    public int getTotalBuildsCount() throws SQLException {
        return getJdbcHelper().executeSelectCount("SELECT COUNT(*) FROM builds");
    }

    @Nullable
    public String getBuildStoringRepo(
            @NonNull String name,
            @NonNull String number,
            long date) throws SQLException {
        String repo = null;
        try (ResultSet rs = getJdbcHelper().executeSelect(
                "SELECT repo FROM builds WHERE build_name = ? AND build_number = ? AND build_date = ?",
                name, number, date)) {
            if (rs.next()) {
                repo = rs.getString(1);
            }
        }
        return repo;
    }

    @NonNull
    public List<PublishedModule> getBuildModule(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String date,
            @NonNull String buildRepo) throws SQLException {
        String buildQuery = """
                SELECT build_modules.module_name_id,
                (select count(*) from build_artifacts
                 where build_artifacts.module_id = build_modules.module_id) as num_of_art,
                (select count(*) from build_dependencies
                 where build_dependencies.module_id = build_modules.module_id) as num_of_dep,
                build_modules.type
                FROM build_modules
                left join builds on builds.build_id=build_modules.build_id
                 where builds.build_name = ? AND builds.build_number=?
                and builds.build_date=? AND builds.repo = ?
                """;
        List<PublishedModule> modules = new ArrayList<>();
        try (ResultSet rs = getJdbcHelper().executeSelect(buildQuery, buildName, buildNumber, parseLong(date), buildRepo)) {
            while (rs.next()) {
                PublishedModule module = getPublishedModule(rs);
                modules.add(module);
            }
        }
        return modules;
    }

    @NonNull
    @VisibleForTesting
    PublishedModule getPublishedModule(@NonNull ResultSet rs) throws SQLException {
        PublishedModule module = new PublishedModule();
        String id = rs.getString(1);
        module.setId(id);
        module.setNumOfArtifact(rs.getString(2));
        module.setNumOfDependencies(rs.getString(3));
        String type = rs.getString(4);
        if (BUILD_TYPE_NAME.equalsIgnoreCase(type)) {
            module.setType(BUILD_TYPE_NAME);
            int index = id.lastIndexOf("/");
            String buildName = "";
            String buildNumber = "";
            if (index != -1) {
                buildName = id.substring(0, index);
                buildNumber = id.substring(index + 1);
            }
            module.setBuildId(buildName);
            module.setBuildNumber(buildNumber);
        } else {
            module.setType(type);
        }
        return module;
    }

    /**
     * Get build module artifacts with their resolved repo paths.
     * <p>
     * For each {@code (name, type, sha1)} group reported by {@link #getPaginatedArtifact}, emit one
     * {@link ModuleArtifact} per matching node, capped by {@code occurrences} -- the number of
     * {@code build_artifacts} rows the current module actually has for that group. The cap prevents the
     * multi-module same-name+SHA case from leaking sibling-module paths into this module's tab; the fan-out
     * surfaces all distinct deployment paths for artifacts that share content within a single module.
     *
     * @param onlyDefaultBuildRepoExists - true if only "artifactory-build-info" exists, false otherwise
     * @throws SQLException - in case of DB errors
     */
    @NonNull
    public List<ModuleArtifact> getModuleArtifact(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String date,
            @NonNull String moduleId,
            @NonNull String buildRepo,
            boolean onlyDefaultBuildRepoExists) throws SQLException {
        record ModuleArtifactRow(String name, String artifactType, String sha1, int occurrences) { }
        long buildDate = parseLong(date);
        List<ModuleArtifactRow> moduleArtifactRows = new ArrayList<>();
        try (ResultSet rs = getPaginatedArtifact(buildNumber, buildDate, moduleId, buildRepo)) {
            while (rs.next()) {
                moduleArtifactRows.add(new ModuleArtifactRow(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("sha1"),
                        rs.getInt("occurrences")));
            }
        }
        if (moduleArtifactRows.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<BuildNode>> artifactNodesBySha1 =
                selectArtifactNodesBySha1(buildName, buildNumber, buildDate, onlyDefaultBuildRepoExists);

        List<ModuleArtifact> result = new ArrayList<>(moduleArtifactRows.size());
        for (ModuleArtifactRow row : moduleArtifactRows) {
            List<BuildNode> matchingNodes = artifactNodesBySha1.getOrDefault(row.sha1(), List.of());
            List<BuildNode> nameMatchingNodes = matchingNodes.stream()
                    .filter(node -> row.name().equals(node.getName()))
                    .toList();

            if (nameMatchingNodes.isEmpty()) {
                // No node has the matching name. Preserve the legacy "first by SHA" fallback to keep behaviour
                // unchanged for cases where the manifest name and node name disagree (e.g. promoted-out content).
                ModuleArtifact artifact = new ModuleArtifact(null, null, row.name(), row.artifactType(), row.sha1());
                if (!matchingNodes.isEmpty()) {
                    BuildNode fallback = matchingNodes.getFirst();
                    artifact.setRepoKey(fallback.getRepo());
                    artifact.setPath(getBuildNodePath(fallback));
                }
                result.add(artifact);
                continue;
            }

            int emitCount = Math.min(row.occurrences(), nameMatchingNodes.size());
            for (int i = 0; i < emitCount; i++) {
                BuildNode node = nameMatchingNodes.get(i);
                ModuleArtifact emitted = new ModuleArtifact(null, null, row.name(), row.artifactType(), row.sha1());
                emitted.setRepoKey(node.getRepo());
                emitted.setPath(getBuildNodePath(node));
                result.add(emitted);
            }
        }
        return result;
    }

    /**
     * Get module artifact info, grouped by (name, type, sha1) with the un-collapsed row count exposed as
     * {@code occurrences}. The count is the number of {@code build_artifacts} rows the current module has for
     * this {@code (name, type, sha1)} key before grouping; it is used by {@link #getModuleArtifact} as a hard
     * cap on how many {@link ModuleArtifact} entries to emit per group, so artifacts with identical name+SHA
     * deployed at different paths fan out correctly without leaking sibling-module paths into this module's tab.
     */
    @NonNull
    private ResultSet getPaginatedArtifact(
            @NonNull String buildNumber,
            @NonNull Long date,
            @NonNull String moduleId,
            @NonNull String buildRepo) throws SQLException {
        String buildQuery = """
                SELECT build_artifacts.artifact_name AS name,
                       build_artifacts.artifact_type AS type,
                       build_artifacts.sha1          AS sha1,
                       COUNT(*)                      AS occurrences
                FROM build_artifacts
                LEFT JOIN build_modules ON build_modules.module_id = build_artifacts.module_id
                LEFT JOIN builds ON build_modules.build_id = builds.build_id
                WHERE builds.build_number = ?
                  AND builds.build_date = ?
                  AND builds.repo = ?
                  AND build_modules.module_name_id = ?
                GROUP BY build_artifacts.artifact_name,
                         build_artifacts.artifact_type,
                         build_artifacts.sha1
                """;
        return getJdbcHelper().executeSelect(buildQuery, buildNumber, date, buildRepo, moduleId);
    }

    /**
     * get Artifact nodes data by build name and number
     * Note - who ever use this method must be responsible for close the result set
     *
     * @return map of data , key = sha1 , value = module artifact
     * @throws SQLException - in case of DB errors
     */
    @NonNull
    private Map<String, BuildNode> getArtifactNodes(
            @NonNull String buildName,
            @NonNull String buildNumber,
            long buildStarted,
            boolean onlyDefaultBuildRepoExists) throws SQLException {
        // Convert the grouped map to the original format by keeping the first artifact for each SHA1
        return selectArtifactNodesBySha1(buildName, buildNumber, buildStarted, onlyDefaultBuildRepoExists)
                .entrySet().stream().collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));
    }

    @NonNull
    private Map<String, List<BuildNode>> selectArtifactNodesBySha1(
            @NonNull String buildName,
            @NonNull String buildNumber,
            long buildStarted,
            boolean onlyDefaultBuildRepoExists) throws SQLException {
        if (onlyDefaultBuildRepoExists) {
            return selectArtifactNodesBySha1(buildName, buildNumber);
        }
        Map<String, List<BuildNode>> nodesBySha1 = selectArtifactNodesBySha1(buildName, buildNumber, buildStarted);
        if (nodesBySha1.isEmpty()) {
            return selectArtifactNodesBySha1(buildName, buildNumber);
        }
        return nodesBySha1;
    }

    @NonNull
    private Map<String, List<BuildNode>> selectArtifactNodesBySha1(
            @NonNull String buildName,
            @NonNull String buildNumber,
            long buildStarted) throws SQLException {
        try (ResultSet rs = getJdbcHelper().executeSelect(GET_ARTIFACT_NODES_BY_BUILD_TIMESTAMP,
                buildName, buildNumber, TRASH_REPO_KEY, String.valueOf(buildStarted))) {
            return scanBuildNodes(rs);
        }
    }

    @NonNull
    private Map<String, List<BuildNode>> selectArtifactNodesBySha1(
            @NonNull String buildName,
            @NonNull String buildNumber) throws SQLException {
        try (ResultSet rs = getJdbcHelper().executeSelect(GET_ARTIFACT_NODES,
                buildName, buildNumber, TRASH_REPO_KEY)) {
            return scanBuildNodes(rs);
        }
    }

    @NonNull
    private Map<String, List<BuildNode>> scanBuildNodes(@NonNull ResultSet rs) throws SQLException {
        Map<String, List<BuildNode>> nodesBySha1 = new HashMap<>();
        while (rs.next()) {
            String sha1 = rs.getString("sha1_actual");
            nodesBySha1.computeIfAbsent(sha1, key -> new LinkedList<>()).add(mapBuildNode(rs));
        }
        return nodesBySha1;
    }

    @NonNull
    private static BuildNode mapBuildNode(@NonNull ResultSet rs) throws SQLException {
        return BuildNode.builder()
                .repo(rs.getString("repo"))
                .path(rs.getString("node_path"))
                .name(rs.getString("node_name"))
                .build();
    }

    @NonNull
    public List<ModuleDependency> selectModuleDependencies(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String moduleId) throws SQLException {
        List<ModuleDependency> dependencies = new ArrayList<>();
        long buildStartedMillis = parseLong(buildStarted);
        try (ResultSet rs = getJdbcHelper().executeSelect("""
                SELECT DISTINCT
                    build_dependencies.dependency_name_id AS id,
                    build_dependencies.dependency_type AS type,
                    build_dependencies.dependency_scopes AS scope,
                    build_dependencies.sha1
                FROM build_dependencies
                LEFT JOIN build_modules
                  ON build_modules.module_id = build_dependencies.module_id
                LEFT JOIN builds
                  ON build_modules.build_id = builds.build_id
                WHERE builds.build_name = ?
                  AND builds.build_number = ?
                  AND builds.build_date = ?
                  AND builds.repo = ?
                  AND build_modules.module_name_id = ?
                """, buildName, buildNumber, buildStartedMillis, buildRepo, moduleId)) {
            while (rs.next()) {
                String name = rs.getString("id");
                String type = rs.getString("type");
                String scope = rs.getString("scope");
                String sha1 = rs.getString("sha1");
                dependencies.add(new ModuleDependency(null, null, name, type, scope, sha1));
            }
        }
        return dependencies;
    }

    @NonNull
    public Map<String, List<BuildNode>> selectDependencyNodesBySha1(@NonNull List<String> sha1s) throws SQLException {
        Map<String, List<BuildNode>> nodesBySha1 = new HashMap<>();
        String distinctOnClause = "";
        if (getDatabaseType() == POSTGRESQL) {
            String distinctOnColumns = "nodes.sha1_actual, nodes.repo, nodes.node_name";
            distinctOnClause = String.format("ON (%s)", distinctOnColumns);
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        List<List<String>> sha1Bulks = Lists.partition(sha1s, bulkSize);
        for (List<String> sha1Bulk : sha1Bulks) {
            try (ResultSet rs = getJdbcHelper().executeSelect("""
                    SELECT DISTINCT %s
                        nodes.repo,
                        nodes.node_path,
                        nodes.node_name,
                        nodes.sha1_actual
                    FROM nodes
                    WHERE nodes.repo != ?
                      AND nodes.sha1_actual IN (#)
                    """.formatted(distinctOnClause), TRASH_REPO_KEY, sha1Bulk)) {
                nodesBySha1.putAll(scanBuildNodes(rs));
            }
        }
        return nodesBySha1;
    }

    @NonNull
    public List<BuildIdEntity> getLatestBuildIds(@NonNull ContinueBuildFilter continueBuildFilter) {
        ResultSet rs = null;
        List<BuildIdEntity> buildNames = new ArrayList<>();
        if (continueBuildFilter.getLimit() <= 0) {
            return buildNames;
        }
        try {
            QueryWriter queryWriter = new QueryWriter(getDatabaseType());
            queryWriter.select("d.build_id, d.build_name, d.build_number, d.build_date, d.repo")
                    .from("builds d ")
                    .innerJoin(
                            "(select c.build_name, max(c.build_date) build_time from builds c group by c.build_name, " +
                                    "c.repo ) k",
                            "k.build_name=d.build_name AND k.build_time=d.build_date")
                    .limit(continueBuildFilter.getLimit());
            String orderByStr = BuildsDaoUtils.createOrderByStr(continueBuildFilter);
            queryWriter.orderBy(orderByStr);
            List<Object> sqlParametersList = new ArrayList<>();
            String whereClause =
                    createWhereClauseFromContinueBuildFilter(continueBuildFilter, sqlParametersList,
                            "d");
            if (isNotBlank(whereClause)) {
                queryWriter.where(whereClause);
            }
            rs = getJdbcHelper().executeSelect(queryWriter.build(), sqlParametersList.toArray());
            buildNames = BuildsDaoUtils.populateBuildIdEntryList(rs);
        } catch (Exception e) {
            log.error("Failed to get latest builds ids, {}", e.getMessage());
            log.debug("Failed to get latest builds ids", e);
        } finally {
            DbUtils.close(rs);
        }
        return buildNames;
    }

    @NonNull
    public List<String> getDistinctBuildNames(@NonNull ContinueBuildFilter continueBuildFilter) {
        ResultSet rs = null;
        List<String> buildNames = new ArrayList<>();
        if (continueBuildFilter.getLimit() <= 0) {
            return buildNames;
        }
        try {
            QueryWriter queryWriter = new QueryWriter(getDatabaseType());
            queryWriter.select("DISTINCT " + BUILD_NAME_FIELD)
                    .from(BUILDS_TABLE)
                    .limit(continueBuildFilter.getLimit());
            String orderByStr = BuildsDaoUtils.createOrderByStr(continueBuildFilter);
            queryWriter.orderBy(orderByStr);
            List<Object> sqlParametersList = new ArrayList<>();
            String whereClause =
                    createWhereClauseFromContinueBuildFilter(continueBuildFilter, sqlParametersList, "");
            if (isNotBlank(whereClause)) {
                queryWriter.where(whereClause);
            }
            rs = getJdbcHelper().executeSelect(queryWriter.build(), sqlParametersList.toArray());
            while (rs.next()) {
                buildNames.add(rs.getString(BUILD_NAME_FIELD));
            }
        } catch (Exception e) {
            log.error("Failed to get distinct build names, {}", e.getMessage());
            log.debug("Failed to get distinct build names", e);
        } finally {
            DbUtils.close(rs);
        }
        return buildNames;
    }

    public int getDistinctBuildNamesCount(@NonNull ContinueBuildFilter continueBuildFilter) {
        int count = 0;
        try {
            QueryWriter queryWriter = new QueryWriter(getDatabaseType());
            queryWriter.select("DISTINCT " + BUILD_NAME_FIELD).from(BUILDS_TABLE);
            List<Object> sqlParametersList = new ArrayList<>();
            String whereClause = createWhereClauseFromContinueBuildFilter(continueBuildFilter, sqlParametersList, "");
            if (isNotBlank(whereClause)) {
                queryWriter.where(whereClause);
            }
            count = getJdbcHelper().executeSelectCount("SELECT COUNT(*) FROM ( " + queryWriter.build() + " ) a",
                    sqlParametersList.toArray());
        } catch (Exception e) {
            log.error("Failed to get count of latest builds ids, {}", e.getMessage());
            log.debug("Failed to get count of latest builds ids", e);
        }
        return count;
    }

    /**
     * Default sorting is by date desc
     */
    @NonNull
    public List<BuildIdEntity> selectLatestBuildIdsByNameCaseInsensitive(
            @NonNull BuildsSearchFilter buildsSearchFilter) throws SQLException {
        ResultSet rs = null;
        List<BuildIdEntity> builds = new ArrayList<>();
        List<Object> whereParams = new LinkedList<>();
        try {
            String whereClause = buildILikeWhereClause(whereParams, buildsSearchFilter);
            String orderByAndSort = buildOrderByAndSort(buildsSearchFilter);
            String query = buildSearchQuery(buildsSearchFilter.getBefore(), whereClause, getDatabaseType());
            query = createSqlBuilder(getDatabaseType())
                    .build(query, orderByAndSort, buildsSearchFilter.getOffset(), buildsSearchFilter.getLimit());
            log.debug("Query = '{}, whereParams = '{}''", query, whereParams);
            rs = getJdbcHelper().executeSelect(query, whereParams.toArray());
            while (rs.next()) {
                builds.add(new BuildIdEntity(rs.getLong(BUILD_ID_FIELD), rs.getString(BUILD_NAME_FIELD),
                        rs.getString(BUILD_NUMBER_FIELD), rs.getLong(BUILD_DATE_FIELD),
                        rs.getString(BUILD_REPO_FIELD)));
            }
        } finally {
            DbUtils.close(rs);
        }
        return builds;
    }

    public void populateReleaseBundleNames(
            @NonNull List<BuildId> buildIds) throws SQLException {
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        List<String> buildNames = buildIds.stream().map(BuildId::getName).toList();
        List<List<String>> buildNameBulks = Lists.partition(buildNames, bulkSize);
        log.trace("Executing DB select by {} build name bulks", buildNameBulks.size());
        for (List<String> buildNameBulk : buildNameBulks) {
            try (ResultSet resultSet = getJdbcHelper().executeSelect("""
                    SELECT A.repo, A.build_name, B.bundle_repository, B.bundle_name
                    FROM builds A
                    INNER JOIN build_release_bundles B
                    ON A.build_id = B.build_id
                    WHERE A.build_name IN (#)
                    """, buildNameBulk)) {
                while (resultSet.next()) {
                    String buildRepoKey = resultSet.getString(BUILD_REPO_FIELD);
                    String buildName = resultSet.getString(BUILD_NAME_FIELD);
                    String bundleRepoKey = resultSet.getString(BUNDLE_REPO_FIELD);
                    String bundleName = resultSet.getString(BUNDLE_NAME_FIELD);
                    buildIds.stream()
                            .filter(buildId -> buildId.getName().equals(buildName))
                            .filter(buildId -> buildId.getBuildRepo().equals(buildRepoKey))
                            .filter(BuildIdImpl.class::isInstance)
                            .map(BuildIdImpl.class::cast)
                            .findAny().ifPresent(buildId -> {
                                BuildReleaseBundle rb = new BuildReleaseBundle(bundleRepoKey, bundleName);
                                buildId.getReleaseBundles().add(rb);
                            });
                }
            }
        }
    }

    private String buildSearchQuery(long toDate, String whereClause, DbType dbType) {
        if (dbType == DbType.POSTGRESQL) {
            return buildSearchQueryPostgres(whereClause);
        }
        return buildSearchQueryNonPostgres(toDate, whereClause);
    }

    private String buildSearchQueryNonPostgres(long toDate, String whereClause) {
        if (toDate != 0) {
            return buildSearchQueryWithBeforeDate(whereClause);
        }
        return buildSearchAllBuildsQuery(whereClause);
    }

    /**
     * Build a query for getting all the builds with the max build_date and before a specific date.
     * The query returns builds that have a version before "toDate",
     * and their newest version (even if it is after "toDate")
     */
    @NonNull
    private String buildSearchQueryWithBeforeDate(@NonNull String whereClause) {
        return SEARCH_BUILD +
                "   INNER JOIN \n" +
                "     (SELECT c.build_name, c.repo, max(build_date) build_time \n" +
                "     FROM builds c\n" +
                "     INNER JOIN \n" +
                "           (SELECT build_name, repo\n" +
                "            FROM builds \n" + whereClause +
                "            GROUP BY repo, build_name) d\n" +
                "     ON c.build_name = d.build_name AND c.repo = d.repo\n" +
                "     GROUP BY c.repo, c.build_name) b\n" +
                "   ON a.build_name = b.build_name AND a.build_date = build_time AND a.repo = b.repo \n";
    }

    /**
     * Build a query to get all the builds with max build_date.
     */
    @NonNull
    private String buildSearchAllBuildsQuery(@NonNull String whereClause) {
        return SEARCH_BUILD +
                "   INNER JOIN \n" +
                "     (SELECT build_name, repo, MAX(build_date) build_time\n" +
                "     FROM builds\n" + whereClause +
                "     GROUP BY repo, build_name) b\n" +
                "   ON a.build_name = b.build_name AND a.build_date = b.build_time AND a.repo = b.repo\n";
    }

    /**
     * PostgreSQL-optimized query using DISTINCT ON to get the latest build per (repo, build_name)
     * in a single table scan, avoiding the self-join used by other databases.
     */
    private String buildSearchQueryPostgres(String whereClause) {
        return """
                SELECT build_id, build_name, build_number, build_date, repo FROM (
                  SELECT DISTINCT ON (repo, build_name)
                    build_id, build_name, build_number, build_date, repo
                  FROM builds
                  %s
                  ORDER BY repo, build_name, build_date DESC
                ) latest_builds
                """.formatted(whereClause);
    }

    @NonNull
    private String buildILikeWhereClause(
            @NonNull List<Object> whereParams,
            @NonNull BuildsSearchFilter  buildsSearchFilter) {
        StringBuilder where = new StringBuilder();
        String buildName = buildsSearchFilter.getName();
        String buildRepo = buildsSearchFilter.getBuildRepo();
        long after = buildsSearchFilter.getAfter();
        long before = buildsSearchFilter.getBefore();
        if (isNotBlank(buildName)) {
            String name = buildName.replace("?", "_").replace("*", "%");
            where.append("LOWER(build_name) LIKE ?");
            whereParams.add(name.toLowerCase() + "%");
        }
        if (isNotBlank(buildRepo)) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append("repo = ?");
            whereParams.add(buildRepo);
        }
        if (after != 0) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append("build_date >= ?");
            whereParams.add(after);
        }
        if (before != 0) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append("build_date <= ?");
            whereParams.add(before);
        }
        return !where.isEmpty() ? " WHERE " + where : "";
    }

    @NonNull
    private String buildOrderByAndSort(@NonNull BuildsSearchFilter buildsSearchFilter) {
        String orderBy = BuildsSearchFilter.OrderByColumn.forValue(buildsSearchFilter.getOrderBy());
        String sortOrder = BuildsSearchFilter.SortOrder.forValue(buildsSearchFilter.getSortOrder());
        return orderBy + " " + sortOrder;
    }

    @NonNull
    public List<BuildEntityRecord> selectBuildNumbersByName(@NonNull BuildsSearchFilter filter) throws SQLException {
        List<BuildEntityRecord> results = new LinkedList<>();
        List<Object> params = new ArrayList<>();
        StringBuilder queryBuilder = new StringBuilder("""
                SELECT build_id,
                       build_number,
                       build_date,
                       ci_url
                FROM builds
                WHERE build_name = ?
                  AND repo = ?
                """);
        params.add(filter.getName());
        params.add(filter.getBuildRepo());
        if (filter.getAfter() != 0) {
            queryBuilder.append(" AND build_date >= ?");
            params.add(filter.getAfter());
        }
        if (filter.getBefore() != 0) {
            queryBuilder.append(" AND build_date <= ?");
            params.add(filter.getBefore());
        }
        String orderBy = "number".equalsIgnoreCase(filter.getOrderBy()) ? BUILD_NUMBER_FIELD : BUILD_DATE_FIELD;
        String orderDir = "asc".equalsIgnoreCase(filter.getSortOrder()) ? "ASC" : "DESC";
        String q = createSqlBuilder(getDatabaseType()).build(
                queryBuilder.toString(),
                orderBy + " " + orderDir,
                filter.getOffset(),
                filter.getLimit()
        );
        try (ResultSet rs = getJdbcHelper().executeSelect(q, params.toArray())) {
            while (rs.next()) {
                results.add(buildBasicBuildEntityRecord(rs, filter));
            }
        }
        return results;
    }

    @NonNull
    public Map<Long, String> selectLatestPromotionStatusForBuildIds(@NonNull List<Long> buildsIds) throws SQLException {
        Map<Long, String> buildIdsToPromotionStatuses = new HashMap<>();
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<Long> buildIdsBulk : Lists.partition(buildsIds, bulkSize)) {
            try (ResultSet rs = getJdbcHelper().executeSelect("""
                    SELECT BP.build_id, BP.status
                    FROM build_promotions BP JOIN (
                        SELECT build_id, MAX(created) AS max_created
                        FROM build_promotions
                        WHERE build_id IN (#)
                        GROUP BY build_id
                    ) LATEST ON LATEST.build_id = BP.build_id
                            AND LATEST.max_created = BP.created
                    """, buildIdsBulk)) {
                while (rs.next()) {
                    buildIdsToPromotionStatuses.put(
                            rs.getLong(BUILD_ID_FIELD),
                            rs.getString(BUILD_PROMOTION_STATUS_FIELD)
                    );
                }
            }
        }
        return buildIdsToPromotionStatuses;
    }

    @NonNull
    private BuildEntityRecord buildBasicBuildEntityRecord(
            @NonNull ResultSet rs,
            @NonNull BuildsSearchFilter filter) throws SQLException {
        return BuildEntityRecord.builder()
                .repo(filter.getBuildRepo())
                .buildName(filter.getName())
                .buildNumber(rs.getString(BUILD_NUMBER_FIELD))
                .buildDate(rs.getLong(BUILD_DATE_FIELD))
                .buildId(rs.getLong(BUILD_ID_FIELD))
                .ciUrl(rs.getString(BUILD_CI_URL_FIELD))
                .build();
    }

    @NonNull
    public Collection<BuildEntity> findBuildsForArtifactChecksum(
            @NonNull BuildChecksumType type,
            @NonNull String checksum,
            @NonNull String buildRepo) throws SQLException {
        return findBuildsByArtifactChecksum(type, checksum, "build_artifacts", buildRepo);
    }

    @NonNull
    public Collection<BuildEntity> findBuildsForDependencyChecksum(
            @NonNull BuildChecksumType type,
            @NonNull String checksum,
            @NonNull String buildRepo) throws SQLException {
        return findBuildsByArtifactChecksum(type, checksum, "build_dependencies", buildRepo);
    }

    @NonNull
    private Collection<BuildEntity> findBuildsByArtifactChecksum(
            @NonNull  BuildChecksumType type,
            @NonNull String checksum,
            @NonNull String artifactsTable,
            @NonNull String buildRepo) throws SQLException {
        Collection<BuildEntity> results = Lists.newArrayList();
        if (BuildChecksumType.SHA256.equals(type)) {
            // Kicked out build artifacts sha2 from db because of performance
            log.debug("Build artifact sha256 search not supported.");
            return results;
        }

        List<Object> params = new ArrayList<>();
        String query = "SELECT DISTINCT b.* FROM builds b, " + artifactsTable +
                " artifactsTable, build_modules bm" +
                " WHERE b.build_id = bm.build_id" +
                " AND bm.module_id = artifactsTable.module_id" +
                " AND artifactsTable." + type.toColumnName() + " = ?" +
                " AND artifactsTable.module_id = bm.module_id" +
                " AND b.repo = ?";

        params.add(checksum);
        params.add(buildRepo);

        try (ResultSet rs = getJdbcHelper().executeSelect(query, params.toArray())) {
            while (rs.next()) {
                results.add(resultSetToBuild(rs));
            }
        }
        for (BuildEntity buildEntity : results) {
            buildEntity.setProperties(findBuildProperties(buildEntity.getBuildId()));
            buildEntity.setPromotions(findBuildPromotions(buildEntity.getBuildId()));
        }
        return results;
    }

    @NonNull
    private Set<BuildProperty> findBuildProperties(long buildId) throws SQLException {
        Set<BuildProperty> buildProperties = new HashSet<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT * FROM build_props WHERE build_id = ?", buildId)) {
            while (rs.next()) {
                buildProperties.add(resultSetToBuildProperty(rs));
            }
        }
        return buildProperties;
    }

    @NonNull
    private SortedSet<BuildPromotionStatus> findBuildPromotions(long buildId) throws SQLException {
        SortedSet<BuildPromotionStatus> buildPromotions = new TreeSet<>();
        try (ResultSet rs = getJdbcHelper().executeSelect("SELECT * FROM build_promotions WHERE build_id = ?", buildId)) {
            while (rs.next()) {
                buildPromotions.add(resultSetToBuildPromotion(rs));
            }
        }
        return buildPromotions;
    }

    @NonNull
    private static BuildProperty resultSetToBuildProperty(@NonNull ResultSet rs) throws SQLException {
        return new BuildProperty(
                rs.getLong(1),
                rs.getLong(2),
                rs.getString(3),
                rs.getString(4)
        );
    }

    @NonNull
    private static BuildProps resultSetToBuildProps(@NonNull ResultSet rs) throws SQLException {
        return new BuildProps(
                rs.getString(1),
                rs.getString(2),
                null
        );
    }

    @NonNull
    private BuildPromotionStatus resultSetToBuildPromotion(@NonNull ResultSet rs) throws SQLException {
        return new BuildPromotionStatus(
                rs.getLong(1),
                rs.getLong(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7)
        );
    }

    @NonNull
    private BuildEntity resultSetToBuild(@NonNull ResultSet rs) throws SQLException {
        return new BuildEntity(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getLong(4),
                rs.getString(5),
                rs.getLong(6),
                rs.getString(7),
                rs.getLong(8),
                rs.getString(9),
                rs.getString(10)
        );
    }

    @NonNull
    private GeneralBuild resultSetToGeneralBuild(
            @NonNull ResultSet rs,
            @NonNull Long id) throws SQLException {
        return new GeneralBuild(
                id,
                rs.getString(BUILD_NAME_FIELD),
                rs.getString(BUILD_NUMBER_FIELD),
                rs.getLong(BUILD_DATE_FIELD),
                rs.getString(BUILD_CI_URL_FIELD),
                rs.getLong(BUILD_CREATED_FIELD),
                rs.getString(BUILD_CREATED_BY_FIELD),
                rs.getLong(BUILD_MODIFIED_FIELD),
                rs.getString(BUILD_MODIFIED_BY_FIELD),
                rs.getString(BUILD_REPO_FIELD)
        );
    }

    public void insertBuildRelToReleaseBundle(
            @NonNull Long buildId,
            @NonNull String bundleRepoKey,
            @NonNull String bundleName,
            @NonNull String bundleVersion) throws SQLException {
        getJdbcHelper().executeUpdate("INSERT INTO build_release_bundles VALUES(?, ?, ?, ?, ?)",
                bridgeToArtifactory.getNextDatabaseId(), buildId, bundleRepoKey, bundleName, bundleVersion);
    }

    public void deleteBuildRelToReleaseBundle(
            @NonNull String bundleRepoKey,
            @NonNull String bundleName,
            @NonNull String bundleVersion
    ) throws SQLException {
        getJdbcHelper().executeUpdate("""
                DELETE FROM build_release_bundles
                WHERE bundle_repository = ? AND bundle_name = ? AND bundle_version = ?
                """, bundleRepoKey, bundleName, bundleVersion);
    }

    @NonNull
    private static Optional<String> createWhereClause(
            @NonNull List<String> buildRepos,
            @Nullable String buildName,
            @NonNull List<Object> params) {
        String where = null;
        if (!buildRepos.isEmpty()) {
            where = " WHERE repo IN (#)";
            params.add(buildRepos);
        }
        if (isNotBlank(buildName)) {
            if (where == null) {
                where = " WHERE";
            } else {
                where += " AND";
            }
            where += " build_name LIKE ?";
            if (buildName.contains("_") || buildName.contains("%")) {
                log.debug("Escaping special chars in buildName '{}'", buildName);
                buildName = buildName.replace("_", "^_").replace("%", "^%");
                where += " ESCAPE '^'";
            }
            if (buildName.endsWith("*")) {
                buildName = buildName.substring(0, buildName.length() - 1);
                buildName += "%";
            }
            params.add(buildName);
        }
        return Optional.ofNullable(where);
    }

    @NonNull
    private static String getBuildNumbersQuery(boolean excludeInReleaseBundles) {
        String query = """
                SELECT build_number,
                       build_date,
                       created_by,
                       bundle_name,
                       bundle_version,
                       bundle_repository
                FROM builds
                LEFT JOIN build_release_bundles on builds.build_id = build_release_bundles.build_id
                WHERE repo = ?
                  AND build_name = ?
                  AND build_date < ?
                  AND build_date > ?
                """;
        if (excludeInReleaseBundles) {
            query += "  AND build_rb_id IS NULL\n";
        }
        return query;
    }

    private record NumberStarted(@NonNull String number, long started) {
    }

    private record NumbersRowResult(@Nullable NumberStarted curr, boolean shouldReturn) {
    }

    @NonNull
    private static BuildNumberRecord createBuildNumberRecord(
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull ResultSet rs) throws SQLException {
        List<BuildNumberBundleRecord> bundles = new ArrayList<>();
        if (rs.getString(BUNDLE_NAME_FIELD) != null) {
            // add the first bundle
            bundles.add(createBuildNumberBundleRecord(rs));
        }
        return BuildNumberRecord.builder()
                .buildRepo(repoKey)
                .buildName(name)
                .buildNumber(rs.getString(BUILD_NUMBER_FIELD))
                .started(rs.getLong(BUILD_DATE_FIELD))
                .createdBy(rs.getString(BUILD_CREATED_BY_FIELD))
                .bundles(bundles)
                .build();
    }

    @NonNull
    private static BuildNumberBundleRecord createBuildNumberBundleRecord(@NonNull ResultSet rs) throws SQLException {
        String bundleRepo = rs.getString(BUNDLE_REPO_FIELD);
        String bundleName = rs.getString(BUNDLE_NAME_FIELD);
        String bundleVersion = rs.getString(BUNDLE_VERSION_FIELD);
        return new BuildNumberBundleRecord(bundleRepo, bundleName, bundleVersion);
    }

    @NonNull
    private String createQueryForExecution(
            @NonNull String query,
            long limit,
            long offset) {
        return createSqlBuilder(getDatabaseType())
                .build(query, "build_date, bundle_repository, bundle_name, bundle_version ASC", offset, limit);
    }

    @NonNull
    private NumbersRowResult consumeNumbersRow(
            @NonNull String repoKey,
            @NonNull String name,
            long offset,
            long limit,
            long bundlesLimit,
            @Nullable NumberStarted prev,
            @NonNull ResultSet rs,
            @NonNull List<BuildNumberRecord> buildNumbers) throws SQLException {
        NumberStarted curr = createNumberStarted(rs);
        log.debug("Processing curr {}, prev {}, offset {}", curr, prev, offset);
        if (!curr.equals(prev)) {
            if (buildNumbers.size() == limit) {
                // a new number/start, but we already have enough results
                return new NumbersRowResult(prev, true);
            }
            BuildNumberRecord record = createBuildNumberRecord(repoKey, name, rs);
            buildNumbers.add(record);
            log.debug("Added number {}; now has {} numbers", record, buildNumbers.size());
        } else if (getLastBundlesSize(buildNumbers) < bundlesLimit) {
            // add the next bundle, if we have room
            getLastBundles(buildNumbers).add(createBuildNumberBundleRecord(rs));
            log.debug("Added bundle to number {}; now it has {} bundles", curr, getLastBundlesSize(buildNumbers));
        } else {
            log.info("Reached the limit of {} bundles for number {}", bundlesLimit, curr);
            if (buildNumbers.size() == limit) {
                return new NumbersRowResult(curr, true);
            }
        }
        return new NumbersRowResult(curr, false);
    }

    @NonNull
    private static List<BuildNumberBundleRecord> getLastBundles(@NonNull List<BuildNumberRecord> buildNumbers) {
        return buildNumbers.getLast().getBundles();
    }

    private static int getLastBundlesSize(@NonNull List<BuildNumberRecord> buildNumbers) {
        return getLastBundles(buildNumbers).size();
    }

    @NonNull
    private static NumberStarted createNumberStarted(@NonNull ResultSet rs) throws SQLException {
        String currNumber = rs.getString(BUILD_NUMBER_FIELD);
        long started = rs.getLong(BUILD_DATE_FIELD);
        return new NumberStarted(currNumber, started);
    }

    @NonNull
    private List<Object[]> mapBuildPropertyParams(@NonNull List<BuildProperty> buildProperties) {
        int indexedValueMaxSize = getDbIndexedValueMaxSize(getDatabaseType());
        return buildProperties.stream().map(property -> new Object[] {
                property.getPropId(),
                property.getBuildId(),
                property.getPropKey(),
                preparePropValue(indexedValueMaxSize, property.getPropKey(), property.getPropValue())
        }).toList();
    }

    @NonNull
    private List<Object[]> mapBuildPromotionParams(@NonNull List<BuildPromotionStatus> buildPromotions) {
        return buildPromotions.stream().map(promotion -> new Object[] {
                promotion.getBuildId(),
                promotion.getCreated(),
                promotion.getCreatedBy(),
                promotion.getStatus(),
                promotion.getRepository(),
                promotion.getComment(),
                promotion.getCiUser()
        }).toList();
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
