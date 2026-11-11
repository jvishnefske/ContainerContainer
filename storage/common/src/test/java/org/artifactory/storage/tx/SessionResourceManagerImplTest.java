package org.artifactory.storage.tx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionResourceManagerImplTest {

    @Test
    void getOrCreateResource_createsAndReusesResource() {
        SessionResourceManagerImpl manager = new SessionResourceManagerImpl();
        TestSessionResource resource1 = manager.getOrCreateResource(TestSessionResource.class);
        TestSessionResource resource2 = manager.getOrCreateResource(TestSessionResource.class);
        assertSame(resource1, resource2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void afterConnectionReleased_callsAllResources(boolean committed) {
        SessionResourceManagerImpl manager = new SessionResourceManagerImpl();
        TestSessionResource resource = manager.getOrCreateResource(TestSessionResource.class);
        manager.afterConnectionReleased(committed);
        assertSame(committed, resource.wasCommitted);
        assertSame(true, resource.afterConnectionReleasedCalled);
    }

    @Test
    void afterConnectionReleased_propagatesException() {
        SessionResourceManagerImpl manager = new SessionResourceManagerImpl();
        manager.getOrCreateResource(FailingSessionResource.class);
        assertThrows(RuntimeException.class, () -> manager.afterConnectionReleased(true));
    }

    @Test
    void afterConnectionReleased_callsAllResourcesEvenOnException() {
        SessionResourceManagerImpl manager = new SessionResourceManagerImpl();
        manager.getOrCreateResource(FailingSessionResource.class);
        TestSessionResource resource = manager.getOrCreateResource(TestSessionResource.class);
        assertThrows(RuntimeException.class, () -> manager.afterConnectionReleased(true));
        assertSame(true, resource.afterConnectionReleasedCalled);
    }

    public static class TestSessionResource extends SessionResourceAdapter {
        boolean afterConnectionReleasedCalled = false;
        boolean wasCommitted = false;

        @Override
        public void afterConnectionReleased(boolean committed) {
            afterConnectionReleasedCalled = true;
            wasCommitted = committed;
        }
    }

    public static class FailingSessionResource extends SessionResourceAdapter {
        @Override
        public void afterConnectionReleased(boolean committed) {
            throw new RuntimeException("Test exception");
        }
    }
}
