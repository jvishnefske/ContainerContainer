package org.artifactory.unifiedevent.dispatchers;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.config.JfbusTopicConfig;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.serializer.SerializationService;
import org.jfrog.eventing.facade.TopicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.artifactory.jfbus.JfbusConstants.HEADER_CONTENT_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.HEADER_ID;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SOURCE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SPEC_VERSION;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SUBJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TIME;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Open mocks in {@code @BeforeEach} (not MockitoExtension) so {@link ArtifactoryHomeBoundJUnitTest} binds home
 * first; {@link JfbusEventingService} static init calls {@code ConstantValues.jfbusUseOutboxEnabled.getBoolean()}.
 */
class UemV1EventDispatcherTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SerializationService serializationService;

    @Mock
    private JfbusEventingService jfbusEventingService;

    @Mock
    private ArtifactoryContext context;

    private AutoCloseable mocks;

    private UemV1EventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        dispatcher = new UemV1EventDispatcher(
                new JfbusAsyncEventSupport(serializationService, jfbusEventingService));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.asyncEventDispatcherEnabled, String.valueOf(true));
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void isEnabled_returnsTrueWhenJfbusOperationModeIsActive() {
        assertTrue(dispatcher.isEnabled());
    }

    @Test
    void isSync_returnsFalse() {
        assertFalse(dispatcher.isSync());
    }

    @Test
    void onContextCreated_createsEachGenericTopicOnce_withConfiguredPartitions() {
        dispatcher.onContextCreated();

        verify(jfbusEventingService).createTopic(
                eq(JfbusTopicConfig.getTopicId(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)), eq(10), eq(true));
        verify(jfbusEventingService, times(1)).createTopic(any(TopicId.class), any(Integer.class), eq(true));
    }

    @Test
    void onContextCreated_whenCreateTopicThrows_doesNotPropagateAndDoesNotCacheFailure() {
        doThrow(new RuntimeException("Topic creation failed"))
                .when(jfbusEventingService).createTopic(any(TopicId.class), any(Integer.class), eq(true));

        dispatcher.onContextCreated();

        verify(jfbusEventingService, times(JfbusTopicConfig.genericAsyncTopicConfigs().size())).createTopic(
                any(TopicId.class), any(Integer.class), eq(true));
    }

    @Test
    void dispatchEvent_whenTopicExists_serializesAndSendsEvent_returnsShouldProceedTrue() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-123")
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("afterRemoteDownload")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(null)
                .build();

        byte[] payload = new byte[]{1, 2, 3};
        when(serializationService.toBytes(envelope)).thenReturn(payload);

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService).toBytes(envelope);
        ArgumentCaptor<TopicId> topicIdCaptor = ArgumentCaptor.forClass(TopicId.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jfbusEventingService).sendEvent(topicIdCaptor.capture(), keyCaptor.capture(),
                payloadCaptor.capture(), headersCaptor.capture());

        assertEquals(JfbusTopicConfig.getTopicId(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD),
                topicIdCaptor.getValue());
        assertEquals("repo/path", keyCaptor.getValue());
        assertEquals(payload, payloadCaptor.getValue());

        Map<String, String> headers = headersCaptor.getValue();
        assertEquals("1.0", headers.get(HEADER_SPEC_VERSION));
        assertEquals("evt-123", headers.get(HEADER_ID));
        assertEquals(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD.getType(), headers.get(HEADER_TYPE));
        assertEquals("artifactory://svc@node1", headers.get(HEADER_SOURCE));
        assertEquals("2025-01-01T12:00:00Z", headers.get(HEADER_TIME));
        assertEquals("repo/path", headers.get(HEADER_SUBJECT));
        assertEquals("application/json", headers.get(HEADER_CONTENT_TYPE));
        assertEquals("afterRemoteDownload", headers.get(HEADER_DATA_SCHEMA));
    }

    @Test
    void dispatchEvent_whenAfterRepoCreate_isHandledByRepoCudDispatcher_notGenericAsyncDispatcher() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-repo")
                .type(SupportedUnifiedEvents.AFTER_REPO_CREATE)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repoKey")
                .dataSchema("afterRepoCreate")
                .dataContentType("application/json")
                .eventHeaders(null)
                .data(null)
                .build();

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService, never()).toBytes(any());
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void dispatchEvent_whenNoTopicConfiguredForEventType_returnsWithoutSending() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-456")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .eventHeaders(null)
                .data(null)
                .build();

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService, never()).toBytes(any());
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());
    }

    @Test
    void dispatchEvent_whenSendEventThrows_stillReturnsShouldProceedTrue() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-789")
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("afterRemoteDownload")
                .dataContentType("application/json")
                .eventHeaders(null)
                .data(null)
                .build();

        when(serializationService.toBytes(envelope)).thenReturn(new byte[0]);
        doThrow(new RuntimeException("send failed")).when(jfbusEventingService).sendEvent(any(), any(), any(), any());

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService).toBytes(envelope);
        verify(jfbusEventingService).sendEvent(any(), any(), any(), any());
    }

    @Test
    void dispatchEvent_whenToBytesThrows_stillReturnsShouldProceedTrue() throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-999")
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("afterRemoteDownload")
                .dataContentType("application/json")
                .eventHeaders(null)
                .data(null)
                .build();

        when(serializationService.toBytes(envelope)).thenThrow(new RuntimeException("serialize failed"));

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(serializationService).toBytes(envelope);
        verify(jfbusEventingService, never()).sendEvent(any(), any(), any(), any());
    }
}
