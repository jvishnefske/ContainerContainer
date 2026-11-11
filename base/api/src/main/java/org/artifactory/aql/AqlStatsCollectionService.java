package org.artifactory.aql;

import com.google.common.cache.Cache;
import org.artifactory.aql.result.AqlRestResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Andrey Tashlitsky
 */
public interface AqlStatsCollectionService {

    /**
     * Collect various remote search related statistics.
     *
     * @param aqlRestResult The result of the request to the remote.
     */
    void collectRemoteSearchStats(AqlRestResult aqlRestResult);

    /**
     * Clears the Remote Search statistics map.
     */
    void clearStats();

    /**
     * Getter for the remote searches per repository map.
     *
     * @return Remote searches per repository map.
     */
    Cache<String, AtomicLong> getRemoteSearchesPerRepo();

    /**
     * Getter for the remote search response codes map.
     *
     * @return Remote search response codes map
     */
    Map<String, AtomicLong> getResponseCodeStats();
}
