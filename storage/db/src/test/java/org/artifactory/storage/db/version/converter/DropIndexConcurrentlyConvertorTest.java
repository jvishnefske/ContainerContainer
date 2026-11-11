package org.artifactory.storage.db.version.converter;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.mockito.Mock;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.jfrog.converter.model.ConverterApplyStatus.COMPLETED;
import static org.jfrog.converter.model.ConverterApplyStatus.SKIPPED;
import static org.jfrog.converter.model.ConverterApplyStatus.SUSPENDED;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */
@Test
public class DropIndexConcurrentlyConvertorTest extends DbBaseTest {

    private static final String CREATE_INDEX_QUERY = "CREATE INDEX cool_index ON nodes(node_type)";
    private static final String DROP_INDEX_QUERY = "DROP INDEX IF EXISTS cool_index";
    private ChangeLogContext context;
    private AutoCloseable autoCloseable;
    private DropIndexConcurrentlyConvertor dropIndexConcurrentlyConvertor;
    @Mock
    private DbVersion dbVersion;

    @BeforeClass
    void setup() throws SQLException {
        if (dbProperties.isPostgres()) {
            autoCloseable = openMocks(this);
            context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(), POSTGRESQL, dbVersion);
            this.dropIndexConcurrentlyConvertor =
                    spy(new DropIndexConcurrentlyConvertor("cool_index", "nodes", List.of("node_type")));
        }
    }

    @AfterClass
    void clear() throws Exception {
        if (dbProperties.isPostgres()) {
            autoCloseable.close();
            context.close();
        }
    }

    @Test
    void testIndexDroppedSuccessfully() throws SQLException {
        shouldSkipTest();
        jdbcHelper.executeUpdate(CREATE_INDEX_QUERY);
        DbConverterResult result = dropIndexConcurrentlyConvertor.convert(context);
        assertEquals(result.getStatus(), COMPLETED);
        jdbcHelper.executeUpdate(DROP_INDEX_QUERY);
    }

    @Test
    void testIndexDroppedSkipped() throws SQLException {
        shouldSkipTest();
        jdbcHelper.executeUpdate(CREATE_INDEX_QUERY);
        jdbcHelper.executeUpdate(DROP_INDEX_QUERY);
        DbConverterResult result = dropIndexConcurrentlyConvertor.convert(context);
        assertEquals(result.getStatus(), SKIPPED);
    }

    @Test
    void testIndexDroppedSuspended() throws SQLException, DbConverterExecutionException {
        shouldSkipTest();
        jdbcHelper.executeUpdate(CREATE_INDEX_QUERY);
        doThrow(DbConverterExecutionException.class).when(dropIndexConcurrentlyConvertor).executeConverter(context);
        DbConverterResult result = dropIndexConcurrentlyConvertor.convert(context);
        assertEquals(result.getStatus(), SUSPENDED);
        jdbcHelper.executeUpdate(DROP_INDEX_QUERY);
    }

    void shouldSkipTest() {
        if (!dbProperties.isPostgres()) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }
}