package org.artifactory.metrics.cuj;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Providers;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * This class is designed to collect repo creation metrics as part of "Critical User Journey" initiative.
 * The metrics will be collected using micrometer.io framework and used to build Graphana dashboards.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
public class RepoCreationMetricsWrapper {

    private static final int DEFAULT_UNKNOWN_ERROR_STATUS_CODE = 999;
    private static final String UNKNOWN_TAG_VALUE = "unknown";
    private static final String REPO_PACKAGE_TYPE_TAG = "repoPackageType";
    private static final String REPO_TYPE_TAG = "repoType";
    private static final String STATUS_TAG = "status";

    private final MeterRegistry meterRegistry;
    @VisibleForTesting
    LongSupplier currentTimeSupplier = System::currentTimeMillis;

    @Autowired
    public RepoCreationMetricsWrapper(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Wraps a repo creation operation in a code that collects relevant metrics.
     * A micrometer.io {@link Timer} object is used to measure the operation's duration.
     * Also, a success/failed status, repo type and repo package type are stored as tags to allow
     * grouping of the collected data based on those values.<br/>
     * The behavior and response of the delegated repo creation method are not affected by the wrapper's mechanism.
     *
     * @param repoTypeSupplier        a "supplier" method to supply repo type
     * @param repoPackageTypeSupplier a "supplier" method to supply repo package type
     * @param createRepoMethod        a delegated method that actually creates a repo
     * @param providers               JAX-RS {@link Providers} object that will be used to correctly map exceptions to
     *                                {@link Response} objects. Normally, this mapping is done automatically by Jersey
     *                                framework. But in our case we need it in order to correctly record HTTP response
     *                                codes to the metrics. This parameter can be <code>null</code>, but then,
     *                                in case of unhandled exceptions, the metric will contain a default "Generic Error"
     *                                status code which is "999".
     * @return original {@link Response} object returned by the provided <code>createRepoMethod</code> param
     */
    public Response createRepoAndCollectMetrics(@NonNull Supplier<String> repoTypeSupplier,
            @NonNull Supplier<String> repoPackageTypeSupplier,
            @NonNull Callable<Response> createRepoMethod, Providers providers) {
        long startTime = currentTimeSupplier.getAsLong();
        Response response = null;
        try {
            response = createRepoMethod.call();
            return response;
        } catch (RuntimeException re) {
            response = mapToResponse(re, providers);
            throw re;
        } catch (Exception e) {
            log.error("Unexpected exception occurred while trying to create repo and collect metrics", e);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
            return response;
        } finally {
            recordMetrics(startTime, response, repoTypeSupplier, repoPackageTypeSupplier);
        }
    }

    private void recordMetrics(long startTime, Response response, Supplier<String> repoTypeSupplier,
            Supplier<String> repoPackageTypeSupplier) {
        try {
            long duration = currentTimeSupplier.getAsLong() - startTime;
            int status = response != null ? response.getStatus() : DEFAULT_UNKNOWN_ERROR_STATUS_CODE;
            Tags tags = Tags.of(REPO_TYPE_TAG, safeFetchTagValue(repoTypeSupplier, REPO_TYPE_TAG),
                    REPO_PACKAGE_TYPE_TAG, safeFetchTagValue(repoPackageTypeSupplier, REPO_PACKAGE_TYPE_TAG),
                    STATUS_TAG, String.valueOf(status));
            // This will allow to build Graphana dashboards such as "Repo creation error rate" and "Repo creation time"
            Timer.builder("repo.create.time")
                    .description("The time taken to create the repo")
                    .tags(tags)
                    .register(this.meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Could not record a metric for repo creation time", e);
        }
    }

    private String safeFetchTagValue(Supplier<String> valueSupplier, String tagName) {
        try {
            String value = valueSupplier.get();
            if (StringUtils.isNotBlank(value)) {
                return value.toLowerCase();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch metrics value tag value for tag {}", tagName, e);
        }
        return UNKNOWN_TAG_VALUE;
    }

    @Nullable
    private Response mapToResponse(Throwable t, Providers providers) {
        if (providers == null) {
            return null;
        }
        ExceptionMapper<Throwable> mapper =
                (ExceptionMapper<Throwable>) providers.getExceptionMapper(t.getClass());
        if (mapper != null) {
            return mapper.toResponse(t);
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(t.getMessage()).build();
        }
    }
}
