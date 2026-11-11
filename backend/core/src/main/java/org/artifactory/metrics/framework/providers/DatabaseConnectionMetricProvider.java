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

import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.metric.MetricsDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.artifactory.metrics.MetricsUtil.addMetricToList;

/**
 * Provider to collect Database Metrics
 *
 * @author shraddhan
 */
@Component
public class DatabaseConnectionMetricProvider implements MetricProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionMetricProvider.class);

    private final ArtifactoryJdbcHelper jdbcHelper;

    @Autowired
    public DatabaseConnectionMetricProvider(ArtifactoryJdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    @Override
    public String getName() {
        return "jfrt_db_connections";
    }

    @Override
    public String getDescription() {
        return "Artifactory Database Connection Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        try {
            ArtifactoryDataSource artifactoryDS = null;
            //This order of if-else is important to get JFrogDatasource Metrics working when database metrics are
            //enabled
            if (jdbcHelper.getDataSource().isWrapperFor(MetricsDataSource.class)) {
                MetricsDataSource metricsDataSource = jdbcHelper.getDataSource().unwrap(MetricsDataSource.class);
                log.trace("Datasource is MetricsDatasource");
                if (metricsDataSource.isWrapperFor(ArtifactoryDataSource.class)) {
                    log.trace("MetricsDatasource is ArtifactoryDatasource wrapper");
                    artifactoryDS = jdbcHelper.getDataSource().unwrap(MetricsDataSource.class)
                            .unwrap(ArtifactoryDataSource.class);
                } else {
                    log.debug(
                            "Database source is MetricsDatasource. But not of ArtifactoryDatasource type, hence " +
                                    "metrics won't be collected");
                }
            } else if (jdbcHelper.getDataSource().isWrapperFor(ArtifactoryDataSource.class)) {
                log.trace("Datasource is ArtifactoryDatasource");
                artifactoryDS = jdbcHelper.getDataSource().unwrap(ArtifactoryDataSource.class);
            } else {
                log.debug(
                        "Database source is neither ArtifactoryDatasource or MetricsDatasource. Hence metrics won't " +
                                "be collected");
            }
            if (null != artifactoryDS) {
                addMetricToList(
                        metrics,
                        "active", Metric.MetricType.GAUGE,
                        "Total Active Connections", artifactoryDS.getActiveConnectionsCount()
                );
                addMetricToList(
                        metrics,
                        "idle", Metric.MetricType.GAUGE,
                        "Total Idle Connections", artifactoryDS.getIdleConnectionsCount()
                );
                addMetricToList(
                        metrics,
                        "max_active", Metric.MetricType.GAUGE,
                        "Total Max Active Connections", artifactoryDS.getMaxActive()
                );
                addMetricToList(
                        metrics,
                        "min_idle", Metric.MetricType.GAUGE,
                        "Total Min Idle Connections", artifactoryDS.getMinIdle()
                );
            }
        } catch (SQLException e) {
            log.warn("Unable to gather Database Connection Metrics {}", e.getMessage());
            log.debug("Unable to gather Database Connection Metrics {}", e.getMessage(), e);
        }
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
