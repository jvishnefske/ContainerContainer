package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.jfrog.ph.acl.metrics.PackageIndexMetrics;
import com.jfrog.ph.acl.metrics.PackageIndexMetrics.Trigger;
import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetPackageIndexingPercentilesService}. The behavioral tests use a real
 * {@link PrometheusMeterRegistry} wired to {@link PackageIndexMetrics} so they exercise the actual
 * Micrometer + quantile-interpolation contract; the two {@code histogramQuantileMs_*} tests pin
 * the math directly against Prometheus's reference behavior ({@code prometheus/promql/quantile.go}).
 *
 * @author Linoy Levi
 */
@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
class GetPackageIndexingPercentilesServiceTest {

    @Mock
    private ArtifactoryRestRequest<PackageIndexingPercentiles> request;

    private PrometheusMeterRegistry meterRegistry;
    private PackageIndexMetrics packageIndexMetrics;
    private GetPackageIndexingPercentilesService service;
    private ArtifactoryRestResponse<PackageIndexingPercentiles> response;

    @BeforeEach
    void setUp() {
        // PrometheusMeterRegistry is required (not SimpleMeterRegistry) because it sets
        // supportsAggregablePercentiles=true, which makes Micrometer create
        // TimeWindowFixedBoundaryHistogram with Prometheus-compatible bucket boundaries.
        meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new CollectorRegistry(), Clock.SYSTEM);
        packageIndexMetrics = new PackageIndexMetrics(meterRegistry);
        service = new GetPackageIndexingPercentilesService(meterRegistry);
        response = new ArtifactoryRestResponse<>();
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void execute_blankPackageType_failsFastWith400_andDoesNotQueryMicrometer() {
        when(request.getPathParamByKey("packageType")).thenReturn("   ");

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }

    @Test
    void execute_missingPackageType_failsFastWith400_andDoesNotQueryMicrometer() {
        when(request.getPathParamByKey("packageType")).thenReturn("");

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }

    @Test
    void execute_nonExistingPackageType_succeedsWithZeroPercentiles() {
        when(request.getPathParamByKey("packageType")).thenReturn("nonexistent");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("nonexistent", result.packageType());
        assertEquals(0.0, result.p50Ms());
        assertEquals(0.0, result.p95Ms());
        assertEquals(0.0, result.p99Ms());
    }

    @Test
    void execute_noSamples_returnsRequestedPackageTypeWithZeroPercentiles() {
        when(request.getPathParamByKey("packageType")).thenReturn("npm");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("npm", result.packageType());
        assertEquals(0.0, result.p50Ms());
        assertEquals(0.0, result.p95Ms());
        assertEquals(0.0, result.p99Ms());
    }

    @Test
    void execute_unknownPackageType_returnsZeroPercentiles() {
        packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, 100, true, null, null);
        when(request.getPathParamByKey("packageType")).thenReturn("nonexistent");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("nonexistent", result.packageType());
        assertEquals(0.0, result.p50Ms());
        assertEquals(0.0, result.p95Ms());
        assertEquals(0.0, result.p99Ms());
    }

    @Test
    void execute_populatesP50P95P99InMonotonicallyIncreasingOrder() {
        for (int i = 1; i <= 100; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, i, true, null, null);
        }
        when(request.getPathParamByKey("packageType")).thenReturn("npm");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("npm", result.packageType());
        assertTrue(result.p50Ms() > 0, "p50 must be populated, was " + result.p50Ms());
        assertTrue(result.p95Ms() > 0, "p95 must be populated, was " + result.p95Ms());
        assertTrue(result.p99Ms() > 0, "p99 must be populated, was " + result.p99Ms());
        assertTrue(result.p50Ms() <= result.p95Ms(), "p50 must be <= p95");
        assertTrue(result.p95Ms() <= result.p99Ms(), "p95 must be <= p99");
    }

    @Test
    void execute_reindexAndIndexSamplesAreBothIncluded() {
        for (int i = 1; i <= 50; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, i, true, null, null);
        }
        for (int i = 51; i <= 100; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.REINDEX, i, true, null, null);
        }
        when(request.getPathParamByKey("packageType")).thenReturn("npm");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("npm", result.packageType());
        assertTrue(result.p50Ms() > 40 && result.p50Ms() < 70,
                "p50 of the merged 1..100ms range should be ~50, was " + result.p50Ms());
        assertTrue(result.p99Ms() > result.p95Ms(),
                "p99 (" + result.p99Ms() + ") must reflect the REINDEX tail samples, > p95 (" + result.p95Ms() + ")");
    }

    @Test
    void execute_remoteAndFailureSamplesDoNotPollutePercentiles() {
        for (int i = 0; i < 50; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, 50, true, null, null);
        }
        for (int i = 0; i < 50; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.REMOTE, Trigger.INDEX, 9000, true, null, null);
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, 9000, false, null, null);
        }
        when(request.getPathParamByKey("packageType")).thenReturn("npm");

        service.execute(request, response);

        PackageIndexingPercentiles result = response.getIModel();
        assertEquals("npm", result.packageType());
        assertTrue(result.p50Ms() < 1000,
                "p50 should reflect only the 50ms local-success samples, was " + result.p50Ms());
        assertTrue(result.p99Ms() < 1000,
                "p99 should reflect only the 50ms local-success samples, was " + result.p99Ms());
    }

    @Test
    void execute_isolatesByPackageType() {
        for (int i = 0; i < 50; i++) {
            packageIndexMetrics.recordAll(PackageType.NPM, RepoType.LOCAL, Trigger.INDEX, 50, true, null, null);
            packageIndexMetrics.recordAll(PackageType.CRAN, RepoType.LOCAL, Trigger.INDEX, 5000, true, null, null);
        }

        when(request.getPathParamByKey("packageType")).thenReturn("npm");
        service.execute(request, response);
        PackageIndexingPercentiles npm = response.getIModel();

        ArtifactoryRestResponse<PackageIndexingPercentiles> cranResponse = new ArtifactoryRestResponse<>();
        when(request.getPathParamByKey("packageType")).thenReturn("cran");
        service.execute(request, cranResponse);
        PackageIndexingPercentiles cran = cranResponse.getIModel();

        assertEquals("npm", npm.packageType());
        assertTrue(npm.p99Ms() < 1000, "npm p99 should reflect only npm's 50ms samples, was " + npm.p99Ms());
        assertEquals("cran", cran.packageType());
        assertTrue(cran.p50Ms() > 1000, "cran p50 should reflect cran's 5000ms samples, was " + cran.p50Ms());
    }

    /**
     * Prometheus reference: linear interpolation inside a populated bucket. For cumulative buckets
     * {1ms→1, 2ms→3, 4ms→4, 8ms→5} and q=0.6, target rank 3.0 falls in {@code (1ms, 2ms]} and
     * interpolates to {@code 1 + (2-1) * (3.0-1)/(3-1) = 2.0ms} — matching {@code histogram_quantile}.
     */
    @Test
    void histogramQuantileMs_linearlyInterpolatesInsideMatchingBucket() {
        NavigableMap<Double, Double> buckets = bucketsByMs(
                1, 1.0,
                2, 3.0,
                4, 4.0,
                8, 5.0);

        double result = GetPackageIndexingPercentilesService.histogramQuantileMs(0.6, buckets);

        assertEquals(2.0, result, 1e-9,
                "histogram_quantile(0.6) over cumulative buckets {1ms:1, 2ms:3, 4ms:4, 8ms:5} " +
                        "must land inside the (1ms, 2ms] bucket at exactly 2.0ms, matching Prometheus");
    }

    /**
     * Prometheus reference: linear interpolation inside the first bucket from the implicit 0 lower
     * bound. For {10ms→5} and q=0.5, target rank 2.5 interpolates to
     * {@code 0 + (10-0) * (2.5-0)/(5-0) = 5.0ms} — matching {@code histogram_quantile}.
     */
    @Test
    void histogramQuantileMs_firstBucket_interpolatesFromImplicitZeroLowerBound() {
        NavigableMap<Double, Double> buckets = bucketsByMs(10, 5.0);

        double result = GetPackageIndexingPercentilesService.histogramQuantileMs(0.5, buckets);

        assertEquals(5.0, result, 1e-9,
                "histogram_quantile(0.5) over a single cumulative bucket {10ms:5} must interpolate " +
                        "from the implicit 0 lower bound to land at 5.0ms, matching Prometheus");
    }

    /**
     * Builds a cumulative-bucket map keyed by upper bound in nanoseconds (Timer's base unit) from
     * flattened {@code (boundMs, cumulativeCount)} pairs, accepting ms for test readability.
     */
    private static NavigableMap<Double, Double> bucketsByMs(double... boundMsAndCount) {
        if (boundMsAndCount.length % 2 != 0) {
            throw new IllegalArgumentException("bucketsByMs requires (boundMs, cumulativeCount) pairs");
        }
        NavigableMap<Double, Double> buckets = new TreeMap<>();
        for (int i = 0; i < boundMsAndCount.length; i += 2) {
            double boundNs = boundMsAndCount[i] * TimeUnit.MILLISECONDS.toNanos(1);
            buckets.put(boundNs, boundMsAndCount[i + 1]);
        }
        return buckets;
    }
}
