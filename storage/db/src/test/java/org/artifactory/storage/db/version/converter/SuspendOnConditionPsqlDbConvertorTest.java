package org.artifactory.storage.db.version.converter;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.mockito.Mock;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

import static org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion.PSQL_10;
import static org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion.VALID_VERSION;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;

/**
 * @author Tomer Slutsky
 */
public class SuspendOnConditionPsqlDbConvertorTest extends DbBaseTest {
    private ChangeLogContext context;
    private AutoCloseable autoCloseable;
    @Mock
    private DbVersion dbVersion;

    @BeforeMethod
    void setup() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        autoCloseable = openMocks(this);
        context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(), dbType, dbVersion);
    }

    @AfterMethod
    void clear() throws Exception {
        autoCloseable.close();
        context.close();
    }

    @Test(dataProvider = "suspendConditional")
    void testStatus(ValidPsqlConversionVersion validPsqlConversionVersion, ConverterApplyStatus expectedStatus)
            throws SQLException {
        if (!isPostgres()) {
            throw new SkipException("Skipping test since it's not postgres db");
        }

        SuspendOnConditionPsqlDbConvertor suspendOnConditionPsqlDbConvertor =
                new SuspendOnConditionPsqlDbConvertor("valid_content", validPsqlConversionVersion,
                        executeUnconditional());
        DbConverterResult result = suspendOnConditionPsqlDbConvertor.convert(context);
        if (isPostgresVersion10OrAbove()) {
            assertEquals(result.getStatus(), expectedStatus);
        } else {
            if (validPsqlConversionVersion.equals(VALID_VERSION)) {
                assertEquals(result.getStatus(), ConverterApplyStatus.COMPLETED);
            } else {
                assertEquals(result.getStatus(), ConverterApplyStatus.SUSPENDED);
            }
        }
    }

    @Test
    void testStatusSkipped() {
        if (!isPostgres()) {
            throw new SkipException("Skipping test since it's not postgres db");
        }

        SuspendOnConditionPsqlDbConvertor suspendOnConditionPsqlDbConvertor =
                new SuspendOnConditionPsqlDbConvertor("valid_content", PSQL_10, executeFalseConditional());
        DbConverterResult result = suspendOnConditionPsqlDbConvertor.convert(context);
        assertEquals(result.getStatus(), ConverterApplyStatus.SKIPPED);
    }


    public BiPredicate<Connection, DbType> executeFalseConditional() {
        return (connection, dbType) -> false;
    }

    private boolean isPostgres() {
        return dbProperties.getDbType().equals(DbType.POSTGRESQL);
    }

    @DataProvider
    public static Object[][] suspendConditional() {
        return new Object[][] {
                {PSQL_10, ConverterApplyStatus.COMPLETED},
                {ValidPsqlConversionVersion.VALID_VERSION, ConverterApplyStatus.COMPLETED}
        };
    }

    private boolean isPostgresVersion10OrAbove() throws SQLException {
        try (Connection con = jdbcHelper.getDataSource().getConnection(); PreparedStatement stmt = con.prepareStatement(
                "SHOW server_version_num")) {
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                long version = resultSet.getLong(1);
                return version >= 100000L;
            }
            return false;
        }
    }
}
