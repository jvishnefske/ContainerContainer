package org.artifactory.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.jfrog.common.opentracing.TraceHeaderInjector;

import java.io.IOException;
import java.util.Map;

/**
 * Generic tracing interceptor that uses a TraceHeaderInjector to inject
 * propagation headers (e.g., OpenTelemetry) to outgoing HTTP requests.
 */
@Slf4j
public class TracingHttpRequestInterceptor implements HttpRequestInterceptor {
    private final TraceHeaderInjector injector;

    public TracingHttpRequestInterceptor(TraceHeaderInjector injector) {
        this.injector = injector;
    }

    @Override
    public void process(HttpRequest httpRequest, EntityDetails entityDetails, HttpContext httpContext)
            throws HttpException, IOException {
        try {
            Map<String, String> headers =
                    injector.headersToInject(httpRequest.getMethod(), httpRequest.getRequestUri());
            if (headers != null) {
                headers.forEach(httpRequest::addHeader);
                log.trace("Injected tracing headers {} to request {}", headers.keySet(), httpRequest.getRequestUri());
            }
        } catch (Exception e) {
            log.debug("Failed to inject tracing headers for request {}: {}", httpRequest.getRequestUri(), e.toString());
        }
    }
}


