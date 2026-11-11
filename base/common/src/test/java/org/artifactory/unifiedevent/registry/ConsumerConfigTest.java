package org.artifactory.unifiedevent.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import org.artifactory.security.access.AccessService;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerConfigTest {
    private static final String CONSUMER_NAME_PATTERN_PREFIX = "unified.events.draft.";
    private AccessService accessService;
    private ConsumerConfig consumerConfig;

    @BeforeEach
    void setUp() {
        accessService = mock(AccessService.class, RETURNS_DEEP_STUBS);
        consumerConfig = new ConsumerConfig(accessService);
    }

    @Test
    void testGetAndValidateActionKey() {
        SupportedUnifiedEvents action = mock(SupportedUnifiedEvents.class);
        when(action.getName()).thenReturn("testAction");
        String key = consumerConfig.getAndValidateActionKey(action);
        assertEquals(CONSUMER_NAME_PATTERN_PREFIX + "testAction", key);
    }

    @ParameterizedTest
    @MethodSource("getAndValidateActionKeySource")
    void testSetConsumerForAction_AddsConsumer(int timesInvoke) {
        SupportedUnifiedEvents action = mock(SupportedUnifiedEvents.class);
        when(action.getName()).thenReturn("action1");
        String actionKey = consumerConfig.getAndValidateActionKey(action);
        Consumer consumer = Consumer.builder().name(SupportedConsumers.WORKER).endpoint("url").outbound(
                SupportedOutbound.CLIENT).build();
        List<Consumer> consumers = new ArrayList<>();
        if (timesInvoke == 0) {
            consumers.add(consumer);
        }
        when(accessService.getPlatformConfigClient().getConfig(anyString(), any(TypeReference.class)))
                .thenReturn(consumers);
        consumerConfig.setConsumerForAction(action, consumer, false);
        if (timesInvoke != 0) {
            consumers.add(consumer);
        }
        verify(accessService.getPlatformConfigClient(), times(timesInvoke))
                .setConfig(actionKey, consumers, false);
        assertTrue(consumers.contains(consumer));
    }

    private static Stream<Arguments> getAndValidateActionKeySource() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(0)
        );
    }

    @Test
    void testGetConsumersForAction() {
        SupportedUnifiedEvents action = mock(SupportedUnifiedEvents.class);
        when(action.getName()).thenReturn("action3");
        List<Consumer> expected = List.of(Consumer.builder().name(SupportedConsumers.WORKER).endpoint("url")
                .outbound(SupportedOutbound.CLIENT).build());
        when(accessService.getPlatformConfigClient().getConfig(anyString(), any(TypeReference.class)))
                .thenReturn(expected);
        List<Consumer> result = consumerConfig.getConsumersForAction(action);
        assertEquals(expected, result);
    }

    @Test
    void testRemoveConsumersForAction_RemovesConsumer() {
        SupportedUnifiedEvents event = mock(SupportedUnifiedEvents.class);
        when(event.getName()).thenReturn("event1");
        String actionKey = "unified.events.draft.event1";
        Consumer consumerToRemove = Consumer.builder()
                .name(SupportedConsumers.WORKER)
                .endpoint("url")
                .outbound(SupportedOutbound.HTTP)
                .build();
        Consumer otherConsumer = Consumer.builder()
                .name(SupportedConsumers.WORKER_CLIENT)
                .endpoint("url2")
                .outbound(SupportedOutbound.CLIENT)
                .build();
        List<Consumer> consumers = new ArrayList<>();
        consumers.add(consumerToRemove);
        consumers.add(otherConsumer);

        when(accessService.getPlatformConfigClient().getConfig(anyString(), any(TypeReference.class)))
                .thenReturn(consumers);

        consumerConfig.removeConsumersForAction(event, consumerToRemove);

        List<Consumer> expectedConsumers = new ArrayList<>();
        expectedConsumers.add(otherConsumer);

        verify(accessService.getPlatformConfigClient()).setConfig(actionKey, expectedConsumers, false);
    }

    @Test
    void testInit() {
        ConsumerConfig spyConfig = spy(new ConsumerConfig(accessService));
        Consumer consumer = Consumer.builder()
                .name(SupportedConsumers.WORKER_CLIENT)
                .outbound(SupportedOutbound.CLIENT)
                .build();
        SupportedUnifiedEvents[] actions = {mock(SupportedUnifiedEvents.class), mock(SupportedUnifiedEvents.class)};
        doReturn("action1").when(actions[0]).getName();
        doReturn("action2").when(actions[1]).getName();
        try (var mocked = mockStatic(SupportedUnifiedEvents.class)) {
            mocked.when(SupportedUnifiedEvents::values).thenReturn(actions);
            doNothing().when(spyConfig)
                    .setConsumerForAction(any(SupportedUnifiedEvents.class), eq(consumer), eq(false));
            spyConfig.init();
            verify(spyConfig).setConsumerForAction(actions[0], consumer, false);
            verify(spyConfig).setConsumerForAction(actions[1], consumer, false);
        }
    }
}
