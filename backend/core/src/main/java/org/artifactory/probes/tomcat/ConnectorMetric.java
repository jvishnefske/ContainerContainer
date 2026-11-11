package org.artifactory.probes.tomcat;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.system.load.event.ConnectorLoadState;
import org.artifactory.throttling.lh.service.LoadHealerService;

/**
 * @author Igor Usenko
 */
@Builder
@Slf4j
public record ConnectorMetric(
        String name,
        long maxThreads,
        long currentThreadsBusy,
        long processedCount,
        long maxConnections,
        long currentConnections) {

    private static final int MIN_THREADS = 1;
    private static final double THREADS_USAGE_80_PERCENT = 0.8;
    private static final double THREADS_USAGE_100_PERCENT = 1.0;

    public void checkHeavyLoadState(LoadHealerService loadHealerService) {
        if (currentThreadsBusy >= maxThreads) {
            log.info("Connector {}: all threads are busy ({} out of {}), activities that take above 20% of threads: {}",
                    name(), currentThreadsBusy, maxThreads, loadHealerService.getHeavyActivities());

            if (currentConnections < maxConnections) {
                log.info("Connector {}: connections ({}) below max ({})", name(), currentConnections, maxConnections);
            }
            if (currentConnections >= maxConnections) {
                log.info("Connector {}: connections at max ({}), maxConnection ({})", name(), currentConnections,
                        maxConnections);
            }
        } else {
            isActiveThreadsOver80PercentButNotMaxedOut();
        }
    }

    public ConnectorLoadState getConnectorLoadState() {
        if (isActiveThreadsBelow80Percent()) {
            return ConnectorLoadState.HEALTHY;
        } else if (isActiveThreadsOver80PercentButNotMaxedOut()) {
            return ConnectorLoadState.LOADED;
        } else if (isActiveThreadsMaxedOut() && isConnectionsBelowMax()) {
            return ConnectorLoadState.OVERLOADED;
        } else if (isBothActiveThreadsAndConnectionsMaxedOut()) {
            return ConnectorLoadState.OUTAGE;
        } else {
            return ConnectorLoadState.HEALTHY;
        }
    }

    private boolean isBothActiveThreadsAndConnectionsMaxedOut() {
        return isActiveThreadsMaxedOut() && !isConnectionsBelowMax();
    }

    private boolean isActiveThreadsBelow80Percent() {
        return calcActiveThreadsUsage() < THREADS_USAGE_80_PERCENT;
    }

    private boolean isConnectionsBelowMax() {
        return currentConnections < maxConnections;
    }

    private boolean isActiveThreadsMaxedOut() {
        return currentThreadsBusy >= maxThreads;
    }

    private boolean isActiveThreadsOver80PercentButNotMaxedOut() {
        double activeThreadsUsage = calcActiveThreadsUsage();
        if (activeThreadsUsage >= THREADS_USAGE_80_PERCENT && activeThreadsUsage < THREADS_USAGE_100_PERCENT) {
            log.info("Connector: '{}' is under heavy load: {} busy threads out of {}", name(), currentThreadsBusy,
                    maxThreads);
            return true;
        }
        return false;
    }

    private double calcActiveThreadsUsage() {
        return (double) currentThreadsBusy / Math.max(maxThreads, MIN_THREADS);
    }
}