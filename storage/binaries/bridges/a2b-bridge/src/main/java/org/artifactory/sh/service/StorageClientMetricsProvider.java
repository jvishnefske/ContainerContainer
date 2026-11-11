package org.artifactory.sh.service;

import com.google.common.collect.Maps;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author annakv
 */
public class StorageClientMetricsProvider implements MetricProvider {

    private HTTPConnectionPool pool;

    @Override
    public String getName() {
        return "jfrt_storage_client_http_connections";
    }

    @Override
    public String getDescription() {
        return "Artifactory Storage Client HTTP Connection Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0;
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        Optional.ofNullable(pool)
                .ifPresent(cp -> cp.registerMetrics(metrics, Maps.newHashMap()));
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

    public void monitorPool(HTTPConnectionPool httpConnectionPool) {
        pool = httpConnectionPool;
    }

}
