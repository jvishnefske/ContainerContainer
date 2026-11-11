package org.artifactory.storage.db.version.converter;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.mockito.Mock;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BiPredicate;

import static org.jfrog.converter.model.ConverterApplyStatus.COMPLETED;
import static org.jfrog.converter.model.ConverterApplyStatus.FAILED;
import static org.jfrog.converter.model.ConverterApplyStatus.SKIPPED;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */
public class CleanUpTasksTableDBConvertorTest extends DbBaseTest {

    private ChangeLogContext context;
    private AutoCloseable autoCloseable;
    @Mock
    private DbVersion dbVersion;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;

    @BeforeClass
    void setup() throws SQLException {
        autoCloseable = openMocks(this);
        dummyArtifactoryContext.addBean(centralConfigService, CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        ArtifactoryContextThreadBinder.bind(dummyArtifactoryContext);
        DbType dbType = dbProperties.getDbType();
        context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(), dbType, dbVersion);
    }

    @AfterClass
    void clear() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
        context.close();
    }

    @Test(dataProvider = "archiveIndexDisabled")
    public void testConvertorStatus(BiPredicate<Connection, DbType> execCond, ConverterApplyStatus expectedStatus) {
        CleanUpTasksTableDBConvertor cleanUpTasksTableDBConvertor =
                new CleanUpTasksTableDBConvertor("valid_content", execCond);
        DbConverterResult result = cleanUpTasksTableDBConvertor.convert(context);
        assertEquals(result.getStatus(), expectedStatus);
    }

    @Test(dataProvider = "fileName")
    public void testConvertorThrowException(String fileName) {
        importSql("/sql/insert_tasks_records.sql");
        CleanUpTasksTableDBConvertor cleanUpTasksTableDBConvertor =
                new CleanUpTasksTableDBConvertor(fileName, executeUnconditional());
        DbConverterResult result = cleanUpTasksTableDBConvertor.convert(context);
        assertEquals(result.getStatus(), FAILED);
    }

    @DataProvider
    public static Object[][] archiveIndexDisabled() {
        BiPredicate<Connection, DbType> dontExecuteCondition = (connection, dbType) -> false;
        return new Object[][] {
                {executeUnconditional(), COMPLETED},
                {dontExecuteCondition, SKIPPED}
        };
    }

    @DataProvider
    public static Object[][] fileName() {
        return new Object[][] {
                {"invalid_content"},
                {"not_exists"},
        };
    }
}