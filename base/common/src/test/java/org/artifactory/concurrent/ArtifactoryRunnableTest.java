package org.artifactory.concurrent;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingSpan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.Set;

import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.mirror.MirrorConstants.ASYNC_THREAD;

class ArtifactoryRunnableTest {


    @Test
    void testMirrorMarkerIsSetAndRemovedOnEachRunnable() {
        Optional.ofNullable(EventLogHintManager.getHints()).ifPresent(Set::clear);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        Authentication authentication = Mockito.mock(Authentication.class);
        ArtifactoryTracer artifactoryTracer = Mockito.mock(ArtifactoryTracer.class);
        ServerTracingSpan currentSpan = Mockito.mock(ServerTracingSpan.class);
        Mockito.when(artifactoryTracer.getCurrentThreadActiveSpan()).thenReturn(currentSpan);
        Mockito.when(context.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
        ArtifactoryRunnable artifactoryRunnable = new ArtifactoryRunnable(assertMirrorMarker, context, authentication);

        Assertions.assertNull(EventLogHintManager.getHints());
        artifactoryRunnable.run();
        Assertions.assertNull(EventLogHintManager.getHints());
    }

    private final Runnable assertMirrorMarker = () -> {
        Assertions.assertNotNull(EventLogHintManager.getHints());
        Assertions.assertTrue(
                EventLogHintManager.getHints().contains(AsyncThread.getPrefix() + ASYNC_THREAD));
    };
}