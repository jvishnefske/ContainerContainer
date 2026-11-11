package org.artifactory.activity;

import org.slf4j.MDC;

import java.util.Map;

/**
 * A helper class, implementing AutoClosable interface to use it in try-with-resources blocks
 * to restore the MDC context on close.
 */
public class ActivityScope implements AutoCloseable {
    final Map<String, String> snapshot;

    public ActivityScope() {
        this.snapshot = MDC.getCopyOfContextMap();
    }

    @Override
    public void close() {
        if (this.snapshot != null) {
            MDC.setContextMap(this.snapshot);
        } else {
            MDC.clear();
        }
    }
}
