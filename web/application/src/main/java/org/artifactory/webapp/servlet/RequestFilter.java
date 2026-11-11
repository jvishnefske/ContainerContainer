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

package org.artifactory.webapp.servlet;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.exception.SearchTooManyRequestsException;
import org.artifactory.logging.layout.request.RequestExtraFields;
import org.artifactory.metrics.MetricsListenerService;
import org.artifactory.metrics.MetricsMetadata;
import org.artifactory.metrics.MetricsThreadLocal;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RedirectInfo;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.response.ResponseThreadLocal;
import org.artifactory.response.ResponseWrapper;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.artifactory.throtlling.search.service.SearchRateLimiterService;
import org.artifactory.traffic.RequestLogger;
import org.artifactory.traffic.entry.RequestEntry;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.slf4j.MDC;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.common.logging.logback.filters.contextaware.request.RequestAwareLogbackContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.apache.http.HttpStatus.SC_MULTIPLE_CHOICES;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.common.ConstantValues.requestLogsQueryParamsEnabled;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.util.HttpUtils.sendErrorResponse;

/**
 * A dedicated filter for the Request Logger that sits after the ArtifactoryFilter
 *
 * @author Noam Tenne
 */
@Slf4j
public class RequestFilter extends DelayedFilterBase {
    private static final RangeMap<Long, String> SIZE_CATEGORY_MAP = createSizeCategoryMap();
    private static final String DEFAULT_VALUE = "unknown";

    private MeterRegistry metrics;
    private RequestLogService requestLogService;
    @Getter
    @Setter
    private LongSupplier currentTimeSupplier = System::currentTimeMillis;

    @Override
    public void initLater(FilterConfig filterConfig) {
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(filterConfig.getServletContext());
        this.metrics = context.beanForType("applicationMeterRegistry", MeterRegistry.class);
        try {
            this.requestLogService = context.beanForType(RequestLogService.class);
        } catch (Exception e) {
            log.debug("RequestLogService not available, request log DB persistence disabled", e);
        }
    }

    @Override
    public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain)
            throws IOException, ServletException {
        if (shouldSkipFilter(req)) {
            chain.doFilter(req, resp);
            return;
        }
        long startTime = getCurrentTime();
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        CapturingHttpServletResponseWrapper responseWrapper =
                new CapturingHttpServletResponseWrapper(response, this.currentTimeSupplier);
        RequestAwareLogbackContext context = null;
        String servletPath = "";
        String username = "non_authenticated_user";
        String method = request.getMethod();
        String remoteAddress = request.getRemoteAddr();
        try {
            remoteAddress = HttpUtils.getRemoteClientAddress(request);
        } catch (URISyntaxException e) {
            log.warn("{}, cause: {} switching to : {}", e.getMessage(), e, remoteAddress);
        }
        int statusCode = 500;
        long endTime = 0;

        try {
            servletPath = RequestUtils.getServletPathFromRequest(request, requestLogsQueryParamsEnabled.getBoolean());
            populateRepositoryInfo(servletPath);
            RequestThreadLocal.bind(new RequestWrapper(request));
            ResponseThreadLocal.bind(new ResponseWrapper(response));
            MetricsThreadLocal.bind(new MetricsMetadata());
            if (ConstantValues.enableContextAwareLoggerEnabled.getBoolean()) {
                context = bindAndFillRequestAwareLogbackContext(request);
            }
            processRequestWithQueryRateLimiter(req, chain, request, responseWrapper);
            endTime = getCurrentTime();
            Object userAtt = req.getAttribute(AccessFilter.AUTHENTICATED_USERNAME_ATTRIBUTE);
            username = userAtt != null ? userAtt.toString() : "non_authenticated_user";

            statusCode = getStatusCode(responseWrapper);
            getArtifactoryContext().beanForType(MetricsListenerService.class)
                    .send(statusCode, responseWrapper.getContentLength(), request);
        } catch (AccessClientHttpException accessClientHttpException) {
            statusCode = accessClientHttpException.getStatusCode();
            responseWrapper.recordStatusForMetrics(statusCode);
            ResponseThreadLocal.setStatusCode(statusCode);
            throw accessClientHttpException;
        } catch (Exception e) {
            int statusFromResponse = getStatusCode(responseWrapper);
            boolean statusIsNotUpdated = statusFromResponse == HttpServletResponse.SC_OK;
            statusCode = statusIsNotUpdated ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : statusFromResponse;
            responseWrapper.recordStatusForMetrics(statusCode);
            throw e;
        } finally {
            endTime = endTime > 0 ? endTime : getCurrentTime();
            Map<String, Object> metadata = collectRequestLogMetadata(request, statusCode, responseWrapper, startTime);
            RequestEntry entry = new RequestEntry(remoteAddress, username, method, servletPath,
                    statusCode, responseWrapper.getContentLength(),
                    HttpUtils.getContentLength(request), endTime - startTime,
                    request.getHeader(HttpHeaders.USER_AGENT));
            entry.setMetadata(metadata);
            RequestLogger.request(entry);
            enqueueInboundIfEnabled(startTime, username, method, servletPath, statusCode,
                    endTime - startTime);

            reportRequestDuration(request, statusCode, startTime, endTime, responseWrapper.getTimeToSetStatus());
            reportRequestsWithExpectHeaders(request, statusCode, HttpUtils.getContentLength(request));
            RequestThreadLocal.unbind();
            ResponseThreadLocal.unbind();
            MetricsThreadLocal.unbind();
            if (context != null) {
                context.destroy();
            }
        }
    }

    private Map<String, Object> collectRequestLogMetadata(HttpServletRequest request, int statusCode,
            CapturingHttpServletResponseWrapper responseWrapper, long startTime) {
        Map<String, Object> metadata = new HashMap<>();
        if (ConstantValues.requestLogsExtraDetailsEnabled.getBoolean()) {
            if (responseWrapper.getTimeToSetStatus() != null) {
                metadata.put(RequestExtraFields.TIME_TO_FIRST_BYTE,
                        responseWrapper.getTimeToSetStatus() - startTime);
            }
            populateDownloadPerformanceDetails(metadata, request, statusCode, responseWrapper.getContentLength());
        }
        if (ConstantValues.requestLogsExternalRequestIdEnabled.getBoolean()) {
            // NGINX, ISTIO and some other proxies often pass their own request ID in the X-Request-ID header
            // Saving it in the MDC will allow us to correlate the request with the logs (JSON only)
            String externalRequestId = request.getHeader("X-Request-ID");
            if (externalRequestId != null && !externalRequestId.isEmpty()) {
                metadata.put(RequestExtraFields.EXTERNAL_ID, externalRequestId);
            }
        }
        populateRedirectDetails(metadata, request);
        populateAqlQueryChecksum(metadata, request);
        return metadata;
    }

    /**
     * Adds {@code aql_checksum} to {@code /api/search/aql} log lines. The checksum is read from the
     * request attribute populated during query handling, so it is present whenever a template was
     * computed -- both for executed queries and for queries rejected by the rate limiter (the 429
     * path parses the query via {@code computeQueryTemplate} specifically to populate it).
     * <p>
     * The attribute is only set when AQL statistics collection is enabled; when it is disabled the
     * query is never parsed for templating and the request is logged without a checksum.
     */
    private void populateAqlQueryChecksum(Map<String, Object> metadata, HttpServletRequest request) {
        Object cached = request.getAttribute(AqlQueryTemplate.REQ_ATTR_KEY);
        if (cached instanceof AqlQueryTemplate template && template.checksum() != null) {
            metadata.put(RequestExtraFields.AQL_QUERY_CHECKSUM, template.checksum());
        }
    }

    private void populateDownloadPerformanceDetails(Map<String, Object> metadata, HttpServletRequest request,
            int statusCode, long contentLength) {
        DownloadPerfCounters downloadPerfCounters =
                (DownloadPerfCounters) request.getAttribute(DownloadPerfCounters.class.getName());
        Long artifactSize = downloadPerfCounters == null ? null : downloadPerfCounters.getArtifactSizeBytes();

        // When we returned only a partial content, redirected to another URL,
        // or failed processing the request, it is worth logging the full size of
        // the requested artifact to allow further analysis of how much data we supposed to stream.
        if (artifactSize != null && (artifactSize != contentLength || statusCode >= 400)) {
            metadata.put(RequestExtraFields.ARTIFACT_SIZE, artifactSize);
        }

        // Report the time we spent, downloading the artifact from the remote repository
        if (downloadPerfCounters != null) {
            if (downloadPerfCounters.getRemoteDownloadDurationMillis() != null) {
                metadata.put(RequestExtraFields.REMOTE_DOWNLOAD_TIME,
                        downloadPerfCounters.getRemoteDownloadDurationMillis());
            }
            if (ConstantValues.requestLogsWaitTimeDetailsEnabled.getBoolean() &&
                    downloadPerfCounters.getExternalWaitTimeMsec() != null) {
                metadata.put(RequestExtraFields.WAITED_TIME, downloadPerfCounters.getExternalWaitTimeMsec());
            }
        }

        // Report the requested range (when relevant)
        HttpUtils.RangesSummary ranges =
                HttpUtils.summarizeRangesRequested(request.getHeader("Range"), artifactSize);
        if (ranges != null) {
            metadata.put(RequestExtraFields.RANGES_COUNT, ranges.rangesCount());
            if (ranges.offset() >= 0) {
                metadata.put(RequestExtraFields.RANGES_OFFSET, ranges.offset());
            }
            if (ranges.size() >= 0) {
                metadata.put(RequestExtraFields.RANGES_SIZE, ranges.size());
            }
        }
    }

    private void populateRedirectDetails(Map<String, Object> metadata, HttpServletRequest request) {
        RedirectInfo redirectInfo = (RedirectInfo) request.getAttribute(RedirectInfo.class.getName());
        if (redirectInfo == null) {
            return;
        }
        if (StringUtils.isNotBlank(redirectInfo.getRedirectTarget())) {
            metadata.put(RequestExtraFields.REDIRECT_TARGET, redirectInfo.getRedirectTarget());
        }
    }

    private void reportRequestsWithExpectHeaders(HttpServletRequest request, int statusCode, long contentLength) {
        if (request.getHeader("Expect") != null) {
            Tags tags = Tags.of("status", String.valueOf(statusCode), "method", request.getMethod());
            Counter.builder("http.requests.with.expect.header")
                    .tags(tags)
                    .register(metrics)
                    .increment();
            if (statusCode >= SC_OK && statusCode < SC_MULTIPLE_CHOICES) {
                tags = tags.and("size", getSizeCategory(contentLength));
                Counter.builder("http.requests.with.expect.header.size")
                        .tags(tags)
                        .register(metrics)
                        .increment();
            }
        }
    }

    private String getSizeCategory(long contentLength) {
        String value = SIZE_CATEGORY_MAP.get(contentLength);
        return value == null ? DEFAULT_VALUE : value;
    }

    private static RangeMap<Long, String> createSizeCategoryMap() {
        RangeMap<Long, String> rangeMap = TreeRangeMap.create();
        final long KB = 1024;
        final long MB = 1024 * KB;
        final long GB = 1024 * MB;

        rangeMap.put(Range.closed(-1L, -1L), "2gb+");
        rangeMap.put(Range.closed(0L, 200L * KB), "0-200kb");
        rangeMap.put(Range.closed(200L * KB + 1, MB), "200kb-1mb");
        rangeMap.put(Range.closed(MB + 1, 10 * MB), "1mb-10mb");
        rangeMap.put(Range.closed(10 * MB + 1, GB), "100mb-1gb");
        rangeMap.put(Range.closed(GB + 1, 2 * GB), "1gb-2gb");

        return rangeMap;
    }

    private void processRequestWithQueryRateLimiter(ServletRequest req, FilterChain chain, HttpServletRequest request,
            CapturingHttpServletResponseWrapper responseWrapper) throws ServletException, IOException {
        try {
            QueryRateLimiterService qrlService = getArtifactoryContext().beanForType(QueryRateLimiterService.class);
            SearchRateLimiterService searchRateLimiterService =
                    getArtifactoryContext().beanForType(SearchRateLimiterService.class);
            qrlService.wrapRequestWithLowPriority(request, () ->
                    searchRateLimiterService.wrapRequestWithSearchLimit(request,
                            () -> chain.doFilter(req, responseWrapper)));
        } catch (LoadHealerException | SearchTooManyRequestsException e) {
            responseWrapper.setStatus(TOO_MANY_REQUESTS.getStatusCode());
            sendErrorResponse(responseWrapper, TOO_MANY_REQUESTS.getStatusCode(), e.getMessage());
        }
    }

    private long getCurrentTime() {
        return this.currentTimeSupplier.getAsLong();
    }

    private void reportRequestDuration(HttpServletRequest request, int statusCode,
            long timeStart, long timeEnd, Long timeToSetStatus) {
        ActivityContext activity = CurrentActivity.context();
        Tags tags = Tags.of("status", String.valueOf(statusCode),
                "method", request.getMethod() == null ? "" : request.getMethod(),
                "group", activity.getGroup(""),
                "package", activity.getPackageType(""));

        // Report how much time it takes us to start processing the request (initialize context, MFE etc.)
        if (request.getAttribute(ServletUtils.ATTR_REQUEST_START_TIME) instanceof Long start) {
            Timer.builder("http.time.to_start")
                    .description("The time taken to start processing incoming HTTP requests")
                    .tags(tags)
                    .register(this.metrics)
                    .record(timeStart - start, TimeUnit.MILLISECONDS);
        }
        long durationToResolveResource;
        long durationToWriteResponse;
        long totalDuration = timeEnd - timeStart;
        if (timeToSetStatus != null) {
            durationToResolveResource = timeToSetStatus - timeStart;
            durationToWriteResponse = timeEnd - timeToSetStatus;
        } else {
            durationToResolveResource = totalDuration;
            durationToWriteResponse = 0;
            log.debug("response.setStatus() was not called on wrapper for: {} {}; using fallback durations for metrics",
                    request.getMethod(), request.getRequestURI());
        }
        // How long it takes us to actually process the request, without streaming the response to the client
        Timer.builder("http.time.resolve")
                .description("The time taken to resolve the requested resource.")
                .tags(tags)
                .register(this.metrics)
                .record(durationToResolveResource, TimeUnit.MILLISECONDS);

        Timer.builder("http.time.resolve.external.wait")
                .description("Per-request total wait time spent on external services (Xray, remote upstream).")
                .tags(tags)
                .register(this.metrics)
                .record(getRequestExternalWaitMillis(request), TimeUnit.MILLISECONDS);

        Timer.builder("http.time.write")
                .description("The time taken to write the response body to HTTP.")
                .tags(tags)
                .register(this.metrics)
                .record(durationToWriteResponse, TimeUnit.MILLISECONDS);

        Timer.builder("http.time.total")
                .description("The total time taken to process incoming HTTP requests")
                .tags(tags)
                .register(this.metrics)
                .record(totalDuration, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the sum of all external-service wait times (Xray, remote upstream, ...) recorded
     * on this request's {@link DownloadPerfCounters}, in milliseconds.
     * Returns 0 when no counters were attached or no external wait was recorded, so the
     * companion timer can be emitted on every request and stay aligned 1:1 with
     * {@code http.time.resolve}.
     */
    private long getRequestExternalWaitMillis(HttpServletRequest request) {
        DownloadPerfCounters counters =
                (DownloadPerfCounters) request.getAttribute(DownloadPerfCounters.class.getName());
        if (counters == null) {
            return 0L;
        }
        Map<String, Long> waitTimes = counters.getExternalWaitTimeMsec();
        if (MapUtils.isEmpty(waitTimes)) {
            return 0L;
        }
        return waitTimes.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private int getStatusCode(CapturingHttpServletResponseWrapper responseWrapper) {
        if (ResponseThreadLocal.isAbortedByClient()) {
            return 499;
        }
        return Optional.ofNullable(ResponseThreadLocal.getStatusCode())
                .orElseGet(responseWrapper::getStatus);
    }

    private RequestAwareLogbackContext bindAndFillRequestAwareLogbackContext(HttpServletRequest request) {
        RequestAwareLogbackContext context = RequestAwareLogbackContext.fromRequest(request);
        context.setKeyValueFetcher("request.remote_ip", () -> {
            try {
                return HttpUtils.getRemoteClientAddress(request);
            } catch (URISyntaxException e) {
                log.debug("{}, cause: {}", e.getMessage(), e);
                return request.getRemoteAddr();
            }
        });
        context.setKeyValueFetcher("user",
                () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                        .map(Authentication::getPrincipal).map(Object::toString).orElse(null));
        return context;
    }

    private void populateRepositoryInfo(String servletPath) {
        try {
            String repoKey = RequestUtils.extractRepoKey(servletPath);
            if (repoKey != null) {
                RepoDetailsType repoType = getRepoType(repoKey);
                if (repoType != null) {
                    CurrentActivity.context().putRepoKey(repoKey);
                    String repoTypeChar = getRepoTypeChar(repoType);
                    if (repoTypeChar != null) {
                        CurrentActivity.context().putRepoType(repoTypeChar);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract repository information from request: {}", e.getMessage());
        }
    }

    private RepoDetailsType getRepoType(String repoKey) {
        RepositoryConfigService repoService = ContextHelper.get().getRepositoryConfigService();
        if (repoService.getLocalOrFederatedRepoConfigByKey(repoKey) != null) {
            return LOCAL;
        } else if (repoService.getRemoteRepoConfigByKey(repoKey) != null) {
            return REMOTE;
        } else if (repoService.getVirtualRepoConfigByKey(repoKey) != null) {
            return VIRTUAL;
        } else {
            return null;
        }
    }

    private String getRepoTypeChar(RepoDetailsType repoType) {
        if (repoType == VIRTUAL) {
            return "virtual";
        } else if (repoType == LOCAL || repoType == FEDERATED || repoType == RELEASE_BUNDLE) {
            return "local";
        } else if (repoType == REMOTE) {
            return "remote";
        }
        return null;
    }

    private void enqueueInboundIfEnabled(long requestTime, String username, String method, String path,
            int statusCode, long durationMs) {
        if (requestLogService == null || !requestLogService.isFeatureEnabled()) {
            return;
        }
        RequestLogRow row = RequestLogRow.builder()
                .id(0L)
                .requestTime(requestTime)
                .traceId(MDC.get("uber-trace-id"))
                .username(username)
                .httpMethod(method.toUpperCase(Locale.ROOT))
                .requestPath(path)
                .statusCode(statusCode)
                .durationMs(durationMs)
                .build();
        requestLogService.enqueueInbound(row);
    }

    @Override
    public void destroy() {
        // nop
    }
}