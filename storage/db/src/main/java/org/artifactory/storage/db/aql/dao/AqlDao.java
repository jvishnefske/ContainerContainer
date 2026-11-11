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

package org.artifactory.storage.db.aql.dao;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.aql.sql.result.AqlEagerResultImpl;
import org.artifactory.storage.db.aql.sql.result.AqlLazyResultImpl;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.TxHelper;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * @author Gidi Shabat
 */
@Slf4j
@Repository
public class AqlDao extends BaseDao {

    @Autowired
    private DbService dbService;

    @Autowired
    public AqlDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Execute the AQL query ant fetch the results eagerly
     * Note that it is recommended to use the executeQueryLazy which consumes less memory and allow minimal waiting
     * time.
     */
    public <T extends AqlRowResult> AqlEagerResultImpl<T> executeQueryEager(SqlQuery<T> sqlQuery,
            AqlRepoProvider aqlRepoProvider) {
        ResultSet resultSet = null;
        try {
            if (allowReadCommitted()) {
                resultSet = jdbcHelper.executeSelect(sqlQuery.getQueryString(), sqlQuery.getQueryParams());
            } else {
                QueryControls queryControls = QueryControls.builder().allowDirtyReads(true).build();
                resultSet =
                        jdbcHelper.executeSelect(queryControls, sqlQuery.getQueryString(), sqlQuery.getQueryParams());
            }
            return new AqlEagerResultImpl<>(resultSet, sqlQuery, aqlRepoProvider);
        } catch (Exception e) {
            throw new AqlException("Failed to execute the following sql query: " + sqlQuery, e);
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private boolean allowReadCommitted() {
        return dbService.getDatabaseType().equals(DbType.ORACLE) ||
                dbService.getDatabaseType().equals(DbType.POSTGRESQL) ||
                ConstantValues.enableAqlReadCommitted.getBoolean() || TxHelper.isInTransaction();
    }

    /**
     * Execute the AQL query and fetch the results lazy
     * Unlike the Eager mode which loads all the results into memory and only then return it to the caller.
     * The lazy mode dynamically fills the result into stream, actually the AqlQueryStreamResult role is is kind of
     * broker between the database and the client
     */
    public <T extends AqlRowResult> AqlLazyResult<T> executeQueryLazy(SqlQuery<T> sqlQuery,
            AqlPermissionProvider aqlPermissionProvider, AqlRepoProvider aqlRepoProvider, Long queryTimeout) {
        ResultSet resultSet;
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        long startTime;
        if (queryTimeout != null) {
            builder.queryTimeout(queryTimeout.intValue());
        }
        try {
            if (allowReadCommitted()) {
                startTime = System.currentTimeMillis();
                log.debug("Starting AQL lazy query, allowReadCommitted, startTime: {}, queryTimeout: {}",
                        startTime, queryTimeout);
                resultSet = jdbcHelper.executeSelect(builder.build(),
                        sqlQuery.getQueryString(), sqlQuery.getQueryParams());
                log.debug("Finished AQL lazy query: {}", System.currentTimeMillis() - startTime);
            } else {
                startTime = System.currentTimeMillis();
                log.debug("Starting AQL lazy query: {}, , queryTimeout: {}", startTime, queryTimeout);
                resultSet =
                        jdbcHelper.executeSelect(builder.allowDirtyReads(true).build(),
                                sqlQuery.getQueryString(), sqlQuery.getQueryParams());
                log.debug("Finished AQL lazy query: {}", System.currentTimeMillis() - startTime);
            }
            logForLongQuery(startTime, System.currentTimeMillis(), queryTimeout, sqlQuery.getQueryString());
        } catch (SQLException e) {
            throw new AqlException("Failed to execute the following sql query: " + sqlQuery, e);
        }
        return new AqlLazyResultImpl<>(resultSet, sqlQuery, aqlPermissionProvider, aqlRepoProvider);
    }

    public <T extends AqlRowResult> AqlLazyResult<T> executeStreamedResponse(SqlQuery<T> sqlQuery,
            AqlPermissionProvider aqlPermissionProvider, AqlRepoProvider aqlRepoProvider, Long queryTimeout,
            int fetchSize) {
        ResultSet resultSet;
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        long startTime;
        if (queryTimeout != null) {
            builder.queryTimeout(queryTimeout.intValue());
        }
        addFetchSize(fetchSize, builder);
        addDirtyReads(builder);
        try {
            startTime = System.currentTimeMillis();
            log.debug("Starting AQL lazy query with streamed response: {}, queryTimeout: {}", startTime,
                    queryTimeout);
            resultSet =
                    jdbcHelper.executeSelectStreamed(builder.build(),
                            sqlQuery.getQueryString(), sqlQuery.getQueryParams());
            log.debug("Finished AQL lazy query with streamed response: {}", System.currentTimeMillis() - startTime);
        } catch (SQLException e) {
            throw new AqlException("Failed to execute the following sql query: " + sqlQuery, e);
        }
        return new AqlLazyResultImpl<>(resultSet, sqlQuery, aqlPermissionProvider, aqlRepoProvider);
    }

    void addDirtyReads(QueryControls.QueryControlsBuilder builder) {
        if (ConstantValues.aqlAllowDirtyReads.getBoolean()) {
            builder.allowDirtyReads(true);
        }
    }

    @VisibleForTesting
    void addFetchSize(int fetchSize, QueryControls.QueryControlsBuilder builder) {
        if (!dbService.getDatabaseType().equals(DbType.DERBY)) {
            builder.fetchSize(fetchSize);
        }
    }

    protected void logForLongQuery(Long startTime, Long endTime, Long queryTimeout, String query) {
        Long timeout = ContextHelper.get().beanForType(AddonsManager.class).getAqlQueryTimeoutByLicense();
        long duration = TimeUnit.MILLISECONDS.toSeconds(endTime - startTime);
        if (queryTimeout == null && duration > timeout) {
            log.warn("An AQL query without timeout took more time than the defined value for this license. " +
                    "defined value: {}, timeToFinish: {}, query: {}", timeout, duration, query);
            log.debug("An AQL query without timeout took more time than the defined value for this license. " +
                            "defined value: {}, timeToFinish: {}, stackTrace: {}", timeout, duration,
                    ExceptionUtils.getStackTrace(new Exception()));
        }
    }
}
