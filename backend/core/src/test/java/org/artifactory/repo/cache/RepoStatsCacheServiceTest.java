package org.artifactory.repo.cache;

import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.storage.db.fs.dao.RepoStatsDao;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RepoStatsCacheServiceTest extends ArtifactoryHomeBoundTest {

    private RepoStatsDao repoStatsDao;

    private RepoStatsCacheService cacheService;

    @BeforeMethod
    public void setUp() throws SQLException {
        repoStatsDao = mock(RepoStatsDao.class);
        cacheService = new RepoStatsCacheService(repoStatsDao);
    }

    @AfterMethod
    public  void tearDown() throws SQLException {
        Mockito.reset(repoStatsDao);
    }

    @Test
    public void getAllFromCacheReturnsCachedData() throws SQLException {
        RepoStatDto stat1 = RepoStatDto.builder().repoKey("repo1").build();
        RepoStatDto stat2 = RepoStatDto.builder().repoKey("repo2").build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1, stat2));
        cacheService.setCurrentTimeSupplier(() -> 200000L);
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1", "repo2"));

        assertEquals(2, result.size());
    }

    @Test
    public void getAllFromCacheUpdatesExistingEntries() throws SQLException {
        String repoKey = "repo1";
        Set<String> repoKeys = Set.of(repoKey);
        RepoStatDto originalStat = RepoStatDto.builder().repoKey(repoKey).lastDownloaded(1000L).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(originalStat));
        cacheService.setCurrentTimeSupplier(() -> 1000L);
        cacheService.getByKeys(Set.of(repoKey));

        RepoStatDto updatedStat = RepoStatDto.builder().repoKey(repoKey).lastDownloaded(2000L).build();
        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of(updatedStat));
        cacheService.setCurrentTimeSupplier(() -> 200000L);

        List<RepoStatDto> result = cacheService.getByKeys(repoKeys);

        assertEquals(1, result.size());
        assertEquals(2000L, result.getFirst().getLastDownloaded());
    }

    @Test
    public void getAllFromCacheAddsNewEntriesToExistingCache() throws SQLException {
        Set<String> repoKeys = Set.of("existing-repo", "new-repo");
        RepoStatDto existingStat = RepoStatDto.builder().repoKey("existing-repo").lastDownloaded(1000L).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(existingStat));
        cacheService.setCurrentTimeSupplier(() -> 10000L);
        cacheService.getByKeys(repoKeys);

        RepoStatDto newStat = RepoStatDto.builder().repoKey("new-repo").lastDownloaded(2000L).build();
        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of(newStat));
        cacheService.setCurrentTimeSupplier(() -> 200000L);

        List<RepoStatDto> result = cacheService.getByKeys(repoKeys);

        assertEquals(2, result.size());
        assertEquals(1, result.stream().filter(stat ->
                "existing-repo".equals(stat.getRepoKey())).count());
        assertEquals(1, result.stream().filter(stat ->
                ("new-repo").equals(stat.getRepoKey())).count());
    }

    @Test
    public void getAllFromCacheReturnsEmptyListWhenCacheEmpty() throws SQLException {
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1"));
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of());
        assertEquals(0, result.size());
    }

    @Test
    public void getAllFromCacheTriggersReloadWhenIntervalElapsed() throws SQLException {
        long currentTime = 10000L;
        cacheService.setCurrentTimeSupplier(() -> currentTime);
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of());

        cacheService.getByKeys(Set.of("repo1", "repo2"));

        verify(repoStatsDao).getAllByDelta(0L);
    }

    @Test
    public void getAllFromCacheHandlesSQLExceptionDuringReload() throws SQLException {
        String repoKey = "existing-repo";
        RepoStatDto existingStat = RepoStatDto.builder().repoKey(repoKey).lastDownloaded(1000L).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(existingStat));
        cacheService.setCurrentTimeSupplier(() -> 10000L);
        cacheService.getByKeys(Set.of(repoKey));

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("Database error"));
        cacheService.setCurrentTimeSupplier(() -> 200000L);

        List<RepoStatDto> result = cacheService.getByKeys(Set.of(repoKey));

        assertEquals(1, result.size());
        assertEquals(repoKey, result.getFirst().getRepoKey());
    }

    @Test
    public void getAllFromCacheSkipsReloadDuringFailure() throws SQLException {
        String repoKey = "existing-repo";
        Set<String> repoKeys = Set.of(repoKey);
        RepoStatDto existingStat = RepoStatDto.builder().repoKey(repoKey).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(existingStat));
        cacheService.setCurrentTimeSupplier(() -> 10000);
        cacheService.getByKeys(repoKeys);

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("Database error"));
        cacheService.setCurrentTimeSupplier(() -> 20000);
        cacheService.getByKeys(repoKeys);

        doReturn(List.of()).when(repoStatsDao).getAllByDelta(anyLong());

        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of());
        cacheService.setCurrentTimeSupplier(() ->
                ConstantValues.repoStatsCacheReloadFailureRetryDelayMillis.getLong() - 1000);
        cacheService.getByKeys(repoKeys);

        verify(repoStatsDao, times(2)).getAllByDelta(anyLong());
    }

    @Test
    public void getAllFromCacheRetriesAfterFailure() throws SQLException {
        String repoKey = "existing-repo";
        Set<String> repoKeys = Set.of(repoKey);
        RepoStatDto existingStat = RepoStatDto.builder().repoKey(repoKey).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(existingStat));
        cacheService.setCurrentTimeSupplier(() -> 10000);
        cacheService.getByKeys(repoKeys);

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("Database error"));
        cacheService.setCurrentTimeSupplier(() -> 20000L);
        cacheService.getByKeys(repoKeys);

        doReturn(List.of()).when(repoStatsDao).getAllByDelta(anyLong());

        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of());
        cacheService.setCurrentTimeSupplier(() -> 50000L);
        cacheService.getByKeys(repoKeys);

        verify(repoStatsDao, times(3)).getAllByDelta(anyLong());
    }

    @Test
    public void getAllFromCacheRetriesAfterFailureDelayExpires() throws SQLException {
        String repoKey = "existing-repo";
        Set<String> repoKeys = Set.of(repoKey);
        RepoStatDto existingStat = RepoStatDto.builder().repoKey("existing-repo").build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(existingStat));
        cacheService.setCurrentTimeSupplier(() -> 10000);
        cacheService.getByKeys(repoKeys);

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("Database error"));
        cacheService.setCurrentTimeSupplier(() -> 20000L);
        cacheService.getByKeys(repoKeys);

        doReturn(List.of()).when(repoStatsDao).getAllByDelta(anyLong());

        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of());
        cacheService.setCurrentTimeSupplier(() -> 200000L);
        cacheService.getByKeys(repoKeys);

        verify(repoStatsDao, times(3)).getAllByDelta(anyLong());
    }

    @Test
    public void getAllFromCacheHandlesConcurrentAccess() throws SQLException, InterruptedException {
        when(repoStatsDao.getAllByDelta(anyLong())).thenReturn(List.of());

        Thread thread1 = new Thread(() -> {
            cacheService.getByKeys(Set.of("repo1", "repo2"));
        });

        Thread thread2 = new Thread(() -> {
            cacheService.getByKeys(Set.of("repo1", "repo2"));
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        verify(repoStatsDao, times(1)).getAllByDelta(anyLong());
    }

    @Test
    public void deleteByKeysRemovesSpecifiedKeysFromCache() throws SQLException {
        RepoStatDto stat1 = RepoStatDto.builder().repoKey("repo1").build();
        RepoStatDto stat2 = RepoStatDto.builder().repoKey("repo2").build();
        RepoStatDto stat3 = RepoStatDto.builder().repoKey("repo3").build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1, stat2, stat3));

        cacheService.getByKeys(Set.of("repo1", "repo2", "repo3"));
        cacheService.deleteByKeys(Set.of("repo1", "repo3"));
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1", "repo2", "repo3"));

        assertEquals(1, result.size());
        assertEquals("repo2", result.getFirst().getRepoKey());
    }

    @Test
    public void deleteByKeysHandlesEmptySet() throws SQLException {
        RepoStatDto stat1 = RepoStatDto.builder().repoKey("repo1").build();
        RepoStatDto stat2 = RepoStatDto.builder().repoKey("repo2").build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1, stat2));

        cacheService.getByKeys(Set.of("repo1", "repo2"));
        cacheService.deleteByKeys(Set.of());
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1", "repo2"));

        assertEquals(2, result.size());
    }

    @Test
    public void deleteByKeysIgnoresNonExistentKeys() throws SQLException {
        RepoStatDto stat1 = RepoStatDto.builder().repoKey("repo1").build();
        RepoStatDto stat2 = RepoStatDto.builder().repoKey("repo2").build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1, stat2));

        cacheService.getByKeys(Set.of("repo1", "repo2"));
        cacheService.deleteByKeys(Set.of("repo3", "repo4"));
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1", "repo2"));

        assertEquals(2, result.size());
    }

    @Test
    public void deleteByKeysHandlesEmptyCache() {
        cacheService.deleteByKeys(Set.of("repo1", "repo2"));
        List<RepoStatDto> result = cacheService.getByKeys(Set.of("repo1", "repo2"));

        assertEquals(0, result.size());
    }

    @Test
    public void shouldSkipReloadWhenFailureRetryDelayNotElapsed() throws SQLException {
        String repoKey = "repo1";
        RepoStatDto stat1 = RepoStatDto.builder().repoKey(repoKey).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1));

        long currentTime = 10000L;
        cacheService.setCurrentTimeSupplier(() -> currentTime);
        cacheService.getByKeys(Set.of(repoKey));

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("DB error"));
        long failureTime = currentTime +
                ConstantValues.repoStatsCacheReloadIntervalMillis.getLong() + 5000;
        cacheService.setCurrentTimeSupplier(() -> failureTime);
        cacheService.getByKeys(Set.of(repoKey));

        long newReloadTime = failureTime +
                ConstantValues.repoStatsCacheReloadFailureRetryDelayMillis.getLong() - 5000;
        cacheService.setCurrentTimeSupplier(() -> newReloadTime);
        boolean result = cacheService.shouldSkipReloadOnRecentFailure();

        assertTrue(result);
    }

    @Test
    public void doesNotSkipReloadWhenFailureRetryDelayElapsed() throws SQLException {
        String repoKey = "repo1";
        RepoStatDto stat1 = RepoStatDto.builder().repoKey(repoKey).build();
        when(repoStatsDao.getAllByDelta(0L)).thenReturn(List.of(stat1));

        long currentTime = 10000L;
        cacheService.setCurrentTimeSupplier(() -> currentTime);
        cacheService.getByKeys(Set.of(repoKey));

        when(repoStatsDao.getAllByDelta(anyLong())).thenThrow(new SQLException("DB error"));
        long failureTime = currentTime +
                ConstantValues.repoStatsCacheReloadIntervalMillis.getLong() + 5000;
        cacheService.setCurrentTimeSupplier(() -> failureTime);
        cacheService.getByKeys(Set.of(repoKey));

        long newReloadTime = failureTime +
                ConstantValues.repoStatsCacheReloadFailureRetryDelayMillis.getLong() + 100000;
        cacheService.setCurrentTimeSupplier(() -> newReloadTime);
        boolean result = cacheService.shouldSkipReloadOnRecentFailure();

        assertFalse(result);
    }

    @Test
    public void doesNotSkipReloadWhenNoRecentFailure() {
        cacheService.setCurrentTimeSupplier(() -> 10000L);
        boolean result = cacheService.shouldSkipReloadOnRecentFailure();

        assertFalse(result);
    }
}
