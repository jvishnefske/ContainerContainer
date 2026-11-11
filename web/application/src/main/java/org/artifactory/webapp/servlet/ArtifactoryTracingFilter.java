package org.artifactory.webapp.servlet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingScope;
import org.artifactory.opentracing.ThreadNameTraceUtil;
import org.artifactory.activity.ActivityScope;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_KIND_HTTP;

/**
 * @author Uriah Levy
 */
@Slf4j
public class ArtifactoryTracingFilter extends DelayedFilterBase {

    private ArtifactoryTracer artifactoryTracer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = startServerSpan((HttpServletRequest) request)) {
            String original = Thread.currentThread().getName();
            try {
                String limitedUri = StringUtils.right(((HttpServletRequest) request).getRequestURI(), 60);
                ThreadNameTraceUtil.enhanceThreadName(limitedUri);
                // Save the current activity context in the servlet request, so that other middlewares can access it
                request.setAttribute(ActivityContext.class.getName(), CurrentActivity.context());
                updateCurrentActivityType((HttpServletRequest) request);
                chain.doFilter(request, response);
            } finally {
                if (CurrentActivity.NAME.get() == null) {
                    ArtifactoryTracer.enrichAndRenameHttpSpanFromActivity();
                }
                Thread.currentThread().setName(original);
            }
        }
    }

    public void updateCurrentActivityType(HttpServletRequest request) {
        // Examples: http/get, http/put, http/delete
        CurrentActivity.KIND.put(ACTIVITY_KIND_HTTP + "/" + StringUtils.lowerCase(request.getMethod()));
    }

    @Override
    public void destroy() {
        // No resources to release
    }

    @Override
    protected void initLater(FilterConfig config) {
        // nop
    }

    @VisibleForTesting
    ServerTracingScope startServerSpan(HttpServletRequest request) {
        ArtifactoryTracer tracer = getArtifactoryTracer();
        if (tracer == null) {
            return ServerTracingScope.NOOP;
        }
        if (tracer.isOpenTelemetryAgentActive()) {
            // OpenTelemetry Java agent already created a server span for the incoming request, there is
            // no need to create a new one.  We just need to wrap the current span with 'tracing scope'
            // to store trace id in the MDC.
            return tracer.openScopeForCurrentSpan();
        }

        String requestPath = request.getPathInfo();

        Map<String,String> headers;
        if (request.getHeaderNames() != null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            StreamSupport
                    .stream(((Iterable<String>) () -> Iterators.forEnumeration(request.getHeaderNames()))
                            .spliterator(), false)
                    .filter(StringUtils::isNotBlank)
                    .filter(headerName -> request.getHeader(headerName) != null)
                    .forEach(k -> headers.put(k, request.getHeader(k)));
        } else {
            log.debug("No headers found in the request {}, starting span without headers", requestPath);
            headers = Map.of();
        }
        return tracer.startServerSpan(requestPath, headers);
    }

    private ArtifactoryTracer getArtifactoryTracer() {
        if (this.artifactoryTracer == null) {
            ArtifactoryContext context = getArtifactoryContext();
            if (context != null) {
                this.artifactoryTracer = context.beanForType(ArtifactoryTracer.class);
            }
        }
        return this.artifactoryTracer;
    }

    void setArtifactoryTracer(ArtifactoryTracer tracer) {
        this.artifactoryTracer = tracer;
    }
}
