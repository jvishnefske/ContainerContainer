package org.artifactory.storage.db.aql.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.artifactory.aql.AqlStatsCollectionService;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonRemoteStreamer;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.common.ConstantValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Collect various AQL statistics in order for it to be sent via Call Home.
 *
 * @author Andrey Tashlitsky
 */
@Service
public class AqlStatsCollectionServiceImpl implements AqlStatsCollectionService {

    private static final Logger log = LoggerFactory.getLogger(AqlStatsCollectionServiceImpl.class);
    private static final int CACHE_EXPIRY_HOURS = 48;
    private final Cache<String, AtomicLong> remoteSearchesPerRepo = CacheBuilder.newBuilder()
            .maximumSize(ConstantValues.aqlTransitiveCollectStatsCacheSize.getInt())
            .expireAfterWrite(CACHE_EXPIRY_HOURS, TimeUnit.HOURS)
            .build();
    private final ConcurrentMap<String, AtomicLong> responseCodeStats = new ConcurrentHashMap<>();

    @Override
    public void collectRemoteSearchStats(AqlRestResult aqlRestResult) {
        if (!ConstantValues.aqlTransitiveCollectStatsEnabled.getBoolean()) {
            return;
        }
        try {
            // Get all the remote streamers from the multi streamer
            AqlJsonMultiStreamer multiStreamer = (AqlJsonMultiStreamer) aqlRestResult;
            List<AqlJsonRemoteStreamer> remoteStreamers = this.getAllRemoteStreamers(multiStreamer);
            // Put all info into maps
            for (AqlJsonRemoteStreamer remoteStreamer : remoteStreamers) {
                String repoName = remoteStreamer.getBaseRepoName();
                // Update the search count on this repo
                AtomicLong searchCount = remoteSearchesPerRepo.getIfPresent(repoName);
                if (searchCount == null) {
                    remoteSearchesPerRepo.put(repoName, new AtomicLong(1));
                } else {
                    searchCount.incrementAndGet();
                }
                // Update the response code stats
                String statusClass =
                        String.valueOf(remoteStreamer.getResponse().getStatusLine().getStatusCode()).substring(0, 1);
                AtomicLong statusCodeCount = responseCodeStats.putIfAbsent(statusClass + "xx", new AtomicLong(1));
                if (statusCodeCount != null) {
                    statusCodeCount.incrementAndGet();
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect remote search info for call home! {}", e.getMessage());
            log.debug("Failed to collect remote search info for call home!", e);
        }
    }

    private List<AqlJsonRemoteStreamer> getAllRemoteStreamers(AqlJsonMultiStreamer multiStreamer) {
        return multiStreamer.getStreamers().stream()
                .filter(streamer -> streamer instanceof AqlJsonRemoteStreamer)
                .map(streamer -> (AqlJsonRemoteStreamer) streamer)
                .collect(Collectors.toList());
    }

    @Override
    public void clearStats() {
        remoteSearchesPerRepo.invalidateAll();
        responseCodeStats.clear();
    }

    @Override
    public Cache<String, AtomicLong> getRemoteSearchesPerRepo() {
        return remoteSearchesPerRepo;
    }

    @Override
    public Map<String, AtomicLong> getResponseCodeStats() {
        return responseCodeStats;
    }
}
