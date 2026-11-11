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

package org.artifactory.storage.db.fs.dao;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.jfrog.storage.DbType.MSSQL;

/**
 * A data access object for DB-based blocking lock (utilizes DB transaction on distributed_blocking_locks table).
 * NOTE: This is an expensive lock! Should be used only for special cases (For example: HA initialization).
 *
 * @author Mor Merhav
 */
@Slf4j
@NotThreadSafe
public class BlockingLockDao {

    public static final String TABLE_DISTRIBUTED_BLOCKING_LOCKS = "distributed_blocking_locks";

    private static final String INSERT_LOCK_ENTRY = "INSERT INTO %s (lock_key) VALUES (?)".formatted(
            TABLE_DISTRIBUTED_BLOCKING_LOCKS);
    private static final String SELECT_LOCK_ENTRY = "SELECT lock_key FROM %s WHERE lock_key=?".formatted(
            TABLE_DISTRIBUTED_BLOCKING_LOCKS);
    private static final String SELECT_FOR_UPDATE =
            "SELECT 1 FROM %s WHERE lock_key=? FOR UPDATE".formatted(TABLE_DISTRIBUTED_BLOCKING_LOCKS);
    // The HOLDLOCK hint politely asks SQL Server to hold the lock until you commit the transaction.
    // The ROWLOCK hint politely asks SQL Server to lock only this row rather than issuing a page or table lock.
    private static final String SELECT_FOR_UPDATE_MSSQL =
            "SELECT 1 FROM %s WITH (HOLDLOCK, ROWLOCK) WHERE lock_key=?".formatted(TABLE_DISTRIBUTED_BLOCKING_LOCKS);
    private static final int VALID_CONN_CHECK_TIMEOUT_SECONDS = 10;

    private final String lockKey;
    @Getter
    private final ArtifactoryDbProperties dbProperties;
    private ArtifactoryJdbcHelper jdbcHelper;
    private Connection connection;

    public BlockingLockDao(String lockKey, ArtifactoryDbProperties dbProperties) {
        validate(lockKey);
        validate(dbProperties);
        this.lockKey = lockKey;
        this.dbProperties = dbProperties;
    }

    /**
     * Acquires a blocking lock on the specified lockKey. This method will block until the lock is acquired.
     * It creates a lock entry in the table if it does not exist, and then attempts to acquire a lock.
     * Every call to this method will create a new connection to the database.
     * The connection is closed either if this method is called again or when the lock is released by calling
     * {@link #releaseLock()}.
     *
     * @throws SQLException if an error occurs while acquiring the lock
     */
    public void lockBlocking() throws SQLException {
        jdbcHelper = getJdbcHelper();
        createLockEntryIfNotExists();
        connection = jdbcHelper.getDataSource().getConnection();
        connection.setAutoCommit(false);
        PreparedStatement preparedStatement = createPreparedStatement();
        preparedStatement.setString(1, lockKey);
        ResultSet resultSet = preparedStatement.executeQuery();
        resultSet.close();
    }

    /**
     * Releases the blocking lock acquired by {@link #lockBlocking()} and closes the connection.
     * Does nothing in case the lock was never acquired.
     * @throws SQLException if an error occurs while releasing the lock
     */
    public void releaseLock() throws SQLException {
        if (connection == null) {
            log.warn("Attempted to release HA blocking lock: '{}', but it was never acquired.", lockKey);
            return;
        }
        try {
            if (isValid()) {
                connection.commit();
            }
        } finally {
            close();
        }
    }

    /**
     * Checks if the current connection is valid.
     * @return true if the connection is valid, false otherwise
     * @throws SQLException if an error occurs while checking the connection validity
     */
    public boolean isValid() throws SQLException {
        return connection != null && connection.isValid(VALID_CONN_CHECK_TIMEOUT_SECONDS);
    }

    /**
     * Closes the JDBC helper if needed
     * Should be called directly only in case of error while attempting to acquire the lock.
     */
    public void close() {
        if (jdbcHelper != null) {
            try {
                jdbcHelper.close();
            } catch (IOException e) {
                log.error("Failed to close JDBC Helper", e);
            }
        }
    }

    private void createLockEntryIfNotExists()
            throws SQLException {
        try {
            jdbcHelper.executeUpdate(INSERT_LOCK_ENTRY, lockKey);
        } catch (SQLException e) {
            if (!exists()) {
                throw e;
            }
        }
    }

    private boolean exists() throws SQLException {
        ResultSet resultSet = jdbcHelper.executeSelect(SELECT_LOCK_ENTRY, lockKey);
        boolean exists = resultSet.next();
        resultSet.close();
        return exists;
    }

    private PreparedStatement createPreparedStatement() throws SQLException {
        PreparedStatement preparedStatement;
        if (isMSSqlDB()) {
            preparedStatement = connection.prepareStatement(SELECT_FOR_UPDATE_MSSQL);
        } else {
            preparedStatement = connection.prepareStatement(SELECT_FOR_UPDATE);
        }
        return preparedStatement;
    }

    private static boolean isMSSqlDB() {
        return ArtifactoryHome.get().getDBProperties().getDbType() == MSSQL;
    }

    private ArtifactoryJdbcHelper getJdbcHelper() {
        close();
        return createJdbcHelper();
    }

    @VisibleForTesting
    ArtifactoryJdbcHelper createJdbcHelper() {
        return new ArtifactoryJdbcHelper(CommonDataSourceFactory.createDataSource(dbProperties, true));
    }

    private void validate(String lockKey) {
        if (StringUtils.isBlank(lockKey)) {
            throw new IllegalArgumentException("lockKey cannot be null or empty");
        }
    }

    private void validate(ArtifactoryDbProperties dbProperties) {
        if (dbProperties == null) {
            throw new IllegalArgumentException("dbProperties cannot be null");
        }
    }
}