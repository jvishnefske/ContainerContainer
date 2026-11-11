package org.artifactory.storage.db.init;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.jfrog.storage.util.DbUtils.close;

/**
 * @author Uriah Levy
 */
@Slf4j
@UtilityClass
class DbConnectionUtils {

    static Connection retryGetConnection(JdbcHelper jdbcHelper) throws SQLException {
        for (int retriesLeft = 0; retriesLeft < 2; retriesLeft++) {
            try {
                return jdbcHelper.getDataSource().getConnection();
            } catch (SQLException e) {
                log.error("Failed to connect to database: ", e);
                log.warn("Retrying database connection in 2 seconds...");
            }

            // Sleep between retries
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                log.warn("Sleep interrupted while getting database connection");
                log.debug("", e);
                Thread.currentThread().interrupt();
            }
        }
        // Final retry
        return jdbcHelper.getDataSource().getConnection();
    }

    static void checkMySqlMinVersion(Connection con) {
        double mysqlMinVersion = 5.5;
        log.debug("Checking MySQL version compatibility");
        ResultSet rs = null;
        try {
            try (PreparedStatement preparedStatement = con.prepareStatement("SELECT VERSION();")) {
                rs = preparedStatement.executeQuery();
                if (rs.next()) {
                    String versionString = rs.getString(1);
                    int i = StringUtils.ordinalIndexOf(versionString, ".", 2);
                    if (i == -1) {
                        i = versionString.length();
                    }
                    Double mysqlVersion = Double.valueOf(versionString.substring(0, i));
                    if (mysqlMinVersion > mysqlVersion) {
                        log.error("Unsupported MySQL version found [{}]. " +
                                        "Minimum version required is {}. Please follow the requirements on the wiki " +
                                        "page.",
                                versionString, mysqlMinVersion);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not determine MySQL version due to an exception", e);
        } finally {
            close(rs);
        }
    }

    static void checkDefaultDbProperties(final ArtifactoryDbProperties dbProps) {
        log.debug("Checking default database properties ...");
        if (dbProps.getDbType() == DbType.POSTGRESQL) {
            log.debug("Database type is PostgreSQL nothing to do ...");
            return;
        }

        if (!dbProps.allowNonPgFlag().orElse(false)) {
            throw new IllegalStateException(
                    "Cannot start the application with a database other than PostgreSQL. For more information, see " +
                            "JFrog documentation"
            );
        }
    }
}
