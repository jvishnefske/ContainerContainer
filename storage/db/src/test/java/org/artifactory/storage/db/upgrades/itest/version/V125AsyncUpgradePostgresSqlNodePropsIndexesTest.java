package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.itest.UniqueJvmForkTestIdentifier;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.storage.db.version.converter.SkippableScriptFileDbConverter;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.PostgresqlUtils;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.converter.SkippableVersions.SkippableConverters.NODE_PROPS_VARCHAR_PATTERN_OPS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Tomer Slutsky
 * This class covers the node_props converters v120-v125
 */
public class V125AsyncUpgradePostgresSqlNodePropsIndexesTest extends AsyncUpgradeBaseTest {

    @DataProvider
    public static Object[][] versionProvider() {
        return new Object[][] {
                {ArtifactoryDBVersion.v120, ArtifactoryDBVersion.v121},
                {ArtifactoryDBVersion.v122, ArtifactoryDBVersion.v123},
                {ArtifactoryDBVersion.v123, ArtifactoryDBVersion.v124},
        };
    }

    @Test
    public void testNodesPropsPropValueKeyIndex() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "node_props_prop_value_key_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query =
                "CREATE INDEX node_props_prop_value_key_idx ON node_props USING btree (" +
                        "prop_value varchar_pattern_ops, prop_key varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    @Test
    public void testNodesPropsPropKeyValueIndex() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "node_props_prop_key_value_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query =
                "CREATE INDEX node_props_prop_key_value_idx ON node_props USING btree (" +
                        "prop_key varchar_pattern_ops, prop_value varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    @Test
    public void testNodesPropsNodePropValueIndex() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "node_props_node_prop_value_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query =
                "CREATE INDEX node_props_node_prop_value_idx ON node_props USING btree (" +
                        "node_id, prop_key varchar_pattern_ops, prop_value varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    @Test(dataProvider = "versionProvider")
    public void testNodesPropsTestIndexSkipped(ArtifactoryDBVersion firstVer, ArtifactoryDBVersion secondVer) {
        prepareTestInstanceForSkippableConversion(NODE_PROPS_VARCHAR_PATTERN_OPS.getName());
        ChangeLogContext changeLogContextFirst =
                new ChangeLogContext(Mockito.mock(Connection.class), dbProperties.getDbType(),
                        firstVer);
        ChangeLogContext changeLogContextSecond =
                new ChangeLogContext(Mockito.mock(Connection.class), dbProperties.getDbType(),
                        secondVer);
        SkippableScriptFileDbConverter spyFirstVer = spy((SkippableScriptFileDbConverter) firstVer.converter());
        SkippableScriptFileDbConverter spySecondVer = spy((SkippableScriptFileDbConverter) secondVer.converter());
        Assert.assertFalse(spyFirstVer.testConditions(changeLogContextFirst));
        Assert.assertFalse(spySecondVer.testConditions(changeLogContextSecond));
        verify(spyFirstVer, times(0)).parentTestConditions(changeLogContextFirst);
        verify(spySecondVer, times(0)).parentTestConditions(changeLogContextSecond);
    }

    @Test(dataProvider = "versionProvider")
    public void testNodesPropsTestIndexDoesntSkipped(ArtifactoryDBVersion firstVer, ArtifactoryDBVersion secondVer) {
        prepareTestInstanceForSkippableConversion("fake");
        ChangeLogContext changeLogContextFirst =
                new ChangeLogContext(Mockito.mock(Connection.class), dbProperties.getDbType(),
                        firstVer);
        ChangeLogContext changeLogContextSecond =
                new ChangeLogContext(Mockito.mock(Connection.class), dbProperties.getDbType(),
                        secondVer);
        SkippableScriptFileDbConverter spyFirstVer = spy((SkippableScriptFileDbConverter) firstVer.converter());
        SkippableScriptFileDbConverter spySecondVer = spy((SkippableScriptFileDbConverter) secondVer.converter());
        doReturn(true).when(spyFirstVer).parentTestConditions(any(ChangeLogContext.class));
        doReturn(true).when(spySecondVer).parentTestConditions(any(ChangeLogContext.class));
        spyFirstVer.testConditions(changeLogContextFirst);
        spySecondVer.testConditions(changeLogContextSecond);
        verify(spyFirstVer, times(1)).parentTestConditions(changeLogContextFirst);
        verify(spySecondVer, times(1)).parentTestConditions(changeLogContextSecond);
    }

    private void assertIndexDef(String index, String indexDef) {
        Assert.assertEquals(indexDef, index);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v125;
    }
}
