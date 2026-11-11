package org.artifactory.http;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.util.function.Supplier;

import static org.artifactory.metrics.ExternalClientsServiceMetrics.recordRequest;

/**
 * HTTP interceptor that records latency metrics for requests to external services (e.g., Xray, Access).
 * <p>Measures TTFB the time from sending the request until receiving the response headers.
 * This does NOT include the full response body download time.</p>
 *
 * @author Amir Avidan
 */
@Slf4j
public class ExternalClientsMetricsInterceptor implements HttpRequestInterceptor, HttpResponseInterceptor {

    static final String TARGET_SERVICE_ATTR = "http_target_service";

    private static final String REQUEST_START_TIME = "http_request_start_time";
    private final String serviceName;
    @Setter
    private Supplier<Long> timeSupplier = System::currentTimeMillis;

    public ExternalClientsMetricsInterceptor(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext) {
        httpContext.setAttribute(REQUEST_START_TIME, timeSupplier.get());
    }

    @Override
    public void process(HttpResponse httpResponse, HttpContext httpContext) {
        Long startTime = (Long) httpContext.getAttribute(REQUEST_START_TIME);
        if (startTime == null) {
            log.warn("No request start time found in http context");
            return;
        }
        long latency = timeSupplier.get() - startTime;
        String targetService = (String) httpContext.getAttribute(TARGET_SERVICE_ATTR);
        if (targetService == null) {
            targetService = serviceName;
        }
        recordRequest(targetService, latency);
    }
}

