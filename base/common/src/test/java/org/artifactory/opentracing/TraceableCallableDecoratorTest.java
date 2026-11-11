package org.artifactory.opentracing;

import org.artifactory.api.events.EventLogHintManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.artifactory.event.EventHintType.PackageIndexExtraInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceableCallableDecoratorTest {

    @Test
    void eventLogHintsAreClearedAfterCallableRuns() throws Exception {
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        ServerTracingSpan currentSpan = mock(ServerTracingSpan.class);
        when(artifactoryTracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);

        Callable<String> callable = () -> {
            // Simulate work that leaves a hint on the (pooled) thread without cleaning up after itself.
            EventLogHintManager.put(PackageIndexExtraInfo, "leaked");
            Assertions.assertNotNull(EventLogHintManager.getHints());
            return "result";
        };
        TraceableCallableDecorator<String> decorator =
                new TraceableCallableDecorator<>(callable, artifactoryTracer,
                        artifactoryTracer.getCurrentThreadActiveSpan());

        Assertions.assertNull(EventLogHintManager.getHints());
        String result = decorator.call();
        assertEquals("result", result, "callable result is propagated");
        Assertions.assertNull(EventLogHintManager.getHints(), "hints cleared in finally after callable runs");
    }

    @Test
    void hintsAreClearedEvenWhenCallableThrows() {
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        ServerTracingSpan currentSpan = mock(ServerTracingSpan.class);
        when(artifactoryTracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);

        AtomicBoolean ran = new AtomicBoolean();
        TraceableCallableDecorator<String> decorator =
                new TraceableCallableDecorator<>(() -> {
                    EventLogHintManager.put(PackageIndexExtraInfo, "leaked");
                    ran.set(true);
                    throw new IllegalStateException("boom");
                }, artifactoryTracer, artifactoryTracer.getCurrentThreadActiveSpan());

        Assertions.assertThrows(IllegalStateException.class, decorator::call);
        assertTrue(ran.get(), "callable did run");
        Assertions.assertNull(EventLogHintManager.getHints(), "hints cleared even on exception");
    }
}
