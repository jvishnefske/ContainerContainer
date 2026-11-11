package org.artifactory.repo.remote.interceptor;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.remote.stats.RemoteRepoStatsService;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.TrackingHttpEntity;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.artifactory.traffic.OutboundRequestLogger;
import org.artifactory.util.HttpUtils;
import org.slf4j.MDC;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.artifactory.common.ConstantValues.jfrogUserAgentForRemoteRequestsEnabled;
import static org.artifactory.common.ConstantValues.remoteRepoOutboundHeadersAuditEnabled;
import static org.artifactory.common.ConstantValues.remoteRepoRequestResponseTrackingEnabled;
import static org.artifactory.metrics.ExternalClientsServiceMetrics.TARGET_REMOTE;
import static org.artifactory.metrics.ExternalClientsServiceMetrics.recordRequest;

/**
 * Intercepts outbound requests made by Remote Repositories and logs them to artifactory-request-out.log
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class RemoteRepoRequestResponseInterceptor implements HttpRequestInterceptor, HttpResponseInterceptor {

    private static final String REQUEST_START_TIME = "request_start_time";
    private final String repoName;
    private final String userName;
    private final String repoPackageType;
    private final String remoteRepoUrl;
    private final JFrogUserAgentService jfrogUserAgentService;
    private final MeterRegistry metrics;
    private final boolean usesAuthentication;
    private final RemoteRepoStatsService remoteRepoStatsService;
    private final RemoteRepoOutboundHeaderAuditService remoteRepoOutboundHeaderAuditService;
    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private LongSupplier timeSupplier = System::currentTimeMillis;

    public RemoteRepoRequestResponseInterceptor(String repoName, String userName,
            boolean usesAuthentication, String repoPackageType, String remoteRepoUrl) {
        this.repoName = repoName;
        this.userName = Optional.ofNullable(userName).orElse(StringUtils.EMPTY);
        this.repoPackageType = repoPackageType;
        this.remoteRepoUrl = Optional.ofNullable(remoteRepoUrl).orElse(StringUtils.EMPTY);
        this.jfrogUserAgentService = ContextHelper.get().beanForType(JFrogUserAgentService.class);
        this.metrics = ContextHelper.get().beanForType("applicationMeterRegistry", MeterRegistry.class);
        this.usesAuthentication = usesAuthentication;
        this.remoteRepoStatsService = getRemoteRepoStatsService();
        this.remoteRepoOutboundHeaderAuditService = ContextHelper.get()
                .beanForType(RemoteRepoOutboundHeaderAuditService.class);
    }

    private static RemoteRepoStatsService getRemoteRepoStatsService() {
        try {
            return ContextHelper.get().beanForType(RemoteRepoStatsService.class);
        } catch (Exception e) {
            log.debug("RemoteRepoStatsService not available, stats collection disabled", e);
            return null;
        }
    }

    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext) {
        httpContext.setAttribute(REQUEST_START_TIME, timeSupplier.getAsLong());
        httpRequest.addHeader(HttpHeaders.USER_AGENT, buildUserAgent());
    }

    @Override
    public void process(HttpResponse httpResponse, HttpContext httpContext) {
        CurrentActivity.PACKAGE.putIfEmpty(repoPackageType);
        // Get request related info
        final long requestStartTime = (long) httpContext.getAttribute(REQUEST_START_TIME);
        long initialDuration = calculateDuration(requestStartTime);
        Object httpRequestAttribute = httpContext.getAttribute("http.request");
        if (!(httpRequestAttribute instanceof HttpRequestWrapper)) {
            log.debug("Could not write the outbound request to the outbound request log. " +
                    "The request object is not an instance of HttpRequestWrapper!");
            return;
        }
        HttpRequestWrapper httpRequestWrapper = (HttpRequestWrapper) httpRequestAttribute;
        String url = buildFullUrlForLog(httpRequestWrapper, httpContext);
        long requestContentLength = Optional.ofNullable(httpRequestWrapper.getFirstHeader(HttpHeaders.CONTENT_LENGTH))
                .map(Header::getValue)
                .map(Long::parseLong)
                .orElse(0L);
        // Get response related info
        Object httpResponseAttribute = httpContext.getAttribute("http.response");
        if (!(httpResponseAttribute instanceof HttpResponse)) {
            log.debug("Could not write the outbound request to the outbound request log. " +
                    "The response object is not an instance of HttpResponse!");
            return;
        }
        HttpResponse basicHttpResponse = (HttpResponse) httpResponseAttribute;
        int statusCode = basicHttpResponse.getStatusLine().getStatusCode();
        long responseContentLength = Optional.ofNullable(basicHttpResponse.getFirstHeader(HttpHeaders.CONTENT_LENGTH))
                .map(Header::getValue)
                .map(Long::parseLong)
                .orElse(0L);
        recordInboundHeadersIfAudited(basicHttpResponse);
        HttpEntity originalResponseEntity = basicHttpResponse.getEntity();
        // Create DownloadPerfCounters if it doesn't exist to ensure dtime is recorded for all remote requests,
        // including the first request during retrieveInfo() that happens before respondFoundResource()
        DownloadPerfCounters downloadPerfCounters = RequestThreadLocal.getDownloadPerfCounters(true);
        Tags metricsTags = createMetricsTags(httpRequestWrapper.getMethod(), statusCode);
        String httpMethod = httpRequestWrapper.getMethod();
        String capturedTraceId = MDC.get("uber-trace-id");
        if (originalResponseEntity != null && remoteRepoRequestResponseTrackingEnabled.getBoolean()) {
            basicHttpResponse.setEntity(new TrackingHttpEntity(originalResponseEntity, () -> {
                long fullDuration = calculateDuration(requestStartTime);
                OutboundRequestLogger.log(repoName, userName, httpMethod, url, statusCode,
                        requestContentLength, responseContentLength, fullDuration, usesAuthentication);
                enqueueIfEnabled(httpMethod, url, statusCode, fullDuration, requestStartTime, capturedTraceId);
                reportRequestDuration(downloadPerfCounters, metricsTags, fullDuration);
                recordRemoteRepoStats(httpMethod, statusCode, fullDuration,
                        initialDuration, responseContentLength);
            }));
        } else {
            OutboundRequestLogger.log(repoName, userName, httpMethod, url, statusCode,
                    requestContentLength, responseContentLength, initialDuration, usesAuthentication);
            enqueueIfEnabled(httpMethod, url, statusCode, initialDuration, requestStartTime, capturedTraceId);
            reportRequestDuration(downloadPerfCounters, metricsTags, initialDuration);
            recordRemoteRepoStats(httpMethod, statusCode, initialDuration,
                    initialDuration, responseContentLength);
        }
    }

    private void recordInboundHeadersIfAudited(HttpResponse response) {
        if (!remoteRepoOutboundHeadersAuditEnabled.getBoolean()) {
            return;
        }
        try {
            remoteRepoOutboundHeaderAuditService.record(repoName, remoteRepoUrl, response);
        } catch (Exception e) {
            log.warn("RemoteRepoOutboundHeaderAuditService not available", e);
        }
    }

    /**
     * Builds the full URL for request-out logging so that the host is never omitted.
     * When the request URI is absolute, use it as-is. Otherwise combine target host with path,
     * using context target host as fallback when the request wrapper's target is null (e.g. on redirects).
     */
    private String buildFullUrlForLog(HttpRequestWrapper httpRequestWrapper, HttpContext httpContext) {
        URI uri = httpRequestWrapper.getURI();
        if (uri != null && uri.isAbsolute()) {
            return uri.toString();
        }
        String path = uri != null ? uri.toString() : StringUtils.EMPTY;
        HttpHost target = Optional.ofNullable(httpRequestWrapper.getTarget())
                .orElseGet(() -> (HttpHost) httpContext.getAttribute(HttpCoreContext.HTTP_TARGET_HOST));
        if (target != null) {
            return target + path;
        }
        return path;
    }

    /**
     * Builds the User-Agent header value
     * The User-Agent header value is built from the Artifactory User-Agent and the JFrog User-Agent URL
     * The Artifactory User-Agent is equal to the product name, product version and product revision
     * the JFrog User-Agent URL is fetched from the JFrog User-Agent URL service
     * The User-Agent header value is in the format of "User-Agent: <product> / <product-version> <comment>"
     * according to the User-Agent header format specified in the HTTP standard:
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent#crawler_and_bot_ua_strings">...</a>
     *
     * @return the User-Agent header value
     */
    private String buildUserAgent() {
        String product = HttpUtils.getArtifactoryUserAgent();
        if (jfrogUserAgentForRemoteRequestsEnabled.getBoolean()) {
            String jfrogUserAgent = jfrogUserAgentService.getUserAgent();
            if (StringUtils.isNotBlank(jfrogUserAgent)) {
                String commentURL = jfrogUserAgentService.getUserAgent();
                return String.format("%s (+%s)", product, commentURL);
            }
        }
        return product;
    }

    /**
     * Records remote repo stats in a fail-safe, non-blocking manner.
     */
    private void recordRemoteRepoStats(String httpMethod, int statusCode,
            long responseTimeMs, long ttfbMs, long responseSize) {
        try {
            if (remoteRepoStatsService != null) {
                remoteRepoStatsService.recordRequest(repoName, repoPackageType,
                        httpMethod, statusCode, responseTimeMs, ttfbMs, responseSize);
            }
        } catch (Exception e) {
            log.debug("Failed to record remote repo stats for repo={}", repoName, e);
        }
    }

    private void enqueueIfEnabled(String httpMethod, String url, int statusCode,
            long durationMs, long requestTime, String traceId) {
        try {
            OutboundRequestLogService svc = outboundLogService();
            if (svc == null || !svc.isFeatureEnabled()) {
                return;
            }
            svc.enqueueOutbound(OutboundRequestLogRow.builder()
                    .id(0L)
                    .requestTime(requestTime)
                    .traceId(traceId)
                    .repoKey(repoName)
                    .httpMethod(httpMethod.toUpperCase(Locale.ROOT))
                    .requestUrl(url)
                    .statusCode(statusCode)
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            log.debug("Failed to enqueue outbound request log entry", e);
        }
    }

    private static OutboundRequestLogService outboundLogService() {
        try {
            return ContextHelper.get().beanForType(OutboundRequestLogService.class);
        } catch (Exception e) {
            log.warn("agentic_api.request_logs.outbound_service_unavailable reason={}", e.getMessage());
            return null;
        }
    }

    private long calculateDuration(long requestStartTime) {
        return timeSupplier.getAsLong() - requestStartTime;
    }

    private Tags createMetricsTags(String method, int statusCode) {
        ActivityContext activity = CurrentActivity.context();
        return Tags.of("status", String.valueOf(statusCode),
                "method", method,
                "group", activity.getGroup(""),
                "package", activity.getPackageType(""));
    }

    private void reportRequestDuration(DownloadPerfCounters counters, Tags tags, long duration) {
        Timer.builder("http.time.remote")
                .description("The time it takes to download artifact from the remote repository")
                .tags(tags)
                .register(this.metrics)
                .record(duration, TimeUnit.MILLISECONDS);
        recordRequest(TARGET_REMOTE, duration);
        // Also append to download performance counters (when relevant)
        if (counters != null) {
            counters.addRemoteDownloadTime(duration);
        }
    }
}
