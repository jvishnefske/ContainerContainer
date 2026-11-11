package org.artifactory.sh.cleanup.sync;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.events.BinariesSyncEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinariesSyncEventListenerTest {
    private static AutoCloseable autoCloseable;

    private BinariesSyncEventListener eventListener;
    @Mock
    RepositoryService repositoryService;
    @Mock
    StorageEventsService storageEventsService;

    @BeforeEach
    public void init() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        eventListener = new BinariesSyncEventListener(repositoryService, storageEventsService);
    }

    @Test
    void testOnApplicationEvent() {
        BinaryElement element = mock(BinaryElement.class);
        when(element.getSha1()).thenReturn("sha1");
        when(element.getSha2()).thenReturn("sha2");
        when(element.getLength()).thenReturn(1L);
        BinariesSyncEvent event = new BinariesSyncEvent(this, List.of(element));
        when(repositoryService.existsBySha1("sha1")).thenReturn(false);
        eventListener.onApplicationEvent(event);
        verify(storageEventsService, times(1)).appendEvents(any());
    }

    @AfterEach
    void teardown() throws Exception {
        autoCloseable.close();
    }
}
