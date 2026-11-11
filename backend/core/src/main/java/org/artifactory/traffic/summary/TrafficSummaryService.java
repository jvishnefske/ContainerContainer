package org.artifactory.traffic.summary;

import org.artifactory.spring.ReloadableBean;
import org.artifactory.traffic.TrafficEntry;

/**
 * @author kunalm
 */
public interface TrafficSummaryService extends ReloadableBean {

    /**
     * Collects and aggregates traffic summary with the given traffic entry
     *
     * @param trafficEntry TrafficEntry
     */
    void collectTrafficEntry(TrafficEntry trafficEntry);

    /**
     * Save aggregate traffic snapshot
     *
     */
    void saveAggregatedTrafficSnapshot();

    /**
     * Get traffic summary for the last 30 days
     *
     * @return TrafficSummary
     */
    TrafficSummary getMonthlyTrafficSummary();
}
