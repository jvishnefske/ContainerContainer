package org.artifactory.storage.service;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigChangeReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.repo.cache.RepoStatsCacheService;
import org.artifactory.storage.db.fs.dao.RepoStatsDao;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Set;
import java.util.function.LongSupplier;

public class RepoStatsServiceImplTest extends ArtifactoryHomeBoundTest {

    private final RepoStatsDao repoStatsDao = Mockito.mock(RepoStatsDao.class);
    private final RepoStatsCacheService repoStatsCacheService = Mockito.mock(RepoStatsCacheService.class);
    private final RepositoryService repositoryService = Mockito.mock(RepositoryService.class);
    private RepoStatsServiceImpl repoStatsService;

    @BeforeClass
    public void setup() {
        repoStatsService = new RepoStatsServiceImpl(repoStatsDao, repoStatsCacheService, repositoryService);
        repoStatsService.repoStatsBuffer.clear();
        Mockito.reset(repoStatsDao);
    }

    @AfterMethod
    public void tearDown() {
        repoStatsService.repoStatsBuffer.clear();
        Mockito.reset(repoStatsDao);
    }

    @Test
    public void updateRepoStatsDownload() {
        long lastDownloaded = 10000;
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(lastDownloaded);
        repoStatsService.setCurrentTimeSupplier(currentTimeSupplier);
        String repoKey = "newRepoKey";
        repoStatsService.update(repoKey, RepoStatDto.RepoStatUpdateType.DOWNLOAD, repoKey);
        assertNotNull(repoStatsService.repoStatsBuffer.get(repoKey));
        assertEquals(repoStatsService.repoStatsBuffer.get(repoKey).getLastDownloaded(), lastDownloaded);
    }

    @Test
    public void updateRepoStatsDownloadVirtual() {
        long lastDownloaded = 10000;
        String originalRepoKey = "virtualRepoKey";
        String resolvedRepoKey = "resolvedRepoKey";
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(lastDownloaded);
        repoStatsService.setCurrentTimeSupplier(currentTimeSupplier);
        when(repositoryService.existsByType(RepoDetailsType.VIRTUAL, originalRepoKey)).thenReturn(true);

        repoStatsService.update(resolvedRepoKey, RepoStatDto.RepoStatUpdateType.DOWNLOAD, originalRepoKey);
        assertNotNull(repoStatsService.repoStatsBuffer.get(resolvedRepoKey));
        assertEquals(repoStatsService.repoStatsBuffer.get(resolvedRepoKey).getLastDownloaded(), lastDownloaded);
        assertNotNull(repoStatsService.repoStatsBuffer.get(originalRepoKey));
        assertEquals(repoStatsService.repoStatsBuffer.get(originalRepoKey).getLastDownloaded(), lastDownloaded);
    }

    @Test
    public void updateRepoStatsUpload() {
        long lastUploaded = 10000;
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(lastUploaded);
        repoStatsService.setCurrentTimeSupplier(currentTimeSupplier);
        String repoKey = "newRepoKey";
        repoStatsService.update(repoKey, RepoStatDto.RepoStatUpdateType.UPLOAD, repoKey);
        assertNotNull(repoStatsService.repoStatsBuffer.get(repoKey));
        assertEquals(repoStatsService.repoStatsBuffer.get(repoKey).getLastUploaded(), lastUploaded);
    }

    @Test
    public void updateCachedRepoStatsUpload() {
        long lastUploaded = 10000;
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(lastUploaded);
        repoStatsService.setCurrentTimeSupplier(currentTimeSupplier);
        String remote = "repo";
        String cacheRepo = remote + "-cache";
        repoStatsService.update(cacheRepo, RepoStatDto.RepoStatUpdateType.UPLOAD, remote);
        assertNotNull(repoStatsService.repoStatsBuffer.get(remote));
        assertEquals(repoStatsService.repoStatsBuffer.get(remote).getLastUploaded(), lastUploaded);
    }

    @Test
    public void updateRepoStatsWithExistingKey() {
        String repoKey = "existingRepoKey";
        long initialTime = 10;
        long lastUploaded = 10000;
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(lastUploaded);
        repoStatsService.setCurrentTimeSupplier(currentTimeSupplier);

        RepoStatDto repoStatDto = RepoStatDto.builder().lastDownloaded(initialTime).build();
        repoStatsService.repoStatsBuffer.put(repoKey, repoStatDto);

        repoStatsService.update(repoKey, RepoStatDto.RepoStatUpdateType.DOWNLOAD, repoKey);
        assertTrue(repoStatsService.repoStatsBuffer.get(repoKey).getLastDownloaded() > initialTime);
    }

    @Test
    public void flushStatsTest() throws SQLException {
        String firstKey = "repoKey";
        String secondKey = "repoKey2";

        RepoStatDto repoStatDto = RepoStatDto.builder().lastDownloaded(1000).build();
        repoStatsService.repoStatsBuffer.put(firstKey, repoStatDto);
        repoStatsService.repoStatsBuffer.put(secondKey, repoStatDto);

        repoStatsService.flush();

        verify(repoStatsDao, times(1)).upsert(eq(firstKey), eq(repoStatDto));
        verify(repoStatsDao, times(1)).upsert(eq(secondKey), eq(repoStatDto));

        assertTrue(repoStatsService.repoStatsBuffer.isEmpty());
    }

    @Test
    public void flushStatsReinsertsEntryOnDatabaseFailure() throws SQLException {
        String firstKey = "repoKey";
        String secondKey = "repoKey2";

        RepoStatDto stat = RepoStatDto.builder().lastDownloaded(System.currentTimeMillis()).build();
        repoStatsService.repoStatsBuffer.put(firstKey, stat);
        repoStatsService.repoStatsBuffer.put(secondKey, stat);
        doThrow(SQLException.class).when(repoStatsDao).upsert(eq(firstKey), eq(stat));
        when(repositoryService.existsByRepoKey(eq(firstKey))).thenReturn(true);

        repoStatsService.flush();

        verify(repoStatsDao, times(1)).upsert(eq(secondKey), eq(stat));

        assertEquals(repoStatsService.repoStatsBuffer.size(), 1);
        assertNotNull(repoStatsService.repoStatsBuffer.get(firstKey));
    }

    @Test
    public void flushStatsIgnoresMissingRepoOnDatabaseFailure() throws SQLException {
        String repoKey = "repoKey";
        RepoStatDto stat = RepoStatDto.builder().lastDownloaded(System.currentTimeMillis()).build();
        repoStatsService.repoStatsBuffer.put(repoKey, stat);
        doThrow(SQLException.class).when(repoStatsDao).upsert(eq(repoKey), eq(stat));
        when(repositoryService.existsByRepoKey(eq(repoKey))).thenReturn(false);

        repoStatsService.flush();

        verify(repoStatsDao, times(1)).upsert(eq(repoKey), eq(stat));
        assertTrue(repoStatsService.repoStatsBuffer.isEmpty());
    }

    @Test
    public void onRepoConfigReloadRemovesDeletedRepoKeysFromCache() {
        RepoConfigChangesReload changesReload = mock(RepoConfigChangesReload.class);
        RepoConfigChangeReload change1 = mock(RepoConfigChangeReload.class);
        RepoConfigChangeReload change2 = mock(RepoConfigChangeReload.class);

        when(change1.getRepoKey()).thenReturn("repo1");
        when(change2.getRepoKey()).thenReturn("repo2");
        when(changesReload.filterBy(RepoConfigChange.ConfigAction.REPO_DELETE))
                .thenReturn(Set.of(change1, change2));

        repoStatsService.onRepoConfigReload(changesReload);

        verify(repoStatsCacheService).deleteByKeys(Set.of("repo1", "repo2"));
        assertFalse(repoStatsService.repoStatsBuffer.containsKey("repo1"));
        assertFalse(repoStatsService.repoStatsBuffer.containsKey("repo2"));
    }

    @Test
    public void onRepoConfigReloadHandlesEmptyDeletedRepoKeys() {
        RepoConfigChangesReload changesReload = mock(RepoConfigChangesReload.class);
        when(changesReload.filterBy(RepoConfigChange.ConfigAction.REPO_DELETE)).thenReturn(Set.of());

        repoStatsService.onRepoConfigReload(changesReload);

        verify(repoStatsCacheService).deleteByKeys(Set.of());
    }

    @Test
    void isVirtualRepoSameKey() {
        String key = "generic-local";
        boolean result = repoStatsService.isVirtualRepo(key, key);
        Mockito.verify(repositoryService, Mockito.times(0))
                .existsByType(RepoDetailsType.VIRTUAL, key);
        assertFalse(result);
    }

    @Test
    void isVirtualRepoWithNullOriginalKey() {
        boolean result = repoStatsService.isVirtualRepo("generic-local", null);
        Mockito.verify(repositoryService, Mockito.times(0))
                .existsByType(RepoDetailsType.VIRTUAL, null);
        assertFalse(result);
    }

    @Test
    void isVirtualRepoDifferentKeys() {
        String key = "generic-local";
        String originalKey = "virtual-repo";
        when(repositoryService.existsByType(RepoDetailsType.VIRTUAL, originalKey)).thenReturn(true);
        boolean result = repoStatsService.isVirtualRepo(key, originalKey);
        assertTrue(result);
    }
}
