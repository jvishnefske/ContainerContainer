package org.artifactory.queue;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.services.PersistentQueueCursorService;
import org.artifactory.queue.services.PersistentQueueCursorServiceImpl;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class PersistentQueueBaseTest {

    @Mock
    ConflictGuard conflictGuard;
    @Mock
    EventsService eventsService;
    @Mock
    ArtifactoryContext artifactoryContext;
    @Mock
    NodeEventCursorService nodeEventCursorService;
    @Mock
    PersistentQueueCursorService queueCursorService;
    @Mock
    PersistentQueueErrorService queueErrorService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    QueueTriggeringService queueTriggeringService;
    @Spy
    NodeEventsErrorsStorageService errorsStorageService;
    ArtifactorySystemProperties mockProperties;
    private AutoCloseable mocks;

    @BeforeMethod
    public void prepareMocks() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(EventsService.class)).thenReturn(eventsService);
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);
        when(artifactoryContext.beanForType(PersistentQueueErrorService.class)).thenReturn(queueErrorService);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(mock(ArtifactoryTracer.class));
        when(artifactoryContext.beanForType(QueueTriggeringService.class)).thenReturn(queueTriggeringService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        final ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        mockProperties = new ArtifactorySystemProperties();
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(mockProperties);
        ArtifactoryHome.bind(artifactoryHome);
        HaAddon haAddon = mock(HaAddon.class);
        ConflictsGuard conflictsGuard = mock(ConflictsGuard.class);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(haAddon.getConflictsGuard(eq(PersistentQueueCursorServiceImpl.CREATE_CURSOR_CONFLICTS_GUARD)))
                .thenReturn(conflictsGuard);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    protected QueueConfiguration createIncrementalIndexQueue() {
        return getIncrementalIndexBuilder().build();
    }

    protected QueueConfiguration createIncrementalIndexQueue(QueueEventImplementor implementor, int maxRetries) {
        return getIncrementalIndexBuilder()
                .implementor(implementor)
                .maxErrorRetires(maxRetries)
                .build();
    }

    protected QueueConfiguration.QueueConfigurationBuilder getIncrementalIndexBuilder() {
        return QueueConfiguration.builder()
                .name("test")
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.INCREMENTAL_INDEX);
    }

    protected QueueConfiguration createFederatedReplicationQueue() {
        return getFederatedReplicationBuilder()
                .name("test")
                .implementor((events, key) -> {})
                .maxErrorRetires(1)
                .build();
    }

    protected QueueConfiguration createFederatedReplicationQueue(QueueEventImplementor implementor) {
        return getFederatedReplicationBuilder()
                .name("test")
                .implementor(implementor)
                .maxErrorRetires(1)
                .build();
    }

    protected QueueConfiguration.QueueConfigurationBuilder getFederatedReplicationBuilder() {
        return QueueConfiguration.builder()
                .name("test")
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.FEDERATED_REPLICATION);
    }
}
