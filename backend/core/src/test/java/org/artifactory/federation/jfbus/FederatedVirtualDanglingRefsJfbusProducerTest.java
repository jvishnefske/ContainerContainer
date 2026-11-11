package org.artifactory.federation.jfbus;

import org.artifactory.jfbus.JfbusConstants;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.artifactory.common.ConstantValues.virtualRepoRefRemovalJfbusTopicPartitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedVirtualDanglingRefsJfbusProducerTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void onContextCreated_createsTopic() throws Exception {
        JfbusEventingService jfbus = mock(JfbusEventingService.class);
        FederatedVirtualDanglingRefsJfbusProducer producer =
                new FederatedVirtualDanglingRefsJfbusProducer(jfbus);
        producer.onContextCreated();
        verify(jfbus).createTopic(FederatedVirtualDanglingRefsJfbusProducer.DEFERRED_TOPIC,
                virtualRepoRefRemovalJfbusTopicPartitions.getInt(), true);
    }

    @Test
    void publishDanglingRefCleanupRetry_usesJfbusEventingService() throws Exception {
        JfbusEventingService jfbus = mock(JfbusEventingService.class);
        when(jfbus.isConfigured()).thenReturn(true);
        FederatedVirtualDanglingRefsJfbusProducer producer =
                new FederatedVirtualDanglingRefsJfbusProducer(jfbus);
        assertTrue(producer.publishDanglingRefCleanupRetry("virt", "child"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jfbus).sendEventBlocking(eq(FederatedVirtualDanglingRefsJfbusProducer.DEFERRED_TOPIC),
                eq("virt|child"), any(byte[].class), headersCaptor.capture());
        assertEquals(JfbusConstants.VIRTUAL_REPO_REF_REMOVAL_EVENT_TYPE,
                headersCaptor.getValue().get(JfbusConstants.HEADER_TYPE));
        assertEquals("true",
                headersCaptor.getValue().get(JfbusConstants.HEADER_VIRTUAL_FEDERATED));
        verify(jfbus, never()).createTopic(any(), anyInt(), anyBoolean());
    }

    @Test
    void publishDanglingRefCleanupRetry_doesNotCallCreateTopic() throws Exception {
        JfbusEventingService jfbus = mock(JfbusEventingService.class);
        when(jfbus.isConfigured()).thenReturn(true);
        FederatedVirtualDanglingRefsJfbusProducer producer =
                new FederatedVirtualDanglingRefsJfbusProducer(jfbus);
        producer.publishDanglingRefCleanupRetry("a", "b");
        producer.publishDanglingRefCleanupRetry("c", "d");
        verify(jfbus, never()).createTopic(any(), anyInt(), anyBoolean());
        verify(jfbus, times(2)).sendEventBlocking(any(), any(), any(), any());
    }

    @Test
    void publishDanglingRefCleanupRetry_returnsFalseWhenSendFails() throws Exception {
        JfbusEventingService jfbus = mock(JfbusEventingService.class);
        when(jfbus.isConfigured()).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(jfbus)
                .sendEventBlocking(any(), any(), any(), any());
        FederatedVirtualDanglingRefsJfbusProducer producer =
                new FederatedVirtualDanglingRefsJfbusProducer(jfbus);
        assertFalse(producer.publishDanglingRefCleanupRetry("virt", "child"));
    }

    @Test
    void publishDanglingRefCleanupRetry_returnsTrueWhenJfbusNotConfigured() throws Exception {
        JfbusEventingService jfbus = mock(JfbusEventingService.class);
        when(jfbus.isConfigured()).thenReturn(false);
        FederatedVirtualDanglingRefsJfbusProducer producer =
                new FederatedVirtualDanglingRefsJfbusProducer(jfbus);
        assertTrue(producer.publishDanglingRefCleanupRetry("virt", "child"));
        verify(jfbus, never()).sendEventBlocking(any(), any(), any(), any());
    }
}
