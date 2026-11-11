package org.artifactory.concurrent;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

/**
 * @author Uriah Levy
 * @see java.util.concurrent.ThreadPoolExecutor
 */
@Builder
@Data
public class ThreadPoolConfig {
    // Minimal amount of threads in the pool
    private int corePoolSize;
    // Max amount of threads in the pool
    private int maxThreads;
    private long keepAliveTimeout;
    private TimeUnit keepAliveTimeoutTimeUnit;
    private BlockingQueue<Runnable> queue;
    private String threadNameFormat;
    private boolean daemon;
    // Whether or not threads in the pool can timeout and terminate in the absence of tasks in the queue
    private boolean allowCoreThreadTimeOut;
    private RejectedExecutionHandler rejectedExecutionHandler;
}