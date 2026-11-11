package org.artifactory.opentracing;

import org.artifactory.api.events.EventLogHintManager;

import java.util.concurrent.Callable;

import static org.artifactory.event.EventHintType.RemoveRepo;

/**
 * @author Uriah Levy
 */
public class TraceableCallableDecorator<V> implements Callable<V> {
    private final ArtifactoryTracer artifactoryTracer;
    private final Callable<V> callable;
    private final ServerTracingSpan parentSpan;

    public TraceableCallableDecorator(Callable<V> callable, ArtifactoryTracer artifactoryTracer,
            ServerTracingSpan parentSpan) {
        this.callable = callable;
        this.artifactoryTracer = artifactoryTracer;
        this.parentSpan = parentSpan;
    }

    @Override
    public V call() throws Exception {
        String original = Thread.currentThread().getName();
        try {
            // Start a new span that ends at the end of this invocation
            try (ServerTracingScope ignored = artifactoryTracer.startSpanOnCurrentThread(
                    "Artifactory Async Invocation", this.parentSpan)) {
                ThreadNameTraceUtil.enhanceThreadName();
                V result = callable.call();
                ArtifactoryTracer.enrichAndRenameSpanFromActivity();
                return result;
            }
        } finally {
            Thread.currentThread().setName(original);
            EventLogHintManager.clear();
            EventLogHintManager.remove(RemoveRepo);
        }
    }
}
