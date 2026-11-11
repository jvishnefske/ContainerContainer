package org.artifactory.storage.db.fs.dao;

import org.apache.commons.io.IOUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.entity.VersionHistoryRecord;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbSchemaVersionLogDao;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.model.SchemaVersionLog;
import org.jfrog.storage.JdbcHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VersionHistoryDao {

    private final JdbcHelper jdbcHelper;
    private final DbSchemaVersionLogDao schemaVersionLogDao;
    private final ConfigHistoryDao configHistoryDao;
    private static final String VERSION_HISTORY_TABLE = "version_history";
    private static final String VERSION_HISTORY_TABLE_ALIAS = " vh";
    private static final String CONFIG_HISTORY_TABLE = "config_history";
    private static final String CONFIG_HISTORY_TABLE_ALIAS = " ch";
    private static final String ID_COLUMN = "id";
    private static final String VERSION_COLUMN = "version";
    private static final String CONFIG_VERSION_ID_COLUMN = "config_version_id";
    private static final String BACKWARD_COMPATIBLE_COLUMN = "backward_compatible";
    private static final String CREATED_COLUMN = "created";
    private static final String ACTIVE_COLUMN = "active";
    private static final String SELECT_CONFIG_BY_VERSION_QUERY =
            "SELECT * from " + CONFIG_HISTORY_TABLE + CONFIG_HISTORY_TABLE_ALIAS + " join " +
                    VERSION_HISTORY_TABLE + VERSION_HISTORY_TABLE_ALIAS + " on ch.id = vh.config_version_id " +
                    "where vh." + VERSION_COLUMN + " = ? AND vh." + ACTIVE_COLUMN + " = 1";

    private static final String COLUMNS = String.join(",", VERSION_COLUMN, CONFIG_VERSION_ID_COLUMN,
            BACKWARD_COMPATIBLE_COLUMN, CREATED_COLUMN);
    private static final String INSERT_QUERY = "INSERT INTO " + VERSION_HISTORY_TABLE + "(" + COLUMNS + ") "
            + "VALUES(?, ?, ?, ?)";
    private static final String SELECT_LATEST_QUERY = "SELECT * FROM " + VERSION_HISTORY_TABLE  +
            " where " + ACTIVE_COLUMN  + " = 1" +
            " order by " + ID_COLUMN + " DESC";
    private static final String UPDATE_ACTIVE_FLAG =
            "UPDATE " + VERSION_HISTORY_TABLE +
                    " SET " + ACTIVE_COLUMN + " = 0 where " + VERSION_COLUMN + " = ?";
    private static final String UPDATE_BACKWARD_COMPATIBLE_FLAG =
            "UPDATE " + VERSION_HISTORY_TABLE + " SET " + BACKWARD_COMPATIBLE_COLUMN + " = ? WHERE " +
                    VERSION_COLUMN + " = ? AND " + ACTIVE_COLUMN + " = 1";

    public VersionHistoryDao(JdbcHelper jdbcHelper, ArtifactoryHome home) {
        this.jdbcHelper = jdbcHelper;
        this.configHistoryDao = new ConfigHistoryDao(jdbcHelper);
        this.schemaVersionLogDao = new DbSchemaVersionLogDao(jdbcHelper, home.getDBProperties().getDbType());
    }

    public String getConfigDataByVersion(String version, String columnName) throws SQLException, IOException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_CONFIG_BY_VERSION_QUERY, version)) {
            if (resultSet.next()) {
                return IOUtils.toString(resultSet.getBinaryStream(columnName), UTF_8);
            }
            throw new SQLException("Can't get " + columnName + " for version " + version);
        }
    }

    public boolean insert(String version, String to) throws SQLException {
        int configId = configHistoryDao.getLatestId();
        return jdbcHelper.executeUpdate(INSERT_QUERY, version,
                configId, 1, System.currentTimeMillis()) == 1;
    }

    public boolean updateBackwardCompatible(String version, String to) throws SQLException {
        int backwardCompatible = nonBackwardCompatible(to) ? 0 : 1;
        return jdbcHelper.executeUpdate(UPDATE_BACKWARD_COMPATIBLE_FLAG, backwardCompatible, version) == 1;
    }

    public VersionHistoryRecord getLatestActive() throws SQLException{
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_LATEST_QUERY)) {
            // Note we can use ArtifactoryQueryWriter here because context is not fully initialized yet.
            List<VersionHistoryRecord> versionHistoryRecords = new ArrayList<>();
            while (resultSet.next()) {
                versionHistoryRecords.add(toRecord(resultSet));
            }
            versionHistoryRecords.sort(Comparator.comparingLong(VersionHistoryRecord::id));
            return !versionHistoryRecords.isEmpty() ?
                    versionHistoryRecords.getLast() : null;
        }
    }

    public boolean update(String version) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_ACTIVE_FLAG, version) == 1;
    }

    /**
     * Get all converters that run for version.
     * Version is non-backward compatible if at least one of converters is non-backward compatible
     */
    public boolean nonBackwardCompatible(String productVersion) throws SQLException {
        List<String> completedConverters = schemaVersionLogDao.getConvertersWithStatus(ConverterApplyStatus.COMPLETED)
                .stream()
                .filter(converter -> productVersion.equals(converter.getProductVersion()))
                .map(SchemaVersionLog::getVersion)
                .toList();
        return ArtifactoryDBVersion.getByVersions(completedConverters)
                .stream()
                .anyMatch(version -> !version.isBackwardCompatible());
    }

    // Used for testing
    public void deleteAll() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM " + VERSION_HISTORY_TABLE);
    }

    private VersionHistoryRecord toRecord(ResultSet resultSet) throws SQLException {
        return VersionHistoryRecord.builder()
                .id(resultSet.getInt(ID_COLUMN))
                .version(resultSet.getString(VERSION_COLUMN))
                .configVersionId(resultSet.getInt(CONFIG_VERSION_ID_COLUMN))
                .backwardCompatible(resultSet.getInt(BACKWARD_COMPATIBLE_COLUMN) == 1)
                .active(resultSet.getInt(ACTIVE_COLUMN) == 1)
                .build();
    }
}
