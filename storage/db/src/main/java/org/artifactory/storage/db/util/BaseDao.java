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

package org.artifactory.storage.db.util;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;

import java.sql.SQLException;

/**
 * Base class for the data access objects.
 *
 * @author Yossi Shaul
 */
@Slf4j
public class BaseDao {

    protected final ArtifactoryJdbcHelper jdbcHelper;

    public BaseDao(ArtifactoryJdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    public static Long nullIfZero(long id) {
        return (id == 0L) ? null : id;
    }

    // TODO [by FSI] : Looks useless since JDBS doing it no?
    public static long zeroIfNull(Long id) {
        return (id == null) ? 0L : id;
    }

    // TODO [by FSI] : Looks useless since JDBS doing it no?
    public static int zeroIfNull(Integer id) {
        return (id == null) ? 0 : id;
    }

    public static Long nullIfZeroOrNeg(long id) {
        return (id <= 0L) ? null : id;
    }

    public static Integer nullIfZeroOrNeg(int id) {
        return (id <= 0L) ? null : id;
    }

    public static String emptyIfNullOrDot(String path) {
        return (path == null || path.length() == 0
                || (path.length() == 1 && path.charAt(0) == '.')) ? "" : path;
    }

    public static String dotIfNullOrEmpty(String path) {
        return (path == null || path.length() == 0) ? "." : path;
    }

    public static String emptyIfNull(String path) {
        return path == null ? "" : path;
    }

    public static String nullIfEmpty(String path) {
        return (path == null || path.length() == 0) ? null : path;
    }

    public static String enumToString(Enum versionType) {
        if (versionType != null) {
            return versionType.name();
        }
        return null;
    }

    /**
     * Turns boolean to byte value (0, 1) for cross database compatibility.
     *
     * @param bool The boolean value
     * @return 0 if boolean is false, 1 if true
     */
    protected byte booleanAsByte(boolean bool) {
        return (byte) (bool ? 1 : 0);
    }

    /**
     * Get DB max size of indexed value.
     * By default:
     * <p>
     * dbMsSqlPropertyValueMaxSize for MSSQL DB, dbPostgresPropertyValueMaxSize for PSQL DB
     * defaultMaxValueSize otherwise.
     *
     * @return max size value
     */
    public int getDbIndexedValueMaxSize(int defaultMaxValueSize) {
        if (ContextHelper.get() == null) {
            return defaultMaxValueSize;
        }
        ArtifactoryDbProperties artifactoryDbProperties = ContextHelper.get()
                .beanForType(ArtifactoryDbProperties.class);

        // artifactoryDbProperties sometimes is null when running in a test environment
        if (artifactoryDbProperties == null || artifactoryDbProperties.getDbType() == null) {
            return defaultMaxValueSize;
        }

        return switch (artifactoryDbProperties.getDbType()) {
            case MYSQL -> Math.min(ConstantValues.dbMySqlPropertyValueMaxSize.getInt(), defaultMaxValueSize);
            case MSSQL -> Math.min(ConstantValues.dbMsSqlPropertyValueMaxSize.getInt(), defaultMaxValueSize);
            case POSTGRESQL -> Math.min(ConstantValues.dbPostgresPropertyValueMaxSize.getInt(), defaultMaxValueSize);
            case ORACLE -> Math.min(ConstantValues.dbOraclePropertyValueMaxSize.getInt(), defaultMaxValueSize);
            default -> defaultMaxValueSize;
        };
    }

    /**
     * Checks whether the given exception is a unique constraint / duplicate
     * key violation. Walks the cause chain to handle wrapped exceptions.
     * <ul>
     *   <li>SQLState 23505: unique violation (PostgreSQL, H2, Derby, DB2)</li>
     *   <li>MySQL error 1062: ER_DUP_ENTRY</li>
     *   <li>Oracle error 1: ORA-00001</li>
     *   <li>SQL Server error 2627: unique constraint violation</li>
     *   <li>SQL Server error 2601: unique index violation</li>
     * </ul>
     */
    public static boolean isUniqueConstraintViolation(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (!(t instanceof SQLException sqlEx)) {
                continue;
            }
            String state = sqlEx.getSQLState();
            if ("23505".equals(state)) {
                return true;
            }
            int code = sqlEx.getErrorCode();
            if (code == 1062 || code == 1 || code == 2627 || code == 2601) {
                return true;
            }
        }
        return false;
    }

}
