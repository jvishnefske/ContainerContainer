package org.artifactory.storage.db.fs.dao;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Spring-managed DAO for post-init rollback flows ({@code ConfigsServiceImpl}, {@code ConverterManagerImpl}).
 * Pre-Spring startup code should use {@link VersionHistoryDao} directly.
 */
@Repository
public class VersionConfigDao extends BaseDao {

    public static final String CONFIG_DATA_COLUMN = "config_data";
    public static final String LOG_CONFIG_DATA_COLUMN = "log_config_data";

    private static final String VERSION_HISTORY_TABLE = "version_history";
    private static final String DELETE_BY_VERSION_QUERY =
            "DELETE from " + VERSION_HISTORY_TABLE + " where version = ?";
    private static final String UPDATE_ACTIVE_FLAG =
            "UPDATE " + VERSION_HISTORY_TABLE + " SET active = 0 where version = ? ";

    private final VersionHistoryDao versionHistoryDao;

    public VersionConfigDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
        this.versionHistoryDao = new VersionHistoryDao(jdbcHelper, ArtifactoryHome.get());
    }

    public String getConfigDataByVersion(String version, String columnName) throws SQLException, IOException {
        return versionHistoryDao.getConfigDataByVersion(version, columnName);
    }

    public boolean update(String version) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_ACTIVE_FLAG, version) == 1;
    }

    public void deleteHistoryByVersion(String version) throws SQLException {
        jdbcHelper.executeUpdate(DELETE_BY_VERSION_QUERY, version);
    }
}
