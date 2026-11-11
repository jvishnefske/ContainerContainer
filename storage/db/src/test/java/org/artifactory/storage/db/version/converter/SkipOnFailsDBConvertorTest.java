package org.artifactory.storage.db.version.converter;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
public class SkipOnFailsDBConvertorTest extends DbBaseTest {
    private ChangeLogContext context;
    private SkipOnFailsDBConvertor skipOnFailDBConvertor;
    private AutoCloseable autoCloseable;
    @Mock
    private DbVersion dbVersion;

    @BeforeClass
    void setup() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        autoCloseable = openMocks(this);
        context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(), dbType, dbVersion);
    }

    @AfterClass
    void clear() throws Exception {
        autoCloseable.close();
        context.close();
    }

    @Test(dataProvider = "shouldSkipOnFails")
    void testStatus(boolean SkipOnFails, ConverterApplyStatus expectedStatus){
        skipOnFailDBConvertor = new SkipOnFailsDBConvertor("invalid_content", SkipOnFails, executeUnconditional());
        DbConverterResult result = skipOnFailDBConvertor.convert(context);
        Assert.assertEquals(result.getStatus(), expectedStatus);
    }

    @DataProvider
    public static Object[][] shouldSkipOnFails() {
        return new Object[][] {
                {true,  ConverterApplyStatus.SKIPPED},
                {false, ConverterApplyStatus.FAILED}
        };
    }
}