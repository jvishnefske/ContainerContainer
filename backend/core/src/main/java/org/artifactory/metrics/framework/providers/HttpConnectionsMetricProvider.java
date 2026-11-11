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

import jakarta.annotation.Nonnull;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provider to collect remote repository http connection metrics
 *
 * @author leela
 */
@Component
public class HttpConnectionsMetricProvider implements MetricProvider {

    private final HttpConnectionPoolMonitorService httpConnectionPoolMonitorService;

    @Autowired
    public HttpConnectionsMetricProvider(HttpConnectionPoolMonitorService httpConnectionPoolMonitorService) {
        this.httpConnectionPoolMonitorService = httpConnectionPoolMonitorService;
    }

    private static boolean filterUnnecessaryPools(
            Map.Entry<String, WeakReference<HTTPConnectionPool>> stringWeakReferenceEntry) {
        WeakReference<HTTPConnectionPool> weakReference = stringWeakReferenceEntry.getValue();
        if (weakReference != null) {
            HTTPConnectionPool pool = weakReference.get();
            return pool != null && pool.getAvailable() > 0;
        }
        return false;
    }

    private static int compareByPoolAvailable(Map.Entry<String, WeakReference<HTTPConnectionPool>> e1,
            Map.Entry<String, WeakReference<HTTPConnectionPool>> e2) {
        if (e2.getValue() != null && e1.getValue() != null) {
            HTTPConnectionPool pool2 = e2.getValue().get();
            HTTPConnectionPool pool1 = e1.getValue().get();
            if (pool2 != null && pool1 != null) {
                return pool2.getAvailable() - pool1.getAvailable();
            }
        }
        return 0;
    }

    @Override
    public String getName() {
        return "jfrt_http_connections";
    }

    @Override
    public String getDescription() {
        return "Artifactory HTTP Connection Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0;
    }

    @Override
    public List<Metric> collect() {
        int repoCount = getDefaultNumberOfRepositoriesToCollect();
        List<Metric> metrics = new ArrayList<>();
        if (httpConnectionPoolMonitorService != null) {
            Map<String, WeakReference<HTTPConnectionPool>> activeHttpPools =
                    getActiveHttpPools(httpConnectionPoolMonitorService.getConnectionPools(), repoCount);
            if (!activeHttpPools.isEmpty()) {
                activeHttpPools.forEach((poolName, pool) -> {
                    HTTPConnectionPool httpConnectionPool = pool.get();
                    if (httpConnectionPool != null) {
                        Map<String, Object> labels = new HashMap<>();
                        labels.put("pool", poolName);
                        httpConnectionPool.registerMetrics(metrics, labels);
                    }
                });
            }
        }
        return metrics;
    }

    private int getDefaultNumberOfRepositoriesToCollect() {
        return ConstantValues.totalRepositoriesForHttpConnectionMetrics.getInt();
    }

    @Nonnull
    private Map<String, WeakReference<HTTPConnectionPool>> getActiveHttpPools(
            Map<String, WeakReference<HTTPConnectionPool>> httpPools, int repoCountLimit) {
        return httpPools.entrySet()
                .stream()
                .filter(HttpConnectionsMetricProvider::filterUnnecessaryPools)
                .sorted(HttpConnectionsMetricProvider::compareByPoolAvailable)
                .limit(repoCountLimit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0L;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

}
