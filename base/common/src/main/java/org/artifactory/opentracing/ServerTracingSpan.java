package org.artifactory.opentracing;

import io.opentelemetry.api.trace.Span;
import lombok.Getter;
import org.slf4j.MDC;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;

/**
 * Represents a server tracing span.
 *
 * @author Uriah Levy
 */
@Getter
public class ServerTracingSpan {
    private final Span span;

    ServerTracingSpan(Span span) {
        this.span = span;
    }

    public String getTraceId() {
        return this.span.getSpanContext().getTraceId();
    }

    public void finish() {
        span.end();
        MDC.remove(UBER_TRACE_ID);
    }
}
