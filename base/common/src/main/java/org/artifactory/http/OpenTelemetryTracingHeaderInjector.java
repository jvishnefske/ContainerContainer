package org.artifactory.http;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jfrog.common.opentracing.TraceHeaderInjector;

import java.util.HashMap;
import java.util.Map;

/**
 * Default TraceHeaderInjector implementation that uses OpenTelemetry's propagator
 * to inject the current context into an outgoing carrier.
 */
public class OpenTelemetryTracingHeaderInjector implements TraceHeaderInjector {
    private final TextMapPropagator propagator;

    public OpenTelemetryTracingHeaderInjector() {
        this.propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    }

    public OpenTelemetryTracingHeaderInjector(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    @Override
    public Map<String, String> headersToInject(String httpMethod, String httpURL) {
        Map<String, String> carrier = new HashMap<>();
        Context context = Context.current().with(Span.current());
        propagator.inject(context, carrier, Map::put);
        return carrier;
    }

    @Override
    public String getCurrentTraceId() {
        try {
            return Span.current().getSpanContext().getTraceId();
        } catch (Throwable ignore) {
            return null;
        }
    }

    @Override
    public void cleanup() {
        // No-op
    }
}


