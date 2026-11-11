package org.artifactory.storage.db.fs.dao;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.search.nuget.NugetSearchParams;
import com.jfrog.ph.acl.model.search.nuget.NugetSearchResult;
import com.jfrog.ph.acl.model.search.nuget.filter.NugetAccumulatingFilter;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderBy;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderByColumn;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetSortOrder;
import com.jfrog.ph.acl.service.PackageNugetSearch;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.entity.NodeProperty;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.BINARY_SIZE;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.DOWNLOAD_COUNT;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.LAST_UPDATED;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NODE_ID;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_AUTHORS;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_COPYRIGHT;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DEPENDENCY;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DESCRIPTION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DIGEST;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_ICON_URL;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_ID;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_LICENSE_URL;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_OWNERS;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_PROJECT_URL;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_RAW_VERSION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_RELEASE_NOTES;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_REQUIRE_LICENSE_ACCEPTANCE;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_SUMMARY;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_TAGS;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_TITLE;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_VERSION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.PACKAGE_PATH;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.PUBLISHED;
import static org.artifactory.storage.db.fs.dao.NodesDao.NODE_ID_COLUMN;
import static org.artifactory.storage.db.fs.dao.PropertiesDao.PROP_KEY_COLUMN;
import static org.artifactory.storage.db.fs.dao.PropertiesDao.PROP_VALUE_COLUMN;
import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;


/**
 * author: einasa
 */
@Slf4j
@Repository
public class PackageNugetSearchDaoImpl extends BaseDao implements PackageNugetSearch {
    static final NugetOrderByColumn DEFAULT_ORDER_BY_COLUMN = NugetOrderByColumn.ID;
    static final NugetSortOrder DEFAULT_ORDER_BY_ORDER = NugetSortOrder.ASC;
    private static final Set<NugetOrderByColumn> NON_SUPPORTED_SQL_COLUMNS = Set.of(NugetOrderByColumn.VERSION);

    private final DbService dbService;

    private static final String PKG_NAME_COLUMN = "pkg_name";
    private static final String PKG_VERSION_COLUMN = "pkg_version";
    private static final String PKG_TITLE_COLUMN = "pkg_title";
    private static final String PKG_TAGS_COLUMN = "pkg_tags";
    private static final String PKG_AUTHORS_COLUMN = "pkg_authors";
    private static final String PKG_CREATED_COLUMN = "pkg_created";
    private static final String PKG_DOWNLOADS_COLUMN = "pkg_downloads";
    private static final String PKG_RAW_VERSION_COLUMN = "pkg_raw_version";
    private static final String PKG_NODE_PATH_COLUMN = "node_path";
    private static final String PROP_VALUE_PLACE_HOLDER = "PROP_VALUE_PLACE_HOLDER";
    private static final String PROP_KEY_PLACE_HOLDER = "PROP_KEY_PLACE_HOLDER";
    private static final String QUERY_PLACE_HOLDER = "QUERY_PLACE_HOLDER";

    private static final String ORDER_BY_COLUMN = "#orderBy";
    static final String QUERY_KEYWORD = "keyWord";

    /**
     * Extracts all nuget V2 related props from the database.
     * Except props from node_props we get stats.downloadCount, nodes.binarySize, nodes.published and nodes.lastUpdated.
     */
    private static final String V2_PROPS_QUERY = """
            SELECT
                 np.prop_id, n.node_id, np.prop_key, np.prop_value, s.download_count as downloadCount,
                 n.bin_length as binarySize, n.created as published, n.updated as lastUpdated
            FROM nodes n
                     join node_props np on np.node_id=n.node_id
                     left join stats s on s.node_id=n.node_id
            where n.node_id in (#)
            """;
    /**
     * Contains a list of properties that are not stored in the node_props table but are needed for the V2 search.
     */
    private static final List<String> V2_ADDITIONAL_PROPS =
            List.of(DOWNLOAD_COUNT, BINARY_SIZE, PUBLISHED, LAST_UPDATED);
    /**
     * Extracts all nuget V3 related props from the database.
     * Except props from node_props we get stats.downloadCount.
     */
    private static final String V3_PROPS_QUERY = """
            SELECT
                 np.prop_id, np.node_id, np.prop_key, np.prop_value, s.download_count as downloadCount
            FROM node_props np
                     left join stats s on s.node_id=np.node_id
            where np.node_id in (#)
            """;
    /**
     * Contains a list of properties that are not stored in the node_props table but are needed for the V3 search.
     */
    private static final List<String> V3_ADDITIONAL_PROPS = List.of(DOWNLOAD_COUNT);

    /**
     * This query extracts from the database the props that are used to apply V3 specific search filters.
     * No custom ordering supported.
     */
    private static final String V3_NODES_IDS = String.format("""
            SELECT nodes.node_id,
                   nodes.node_name,
                   nodes.node_path,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_name,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_version,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_raw_version
            FROM nodes
            WHERE nodes.repo = ?
              AND exists(SELECT 1
                         FROM node_props npp1
                         WHERE npp1.node_id = nodes.node_id AND node_type = 1
                           AND npp1.prop_key IN (#)
                           AND (LOWER(npp1.prop_value) = ? OR LOWER(npp1.prop_value) LIKE ?)
                           )
            ORDER BY
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM node_props npp_criteria
                        WHERE npp_criteria.node_id = nodes.node_id
                          AND LOWER(npp_criteria.prop_value) = ?
                    ) THEN 0
                    ELSE 1
                    END,
                    pkg_name ASC
            """, NUGET_ID, NUGET_VERSION, NUGET_RAW_VERSION);

    /**
     * This query extracts from the database the props that are used to apply V2 specific search filters.
     * Some of the filters are ODataFilter, IsLatestVersion, IsAbsoluteLatestVersion.
     * The query supports custom ordering.
     */
    private static final String V2_NODES_IDS = String.format("""
            SELECT nodes.node_id,
                   nodes.node_name,
                   nodes.node_path,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_name,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_version,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_title,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_authors,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_tags,
                   (SELECT LOWER(prop_value)
                    FROM node_props npp
                    WHERE npp.prop_key = '%s'
                      AND nodes.node_id = npp.node_id) AS pkg_raw_version,
                    (SELECT download_count
                     FROM stats st
                     WHERE st.node_id = nodes.node_id) AS pkg_downloads,
                   nodes.created AS pkg_created
            FROM nodes
            WHERE nodes.repo = ?
              AND EXISTS (SELECT 1
                          FROM node_props npp1
                          WHERE npp1.node_id = nodes.node_id AND node_type = 1
                          AND npp1.node_id IS NOT NULL
                          AND npp1.prop_key IN (#)
                          AND (LOWER(npp1.prop_value) = ? OR LOWER(npp1.prop_value) LIKE ?)
                          )
            ORDER BY
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM node_props npp_criteria
                        WHERE npp_criteria.node_id = nodes.node_id
                          AND LOWER(npp_criteria.prop_value) = ?
                    ) THEN 0
                    ELSE 1
                    END,
                    #orderBy
            """, NUGET_ID, NUGET_VERSION, NUGET_TITLE, NUGET_AUTHORS, NUGET_TAGS, NUGET_RAW_VERSION);
    private static final String NUGET_V3_SEARCH_QUERY_WITH_FILTERS =
            String.format("""
                    SELECT nodes.node_id,
                           nodes.node_name,
                           nodes.node_path,
                           (SELECT LOWER(prop_value)
                            FROM node_props npp
                            WHERE npp.prop_key = '%s'
                              AND nodes.node_id = npp.node_id) AS pkg_name,
                           (SELECT LOWER(prop_value)
                            FROM node_props npp
                            WHERE npp.prop_key = '%s'
                              AND nodes.node_id = npp.node_id) AS pkg_version,
                           (SELECT LOWER(prop_value)
                            FROM node_props npp
                            WHERE npp.prop_key = '%s'
                              AND nodes.node_id = npp.node_id) AS pkg_raw_version
                    FROM nodes
                    WHERE nodes.repo = ? AND node_type = 1
                    QUERY_PLACE_HOLDER
                    ORDER BY pkg_name ASC
                    """, NUGET_ID, NUGET_VERSION, NUGET_RAW_VERSION);

    private static final String BUILD_DYNAMIC_QUERY_CONTAINS_PROP_VALUE_TEMPLATE = """
            AND exists(SELECT 1
                         FROM node_props npp1
                         WHERE npp1.node_id = nodes.node_id AND node_type = 1
                           AND LOWER(npp1.prop_value) LIKE '%PROP_VALUE_PLACE_HOLDER%'
                           AND npp1.prop_key = 'PROP_KEY_PLACE_HOLDER'
                           )
            """;

    private static final String BUILD_DYNAMIC_QUERY_EQUAL_IGNORE_CASE_PROP_VALUE_TEMPLATE = """
            AND exists(SELECT 1
                         FROM node_props npp1
                         WHERE npp1.node_id = nodes.node_id AND node_type = 1
                           AND LOWER(npp1.prop_value) = 'PROP_VALUE_PLACE_HOLDER'
                           AND npp1.prop_key = 'PROP_KEY_PLACE_HOLDER'
                           )
            """;

    private static final String BUILD_DYNAMIC_QUERY_START_WITH_PROP_VALUE_TEMPLATE = """
            AND exists(SELECT 1
                         FROM node_props npp1
                         WHERE npp1.node_id = nodes.node_id AND node_type = 1
                           AND (npp1.prop_value) LIKE 'PROP_VALUE_PLACE_HOLDER%'
                           AND npp1.prop_key = 'PROP_KEY_PLACE_HOLDER'
                           )
            """;
    // Props used for V2 search. Migrated from legacy search.
    private static final List<String> V2_SEARCH_PROPS = List.of(
            NUGET_ID,
            NUGET_DESCRIPTION);
    // Props used for V3 search.
    private static final List<String> V3_SEARCH_PROPS = List.of(
            NUGET_ID,
            NUGET_VERSION,
            NUGET_TITLE,
            NUGET_TAGS, NUGET_AUTHORS, NUGET_DESCRIPTION,
            NUGET_SUMMARY,
            NUGET_OWNERS);

    /**
     * List of props extracted from node_props table for V2 search.
     */
    private static final List<Pair<String, String>> V2_EXTRACT_NODE_PROPS = List.of(
            Pair.of(PKG_NAME_COLUMN, NUGET_ID),
            Pair.of(PKG_VERSION_COLUMN, NUGET_VERSION),
            Pair.of(NODE_ID_COLUMN, NODE_ID),
            Pair.of(PKG_NODE_PATH_COLUMN, PACKAGE_PATH),
            Pair.of(PKG_TITLE_COLUMN, NUGET_TITLE),
            Pair.of(PKG_TAGS_COLUMN, NUGET_TAGS),
            Pair.of(PKG_AUTHORS_COLUMN, NUGET_AUTHORS),
            Pair.of(PKG_CREATED_COLUMN, PUBLISHED),
            Pair.of(PKG_DOWNLOADS_COLUMN, DOWNLOAD_COUNT),
            Pair.of(PKG_RAW_VERSION_COLUMN, NUGET_RAW_VERSION));

    /**
     * List of props extracted from node_props table for V3 search.
     */
    private static final List<Pair<String, String>> V3_EXTRACT_NODE_PROPS = List.of(
            Pair.of(PKG_NAME_COLUMN, NUGET_ID),
            Pair.of(PKG_VERSION_COLUMN, NUGET_VERSION),
            Pair.of(NODE_ID_COLUMN, NODE_ID),
            Pair.of(PKG_NODE_PATH_COLUMN, PACKAGE_PATH),
            Pair.of(PKG_RAW_VERSION_COLUMN, NUGET_RAW_VERSION));


    @VisibleForTesting
    static Map<String, String> parseQ(String q) {
        if (StringUtils.isBlank(q)) {
            return Map.of();
        }
        if (!q.contains(":")) {
            return Map.of(QUERY_KEYWORD, q);
        }
        return Arrays.stream(q.split(" "))
                .map(i -> i.split(":"))
                .filter(array -> array.length == 2)
                .filter(filter -> StringUtils.isNotBlank(filter[0]))
                .filter(filter -> StringUtils.isNotBlank(filter[1]))
                .collect(Collectors.toMap(filter -> filter[0].toLowerCase(), filter -> filter[1].replace("\"", "")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NugetSearchResult searchNugetPackageV3(@NonNull NugetSearchParams searchParams,
            @NonNull NugetAccumulatingFilter packageFilter) {
        Map<String, String> query = parseQ(searchParams.getSearchTerm());
        Pair<Map<Long, Properties>, Integer> results;
        String calculateQueryAfterParsingParam;
        String searchTermValAfterParsingParams;

        if (!query.containsKey(QUERY_KEYWORD)) {
            calculateQueryAfterParsingParam = buildDynamicForV3(query, searchParams.getRepoKey());
            searchTermValAfterParsingParams = null;
        } else {
            calculateQueryAfterParsingParam = V3_NODES_IDS;
            searchTermValAfterParsingParams = query.get(QUERY_KEYWORD);
        }
        results = getSearchResult(calculateQueryAfterParsingParam, searchParams.getRepoKey(),
                searchTermValAfterParsingParams, VersionSpecificContext.V3, packageFilter);
        List<PackageArtifact> packages = mapPropertiesToArtifacts(results.getLeft());
        filterNullPackageId(packages);
        return new NugetSearchResult(packages, results.getRight());
    }

    private String buildDynamicForV3(Map<String, String> query, String repoKey) {
        StringBuilder graduallyBuildingDynamicQuery = new StringBuilder();

        for (Map.Entry<String, String> entry : query.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String lowerCaseValue = entry.getValue().toLowerCase();
            String originalValue = entry.getValue();
            NugetV3SearchOnSpecificProps keyFromEnum = NugetV3SearchOnSpecificProps.fromKey(key);
            switch (keyFromEnum) {
                case NUGET_ID:
                case PACKAGE_ID:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueEqualIgnoreCaseVal(NUGET_ID, lowerCaseValue));
                    break;
                case ID:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_ID, lowerCaseValue));
                    break;
                case OWNER:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_OWNERS, lowerCaseValue));
                    break;
                case VERSION:
                case NUGET_VERSION:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueStartWithVal(NUGET_VERSION,
                                    trimSemVer2MetaData((originalValue))));
                    break;
                case TITLE:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_TITLE, lowerCaseValue));
                    break;
                case TAGS:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_TAGS, lowerCaseValue));
                    break;
                case AUTHOR:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_AUTHORS, lowerCaseValue));
                    break;
                case DESCRIPTION:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_DESCRIPTION,
                                    lowerCaseValue));
                    break;
                case SUMMARY:
                    graduallyBuildingDynamicQuery.append(
                            buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(NUGET_SUMMARY, lowerCaseValue));
                    break;
                default:
                    log.debug("Searching by: {} prop key isn't part of nuget search for repository: {}", key, repoKey);
            }
        }
        return NUGET_V3_SEARCH_QUERY_WITH_FILTERS.replace(QUERY_PLACE_HOLDER, graduallyBuildingDynamicQuery.toString());
    }

    private String buildSubQueryDependOnPropKeyPropValueContainsIgnoreCaseVal(String propKey, String propValue) {
        return BUILD_DYNAMIC_QUERY_CONTAINS_PROP_VALUE_TEMPLATE.replace(PROP_KEY_PLACE_HOLDER, propKey)
                .replace(PROP_VALUE_PLACE_HOLDER, propValue);
    }

    private String buildSubQueryDependOnPropKeyPropValueEqualIgnoreCaseVal(String propKey, String propValue) {
        return BUILD_DYNAMIC_QUERY_EQUAL_IGNORE_CASE_PROP_VALUE_TEMPLATE.replace(PROP_KEY_PLACE_HOLDER, propKey)
                .replace(PROP_VALUE_PLACE_HOLDER, propValue);
    }

    private String buildSubQueryDependOnPropKeyPropValueStartWithVal(String propKey, String propValue) {
        return BUILD_DYNAMIC_QUERY_START_WITH_PROP_VALUE_TEMPLATE.replace(PROP_KEY_PLACE_HOLDER, propKey)
                .replace(PROP_VALUE_PLACE_HOLDER, propValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NugetSearchResult searchNugetPackageV2(@NonNull NugetSearchParams searchParams,
            @NonNull NugetAccumulatingFilter packageFilter) {
        final String sqlWithOrderBy = V2_NODES_IDS
                .replace(ORDER_BY_COLUMN, toSql(searchParams.getOrderBy()));
        Pair<Map<Long, Properties>, Integer> results =
                getSearchResult(sqlWithOrderBy, searchParams.getRepoKey(), searchParams.getSearchTerm(),
                        VersionSpecificContext.V2, packageFilter);
        List<PackageArtifact> packages = mapPropertiesToArtifacts(results.getLeft());
        return new NugetSearchResult(packages, results.getRight());
    }

    private void filterNullPackageId(List<PackageArtifact> packages) {
        packages.removeIf(packageArtifact -> {
            String id = getArtifactPropValue(NUGET_ID, packageArtifact);
            if (id == null) {
                log.warn("Skipping package :{} due to missing nuget.id property.", packageArtifact.getName());
                return true;
            }
            return false;
        });
    }


    private Pair<Map<Long, Properties>, Integer> getSearchResult(String sqlForExecution, String repoKey,
            @Nullable String propValue, VersionSpecificContext versionSpecificContext,
            NugetAccumulatingFilter packageFilter) {
        Map<Long, Properties> nodesProps = versionSpecificContext.getNodesProps();
        Pair<List<Long>, Integer> nodesMeetsSearchRequirements;
        PropsAccumulator propsAccumulator;
        try {
            if (propValue != null) {
                nodesMeetsSearchRequirements = getMatchingNodeIds(
                        sqlForExecution, repoKey, propValue, versionSpecificContext, packageFilter);
            } else {
                nodesMeetsSearchRequirements = getMatchingNodeIds(
                        sqlForExecution, repoKey, versionSpecificContext, packageFilter);
            }
            propsAccumulator =
                    getNodesProperties(repoKey, nodesMeetsSearchRequirements.getLeft(), versionSpecificContext);

        } catch (SQLException e) {
            throw new StorageException("Failed to run NuGet search query", e);
        }

        // use nodeIds to get the properties to preserve the order of the results.
        for (Long nodeId : nodesMeetsSearchRequirements.getLeft()) {
            nodesProps.put(nodeId, propsAccumulator.getProperties(nodeId));
        }
        return Pair.of(nodesProps, nodesMeetsSearchRequirements.getRight());
    }

    /**
     * Get the properties for the nodes provided. The properties are extracted from the node_props/stats/nodes tables
     * depending on the V2/V3.
     *
     * @param repoName               The repository to search in.
     * @param nodes                  The nodes to get the properties for.
     * @param versionSpecificContext The context that contains SQL query to execute and the properties to extract.
     * @return An object with MultiMap of nodes_props and Map of additional properties.
     * They go in separate collections because to save memory.
     * Multiple nodes_props exist for each node, but additional properties are unique.
     */
    private PropsAccumulator getNodesProperties(String repoName, List<Long> nodes,
            VersionSpecificContext versionSpecificContext) throws SQLException {
        PropsAccumulator propsAccumulator = new PropsAccumulator(versionSpecificContext.additionalPropsNames);
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        // Oracle limits the max elements in the IN clause to 1000. Lists bigger than max chunk value are done in
        //multiple queries
        final int chunk = ConstantValues.propertiesSearchChunkSize.getInt();
        if (isSupportsStreaming(dbService.getDatabaseType())) {
            builder.fetchSize(ConstantValues.aqlStreamedFetchSize.getInt());
        }
        // split to chunks of no more than CHUNK
        List<List<Long>> partitions = Lists.partition(nodes, chunk);
        for (List<Long> ls : partitions) {
            long startTime = System.currentTimeMillis();
            ResultSet resultSet = null;
            try {
                resultSet =
                        jdbcHelper.executeSelectStreamed(builder.build(), versionSpecificContext.selectPropsQuery, ls);
                long endTime = System.currentTimeMillis(); // Capture the end time
                log.debug("Fetching results from nodes table for repository: {} execution time: {} ", repoName,
                        (endTime - startTime));
                while (resultSet.next()) {
                    // extract node_props and additional props from result set.
                    propsAccumulator.processResultSet(resultSet);
                }
            } finally {
                DbUtils.close(resultSet);
            }
        }
        return propsAccumulator;
    }

    private List<PackageArtifact> mapPropertiesToArtifacts(Map<Long, Properties> map) {
        List<PackageArtifact> result = new ArrayList<>();
        for (Properties properties : map.values()) {
            result.add(mapPropertyToPackageArtifact(properties));
        }
        return result;
    }

    private Pair<List<Long>, Integer> getMatchingNodeIds(String sqlForExecution,
            String repo, String searchTerm, VersionSpecificContext versionSpecificContext,
            NugetAccumulatingFilter packageFilter) throws SQLException {
        String searchTermSubStrings = String.format("%%%s%%", searchTerm.toLowerCase());
        return executeQueryAndProcessResults(sqlForExecution, repo, searchTermSubStrings, versionSpecificContext,
                packageFilter);
    }

    private Pair<List<Long>, Integer> getMatchingNodeIds(String sqlForExecution,
            String repo, VersionSpecificContext versionSpecificContext,
            NugetAccumulatingFilter packageFilter) throws SQLException {
        return executeQueryAndProcessResults(sqlForExecution, repo, null, versionSpecificContext, packageFilter);
    }

    /**
     * Get the node id's that satisfy the search criteria and filters provided.
     *
     * @param sqlForExecution        The SQL query to execute. Different queries are used for V2 and V3 search.
     * @param repo                   The repository to search in.
     * @param searchTermSubStrings   The search term to look for in the properties.
     * @param versionSpecificContext V2 or V3 search context to address differences in searches.
     * @param packageFilter          The filter to apply to the packages.
     * @return A pair containing:
     * a list of node id's that satisfy the search criteria and filter
     * the total number of unique packages available for V3 and -1 for V2.
     */
    private Pair<List<Long>, Integer> executeQueryAndProcessResults(String sqlForExecution,
            String repo, String searchTermSubStrings, VersionSpecificContext versionSpecificContext,
            NugetAccumulatingFilter packageFilter) throws SQLException {
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();

        ResultSet resultSet = null;
        if (isSupportsStreaming(dbService.getDatabaseType())) {
            builder.fetchSize(ConstantValues.aqlStreamedFetchSize.getInt());
        }
        try {
            long startTime = System.currentTimeMillis(); // Capture the start time
            log.debug("Running nuget search query: {} for repository: {}", sqlForExecution, repo);
            if (searchTermSubStrings != null) {
                String searchTerm = searchTermSubStrings.replace("%", "");
                resultSet = jdbcHelper.executeSelectStreamed(builder.build(), sqlForExecution, repo,
                        versionSpecificContext.searchByProps, searchTerm, searchTermSubStrings, searchTerm);
            } else {
                resultSet = jdbcHelper.executeSelectStreamed(builder.build(), sqlForExecution, repo);
            }
            long endTime = System.currentTimeMillis(); // Capture the end time
            log.debug("Fetching results from props table for repository: {} execution time: {} ", repo,
                    (endTime - startTime));
            while (resultSet.next()) {
                final Map<String, Collection<String>> packageProps = extractPropsFromResultSet(
                        resultSet, versionSpecificContext.extractProps);
                // process all incoming packages applying filters.
                packageFilter.process(packageProps);
            }
            // Sort nodes by version if needed.
            final Pair<List<Long>, Integer> matchingNodeIdsWithCount = packageFilter.getMatchingNodeIds();
            log.trace("Matching nodes: {}", matchingNodeIdsWithCount.getKey());
            return Pair.of(matchingNodeIdsWithCount.getKey(), matchingNodeIdsWithCount.getValue());
        } finally {
            DbUtils.close(resultSet);
        }
    }

    @NonNull
    private static Map<String, Collection<String>> extractPropsFromResultSet(ResultSet resultSet,
            List<Pair<String, String>> extractProps) throws SQLException {
        Map<String, Collection<String>> props = new HashMap<>();

        for (Pair<String, String> prop : extractProps) {
            extractNugetProp(resultSet, props, prop.getLeft(), prop.getRight());
        }
        return props;
    }

    /**
     * Extracts a property from the result set and adds it to the props map
     *
     * @param resultSet   The result set where properties are extracted from
     * @param props       The collection where properties are added to
     * @param resPropName The name of the property in the result set
     * @param propKey     The key of the property it should be stored by in the props map
     */
    private static void extractNugetProp(ResultSet resultSet, Map<String, Collection<String>> props,
            String resPropName, String propKey) throws SQLException {
        String propValue = resultSet.getString(resPropName);
        if (propValue != null) {
            props.put(propKey, List.of(propValue));
        }
    }

    private PackageArtifact mapPropertyToPackageArtifact(Properties item) {
        PackageArtifact.PackageArtifactBuilder builder = PackageArtifact.builder();
        builder.file(false);
        builder.packageType(PackageType.NUGET);
        String downloadCountStr = item.getFirst(DOWNLOAD_COUNT);
        SetMultimap<String, String> props = HashMultimap.create();
        if (downloadCountStr != null) {
            // setting downloadCount for V3.
            builder.downloadCount(Long.parseLong(downloadCountStr));
            // setting downloadCount for V2.
            putIfNotNull(props, DOWNLOAD_COUNT, item.getFirst(DOWNLOAD_COUNT));
        }
        putIfNotNull(props, NUGET_ID, item.getFirst(NUGET_ID));
        putIfNotNull(props, NUGET_VERSION, item.getFirst(NUGET_VERSION));
        putIfNotNull(props, NUGET_TITLE, item.getFirst(NUGET_TITLE));
        putIfNotNull(props, NUGET_SUMMARY, item.getFirst(NUGET_SUMMARY));
        putIfNotNull(props, NUGET_AUTHORS, item.getFirst(NUGET_AUTHORS));
        putIfNotNull(props, NUGET_DESCRIPTION, item.getFirst(NUGET_DESCRIPTION));
        putIfNotNull(props, NUGET_COPYRIGHT, item.getFirst(NUGET_COPYRIGHT));
        putIfNotNull(props, NUGET_RELEASE_NOTES, item.getFirst(NUGET_RELEASE_NOTES));
        putIfNotNull(props, NUGET_PROJECT_URL, item.getFirst(NUGET_PROJECT_URL));
        putIfNotNull(props, NUGET_LICENSE_URL, item.getFirst(NUGET_LICENSE_URL));
        putIfNotNull(props, NUGET_ICON_URL, item.getFirst(NUGET_ICON_URL));
        putIfNotNull(props, NUGET_TAGS, item.getFirst(NUGET_TAGS));
        putIfNotNull(props, NUGET_RAW_VERSION, item.getFirst(NUGET_RAW_VERSION));
        putIfNotNull(props, PUBLISHED, item.getFirst(PUBLISHED));
        putIfNotNull(props, LAST_UPDATED, item.getFirst(LAST_UPDATED));
        putIfNotNull(props, BINARY_SIZE, item.getFirst(BINARY_SIZE));
        putIfNotNull(props, NUGET_REQUIRE_LICENSE_ACCEPTANCE,
                String.valueOf(item.getFirst(NUGET_REQUIRE_LICENSE_ACCEPTANCE)));
        final Set<String> dependencies = item.get(NUGET_DEPENDENCY);
        if (dependencies != null) {
            dependencies.forEach(dep -> props.put(NUGET_DEPENDENCY, dep));
        }
        putIfNotNull(props, NUGET_DIGEST, item.getFirst(NUGET_DIGEST));

        builder.properties(props);
        return builder.build();
    }

    private static void putIfNotNull(Multimap<String, String> props, @NonNull String key, String value) {
        if (value != null) {
            props.put(key, value);
        }
    }

    @VisibleForTesting
    static String toSql(List<NugetOrderBy> orderBy) {
        Map<NugetOrderByColumn, NugetSortOrder> orderMap = new LinkedHashMap<>();
        if (orderBy != null) {
            // this map is used to avoid duplicates in case of multiple columns with the same name.
            for (NugetOrderBy ob : orderBy) {
                if (NON_SUPPORTED_SQL_COLUMNS.contains(ob.column())) {
                    continue;
                }
                orderMap.putIfAbsent(ob.column(), ob.order());
            }
        }
        return orderMap.entrySet().stream()
                .map(e -> e.getKey().getValue() + " " + e.getValue().getValue())
                .reduce((s1, s2) -> s1 + ", " + s2)
                .orElse(DEFAULT_ORDER_BY_COLUMN.getValue() + " " + DEFAULT_ORDER_BY_ORDER.getValue());
    }

    @Nullable
    public static String getArtifactPropValue(@NonNull String key, @NonNull PackageArtifact artifact) {
        SetMultimap<String, String> properties = artifact.getProperties();
        String value = null;
        if (properties.containsKey(key)) {
            value = properties.get(key).stream().findFirst().orElse(null);
        }
        return value;
    }

    @Autowired
    public PackageNugetSearchDaoImpl(ArtifactoryJdbcHelper jdbcHelper, DbService dbService) {
        super(jdbcHelper);
        this.dbService = dbService;
    }

    @VisibleForTesting
    static String trimSemVer2MetaData(String version) {
        if (version == null) {
            return null;
        }
        int endIndex = version.indexOf('+');
        if (endIndex == -1) {
            return version;
        }
        return version.substring(0, endIndex);
    }

    /*
    V3 and V2 search are different in several ways. Those differences are contained in this enum.
     */
    private enum VersionSpecificContext {
        V2(V2_SEARCH_PROPS, V2_EXTRACT_NODE_PROPS, V2_PROPS_QUERY, V2_ADDITIONAL_PROPS, LinkedHashMap::new),
        V3(V3_SEARCH_PROPS, V3_EXTRACT_NODE_PROPS, V3_PROPS_QUERY, V3_ADDITIONAL_PROPS, LinkedHashMap::new);

        private final List<String> searchByProps;
        private final List<Pair<String, String>> extractProps;
        private final String selectPropsQuery;
        private final List<String> additionalPropsNames;
        // V3 does sorting after results retrieving. V2 relies on sorting DB returns, so different types of maps needed.
        private final Supplier<Map<Long, Properties>> nodesPropsSupplier;

        VersionSpecificContext(List<String> searchByProps, List<Pair<String, String>> extractProps,
                String selectPropsQuery, List<String> additionalPropsNames,
                Supplier<Map<Long, Properties>> nodesPropsSupplier) {
            this.searchByProps = searchByProps;
            this.extractProps = extractProps;
            this.selectPropsQuery = selectPropsQuery;
            this.additionalPropsNames = additionalPropsNames;
            this.nodesPropsSupplier = nodesPropsSupplier;
        }

        public Map<Long, Properties> getNodesProps() {
            return nodesPropsSupplier.get();
        }
    }

    /**
     * Accumulates properties for a node. It is used to accumulate properties for a node and additional properties
     * that are not stored in the node_props table.
     * Different properties are collected for V2 and V3 search so this class is used to abstract the differences.
     */
    private static class PropsAccumulator {
        private final Multimap<Long, NodeProperty> nodeProps;
        private final Map<Long, Map<String, String>> additionalProps;
        private final List<String> additionalPropsNames;

        public PropsAccumulator(List<String> additionalProps) {
            this.nodeProps = HashMultimap.create();
            this.additionalProps = new HashMap<>();
            this.additionalPropsNames = additionalProps;
        }

        private void addNodeProperty(NodeProperty nodeProperty) {
            nodeProps.put(nodeProperty.getNodeId(), nodeProperty);
        }

        private void addAdditionalProp(long nodeId, String propName, ResultSet resultSet) throws SQLException {
            String propValue = resultSet.getString(propName);
            additionalProps.get(nodeId).putIfAbsent(propName, propValue);
        }

        /**
         * Processes a result set row.
         * Each resultSet contains 1 row from node_props and all additional properties.
         * We need to extract node_props from each resultSet and additionalProps only from the first one for nodeId.
         */
        private void processResultSet(ResultSet resultSet) throws SQLException {
            long nodeId = resultSet.getLong(NODE_ID_COLUMN);
            String propKey = resultSet.getString(PROP_KEY_COLUMN);
            String propValue = emptyIfNull(resultSet.getString(PROP_VALUE_COLUMN));
            addNodeProperty(new NodeProperty(0L, nodeId, propKey, propValue));
            // each row of node_props contains additional properties joined. So need to process once per nodeId.
            if (additionalProps.computeIfAbsent(nodeId, k -> new HashMap<>()).isEmpty()) {
                for (String additionalPropsName : additionalPropsNames) {
                    addAdditionalProp(nodeId, additionalPropsName, resultSet);
                }
            }
        }

        private Properties getProperties(long nodeId) {
            Properties props = new PropertiesImpl();
            nodeProps.get(nodeId).forEach(singleProp -> props.put(singleProp.getPropKey(), singleProp.getPropValue()));
            for (String additionalPropsName : additionalPropsNames) {
                final Map<String, String> nodeAdditionalProps = additionalProps.getOrDefault(nodeId, new HashMap<>());
                props.put(additionalPropsName, nodeAdditionalProps.getOrDefault(additionalPropsName, "0"));
            }
            return props;
        }
    }

    private enum NugetV3SearchOnSpecificProps {
        NUGET_ID("nuget.id"),
        ID("id"),
        PACKAGE_ID("packageid"),
        NUGET_VERSION("nuget.version"),
        VERSION("version"),
        TITLE("title"),
        TAGS("tags"),
        AUTHOR("author"),
        DESCRIPTION("description"),
        OWNER("owner"),
        SUMMARY("summary"),
        UNKNOWN_INVALID("unknown.invalid");

        private final String key;

        NugetV3SearchOnSpecificProps(String key) {
            this.key = key;
        }

        private String getKey() {
            return key;
        }

        public static NugetV3SearchOnSpecificProps fromKey(String key) {
            for (NugetV3SearchOnSpecificProps prop : values()) {
                if (prop.getKey().equals(key)) {
                    return prop;
                }
            }
            return UNKNOWN_INVALID;
        }
    }
}
