package org.artifactory.storage.db.conversion.version.v225;

import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.storage.db.version.converter.LockingDBSqlConverter;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.jfrog.converter.model.ConverterApplyStatus.SKIPPED;

/**
 * Verify that @See org.artifactory.storage.db.conversion.version.v225.V225LockingDBSqlConverter is
 * converting only on POSTGRESQL.
 *
 * @author shaharz
 */
public class LockingDBSqlConverterTest {

    @Test
    public void testWithPostgreSql() throws Throwable {
        //Given
        final DbVersion dbVersion = ArtifactoryDBVersion.v82;
        Assert.assertTrue(dbVersion.converter() instanceof LockingDBSqlConverter);
        LockingDBSqlConverter lockingConverter = (LockingDBSqlConverter) dbVersion.converter();
        JdbcHelper jdbcHelper = Mockito.mock(JdbcHelper.class);
        JFrogDataSource dataSource = Mockito.mock(JFrogDataSource.class);
        Mockito.when(dataSource.getConnection()).thenThrow(SQLException.class);
        Mockito.when(jdbcHelper.getDataSource()).thenReturn(dataSource);

        try {
            //When
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    DbType.POSTGRESQL, dbVersion)) {
                lockingConverter.convert(context);
            }
            //Then
            Assert.fail();
        } catch (Exception e) {
            //Then
            // If we got here with SQLException we are OK
            // If we got into the super lockingConverter it means that POSTGRESQL verification works
            // Our mocked JFrogDataSource.getConnection() throw SQLException

            Assert.assertTrue(e instanceof SQLException);
        }
    }

    @Test
    public void testWithNonePostgreSql() {
        //Given
        final DbVersion dbVersion = ArtifactoryDBVersion.v82;
        Assert.assertTrue(dbVersion.converter() instanceof LockingDBSqlConverter);
        LockingDBSqlConverter lockingConverter = (LockingDBSqlConverter) dbVersion.converter();
        JdbcHelper jdbcHelper = Mockito.mock(JdbcHelper.class);
        JFrogDataSource dataSource = Mockito.mock(JFrogDataSource.class);
        Mockito.when(jdbcHelper.getDataSource()).thenReturn(dataSource);

        try (final Connection connection = jdbcHelper.getDataSource().getConnection()) {
            // If we pass the convert with no exception, we are ok, meaning the call was not
            // proceeded to the super class convert, the POSTGRESQL condition worked.
            final ChangeLogContext oracleContext = new ChangeLogContext(connection, DbType.ORACLE, dbVersion);
            final ChangeLogContext mysqlContext = new ChangeLogContext(connection, DbType.MYSQL, dbVersion);
            final ChangeLogContext mssqlContext = new ChangeLogContext(connection, DbType.MSSQL, dbVersion);
            final ChangeLogContext derbyContext = new ChangeLogContext(connection, DbType.DERBY, dbVersion);
            final ChangeLogContext mariadbContext = new ChangeLogContext(connection, DbType.MARIADB, dbVersion);

            Assert.assertEquals(lockingConverter.convert(oracleContext).getStatus(), SKIPPED);
            Assert.assertEquals(lockingConverter.convert(mysqlContext).getStatus(), ConverterApplyStatus.SKIPPED);
            Assert.assertEquals(lockingConverter.convert(mssqlContext).getStatus(), SKIPPED);
            Assert.assertEquals(lockingConverter.convert(derbyContext).getStatus(), ConverterApplyStatus.SKIPPED);
            Assert.assertEquals(lockingConverter.convert(mariadbContext).getStatus(), SKIPPED);
        } catch (Exception e) {
            //Then
            Assert.fail();
        }
    }
}
