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

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.monitor.DbQueryContext;
import org.artifactory.storage.db.monitor.DbQueryMonitor;
import org.artifactory.storage.db.monitor.ExceedingRowsViolationService;
import org.artifactory.storage.db.monitor.ResultSetMonitoringProxy;
import org.artifactory.storage.db.monitor.ResultSetSizeMonitor;
import org.artifactory.storage.db.util.limit.LimitedResultSet;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.QueryControls;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * A helper class to execute jdbc queries.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Service
public class ArtifactoryJdbcHelper extends JdbcHelper {
    private static final List<String> DEFAULT_SENSITIVE_TABLES_FOR_SLOW_QUERY_LOGGING = List.of(
            "trusted_keys",      // the actual PGP key bytes, fingerprint
            "master_key_status"  // kid - master key fingerprint
    );

    private final List<DbQueryMonitor> monitors;
    @Getter
    @Setter(onMethod_ = { @Autowired (required = false) })
    private ResultSetSizeMonitor resultSizeMonitor;
    @Getter
    @Setter(onMethod_ = { @Autowired(required = false), @Lazy })
    private ExceedingRowsViolationService exceedingRowsViolationService;
    @Getter
    @Setter(onMethod_ = { @Value("${artifactory.db.useLegacyResultSetProxy:false}") })
    protected boolean useLegacyResultSetWrapper;

    @Autowired
    public ArtifactoryJdbcHelper(@Qualifier("dataSource") JFrogDataSource dataSource, List<DbQueryMonitor> monitors) {
        super(dataSource);
        this.monitors = monitors == null ? List.of() : List.copyOf(monitors);
    }

    public ArtifactoryJdbcHelper(JFrogDataSource dataSource) {
        this(dataSource, null);
    }

    @PostConstruct
    void enablePrintingSlowQueriesParamIfNeeded() {
        if (ConstantValues.printSlowQueriesParameters.getBoolean()) {
            log.info("Slow query parameter printing is enabled");
            super.enablePrintingSlowQueriesParameters(buildSensitiveTablesForSlowQueryParameterLogging());
        } else {
            log.info("Slow query parameter printing is disabled");
        }
    }

    /**
     * Built-in sensitive tables plus optional extras from
     * {@link ConstantValues#printSlowQueriesParametersSensitiveTablesExtra} (comma-separated).
     */
    private static List<String> buildSensitiveTablesForSlowQueryParameterLogging() {
        List<String> configuredExtras = ConstantValues.printSlowQueriesParametersSensitiveTablesExtra.getList();
        if (configuredExtras.isEmpty()) {
            return DEFAULT_SENSITIVE_TABLES_FOR_SLOW_QUERY_LOGGING;
        }
        Set<String> merged = new LinkedHashSet<>(DEFAULT_SENSITIVE_TABLES_FOR_SLOW_QUERY_LOGGING);
        merged.addAll(configuredExtras);
        return List.copyOf(merged);
    }

    /**
     * Skip some queries from debug logging to reduce verbosity.
     */
    @Override
    protected boolean excludeQueryLogging(String query) {
        return query.contains("artifactory_servers") || query.contains("SELECT count(1) FROM binaries");
    }

    @Override
    protected void logSlowQuery(
            String query, Object[] params, int results,
            long queryTime, long connectionTime) {
        super.logSlowQuery(query, params, results, queryTime, connectionTime);
        logSlowQueryActivityContext();
    }

    @Override
    protected void onSqlFinish(
            String query, Object[] params, int results,
            long queryTime, long connectionTime) {
        super.onSqlFinish(query, params, results, queryTime, connectionTime);
        onQueryCompleted(query, params, queryTime, null);
    }

    @Override
    protected void onSqlBulkFinish(
            String query, int numOfElements,
            long queryTime, long getConnectionTime) {
        super.onSqlBulkFinish(query, numOfElements, queryTime, getConnectionTime);
        if (isSlowQuery(queryTime)) {
            logSlowQueryActivityContext();
        }
        onQueryCompleted(query, null, queryTime, null);
    }

    private void logSlowQueryActivityContext() {
        String activity = formatCurrentActivity();
        if (!activity.isEmpty()) {
            log.info("Slow DB query activity: {}", activity);
        }
    }

    static String formatCurrentActivity() {
        StringJoiner joiner = new StringJoiner(", ");
        appendIfPresent(joiner, "kind", CurrentActivity.KIND);
        appendIfPresent(joiner, "group", CurrentActivity.GROUP);
        appendIfPresent(joiner, "name", CurrentActivity.NAME);
        appendIfPresent(joiner, "pkg", CurrentActivity.PACKAGE);
        appendIfPresent(joiner, "actor", CurrentActivity.ACTOR);
        return joiner.toString();
    }

    private static void appendIfPresent(
            StringJoiner joiner,
            String label,
            CurrentActivity field) {
        String value = field.get();
        if (value != null && !value.isEmpty()) {
            joiner.add(label + "=" + value);
        }
    }

    protected void onQueryCompleted(String query, Object[] params, long durationNano, Exception error) {
        for (DbQueryMonitor monitor : this.monitors) {
            monitor.queryFinished(query, params, durationNano, error);
        }
        if (queryRateLimiter.get() != null) {
            queryRateLimiter.get().updateExecutionTime((int) TimeUnit.NANOSECONDS.toMillis(durationNano));
        }
    }

    public ResultSet executeSelectForSmallResults(String query, Object... params) throws SQLException {
        return executeSelectForSmallResults(QueryControls.builder().build(), query, params);
    }

    public ResultSet executeSelectForSmallResults(QueryControls controls, String query, Object... params)
            throws SQLException {
        return super.executeSelect(controls, query, params);
    }

    @Override
    @Nonnull
    public ResultSet executeSelect(String query, Object... params) throws SQLException {
        return executeSelect(QueryControls.builder().build(), query, params);
    }

    @Override
    @Nonnull
    public ResultSet executeSelect(QueryControls controls, String query, Object... params) throws SQLException {
        int configuredLimit = UniversalLimitHandlerUtility.getConfiguredLimit();
        if (configuredLimit == 0) {
            if (ConstantValues.executeQueryAlwaysWithStream.getBoolean()) {
                controls = buildQueryControlsWithDefaultFetchSize(controls);
                return super.executeSelectStreamed(controls, query, params);
            }
            return super.executeSelect(controls, query, params);
        }
        return executeSelectStreamWithUniversalLimit(controls, query, params, configuredLimit);
    }

    private LimitedResultSet executeSelectStreamWithUniversalLimit(QueryControls controls, String query,
            Object[] params,
            int configuredLimit) throws SQLException {
        QueryControls queryControls = buildQueryControlsWithDefaultFetchSize(controls);

        ResultSet resultSet = super.executeSelectStreamed(queryControls, query, params);
        return new LimitedResultSet(resultSet,
                configuredLimit,
                UniversalLimitHandlerUtility.isToBlock(),
                query);
    }

    private QueryControls buildQueryControlsWithDefaultFetchSize(QueryControls controls) {
        if (controls.getFetchSize() != 0) {
            return controls;
        }

        int configurableFetchSize = ConstantValues.limitAllQueriesFetchSize.getInt();
        return QueryControls.builder()
                .queryTimeout(controls.getQueryTimeout())
                .networkTimeout(controls.getNetworkTimeout())
                .allowDirtyReads(controls.isAllowDirtyReads())
                .fetchSize(configurableFetchSize)
                .build();
    }

    @Override
    protected ResultSet wrapResultSet(Connection con, Statement stmt, ResultSet rs, boolean resetIsolationLevel,
                                      String hint, boolean isStreaming, String query) {
        return wrapResultSet(con, stmt, rs, resetIsolationLevel, hint, isStreaming, query, false);
    }

    @Override
    protected ResultSet wrapResultSet(Connection con, Statement stmt, ResultSet rs, boolean resetIsolationLevel,
            String hint, boolean isStreaming, String query, boolean changedConnectionAutoCommit) {
        if (!shouldMonitorSize(query, isStreaming, hint) && this.useLegacyResultSetWrapper) {
            return super.wrapResultSet(
                    con, stmt, rs, resetIsolationLevel, hint, isStreaming, query);
        }
        DbQueryContext context = new DbQueryContext(query, isStreaming, hint, changedConnectionAutoCommit);
        return new ResultSetMonitoringProxy(this.resultSizeMonitor, context,
                rs, getDataSource(), con, stmt, resetIsolationLevel,
                this.exceedingRowsViolationService);
    }

    protected boolean shouldMonitorSize(String query, boolean isStreaming, String hint) {
        return this.resultSizeMonitor != null &&
                this.resultSizeMonitor.shouldMonitor(query, isStreaming, hint);
    }
}
