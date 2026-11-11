package org.artifactory.storage.db.monitor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.jfrog.storage.wrapper.ResultSetProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A proxy for {@link ResultSet} that monitors the number of rows fetched and reports it to the
 * attached {@link ResultSetSizeMonitor} when the query fetch is completed (result set is closed).
 *
 * <p>
 *     This proxy extends {@link ResultSetProxy} to automatically close the underlying JDBC
 *     connection and statement when closing the result set.
 * </p>
 * <p>
 *     The ResultSetMonitoringProxy was created especially for the ArtifactoryJdbcHelper and
 *     was not supposed to be used directly by the user.
 * </p>
 *
 * @author Sergey Steinvil
 */

@Slf4j
public class ResultSetMonitoringProxy extends ResultSetProxy {
    private final AtomicLong rowsCount = new AtomicLong(0);
    protected final ResultSetSizeMonitor monitor;
    @Getter
    private final DbQueryContext queryContext;
    protected final long startTimeNanos;
    private final long rowsCountWarningThreshold;
    @Nullable
    private final ExceedingRowsViolationService violationService;
    private boolean warned;

    public ResultSetMonitoringProxy(ResultSetSizeMonitor monitor,
                                    @Nonnull DbQueryContext queryContext,
                                    @Nonnull ResultSet resultSet,
                                    @Nullable DataSource dataSource,
                                    @Nullable Connection connection,
                                    @Nullable Statement statement,
                                    boolean resetIsolationLevel
    ) {
        this(monitor, queryContext, resultSet, dataSource, connection, statement,
                resetIsolationLevel, null);
    }

    public ResultSetMonitoringProxy(ResultSetSizeMonitor monitor,
                                    @Nonnull DbQueryContext queryContext,
                                    @Nonnull ResultSet resultSet,
                                    @Nullable DataSource dataSource,
                                    @Nullable Connection connection,
                                    @Nullable Statement statement,
                                    boolean resetIsolationLevel,
                                    @Nullable ExceedingRowsViolationService violationService
    ) {
        super(resultSet, dataSource, connection, statement, resetIsolationLevel,
                queryContext.getQuery(), queryContext.isStreaming(), queryContext.getHint(),
                queryContext.isChangedConnectionAutoCommit());
        this.monitor = monitor;
        this.queryContext = queryContext;
        this.startTimeNanos = monitor != null ? monitor.getNanoTime() : System.nanoTime();
        this.rowsCountWarningThreshold =
                ConstantValues.queryMonitorExceedingRowsThreshold.getLong();
        this.violationService = violationService;
    }

    // Just for debugging purposes
    public long getRowsCount() {
        return this.rowsCount.get();
    }

    @Override
    public boolean next() throws SQLException {
        if (super.next()) {
            long currentCount = this.rowsCount.incrementAndGet();
            if (currentCount > rowsCountWarningThreshold && !warned) {
                log.warn("Result set exceeded {} rows: query='{}', "
                                + "action={} ({}/{}, package={}, actor={}), "
                                + "hint={}, streaming={}",
                        rowsCountWarningThreshold,
                        queryContext.getQuery(),
                        queryContext.getActionName(),
                        queryContext.getActionKind(),
                        queryContext.getActionGroup(),
                        queryContext.getActionPackage(),
                        queryContext.getActionActor(),
                        queryContext.getHint(),
                        queryContext.isStreaming());
                warned = true;
                if (ConstantValues.queryMonitorExceedingRowsViolationEnablePersist.getBoolean()
                        && violationService != null) {
                    violationService.persistViolationAsync(queryContext, currentCount);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void close() throws SQLException {
        if (this.isClosed()) {
            return;
        }
        try {
            super.close();
        } finally {
            if (monitor != null && monitor.shouldMonitor(queryContext.getQuery(),
                    queryContext.isStreaming(), queryContext.getHint())) {
                long durationNanos = this.monitor.getNanoTime() - this.startTimeNanos;
                this.monitor.onQueryFetchCompleted(this.queryContext, this.rowsCount.get(), durationNanos);
            }
        }
    }
}
