/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.metrics.framework.providers;

import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.metrics.MetricsUtil.addMetricToList;

/**
 * Provider to collect Runtime Heap Size metrics
 *
 * @author leela
 */
@Component
public class RuntimeMemoryMetricsProvider implements MetricProvider {
    @Override
    public String getName() {
        return "jfrt_runtime_heap";
    }

    @Override
    public String getDescription() {
        return "Artifactory Runtime Heap Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0L;
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();

        addMetricToList(metrics, "freememory", Metric.MetricType.BYTES, "Free Memory",
                runtime.freeMemory());
        addMetricToList(metrics, "maxmemory", Metric.MetricType.BYTES, "Max Memory",
                runtime.maxMemory());
        addMetricToList(metrics, "totalmemory", Metric.MetricType.BYTES, "Total Memory",
                runtime.totalMemory());
        addMetricToList(metrics, "processors", Metric.MetricType.COUNTER, "Available Processors",
                runtime.availableProcessors());
        return metrics;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0L;
    }

}
