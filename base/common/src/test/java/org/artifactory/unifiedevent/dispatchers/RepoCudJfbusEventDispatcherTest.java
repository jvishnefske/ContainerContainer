package org.artifactory.unifiedevent.dispatchers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.config.JfbusTopicConfig;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.serializer.SerializationService;
import org.jfrog.eventing.facade.TopicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoCudJfbusEventDispatcherTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SerializationService serializationService;

    @Mock
    private JfbusEventingService jfbusEventingService;

    @Mock
    private ArtifactoryContext context;

    private AutoCloseable mocks;

    private RepoCudJfbusEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        dispatcher = new RepoCudJfbusEventDispatcher(
                new JfbusAsyncEventSupport(serializationService, jfbusEventingService));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.asyncEventDispatcherEnabled, String.valueOf(true));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "true");
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void isEnabled_requiresBothAsyncDispatcherAndRepoCudJfbusFlags() {
        assertTrue(dispatcher.isEnabled());

        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "false");
        assertFalse(dispatcher.isEnabled());
    }

    @Test
    void isSync_returnsFalse() {
        assertFalse(dispatcher.isSync());
    }

    @Test
    void getTopicId_returnsRepositoryTopicForAfterRepoEvents() {
        TopicId expectedTopicId = new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository");

        assertEquals(expectedTopicId, JfbusTopicConfig.getTopicId(SupportedUnifiedEvents.AFTER_REPO_CREATE));
        assertEquals(expectedTopicId, JfbusTopicConfig.getTopicId(SupportedUnifiedEvents.AFTER_REPO_DELETE));
        assertEquals(expectedTopicId, JfbusTopicConfig.getTopicId(SupportedUnifiedEvents.AFTER_REPO_UPDATE));
    }

    @Test
    void afterRepoTopics_useDesignedPartitionCount() {
        assertEquals(3, JfbusTopicConfig.AFTER_REPO_CREATE.getPartitionCount());
        assertEquals(3, JfbusTopicConfig.AFTER_REPO_DELETE.getPartitionCount());
        assertEquals(3, JfbusTopicConfig.AFTER_REPO_UPDATE.getPartitionCount());
    }

    @Test
    void onContextCreated_createsRepositoryTopicOnce_withConfiguredPartitions() {
        dispatcher.onContextCreated();

        verify(jfbusEventingService).createTopic(
                eq(new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository")), eq(3), eq(true));
        verify(jfbusEventingService, times(1)).createTopic(any(TopicId.class), any(Integer.class), eq(true));
    }

    @Test
    void dispatchEvent_whenAfterRepoCreate_usesRepositoryTopic() throws Exception {
        EventEnvelope<Object> envelope = afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repoKey");
        byte[] payload = new byte[]{1};
        when(serializationService.toBytes(envelope)).thenReturn(payload);

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(jfbusEventingService).sendEvent(eq(new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository")),
                eq("repoKey"), eq(payload), any());
    }

    @Test
    void dispatchEvent_createsRepositoryTopicWithConfiguredPartitions_beforeSending() throws Exception {
        EventEnvelope<Object> envelope = afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repoKey");
        byte[] payload = new byte[]{7};
        when(serializationService.toBytes(envelope)).thenReturn(payload);
        TopicId mbRepo = new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository");

        dispatcher.dispatchEvent(envelope);

        InOrder inOrder = inOrder(jfbusEventingService);
        inOrder.verify(jfbusEventingService).createTopic(eq(mbRepo), eq(3), eq(true));
        inOrder.verify(jfbusEventingService).sendEvent(eq(mbRepo), eq("repoKey"), eq(payload), any());
    }

    @Test
    void dispatchEvent_provisionsTopicOncePerTopic_acrossMultipleEvents() throws Exception {
        when(serializationService.toBytes(any())).thenReturn(new byte[]{1});
        TopicId mbRepo = new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository");

        dispatcher.dispatchEvent(afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repo-a"));
        dispatcher.dispatchEvent(afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_UPDATE, "repo-b"));
        dispatcher.dispatchEvent(afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_DELETE, "repo-c"));

        verify(jfbusEventingService, times(1)).createTopic(eq(mbRepo), eq(3), eq(true));
        verify(jfbusEventingService, times(3)).sendEvent(eq(mbRepo), any(), any(), any());
    }

    @Test
    void dispatchEvent_whenTopicCreationFails_skipsDispatchAndRetriesOnNextCall() throws Exception {
        TopicId mbRepo = new TopicId(ARTIFACTORY_SERVICE_NAME, "mb_repository");
        when(serializationService.toBytes(any())).thenReturn(new byte[]{1});

        doThrow(new RuntimeException("broker unreachable"))
                .when(jfbusEventingService).createTopic(eq(mbRepo), eq(3), eq(true));

        dispatcher.dispatchEvent(afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repo-a"));

        verify(jfbusEventingService, times(1)).createTopic(eq(mbRepo), eq(3), eq(true));
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());

        doNothing().when(jfbusEventingService).createTopic(eq(mbRepo), eq(3), eq(true));

        dispatcher.dispatchEvent(afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repo-b"));

        verify(jfbusEventingService, times(2)).createTopic(eq(mbRepo), eq(3), eq(true));
        verify(jfbusEventingService, times(1)).sendEvent(eq(mbRepo), any(), any(), any());
    }

    @Test
    void dispatchEvent_noopsForNonRepoEvents() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .subject("repo/path")
                .build();

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService, never()).toBytes(any());
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void dispatchEvent_whenRepoPayloadExceedsHardLimit_skipsPublishAndLogsStructuredOutcome() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsMaxPayloadBytes, "10");
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(RepoCudJfbusEventDispatcher.class);

        EventEnvelope<Object> envelope = afterRepoEnvelope(SupportedUnifiedEvents.AFTER_REPO_CREATE, "repoKey");
        byte[] oversize = new byte[64];
        when(serializationService.toBytes(envelope)).thenReturn(oversize);

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());

        ILoggingEvent outcomeLog = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().toLowerCase().contains("oversize")
                        || event.getFormattedMessage().toLowerCase().contains("skipped"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected a structured WARN log for the skipped oversize event; got: "
                                + logAppender.list));
        String formatted = outcomeLog.getFormattedMessage();
        assertTrue(formatted.contains("AFTER_REPO_CREATE"));
        assertTrue(formatted.contains("evt-repoKey"));
        assertTrue(formatted.contains("repoKey"));
        assertTrue(formatted.contains("64") && formatted.contains("10"));
    }

    private static EventEnvelope<Object> afterRepoEnvelope(SupportedUnifiedEvents type, String subject) {
        return EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-" + subject)
                .type(type)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject(subject)
                .dataSchema("schema")
                .dataContentType("application/json")
                .eventHeaders(null)
                .data(null)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ListAppender<ILoggingEvent> attachLogAppender(Class<?> loggerOwner) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();
        loggerContext.getLogger(loggerOwner).addAppender(appender);
        return appender;
    }
}
