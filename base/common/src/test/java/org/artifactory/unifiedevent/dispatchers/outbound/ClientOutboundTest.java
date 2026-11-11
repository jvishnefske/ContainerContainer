package org.artifactory.unifiedevent.dispatchers.outbound;

import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.dispatchers.outbound.consumerclient.ClientOutboundDispatcher;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientOutboundTest {

    private final ClientOutboundDispatcher testDispatcher = mock(ClientOutboundDispatcher.class);
    private final Map<String, ClientOutboundDispatcher> dispatcherMap = Map.of(
            "workerClient", testDispatcher
    );
    private final ClientOutbound clientOutbound = new ClientOutbound(dispatcherMap);

    @ParameterizedTest
    @MethodSource("dispatchEventSource")
    void testDispatchEvent(SupportedConsumers consumerName, DispatcherResponse response, int timesInvoke) {
        Consumer consumer = mock(Consumer.class);
        when(consumer.name()).thenReturn(consumerName);
        EventEnvelope payload = mock(EventEnvelope.class);
        when(testDispatcher.sendEvent(payload)).thenReturn(response);
        DispatcherResponse result = clientOutbound.dispatchEvent(consumer, payload);
        assertEquals(response, result);
        verify(testDispatcher, times(timesInvoke)).sendEvent(payload);
    }

    private static Stream<Arguments> dispatchEventSource() {
        return Stream.of(
                Arguments.of(SupportedConsumers.WORKER_CLIENT, mock(DispatcherResponse.class), 1),
                Arguments.of(SupportedConsumers.UNKNOWN, null, 0)
        );
    }

}
