package org.artifactory.aql.callhome;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Holds information collected each time a Remote (Transitive) Search is called.
 * This information is then reported via Call Home.
 *
 * @author Andrey Tashlitsky
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RemoteSearchStats {

    private final long topRemoteSearchesPerRepo;
    private final long totalRemoteSearchesInAllRepos;
    private final long totalNumberOfSearchedRepos;
    private final double AvgRemoteSearchesPerRepo;
    private final Map<String, Long> responseCodeStats;

    public RemoteSearchStats(long topRemoteSearchesPerRepo, long totalRemoteSearchesInAllRepos,
            long totalNumberOfSearchedRepos, double AvgRemoteSearchesPerRepo, Map<String, Long> responseCodeStats) {
        this.topRemoteSearchesPerRepo = topRemoteSearchesPerRepo;
        this.totalRemoteSearchesInAllRepos = totalRemoteSearchesInAllRepos;
        this.totalNumberOfSearchedRepos = totalNumberOfSearchedRepos;
        this.AvgRemoteSearchesPerRepo = AvgRemoteSearchesPerRepo;
        this.responseCodeStats = responseCodeStats;
    }

    public long getTopRemoteSearchesPerRepo() {
        return topRemoteSearchesPerRepo;
    }

    public long getTotalRemoteSearchesInAllRepos() {
        return totalRemoteSearchesInAllRepos;
    }

    public long getTotalNumberOfSearchedRepos() {
        return totalNumberOfSearchedRepos;
    }

    public double getAvgRemoteSearchesPerRepo() {
        return AvgRemoteSearchesPerRepo;
    }

    public Map<String, Long> getResponseCodeStats() {
        return responseCodeStats;
    }
}
