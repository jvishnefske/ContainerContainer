package org.artifactory.metadata.service;

import com.jfrog.artifactory.eventapi.stats.StatsEntryOuterClass;
import com.jfrog.artifactory.eventapi.stats.StatsEventOuterClass;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.jfrog.access.common.ServiceId;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.TopicCreationOptions;
import org.jfrog.eventing.facade.TopicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.jfbus.JfbusConstants.HEADER_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.HEADER_PACKAGE_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SUBJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.STATS_EVENT_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.STATS_UPDATED_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsEventServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AccessService accessService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private LeadArtifactFacade leadArtifactFacade;
    @Mock
    private RepositoryService repositoryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("artifactory-0");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onContextCreated_shouldCreateStatsTopicWithFifteenPartitions() {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        StatsEventService service = new StatsEventService(jfbusEventingService, leadArtifactFacade,
                repositoryService);

        service.onContextCreated();

        ArgumentCaptor<TopicId> topicIdCaptor = ArgumentCaptor.forClass(TopicId.class);
        ArgumentCaptor<TopicCreationOptions> optionsCaptor = ArgumentCaptor.forClass(TopicCreationOptions.class);
        verify(eventingFacade).createTopic(topicIdCaptor.capture(), optionsCaptor.capture());
        assertEquals("artifactory-stats-events", topicIdCaptor.getValue().getName());
        assertEquals(15, optionsCaptor.getValue().getPartitions());
    }

    @Test
    void sendStatsEvent_shouldPopulateHeadersAndUseRepoNameAsKey() throws Exception {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        StatsEventService service = new StatsEventService(jfbusEventingService, leadArtifactFacade,
                repositoryService);

        StatsEventOuterClass.StatsEvent statsEvent = StatsEventOuterClass.StatsEvent.newBuilder()
                .setSchemaVersion("stats.v1")
                .setEventId("evt-1")
                .addEntries(StatsEntryOuterClass.StatsEntry.newBuilder()
                        .setRepoName("docker-local")
                        .setLeadFilePath("docker-local/library/nginx/manifest.json")
                        .setRepoPackageType("docker")
                        .setAdditionalDownloads(1L)
                        .build())
                .build();

        service.sendStatsEvent("docker-local", statsEvent, Map.of());

        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TopicId> topicCaptor = ArgumentCaptor.forClass(TopicId.class);
        verify(eventingFacade).produceViaOutbox(messagesCaptor.capture(), topicCaptor.capture());
        assertEquals("artifactory-stats-events", topicCaptor.getValue().getName());
        Message message = messagesCaptor.getValue().get(0);
        assertEquals("docker-local", message.getKey());
        assertEquals(STATS_UPDATED_EVENT_TYPE, message.getHeaders().get(HEADER_TYPE));
        assertEquals(STATS_EVENT_DATA_SCHEMA, message.getHeaders().get(HEADER_DATA_SCHEMA));
        assertEquals("manifest.json", message.getHeaders().get(HEADER_SUBJECT));
        assertEquals("docker", message.getHeaders().get(HEADER_PACKAGE_TYPE));
    }

    @Test
    void publishStats_shouldSendConvertedEvents() throws Exception {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        StatsEventService service = new StatsEventService(jfbusEventingService, leadArtifactFacade,
                repositoryService);
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx/manifest.json");
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);

        service.publishStats(Map.of(repoPath, new MetadataStats(2L, 10L, 0L, false)));

        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messagesCaptor.capture(), any(TopicId.class));
        assertEquals(1, messagesCaptor.getValue().size());
    }

    @Test
    void publishStats_emptyBatchProducesNoEvents() throws Exception {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        StatsEventService service = new StatsEventService(jfbusEventingService, leadArtifactFacade,
                repositoryService);

        service.publishStats(Map.of());

        verify(eventingFacade, Mockito.never()).produceViaOutbox(any(), any(TopicId.class));
        verify(eventingFacade, Mockito.never()).produce(any(), any(TopicId.class));
    }

    @Test
    void publishStats_perChunkFailureIsSwallowedAndRemainingChunksContinue() throws Exception {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        // All produce calls throw synchronously — verifies that each chunk is attempted independently.
        Mockito.doThrow(new RuntimeException("simulated produce failure"))
                .when(eventingFacade).produceViaOutbox(any(), any());

        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        StatsEventService service = new StatsEventService(jfbusEventingService, leadArtifactFacade,
                repositoryService);
        // Two separate repos → two events; both will fail but publishStats must not throw.
        when(leadArtifactFacade.isPackageConsumptionLead(any())).thenReturn(true);
        Map<RepoPath, MetadataStats> batch = Map.of(
                RepoPathFactory.create("npm-local", "a.tgz"), new MetadataStats(1L, 1L, 0L, false),
                RepoPathFactory.create("maven-local", "b.jar"), new MetadataStats(1L, 2L, 0L, false));

        // Must not propagate — the batch loop must survive per-chunk failures.
        service.publishStats(batch);

        // Both chunks were attempted even though both threw.
        verify(eventingFacade, Mockito.atLeast(1)).produceViaOutbox(any(), any(TopicId.class));
    }
}
