package org.artifactory.storage.db.fs.service;

import com.google.common.collect.Maps;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class AbstractStatsServiceTest {

    @Mock
    AbstractStatsService statsService;

    private ConcurrentMap<RepoPath, AbstractStatsService.StatsEvent> statsEvents = Maps.newConcurrentMap();

    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        when(statsService.getStatsEvents()).thenReturn(statsEvents);
        when(statsService.calculateMetadataStats()).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void calculateMetadataStats() {
        RepoPath path = RepoPathFactory.create("repoKey", "path");

        Map<RepoPath, MetadataStats> metadataStatsMap = statsService.calculateMetadataStats();
        MetadataStats metadataStats = metadataStatsMap.get(path);
        assertNull(metadataStats);

        AbstractStatsService.StatsEvent statsEvent = new AbstractStatsService.StatsEvent(path);
        statsEvent.update("admin", 1716707999);
        statsEvents.put(path, statsEvent);

        metadataStatsMap = statsService.calculateMetadataStats();
        metadataStats = metadataStatsMap.get(path);
        assertNotNull(metadataStats);
        assertEquals(1, metadataStats.downloadCount());
        assertEquals(1716707999, metadataStats.lastDownloaded());
        assertEquals(0, metadataStats.remoteLastDownloaded());

        statsEvent.update("non_admin", 1716708460);

        metadataStatsMap = statsService.calculateMetadataStats();
        metadataStats = metadataStatsMap.get(path);
        assertNotNull(metadataStats);
        assertEquals(2, metadataStats.downloadCount());
        assertEquals(1716708460, metadataStats.lastDownloaded());
        assertEquals(0, metadataStats.remoteLastDownloaded());
    }
}