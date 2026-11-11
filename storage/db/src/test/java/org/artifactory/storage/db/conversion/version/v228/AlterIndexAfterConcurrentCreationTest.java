package org.artifactory.storage.db.conversion.version.v228;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.codehaus.plexus.util.FileUtils;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.jfrog.converter.util.PreDefinedConditions.dbSpecific;
import static org.jfrog.converter.util.PreDefinedConditions.indexNotExists;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
public class AlterIndexAfterConcurrentCreationTest {

    private final String TEST_DIR = System.getProperty("java.io.tmpdir") + "/converter";
    private Connection connection;
    private ResultSet columnsResultSet;

    @BeforeMethod
    private void setup() throws Exception {
        ArtifactoryHome.bind(mock(ArtifactoryHome.class));
        when(ArtifactoryHome.get().getArtifactoryProperties()).thenReturn(Mockito.mock(
                ArtifactorySystemProperties.class));
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        connection = mock(Connection.class);
        columnsResultSet = mock(ResultSet.class);
        when(columnsResultSet.next()).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);
        new File(TEST_DIR).mkdir();
        FileUtils.fileWrite(TEST_DIR + "/postgresql_v112_alter_node_props_node_prop_value_idx.sql", "DROP INDEX foo");
    }

    @AfterMethod
    private void destroy() throws IOException {
        ArtifactoryHome.unbind();
        FileUtils.deleteDirectory(new File(TEST_DIR));
    }

    @DataProvider(name = "testProvider")
    private Object[][] testProvider() {
        return new Object[][] {
                {true, true},
                {false, true},
                {false, false},
                {true, false}
        };
    }

    @Test(dataProvider = "testProvider")
    public void testConditionalConverterNotRunWhenIndexFailed(boolean indexFailed, boolean allowExternalScripts)
            throws SQLException {
        when(columnsResultSet.next()).thenReturn(indexFailed);
        when(ArtifactoryHome.get().getArtifactoryProperties()
                .getBooleanProperty(Mockito.eq(ConstantValues.allowExternalConversionScripts)))
                .thenReturn(allowExternalScripts);
        when(ArtifactoryHome.get().getExternalConversionsDir())
                .thenReturn(new File(TEST_DIR));
        Statement statement = mock(Statement.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.createStatement()).thenReturn(statement);
        when(metaData.getDatabaseProductName()).thenReturn("Postgresql");
        when(connection.getMetaData()).thenReturn(metaData);
        final ArtifactoryDBVersion dbVersion = ArtifactoryDBVersion.v125;
        final AlterIndexAfterConcurrentCreation conditionalConverter =
                new AlterIndexAfterConcurrentCreation("node_props_node_prop_value_idx",
                        "pattern_node_props_node_prop_value_idx", "v112_alter_node_props_node_prop_value_idx",
                        dbSpecific(DbType.POSTGRESQL), indexNotExists("node_props", "node_props_node_prop_value_idx",
                        List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")));
        PreparedStatement psMock = mock(PreparedStatement.class);
        ResultSet rsMock = mock(ResultSet.class);
        when(rsMock.next()).thenReturn(true);
        if (indexFailed) {
            when(rsMock.getInt(1)).thenReturn(1);
        } else {
            when(rsMock.getInt(1)).thenReturn(0);
        }

        when(psMock.executeQuery()).thenReturn(rsMock);
        when(connection.prepareStatement(anyString())).thenReturn(psMock);
        try {
            conditionalConverter.executeConverter(new ChangeLogContext(connection, DbType.POSTGRESQL, dbVersion));
        } catch (DbConverterExecutionException e) {
            if (!indexFailed) {
                Assert.fail("Shouldn't have thrown an exception");
            }
        }
    }
}