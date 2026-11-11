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

package org.artifactory.storage.db.dao.locks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.db.monitor.DbQueryMonitor;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.util.CollectionUtils;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.common.ConstantValues.dbLockConditionalInsertEnabled;

/**
 * @author gidis
 * @author Mor Merhav
 * @author Sergey Steinvil (refactored to use JdbcHelper)
 */
@Repository
@Slf4j
public class DbDistributeLocksDao {

    public static final String TABLE_DISTRIBUTED_LOCKS = "distributed_locks";

    private static final String ORACLE_CONDITIONAL_INSERT_STATEMENT =
            "MERGE INTO DISTRIBUTED_LOCKS " +
                    "USING (SELECT 1 FROM dual) " +
                    "ON (DISTRIBUTED_LOCKS.category = ? and DISTRIBUTED_LOCKS.lock_key = ?) " +
                    "    WHEN NOT MATCHED THEN " +
                    "        INSERT (category, lock_key, owner, owner_thread, owner_thread_name, acquire_time) " +
                    "        VALUES (?, ?, ?, ?, ?, ?)";

    private static final String POSTGRES_CONDITIONAL_INSERT_STATEMENT =
            "INSERT INTO distributed_locks VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING";

    private static final String NON_CONDITIONAL_INSERT_STATEMENT =
            "INSERT INTO distributed_locks VALUES(?,?,?,?,?,?)";

    private final JFrogDataSource uniqueLocksDataSource;
    private final ArtifactoryJdbcHelper jdbcHelper;
    private final DbType dbType;
    private final boolean conditionalInsertEnabled;

    @Autowired
    public DbDistributeLocksDao(@Qualifier("uniqueLockDataSource") JFrogDataSource uniqueLocksDataSource,
            DbProperties dbProperties,
            List<DbQueryMonitor> monitors) {
        this.uniqueLocksDataSource = uniqueLocksDataSource;
        this.dbType = dbProperties.getDbType();
        this.jdbcHelper = new ArtifactoryJdbcHelper(uniqueLocksDataSource, monitors);
        this.conditionalInsertEnabled = isConditionalInsertEnabled();
    }

    public boolean isLocked(String category, String key) throws SQLException {
        key = normalizeKey(key);

        try (ResultSet result = this.jdbcHelper.executeSelect(
                "select owner from distributed_locks where lock_key=? and category=?",
                normalizeKey(key),
                category
        )) {
            return result.next();
        }
    }

    @NonNull
    @SneakyThrows
    public List<LockInfo> getLocksByPrefix(String category, String lockPrefix, long limit) {
        QueryWriter queryWriter = new ArtifactoryQueryWriter();
        String sql = queryWriter
                .select("*")
                .from(TABLE_DISTRIBUTED_LOCKS)
                .where("category = ? AND lock_key LIKE ?")
                .limit(limit)
                .build();
        return new ArrayList<>(selectLocks(sql, category, lockPrefix + '%'));
    }

    public int lockingMapSize(String category) throws SQLException {
        return this.jdbcHelper.executeSelectCount("select count(*) from distributed_locks where category=?", category);
    }

    public Set<String> lockingMapKeySet(String category) throws SQLException {
        try (ResultSet rs = this.jdbcHelper.executeSelect(
                "select lock_key from distributed_locks where category=?", category)) {
            Set<String> result = Sets.newHashSet();
            while (rs.next()) {
                result.add(rs.getString(1));
            }
            return result;
        }
    }

    public boolean unlock(String category, String key, String owner) throws SQLException {
        key = normalizeKey(key);
        int affectedRows = this.jdbcHelper.executeUpdate(
                "delete from distributed_locks where lock_key=? and category=? and owner=?  and owner_thread=?",
                key, category, owner, Thread.currentThread().getId());
        return affectedRows > 0;
    }

    /**
     * Tries to acquire lock by conditional row insertion to DistributeLocks table.
     *
     * NOTE: this DOES NOT apply on Derby DB, as this DB isn't supported in HA.
     * meaning you will still get errors in Derby's DB log if insertion of raw failed.
     *
     * @param lockInfo with the lock info to insert to the DB table
     * @return true if succeeded to insert row, otherwise false
     */
    public boolean tryToAcquireLock(LockInfo lockInfo) throws SQLException {
        String key = normalizeKey(lockInfo.getKey());
        SqlQuery query = prepareInsertQuery(lockInfo, key);
        try {
            return this.jdbcHelper.executeUpdate(query.sql, query.params) == 1;
        } catch (SQLException e) {
            // Note: we cannot use log.atLevel() because integration tests use v1 version of
            // SL4J, which does not support this API.  See RTDEV-38340.
            if (conditionalInsertEnabled && !dbType.equals(DbType.ORACLE)) {
                log.error("Failed to insert lock row to the 'distributed_locks' table: {}. {}", lockInfo,
                        e.getMessage(), e);
            } else {
                log.debug("Failed to insert lock row to the 'distributed_locks' table: {}. {}", lockInfo,
                        e.getMessage(), e);
            }
            return false;
        }
    }

    public boolean deleteLockByCategoryAndKey(String category, String key) throws SQLException {
        return this.jdbcHelper.executeUpdate(
                "delete from distributed_locks where lock_key=? and category=?",
                normalizeKey(key), category
        ) == 1;
    }

    public Connection getLocksConnection() {
        try {
            return uniqueLocksDataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get connection from unique lock data source", e);
        }
    }

    @SneakyThrows
    public Set<LockInfo> getAllLocksByCategoryNotOwnedBy(Set<String> serverIds, String category) {
        if (CollectionUtils.isNullOrEmpty(serverIds)) {
            return Sets.newHashSet();
        }

        return selectLocks("SELECT * FROM distributed_locks WHERE owner NOT IN (#) AND category = ?",
                serverIds,
                category);
    }

    @SneakyThrows
    public Set<LockInfo> getAllLocksNotOwnedByAndNotContainedInCategories(Set<String> excludedOwners,
            Set<String> excludedCategories) {
        if (CollectionUtils.isNullOrEmpty(excludedOwners) || CollectionUtils.isNullOrEmpty(excludedCategories)) {
            return Sets.newHashSet();
        }
        return selectLocks(
                "SELECT * FROM distributed_locks WHERE owner NOT IN (#) AND category NOT IN (#)",
                excludedOwners,
                excludedCategories
        );
    }

    public Set<LockInfo> getAllLocksByOwner(String owner) throws SQLException {
        return selectLocks("select * from distributed_locks where owner=?", owner);
    }

    @SneakyThrows
    public Set<LockInfo> getExpiredLocksNotContainedInCategories(Set<String> excludedCategories,
            long minAllowedTimeMillis, String owner) {
        if (CollectionUtils.isNullOrEmpty(excludedCategories)) {
            return Sets.newHashSet();
        }

        return selectLocks(
                "SELECT * FROM distributed_locks WHERE acquire_time < ? AND category NOT IN (#) AND owner=?",
                minAllowedTimeMillis,
                excludedCategories,
                owner
        );
    }

    @SneakyThrows
    public Set<LockInfo> getExpiredLocksByCategory(String category, long minAllowedTime, String owner) {
        if (StringUtils.isEmpty(category)) {
            return Sets.newHashSet();
        }
        return selectLocks(
                "SELECT * FROM distributed_locks WHERE acquire_time < ? AND category = ? AND owner = ?",
                minAllowedTime,
                category,
                owner
        );
    }

    public Set<LockInfo> getAllLocksByCategoryAndOwner(String category, String owner) throws SQLException {
        return selectLocks("select * from distributed_locks where owner = ? and category = ?", owner, category);
    }

    public LockInfo getLockInfo(String category, String key) throws SQLException {
        key = normalizeKey(key);
        try (ResultSet rs = this.jdbcHelper.executeSelectForSmallResults(
                "select * from distributed_locks where lock_key=? and category=?", key, category)) {
            if (rs.next()) {
                return (toLockInfo(rs));
            }
        }
        return null;
    }

    @SneakyThrows
    private String normalizeKey(String key) {
        if (StringUtils.isBlank(key)) {
            return ".";
        }
        if (key.length() <= 255) {
            return key;
        }
        final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        messageDigest.reset();
        messageDigest.update(key.getBytes(StandardCharsets.UTF_8));
        final byte[] resultByte = messageDigest.digest();
        return new String(Hex.encodeHex(resultByte));
    }

    protected Set<LockInfo> selectLocks(String query, Object... params) throws SQLException {
        try (ResultSet rs = this.jdbcHelper.executeSelect(query, params)) {
            Set<LockInfo> result = Sets.newHashSet();
            while (rs.next()) {
                result.add(toLockInfo(rs));
            }
            return result;
        }
    }

    private static LockInfo toLockInfo(ResultSet resultSet) throws SQLException {
        return new LockInfo(
                resultSet.getString("category"),
                resultSet.getString("lock_key"),
                resultSet.getString("owner"),
                resultSet.getLong("owner_thread"),
                resultSet.getString("owner_thread_name"),
                resultSet.getLong("acquire_time")
        );
    }

    private SqlQuery prepareInsertQuery(@NonNull LockInfo lockInfo, @NonNull String key) {
        String query = NON_CONDITIONAL_INSERT_STATEMENT;

        if (conditionalInsertEnabled) {
            switch (dbType) {
                case POSTGRESQL:
                    query = POSTGRES_CONDITIONAL_INSERT_STATEMENT;
                    break;
                case ORACLE:
                    return new SqlQuery(ORACLE_CONDITIONAL_INSERT_STATEMENT,
                            lockInfo.getCategory(),
                            key,
                            lockInfo.getCategory(),
                            key,
                            lockInfo.getOwner(),
                            lockInfo.getThreadId(),
                            lockInfo.getThreadName(),
                            lockInfo.getStartedTime()
                    );
                case MYSQL, MARIADB, MSSQL, DERBY:  // no conditional
                default:
                    query = NON_CONDITIONAL_INSERT_STATEMENT;
            }
        }

        return new SqlQuery(query,
                lockInfo.getCategory(),
                key,
                lockInfo.getOwner(),
                lockInfo.getThreadId(),
                lockInfo.getThreadName(),
                lockInfo.getStartedTime()
        );
    }

    /**
     * Should not be used directly!
     */
    public boolean isConditionalInsertEnabled() {
        // skip non supported dbs
        if (DbType.DERBY.equals(dbType) || DbType.MSSQL.equals(dbType)
                || DbType.MYSQL.equals(dbType) || DbType.MARIADB.equals(dbType) ) {
            return false;
        }

        boolean globalConditionalInsertEnabled = dbLockConditionalInsertEnabled.getBoolean();
        log.debug("Lock conditional insert property is enabled: {}", globalConditionalInsertEnabled);
        if (globalConditionalInsertEnabled && DbType.POSTGRESQL.equals(dbType)) {
            return isPostgresVersionSupportConditionalInsert();
        }
        return globalConditionalInsertEnabled;
    }

    /**
     * Check if PostgreSQL db supports conditional insert (supported since 9.5),
     * should be called with for PostgreSQL only
     */
    @VisibleForTesting
    boolean isPostgresVersionSupportConditionalInsert() {
        log.debug("Checking if conditional lock is enabled for PostgreSQL DB");
        if (DbType.POSTGRESQL.equals(dbType)) {
            long postgresMinVersion = 90500;
            try (Connection conn = getLocksConnection()) {
                long dbVersion = getPostgresSQLVersionNumber(conn);
                log.debug("PostgreSQL version is: {}", dbVersion);
                return dbVersion >= postgresMinVersion;
            } catch (Exception e) {
                log.error("Unable to detect database version {}", e.getMessage());
                log.debug("Unable to detect database version, assuming that database supports `ON CONFLICT` clause " +
                        "for conditional lock inserts", e);
                return false;
            }
        }
        // assuming that the undetected database supports conditional insert
        return true;
    }

    private int getPostgresSQLVersionNumber(Connection con) throws SQLException {
        int version = 0;
        try (PreparedStatement preparedStatement = con.prepareStatement("show server_version_num;")) {
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                version = rs.getInt(1);
            }
        }
        return version;
    }

    @SuppressWarnings("java:S6218") // This is just a tuple to return string and Object[] not going to compare it
    private record SqlQuery(String sql, Object... params) { }
}