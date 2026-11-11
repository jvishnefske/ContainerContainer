package org.artifactory.metrics.framework;

import java.io.IOException;
import java.io.Writer;

/**
 * Writes the current snapshot of metrics in the OpenMetrics format.  This interface allows {@link MetricsService}
 * to serialize third-party metrics (Micrometer, DropWizard, etc.) directly to the OpenMetrics stream, without
 * converting them to JFrog's metrics first.  This "short" serialization route solves three problems:
 * <il>
 *     <li>
 *         Save memory - JFrog's metrics collector always keeps the last metrics snapshot in memory. If we go
 *         through it for Spring metrics (by implementing the {@link org.jfrog.metrics.model.MetricProvider}), we will
 *         keep most of the counters twice - once within the Micrometer registry and once more in JFrog snapshot.
 *     </li>
 *     <li>
 *         Keep original Spring metric names - when JFrog collector serializes metrics, it always appends the provider
 *         name (as a prefix) and a metric-type-specific suffix ("_total", "_seconds", etc.). Creating non-standard
 *         metric names requires creating custom Grafana dashboards, instead of using the standard ones.
 *     </li>
 *     <li>
 *         Save CPU - the model conversion between Micrometer and JFrog will consume CPU (if we would implement it).
 *     </li>
 * </il>
 */
public interface OpenMetricsWriter {
    void write(Writer writer, boolean onlyApplicationMetrics) throws IOException;
}
