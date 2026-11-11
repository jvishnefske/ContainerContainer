package org.artifactory.sh.cleanup.events;

/**
 * @author TamirH
 */
public interface EventGcMetric {
    long getCleanedBytes();

    long getEventsCount();

    long getDeleteBinariesCount();

    long getDeleteFailuresCount();

    long getRetryExhaustedCount();

    long getDedupCount();

    long getSquashedCount();
}
