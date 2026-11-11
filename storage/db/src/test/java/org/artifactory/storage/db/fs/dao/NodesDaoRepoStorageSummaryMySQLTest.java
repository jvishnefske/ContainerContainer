package org.artifactory.storage.db.fs.dao;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.ResultSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the {@link NodesDao#getFilesCount()} and {@link NodesDao#getRepositoriesStorageSummary()}.
 * that test the use of the queries with and without an index.
 *
 * @author Matthew Wang
 */
class NodesDaoRepoStorageSummaryMySQLTest extends ArtifactoryHomeBoundJUnitTest {
    private NodesDao nodesDao;
    private ArtifactoryJdbcHelper jdbcHelper;

    @BeforeEach
    public void setUp() throws Exception {
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.MYSQL);
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(jdbcHelper.executeSelect(any())).thenReturn(resultSetMock);
        nodesDao = new NodesDao(jdbcHelper, dbProperties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void getRepositoriesStorageSummary(String useIndex) throws Exception {
        //Verify mock with skip index property
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), useIndex);
        nodesDao.getRepositoriesStorageSummary();

        if (useIndex.equals("true")) {
            String skipIndexStorageSummary = """
                    SELECT repo,
                    SUM(CASE WHEN node_type = 0 THEN 1 ELSE 0 END) as folders,
                    SUM(CASE WHEN node_type = 1 THEN 1 ELSE 0 END) as files,
                    SUM(bin_length)
                    FROM nodes USE INDEX() GROUP BY repo""";
            verify(jdbcHelper).executeSelect(skipIndexStorageSummary);
        } else {
            String withIndexStorageSummary = """
                    SELECT repo,
                    SUM(CASE WHEN node_type = 0 THEN 1 ELSE 0 END) as folders,
                    SUM(CASE WHEN node_type = 1 THEN 1 ELSE 0 END) as files,
                    SUM(bin_length)
                    FROM nodes GROUP BY repo""";
            verify(jdbcHelper).executeSelect(withIndexStorageSummary);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void getFilesCount(String useIndex) throws Exception {
        //Verify mock with skip index property
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.totalSizeMySqlNoIndex.getPropertyName(), useIndex);
        nodesDao.getFilesCount();

        if (useIndex.equals("true")) {
            String skipIndexFilesCount = "SELECT COUNT(*) FROM nodes USE INDEX () WHERE node_type = 1";
            verify(jdbcHelper).executeSelectCount(skipIndexFilesCount);
        } else {
            String withIndexFilesCount = "SELECT COUNT(*) FROM nodes WHERE node_type = 1";
            verify(jdbcHelper).executeSelectCount(withIndexFilesCount);
        }
    }
}