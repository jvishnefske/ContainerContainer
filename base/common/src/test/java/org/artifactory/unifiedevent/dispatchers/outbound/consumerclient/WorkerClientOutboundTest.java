package org.artifactory.unifiedevent.dispatchers.outbound.consumerclient;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class WorkerClientOutboundTest {

    private WorkerClientOutboundDispatcherImpl workerClientOutbound;

    private AutoCloseable mocks;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private WorkersAddon workersAddon;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        doReturn(workersAddon).when(addonsManager).addonByType(WorkersAddon.class);
        workerClientOutbound = new WorkerClientOutboundDispatcherImpl(addonsManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void sendEvent_delegatesToWorkersAddonAndReturnsResponse() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.BEFORE_DOWNLOAD);

        DispatcherResponse response = workerClientOutbound.sendEvent(envelope);

        assertTrue(response.shouldProceed());
        assertNull(response.message());
        verify(workersAddon).run(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), eq(envelope), any());
    }

    @Test
    void sendEvent_whenWorkersAddonSetsShouldProceedFalse_returnsBuiltResponse() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.AFTER_REMOTE_DOWNLOAD);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            DispatcherResponse.DispatcherResponseBuilder builder =
                    (DispatcherResponse.DispatcherResponseBuilder) args[2];
            builder.shouldProceed(false).message("worker said no");
            return null;
        }).when(workersAddon).run(any(), any(), any());

        DispatcherResponse response = workerClientOutbound.sendEvent(envelope);

        assertFalse(response.shouldProceed());
        assertEquals("worker said no", response.message());
    }

    @Test
    void sendEvent_whenWorkerStopException_returnsShouldProceedFalseWithMessage() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE);
        WorkerStopException stop = new WorkerStopException("blocked");
        doThrow(stop).when(workersAddon).run(any(), any(), any());

        DispatcherResponse response = workerClientOutbound.sendEvent(envelope);

        assertFalse(response.shouldProceed());
        assertEquals("blocked", response.message());
    }

    @Test
    void sendEvent_whenGenericException_returnsShouldProceedTrueWithMessage() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.BEFORE_DOWNLOAD);
        RuntimeException failure = new RuntimeException("transient failure");
        doThrow(failure).when(workersAddon).run(any(), any(), any());

        DispatcherResponse response = workerClientOutbound.sendEvent(envelope);

        assertTrue(response.shouldProceed());
        assertEquals("transient failure", response.message());
    }

    @Test
    void sendEvent_whenAddonByTypeThrowsDuringShutdown_returnsShouldProceedTrueWithMessage() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.BEFORE_DOWNLOAD);
        IllegalStateException beanFactoryError =
                new IllegalStateException("BeanFactory not initialized or already closed");
        doThrow(beanFactoryError).when(addonsManager).addonByType(WorkersAddon.class);

        DispatcherResponse response = workerClientOutbound.sendEvent(envelope);

        assertTrue(response.shouldProceed());
        assertEquals("BeanFactory not initialized or already closed", response.message());
    }

    @Test
    void sendEvent_passesResponseBuilderToWorkersAddon() {
        EventEnvelope<Object> envelope = sampleEnvelope(SupportedUnifiedEvents.BEFORE_DOWNLOAD);
        ArgumentCaptor<DispatcherResponse.DispatcherResponseBuilder> builderCaptor =
                ArgumentCaptor.forClass(DispatcherResponse.DispatcherResponseBuilder.class);

        workerClientOutbound.sendEvent(envelope);

        verify(workersAddon).run(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), eq(envelope),
                builderCaptor.capture());
        DispatcherResponse built = builderCaptor.getValue().build();
        assertTrue(built.shouldProceed());
    }

    private static EventEnvelope<Object> sampleEnvelope(SupportedUnifiedEvents type) {
        return EventEnvelope.<Object>builder()
                .specVersion("1.0")
                .id("evt-1")
                .type(type)
                .source("artifactory://test")
                .time("2025-01-01T00:00:00Z")
                .subject("repo/path")
                .dataSchema("schema")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(null)
                .build();
    }
}
