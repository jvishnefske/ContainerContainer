package org.artifactory.opentracing;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.common.opentracing.TraceHeaderInjector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the lifecycle of server-side tracing spans using OpenTelemetry.
 * Use this to create spans, or retrieve information about existing spans (such as the trace-id, etc)
 *
 * @author Uriah Levy
 * @author Sergey Steinvil
 */
@Slf4j
@Component
public class ArtifactoryTracer implements TraceHeaderInjector {
    //#region ---------- Support both W3C and Jaeger trace headers ----------
    private static final TextMapPropagator JAEGER_TRACE_CONTEXT_PROPAGATOR =
            JaegerPropagator.getInstance();
    private static final TextMapPropagator DEFAULT_TRACE_CONTEXT_PROPAGATOR =
            TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(),
                    W3CBaggagePropagator.getInstance(),
                    JAEGER_TRACE_CONTEXT_PROPAGATOR
            );
    private static final HashTextMapAdapter TEXT_MAP_ADAPTER = new HashTextMapAdapter();
    //#endregion
    public static final String UBER_TRACE_ID = "uber-trace-id";
    public static final String COLON = ":";
    @Getter
    final Tracer tracer;
    @Getter
    final boolean isOpenTelemetryAgentActive;

    @Autowired
    public ArtifactoryTracer(Tracer tracer) {
        this(tracer, tracer != null && tracer.getClass().getName().contains("javaagent"));
    }

    public ArtifactoryTracer(Tracer tracer, boolean isOpenTelemetryAgentActive) {
        this.tracer = tracer;
        this.isOpenTelemetryAgentActive = isOpenTelemetryAgentActive;
    }

    /**
     * @return the scoped (current thread) span, if an active span exists
     */
    @Nullable
    public ServerTracingSpan getCurrentThreadActiveSpan() {
        Span activeSpan = Span.current();
        if (activeSpan.getSpanContext().isValid()) {
            return new ServerTracingSpan(activeSpan);
        }
        return null;
    }

    public ServerTracingScope startSpanOnCurrentThread(String operation) {
        return startSpanOnCurrentThread(operation, null);
    }

    /**
     * Starts a scoped (current thread) span. It is the callers responsibility to close the returned Scope
     * which will finish the underlying span as well, otherwise the Span will remain active.
     *
     * @return the {@link Scope} that represent the newly activated span
     */
    public ServerTracingScope startSpanOnCurrentThread(String operation, @Nullable ServerTracingSpan parentSpan) {
        SpanBuilder spanBuilder = this.tracer.spanBuilder(operation);
        if (parentSpan != null && parentSpan.getSpan().getSpanContext().isValid()) {
            // The new span should follow the 'parent' span, inheriting its trace id
            spanBuilder.setParent(Context.current().with(parentSpan.getSpan()));
            spanBuilder.setSpanKind(SpanKind.CONSUMER);
        }
        Span span = spanBuilder.startSpan();
        return new ServerTracingScope(span);
    }

    /**
     * Starts a scoped (current thread) span.
     * It is the callers' responsibility to close the returned Scope which will
     * finish the underlying span as well, otherwise the Span will remain active.
     *
     * @param operation      The operation name that will be span name
     * @param requestHeaders The request headers of the referenced span
     * @return The {@link Scope} that represent the newly activated span
     */
    public ServerTracingScope startServerSpan(String operation, Map<String, String> requestHeaders) {
        Context extractedContext = extractSpanContextFromRequest(requestHeaders);
        SpanBuilder spanBuilder = this.tracer.spanBuilder(operation)
                .setSpanKind(SpanKind.SERVER);

        if (extractedContext != null) {
            log.debug("Found existing Tracing Span. A new child Server Tracing Span will be associated with it");
            spanBuilder.setParent(extractedContext);
        }

        Span span = spanBuilder.startSpan();
        return new ServerTracingScope(span);
    }

    /**
     * Opens a new scope for the current span, which is already active in the current thread. This method is useful
     * when the OpenTelemetry Java agent is active, as it automatically handles the span creation and activation.
     * Wrapping the current span in a {@link ServerTracingScope} just updates the trace id in the MDC.
     *
     * @return A new {@link ServerTracingScope} wrapping the current span.
     */
    public ServerTracingScope openScopeForCurrentSpan() {
        return new ServerTracingScope(true);
    }

    /**
     * Starts a new span in the current thread, adding the "follows_from" link to the provided context (if any).
     *
     * @param operation         The operation name that will be span name
     * @param scopeContextAsMap The scope context of the referenced span
     * @return The {@link ServerTracingScope} of the newly created span
     */
    public ServerTracingScope startSpanThatFollows(String operation, @Nullable Map<String, String> scopeContextAsMap) {
        SpanBuilder spanBuilder = this.tracer.spanBuilder(operation);

        // The new span should be a child of the current one (if any)
        if (Span.current().getSpanContext().isValid()) {
            spanBuilder.setParent(Context.current());
        }

        // New span should follow the one extracted from the request headers
        Context extractedContext = extractSpanContextFromRequest(scopeContextAsMap);
        if (extractedContext != null) {
            SpanContext spanContext = Span.fromContext(extractedContext).getSpanContext();
            if (spanContext.isValid()) {
                spanBuilder.addLink(spanContext);
                String referencedTraceId = spanContext.getTraceId();
                log.info("Starting span operation: {}, that follows from trace id: {}", operation, referencedTraceId);
            }
        }

        Span span = spanBuilder.startSpan();
        return new ServerTracingScope(span);
    }

    /**
     * TODO: Remove this method in the future, once all clients are updated to use OpenTelemetry interceptors.
     * Gets the current span context, formatted for Jaeger tracing.
     * This method exists for backward compatibility with old HTTP clients,
     * injecting the 'uber-trace-id' header by themselves, without using
     * OpenTelemetry interceptors.
     *
     * @return the full tracing context ID
     */
    public String getFullContextTracingId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            String spanId = currentSpan.getSpanContext().getSpanId();
            String traceId = currentSpan.getSpanContext().getTraceId();
            return traceId + COLON + spanId + COLON + spanId + COLON + 0;
        }
        return null;
    }

    /**
     * TODO: Move this method to some shared testing module.
     * Gets the trace ID from the current span (if any). This method is used only in tests
     * and should not be used in production code.
     * @return The trace ID for the current span (if any), or null if no active span exists.
     */
    @Nullable
    public String getCurrentSpanTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return null;
    }

    /**
     * TODO: Remove this method after instrumenting Artifactory with OpenTelemetry interceptors.
     * Extracts OpenTelemetry context from request headers.
     * <p>
     * It is important to note that we currently
     * support both W3C and Jaeger trace headers. This allows us to support requests, coming from the 'legacy'
     * services, which were not instrumented with OpenTelemetry interceptors yet and also from the new services,
     * which are instrumented with OpenTelemetry interceptors.
     * <p>
     * Today, this method exists just because Artifactory does not use OpenTelemetry
     * servlets middleware (filter), which supposed to do that automatically, not need to have
     * the application code for that.
     *
     * @return A Context extracted from request headers
     */
    @Nullable
    private Context extractSpanContextFromRequest(@Nullable Map<String, String> requestHeaders) {
        if (requestHeaders == null) {
            return null;
        }

        return DEFAULT_TRACE_CONTEXT_PROPAGATOR.extract(Context.current(), requestHeaders, TEXT_MAP_ADAPTER);
    }

    //#region ---------- Span enrichment from CurrentActivity ----------

    /**
     * Copies non-empty {@link CurrentActivity} properties to the current OpenTelemetry span as attributes.
     * Safe to call when no valid span is active (the method returns immediately).
     * <p>
     * This is typically invoked by {@link ServerTracingScope#close()}, so the span captures the full
     * activity context accumulated during the request / task / job.  Does <b>not</b> rename the span.
     */
    public static void enrichCurrentSpanFromActivity() {
        if (isActivityEnrichmentDisabled()) {
            return;
        }
        Span span = Span.current();
        if (!span.getSpanContext().isValid()) {
            return;
        }
        ActivityContext activity = CurrentActivity.context();
        setIfPresent(span, "artifactory.activity.kind", activity.getKind());
        setIfPresent(span, "artifactory.activity.group", activity.getGroup());
        setIfPresent(span, "artifactory.activity.package", activity.getPackageType());
        setIfPresent(span, "artifactory.repo.key", activity.getRepoKey());
        setIfPresent(span, "artifactory.repo.type", activity.getRepoType());
        setIfPresent(span, "artifactory.actor", activity.getActor());
    }

    /**
     * Enriches the current span with activity attributes <b>and</b> renames it using
     * {@link #buildSpanName(ActivityContext)}.  Intended for jobs, async tasks, and {@code @Async} methods.
     */
    public static void enrichAndRenameSpanFromActivity() {
        if (isActivityEnrichmentDisabled()) {
            return;
        }
        enrichCurrentSpanFromActivity();
        renameCurrentSpan(buildSpanName(CurrentActivity.context()));
    }

    /**
     * Enriches the current span with activity attributes <b>and</b> renames it using
     * {@link #buildHttpSpanName(ActivityContext)}.  Intended for non-Jersey, filter-handled HTTP requests.
     */
    public static void enrichAndRenameHttpSpanFromActivity() {
        if (isActivityEnrichmentDisabled()) {
            return;
        }
        enrichCurrentSpanFromActivity();
        renameCurrentSpan(buildHttpSpanName(CurrentActivity.context()));
    }

    static String buildSpanName(ActivityContext activity) {
        String kind = activity.getKind();
        if (kind == null || kind.isEmpty()) {
            return null;
        }
        String qualifier = activity.getName();
        if (qualifier == null || qualifier.isEmpty()) {
            qualifier = activity.getGroup();
        }
        if (qualifier == null || qualifier.isEmpty()) {
            return kind;
        }
        return kind + " " + qualifier;
    }

    static String buildHttpSpanName(ActivityContext activity) {
        String kind = activity.getKind();
        if (kind == null || kind.isEmpty()) {
            return null;
        }
        String qualifier = activity.getGroup();
        if (qualifier == null || qualifier.isEmpty()) {
            return kind;
        }
        return kind + " " + qualifier;
    }

    private static void renameCurrentSpan(String spanName) {
        if (spanName != null) {
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                span.updateName(spanName);
            }
        }
    }

    private static boolean isActivityEnrichmentDisabled() {
        return ArtifactoryHome.isBound()
                && ConstantValues.otelActivityEnrichmentDisabled.getBoolean();
    }

    private static void setIfPresent(Span span, String key, String value) {
        if (value != null && !value.isEmpty()) {
            span.setAttribute(key, value);
        }
    }

    //#endregion

    //#region ---------- TraceHeaderInjector implementation ----------
    @Override
    public String getCurrentTraceId() {
        // This is a legacy function, which is nobody should actually call.
        return getFullContextTracingId();
    }

    @Override
    public Map<String, String> headersToInject(String httpMethod, String httpURL) {
        Map<String, String> headers = new HashMap<>();
        DEFAULT_TRACE_CONTEXT_PROPAGATOR.inject(Context.current(), headers, TEXT_MAP_ADAPTER);
        return headers;
    }

    @Override
    public void cleanup() {
        // Do nothing
    }
    //#endregion

    /**
     * A simple adapter that allow to use a Map as a carrier for OpenTelemetry TextMapPropagator.
     */
    static class HashTextMapAdapter implements TextMapGetter<Map<String, String>>, TextMapSetter<Map<String,String>> {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, @Nonnull String key) {
            return carrier == null ? null : carrier.get(key);
        }

        @Override
        public void set(@javax.annotation.Nullable Map<String,String> carrier, String key, String value) {
            if (carrier != null) {
                carrier.put(key, value);
            }
        }
    }
}