package org.artifactory.shadow;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.PackageTypeUtil;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.metrics.model.Metric;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static com.google.common.net.HttpHeaders.CONNECTION;
import static com.google.common.net.HttpHeaders.DATE;
import static com.google.common.net.HttpHeaders.EXPIRES;
import static com.google.common.net.HttpHeaders.KEEP_ALIVE;
import static com.google.common.net.HttpHeaders.LAST_MODIFIED;
import static com.google.common.net.HttpHeaders.PRAGMA;
import static com.google.common.net.HttpHeaders.STRICT_TRANSPORT_SECURITY;
import static com.google.common.net.HttpHeaders.TRANSFER_ENCODING;
import static org.artifactory.api.rest.constant.RestConstants.PATH_API;
import static org.artifactory.repo.virtual.VirtualRepo.PACKAGE_HANDLER_ENABLED;

@Slf4j
@Service
public class ShadowRequestService {

    private final Set<String> headersToExclude = Stream.of(
                    CACHE_CONTROL, PRAGMA, EXPIRES, DATE, KEEP_ALIVE, CONNECTION, TRANSFER_ENCODING, LAST_MODIFIED,
                    "X-Artifactory-Id", "X-Artifactory-Node-Id", "X-JFrog-Version", "X-Request-ID",
                    "X-Artifactory-Trace-Id", STRICT_TRANSPORT_SECURITY)
            .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

    static final String HEADER_COMPARISON_IS_NULL_MSG = """
            Shadowing failed in headers comparison for path: '%s' and header: '%s'.
            Header content expected: %s, header content actual: '%s'.
            Originated from: %s""";

    static final String HEADER_COMPARISON_NON_EQUAL_MSG = """
            Shadowing failed in headers comparison for path: '%s' and header: '%s'.
            Header content expected: '%s', header content actual: '%s'.
            Originated from: %s""";

    static final String RESPONSE_STATUS_COMPARISON_MSG = """
            Shadowing failed in status comparison for path: '%s'.
            Status expected: '%s', actual: '%s'.
            Originated from: %s""";

    static final String LOCATION_HEADER_COMPARISON_MSG = """
                    Shadowing location header is different in query parameters for path: '%s'.
                    Location expected: '%s', actual: '%s'
                    Originated from: %s""";

    private static final Set<String> printedLogs = new HashSet<>();

    public static final String SHADOW = "shadow";

    public static final String API_URL = "/" + PATH_API;
    public static final String SHADOW_API_URL = API_URL + "/" + SHADOW;

    // Metrics
    private final ShadowMetricRegistry shadowMetricRegistry;

    private final CloseableHttpClient client;

    public ShadowRequestService() {
        this(new ShadowMetricRegistry(), buildClient());
    }

    public ShadowRequestService(ShadowMetricRegistry shadowMetricRegistry, CloseableHttpClient client) {
        this.shadowMetricRegistry = shadowMetricRegistry;
        this.client = client;
    }

    @PreDestroy
    void cleanup() {
        IOUtils.closeQuietly(client, (ioException -> log.error("Failed to close http client", ioException)));
    }

    public ShadowResponseType executeShadow(ShadowRequestContext shadowRequestContext,
            ShadowResponseContext response) {
        if (response == null) {
            log.debug("Skipping shadow comparison: original response context is null");
            return ShadowResponseType.FAILED;
        }
        UrlPair urlPair = calculateRequestAndShadowedUrls(shadowRequestContext);
        log.trace("Shadowing request: from {} to: {}", urlPair.requestUrl, urlPair.shadowedUrl);

        String packageType = PackageTypeUtil.extractPackageType(urlPair.requestUrl());
        log.trace("Shadowing package type: {}", packageType);

        HttpRequest httpRequest =
                new HttpRequest(URI.create(urlPair.shadowedUrl()), shadowRequestContext.requestMethod());
        httpRequest.copyHeaders(shadowRequestContext.headers());
        // Specifically for virtual traversal, we need to add the package handler enabled header
        httpRequest.addHeader(PACKAGE_HANDLER_ENABLED, "true");
        log.trace("Shadowed request: {}", httpRequest);
        String relativeShadowUrl = StringUtils.substringAfter(urlPair.shadowedUrl(), SHADOW_API_URL + "/");
        try {
            return proceedShadow(shadowRequestContext.originalTraceId(), response, httpRequest, packageType,
                    relativeShadowUrl);
        } catch (Exception e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed to proceed shadow", e);
        }

        updateFailureShadowMetric(packageType, ShadowMetricLabels.ShadowMetricResultLabelType.ERROR);
        return ShadowResponseType.FAILED;
    }

    @Nonnull
    UrlPair calculateRequestAndShadowedUrls(ShadowRequestContext shadowRequestContext) {
        String requestUrl = shadowRequestContext.requestUri();
        String shadowedUrl = requestUrl.replaceFirst(API_URL, SHADOW_API_URL);
        return new UrlPair(requestUrl, shadowedUrl);
    }

    record UrlPair(String requestUrl, String shadowedUrl) {
    }

    private ShadowResponseType proceedShadow(String originalTraceId, ShadowResponseContext response,
            HttpRequest httpRequest, String packageType, String relativeShadowedUrl) throws IOException {
        ShadowResponseType result = ShadowResponseType.SUCCEEDED;
        try (CloseableHttpResponse shadowedResponse = client.execute(httpRequest)) {
            log.debug("Shadowed response: {} originated from :{}", shadowedResponse, originalTraceId);
            int shadowStatus = shadowedResponse.getStatusLine().getStatusCode();
            if (shadowStatus != response.status()) {
                if (HttpStatus.SC_NOT_IMPLEMENTED == shadowStatus) {
                    log.debug("Shadowing for path: '{}' is not implemented yet.", relativeShadowedUrl);
                    return ShadowResponseType.NOT_IMPLEMENTED;
                }
                log.warn(String.format(RESPONSE_STATUS_COMPARISON_MSG, relativeShadowedUrl, response.status(),
                        shadowStatus, originalTraceId));
                updateFailureShadowByStatusMetric(packageType);

                // If the shadowed response code is not equal to the original response code,
                // we don't need to check the headers
                return ShadowResponseType.FAILED;
            }

            boolean hasNonEqualHeader =
                    hasAnyNonEqualHeader(originalTraceId, response, relativeShadowedUrl, shadowedResponse);
            if (hasNonEqualHeader) {
                result = ShadowResponseType.FAILED;
                updateFailureShadowByHeadersMetric(packageType);
            }
        }
        if (ShadowResponseType.SUCCEEDED == result) {
            updateSuccessfulShadowMetric(packageType);
        }

        printedLogs.clear();
        return result;
    }

    private boolean hasAnyNonEqualHeader(String originalTraceId, ShadowResponseContext response,
            String relativeShadowedUrl, CloseableHttpResponse shadowedResponse) {

        Map<String, Set<String>> mergedShadowHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Arrays.stream(shadowedResponse.getAllHeaders())
                .filter(header -> !headersToExclude.contains(header.getName()))
                .forEach(e -> mergedShadowHeaders.computeIfAbsent(e.getName(), k -> new HashSet<>()).add(e.getValue()));

        Map<String, Set<String>> mergedOriginalHeaders = response.headers().entrySet().stream()
                .filter(entry -> !headersToExclude.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(Object::toString).collect(Collectors.toSet()),
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        },
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
                ));

        Set<String> originalHeaderLocation = mergedOriginalHeaders.remove(HttpHeaders.LOCATION);
        Set<String> shadowHeaderLocation = mergedShadowHeaders.remove(HttpHeaders.LOCATION);
        boolean hasNonEqualHeader = isNonEqualLocationHeader(originalTraceId, relativeShadowedUrl,
                originalHeaderLocation, shadowHeaderLocation);

        Set<String> originalContentLength = mergedOriginalHeaders.get(HttpHeaders.CONTENT_LENGTH);
        Set<String> shadowContentLength = mergedShadowHeaders.get(HttpHeaders.CONTENT_LENGTH);
        if (shadowContentLength != null && shadowContentLength.contains("0") && originalContentLength == null) {
            // Remove Content-Length from both headers map to ignore it
            log.trace("Content-Length header removed from both original and shadow headers.");
            mergedOriginalHeaders.remove(HttpHeaders.CONTENT_LENGTH);
            mergedShadowHeaders.remove(HttpHeaders.CONTENT_LENGTH);
        }

        hasNonEqualHeader |=
                isNonEqualHeaders(originalTraceId, relativeShadowedUrl, mergedOriginalHeaders, mergedShadowHeaders);

        return hasNonEqualHeader;
    }

    /**
     * In case of location header, the query params might be unique, so we want to compare only the path.
     * For example, in storage redirect response, the trace-id is part of the query params.
     *
     * @return true - if location headers are different in path, otherwise false
     */
    private boolean isNonEqualLocationHeader(String originalTraceId, String relativeShadowedUrl,
            Set<String> originalHeaderLocation, Set<String> shadowHeaderLocation) {

        if (originalHeaderLocation == null && shadowHeaderLocation == null) {
            return false;
        }

        if (originalHeaderLocation == null || shadowHeaderLocation == null) {
            log.debug("""
                            Shadowing location header is different in query parameters for path: '{}'.
                            {} header location is null.
                            Originated from :{}""", relativeShadowedUrl,
                    originalHeaderLocation == null ? "Original" : "Shadow", originalTraceId);
            return true;
        }

        String originalLocationHeader = originalHeaderLocation.stream().findFirst().orElse("");
        String shadowLocationHeader = shadowHeaderLocation.stream().findFirst().orElse("");

        if (originalLocationHeader.equals(shadowLocationHeader)) {
            return false;
        }

        String originalHeaderStriped = HttpUtils.stripQuery(originalLocationHeader);
        String shadowHeaderStriped = HttpUtils.stripQuery(shadowLocationHeader);

        Set<String> originalStripedSet = new HashSet<>();
        originalStripedSet.add(originalHeaderStriped);

        Set<String> shadowStripedSet = new HashSet<>();
        shadowStripedSet.add(shadowHeaderStriped);
        Map<String, Set<String>> shadowContent = new HashMap<>();
        shadowContent.put(HttpHeaders.LOCATION, shadowStripedSet);

        boolean hasNonEqualHeader = isNonEqualHeader(originalTraceId, relativeShadowedUrl, HttpHeaders.LOCATION,
                originalStripedSet, shadowContent, false);

        if (!hasNonEqualHeader) {
            log.debug(String.format(LOCATION_HEADER_COMPARISON_MSG,
                    relativeShadowedUrl, originalLocationHeader, shadowLocationHeader, originalTraceId));
        }
        return hasNonEqualHeader;
    }

    private static boolean isNonEqualHeaders(String originalTraceId, String relativeShadowedUrl,
            Map<String, Set<String>> mergedOriginalHeaders, Map<String, Set<String>> mergedShadowHeaders) {

        boolean hasNonEqualHeader = isNonEqualHeaderContent(originalTraceId, relativeShadowedUrl,
                mergedOriginalHeaders, mergedShadowHeaders, false);
        hasNonEqualHeader |= isNonEqualHeaderContent(originalTraceId, relativeShadowedUrl,
                mergedShadowHeaders, mergedOriginalHeaders, true);

        return hasNonEqualHeader;
    }

    /**
     * This method gets two maps and compares between the content of each key
     *
     * @param originalTraceId     the original trace id of the original request the shadow is testing
     * @param relativeShadowedUrl The relative path of the shadow request - part of the log information
     * @param headersMap          map of headers and the aggregated values.
     *                            e.g. Swift has link header that can appear more than once.
     * @param mapToCompereWith    map of headers and the aggregated values we want to compare with headersMap.
     *                            *                   e.g. Swift has link header that can appear more than once.
     * @param isShadowContent     true, if headersMap is the merged shadow headers,
     *                            false, if the merged headers is from the original request
     * @return true if we found non-equal header
     */
    private static boolean isNonEqualHeaderContent(String originalTraceId, String relativeShadowedUrl,
            Map<String, Set<String>> headersMap, Map<String, Set<String>> mapToCompereWith,
            boolean isShadowContent) {
        boolean hasNonEqualHeader = false;
        for (Map.Entry<String, Set<String>> header : headersMap.entrySet()) {
            String headerName = header.getKey();
            Set<String> headerContent = header.getValue();
            hasNonEqualHeader |= isNonEqualHeader(originalTraceId, relativeShadowedUrl, headerName, headerContent,
                    mapToCompereWith, isShadowContent);
        }

        return hasNonEqualHeader;
    }

    private static boolean isNonEqualHeader(String originalTraceId, String relativeShadowedUrl, String headerName,
            Set<String> headerContent, Map<String, Set<String>> mapToCompereWith,
            boolean isHeaderContentIsShadowContent) {
        String headerContentStr = String.join(",", headerContent);
        Set<String> comperedContent = mapToCompereWith.get(headerName);
        String logMessage;
        if (comperedContent == null) {
            if (isHeaderContentIsShadowContent) {
                logMessage = String.format(HEADER_COMPARISON_IS_NULL_MSG, relativeShadowedUrl, headerName,
                        headerContentStr, null, originalTraceId);
            } else {
                logMessage = String.format(HEADER_COMPARISON_IS_NULL_MSG, relativeShadowedUrl, headerName,
                        null, headerContentStr, originalTraceId);
            }

            if (shouldPrintLog(logMessage)) {
                log.warn(logMessage);
            }
            return true;
        }

        if (!comperedContent.equals(headerContent)) {
            String comperedContentStr = String.join(",", comperedContent);

            if (isHeaderContentIsShadowContent) {
                logMessage = String.format(HEADER_COMPARISON_NON_EQUAL_MSG, relativeShadowedUrl, headerName,
                        comperedContentStr, headerContentStr,
                        originalTraceId);
            } else {
                logMessage = String.format(HEADER_COMPARISON_NON_EQUAL_MSG, relativeShadowedUrl, headerName,
                        headerContentStr, comperedContentStr,
                        originalTraceId);
            }

            if (shouldPrintLog(logMessage)) {
                log.warn(logMessage);
            }
            return true;
        }
        return false;
    }

    private static boolean shouldPrintLog(String logMessage) {
        if (printedLogs.contains(logMessage)) {
            return false;
        }
        printedLogs.add(logMessage);
        return true;
    }

    private void updateSuccessfulShadowMetric(String packageType) {
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests,
                Map.of(ShadowMetricLabels.PACKAGE_TYPE.displayName, packageType.toLowerCase(),
                        ShadowMetricLabels.RESULT.displayName,
                        ShadowMetricLabels.ShadowMetricResultLabelType.MATCH.displayName));
    }

    private void updateFailureShadowByHeadersMetric(String packageType) {
        updateFailureShadowMetric(packageType, ShadowMetricLabels.ShadowMetricResultLabelType.HEADERS);
    }

    private void updateFailureShadowByStatusMetric(String packageType) {
        updateFailureShadowMetric(packageType, ShadowMetricLabels.ShadowMetricResultLabelType.STATUS);
    }

    private void updateFailureShadowMetric(String packageType,
            ShadowMetricLabels.ShadowMetricResultLabelType resultType) {
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests,
                Map.of(ShadowMetricLabels.PACKAGE_TYPE.displayName, packageType.toLowerCase(),
                        ShadowMetricLabels.RESULT.displayName, resultType.displayName));
    }

    public List<Metric> collectMetrics() {
        return new LinkedList<>(shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests)));
    }

    private static CloseableHttpClient buildClient() {
        HttpClientConfigurator httpClientConfigurator = new HttpClientConfigurator()
                .connectionTimeout(ConstantValues.shadowingConnectionTimeoutMillis.getInt())
                .maxTotalConnections(ConstantValues.shadowingMaxTotalConnections.getInt())
                .socketTimeout(ConstantValues.shadowingSocketTimeoutMillis.getInt());
        httpClientConfigurator.getConfig().setRedirectsEnabled(false);
        return httpClientConfigurator.build();
    }

    @Getter
    static class HttpRequest extends HttpRequestBase {

        private final String method;

        public HttpRequest(final URI uri, final String method) {
            super();
            setURI(uri);
            this.method = method;
        }

        /**
         * @throws IllegalArgumentException if the uri is invalid.
         */
        public HttpRequest(final String uri, final String method) {
            this(URI.create(uri), method);
        }

        public HttpRequest(ContainerRequestContext request) {
            this(request.getUriInfo().getRequestUri().toString(), request.getMethod());
        }

        public void copyHeaders(MultivaluedMap<String, String> headers) {
            headers.forEach((key, value) -> {
                for (String internalValue : value) {
                    this.addHeader(key, internalValue);
                }
            });
        }

        @Override
        public String toString() {
            return "HttpRequest{" +
                    "method='" + method + '\'' +
                    ", headers=" + Arrays.toString(headergroup.getAllHeaders()) +
                    '}';
        }
    }
}
