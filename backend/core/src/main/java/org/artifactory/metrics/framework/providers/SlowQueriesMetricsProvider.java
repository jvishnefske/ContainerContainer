/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2021 JFrog Ltd.
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

import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.metric.DatabaseMetrics;
import org.jfrog.storage.metric.MetricSqlQuery;
import org.jfrog.storage.metric.MetricsDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;


/**
 * Provider to collect slow queries from artifactory.
 *
 * Currently it doesn't get real list of slow queries. We're having hardcoded list of queries.
 *
 * @author leela
 */

@Component
public class SlowQueriesMetricsProvider implements MetricProvider {
    private static final Logger log = LoggerFactory.getLogger(SlowQueriesMetricsProvider.class);

    private ArtifactoryJdbcHelper jdbcHelper;

    @Autowired
    public SlowQueriesMetricsProvider(ArtifactoryJdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    @Override
    public String getName() {
        return "jfrt_slow_queries";
    }

    @Override
    public String getDescription() {
        return "Artifactory Slow Queries";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0;
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList();
        SortedSet<MetricSqlQuery> queriesList = getSlowQueriesList();
        if (null != queriesList) {
            queriesList.forEach(q -> {
                Map<String, Object> labels = new HashMap<>();
                labels.put("query", q.getSql().lines().map(String::stripLeading).collect(Collectors.joining(" ")));
                long duration = q.getTotalTime();
                long count = q.getCount();
                try {
                    addMetricWithLabelsToList(metrics, labels, "duration", Metric.MetricType.SECONDS,
                            "Time taken for query ",
                            duration/count);
                    addMetricWithLabelsToList(metrics, labels, "count", Metric.MetricType.COUNTER,
                            "Number of times query has been executed ",
                            count);
                } catch (ArithmeticException e) {
                    log.debug("Arithmetic Exception: slow query duration: {}, slow query count: {}", duration, count);
                }
            });
        }
        return metrics;
    }

    private SortedSet<MetricSqlQuery> getSlowQueriesList() {
        SortedSet<MetricSqlQuery> queriesList = null;
        try {
            if (jdbcHelper.getDataSource().isWrapperFor(MetricsDataSource.class)) {
                log.trace("Collecting slow db query metrics from MetricsDataSource..");
                MetricsDataSource metricsDataSource =
                        jdbcHelper.getDataSource().unwrap(MetricsDataSource.class);
                DatabaseMetrics metrics = metricsDataSource.getMetrics();
                queriesList = metrics.topQueriesByTotalTime();
            }
        } catch (SQLException e) {
            String str= "Unable to collect slow queries metrics using jdbchelper: ";
            log.error("{}{}", str, e.getMessage(), e);
        }

        return queriesList;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

}

