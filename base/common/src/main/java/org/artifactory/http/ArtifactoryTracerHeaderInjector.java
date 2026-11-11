package org.artifactory.http;

import org.artifactory.opentracing.ArtifactoryTracer;
import org.jfrog.common.opentracing.TraceHeaderInjector;

import java.util.Map;

/**
 * Adapter that wraps ArtifactoryTracer to the shared TraceHeaderInjector interface.
 */
public class ArtifactoryTracerHeaderInjector implements TraceHeaderInjector {
    private final ArtifactoryTracer tracer;

    public ArtifactoryTracerHeaderInjector(ArtifactoryTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Map<String, String> headersToInject(String httpMethod, String httpURL) {
        return tracer.headersToInject(httpMethod, httpURL);
    }

    @Override
    public String getCurrentTraceId() {
        try {
            return tracer.getCurrentTraceId();
        } catch (Throwable ignore) {
            return null;
        }
    }

    @Override
    public void cleanup() {
        // No-op for adapter
    }
}


