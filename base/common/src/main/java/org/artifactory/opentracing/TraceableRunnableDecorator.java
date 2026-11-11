package org.artifactory.opentracing;

import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.activity.ActivityScope;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_GROUP_API;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_KIND_ASYNC_TASK;
import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.artifactory.mirror.MirrorConstants.ASYNC_THREAD;

/**
 * A runnable decorator that starts a server-side tracing span, either as an independent span or as a child of an
 * existing
 * span
 *
 * @author Uriah Levy
 */
public class TraceableRunnableDecorator implements Runnable {
    private final ArtifactoryTracer artifactoryTracer;
    private final Runnable runnable;
    private final ServerTracingSpan parentSpan;

    public TraceableRunnableDecorator(Runnable runnable, ArtifactoryTracer artifactoryTracer,
            ServerTracingSpan parentSpan) {
        this.runnable = runnable;
        this.artifactoryTracer = artifactoryTracer;
        this.parentSpan = parentSpan;
    }

    @Override
    public void run() {
        String originalName = Thread.currentThread().getName();
        try (ActivityScope ignoredScope = CurrentActivity.newScope(ACTIVITY_KIND_ASYNC_TASK, ACTIVITY_GROUP_API)) {
            EventLogHintManager.put(AsyncThread, ASYNC_THREAD);
            // Start a new span that ends at the end of this invocation
            try (ServerTracingScope ignored = artifactoryTracer
                    .startSpanOnCurrentThread("Artifactory Async Invocation", this.parentSpan)) {
                ThreadNameTraceUtil.enhanceThreadName();
                runnable.run();
                ArtifactoryTracer.enrichAndRenameSpanFromActivity();
            }
        } finally {
            Thread.currentThread().setName(originalName);
            EventLogHintManager.clear();
            EventLogHintManager.remove(RemoveRepo);
        }
    }
}
