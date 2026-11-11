package org.artifactory.metrics.framework;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * A class that monitors the last usage of a service, endpoint, etc and
 * notifies if it has not been used for a specified period.
 *
 * @author Igor Usenko
 */
@Slf4j
public class LastUsageMonitor {

    public static final int NOT_USED = -1;

    private final long maxIdleTime;
    private final String message;
    private final Supplier<Long> timeSupplier;

    private LastUsage lastUsage;

    public LastUsageMonitor(long maxIdleTime, String message, Supplier<Long> timeSupplier) {
        this.maxIdleTime = maxIdleTime;
        this.message = message;
        this.timeSupplier = timeSupplier;
        this.lastUsage = new LastUsage(NOT_USED, false);
    }

    public synchronized void register() {
        lastUsage = new LastUsage(timeSupplier.get(), false);
    }

    public synchronized void check() {
        if (lastUsage.timestamp() == NOT_USED) {
            log.debug("No previous usage registered");
            return;
        }
        boolean timeToNotify = lastUsage.timestamp() + maxIdleTime < timeSupplier.get();
        if (timeToNotify) {
            if (lastUsage.notified()) {
                log.debug("Already notified - skipping");
            } else {
                log.warn(message);
                lastUsage = new LastUsage(lastUsage.timestamp(), true);
            }
        } else {
            log.debug("Idle time is ok - no need to notify");
        }
    }

    private record LastUsage(long timestamp, boolean notified) {
    }
}
