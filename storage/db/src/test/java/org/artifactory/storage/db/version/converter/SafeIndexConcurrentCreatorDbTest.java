package org.artifactory.storage.db.version.converter;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.mockito.Mock;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.common.ConstantValues.dbPostgresIndexInProgressSamplesTimeMillis;
import static org.artifactory.common.ConstantValues.dbPostgresIndexInProgressWaitMaxTimeMillis;
import static org.jfrog.converter.util.PreDefinedConditions.dbSpecific;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */

public class SafeIndexConcurrentCreatorDbTest extends DbBaseTest {
    private static final String MAKE_INDEX_INVALID_QUERY =
            "UPDATE pg_index SET indisvalid = false FROM pg_class c WHERE c.relname = 'index_name'" +
                    "AND c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = current_schema()) " +
                    "AND pg_index.indexrelid = c.oid;";
    private static final String IS_INVALID_INDEX_QUERY = "SELECT count(1) FROM pg_class, pg_index WHERE " +
            "pg_index.indisvalid = false AND pg_index.indexrelid = pg_class.oid and relname = " +
            "'index_name'";
    private static final String CREATE_INDEX_QUERY = "CREATE INDEX simple_index ON nodes(node_type)";
    private static final String DROP_INDEX_QUERY = "DROP INDEX IF EXISTS simple_index";
    private ChangeLogContext context;
    private SafeIndexConcurrentCreator safeIndexConcurrentlyCreator;
    private AutoCloseable autoCloseable;
    @Mock
    private DbVersion dbVersion;

    @BeforeClass
    void setup() throws SQLException {
        if (dbProperties.isPostgres()) {
            autoCloseable = openMocks(this);
            context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(), POSTGRESQL, dbVersion);
            safeIndexConcurrentlyCreator = new SafeIndexConcurrentCreator("simple_index", "nodes",
                    List.of("node_type"), dbSpecific(POSTGRESQL));
        }
    }

    @AfterClass
    void clear() throws Exception {
        if (dbProperties.isPostgres()) {
            autoCloseable.close();
            context.close();
        }
    }

    @AfterMethod
    void dropIndex() throws SQLException {
        if (dbProperties.isPostgres()) {
            jdbcHelper.executeUpdate(DROP_INDEX_QUERY);
        }
    }

    void shouldSkipTest() {
        if (!dbProperties.isPostgres()) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }

    @Test
    void testIndexExistAndValid() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        jdbcHelper.executeUpdate(CREATE_INDEX_QUERY);
        safeIndexConcurrentlyCreator.executeConverter(context);
        assertEquals(jdbcHelper.executeSelectCount(IS_INVALID_INDEX_QUERY), 0);
    }

    @Test
    void testIndexExistAndInvalid() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        jdbcHelper.executeUpdate(CREATE_INDEX_QUERY);
        jdbcHelper.executeUpdate(MAKE_INDEX_INVALID_QUERY);
        safeIndexConcurrentlyCreator.executeConverter(context);
        assertEquals(jdbcHelper.executeSelectCount(IS_INVALID_INDEX_QUERY), 0);
    }

    @Test
    void testIndexNotExists() throws DbConverterExecutionException, SQLException {
        shouldSkipTest();
        safeIndexConcurrentlyCreator.executeConverter(context);
        assertEquals(jdbcHelper.executeSelectCount(IS_INVALID_INDEX_QUERY), 0);
    }

    @Test
    void testIndexNotExistsAndCreatedInvalid() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        safeIndexConcurrentlyCreator.executeConverter(context);
        jdbcHelper.executeUpdate(MAKE_INDEX_INVALID_QUERY);
        safeIndexConcurrentlyCreator.executeConverter(context);
        assertEquals(jdbcHelper.executeSelectCount(IS_INVALID_INDEX_QUERY), 0);
    }

    @Test(expectedExceptions = DbConverterExecutionException.class, expectedExceptionsMessageRegExp = "Timed out.*")
    void testSkipIndexCreationIfAlreadyInProgress() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        artifactoryHomeBoundTest.setStringSystemProperty(dbPostgresIndexInProgressSamplesTimeMillis, "10");
        artifactoryHomeBoundTest.setStringSystemProperty(dbPostgresIndexInProgressWaitMaxTimeMillis, "50");
        Connection conn = jdbcHelper.getDataSource().getConnection();
        conn.setAutoCommit(false);
        conn.beginRequest();
        PreparedStatement createIndexStatement = conn.prepareStatement(CREATE_INDEX_QUERY);
        createIndexStatement.execute();
        //Index creation command would be in state of 'idle-in-transaction'
        try {
            safeIndexConcurrentlyCreator.executeConverter(context);
        } finally {
            conn.commit();
            conn.close();
            createIndexStatement.close();
        }
    }

    @Test(expectedExceptions = DbConverterExecutionException.class,
            expectedExceptionsMessageRegExp = "Failure while checking if index creation.*")
    void testThrowDbConverterExecutionException() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        Connection conn = mock(Connection.class);
        ChangeLogContext mockContext = mock(ChangeLogContext.class);
        when(mockContext.getDbType()).thenReturn(POSTGRESQL);
        when(mockContext.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(any())).thenThrow(SQLException.class);
        safeIndexConcurrentlyCreator.executeConverter(mockContext);
    }

    @Test
    void testStatementTimoutDisabled() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        context.getConnection().prepareStatement("set statement_timeout = 200000").execute();
        assertEquals(context.getConnection().getNetworkTimeout(), 3600000);
        safeIndexConcurrentlyCreator.executeConverter(context);
        assertEquals(context.getConnection().getNetworkTimeout(), 3600000);
        ResultSet rs = context.getConnection().prepareStatement(
                "select setting from pg_settings where pg_settings.name = 'statement_timeout';").executeQuery();
        if (rs.next()) {
            assertEquals(rs.getInt(1), 200000);
            rs.close();
        }
    }
}