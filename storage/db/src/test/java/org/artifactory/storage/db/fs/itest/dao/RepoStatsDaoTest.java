package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.storage.db.fs.dao.RepoStatsDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.repo.dao.RepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.itest.RepositoryConfigDaoTest;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class RepoStatsDaoTest extends DbBaseTest {

    @Autowired
    private RepoStatsDao repoStatsDao;

    @Autowired
    private RepositoryConfigDao repositoryConfigDao;

    private final String repoKey = "test-repo";

    @BeforeMethod
    public void setup() throws SQLException {
        String repoKey = "test-repo";
        DbRepositoryConfig repoConfig =
                new DbRepositoryConfig(repoKey, "local", null, RepoType.Maven.getType(), 1, 10000,
                        3, JsonUtils.getInstance()
                        .valueToByteArray(new RepositoryConfigDaoTest.TestBlob(1)));
        repositoryConfigDao.insert(repoConfig);
    }

    @AfterMethod
    public void cleanup() throws SQLException {
        repositoryConfigDao.deleteAll();
        repoStatsDao.deleteAll();
    }

    @Test
    public void saveTest() throws SQLException {
        long lastDownloaded = 10000L;
        long lastUploaded = 20000L;
        long lastStatsModified = 25000L;
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);

        RepoStatDto repoStat = RepoStatDto.builder()
                .lastDownloaded(lastDownloaded)
                .lastUploaded(lastUploaded)
                .build();
        repoStatsDao.save(repoKey, repoStat);

        RepoStatDto actual = repoStatsDao.get(repoKey);
        assertEquals(lastDownloaded, actual.getLastDownloaded());
        assertEquals(lastUploaded, actual.getLastUploaded());
        assertEquals(lastStatsModified, actual.getLastStatisticsModified());
    }

    @Test
    public void updateTest() throws SQLException {
        long lastDownloaded = 10000L;
        long updatedLastDownloaded = 20000L;
        long lastStatsModified = 15000L;
        long updatedLastStatsModified = 25000L;
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);

        RepoStatDto repoStat = RepoStatDto.builder()
                .lastDownloaded(lastDownloaded)
                .build();
        repoStatsDao.save(repoKey, repoStat);

        // Update the last modified time
        RepoStatDto updatedRepoStat = RepoStatDto.builder()
                .lastDownloaded(updatedLastDownloaded)
                .build();

        repoStatsDao.setCurrentTimeSupplier(() -> updatedLastStatsModified);

        repoStatsDao.update(repoKey, updatedRepoStat);

        RepoStatDto updated = repoStatsDao.get(repoKey);
        assertEquals(updatedLastDownloaded, updated.getLastDownloaded());
        assertEquals(updatedLastStatsModified, updated.getLastStatisticsModified());
    }

    @Test
    public void existsTest() throws SQLException {
        repoStatsDao.save(repoKey, RepoStatDto.builder()
                .lastDownloaded(10000L)
                .build());

        assertTrue(repoStatsDao.exists(repoKey));
    }

    @Test
    public void existsEmptyTest() throws SQLException {
        assertFalse(repoStatsDao.exists(repoKey));
    }

    @Test
    public void upsertUpdateRecordTest() throws SQLException {
        long lastDownloaded = 10000L;
        long lastStatsModified = 15000L;
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);

        RepoStatDto repoStatDto = RepoStatDto.builder()
                .lastDownloaded(lastDownloaded)
                .build();

        repoStatsDao.save(repoKey, repoStatDto);
        RepoStatDto retrieved = repoStatsDao.get(repoKey);
        assertEquals(lastDownloaded, retrieved.getLastDownloaded());

        long updatedLastDownloaded = 20000L;
        RepoStatDto updatedRepoStat = RepoStatDto.builder()
                .lastDownloaded(updatedLastDownloaded)
                .build();

        repoStatsDao.upsert(repoKey, updatedRepoStat);

        retrieved = repoStatsDao.get(repoKey);
        assertEquals(updatedLastDownloaded, retrieved.getLastDownloaded());
        assertEquals(lastStatsModified, retrieved.getLastStatisticsModified());
    }

    @Test
    public void upsertSaveRecordTest() throws SQLException {
        long lastDownloaded = 10000L;
        long lastStatsModified = 15000L;
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);
        RepoStatDto repoStat = RepoStatDto.builder()
                .lastDownloaded(lastDownloaded)
                .build();

        repoStatsDao.upsert(repoKey, repoStat);
        RepoStatDto actual = repoStatsDao.get(repoKey);
        assertEquals(lastDownloaded, actual.getLastDownloaded());
        assertEquals(lastStatsModified, actual.getLastStatisticsModified());
    }

    @Test
    public void getTest() throws SQLException {
        long lastDownloaded = 10000L;
        long lastStatsModified = 15000L;
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);

        repoStatsDao.save(repoKey, RepoStatDto.builder()
                .lastDownloaded(lastDownloaded)
                .build());
        RepoStatDto actual = repoStatsDao.get(repoKey);
        assertEquals(lastDownloaded, actual.getLastDownloaded());
        assertEquals(lastStatsModified, actual.getLastStatisticsModified());
    }

    @Test
    public void getEmptyTest() throws SQLException {
        RepoStatDto actual = repoStatsDao.get(repoKey);
        assertNull(actual);
    }

    @Test(dataProvider = "deltaScenarios")
    public void getAllByDeltaTest(long lastStatsModified, long queryDelta, int expectedSize) throws SQLException {
        repoStatsDao.setCurrentTimeSupplier(() -> lastStatsModified);
        repoStatsDao.save(repoKey, RepoStatDto.builder()
                .lastDownloaded(10)
                .build());

        List<RepoStatDto> actual = repoStatsDao.getAllByDelta(queryDelta);
        assertEquals(expectedSize, actual.size());
    }

    @DataProvider(name = "deltaScenarios")
    public Object[][] provideDeltaScenarios() {
        return new Object[][]{
                {20, 30, 0},
                {20, 15, 1},
                {20, 0, 1},
        };
    }
}
