package org.artifactory.common.config.db;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbUtils;

import jakarta.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

import static org.jfrog.storage.util.DaoUtils.zeroIfNull;

/**
 * @author Amir Avidan
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DbVersionUtil {

    public static final String DB_PROPERTIES_TABLE = "db_properties";
    public static final String INSTALLATION_DATE_COLUMN = "installation_date";
    public static final String ARTIFACTORY_SERVICE_VERSION_COLUMN = "artifactory_version";
    public static final String ARTIFACTORY_REVISION_COLUMN = "artifactory_revision";
    public static final String ARTIFACTORY_RELEASE_COLUMN = "artifactory_release";
    public static final String ARTIFACTORY_PRODUCT_VERSION_COLUMN = "artifactory_product_version";

    public static boolean createVersion(JdbcHelper jdbcHelper, DbVersionInfo dbVersionInfo) throws SQLException {
        return jdbcHelper.executeUpdate("INSERT INTO " + DB_PROPERTIES_TABLE +
                        " (installation_date, artifactory_version, artifactory_revision, artifactory_release, " +
                        "artifactory_product_version) VALUES(?, ?, ?, ?, ?)",
                dbVersionInfo.getInstallationDate(), dbVersionInfo.getArtifactoryVersion(),
                nullIfZeroOrNeg(dbVersionInfo.getArtifactoryRevision()),
                nullIfZeroOrNeg(dbVersionInfo.getArtifactoryRelease()),
                dbVersionInfo.getArtifactoryProductVersion()) == 1;
    }

    public static boolean deleteVersion(JdbcHelper jdbcHelper, DbVersionInfo dbVersionInfo) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM db_properties WHERE installation_date = ?" +
                        " AND artifactory_version = ?",
                dbVersionInfo.getInstallationDate(), dbVersionInfo.getArtifactoryVersion()) == 1;
    }

    public static boolean isDbPropertiesTableExists(JdbcHelper jdbcHelper, DbType dbType)
            throws SQLException {
        return DbUtils.tableExists(jdbcHelper, dbType, DB_PROPERTIES_TABLE);
    }

    public static boolean isDbPropertiesEmpty(JdbcHelper jdbcHelper) {
        try {
            return jdbcHelper.executeSelectLong("SELECT COUNT(*) FROM " + DB_PROPERTIES_TABLE) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check if db_properties table is empty", e);
        }
    }

    @Nonnull
    public static List<DbVersionInfo> getVersions(JdbcHelper jdbcHelper) throws SQLException {
        List<DbVersionInfo> result = Lists.newArrayList();
        try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT * FROM " + DB_PROPERTIES_TABLE)) {
            while (resultSet.next()) {
                result.add(new DbVersionInfo(resultSet.getLong(INSTALLATION_DATE_COLUMN),
                        resultSet.getString(ARTIFACTORY_SERVICE_VERSION_COLUMN),
                        zeroIfNull(resultSet.getInt(ARTIFACTORY_REVISION_COLUMN)),
                        zeroIfNull(resultSet.getLong(ARTIFACTORY_RELEASE_COLUMN)),
                        resultSet.getString(ARTIFACTORY_PRODUCT_VERSION_COLUMN)));
            }
            return result;
        }
    }

    public static DbVersionInfo getVersion(JdbcHelper jdbcHelper) {
        List<DbVersionInfo> dbProperties = Lists.newArrayList();
        try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT * FROM " + DB_PROPERTIES_TABLE)) {
            while (resultSet.next()) {
                dbProperties.add(getDbServiceVersionFromResult(resultSet));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve version information from database:" + e.getMessage(),
                    e);
        }
        dbProperties.sort(Comparator.comparingLong(DbVersionInfo::getInstallationDate));
        return !dbProperties.isEmpty() ? dbProperties.get(dbProperties.size() - 1) : null;
    }

    private static DbVersionInfo getDbServiceVersionFromResult(ResultSet resultSet) throws SQLException {
        return new DbVersionInfo(resultSet.getLong(INSTALLATION_DATE_COLUMN),
                resultSet.getString(ARTIFACTORY_SERVICE_VERSION_COLUMN),
                zeroIfNull(resultSet.getInt(ARTIFACTORY_REVISION_COLUMN)),
                zeroIfNull(resultSet.getLong(ARTIFACTORY_RELEASE_COLUMN)), null);
    }

    private static Long nullIfZeroOrNeg(long id) {
        return (id <= 0L) ? null : id;
    }

    private static Integer nullIfZeroOrNeg(int id) {
        return (id <= 0L) ? null : id;
    }
}
