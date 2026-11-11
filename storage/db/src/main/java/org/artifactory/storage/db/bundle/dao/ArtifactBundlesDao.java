/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.bundle.dao;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.rest.distribution.bundle.utils.BundleNameVersionResolver;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.artifactory.storage.db.bundle.model.DBBundleResult;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.common.ClockUtils;
import org.joda.time.DateTime;
import org.mapstruct.ap.internal.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Tomer Mayost
 */
@Repository
@Slf4j
public class ArtifactBundlesDao extends BaseDao {

    public static final String BUNDLE_FILES_TABLE = "bundle_files";

    public static final String ARTIFACT_BUNDLES_TABLE = "artifact_bundles";
    private static final String EXPIRED_BUNDLES_QUERY =
            "SELECT id FROM " + ARTIFACT_BUNDLES_TABLE + " where type = 'TARGET'" + " and status != '" +
                    BundleTransactionStatus.COMPLETE.name() + "' and date_distributed < ?";

    private static final String UPDATE_BUNDLES_CREATION_DATE_QUERY =
            "UPDATE " + ARTIFACT_BUNDLES_TABLE + " SET date_created = ? where " +
                    "id = ? and status = '" + BundleTransactionStatus.INPROGRESS.name() + "'";

    private static final String INSERT_TO_BUNDLE_FILES_QUERY = "INSERT INTO " + BUNDLE_FILES_TABLE +
            " (id, node_id, bundle_id, repo_path, original_component_details) VALUES (?, ?, ?, ?, ?)";

    private static final String IN_TRANSIT_REPO_KEY = "_intransit";

    private static final String VERSION = "version";
    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final String DATE_CREATED = "date_created";
    private static final String STORING_REPO = "storing_repo";
    private static final String KEEP = "keep";
    private static final String NAME = "name";
    private static final String DATE_DISTRIBUTED = "date_distributed";
    private static final String SIGNATURE = "signature";
    private static final String TYPE = "type";
    private static final String SOURCE_SERVICE_ID = "source_service_id";

    private final DbService dbService;

    @Autowired
    public ArtifactBundlesDao(ArtifactoryJdbcHelper jdbcHelper, DbService dbService) {
        super(jdbcHelper);
        this.dbService = dbService;
    }

    public int create(DBArtifactsBundle bundle) throws SQLException {
        bundle.setId(Optional.ofNullable(nullIfZero(bundle.getId()))
                .orElseGet(dbService::nextId));
        bundle.validate();
        String storingRepo = BundleType.TARGET.equals(bundle.getType()) ? "release-bundles" : bundle.getStoringRepo();
        return jdbcHelper.executeUpdate("INSERT INTO " + ARTIFACT_BUNDLES_TABLE +
                        "(id, " +
                        "name, " +
                        "version, " +
                        "status, " +
                        "date_created, " +
                        "date_distributed, " +
                        "signature, " +
                        "type, " +
                        "storing_repo, " +
                        "keep, " +
                        "source_service_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bundle.getId(),
                bundle.getName(),
                bundle.getVersion(),
                bundle.getStatus().name(),
                bundle.getDateCreated().getMillis(),
                bundle.getDateDistributed() != null ? bundle.getDateDistributed().getMillis() : null,
                bundle.getSignature(),
                bundle.getType().name(),
                storingRepo,
                0,
                bundle.getSourceServiceId());
    }

    public void deleteBundleNodes(long bundleId) throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM " + BUNDLE_FILES_TABLE + " WHERE bundle_id = ?", bundleId);
    }

    public boolean deleteArtifactsBundle(long id) throws SQLException {
        int deleted = jdbcHelper.executeUpdate("DELETE FROM " + ARTIFACT_BUNDLES_TABLE + " WHERE id = ?", id);
        return deleted > 0;
    }

    public boolean deleteAllArtifactsBundles() throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM " + ARTIFACT_BUNDLES_TABLE) > 0;
    }

    public boolean deleteAllBundleNodes() throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM " + BUNDLE_FILES_TABLE) > 0;
    }

    public int create(BundleNode bundleNode) throws SQLException {
        bundleNode.setId(Optional.ofNullable(nullIfZero(bundleNode.getId())).orElseGet(dbService::nextId));
        bundleNode.validate();
        return jdbcHelper.executeUpdate(INSERT_TO_BUNDLE_FILES_QUERY,
                bundleNode.getId(), bundleNode.getNodeId(),
                bundleNode.getBundleId(), bundleNode.getRepoPath(), bundleNode.getOriginalFileDetails());
    }

    public void createBulk(@NonNull List<BundleNode> bundleNodes) throws SQLException {
        List<List<BundleNode>> bundleNodeBulks = Lists.partition(bundleNodes,
                ConstantValues.dbOperationsBulkSize.getInt());
        for (List<BundleNode> bundleNodeBulk : bundleNodeBulks) {
            List<Object[]> listOfParams = new ArrayList<>();
            for (BundleNode bundleNode : bundleNodeBulk) {
                Object[] params = bundleNodeToInsertParams(bundleNode);
                listOfParams.add(params);
            }
            jdbcHelper.executeBulkUpdate(INSERT_TO_BUNDLE_FILES_QUERY, listOfParams);
        }
    }

    private Object[] bundleNodeToInsertParams(BundleNode bundleNode) {
        return new Object[] {
                Optional.ofNullable(nullIfZero(bundleNode.getId())).orElseGet(dbService::nextId),
                bundleNode.getNodeId(),
                bundleNode.getBundleId(),
                bundleNode.getRepoPath(),
                bundleNode.getOriginalFileDetails()
        };
    }

    public BundleTransactionStatus tryToCloseTransaction(String transactionPath) throws SQLException {
        BundleNameVersionResolver bundleNameVersion = new BundleNameVersionResolver(transactionPath);
        return tryToSetBundleStatus(bundleNameVersion.getBundleName(),
                bundleNameVersion.getBundleVersion(),
                BundleType.TARGET,
                BundleTransactionStatus.COMPLETE);
    }

    @NonNull
    public BundleTransactionStatus tryToSetBundleStatus(@NonNull String bundleName,
            @NonNull String bundleVersion,
            @NonNull BundleType type,
            @NonNull BundleTransactionStatus status) throws SQLException {

        String sqlQuery = "UPDATE " + ARTIFACT_BUNDLES_TABLE +
                " SET status = ? WHERE name = ? AND version = ? AND type = ? AND status NOT IN (#)";

        boolean updated = jdbcHelper.executeUpdate(sqlQuery, status.name(), bundleName, bundleVersion, type.name(),
                BundleTransactionStatus.FINAL_STATES.stream().map(Enum::name).toList()) > 0;

        if (updated) {
            return status;
        } else {
            DBArtifactsBundle artifactsBundle = getArtifactsBundle(bundleName, bundleVersion, type);
            if (artifactsBundle == null) {
                String errorMessage = ("Failed updating Release Bundle %s/%s with type %s. " +
                        "Release Bundle does not exist").formatted(bundleName, bundleVersion, type);
                throw new IllegalStateException(errorMessage);
            } else {
                log.warn("Failed updating Release Bundle {}/{} with type {}. Tried to change the status from {} to {}",
                        bundleName, bundleVersion, type, artifactsBundle.getStatus(), status);
            }
            return artifactsBundle.getStatus();
        }
    }

    public int setKeepBundle(@NonNull String bundleName, @NonNull String bundleVersion, boolean keep)
            throws SQLException {

        int updatedKeep = keep ? 1 : 0;
        String query = "UPDATE " + ARTIFACT_BUNDLES_TABLE + " SET keep = ? WHERE name = ? AND version = ? AND type = ?";

        return jdbcHelper.executeUpdate(query, updatedKeep, bundleName, bundleVersion, BundleType.TARGET.name());
    }

    public boolean isKeepBundle(@NonNull String bundleName, @NonNull String bundleVersion) throws SQLException {
        String query = "SELECT keep FROM " + ARTIFACT_BUNDLES_TABLE + " WHERE name = ? AND version = ? AND type = ?";

        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleName, bundleVersion,
                BundleType.TARGET.name())) {
            if (resultSet.next()) {
                int keepInt = resultSet.getInt(KEEP);
                return keepInt == 1;
            } else {
                throw new SQLException("Bundle " + bundleName + "/" + bundleVersion + " does not exists");
            }
        }
    }

    @Nullable
    public DBArtifactsBundle getArtifactsBundle(String bundleName, String bundleVersion, BundleType type)
            throws SQLException {
        DBArtifactsBundle artifactsBundle = null;
        String query = "SELECT * FROM " + ARTIFACT_BUNDLES_TABLE + " WHERE name = ? AND version = ? AND type = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleName, bundleVersion, type.name())) {
            if (resultSet.next()) {
                artifactsBundle = artifactsBundleFromResultSet(resultSet, "*");
            }
        }
        return artifactsBundle;
    }

    public boolean isReleaseBundleExist(@NonNull String releaseBundleName) throws SQLException {
        String query = "SELECT COUNT(*) FROM  " + ARTIFACT_BUNDLES_TABLE + " WHERE name = ?";
        return jdbcHelper.executeSelectCount(query, releaseBundleName) > 0;
    }

    @Nullable
    public DBArtifactsBundle getArtifactsBundle(long id) throws SQLException {
        try (ResultSet resultSet = jdbcHelper
                .executeSelect("SELECT * FROM " + ARTIFACT_BUNDLES_TABLE + " WHERE id = ?", id)) {
            if (resultSet.next()) {
                return artifactsBundleFromResultSet(resultSet, "*");
            }
        }
        return null;
    }

    public DBArtifactsBundle getArtifactsBundleStatus(String bundleName, String bundleVersion, BundleType bundleType)
            throws SQLException {
        String query = "SELECT status, storing_repo FROM " + ARTIFACT_BUNDLES_TABLE +
                " WHERE name = ? AND version = ? AND type = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleName, bundleVersion, bundleType.name())) {
            if (resultSet.next()) {
                return artifactsBundleFromResultSet(resultSet, STATUS, STORING_REPO);
            }
        }
        return null;
    }

    public List<DBArtifactsBundle> getArtifactsBundles(String bundleName, BundleType bundleType) throws SQLException {
        List<DBArtifactsBundle> artifactsBundles = Lists.newArrayList();
        String query =
                "SELECT id, name, version, status, date_created, storing_repo, keep FROM " + ARTIFACT_BUNDLES_TABLE +
                        " WHERE name = ? AND type = ? ORDER BY date_created DESC, version DESC";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleName, bundleType.name())) {
            while (resultSet.next()) {
                artifactsBundles.add(
                        artifactsBundleFromResultSet(
                                resultSet, ID, NAME, VERSION, STATUS, DATE_CREATED, STORING_REPO, KEEP
                        ));
            }
        }
        return artifactsBundles;
    }

    public List<DBArtifactsBundle> getAllArtifactsBundles(BundleType bundleType) throws SQLException {
        List<DBArtifactsBundle> artifactsBundles = Lists.newLinkedList();
        String query = "SELECT name, version, status, date_created, date_distributed, storing_repo, keep FROM " +
                ARTIFACT_BUNDLES_TABLE +
                " WHERE type = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleType.name())) {
            while (resultSet.next()) {
                artifactsBundles
                        .add(artifactsBundleFromResultSet(resultSet, NAME, VERSION, STATUS, DATE_CREATED,
                                DATE_DISTRIBUTED, STORING_REPO, KEEP));
            }
        }
        return artifactsBundles;
    }

    /**
     * @return The latest version (according to creation date) of every completed bundle. In case there is more than one
     * latest version, returns the one with highest alphabetic order
     */
    public List<DBArtifactsBundle> getCompletedBundlesLastVersion(BundleType bundleType) throws SQLException {
        List<DBArtifactsBundle> artifactsBundles = Lists.newLinkedList();
        String query = "SELECT a1.name, max(a1.version) as version, a1.date_created FROM " + ARTIFACT_BUNDLES_TABLE +
                " a1 join" +
                " ( SELECT name, max(date_created) as date_created FROM " + ARTIFACT_BUNDLES_TABLE +
                "   WHERE status = '" + BundleTransactionStatus.COMPLETE.name() + "' AND type = ? GROUP BY name" +
                " ) a2" +
                " on a1.name = a2.name AND a1.date_created = a2.date_created" +
                " WHERE status = '" + BundleTransactionStatus.COMPLETE.name() +
                "' AND type = ? GROUP BY a1.name, a1.date_created";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleType.name(), bundleType.name())) {
            while (resultSet.next()) {
                artifactsBundles.add(artifactsBundleFromResultSet(resultSet, NAME, VERSION, DATE_CREATED));
            }
        }
        return artifactsBundles;
    }

    /**
     * @param bundleType - type of bundles to fetch, or `null` for all types
     * @return All release-bundles with {@link BundleTransactionStatus#COMPLETE} status, regardless of their type
     * (target/source)
     */
    public List<DBBundleResult> getAllCompletedBundles(@Nullable BundleType bundleType) throws SQLException {
        List<DBBundleResult> bundles = Lists.newArrayList();
        String typeQuery = "";
        if (bundleType != null) {
            typeQuery = " AND type = '" + bundleType + "' ";
        }
        String query = "SELECT name, storing_repo " +
                "FROM " + ARTIFACT_BUNDLES_TABLE + " " +
                "WHERE status = '" + BundleTransactionStatus.COMPLETE.name() + "' " +
                typeQuery +
                "GROUP BY name, storing_repo ORDER BY name";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)) {
            while (resultSet.next()) {
                bundles.add(populateDbBundleResult(resultSet));
            }
        }
        return bundles;
    }

    private DBBundleResult populateDbBundleResult(ResultSet resultSet) throws SQLException {
        return new DBBundleResult(resultSet.getString(NAME), resultSet.getString(STORING_REPO));
    }

    /**
     * @return The count of latest version (according to creation date) of every completed bundle.
     * In case there is more than one latest version, returns the one with highest alphabetic order
     */
    public int getFilteredBundlesCount(ReleaseBundleSearchFilter filter) throws SQLException {
        String from = ARTIFACT_BUNDLES_TABLE +
                " a1 join " +
                " ( SELECT name, max(date_created) as date_created FROM " + ARTIFACT_BUNDLES_TABLE +
                "   WHERE status = '" + BundleTransactionStatus.COMPLETE.name() + "'  AND type = ? GROUP BY name " +
                " ) a2 " +
                " on a1.name = a2.name AND a1.date_created = a2.date_created ";

        String where = " status = '" + BundleTransactionStatus.COMPLETE.name() + "' " +
                " AND type = ? " + addToWhereStatement(filter);

        String query = new ArtifactoryQueryWriter()
                .select("COUNT(*)")
                .from(from)
                .where(where)
                .build();

        Object[] params = setQueryParams(filter).toArray();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, params)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }

    /**
     * @return The latest version (according to creation date) of every completed bundle. In case there is more than one
     * latest version, returns the one with highest alphabetic order
     */
    public List<DBArtifactsBundle> getFilteredBundlesLastVersion(ReleaseBundleSearchFilter filter) throws SQLException {
        List<DBArtifactsBundle> artifactsBundles = Lists.newLinkedList();
        String select = " a1.name, max(a1.version) as version, a1.date_created , a1.storing_repo, a1.keep ";

        String from = ARTIFACT_BUNDLES_TABLE +
                " a1 join " +
                " ( SELECT name, max(date_created) as date_created FROM " + ARTIFACT_BUNDLES_TABLE +
                "   WHERE status = '" + BundleTransactionStatus.COMPLETE.name() + "'  AND type = ? GROUP BY name " +
                " ) a2 " +
                " on a1.name = a2.name AND a1.date_created = a2.date_created ";

        String where = " status = '" + BundleTransactionStatus.COMPLETE.name() + "' " +
                " AND type = ? " + addToWhereStatement(filter);
        String groupBy = " a1.name, a1.date_created , a1.storing_repo, a1.keep ";
        String orderby = addOrderBy(filter);

        String query = new ArtifactoryQueryWriter()
                .select(select)
                .from(from)
                .where(where)
                .groupBy(groupBy)
                .orderBy(orderby)
                .limit(filter.getDaoLimit())
                .offset(filter.getOffset())
                .build();

        Object[] params = setQueryParams(filter).toArray();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, params)) {
            while (resultSet.next()) {
                artifactsBundles.add(artifactsBundleFromResultSet(resultSet, NAME, VERSION, DATE_CREATED,
                        STORING_REPO, KEEP));
            }
        }
        return artifactsBundles;
    }

    /**
     * @return the source service id of a release bundle {bundleName}/{bundleVersion} and bundle type TARGET only
     * (bundle type SOURCE should be null)
     * @throws SQLException
     */
    @Nullable
    public String getSourceServiceId(@NonNull String bundleName, @NonNull String bundleVersion) throws SQLException {
        String query = "SELECT source_service_id FROM " + ARTIFACT_BUNDLES_TABLE
                + " WHERE name = ? AND version = ? AND type = ?";

        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleName, bundleVersion,
                BundleType.TARGET.name())) {
            if (resultSet.next()) {
                return resultSet.getString(SOURCE_SERVICE_ID);
            } else {
                return null;
            }
        }
    }

    private ArrayList<Object> setQueryParams(ReleaseBundleSearchFilter filter) {
        ArrayList<Object> params = Lists.newArrayList();
        params.add(filter.getBundleType().name());
        params.add(filter.getBundleType().name());
        String name = filter.getName();
        if (StringUtils.isNotBlank(name)) {
            if (name.contains("*")) {
                name = name.replace("*", "%");
            }
            if (name.contains("?")) {
                name = name.replace("?", "_");
            }
            params.add(name.toLowerCase() + "%");
        }
        if (filter.getAfter() != 0) {
            params.add(filter.getAfter());
        }
        if (filter.getBefore() != 0) {
            params.add(filter.getBefore());
        }
        return params;
    }

    private String addToWhereStatement(ReleaseBundleSearchFilter filter) {
        String query = " ";
        if (StringUtils.isNotBlank(filter.getName())) {
            query += " AND lower(a1.name) like ? ";
        }
        if (filter.getAfter() != 0) {
            query += " AND a1.date_created > ? ";

        }
        if (filter.getBefore() != 0) {
            query += " AND a1.date_created < ? ";
        }
        return query;
    }

    private String addOrderBy(ReleaseBundleSearchFilter filter) {
        String query = " ";
        String orderBy = filter.getOrderBy();
        if (!Strings.isEmpty(orderBy)) {
            String[] orderColumns = filter.getOrderBy().split("[,]");
            if (orderColumns.length > 0) {
                orderBy = Arrays.stream(orderColumns)
                        .map(StringUtils::deleteWhitespace)
                        .map(ReleaseBundleSearchFilter.OrderByColumn::forValue)
                        .collect(Collectors.joining(", "));
            }
        }
        query += StringUtils.isNotBlank(orderBy) ? orderBy + " " : " date_created ";
        query += StringUtils.isNotBlank(filter.getDirection()) ?
                ReleaseBundleSearchFilter.Direction.forValue(filter.getDirection()) + " " : " desc ";
        return query;
    }

    public List<BundleNode> getAllBundleNodes() throws SQLException {
        List<BundleNode> bundleNodes = Lists.newLinkedList();
        String query = "SELECT * FROM " + BUNDLE_FILES_TABLE;
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)) {
            while (resultSet.next()) {
                bundleNodes.add(bundleNodeFromResultSet(resultSet));
            }
        }
        return bundleNodes;
    }

    @NonNull
    public List<BundleNode> getBundleNodes(long bundleId) throws SQLException {
        List<BundleNode> bundleNodes = Lists.newLinkedList();
        String query = "SELECT * FROM " + BUNDLE_FILES_TABLE + " WHERE bundle_id = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, bundleId)) {
            while (resultSet.next()) {
                bundleNodes.add(bundleNodeFromResultSet(resultSet));
            }
        }
        return bundleNodes;
    }

    public List<DBArtifactsBundle> getRelatedArtifactsBundles(String path, boolean isFile, long limit)
            throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select("ab.id, ab.name, ab.version")
                .from(ARTIFACT_BUNDLES_TABLE + " ab ")
                .innerJoin(BUNDLE_FILES_TABLE + " bf ", "ab.id = bf.bundle_id")
                .where(isFile ? "bf.repo_path = ?" : "bf.repo_path like ?")
                .groupBy("ab.id, ab.name, ab.version")
                .orderBy(VERSION)
                .limit(limit)
                .build();
        String pathParameter = isFile ? path : appendTrailingSlash(path) + "%";

        List<DBArtifactsBundle> results = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, pathParameter)) {
            while (resultSet.next()) {
                results.add(artifactsBundleFromResultSet(resultSet, ID, NAME, VERSION));
            }
        }
        return results;
    }

    private String appendTrailingSlash(String in) {
        if (in.endsWith("/")) {
            return in;
        }
        return in + "/";
    }

    @Nonnull
    public List<Long> getExpiredBundlesIds(Long cleanupPeriod) throws SQLException {
        List<Long> ids = new LinkedList<>();
        long expiryDate = ClockUtils.epochMillis() - TimeUnit.HOURS.toMillis(cleanupPeriod);
        try (ResultSet resultSet = jdbcHelper.executeSelect(EXPIRED_BUNDLES_QUERY, expiryDate)) {
            while (resultSet.next()) {
                ids.add(resultSet.getLong(ID));
            }
        }
        return ids;
    }

    public String getStoringRepo(String name, String version, BundleType type) throws SQLException {
        String query =
                "SELECT storing_repo FROM " + ARTIFACT_BUNDLES_TABLE + " WHERE name = ? AND version = ? AND type = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, name, version, type.name())) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
    }

    public List<Long> getAllBundlesRelatedToNode(long nodeId) throws SQLException {
        String query = "SELECT bundle_id FROM " + BUNDLE_FILES_TABLE + " WHERE node_id = ? ";
        List<Long> bundleIds = Lists.newArrayList();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, nodeId)) {
            while (resultSet.next()) {
                bundleIds.add(resultSet.getLong("bundle_id"));
            }
        }
        return bundleIds;
    }

    public int updateArtifactsBundleCreationDate(Long id) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_BUNDLES_CREATION_DATE_QUERY, ClockUtils.epochMillis(), id);
    }

    private DBArtifactsBundle artifactsBundleFromResultSet(ResultSet resultSet, String... columns) throws SQLException {
        if ("*".equals(columns[0])) {
            return artifactsBundleFromResultSet(resultSet, ID, NAME, VERSION, STATUS, DATE_CREATED,
                    DATE_DISTRIBUTED, SIGNATURE, TYPE, STORING_REPO, KEEP, SOURCE_SERVICE_ID);
        }
        DBArtifactsBundle artifactsBundle = new DBArtifactsBundle();
        for (String column : columns) {
            switch (column) {
                case ID:
                    artifactsBundle.setId(resultSet.getLong(column));
                    break;
                case NAME:
                    artifactsBundle.setName(resultSet.getString(column));
                    break;
                case VERSION:
                    artifactsBundle.setVersion(resultSet.getString(column));
                    break;
                case STATUS:
                    artifactsBundle.setStatus(BundleTransactionStatus.valueOf(resultSet.getString(column)));
                    break;
                case DATE_CREATED:
                    artifactsBundle.setDateCreated(new DateTime(resultSet.getLong(column)));
                    break;
                case DATE_DISTRIBUTED:
                    long dateDistributed = resultSet.getLong(column);
                    artifactsBundle.setDateDistributed(dateDistributed != 0 ? new DateTime(dateDistributed) : null);
                    break;
                case SIGNATURE:
                    artifactsBundle.setSignature(resultSet.getString(column));
                    break;
                case TYPE:
                    String type = resultSet.getString(column);
                    artifactsBundle.setType(type != null ? BundleType.valueOf(type) : BundleType.TARGET);
                    break;
                case STORING_REPO:
                    artifactsBundle.setStoringRepo(resultSet.getString(column));
                    break;
                case KEEP:
                    int keepInt = resultSet.getInt(column);
                    artifactsBundle.setKeep(keepInt != 0);
                    break;
                case SOURCE_SERVICE_ID:
                    artifactsBundle.setSourceServiceId(resultSet.getString(column));
                    break;
                default:
            }
        }
        return artifactsBundle;
    }

    private BundleNode bundleNodeFromResultSet(ResultSet resultSet) throws SQLException {
        BundleNode bundleNode = new BundleNode();
        bundleNode.setId(resultSet.getLong(1));
        bundleNode.setNodeId(resultSet.getLong(2));
        bundleNode.setBundleId(resultSet.getLong(3));
        bundleNode.setRepoPath(resultSet.getString(4));
        bundleNode.setOriginalFileDetails(resultSet.getString(5));
        return bundleNode;
    }

    /**
     * Get original bundle node component details json of the source artifact
     *
     * @param repoPathOfBundleNode path of the artifact in the release bundle repo
     * @return json string representing ComponentDetails model for original artifacts details
     */
    public String getComponentDetails(String repoPathOfBundleNode) throws SQLException {
        String query = "SELECT original_component_details FROM " + BUNDLE_FILES_TABLE + " WHERE repo_path = ?";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, repoPathOfBundleNode)) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
            return null;
        }
    }

    @NonNull
    public Set<RepoPath> getOrphanRBVRepoPathsFromIntransit() throws SQLException {
        // get all release bundle versions folders from NODES with repository _intransit,
        // which are not in artifact_bundles table
        String query = "SELECT node_path, node_name FROM nodes " +
                "LEFT JOIN artifact_bundles ON " +
                "(nodes.node_path = artifact_bundles.name AND nodes.node_name = artifact_bundles.version) " +
                "WHERE repo = ? AND artifact_bundles.id IS NULL AND depth = 2 AND node_type = 0";
        Set<RepoPath> repoPathsToDelete = new HashSet<>();
        long start = System.currentTimeMillis();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, IN_TRANSIT_REPO_KEY)) {
            while (resultSet.next()) {
                repoPathsToDelete.add(RepoPathFactory.create(String.join("/", IN_TRANSIT_REPO_KEY,
                        resultSet.getString(1), resultSet.getString(2))));
            }
        }
        log.debug("finding orphan release bundle version folder took {} ms", System.currentTimeMillis() - start);
        return repoPathsToDelete;
    }

    @NonNull
    public Set<RepoPath> getUnidentifiedRepoPathsOnInTransit() throws SQLException {
        String query = "SELECT node_path, node_name FROM nodes " +
                "LEFT JOIN artifact_bundles ON " +
                "(nodes.node_path = '.' AND nodes.node_name = artifact_bundles.name) " +
                "WHERE repo = ? AND artifact_bundles.id IS NULL AND depth = 1";
        Set<RepoPath> repoPaths = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, IN_TRANSIT_REPO_KEY)) {
            while (resultSet.next()) {
                String nodePath = resultSet.getString(1);
                String nodeName = resultSet.getString(2);
                String repoPath = nodePath.equals(".") ? nodeName : String.join("/", nodePath, nodeName);
                repoPaths.add(RepoPathFactory.create(String.join("/", IN_TRANSIT_REPO_KEY, repoPath)));
            }
        }
        return repoPaths;
    }
}
