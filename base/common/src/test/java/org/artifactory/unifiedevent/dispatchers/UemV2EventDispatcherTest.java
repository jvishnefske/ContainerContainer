package org.artifactory.unifiedevent.dispatchers;

import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.UemV2AsyncPublisher;
import org.artifactory.jfbus.UemV2PublishRequest;
import org.artifactory.jfbus.UemV2Publisher;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Open mocks in {@code @BeforeEach} (not MockitoExtension) so {@link ArtifactoryHomeBoundJUnitTest} binds home
 * first; {@link UemV2EventDispatcher#isEnabled()} reads {@code ConstantValues.uemV2PublisherEnabled}.
 */
class UemV2EventDispatcherTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private UemV2Publisher uemV2Publisher;

    @Mock
    private UemV2AsyncPublisher uemV2AsyncPublisher;

    private AutoCloseable mocks;

    private UemV2EventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        dispatcher = new UemV2EventDispatcher(uemV2Publisher, uemV2AsyncPublisher);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void isSync_returnsFalse() {
        assertFalse(dispatcher.isSync());
    }

    @Test
    void isEnabled_isFalseByDefault() {
        assertFalse(dispatcher.isEnabled());
    }

    @Test
    void isEnabled_reflectsFlagWhenEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.uemV2PublisherEnabled, String.valueOf(true));
        assertTrue(dispatcher.isEnabled());
    }

    @Test
    void dispatchEvent_whenEntityTypeConfigured_publishesMappedRequest_returnsShouldProceedTrue() {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-123")
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("afterRemoteDownload")
                .dataContentType("application/json")
                .data("payload")
                .build();

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        ArgumentCaptor<UemV2PublishRequest> requestCaptor = ArgumentCaptor.forClass(UemV2PublishRequest.class);
        verify(uemV2AsyncPublisher).publish(requestCaptor.capture());
        UemV2PublishRequest request = requestCaptor.getValue();
        assertEquals("artifact", request.entityType());
        assertEquals(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD.getType(), request.type());
        assertEquals("repo/path", request.subject());
        assertEquals("artifactory://svc@node1", request.source());
        assertEquals("afterRemoteDownload", request.dataSchema());
        assertEquals("payload", request.data());
        assertEquals("evt-123", request.id());
    }

    @Test
    void onContextCreated_createsTopicForEachConfiguredEntityType() {
        dispatcher.onContextCreated();

        // AFTER_REMOTE_DOWNLOAD -> entityType "artifact" with 10 partitions (JfbusTopicConfig).
        verify(uemV2Publisher).createTopic(eq("artifact"), eq(10));
    }

    @Test
    void onContextCreated_whenCreateTopicThrows_doesNotPropagate() {
        doThrow(new RuntimeException("broker down")).when(uemV2Publisher).createTopic(anyString(), anyInt());

        assertDoesNotThrow(() -> dispatcher.onContextCreated());
    }

    @Test
    void dispatchEvent_whenNoEntityTypeConfigured_doesNotPublish_returnsShouldProceedTrue() {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-456")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .data("payload")
                .build();

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(uemV2AsyncPublisher, never()).publish(any());
    }

    @Test
    void dispatchEvent_whenPublishThrows_stillReturnsShouldProceedTrue() {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-789")
                .type(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD)
                .source("artifactory://svc@node1")
                .time("2025-01-01T12:00:00Z")
                .subject("repo/path")
                .dataSchema("afterRemoteDownload")
                .dataContentType("application/json")
                .data("payload")
                .build();

        doThrow(new RuntimeException("publish failed")).when(uemV2AsyncPublisher).publish(any());

        DispatcherResponse response = dispatcher.dispatchEvent(envelope);

        assertTrue(response.shouldProceed());
        verify(uemV2AsyncPublisher).publish(any());
    }
}
