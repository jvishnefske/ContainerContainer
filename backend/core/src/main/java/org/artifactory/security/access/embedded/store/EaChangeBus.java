/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers {@link EaChange} events to listeners, in publication order, on one daemon thread.
 * <p>
 * {@link #publish(EaChange)} never blocks: events wait in a bounded queue ({@link #DEFAULT_CAPACITY} by default).
 * When the queue is full the event is dropped and the bus remembers that it overflowed; the next delivery then
 * discards everything still queued and sends a single {@link EaChange.EverythingChanged} instead, so a listener that
 * drops all cached state on that event never misses a change.
 * <p>
 * Shared state: the listener list and the overflow flag are touched by publishing threads and the delivery thread,
 * so they are a {@link CopyOnWriteArrayList} and an {@link AtomicBoolean}. This is the minimal shared handle the
 * asynchronous hand-off needs.
 * <p>
 * Listener exceptions are logged and do not stop delivery to other listeners or later events.
 */
public final class EaChangeBus implements AutoCloseable {

    /** The queue capacity of {@link #EaChangeBus()}. */
    public static final int DEFAULT_CAPACITY = 1024;

    private static final Logger log = LoggerFactory.getLogger(EaChangeBus.class);

    /** Receives committed changes on the bus thread. */
    @FunctionalInterface
    public interface Listener {
        /**
         * Handles one change. Runs on the bus thread; it should be quick and must not call back into the bus.
         *
         * @param change the committed change
         */
        void onChange(EaChange change);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean overflowed = new AtomicBoolean();
    private final ThreadPoolExecutor executor;

    /** Creates a bus with a queue of {@link #DEFAULT_CAPACITY} events. */
    public EaChangeBus() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a bus with the given queue capacity.
     *
     * @param capacity the number of events that may wait for delivery, at least 1
     */
    public EaChangeBus(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "embedded-access-change-bus");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Registers a listener for all later events.
     *
     * @param listener the listener
     */
    public void subscribe(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    public void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Queues an event for delivery, or records an overflow if the queue is full. Events published after
     * {@link #close()} are ignored.
     *
     * @param change the committed change
     */
    public void publish(EaChange change) {
        Objects.requireNonNull(change, "change");
        if (executor.isShutdown()) {
            log.debug("Change bus closed; dropping {}", change);
            return;
        }
        try {
            executor.execute(() -> deliver(change));
        } catch (RejectedExecutionException e) {
            if (!executor.isShutdown() && !overflowed.getAndSet(true)) {
                log.warn("Embedded access change queue is full; coalescing into one 'everything changed' event");
            }
        }
    }

    private void deliver(EaChange change) {
        EaChange effective = change;
        if (overflowed.getAndSet(false)) {
            executor.getQueue().clear();
            effective = new EaChange.EverythingChanged();
        }
        for (Listener listener : listeners) {
            try {
                listener.onChange(effective);
            } catch (RuntimeException e) {
                log.error("Embedded access change listener failed on {}", effective, e);
            }
        }
    }

    /** Stops the bus thread after the queued events are delivered, waiting at most five seconds. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
