package org.artifactory.api.request;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.val;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.ArtifactoryRequest;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.function.Supplier;

public interface GracefulShutdownAware {

    String UBER_TRACE_ID = "uber-trace-id";

    default boolean canBeShutdown() {
        return true;
    }

    /*
    deliberately using guava over caffeine
    checking for cache size in caffeine cache introduced race condition bugs, due to its async eviction behavior
    if the need to remove guava arises, consider changing altogether the data structure used by track
     */
    static Cache<String, Object> createTrackingCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(ConstantValues.gracefulShutdownMaxTrackedRequestCount.getLong())
                .build();
    }

    static void track(Process process, ArtifactoryRequest request, ArtifactoryResponse response,
            Cache<String, Object> tracker, Logger log)
            throws IOException {
        val traceId = MDC.get(UBER_TRACE_ID);
        if (traceId != null) {
            tracker.put(traceId, traceId);
        }
        log.debug("Process {} tracking started", traceId);
        try {
            process.execute(request, response);
        } catch (Exception e) {
            if (isShutdownClassLoaderError(e, log)) {
                log.warn("Request processing interrupted during shutdown: {}", request.getRepoPath(), e);
                return;
            }
            throw e;
        } finally {
            if (traceId != null) {
                tracker.invalidate(traceId);
            }
            log.debug("Process {} tracking stopped", traceId);
        }
    }

    /**
     * Scoped graceful-shutdown tracking for work that is not expressed as {@link Process} with an
     * {@link ArtifactoryRequest}. Same MDC contract as
     * {@link #track(Process, ArtifactoryRequest, ArtifactoryResponse, Cache, Logger)}: {@code tracker} is updated only
     * when MDC {@value #UBER_TRACE_ID} is set; otherwise the supplier still runs but nothing is registered. Unlike the
     * {@code Process} overload, this method does not swallow shutdown-related exceptions.
     */
    static <T> T track(Supplier<T> supplier, Cache<String, Object> tracker, Logger log) {
        val traceId = MDC.get(UBER_TRACE_ID);
        if (traceId != null) {
            tracker.put(traceId, traceId);
        }
        log.debug("Process {} tracking started", traceId);
        try {
            return supplier.get();
        } finally {
            // does not check for "isShutdownClassLoaderError" on purpose
            // on non-void methods, a returned value on exception is harder to make generic
            if (traceId != null) {
                tracker.invalidate(traceId);
            }
            log.debug("Process {} tracking stopped", traceId);
        }
    }

    private static boolean isShutdownClassLoaderError(Throwable e, Logger log) {
        ArtifactoryContext context = ContextHelper.get();
        boolean shuttingDown = (context != null && !context.isReady());
        if (!shuttingDown) {
            log.trace("No shutdown detected (context != null && !context.isReady())");
            return false;
        }

        log.info("Shutdown detected (contextReady=false). Evaluating exception for shutdown handling. throwable={}",
                e.getClass().getName());

        while (e != null) {
            if (e instanceof ClassNotFoundException || e instanceof IllegalStateException) {
                return true;
            }
            e = e.getCause();
        }
        log.info("No shutdown detected (no ClassNotFoundException or IllegalStateException found)");
        return false;
    }

    static boolean noActiveTracking(Cache<String, Object> tracker) {
        val trackingCount = tracker.size();
        return trackingCount == 0;
    }

    @FunctionalInterface
    interface Process {
        void execute(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException;
    }

}
