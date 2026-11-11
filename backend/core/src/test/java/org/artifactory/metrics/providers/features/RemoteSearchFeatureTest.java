package org.artifactory.metrics.providers.features;

import org.artifactory.aql.AqlStatsCollectionService;
import org.artifactory.aql.callhome.RemoteSearchStats;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class RemoteSearchFeatureTest {

    private final RemoteSearchFeature remoteSearchFeature =
            new RemoteSearchFeature(mock(AqlStatsCollectionService.class));

    @Test
    public void testCalcRemoteSearchStats() {
        Map<String, AtomicLong> remoteSearchesPerRepo = new HashMap<>();
        remoteSearchesPerRepo.put("generic-remote-1", new AtomicLong(5));
        remoteSearchesPerRepo.put("generic-remote-2", new AtomicLong(2));
        remoteSearchesPerRepo.put("generic-remote-3", new AtomicLong(7));
        Map<String, AtomicLong> responseCodeStats = new HashMap<>();
        responseCodeStats.put("2xx", new AtomicLong(8));
        responseCodeStats.put("3xx", new AtomicLong(2));
        responseCodeStats.put("4xx", new AtomicLong(1));
        responseCodeStats.put("5xx", new AtomicLong(3));
        RemoteSearchStats remoteSearchStats =
                remoteSearchFeature.calcRemoteSearchStats(remoteSearchesPerRepo, responseCodeStats);
        assertEquals(remoteSearchStats.getTopRemoteSearchesPerRepo(), 7);
        assertEquals(remoteSearchStats.getTotalRemoteSearchesInAllRepos(), 14);
        assertEquals(remoteSearchStats.getTotalNumberOfSearchedRepos(), 3);
        assertEquals(remoteSearchStats.getAvgRemoteSearchesPerRepo(), (double) 14 / 3);
        assertEquals(remoteSearchStats.getResponseCodeStats().get("2xx"), Long.valueOf(8));
        assertEquals(remoteSearchStats.getResponseCodeStats().get("3xx"), Long.valueOf(2));
        assertEquals(remoteSearchStats.getResponseCodeStats().get("4xx"), Long.valueOf(1));
        assertEquals(remoteSearchStats.getResponseCodeStats().get("5xx"), Long.valueOf(3));
    }

    @Test
    public void testCalcRemoteSearchStatsZero() {
        Map<String, AtomicLong> remoteSearchesPerRepo = new HashMap<>();
        Map<String, AtomicLong> responseCodeStats = new HashMap<>();
        RemoteSearchStats remoteSearchStats =
                remoteSearchFeature.calcRemoteSearchStats(remoteSearchesPerRepo, responseCodeStats);
        assertEquals(remoteSearchStats.getTopRemoteSearchesPerRepo(), 0);
        assertEquals(remoteSearchStats.getTotalRemoteSearchesInAllRepos(), 0);
        assertEquals(remoteSearchStats.getTotalNumberOfSearchedRepos(), 0);
        assertEquals(remoteSearchStats.getAvgRemoteSearchesPerRepo(), 0.0);
    }
}
