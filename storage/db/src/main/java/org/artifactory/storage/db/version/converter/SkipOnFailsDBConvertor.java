package org.artifactory.storage.db.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.predefined.CommonScriptFileDbConverter;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class SkipOnFailsDBConvertor extends CommonScriptFileDbConverter {
    private final boolean skipOnFail;

    @SafeVarargs
    public SkipOnFailsDBConvertor(String file, boolean skipOnFail, BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
        this.skipOnFail = skipOnFail;
    }

    @Override
    public DbConverterResult convert(ChangeLogContext context) {
        DbConverterResult result = new DbConverterResult();
        log.info("Starting converter {} with description: \"{}\"", context.getDbVersion(), description());
        try {
            handleConvertorExecution(context, result);
        } catch (DbConverterExecutionException e) {
            handleConvertorFailure(result);
            result.setException(e);
        }
        log.info("Converter `{}` is done with status `{}`.", context.getDbVersion().version(), result.getStatus());
        return result;
    }

    private void handleConvertorExecution(ChangeLogContext context, DbConverterResult result)
            throws DbConverterExecutionException {
        if (testConditions(context)) {
            executeConverter(context);
            result.setStatus(ConverterApplyStatus.COMPLETED);
        } else {
            result.setStatus(ConverterApplyStatus.SKIPPED);
        }
    }

    private void handleConvertorFailure(DbConverterResult result) {
        if (skipOnFail) {
            result.setStatus(ConverterApplyStatus.SKIPPED);
        } else {
            result.setStatus(ConverterApplyStatus.FAILED);
        }
    }
}
