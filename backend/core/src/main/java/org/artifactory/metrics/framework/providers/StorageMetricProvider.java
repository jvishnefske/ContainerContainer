package org.artifactory.metrics.framework.providers;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.storage.service.StorageMetricCollector;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.metrics.MetricsUtil.addMetricToList;

@Component
public class StorageMetricProvider implements MetricProvider {

    private final StorageMetricCollector collector;

    @Autowired
    public StorageMetricProvider(StorageMetricCollector collector) {
        this.collector = collector;
    }

    @Override
    public String getName() {
        return "jfrt_storage";
    }

    @Override
    public String getDescription() {
        return "Artifactory Storage Metric Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return 0;
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        if (isColdInstance()) { // skip metrics collection for cold instances by legacy reporting method
            return metrics;
        }
        long storageSize = collector.get().getStorageSize();
        if (storageSize >= 0) {
            addMetricToList(metrics, "current_total_size", Metric.MetricType.BYTES, "Used Storage",
                    storageSize);
        }
        return metrics;
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

    private boolean isColdInstance() {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.isEntitled(EntitlementType.COLD_STORAGE);
    }
}
