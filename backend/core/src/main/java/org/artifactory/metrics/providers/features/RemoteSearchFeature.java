package org.artifactory.metrics.providers.features;

import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.aql.AqlStatsCollectionService;
import org.artifactory.aql.callhome.RemoteSearchStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents the Remote Search feature group of the CallHome feature.
 *
 * @author Andrey Tashlitsky
 */
@Component
public class RemoteSearchFeature implements CallHomeFeature {

    private final AqlStatsCollectionService aqlStatsCollectionService;

    @Autowired
    public RemoteSearchFeature(AqlStatsCollectionService aqlStatsCollectionService) {
        this.aqlStatsCollectionService = aqlStatsCollectionService;
    }

    @Override
    public FeatureGroup getFeature() {
        FeatureGroup remoteSearchFeature = new FeatureGroup("remote_search");
        remoteSearchFeature.addFeatureAttribute("statistics",
                this.calcRemoteSearchStats(this.aqlStatsCollectionService.getRemoteSearchesPerRepo().asMap(),
                        this.aqlStatsCollectionService.getResponseCodeStats()));
        aqlStatsCollectionService.clearStats();
        return remoteSearchFeature;
    }

    RemoteSearchStats calcRemoteSearchStats(Map<String, AtomicLong> remoteSearchesPerRepo,
            Map<String, AtomicLong> responseCodeStats) {
        // Update the top number of remote searches
        long topRemoteSearchesPerRepo =
                remoteSearchesPerRepo.values().stream().mapToLong(AtomicLong::get).max().orElse(0);
        // Update Total number of searches, total number of repos, Avg
        long totalRemoteSearchesInAllRepos = remoteSearchesPerRepo.values().stream().mapToLong(AtomicLong::get).sum();
        long totalNumberOfSearchedRepos = remoteSearchesPerRepo.keySet().size();
        double AvgRemoteSearchesPerRepo = totalNumberOfSearchedRepos != 0 ?
                (double) totalRemoteSearchesInAllRepos / totalNumberOfSearchedRepos : 0;
        // Copy the response code stats map
        HashMap<String, Long> responseCodeStatsCopy = new HashMap<>();
        responseCodeStats.forEach((k, v) -> responseCodeStatsCopy.put(k, v.get()));
        return new RemoteSearchStats(topRemoteSearchesPerRepo, totalRemoteSearchesInAllRepos,
                totalNumberOfSearchedRepos, AvgRemoteSearchesPerRepo, responseCodeStatsCopy);
    }
}
