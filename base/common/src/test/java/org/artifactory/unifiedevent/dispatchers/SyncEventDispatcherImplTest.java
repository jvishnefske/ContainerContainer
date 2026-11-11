package org.artifactory.unifiedevent.dispatchers;

import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.dispatchers.outbound.ClientOutbound;
import org.artifactory.unifiedevent.dispatchers.outbound.HttpOutbound;
import org.artifactory.unifiedevent.dispatchers.outbound.OutboundDispatcher;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.registry.ConsumerConfig;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class SyncEventDispatcherImplTest extends ArtifactoryHomeBoundJUnitTest {
    ConsumerConfig consumerConfig;
    private final OutboundDispatcher httpOutbound = mock(HttpOutbound.class);
    private final OutboundDispatcher clientOutbound = mock(ClientOutbound.class);
    private final Map<String, OutboundDispatcher> outboundDispatchers = Map.of(
            "httpOutbound", httpOutbound,
            "clientOutbound", clientOutbound
    );
    private SyncEventDispatcherImpl syncDispatcher;

    @BeforeEach
    void setUp() {
        setLongSystemProperty(ConstantValues.syncEventDispatcherCorePoolSize, 1);
        setLongSystemProperty(ConstantValues.syncEventDispatcherMaxThreads, 1);
        setLongSystemProperty(ConstantValues.syncEventDispatcherKeepAliveTimeoutSecs, 1);
        setLongSystemProperty(ConstantValues.syncEventDispatcherQueueSize, 1);
        AccessService accessService = mock(AccessService.class);
        consumerConfig = mock(ConsumerConfig.class);
        syncDispatcher = new SyncEventDispatcherImpl(accessService, consumerConfig, outboundDispatchers);
    }

    @Test
    void testIsEnabled() {
        assertTrue(syncDispatcher.isEnabled());
    }

    @Test
    void testIsSync() {
        assertTrue(syncDispatcher.isSync());
    }

    @ParameterizedTest
    @MethodSource("handleResponseSource")
    void testHandleResponse(List<DispatcherResponse> responses, boolean expectedShouldProceed) {
        // DispatcherResponse result = syncDispatcher.handleResponse(responses);
        // assertEquals(expectedShouldProceed, result.shouldProceed());
    }

    private static Stream<Arguments> handleResponseSource() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of(List.of(), true),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build()), true),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(false).build()), false),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build(),
                        DispatcherResponse.builder().shouldProceed(true).build()), true),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build(),
                        DispatcherResponse.builder().shouldProceed(false).build()), false),
                Arguments.of(
                        new ArrayList<>(Arrays.asList(
                                null, DispatcherResponse.builder().shouldProceed(true).build())),
                        true),
                Arguments.of(
                        new ArrayList<>(Arrays.asList(
                                null, DispatcherResponse.builder().shouldProceed(false).build())),
                        false)
        );
    }

    @Test
    void testDispatchEvent() {
        SyncEventDispatcherImpl spyDispatcher = spy(syncDispatcher);
        when(consumerConfig.getConsumersForAction(any())).thenReturn(List.of(mock(Consumer.class)));
        doThrow(new UnifiedEventException("Test Exception"))
                .when(spyDispatcher).sendRequestToConsumer(any(), any());
        EventEnvelope mockPayload = EventEnvelope.builder()
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .data(ArtifactData.builder().repoKey("repoKey").path("repoPath"))
                .build();
        DispatcherResponse result = spyDispatcher.dispatchEvent(mockPayload);
        assertTrue(result.shouldProceed());
    }

    @Test
    void testDispatchEventMultiple() {
        SyncEventDispatcherImpl spyDispatcher = spy(syncDispatcher);
        when(consumerConfig.getConsumersForAction(any())).thenReturn(
                List.of(mock(Consumer.class), mock(Consumer.class)));
        doThrow(new UnifiedEventException("Test Exception"))
                .when(spyDispatcher).sendRequestToConsumer(any(), any());
        EventEnvelope mockPayload = EventEnvelope.builder()
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .data(ArtifactData.builder().repoKey("repoKey").path("repoPath"))
                .build();
        DispatcherResponse result = spyDispatcher.dispatchEvent(mockPayload);
        assertTrue(result.shouldProceed());
    }

    @ParameterizedTest
    @MethodSource("dispatchEventSource")
    void testSendRequestToConsumer(SupportedOutbound outbound, DispatcherResponse expectedResponse) {
        Consumer consumer = mock(Consumer.class);
        EventEnvelope payload = mock(EventEnvelope.class);
        when(consumer.outbound()).thenReturn(outbound);
        when(httpOutbound.dispatchEvent(consumer, payload)).thenReturn(expectedResponse);
        DispatcherResponse response = syncDispatcher.sendRequestToConsumer(consumer, payload);
        assertEquals(expectedResponse, response);
    }

    private static Stream<Arguments> dispatchEventSource() {
        return Stream.of(
                Arguments.of(SupportedOutbound.HTTP, mock(DispatcherResponse.class)),
                Arguments.of(SupportedOutbound.UNKNOWN, null)
        );
    }

}
