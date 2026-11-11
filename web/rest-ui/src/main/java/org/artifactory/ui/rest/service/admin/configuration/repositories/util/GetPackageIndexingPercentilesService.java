package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.jfrog.ph.acl.metrics.PackageIndexMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.repo.RepoType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.METRIC_INDEX_DURATION;
import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.RESULT_SUCCESS;
import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.TAG_PACKAGE_TYPE;
import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.TAG_REPO_TYPE;
import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.TAG_RESULT;
import static com.jfrog.ph.acl.metrics.PackageIndexMetrics.toTag;

/**
 * Returns per-node, per-package-type indexing percentiles for the create/edit repository UI,
 * computed from the local registry over LOCAL + success + INDEX/REINDEX timers using a
 * Prometheus-style {@code histogram_quantile}. For cluster-wide percentiles use Grafana.
 *
 * @author Linoy Levi
 */
@Component
public class GetPackageIndexingPercentilesService implements RestService<PackageIndexingPercentiles> {

    private final MeterRegistry meterRegistry;

    @Autowired
    public GetPackageIndexingPercentilesService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void execute(ArtifactoryRestRequest<PackageIndexingPercentiles> request, RestResponse response) {
        String packageType = request.getPathParamByKey("packageType");
        if (StringUtils.isBlank(packageType)) {
            throw new BadRequestException("packageType path parameter must be provided");
        }
        response.iModel(computePackagePercentiles(packageType));
    }

    /**
     * Reads the index-duration timers for {@code packageType} from the local registry
     * and returns the merged percentiles, or zeroes when no samples have been recorded on this node yet.
     */
    private PackageIndexingPercentiles computePackagePercentiles(String packageType) {
        List<HistogramSnapshot> snapshots = meterRegistry.find(METRIC_INDEX_DURATION)
                .tag(TAG_PACKAGE_TYPE, packageType)
                .tag(TAG_REPO_TYPE, toTag(RepoType.LOCAL))
                .tag(TAG_RESULT, RESULT_SUCCESS)
                .timers().stream()
                .map(Timer::takeSnapshot)
                .toList();
        NavigableMap<Double, Double> mergedCumulativeHistogram = mergeCumulativeHistograms(snapshots);
        return PackageIndexingPercentiles.builder()
                .packageType(packageType)
                .p50Ms(histogramQuantileMs(0.5, mergedCumulativeHistogram))
                .p95Ms(histogramQuantileMs(0.95, mergedCumulativeHistogram))
                .p99Ms(histogramQuantileMs(0.99, mergedCumulativeHistogram))
                .build();
    }

    /**
     * Sums cumulative bucket counts across {@code snapshots} into a single histogram keyed by upper bound (ns).
     * All matching timers share the bucket layout in {@link PackageIndexMetrics}.
     * The map is sorted ascending so {@link #histogramQuantileMs} can walk it in order and read the total from
     * the last entry.
     */
    private static NavigableMap<Double, Double> mergeCumulativeHistograms(List<HistogramSnapshot> snapshots) {
        NavigableMap<Double, Double> merged = new TreeMap<>();
        for (HistogramSnapshot snapshot : snapshots) {
            for (CountAtBucket bucket : snapshot.histogramCounts()) {
                merged.merge(bucket.bucket(), bucket.count(), Double::sum);
            }
        }
        return merged;
    }

    /**
     * Prometheus-style {@code histogram_quantile} over a merged cumulative bucket histogram (bounds in ns),
     * returning the linearly-interpolated quantile in milliseconds, or {@code 0.0} when the histogram is empty.
     */
    static double histogramQuantileMs(double quantile, NavigableMap<Double, Double> cumulativeByBound) {
        if (cumulativeByBound.isEmpty()) {
            return 0.0;
        }
        double totalCount = cumulativeByBound.lastEntry().getValue();
        if (totalCount <= 0.0) {
            return 0.0;
        }
        double targetRank = quantile * totalCount;
        double previousBoundNs = 0.0;
        double previousCount = 0.0;
        for (Map.Entry<Double, Double> entry : cumulativeByBound.entrySet()) {
            double boundNs = entry.getKey();
            double cumulativeCount = entry.getValue();
            if (cumulativeCount >= targetRank) {
                double bucketDelta = cumulativeCount - previousCount;
                double interpolatedNs = bucketDelta > 0.0
                        ? previousBoundNs + (boundNs - previousBoundNs) * (targetRank - previousCount) / bucketDelta
                        : boundNs;
                return interpolatedNs / TimeUnit.MILLISECONDS.toNanos(1);
            }
            previousBoundNs = boundNs;
            previousCount = cumulativeCount;
        }
        return previousBoundNs / TimeUnit.MILLISECONDS.toNanos(1);
    }
}
