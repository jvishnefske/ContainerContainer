package org.artifactory.metrics.framework.spring;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.artifactory.metrics.framework.OpenMetricsWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Takes the current snapshot of metrics, collected by Spring (actually Micrometer) and writes them to the given
 * stream (in the OpenMetrics format).
 */
public class SpringOpenMetricsWriter implements OpenMetricsWriter {
    final CollectorRegistry collectorRegistry;
    final CollectorRegistry appMetricsCollectorRegistry;

    public SpringOpenMetricsWriter(CollectorRegistry collectorRegistry, CollectorRegistry appMetricsCollectorRegistry) {
        this.collectorRegistry = collectorRegistry;
        this.appMetricsCollectorRegistry = appMetricsCollectorRegistry;
    }

    @Override
    public void write(Writer writer, boolean onlyApplicationMetrics) throws IOException {
        // Note: multi-tenant services supposed to filter metric samples by tenant id, but
        // Artifactory is currently a single-tenant service, so we can just dump everything.
        
        if (onlyApplicationMetrics) {
            Enumeration<Collector.MetricFamilySamples> appMetricsCollectorRegistrySamples =
                    fixMetricFamilySamplesHelp(appMetricsCollectorRegistry.metricFamilySamples());
            TextFormat.writeOpenMetrics100(writer, appMetricsCollectorRegistrySamples);
        } else {
            Enumeration<Collector.MetricFamilySamples> collectorRegistrySamples =
                    fixMetricFamilySamplesHelp(collectorRegistry.metricFamilySamples());
            TextFormat.writeOpenMetrics100(writer, collectorRegistrySamples);
            
            Enumeration<Collector.MetricFamilySamples> appMetricsCollectorRegistrySamples =
                    fixMetricFamilySamplesHelp(appMetricsCollectorRegistry.metricFamilySamples());
            TextFormat.writeOpenMetrics100(writer, appMetricsCollectorRegistrySamples);
        }
    }

    /**
     * Fixes escaped quotes in the help field of MetricFamilySamples.
     * <p>
     * Problem: The Prometheus Java client library doesn't remove
     * escaped double quotes in HELP text as \" which some applications can't consume.
     * <p>
     * <p>
     * Solution: Iterate over all MetricFamilySamples and remove escaped quotes (\") in the help field.
     * <p>
     * Example transformation:
     * Before: help = "The \"cpu time\" used by the process"
     * After:  help = "The cpu time used by the process"
     *
     * @param originalSamples The original enumeration of metric family samples
     * @return A new enumeration with fixed help text
     */
    private Enumeration<Collector.MetricFamilySamples> fixMetricFamilySamplesHelp(
            Enumeration<Collector.MetricFamilySamples> originalSamples) {
        
        List<Collector.MetricFamilySamples> fixedSamples = new ArrayList<>();
        
        while (originalSamples.hasMoreElements()) {
            Collector.MetricFamilySamples sample = originalSamples.nextElement();
            
            String fixedHelp = sample.help != null ? sample.help.replace("\"", "") : sample.help;
            
            Collector.MetricFamilySamples fixedSample = new Collector.MetricFamilySamples(
                    sample.name,
                    sample.type,
                    fixedHelp,
                    sample.samples
            );
            
            fixedSamples.add(fixedSample);
        }
        
        return Collections.enumeration(fixedSamples);
    }
}

