package org.artifactory.metadata.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.artifactory.packageindexed.CreatePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.FileInfoOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass.PackageIndexEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.projects.ProjectsServiceImpl;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.project.ProjectResourceEntity;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagesEventServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AccessService accessService;

    private AutoCloseable mocks;

    @Mock
    private ArtifactoryContext context;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("artifactory-0");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        ArtifactoryContextThreadBinder.bind(context);
        getBound().setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), "ACTIVE");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testShouldCreateTopicWithCorrectParameters() {
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        service.onContextCreated();

        ArgumentCaptor<TopicId> topicIdCaptor = ArgumentCaptor.forClass(TopicId.class);
        ArgumentCaptor<TopicCreationOptions> optionsCaptor = ArgumentCaptor.forClass(TopicCreationOptions.class);
        verify(eventingFacade).createTopic(topicIdCaptor.capture(), optionsCaptor.capture());

        TopicId capturedTopicId = topicIdCaptor.getValue();
        assertEquals("artifactory-package-indexed-events", capturedTopicId.getName());

        TopicCreationOptions capturedOptions = optionsCaptor.getValue();
        assertEquals(15, capturedOptions.getPartitions());

        // Arrange - exception path: configure mock to throw exception on second call
        RuntimeException testException = new RuntimeException("Topic creation failed");
        when(eventingFacade.createTopic(any(TopicId.class), any(TopicCreationOptions.class)))
                .thenThrow(testException);

        // Act - exception path: init should catch exception and not propagate it
        PackagesEventService serviceForExceptionTest = new PackagesEventService(
                new JfbusEventingService(Optional.of(eventingFacade)), projectsService);
        serviceForExceptionTest.onContextCreated(); // Should not throw - exception is caught and logged
    }

    @Test
    void sendPackageIndexEvent_shouldNotEnrich_whenRepoNameIsBlank() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert - projectsService should not be called when repoName is blank
        Mockito.verify(projectsService, Mockito.never()).getProjectKeyOfRepository(any());
    }

    @Test
    void sendPackageIndexEvent_shouldSetProjectAndHeader_whenProjectExists() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        when(projectsService.getProjectKeyOfRepository("docker-local")).thenReturn("my-project");
        when(projectsService.getProjectRepositoryEntity("my-project", "docker-local")).thenReturn(null);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert
        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), any(TopicId.class));

        Map<String, String> headers = messageCaptor.getValue().getFirst().getHeaders();
        assertEquals("my-project", headers.get("project"));
    }

    @Test
    void sendPackageIndexEvent_shouldNotSetProjectHeader_whenProjectIsNull() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        when(projectsService.getProjectKeyOfRepository("docker-local")).thenReturn(null);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert
        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), any(TopicId.class));

        Map<String, String> headers = messageCaptor.getValue().getFirst().getHeaders();
        assertFalse(headers.containsKey("project"));
    }

    @Test
    void sendPackageIndexEvent_shouldAddStages_whenResourceEntityHasEnvironments() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        ProjectResourceEntity resourceEntity = Mockito.mock(ProjectResourceEntity.class);
        when(resourceEntity.getEnvironments()).thenReturn(Set.of("DEV", "PROD"));

        when(projectsService.getProjectKeyOfRepository(any())).thenReturn("my-project");
        when(projectsService.getProjectRepositoryEntity(any(), any())).thenReturn(resourceEntity);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert
        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), any(TopicId.class));

        // Verify project header is set
        Map<String, String> headers = messageCaptor.getValue().getFirst().getHeaders();
        assertEquals("my-project", headers.get("project"));

        // Parse the event from the message to verify stages were added
        byte[] payload = messageCaptor.getValue().getFirst().getPayload();
        PackageIndexEvent sentEvent = PackageIndexEvent.parseFrom(payload);
        assertEquals(2, sentEvent.getStageCount());
        assertTrue(sentEvent.getStageList().containsAll(Set.of("DEV", "PROD")));
    }

    @Test
    void sendPackageIndexEvent_shouldNotAddStages_whenResourceEntityIsNull() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        when(projectsService.getProjectKeyOfRepository("docker-local")).thenReturn("my-project");
        when(projectsService.getProjectRepositoryEntity("my-project", "docker-local")).thenReturn(null);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert
        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), any(TopicId.class));

        byte[] payload = messageCaptor.getValue().getFirst().getPayload();
        PackageIndexEvent sentEvent = PackageIndexEvent.parseFrom(payload);
        assertTrue(sentEvent.getStageList().isEmpty());
    }

    @Test
    void sendPackageIndexEvent_shouldNotAddStages_whenEnvironmentsIsNull() throws Exception {
        // Arrange
        EventingFacade eventingFacade = Mockito.mock(EventingFacade.class);
        ProjectsServiceImpl projectsService = Mockito.mock(ProjectsServiceImpl.class);
        JfbusEventingService jfbusEventingService = new JfbusEventingService(Optional.of(eventingFacade));
        PackagesEventService service = new PackagesEventService(jfbusEventingService, projectsService);

        ProjectResourceEntity resourceEntity = Mockito.mock(ProjectResourceEntity.class);
        when(resourceEntity.getEnvironments()).thenReturn(null);

        when(projectsService.getProjectKeyOfRepository("docker-local")).thenReturn("my-project");
        when(projectsService.getProjectRepositoryEntity("my-project", "docker-local")).thenReturn(resourceEntity);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .addFiles(createDummyFileInfo())
                                .build())
                .build();

        // Act
        service.sendPackageIndexEvent("key", event, Map.of());

        // Assert
        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), any(TopicId.class));

        byte[] payload = messageCaptor.getValue().getFirst().getPayload();
        PackageIndexEvent sentEvent = PackageIndexEvent.parseFrom(payload);
        assertTrue(sentEvent.getStageList().isEmpty());
    }

    private static FileInfoOuterClass.FileInfo createDummyFileInfo() {
        return FileInfoOuterClass.FileInfo.newBuilder()
                .setLead(true)
                .setLength(1234)
                .setSha256("dummy-sha256")
                .setName("test-file.tgz")
                .build();
    }

    @Test
    void printEventForTracing_shouldLogJson_whenTraceEnabled() {
        // Arrange
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                PackagesEventService.class, Level.TRACE);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .setRepoPackageType("docker")
                .setCreatePackageIndexEvent(
                        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder()
                                .setName("test-package")
                                .build())
                .build();

        // Act
        PackagesEventService.printEventForTracing(event);

        // Assert
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        assertEquals(1, loggedEvents.size());
        assertEquals(Level.TRACE, loggedEvents.getFirst().getLevel());
        assertTrue(loggedEvents.getFirst().getFormattedMessage().contains("test-package"));
        assertTrue(loggedEvents.getFirst().getFormattedMessage().contains("docker-local"));
    }

    @Test
    void printEventForTracing_shouldNotLog_whenTraceDisabled() {
        // Arrange - INFO level means TRACE is disabled
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                PackagesEventService.class, Level.INFO);

        PackageIndexEvent event = PackageIndexEvent.newBuilder()
                .setRepoName("docker-local")
                .build();

        // Act
        PackagesEventService.printEventForTracing(event);

        // Assert
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        assertTrue(loggedEvents.isEmpty());
    }

    @Test
    void printEventForTracing_shouldHandleNullGracefully_whenTraceEnabled() {
        // Arrange
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                PackagesEventService.class, Level.TRACE);

        // Act - pass null event
        PackagesEventService.printEventForTracing(null);

        // Assert - should log something (either error or the null handling)
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        // The method catches exceptions, so it should not throw
        // It may log a warning/debug message for the exception
        assertFalse(loggedEvents.isEmpty());
    }
}
