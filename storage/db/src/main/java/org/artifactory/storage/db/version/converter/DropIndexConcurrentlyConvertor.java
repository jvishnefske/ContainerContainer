package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.predefined.CommonScriptFileDbConverter;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.PostgresqlUtils;

import jakarta.annotation.Nonnull;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import static org.jfrog.converter.util.PreDefinedConditions.indexExists;

/**
 * The {@code DropIndexConcurrently} class serves as a database converter specifically designed for concurrent index
 * removal within a postgresql database.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
public class DropIndexConcurrentlyConvertor extends CommonScriptFileDbConverter {

    private final String indexName;

    @SafeVarargs
    public DropIndexConcurrentlyConvertor(@Nonnull String indexName, @Nonnull String tableName,
            @Nonnull List<String> columnNames,
            BiPredicate<Connection, DbType>... conditions) {
        super("Dropping index: " + indexName + " concurrently.",
                Arrays.stream(conditions).reduce(BiPredicate::and).orElse((x, y) -> true)
                        .and(indexExists(tableName, indexName, columnNames)));
        this.indexName = indexName;
    }

    /**
     * The converter result may have a status of SUSPENDED, which indicates that the
     * conversion process failed to execute successfully this time.
     * The process will be retried during the next conversion attempt.
     * In such cases, other converters will proceed, and this specific converter will be reattempted later.
     */
    @Override
    public DbConverterResult convert(ChangeLogContext context) {
        DbConverterResult result = new DbConverterResult();
        log.info("Starting converter {} with description: \"{}\"", context.getDbVersion(), description());
        try {
            handleConvertorExecution(context, result);
        } catch (DbConverterExecutionException e) {
            log.info(
                    "Converter {} failed to execute this time. Further converters would be executed and this one " +
                            "would be reattempted the next time time. You can try to drop {} index manually.",
                    context.getDbVersion().version(), indexName);
            result.setStatus(ConverterApplyStatus.SUSPENDED);
            result.setException(e);
        }
        log.info("Converter `{}` is done with status `{}`.", context.getDbVersion().version(), result.getStatus());
        return result;
    }

    @VisibleForTesting
    void handleConvertorExecution(ChangeLogContext context, DbConverterResult result)
            throws DbConverterExecutionException {
        if (testConditions(context)) {
            executeConverter(context);
            result.setStatus(ConverterApplyStatus.COMPLETED);
        } else {
            result.setStatus(ConverterApplyStatus.SKIPPED);
        }
    }

    @Override
    protected void executeConverter(ChangeLogContext context) throws DbConverterExecutionException {
        int originalStatementTimeout = -1;
        Connection connection = context.getConnection();
        try {
            originalStatementTimeout = getStatementTimeout(connection);
            log.debug("Disabling statement timeout to ensure uninterrupted execution during index removal.");
            setStatementTimeout(connection, 120_000);
            dropIndexConcurrently(connection);
        } catch (SQLException e) {
            throw new DbConverterExecutionException(context.getDbVersion(), e);
        } finally {
            restoreDefaultStatementTimeout(connection, originalStatementTimeout);
        }
    }

    private void dropIndexConcurrently(Connection connection) throws SQLException {
        PostgresqlUtils.dropIndexConcurrently(connection, indexName);
    }

    private int getStatementTimeout(Connection connection) throws SQLException {
        try (ResultSet rs = connection.prepareStatement(
                "select setting from pg_settings where pg_settings.name = 'statement_timeout'").executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void setStatementTimeout(Connection connection, int timeoutMs) throws SQLException {
        if (timeoutMs != -1) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("set statement_timeout = " + timeoutMs);
            }
        }
    }

    private void restoreDefaultStatementTimeout(Connection conn, int originalStatementTimeout) {
        log.debug("Restoring the connection statement timeout.");
        try {
            setStatementTimeout(conn, originalStatementTimeout);
        } catch (Exception e) {
            log.warn("Failed to restore connection statement timeout.");
        }
    }
}