package org.artifactory.storage.db.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.predefined.CommonScriptFileDbConverter;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;

import jakarta.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BiPredicate;

import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.util.DbStatementUtils.executeSqlStream;
import static org.jfrog.storage.util.DbUtils.dropTableIfExists;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class CleanUpTasksTableDBConvertor extends CommonScriptFileDbConverter {

    private static final String NUMBER_OF_INDEX_TASKS_ORACLE_QUERY =
            "select count(*) from (select 1 from tasks where task_type = 'INDEX' AND ROWNUM <= 1001) a";

    @SafeVarargs
    public CleanUpTasksTableDBConvertor(@Nonnull String file, BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
    }

    @Override
    public DbConverterResult convert(ChangeLogContext context) {
        DbConverterResult result = new DbConverterResult();
        log.info("Starting converter {} with description: \"{}\"", context.getDbVersion(), description());
        try {
            handleConvertorExecution(context, result);
        } catch (DbConverterExecutionException e) {
            result.setStatus(ConverterApplyStatus.FAILED);
            result.setException(e);
        } catch (Exception e) {
            result.setStatus(ConverterApplyStatus.FAILED);
            result.setException(new DbConverterExecutionException(context.getDbVersion(), e));
        }
        log.info("Converter `{}` is done with status `{}`.", context.getDbVersion().version(), result.getStatus());
        return result;
    }

    private void handleConvertorExecution(ChangeLogContext context, DbConverterResult result)
            throws DbConverterExecutionException, SQLException, IOException {
        if (testConditions(context)) {
            JdbcHelper jdbcHelper = ContextHelper.get().beanForType(JdbcHelper.class);
            ArtifactoryDbProperties dbProperties = ContextHelper.get().beanForType(ArtifactoryDbProperties.class);
            dropTableIfExists(jdbcHelper, dbProperties.getDbType(), "tasks_tmp");
            if (ORACLE.equals(dbProperties.getDbType())) {
                handleOracleConvertor(context);
            } else {
                executeConverter(context);
            }
            result.setStatus(ConverterApplyStatus.COMPLETED);
        } else {
            result.setStatus(ConverterApplyStatus.SKIPPED);
        }
    }

    private void handleOracleConvertor(ChangeLogContext context)
            throws DbConverterExecutionException, SQLException, IOException {
        String deleteChunkSqlQuery = this.calculateSqlStatement(context.getDbType());
        JdbcHelper jdbcHelper = ContextHelper.get().beanForType(JdbcHelper.class);
        int numberOfDeletionCandidates = jdbcHelper.executeSelectCount(NUMBER_OF_INDEX_TASKS_ORACLE_QUERY);
        log.info("SQL statement \"{}\" will be executed.", deleteChunkSqlQuery);
        while (numberOfDeletionCandidates > 1000) {
            waitDelayTime();
            executeSqlStream(context.getConnection(), new ByteArrayInputStream(deleteChunkSqlQuery.getBytes()));
            numberOfDeletionCandidates = jdbcHelper.executeSelectCount(NUMBER_OF_INDEX_TASKS_ORACLE_QUERY);
        }
    }

    private void waitDelayTime() {
        try {
            log.debug("waiting {} milli seconds before next retry", 10);
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}