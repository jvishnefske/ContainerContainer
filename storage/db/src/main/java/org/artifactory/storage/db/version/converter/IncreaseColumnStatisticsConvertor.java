package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.predefined.CommonScriptFileDbConverter;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class IncreaseColumnStatisticsConvertor extends CommonScriptFileDbConverter {

    private static final String GET_STATICS_QUERY = """
            SELECT attname, attstattarget FROM pg_attribute
            WHERE attrelid = ?::regclass
            AND (attname = ?);
            """;

    private final String tableName;
    private final String attributeName;

    @SafeVarargs
    public IncreaseColumnStatisticsConvertor(String file, String attributeName,
            BiPredicate<Connection, DbType>... conditions) {
        this(file, "node_props", attributeName, conditions);
    }

    @SafeVarargs
    public IncreaseColumnStatisticsConvertor(String file, String tableName, String attributeName,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
        this.tableName = tableName;
        this.attributeName = attributeName;
    }

    @Override
    public DbConverterResult convert(ChangeLogContext context) {
        DbConverterResult result = new DbConverterResult();
        log.info("Starting converter {} with description: \"{}\"", context.getDbVersion(), description());
        try {
            handleConvertorExecution(context, result);
        } catch (DbConverterExecutionException e) {
            result.setStatus(ConverterApplyStatus.SUSPENDED);
            result.setException(e);
        }
        log.info("Converter `{}` is done with status `{}`.", context.getDbVersion().version(), result.getStatus());
        return result;
    }

    private void handleConvertorExecution(ChangeLogContext context, DbConverterResult result)
            throws DbConverterExecutionException {
        if (testConditions(context)) {
            try {
                if (!areStatisticsSet(context.getConnection())) {
                    executeConverter(context);
                    result.setStatus(ConverterApplyStatus.COMPLETED);
                } else {
                    result.setStatus(ConverterApplyStatus.SKIPPED);
                }
            } catch (SQLException e) {
                result.setStatus(ConverterApplyStatus.FAILED);
            }
        } else {
            result.setStatus(ConverterApplyStatus.SKIPPED);
        }
    }

    @VisibleForTesting
    boolean areStatisticsSet(Connection conn) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(GET_STATICS_QUERY)) {
            statement.setString(1, tableName);
            statement.setString(2, attributeName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int staticValue = resultSet.getInt(2);
                    log.info("Retrieved static value for attribute {}: {}", attributeName, staticValue);
                    return staticValue >= 1000;
                } else {
                    return false;
                }
            }
        }
    }
}