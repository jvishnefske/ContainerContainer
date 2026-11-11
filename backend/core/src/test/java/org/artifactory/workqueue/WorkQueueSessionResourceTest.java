package org.artifactory.workqueue;

import org.artifactory.workqueue.WorkQueueSessionService.WorkQueueSessionCompletionCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkQueueSessionResourceTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void afterConnectionReleased(boolean committed) {
        WorkQueueSessionCompletionCallback callback1 = mock(WorkQueueSessionCompletionCallback.class);
        WorkQueueSessionCompletionCallback callback2 = mock(WorkQueueSessionCompletionCallback.class);

        WorkQueueSessionResource resource = new WorkQueueSessionResource();
        resource.addCallback(callback1);
        resource.addCallback(callback2);
        resource.afterConnectionReleased(committed);

        if (committed) {
            verify(callback1).afterCompletion();
            verify(callback2).afterCompletion();
        } else {
            verify(callback1, never()).afterCompletion();
            verify(callback2, never()).afterCompletion();
        }
    }

    @Test
    void afterConnectionReleased_noCallbacks() {
        WorkQueueSessionResource resource = new WorkQueueSessionResource();
        resource.afterConnectionReleased(true);
    }
}
