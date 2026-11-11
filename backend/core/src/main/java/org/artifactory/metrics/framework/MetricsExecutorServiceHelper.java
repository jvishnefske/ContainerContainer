package org.artifactory.metrics.framework;

import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Creates a ScheduledExecutorService instance to be injected into metrics services.
 *
 * @author Oleksandr Legenkiy
 */
@Component
public class MetricsExecutorServiceHelper {

    public ScheduledExecutorService createMetricsScheduledExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }
}
