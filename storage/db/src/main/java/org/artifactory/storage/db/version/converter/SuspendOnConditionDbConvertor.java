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
import java.sql.SQLException;
import java.util.function.BiPredicate;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public abstract class SuspendOnConditionDbConvertor extends CommonScriptFileDbConverter {

    @SafeVarargs
    protected SuspendOnConditionDbConvertor(String file, BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
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

    @VisibleForTesting
    void handleConvertorExecution(ChangeLogContext context, DbConverterResult result)
            throws DbConverterExecutionException {
        try (Connection con = context.getConnection()) {
            if (testConditions(context)) {
                if (!checkIsValidConversionVersion(con)) {
                    log.debug("The converter has been suspended due to: ", result.getException());
                    throw new DbConverterExecutionException("Failed to run converter due to incompatible db version");
                }
                executeConverter(context);
                result.setStatus(ConverterApplyStatus.COMPLETED);
            } else {
                result.setStatus(ConverterApplyStatus.SKIPPED);
            }
        } catch (SQLException e) {
            log.debug(getExceptionMessage() + file, e);
            log.error(getExceptionMessage() + file, e.getMessage());
            result.setStatus(ConverterApplyStatus.FAILED);
        }
    }

    abstract boolean checkIsValidConversionVersion(Connection con);

    abstract String getExceptionMessage();
}
