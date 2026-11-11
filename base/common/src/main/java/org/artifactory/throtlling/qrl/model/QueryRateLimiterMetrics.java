package org.artifactory.throtlling.qrl.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Andrey Tashlitsky
 */
public final class QueryRateLimiterMetrics {

    private final AtomicLong totalPermits = new AtomicLong(0);
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalSlowedDownMillis = new AtomicLong(0);
    private final AtomicLong totalSlowedDownByTimeMillis = new AtomicLong(0);
    private final AtomicInteger totalSlowedDownPercentage = new AtomicInteger(0);
    private final AtomicInteger totalSlowedDownByTimePercentage = new AtomicInteger(0);
    private final AtomicLong totalChargedQueryTime = new AtomicLong(0);

    public void setTotalPermits(long totalPermits) {
        this.totalPermits.set(totalPermits);
    }

    public long getTotalPermits() {
        return totalPermits.getAndSet(0);
    }

    public void incrementTotalQueries() {
        this.totalQueries.incrementAndGet();
    }

    public long getTotalQueries() {
        return this.totalQueries.getAndSet(0);
    }

    public void addTotalSlowedDownMillis(long totalSlowedDownMillis) {
        this.totalSlowedDownMillis.addAndGet(totalSlowedDownMillis);
    }

    public void addTotalSlowedDownByTimeMillis(long totalSlowedDownTimeMillis) {
        this.totalSlowedDownByTimeMillis.addAndGet(totalSlowedDownTimeMillis);
    }

    public void addTotalChargedQueryTime(int queryTime) {
        this.totalChargedQueryTime.addAndGet(queryTime);
    }

    public long getTotalChargedQueryTime() {
        return totalChargedQueryTime.getAndSet(0);
    }

    public long getTotalSlowedDownByTimeQuotaMillis() {
        return totalSlowedDownByTimeMillis.getAndSet(0);
    }

    public long getTotalSlowedDownMillis() {
        return totalSlowedDownMillis.getAndSet(0);
    }

    public void setTotalSlowedDownPercentage(int totalSlowedDownPercentage) {
        this.totalSlowedDownPercentage.set(totalSlowedDownPercentage);
    }

    public void setTotalSlowedDownByTimePercentage(int totalSlowedDownTimePercentage) {
        this.totalSlowedDownByTimePercentage.set(totalSlowedDownTimePercentage);
    }

    public long getTotalSlowedDownByTimePercentage() {
        return totalSlowedDownByTimePercentage.getAndSet(0);
    }

    public long getTotalSlowedDownPercentage() {
        return totalSlowedDownPercentage.getAndSet(0);
    }
}
