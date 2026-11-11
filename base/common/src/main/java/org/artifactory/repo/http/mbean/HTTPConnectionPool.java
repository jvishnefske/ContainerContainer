/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
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

package org.artifactory.repo.http.mbean;

import lombok.NonNull;
import org.apache.http.pool.ConnPoolControl;
import org.artifactory.metrics.MetricsUtil;
import org.jfrog.metrics.model.Metric;

import java.util.List;
import java.util.Map;


/**
 * @author Ofer Cohen
 */
public class HTTPConnectionPool implements HTTPConnectionPoolMBean {

    private static final String METRIC_AVAILABLE = "available";
    private static final String METRIC_LEASED = "leased";
    private static final String METRIC_PENDING = "pending";
    private static final String METRIC_MAX = "max";

    private final ConnPoolControl<?> connectionPool;

    public HTTPConnectionPool(ConnPoolControl<?> connectionPool) {
        this.connectionPool = connectionPool;

    }

    @Override
    public int getAvailable() {
        return connectionPool.getTotalStats().getAvailable();
    }

    @Override
    public int getLeased() {
        return connectionPool.getTotalStats().getLeased();
    }

    @Override
    public int getMax() {
        return connectionPool.getTotalStats().getMax();
    }

    @Override
    public int getPending() {
        return connectionPool.getTotalStats().getPending();
    }

    public void registerMetrics(@NonNull List<Metric> metrics, @NonNull Map<String, Object> labels) {
        int max = getMax();
        labels.put(METRIC_MAX, String.valueOf(max));
        MetricsUtil.addMetricWithLabelsToList(metrics, labels, METRIC_AVAILABLE, Metric.MetricType.GAUGE,
                "Available Connections", getAvailable());
        MetricsUtil.addMetricWithLabelsToList(metrics, labels, METRIC_LEASED, Metric.MetricType.GAUGE,
                "Leased Connections", getLeased());
        MetricsUtil.addMetricWithLabelsToList(metrics, labels, METRIC_PENDING, Metric.MetricType.GAUGE,
                "Pending Connections", getPending());
        MetricsUtil.addMetricWithLabelsToList(metrics, labels, METRIC_MAX, Metric.MetricType.GAUGE,
                "Max Connections", max);
    }

}
