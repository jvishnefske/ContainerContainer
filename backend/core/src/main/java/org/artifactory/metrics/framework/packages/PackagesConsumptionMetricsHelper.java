package org.artifactory.metrics.framework.packages;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.analytics.PackageUsageCounter;
import org.jfrog.metrics.model.Metric;

import java.util.List;
import java.util.Map;

@UtilityClass
public class PackagesConsumptionMetricsHelper {

    public static final String PACKAGE_TYPE = "package_type";
    public static final String ACTION_TYPE = "action_type";
    public static final String ACTION_DOWNLOAD = "download";
    public static final String ACTION_UPLOAD = "upload";
    public static final String ACTION_REDIRECT = "redirect";
    public static final String ACTION_HEAD = "head";

    public static void collectPackagesDownloadMetrics(List<Metric> metrics, PackageUsageCounter packageUsageData) {
        packageUsageData.getSuccessfulPackagesDownloads().forEach((repoType, value) -> {
            if (value.get() == 0) {
                return;
            }
            Metric metric = Metric.builder()
                    .name(StringUtils.EMPTY)
                    .description("Successful packages downloads per type")
                    .value(value.getAndSet(0))
                    .labels(Map.of(PACKAGE_TYPE, repoType.getType(), ACTION_TYPE, ACTION_DOWNLOAD))
                    .type(Metric.MetricType.COUNT_COUNTER).build();
            metrics.add(metric);
        });
    }

    public static void collectPackagesUploadMetrics(List<Metric> metrics, PackageUsageCounter packageUsageData) {
        packageUsageData.getSuccessfulPackagesUploads().forEach((repoType, value) -> {
            if (value.get() == 0) {
                return;
            }
            Metric metric = Metric.builder()
                    .name(StringUtils.EMPTY)
                    .description("Successful packages uploads per type")
                    .value(value.getAndSet(0))
                    .labels(Map.of(PACKAGE_TYPE, repoType.getType(), ACTION_TYPE, ACTION_UPLOAD))
                    .type(Metric.MetricType.COUNT_COUNTER).build();
            metrics.add(metric);
        });
    }

    public static void collectPackagesRedirectMetrics(List<Metric> metrics, PackageUsageCounter packageUsageData) {
        packageUsageData.getRedirectedPackagesDownloads().forEach((repoType, value) -> {
            if (value.get() == 0) {
                return;
            }
            Metric metric = Metric.builder()
                    .name(StringUtils.EMPTY)
                    .description("Successful packages redirects per type")
                    .value(value.getAndSet(0))
                    .labels(Map.of(PACKAGE_TYPE, repoType.getType(), ACTION_TYPE, ACTION_REDIRECT))
                    .type(Metric.MetricType.COUNT_COUNTER).build();
            metrics.add(metric);
        });
    }

    public static void collectPackagesHeadRequestsMetrics(List<Metric> metrics, PackageUsageCounter packageUsageData) {
        packageUsageData.getSuccessfulPackagesHeadRequests().forEach((repoType, value) -> {
            if (value.get() == 0) {
                return;
            }
            Metric metric = Metric.builder()
                    .name(StringUtils.EMPTY)
                    .description("Successful packages head requests per type")
                    .value(value.getAndSet(0))
                    .labels(Map.of(PACKAGE_TYPE, repoType.getConfigName(), ACTION_TYPE, ACTION_HEAD))
                    .type(Metric.MetricType.COUNT_COUNTER).build();
            metrics.add(metric);
        });
    }

    public static void collectPackagesCountMetrics(List<Metric> metrics, Map<String, Long> repoTypeToFilesCount) {
        repoTypeToFilesCount.forEach((type, filesCount) -> {
            if (filesCount == 0) {
                return;
            }
            Metric metric = Metric.builder()
                    .name(StringUtils.EMPTY)
                    .description("Packages count per type")
                    .value(filesCount)
                    .labels(Map.of(PACKAGE_TYPE, type.toLowerCase()))
                    .type(Metric.MetricType.GAUGE).build();
            metrics.add(metric);
        });
    }
}