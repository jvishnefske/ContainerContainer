package org.artifactory.opentracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.Getter;
import org.slf4j.MDC;

import java.io.Closeable;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;

/**
 * Resembles a server-side Tracing Scope. The scope is the product of a created span.
 * The span is started and finished via an instance of this class
 *
 * @author Uriah Levy
 */
public class ServerTracingScope implements Closeable {
    public static final ServerTracingScope NOOP = new ServerTracingScope();
    @Getter
    private final Span span;
    @Getter
    private final Scope tracingScope;
    private String previousTraceId;

    ServerTracingScope() {
        this.tracingScope = null;
        this.span = null;
    }

    ServerTracingScope(boolean mdcOnly) {
        this(null);
        if (mdcOnly) {
            Span current = Span.current();
            if (current != null && current.getSpanContext().isValid()) {
                this.previousTraceId = MDC.get(UBER_TRACE_ID);
                MDC.put(UBER_TRACE_ID, current.getSpanContext().getTraceId());
            }
        }
    }

    ServerTracingScope(Span span) {
        this.span = span;
        this.previousTraceId = MDC.get(UBER_TRACE_ID);
        if (span != null && span.getSpanContext().isValid()) {
            MDC.put(UBER_TRACE_ID, span.getSpanContext().getTraceId());
            this.tracingScope = span.makeCurrent();
        } else {
            this.tracingScope = null;
        }
    }

    @Override
    public void close() {
        ArtifactoryTracer.enrichCurrentSpanFromActivity();
        if (this.tracingScope != null) {
            this.tracingScope.close();
        }
        if (this.span != null) {
            this.span.end();
        }
        if (this.previousTraceId == null || this.previousTraceId.isEmpty()) {
            MDC.remove(UBER_TRACE_ID);
        } else {
            MDC.put(UBER_TRACE_ID, this.previousTraceId);
        }
    }
}
