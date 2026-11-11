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

import org.artifactory.sh.cleanup.GarbageCollectionDatastoreService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;

/**
 * @author leela
 */
@Component
public class GarbageCollectionMetricProvider implements MetricProvider {
    private static final Logger log = LoggerFactory.getLogger(GarbageCollectionMetricProvider.class);

    private long startDate;

    private final GarbageCollectionDatastoreService garbageCollectionDatastoreService;

    @Autowired
    public GarbageCollectionMetricProvider(GarbageCollectionDatastoreService gcService) {
        this.garbageCollectionDatastoreService = gcService;
    }

    @Override
    public String getName() {
        return "jfrt_artifacts_gc";
    }

    @Override
    public String getDescription() {
        return "Artifactory Garbage Collection Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        GarbageCollectorInfo data = garbageCollectionDatastoreService.getLastGCRunInfo();
        if (null == data) {
            log.debug("GC Store data is null, hence ignoring to collect metrics.");
            return metrics;
        }
        startDate = data.getGcStartTime();
        float durationInSecs = ((float) (data.getGcEndTime() - data.getGcStartTime())) / 1000;
        Map<String, Object> labels = new HashMap<>();
        labels.put("start", startDate);
        labels.put("end", data.getGcEndTime());
        labels.put("status", data.getStatus().name());
        labels.put("type", data.getStrategy());

        addMetricWithLabelsToList(
                metrics,
                labels, "duration", Metric.MetricType.SECONDS,
                "Time taken for garbage collection", durationInSecs
        );
        addMetricWithLabelsToList(
                metrics,
                labels, "size_cleaned", Metric.MetricType.BYTES,
                "Total Bytes recovered by Garbage Collection", data.getTotalSizeCleaned().get()
        );
        addMetricWithLabelsToList(
                metrics,
                labels, "binaries", Metric.MetricType.GAUGE,
                "Total number of binaries removed by Garbage Collection", data.getBinariesCleaned().get()
        );

        return metrics;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.DEDUPED;
    }

    @Override
    public long getLastUpdatedMillis() {
        if (null != garbageCollectionDatastoreService.getLastGCRunInfo()) {
            startDate = garbageCollectionDatastoreService.getLastGCRunInfo().getGcStartTime();
        }
        return startDate;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

}
