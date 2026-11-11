package org.artifactory.queue.register;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueCursorService;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.artifactory.event.queue.QueueCursorType.FEDERATION_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentQueueRegistrationServiceImplTest {
    @Mock
    private QueueConfigurationRegistrar queueConfigurationRegistrar;
    @Mock
    private GlobalQueueConfigurationRegistrar globalQueueConfigurationRegistrar;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private PersistentQueueCursorService persistentQueueCursorService;
    @Mock
    private EventsService eventsService;
    @Mock
    private NodeEventCursorService nodeEventCursorService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testFilterRegistrarsOnCreation(boolean shouldBeRegistered) {
        lenient().when(queueConfigurationRegistrar.isRepoBaseRegistrar()).thenReturn(true);
        when(queueConfigurationRegistrar.shouldBeRegistered()).thenReturn(shouldBeRegistered);
        PersistentQueueRegistrationService persistentQueueRegistrationService =
                new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService);
        persistentQueueRegistrationService.init();
        verify(queueConfigurationRegistrar, times(1)).shouldBeRegistered();
        verify(queueConfigurationRegistrar, times(shouldBeRegistered ? 1 : 0)).isRepoBaseRegistrar();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRegisterRepository_adjustRemoteToCache(boolean isStoreArtifactsLocally) {
        String remoteRepo = "remote";
        String remoteRepoCache = "remote-cache";
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig remoteRepositoryRepoTypeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepositoryRepoTypeConfig.isStoreArtifactsLocally()).thenReturn(isStoreArtifactsLocally);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepositoryRepoTypeConfig);
        when(repositoryService.getRepoConfigByKey(remoteRepo)).thenReturn(remoteRepoConfig);
        lenient().when(queueConfigurationRegistrar.isRepoBaseRegistrar()).thenReturn(true);
        when(queueConfigurationRegistrar.shouldBeRegistered()).thenReturn(true);
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        lenient().when(queueConfiguration.getCursorType()).thenReturn(QueueCursorType.INCREMENTAL_INDEX);
        lenient().when(queueConfigurationRegistrar.getConfigurations(any())).thenReturn(List.of(queueConfiguration));
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of(remoteRepo));
        lenient().when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.Docker);
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        lenient().doNothing().when(persistentQueueRegistrationService)
                .createQueue(any(), anyBoolean(), any(QueueConfiguration.class));
        clearInvocations(persistentQueueRegistrationService);
        persistentQueueRegistrationService.init();
        verify(persistentQueueRegistrationService, times(isStoreArtifactsLocally ? 1 : 0)).isRegistered(
                eq(remoteRepoCache), any(), any());
        verify(persistentQueueRegistrationService, never()).isRegistered(eq(remoteRepo), any(), any());
        verify(persistentQueueRegistrationService, times(isStoreArtifactsLocally ? 1 : 0)).createQueue(
                eq(remoteRepoCache), anyBoolean(), any(QueueConfiguration.class));
        verify(persistentQueueRegistrationService, never()).createQueue(eq(remoteRepo), anyBoolean(),
                any(QueueConfiguration.class));
    }

    @ParameterizedTest
    @CsvSource({
            "false,true,true, false",
            "false,false,true, false",
            "true, true,true, false",
            "true, false,true, false",
            "false,true,false, false",
            "false,false,false, false",
            "true, true,false, false",
            "true, false,false, false",
            "true, false,false, true"
    })
    void testCreateQueue_shouldReset(boolean isNewRepository, boolean isResetOnCreation, boolean isCreated,
            boolean shouldAlwaysTrigger) {
        long cachedId = 3;
        lenient().when(persistentQueueCursorService.getCachedLatestEventId()).thenReturn(cachedId);
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        lenient().when(queueConfiguration.isResetEventMarkerOnCreation()).thenReturn(isResetOnCreation);
        when(queueConfiguration.getCursorType()).thenReturn(QueueCursorType.INCREMENTAL_INDEX);
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        try (MockedConstruction<PersistentQueueImpl> persistentQueueMockedConstruction = mockConstruction(
                PersistentQueueImpl.class,
                (mock, context) -> {
                    when(mock.createMarkerIfNotExists()).thenReturn(isCreated);
                    when(mock.shouldAlwaysTrigger()).thenReturn(shouldAlwaysTrigger);
                    when(mock.getQueueConfiguration()).thenReturn(queueConfiguration);
                })) {

            persistentQueueRegistrationService.createQueue("repo", isNewRepository, queueConfiguration);
            verify(persistentQueueMockedConstruction.constructed().get(0), times(1)).createMarkerIfNotExists();
            verify(persistentQueueMockedConstruction.constructed().get(0), times(1)).getName();
            verify(persistentQueueMockedConstruction.constructed().get(0), times(1)).getQueueConfiguration();

            if ((isNewRepository || isResetOnCreation) && isCreated) {
                verify(persistentQueueMockedConstruction.constructed().get(0), times(isNewRepository ? 1 : 0)).reset();
                verify(persistentQueueMockedConstruction.constructed().get(0), times(isNewRepository ? 0 : 1)).reset(
                        "repo", cachedId);
            }
            verify(persistentQueueMockedConstruction.constructed().get(0)).shouldAlwaysTrigger();
            if (shouldAlwaysTrigger) {
                assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
            } else {
                assertEquals(0, persistentQueueRegistrationService.getConstantQueues().size());
            }
            verifyNoMoreInteractions(persistentQueueMockedConstruction.constructed().get(0));
        }

    }

    @Test
    void testCreateGlobalQueueOnInit() {
        try {
            ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
            when(applicationContext.beanForType(EventsService.class)).thenReturn(eventsService);
            ArtifactoryContextThreadBinder.bind(applicationContext);

            QueueConfiguration queueConfiguration = mock(QueueConfiguration.class, RETURNS_DEEP_STUBS);
            when(queueConfiguration.getCursorType()).thenReturn(QueueCursorType.INCREMENTAL_INDEX);
            when(queueConfiguration.getBackoffSupplier().getFactor()).thenReturn(1);
            when(queueConfiguration.getBackoffSupplier().getMaxWaitHours()).thenReturn(1);
            when(globalQueueConfigurationRegistrar.getQueueConfiguration()).thenReturn(queueConfiguration);

            String globalQueueName = "GlobalQueue";
            when(globalQueueConfigurationRegistrar.getQueueName()).thenReturn(globalQueueName);
            when(globalQueueConfigurationRegistrar.shouldRegister()).thenReturn(true);

            PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                    spy(new PersistentQueueRegistrationServiceImpl(List.of(queueConfigurationRegistrar),
                            List.of(globalQueueConfigurationRegistrar),
                            repositoryService, persistentQueueCursorService, eventsService, nodeEventCursorService));
            doNothing().when(persistentQueueRegistrationService)
                    .createQueue(eq(globalQueueName), eq(false), any(PersistentQueue.class));

            persistentQueueRegistrationService.init();

            verify(persistentQueueRegistrationService).createQueue(eq(globalQueueName), eq(false),
                    any(PersistentQueue.class));
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testDeRegisterRepository() {
        // Create three queues = one with always trigger two without
        // Check that constant queues got 1
        // Trigger deregister of the one without always and see that constant still got 1 and queues got 2.
        // Trigger deregister on the one with always trigger.
        // Check that the constant queues set to 0.
        long cachedId = 3;
        lenient().when(persistentQueueCursorService.getCachedLatestEventId()).thenReturn(cachedId);
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo2");
        createQueue(true, persistentQueueRegistrationService, "repo3");

        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(3, persistentQueueRegistrationService.getAllQueues().size());
        persistentQueueRegistrationService.deRegisterRepository("repo");

        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(2, persistentQueueRegistrationService.getAllQueues().size());

        persistentQueueRegistrationService.deRegisterRepository("repo3");
        assertEquals(0, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(1, persistentQueueRegistrationService.getAllQueues().size());
    }

    @Test
    void testDestroy() {
        long cachedId = 3;
        lenient().when(persistentQueueCursorService.getCachedLatestEventId()).thenReturn(cachedId);
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        PersistentQueueImpl repo = createQueue(false, persistentQueueRegistrationService, "repo");
        PersistentQueueImpl repo2 = createQueue(false, persistentQueueRegistrationService, "repo2");
        PersistentQueueImpl repo3 = createQueue(true, persistentQueueRegistrationService, "repo3");

        persistentQueueRegistrationService.destroy();
        assertEquals(0, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(0, persistentQueueRegistrationService.getAllQueues().size());

        verify(repo).shutdown();
        verify(repo2).shutdown();
        verify(repo3).shutdown();
    }

    @Test
    void testCopyQueue() {
        long cachedId = 3;
        lenient().when(persistentQueueCursorService.getCachedLatestEventId()).thenReturn(cachedId);
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo2");
        createQueue(true, persistentQueueRegistrationService, "repo3");

        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(3, persistentQueueRegistrationService.getAllQueues().size());

        QueueEventImplementor queueEventImplementor = (events, key) -> {};
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        doReturn(queueConfiguration).when(persistentQueueRegistrationService)
                .copyBuildConfigurationWithVariant(eq("variant-new"), eq(queueEventImplementor), any());

        try (MockedConstruction<PersistentQueueImpl> persistentQueueMockedConstruction = mockConstruction(
                PersistentQueueImpl.class,
                (mock, context) -> {
                    when(mock.createMarkerIfNotExists()).thenReturn(true);
                    when(mock.shouldAlwaysTrigger()).thenReturn(true);
                    when(mock.getQueueConfiguration()).thenReturn(queueConfiguration);
                })) {

            persistentQueueRegistrationService.copyQueue("repo", "variant", "variant-new",
                    QueueCursorType.INCREMENTAL_INDEX, queueEventImplementor);
            verify(persistentQueueMockedConstruction.constructed().get(0)).shouldAlwaysTrigger();
        }

        assertEquals(2, persistentQueueRegistrationService.getConstantQueues().size());
        assertEquals(4, persistentQueueRegistrationService.getAllQueues().size());
    }

    @Test
    void testGetConstantQueueStateBadEnum() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> persistentQueueRegistrationService.getConstantQueueState("Not_Valid"));
        assertEquals("Provided cursor type is not valid", badRequestException.getMessage());
    }

    @Test
    void testGetConstantQueueState() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        NodeEventCursor nodeEventCursor = mock(NodeEventCursor.class);
        when(nodeEventCursor.getStatus()).thenReturn(NodeEventCursorStatus.HEALTHY);
        try (MockedConstruction<PersistentQueueImpl> persistentQueueMockedConstruction = mockConstruction(
                PersistentQueueImpl.class,
                (mock, context) -> {
                    when(mock.createMarkerIfNotExists()).thenReturn(true);
                    when(mock.shouldAlwaysTrigger()).thenReturn(true);
                    when(mock.getQueueConfiguration()).thenReturn(queueConfiguration);
                    when(mock.getName()).thenReturn("key");
                    when(mock.getCurrentMarkerCursorForQueue()).thenReturn(nodeEventCursor);
                })) {

            lenient().when(queueConfiguration.getVariant()).thenReturn("variant");
            lenient().when(queueConfiguration.isResetEventMarkerOnCreation()).thenReturn(false);
            when(queueConfiguration.getCursorType()).thenReturn(FEDERATION_SERVICE);
            persistentQueueRegistrationService.createQueue("key", true, queueConfiguration);
            assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
            NodeEventCursorStatus constantQueueState =
                    persistentQueueRegistrationService.getConstantQueueState(FEDERATION_SERVICE.name());
            assertEquals(NodeEventCursorStatus.HEALTHY, constantQueueState);
        }
    }

    @Test
    void testGetConstantQueueStateNoQueue() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        createQueue(true, persistentQueueRegistrationService, "repo");
        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        BadRequestException badRequestException = assertThrows(BadRequestException.class, () ->
                persistentQueueRegistrationService.getConstantQueueState(QueueCursorType.FEDERATED_REPLICATION.name()));
        assertEquals("No queue found for cursor type FEDERATED_REPLICATION", badRequestException.getMessage());
    }

    @Test
    void testUpdateConstantQueueNoQueue() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        createQueue(true, persistentQueueRegistrationService, "repo");
        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> persistentQueueRegistrationService.updateConstantQueue(FEDERATION_SERVICE,
                        NodeEventCursorStatus.HEALTHY, 1L));
        assertEquals("No queue found for cursor type FEDERATION_SERVICE", badRequestException.getMessage());
        verify(eventsService, never()).getFirstEventIdAfterProvidedTimestamp(anyLong(), anyString(), anyString());
    }

    @Test
    void testUpdateConstantQueueEventId() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        PersistentQueueImpl repo = createQueue(true, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        when(eventsService.getFirstEventIdAfterProvidedTimestamp(1L, "repo", FEDERATION_SERVICE.name()))
                .thenReturn(10L);
        persistentQueueRegistrationService.updateConstantQueue(FEDERATION_SERVICE,
                NodeEventCursorStatus.HEALTHY, 1L);
        verify(eventsService).getFirstEventIdAfterProvidedTimestamp(1L,  "repo", FEDERATION_SERVICE.name());
        verify(repo).updateStatus(NodeEventCursorStatus.HEALTHY);
        verify(repo).reset("repo", 10L);
    }

    @Test
    void testUpdateConstantQueueEventIdWithNoNewEvents() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        PersistentQueueImpl repo = createQueue(true, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        assertEquals(1, persistentQueueRegistrationService.getConstantQueues().size());
        when(eventsService.getFirstEventIdAfterProvidedTimestamp(1L, "repo", FEDERATION_SERVICE.name()))
                .thenReturn(-1L);
        persistentQueueRegistrationService.updateConstantQueue(FEDERATION_SERVICE,
                NodeEventCursorStatus.HEALTHY, 1L);
        verify(eventsService).getFirstEventIdAfterProvidedTimestamp(1L,  "repo", FEDERATION_SERVICE.name());
        verify(nodeEventCursorService, times(1)).updateCursorWithStatus("repo",NodeEventCursorStatus.HEALTHY);
        verify(repo, times(0)).reset("repo", -1L);
    }

    @Test
    void testGetQueuesNoQueues() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues(Set.of("test"));
        assertTrue(queues.isEmpty());
    }

    @Test
    void testGetQueues() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues(Set.of("repo"));
        assertEquals(2, queues.size());
    }

    @Test
    void testGetQueuesPerTypeNoQueues() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues("repo", FEDERATION_SERVICE);
        assertTrue(queues.isEmpty());
    }

    @Test
    void testGetQueuesPerType() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));

        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);

        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues("repo", FEDERATION_SERVICE);
        assertEquals(1, queues.size());
    }

    @Test
    void testDeRegisterNoQueue() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues(Set.of("repo"));
        assertEquals(2, queues.size());
        persistentQueueRegistrationService.deRegister("repo", FEDERATION_SERVICE, "no-variant");
        queues = persistentQueueRegistrationService.getQueues(Set.of("repo"));
        assertEquals(2, queues.size());
    }

    @Test
    void testDeRegister() {
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        createQueue(false, persistentQueueRegistrationService, "repo");
        createQueue(false, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        Collection<PersistentQueue> queues = persistentQueueRegistrationService.getQueues("repo", FEDERATION_SERVICE);
        assertEquals(1, queues.size());

        persistentQueueRegistrationService.deRegister("repo", FEDERATION_SERVICE, "variant");

        queues = persistentQueueRegistrationService.getQueues("repo", FEDERATION_SERVICE);
        assertEquals(0, queues.size());
    }

    @Test
    void testUpdateConstantQueueStatus(){
        PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService =
                spy(new PersistentQueueRegistrationServiceImpl(
                        List.of(queueConfigurationRegistrar), List.of(), repositoryService,
                        persistentQueueCursorService, eventsService, nodeEventCursorService));
        createQueue(true, persistentQueueRegistrationService, "repo", FEDERATION_SERVICE);
        persistentQueueRegistrationService.updateConstantQueueStatus(FEDERATION_SERVICE,
                NodeEventCursorStatus.DISABLED_BY_SYSTEM);
        persistentQueueRegistrationService.getConstantQueues()
                .forEach(queue -> verify(queue).updateStatus(NodeEventCursorStatus.DISABLED_BY_SYSTEM));
    }

    private static PersistentQueueImpl createQueue(boolean value,
            PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService, String key,
            QueueCursorType type) {
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        try (MockedConstruction<PersistentQueueImpl> persistentQueueMockedConstruction = mockConstruction(
                PersistentQueueImpl.class, (mock, context) -> {
                    when(mock.createMarkerIfNotExists()).thenReturn(true);
                    when(mock.shouldAlwaysTrigger()).thenReturn(value);
                    when(mock.getQueueConfiguration()).thenReturn(queueConfiguration);
                    when(mock.getName()).thenReturn(key);
                    when(mock.getRepoKey()).thenReturn(key);
                    doNothing().when(mock).reset(anyString(), anyLong());
                    doNothing().when(mock).updateStatus(any());
                })) {

            lenient().when(queueConfiguration.getVariant()).thenReturn("variant");
            lenient().when(queueConfiguration.isResetEventMarkerOnCreation()).thenReturn(false);
            when(queueConfiguration.getCursorType()).thenReturn(type);
            persistentQueueRegistrationService.createQueue(key, true, queueConfiguration);
            return persistentQueueMockedConstruction.constructed().get(0);
        }
    }

    private static PersistentQueueImpl createQueue(boolean value,
            PersistentQueueRegistrationServiceImpl persistentQueueRegistrationService, String key) {
        return createQueue(value, persistentQueueRegistrationService, key, QueueCursorType.INCREMENTAL_INDEX);
    }
}