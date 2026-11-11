package org.artifactory.devopsagent.requestlog.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogsGinReindexJobTest {

    @Test
    void nullContextSkipsExecution() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(null);

            new RequestLogsGinReindexJob().onExecute(mock(JobExecutionContext.class));
        }
    }

    @Test
    void contextNotReadySkipsExecution() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(false);

            new RequestLogsGinReindexJob().onExecute(mock(JobExecutionContext.class));

            verify(ctx, never()).beanForType(ArtifactoryJdbcHelper.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DbType.class, names = {"MYSQL", "ORACLE", "MSSQL", "DERBY"})
    void nonPostgresSkipsReindex(DbType dbType) throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(true);
            ArtifactoryDbProperties dbProps = mock(ArtifactoryDbProperties.class);
            when(dbProps.getDbType()).thenReturn(dbType);
            when(ctx.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProps);

            new RequestLogsGinReindexJob().onExecute(mock(JobExecutionContext.class));

            verify(ctx, never()).beanForType(ArtifactoryJdbcHelper.class);
        }
    }

    @Test
    void postgresReindexesBothIndexes() throws JobExecutionException, SQLException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(true);
            ArtifactoryDbProperties dbProps = mock(ArtifactoryDbProperties.class);
            when(dbProps.getDbType()).thenReturn(DbType.POSTGRESQL);
            when(ctx.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProps);
            ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
            when(ctx.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);

            new RequestLogsGinReindexJob().onExecute(mock(JobExecutionContext.class));

            verify(jdbcHelper).executeUpdate("REINDEX INDEX CONCURRENTLY request_logs_search_vector_idx");
            verify(jdbcHelper).executeUpdate(
                    "REINDEX INDEX CONCURRENTLY outbound_rl_search_vector_idx");
        }
    }

    @Test
    void sqlExceptionWrappedAsJobExecutionException() throws SQLException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(true);
            ArtifactoryDbProperties dbProps = mock(ArtifactoryDbProperties.class);
            when(dbProps.getDbType()).thenReturn(DbType.POSTGRESQL);
            when(ctx.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProps);
            ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
            when(ctx.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
            when(jdbcHelper.executeUpdate(anyString())).thenThrow(new SQLException("lock timeout"));

            assertThrows(JobExecutionException.class,
                    () -> new RequestLogsGinReindexJob().onExecute(mock(JobExecutionContext.class)));
        }
    }
}
