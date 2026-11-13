/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Deletes expired token records of the embedded Access store periodically.
 * <p>
 * A token record is kept for {@code refreshWindow} after its expiry, because its refresh token may still be used
 * that long ({@code EmbeddedAccessClient.DEFAULT_REFRESH_WINDOW}); only older records are deleted
 * ({@link #cutoff}). An expired access token fails verification on its {@code exp} claim whether or not its record
 * exists, so deleting the record changes no verification outcome.
 * <p>
 * <b>Scheduling.</b> The job owns one daemon thread ({@link ScheduledExecutorService}) for its whole life and is
 * stopped by {@link #close}. Artifactory's Quartz {@code TaskService} is not used: its jobs are Spring-managed
 * classes found through the application context, whereas the store is owned by the embedded Access instance and is
 * not a bean, and the purge needs neither cluster coordination (embedded Access is single node) nor the task UI. The
 * first run is one interval after start; a run that fails is logged and the next run tries again.
 */
public final class EaTokenPurgeJob implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EaTokenPurgeJob.class);

    private final EmbeddedAccessStore store;
    private final Clock clock;
    private final Duration refreshWindow;
    private final ScheduledExecutorService executor;

    private EaTokenPurgeJob(EmbeddedAccessStore store, Clock clock, Duration refreshWindow,
            ScheduledExecutorService executor) {
        this.store = store;
        this.clock = clock;
        this.refreshWindow = refreshWindow;
        this.executor = executor;
    }

    /**
     * Creates a job that is not scheduled; {@link #runOnce} runs it by hand. For tests and one-off use.
     *
     * @param store         the store
     * @param clock         the clock
     * @param refreshWindow how long after expiry a record is kept
     * @return the job
     */
    public static EaTokenPurgeJob unscheduled(EmbeddedAccessStore store, Clock clock, Duration refreshWindow) {
        return new EaTokenPurgeJob(Objects.requireNonNull(store, "store"), Objects.requireNonNull(clock, "clock"),
                Objects.requireNonNull(refreshWindow, "refreshWindow"), null);
    }

    /**
     * Starts the job on its own daemon thread.
     *
     * @param store         the store
     * @param clock         the clock
     * @param refreshWindow how long after expiry a record is kept
     * @param interval      the time between runs, positive
     * @return the running job
     */
    public static EaTokenPurgeJob start(EmbeddedAccessStore store, Clock clock, Duration refreshWindow,
            Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("purge interval must be positive: " + interval);
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "embedded-access-token-purge");
            thread.setDaemon(true);
            return thread;
        });
        EaTokenPurgeJob job = new EaTokenPurgeJob(Objects.requireNonNull(store, "store"),
                Objects.requireNonNull(clock, "clock"), Objects.requireNonNull(refreshWindow, "refreshWindow"),
                executor);
        long millis = interval.toMillis();
        executor.scheduleWithFixedDelay(job::runLogged, millis, millis, TimeUnit.MILLISECONDS);
        return job;
    }

    /**
     * Computes the expiry before which records are deleted. Pure.
     *
     * @param now           the current time
     * @param refreshWindow how long after expiry a record is kept
     * @return {@code now - refreshWindow}
     */
    static Instant cutoff(Instant now, Duration refreshWindow) {
        return now.minus(refreshWindow);
    }

    /**
     * Deletes the records that expired before {@link #cutoff}.
     *
     * @return the number of deleted records, or the store's refusal
     */
    public EaOutcome<Integer> runOnce() {
        return store.purgeExpiredTokens(cutoff(clock.instant(), refreshWindow));
    }

    private void runLogged() {
        try {
            EaOutcome<Integer> outcome = runOnce();
            if (outcome instanceof EaOutcome.Ok<Integer> ok) {
                if (ok.value() > 0) {
                    log.info("Deleted {} expired embedded access token records", ok.value());
                }
            } else {
                log.warn("Embedded access token purge failed: {}", outcome);
            }
        } catch (RuntimeException e) {
            log.warn("Embedded access token purge failed", e);
        }
    }

    /** Stops the schedule; a run in progress finishes. */
    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
