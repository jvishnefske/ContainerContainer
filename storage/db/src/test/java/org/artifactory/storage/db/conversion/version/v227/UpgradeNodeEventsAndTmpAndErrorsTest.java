package org.artifactory.storage.db.conversion.version.v227;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverter;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Test the flow of @{@link CreateDbSpecificSequence}
 *
 * @author shaharz
 */
@Test
public class UpgradeNodeEventsAndTmpAndErrorsTest {

    private ArtifactoryJdbcHelper jdbcHelper;
    private Connection connection;
    private Statement statement;
    private ResultSet currentIdResultSet;
    private ResultSet tablesResultSet;
    private ResultSet columnsResultSet;

    @BeforeMethod
    private void setup() throws Exception {

        ArtifactoryHome.bind(Mockito.mock(ArtifactoryHome.class));
        Mockito.when(ArtifactoryHome.get().getArtifactoryProperties()).thenReturn(Mockito.mock(
                ArtifactorySystemProperties.class));
        Mockito.when(ArtifactoryHome.get().getArtifactoryProperties()
                .getLongProperty(Mockito.eq(ConstantValues.nodeEventsEventIdMigrationMargin))).thenReturn(1_000_000L);
        Mockito.when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(Mockito.eq(ConstantValues.allowExternalConversionScripts))).thenReturn(false);

        jdbcHelper = Mockito.mock(ArtifactoryJdbcHelper.class);
        JFrogDataSource dataSource = Mockito.mock(JFrogDataSource.class);
        connection = Mockito.mock(Connection.class);
        statement = Mockito.mock(Statement.class);
        currentIdResultSet = Mockito.mock(ResultSet.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        tablesResultSet = Mockito.mock(ResultSet.class);
        Mockito.when(tablesResultSet.next()).thenReturn(true);
        columnsResultSet = Mockito.mock(ResultSet.class);
        Mockito.when(columnsResultSet.next()).thenReturn(false);

        Mockito.when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(connection.getMetaData()).thenReturn(metaData);

        Mockito.when(metaData.getColumns(any(), any(), any(), any())).thenReturn(columnsResultSet);
        Mockito.when(statement.executeQuery(anyString())).thenReturn(currentIdResultSet);
        Mockito.when(currentIdResultSet.next()).thenReturn(true);
        Mockito.when(currentIdResultSet.getInt(Mockito.eq(1))).thenReturn(1);
        Mockito.when(metaData.getConnection()).thenReturn(connection);
        Mockito.when(metaData.getTables(any(), any(), any(), any()))
                .thenReturn(tablesResultSet);

        Mockito.when(tablesResultSet.next()).thenReturn(false);
    }

    @AfterMethod
    private void destroy() {
        ArtifactoryHome.unbind();
    }

    @Test(dataProvider = "AlreadyInvokedProvider")
    public void testAlreadyInvoked(boolean invoked) throws SQLException {
        final ArtifactoryDBVersion dbVersion = ArtifactoryDBVersion.v86;
        final CreateDbSpecificSequence sequenceConverter = (CreateDbSpecificSequence) dbVersion.converter();
        final CreateDbSpecificSequence converterSpy = Mockito.spy(sequenceConverter);
        Mockito.doReturn(invoked).when(converterSpy).sequenceExists(connection, DbType.POSTGRESQL);

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                DbType.POSTGRESQL, dbVersion)) {
            converterSpy.convert(context);
        }

        if (invoked) {
            Mockito.verify(connection, Mockito.never()).setAutoCommit(Mockito.eq(false));
        } else {
            Mockito.verify(connection, Mockito.times(1)).setAutoCommit(Mockito.eq(false));
            Mockito.verify(connection, Mockito.times(1)).setAutoCommit(Mockito.eq(true));
        }
    }

    @DataProvider(name = "AlreadyInvokedProvider")
    private Object[][] testAlreadyInvokedProvider() {
        return new Object[][] {{true}, {false}};
    }

    @Test
    public void testSequenceStartValue() throws Exception {
        Mockito.when(currentIdResultSet.getLong(Mockito.eq(1))).thenReturn(5000L);
        final PreparedStatement psMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(psMock.executeQuery()).thenReturn(Mockito.mock(ResultSet.class));
        Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(psMock);
        final ArtifactoryDBVersion dbVersion = ArtifactoryDBVersion.v86;
        final DbConverter sequenceConverter = dbVersion.converter();
        sequenceConverter.convert(new ChangeLogContext(connection, DbType.POSTGRESQL, dbVersion));

        ArgumentCaptor<String> createSeqSql = ArgumentCaptor.forClass(String.class);
        Mockito.verify(statement, Mockito.atLeast(1)).executeUpdate(createSeqSql.capture());
        Assert.assertTrue(createSeqSql.getAllValues().stream()
                .anyMatch(value -> value.contains("START WITH " + (ConstantValues.nodeEventsEventIdMigrationMargin
                        .getLong() + 5000))));
    }

    @Test(dataProvider = "AlreadyInvokedProvider")
    public void testConditionalConverterNotRunWhenColumnExists(boolean columnExists) throws SQLException {
        Mockito.when(columnsResultSet.next()).thenReturn(columnExists);

        final ArtifactoryDBVersion dbVersion = ArtifactoryDBVersion.v89;
        final DbConverter conditionalConverter = dbVersion.converter();
        conditionalConverter.convert(new ChangeLogContext(connection, DbType.POSTGRESQL, dbVersion));

        if (columnExists) {
            Mockito.verify(connection, Mockito.never()).commit();
        } else {
            Mockito.verify(connection).commit();
        }
    }
}
