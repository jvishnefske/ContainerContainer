package org.artifactory.opentracing;

import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.events.EventLogHintManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_GROUP_API;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_KIND_ASYNC_TASK;
import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.mirror.MirrorConstants.ASYNC_THREAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceableRunnableDecoratorTest {

    @Test
    void testMirrorMarkerIsSetAndRemovedOnEachRunnable() {
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        ServerTracingSpan currentSpan = mock(ServerTracingSpan.class);
        when(artifactoryTracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);
        TraceableRunnableDecorator runnableDecorator =
                new TraceableRunnableDecorator(assertMirrorMarker, artifactoryTracer,
                        artifactoryTracer.getCurrentThreadActiveSpan());

        Assertions.assertNull(EventLogHintManager.getHints());
        runnableDecorator.run();
        Assertions.assertNull(EventLogHintManager.getHints());
    }

    @Test
    void traceableRunnableDecoratorShouldSetActivity() {
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        ServerTracingSpan currentSpan = mock(ServerTracingSpan.class);
        when(artifactoryTracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);

        AtomicBoolean didRun = new AtomicBoolean();
        TraceableRunnableDecorator decorator = new TraceableRunnableDecorator(() -> {
            assertEquals(ACTIVITY_KIND_ASYNC_TASK, CurrentActivity.KIND.get());
            assertEquals(ACTIVITY_GROUP_API, CurrentActivity.GROUP.get());
            didRun.set(true);
        }, artifactoryTracer, artifactoryTracer.getCurrentThreadActiveSpan());

        decorator.run();
        assertTrue(didRun.get(), "Runnable did not run");
    }

    private final Runnable assertMirrorMarker = () -> {
        Assertions.assertNotNull(EventLogHintManager.getHints());
        Assertions.assertTrue(EventLogHintManager.getHints().contains(AsyncThread.getPrefix() + ASYNC_THREAD));
    };
}
