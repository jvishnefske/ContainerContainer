/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.wasm;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bounded worker pool every WASM step runs on (W4): a fixed number of daemon threads and a bounded queue. A
 * step that cannot be queued is refused at once rather than waiting; a step that misses its deadline (queue time
 * included) is cancelled with an interrupt, which Endive's interpreter turns into {@code WasmInterruptedException}
 * at the next branch or call, so a looping module stops within one instruction block. The pool is owned by the
 * plugin host and shared by all WASM plugins.
 */
public final class WasmWorkers {

    /** Worker threads of the production pool. */
    public static final int DEFAULT_THREADS = 8;
    /** Queued steps of the production pool. */
    public static final int DEFAULT_QUEUE = 32;

    private final ThreadPoolExecutor executor;

    /** A pool of {@code threads} workers and a queue of {@code queue} steps. */
    public WasmWorkers(int threads, int queue) {
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), task -> {
                    Thread thread = new Thread(task, "wasm-plugin-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    /** A pool with {@link #DEFAULT_THREADS} workers and {@link #DEFAULT_QUEUE} queued steps. */
    public static WasmWorkers standard() {
        return new WasmWorkers(DEFAULT_THREADS, DEFAULT_QUEUE);
    }

    /**
     * Runs {@code task} on a worker and waits at most {@code timeoutMs}. Never throws.
     *
     * @return the task's output, or a {@link StepOutput.Failed} for a full pool, a missed deadline (the task is
     *         interrupted), an interrupted caller or a task that threw
     */
    public StepOutput run(Callable<StepOutput> task, long timeoutMs) {
        Future<StepOutput> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            return new StepOutput.Failed(StepOutput.Failure.BUSY, "all WASM workers are busy");
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new StepOutput.Failed(StepOutput.Failure.TIMEOUT, "step exceeded " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new StepOutput.Failed(StepOutput.Failure.TIMEOUT, "the calling thread was interrupted");
        } catch (ExecutionException e) {
            return new StepOutput.Failed(StepOutput.Failure.TRAP, String.valueOf(e.getCause()));
        }
    }

    /** @return the number of steps running now; for tests */
    int active() {
        return executor.getActiveCount();
    }
}
