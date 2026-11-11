package org.artifactory.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for recording metrics related to external service HTTP requests.
 *
 * @author Amir Avidan
 */
@Slf4j
@UtilityClass
public class ExternalClientsServiceMetrics {

    private static final String METRIC_NAME = "http.external.requests";
    public static final String TARGET_REMOTE = "remote";

    /**
     * Records metrics for an external service HTTP request.
     *
     * @param targetService The name of the target service (e.g., "xray", "access", "curation")
     * @param latencyMs     The latency in milliseconds (should be >= 0)
     */
    public static void recordRequest(String targetService, long latencyMs) {
        MeterRegistry registry = ContextHelper.get().beanForType(MeterRegistry.class);
        String group = CurrentActivity.GROUP.get("");
        String packageType = CurrentActivity.PACKAGE.get("");
        if (latencyMs < 0) {
            log.warn("Negative latency provided for external service request: {} ms", latencyMs);
            latencyMs = 0;
        }
        registry.timer(METRIC_NAME, "target", targetService, "group", group, "package", packageType)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        // Also update the performance counters for the current download request (if any).
        // Use createIfNotExists=true so that external-service wait time is not lost for calls that
        // happen before DownloadServiceImpl.processDownloadInternal prepares the counters
        // (e.g. the early Curation cache pre-check during RemoteRepoBase.internalGetInfo).
        DownloadPerfCounters downloadPerfCounters = RequestThreadLocal.getDownloadPerfCounters(true);
        if (downloadPerfCounters != null) {
            downloadPerfCounters.addExternalWaitTime(targetService, latencyMs);
        }
    }
}

