package org.artifactory.devopsagent.errorcatalog.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorCatalogGinReindexJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;
    @Mock
    ArtifactoryDbProperties dbProperties;
    @Mock
    ArtifactoryJdbcHelper jdbcHelper;
    AutoCloseable mocks;

    private ErrorCatalogGinReindexJob job;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.isReady()).thenReturn(true);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        ArtifactoryContextThreadBinder.bind(context);
        job = new ErrorCatalogGinReindexJob();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void onExecuteRunsReindexOnPg() throws JobExecutionException, java.sql.SQLException {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        job.onExecute(mock(JobExecutionContext.class));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper, times(1)).executeUpdate(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("REINDEX INDEX CONCURRENTLY "),
                "REINDEX statement expected, got: " + sql);
        assertTrue(sql.contains("error_catalog_templates_msg_fts_idx"),
                "REINDEX must target the FTS index, got: " + sql);
    }

    @Test
    void onExecuteIsNoOpOnNonPg() throws JobExecutionException, SQLException {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);

        job.onExecute(mock(JobExecutionContext.class));

        verify(jdbcHelper, never()).executeUpdate(anyString());
    }

    @Test
    void onExecuteIsNoOpWhenContextNotReady() throws JobExecutionException, SQLException {
        when(context.isReady()).thenReturn(false);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        job.onExecute(mock(JobExecutionContext.class));

        verify(jdbcHelper, never()).executeUpdate(anyString());
    }

    @Test
    void onExecuteWrapsSqlExceptionAsJobExecutionException() throws SQLException {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        doThrow(new SQLException("reindex blocked")).when(jdbcHelper).executeUpdate(anyString());

        JobExecutionException ex = assertThrows(JobExecutionException.class,
                () -> job.onExecute(mock(JobExecutionContext.class)));
        assertEquals(SQLException.class, ex.getCause().getClass(),
                "JobExecutionException must wrap the original SQLException as cause");
        verify(jdbcHelper, times(1)).executeUpdate(anyString());
    }
}
