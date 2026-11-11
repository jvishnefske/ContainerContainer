package org.artifactory.storage.db.fs.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.fs.StatsInfo;
import org.artifactory.model.common.RemoteRepoPathImpl;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.StatsImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.lang.String.valueOf;
import static org.artifactory.common.ConstantValues.statsUseRemoteEventsCache;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class StatsPersistingServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    public static final String REPO_KEY_01 = "local-02";
    public static final String PATH_01 = "flow-RTDEV-50078-01.sh";
    public static final String REMOTE_HOST_01 = "127.0.0.1";
    public static final String REMOTE_HOST_02 = "127.0.0.2";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenNoStats(boolean useRemoteEventsCache) {
        setStringSystemProperty(statsUseRemoteEventsCache, valueOf(useRemoteEventsCache));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsPersistingServiceImpl statsPersistingService = new StatsPersistingServiceForTest(registry, null);
        RepoPathImpl repoPath = new RepoPathImpl(REPO_KEY_01, PATH_01);
        RemoteRepoPathImpl remoteRepoPath = new RemoteRepoPathImpl(REMOTE_HOST_01, repoPath);
        remoteDownload(statsPersistingService, remoteRepoPath, "admin", 48L, 1);

        assertNull(statsPersistingService.getStats(repoPath));
    }

    @Test
    void whenCleaningCache() {
        setStringSystemProperty(statsUseRemoteEventsCache, valueOf(true));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsPersistingServiceImpl statsPersistingService = new StatsPersistingServiceForTest(registry, null);
        RepoPathImpl repoPath = new RepoPathImpl(REPO_KEY_01, PATH_01);
        RemoteRepoPathImpl remoteRepoPath = new RemoteRepoPathImpl(REMOTE_HOST_01, repoPath);
        remoteDownload(statsPersistingService, remoteRepoPath, "admin", 48L, 1);

        assertEquals(1, statsPersistingService.getStatsInfoCacheSize());

        AbstractStatsService.StatsEvent statsEvent =
                new AbstractStatsService.StatsEvent(remoteRepoPath, REMOTE_HOST_01);
        statsPersistingService.onRemoveEvent(statsEvent);

        assertEquals(0, statsPersistingService.getStatsInfoCacheSize());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenFileDownloadedRemotelyMoreThanOnce(boolean useRemoteEventsCache) {
        setStringSystemProperty(statsUseRemoteEventsCache, valueOf(useRemoteEventsCache));

        final long downloadCount = 1;
        final long lastDownloaded = 2;
        final String lastDownloadedBy = "local-user";

        final long remoteDownloadCount = 3;
        final long remoteLastDownloaded = 4;
        final String remoteLastDownloadedBy = "remote-user";

        StatsImpl stats = createStats(
                downloadCount, lastDownloaded, lastDownloadedBy,
                remoteDownloadCount, remoteLastDownloaded, remoteLastDownloadedBy,
                REMOTE_HOST_01);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsPersistingServiceImpl statsPersistingService = new StatsPersistingServiceForTest(registry, stats);

        RepoPathImpl repoPath01 = new RepoPathImpl(REPO_KEY_01, PATH_01);
        RemoteRepoPathImpl remoteRepoPath01 = new RemoteRepoPathImpl(REMOTE_HOST_01, repoPath01);
        RemoteRepoPathImpl remoteRepoPath02 = new RemoteRepoPathImpl(REMOTE_HOST_01, repoPath01);
        RemoteRepoPathImpl remoteRepoPath03 = new RemoteRepoPathImpl(REMOTE_HOST_02, repoPath01);

        String user01 = "admin01";
        long downloadedTime01 = 48L;
        int count01 = 1;

        remoteDownload(statsPersistingService, remoteRepoPath01, user01, downloadedTime01, count01);

        String user02 = "admin02";
        long downloadedTime02 = 52L;
        int count02 = 2;

        remoteDownload(statsPersistingService, remoteRepoPath02, user02, downloadedTime02, count02);

        String user03 = "admin03";
        long downloadedTime03 = 62L;
        int count03 = 3;

        remoteDownload(statsPersistingService, remoteRepoPath03, user03, downloadedTime03, count03);

        StatsInfo actual = statsPersistingService.getStats(repoPath01);

        assertLocalStatsNotChanged(actual, downloadCount, lastDownloaded, lastDownloadedBy);
        assertRemoteStatsUpdated(actual, remoteDownloadCount + count01 + count02 + count03,
                downloadedTime03, user03);

        assertEquals(2.0, registry.get("stats.events.size").gauge().value());
        assertEquals(useRemoteEventsCache?  1.0 : 0.0, registry.get("stats.cache.size").gauge().value());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenFileDownloadedRemotelyOnce(boolean useRemoteEventsCache) {
        setStringSystemProperty(statsUseRemoteEventsCache, valueOf(useRemoteEventsCache));

        final long downloadCount = 1;
        final long lastDownloaded = 2;
        final String lastDownloadedBy = "local-user";

        final long remoteDownloadCount = 3;
        final long remoteLastDownloaded = 4;
        final String remoteLastDownloadedBy = "remote-user";

        StatsImpl stats = createStats(
                downloadCount, lastDownloaded, lastDownloadedBy,
                remoteDownloadCount, remoteLastDownloaded, remoteLastDownloadedBy,
                REMOTE_HOST_01);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsPersistingServiceImpl statsPersistingService = new StatsPersistingServiceForTest(registry, stats);

        RepoPathImpl repoPath = new RepoPathImpl(REPO_KEY_01, PATH_01);
        RemoteRepoPathImpl remoteRepoPath = new RemoteRepoPathImpl(REMOTE_HOST_01, repoPath);

        String admin = "admin";
        long downloadedTime = 48L;
        int count = 1;
        remoteDownload(statsPersistingService, remoteRepoPath, admin, downloadedTime, count);

        StatsInfo actual = statsPersistingService.getStats(repoPath);

        assertLocalStatsNotChanged(actual, downloadCount, lastDownloaded, lastDownloadedBy);
        assertRemoteStatsUpdated(actual, remoteDownloadCount + count, downloadedTime, admin);

        assertEquals(1.0, registry.get("stats.events.size").gauge().value());
        assertEquals(useRemoteEventsCache?  1.0 : 0.0, registry.get("stats.cache.size").gauge().value());
    }

    private static void remoteDownload(StatsPersistingServiceImpl statsPersistingService,
            RemoteRepoPathImpl remoteRepoPath,
            String user, long downloadedTime, int count) {
        statsPersistingService.fileDownloadedRemotely(remoteRepoPath.getOrigin(),
                remoteRepoPath.getActualRepoPath().getPath(),
                remoteRepoPath, user, downloadedTime, count);
    }

    private static void assertRemoteStatsUpdated(StatsInfo actual, long remoteDownloadCount, long downloadedTime,
            String admin) {
        assertEquals(remoteDownloadCount, actual.getRemoteDownloadCount());
        assertEquals(downloadedTime, actual.getRemoteLastDownloaded());
        assertEquals(admin, actual.getRemoteLastDownloadedBy());
    }

    private static void assertLocalStatsNotChanged(StatsInfo actual, long downloadCount, long lastDownloaded,
            String lastDownloadedBy
    ) {
        assertEquals(downloadCount, actual.getDownloadCount());
        assertEquals(lastDownloaded, actual.getLastDownloaded());
        assertEquals(lastDownloadedBy, actual.getLastDownloadedBy());
    }

    private static StatsImpl createStats(
            long downloadCount,
            long lastDownloaded,
            String lastDownloadedBy,
            long remoteDownloadCount,
            long remoteLastDownloaded,
            String remoteLastDownloadedBy,
            String origin
    ) {
        StatsImpl result = new StatsImpl();

        result.setDownloadCount(downloadCount);
        result.setLastDownloaded(lastDownloaded);
        result.setLastDownloadedBy(lastDownloadedBy);
        result.setRemoteDownloadCount(remoteDownloadCount);
        result.setRemoteLastDownloaded(remoteLastDownloaded);
        result.setRemoteLastDownloadedBy(remoteLastDownloadedBy);
        result.setOrigin(origin);
        result.setPath(origin);

        return result;
    }

    static class StatsPersistingServiceForTest extends StatsPersistingServiceImpl {

        private final StatsImpl statsFromStorage;

        public StatsPersistingServiceForTest(MeterRegistry meterRegistry, StatsImpl statsFromStorage) {
            super(null, meterRegistry);
            this.statsFromStorage = statsFromStorage;
        }

        @Override
        protected StatsInfo getStatsFromStorage(RepoPath repoPath, long nodeId) {
            return statsFromStorage;
        }
    }
}