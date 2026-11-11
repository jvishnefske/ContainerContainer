package org.artifactory.unifiedevent;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.dispatchers.EventDispatcher;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class EventDispatcherHandlerTest extends ArtifactoryHomeBoundJUnitTest {

    private static EventDispatcher mockAdaptor(boolean enabled, boolean sync, DispatcherResponse response,
            boolean throwException) {
        EventDispatcher adaptor = mock(EventDispatcher.class);
        when(adaptor.isEnabled()).thenReturn(enabled);
        when(adaptor.isSync()).thenReturn(sync);
        if (throwException) {
            when(adaptor.dispatchEvent(any())).thenThrow(new RuntimeException("fail"));
        } else {
            when(adaptor.dispatchEvent(any())).thenReturn(response);
        }
        return adaptor;
    }

    static Stream<Arguments> dispatchEventSource() {
        ArtifactData artifactData = ArtifactData.builder()
                .repoKey("repoKey")
                .path("repoPath")
                .build();
        ArtifactEventData artifactEventData = ArtifactEventData.builder()
                .artifact(artifactData)
                .build();
        EventEnvelope<ArtifactEventData> unifiedEvent = EventEnvelope.<ArtifactEventData>builder()
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .data(artifactEventData)
                .build();
        DispatcherResponse responseTrue = DispatcherResponse.builder().shouldProceed(true).build();
        DispatcherResponse responseFalse = DispatcherResponse.builder().shouldProceed(false).build();
        return Stream.of(
                // One enabled adaptor returns true
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(true, true, responseTrue, false)),
                        List.of(responseTrue)),
                // One enabled adaptor returns false
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(true, true, responseFalse, false)),
                        List.of(responseFalse)),
                // One disabled adaptor
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(false, true, responseTrue, false)),
                        List.of()),
                // One enabled adaptor throws exception
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(true, true, null, true)),
                        new ArrayList<>()),
                // Multiple adaptors, mixed enabled/disabled and exception
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(true, true, responseTrue, false),
                                mockAdaptor(false, true, responseFalse, false),
                                mockAdaptor(true, true, null, true)),
                        new ArrayList<>(Arrays.asList(responseTrue, null))),
                // One async dispatcher
                Arguments.of(
                        unifiedEvent,
                        List.of(mockAdaptor(true, false, null, true)),
                        new ArrayList<>())
        );
    }

    @ParameterizedTest
    @MethodSource("dispatchEventSource")
    void testDispatchEvent(EventEnvelope unifiedEvent, List<EventDispatcher> adaptors,
            List<DispatcherResponse> expected) {
        setStringSystemProperty(ConstantValues.unifiedEventDispatcherCorePoolSize, "3");
        setStringSystemProperty(ConstantValues.unifiedEventDispatcherMaxThreads, "3");
        setStringSystemProperty(ConstantValues.unifiedEventDispatcherKeepAliveTimeoutSecs, "5");
        setStringSystemProperty(ConstantValues.unifiedEventDispatcherQueueSize, "10");
        EventDispatcherHandler dispatcherHandler = spy(new EventDispatcherHandler(adaptors));
        List<DispatcherResponse> result = dispatcherHandler.dispatchEvent(unifiedEvent);
        assertTrue(result.containsAll(expected));
    }

}
