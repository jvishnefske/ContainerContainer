package org.artifactory.opentracing;

import lombok.RequiredArgsConstructor;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.HttpClientConfiguratorInitializer;
import org.jfrog.common.opentracing.TraceHeaderInjector;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * This interceptor injects tracing context headers into outgoing HTTP requests.
 * All {@link HttpClientConfigurator} instances will automatically inject this interceptor
 * into the underlying HTTP client builder (see {@link HttpClientConfiguratorInitializer}).
 * <p>
 * This class is temporary and will be removed after migrating Artifactory to use
 * the OpenTelemetry Java agent (also for self-hosted customers).
 * </p>
 */
@Component
@RequiredArgsConstructor
public class HttpClientTraceInterceptor implements HttpRequestInterceptor, HttpClientConfiguratorInitializer {
    private final TraceHeaderInjector injector;

    /**
     * {@link HttpClientConfigurator} calls this method from its constructor, allowing us to register
     * ourselves as yet another HTTP request interceptor.
     * @param httpClientConfigurator - the HTTP client configurator to customize.
     */
    @Override
    public void configure(HttpClientConfigurator httpClientConfigurator) {
        if (this.injector != null) {
            httpClientConfigurator.addRequestInterceptor(this);
        }
    }

    /**
     * Retrieves the tracing context from the current thread and injects it into the HTTP request.
     * @param httpRequest - the HTTP request to hook
     * @param httpContext - Context of the HTTP request
     */
    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext) {
        // Get headers from the current span
        Map<String, String> headersToInject = this.injector.headersToInject(
                httpRequest.getRequestLine().getMethod(),
                httpRequest.getRequestLine().getUri());

        // Inject headers to the request (without overriding existing ones)
        headersToInject.forEach((name, value) -> {
            if (!httpRequest.containsHeader(name)) {
                httpRequest.setHeader(name, value);
            }
        });
    }
}
