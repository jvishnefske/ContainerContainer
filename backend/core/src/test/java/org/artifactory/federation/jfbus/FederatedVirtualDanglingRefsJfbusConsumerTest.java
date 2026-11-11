package org.artifactory.federation.jfbus;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.DeferredDanglingRefCleanupDao;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.jfrog.eventing.facade.ConsumeOptions;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.MessageBatch;
import org.jfrog.eventing.facade.TopicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedVirtualDanglingRefsJfbusConsumerTest
        extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private JfbusEventingService jfbusEventingService;
    @Mock
    private FederatedRepoService federatedRepoService;
    @Mock
    private SecurityService securityService;
    @Mock
    private DeferredDanglingRefCleanupDao danglingRefCleanupDao;

    private InternalArtifactoryContext applicationContext;
    private AutoCloseable mocks;

    @BeforeEach
    void setUpConsumerTest() {
        mocks = MockitoAnnotations.openMocks(this);
        applicationContext = mock(InternalArtifactoryContext.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(securityService).doAsSystem(any());
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    @Override
    public void unbindArtifactoryHome() {
        ArtifactoryContextThreadBinder.unbind();
        try {
            mocks.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        super.unbindArtifactoryHome();
    }

    private FederatedVirtualDanglingRefsJfbusConsumer createConsumer() {
        return new FederatedVirtualDanglingRefsJfbusConsumer(
                jfbusEventingService, federatedRepoService,
                securityService, applicationContext,
                danglingRefCleanupDao);
    }

    private MessageBatch batchOf(String virtual, String child) {
        byte[] raw = JsonUtils.getInstance().valueToByteArray(
                new DeferredDanglingRefMessage(virtual, child));
        Message message = Message.builder().payload(raw)
                .headers(Map.of(org.artifactory.jfbus.JfbusConstants.HEADER_VIRTUAL_FEDERATED, "true"))
                .build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));
        return batch;
    }

    @Test
    void commitsBatch_whenCleanupSucceeds() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        when(federatedRepoService
                .runDanglingRefCleanupRetry("virt", "child"))
                .thenReturn(true);
        MessageBatch batch = batchOf("virt", "child");

        consumer.handleBatch(batch);

        verify(federatedRepoService)
                .runDanglingRefCleanupRetry("virt", "child");
        verify(batch).commit();
        verify(danglingRefCleanupDao).delete("virt", "child");
        assertTrue(consumer.retryCounters.isEmpty());
    }

    @Test
    void skipsCommit_whenCleanupStillNeeded_belowMaxRetries()
            throws Exception {
        getBound().setProperty(
                ConstantValues
                        .virtualRepoRefRemovalJfbusMaxRedeliveryCount,
                "5");
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        when(federatedRepoService
                .runDanglingRefCleanupRetry("v", "c"))
                .thenReturn(false);
        MessageBatch batch = batchOf("v", "c");

        consumer.handleBatch(batch);

        verify(batch, never()).commit();
        verify(danglingRefCleanupDao, never())
                .upsert(anyString(), anyString());
        assertEquals(1,
                consumer.retryCounters.get("v|c").get());
    }

    @Test
    void insertsDbRow_whenRetryCounterExhausted()
            throws Exception {
        getBound().setProperty(
                ConstantValues
                        .virtualRepoRefRemovalJfbusMaxRedeliveryCount,
                "3");
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        when(federatedRepoService
                .runDanglingRefCleanupRetry("v", "c"))
                .thenReturn(false);

        for (int i = 0; i < 2; i++) {
            consumer.handleBatch(batchOf("v", "c"));
        }
        verify(danglingRefCleanupDao, never()).upsert("v", "c");

        MessageBatch finalBatch = batchOf("v", "c");
        consumer.handleBatch(finalBatch);

        verify(danglingRefCleanupDao).upsert("v", "c");
        verify(finalBatch, never()).commit();
        assertFalse(consumer.retryCounters.containsKey("v|c"));
    }

    @Test
    void removesCounterAndDeletesDbRow_whenCleanupSucceedsAfterRetries()
            throws Exception {
        getBound().setProperty(
                ConstantValues
                        .virtualRepoRefRemovalJfbusMaxRedeliveryCount,
                "10");
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        when(federatedRepoService
                .runDanglingRefCleanupRetry("v", "c"))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        consumer.handleBatch(batchOf("v", "c"));
        consumer.handleBatch(batchOf("v", "c"));
        assertEquals(2, consumer.retryCounters.get("v|c").get());

        MessageBatch successBatch = batchOf("v", "c");
        consumer.handleBatch(successBatch);

        verify(successBatch).commit();
        verify(danglingRefCleanupDao).delete("v", "c");
        assertFalse(consumer.retryCounters.containsKey("v|c"));
    }

    @Test
    void commitsBatch_whenPayloadIsInvalidJson() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        byte[] raw = "not-valid-json"
                .getBytes(StandardCharsets.UTF_8);
        Message message = Message.builder().payload(raw).build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));

        consumer.handleBatch(batch);

        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(anyString(), anyString());
        verify(batch).commit();
    }

    @Test
    void skipsCommit_onUnexpectedProcessingFailure() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        doThrow(new IllegalStateException("simulated failure"))
                .when(federatedRepoService)
                .runDanglingRefCleanupRetry("v", "c");
        MessageBatch batch = batchOf("v", "c");

        consumer.handleBatch(batch);

        verify(batch, never()).commit();
        assertTrue(consumer.retryCounters.isEmpty());
    }

    @Test
    void commitsBatch_whenVirtualOrChildKeyMissing() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer =
                createConsumer();
        byte[] raw = JsonUtils.getInstance().valueToByteArray(
                new DeferredDanglingRefMessage(null, "child"));
        Message message = Message.builder().payload(raw)
                .headers(Map.of(org.artifactory.jfbus.JfbusConstants.HEADER_VIRTUAL_FEDERATED, "true"))
                .build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));

        consumer.handleBatch(batch);

        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(anyString(), anyString());
        verify(batch).commit();
    }

    @Test
    void commitsBatch_whenVirtualFederatedHeaderAbsent() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        byte[] raw = JsonUtils.getInstance().valueToByteArray(
                new DeferredDanglingRefMessage("virt", "child"));
        Message message = Message.builder().payload(raw).headers(Map.of()).build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));

        consumer.handleBatch(batch);

        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(anyString(), anyString());
        verify(batch).commit();
    }

    @Test
    void commitsBatch_whenPayloadIsEmptyBytes() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        Message message = Message.builder().payload(new byte[0])
                .headers(Map.of(org.artifactory.jfbus.JfbusConstants.HEADER_VIRTUAL_FEDERATED, "true"))
                .build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));

        consumer.handleBatch(batch);

        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(anyString(), anyString());
        verify(batch).commit();
    }

    @Test
    void commitsBatch_whenPayloadJsonIsInvalid() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        byte[] raw = "not-valid-json".getBytes(StandardCharsets.UTF_8);
        Message message = Message.builder().payload(raw)
                .headers(Map.of(org.artifactory.jfbus.JfbusConstants.HEADER_VIRTUAL_FEDERATED, "true"))
                .build();
        MessageBatch batch = mock(MessageBatch.class);
        when(batch.getMessages()).thenReturn(List.of(message));

        consumer.handleBatch(batch);

        verify(federatedRepoService, never())
                .runDanglingRefCleanupRetry(anyString(), anyString());
        verify(batch).commit();
    }

    @Test
    void continuesWithoutThrowing_whenDaoUpsertFails() throws Exception {
        getBound().setProperty(
                ConstantValues.virtualRepoRefRemovalJfbusMaxRedeliveryCount, "1");
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        when(federatedRepoService.runDanglingRefCleanupRetry("v", "c")).thenReturn(false);
        doThrow(new RuntimeException("db down")).when(danglingRefCleanupDao).upsert("v", "c");
        MessageBatch batch = batchOf("v", "c");

        consumer.handleBatch(batch);

        verify(danglingRefCleanupDao).upsert("v", "c");
        verify(batch, never()).commit();
        assertFalse(consumer.retryCounters.containsKey("v|c"));
    }

    @Test
    void continuesWithoutThrowing_whenDaoDeleteFails() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        when(federatedRepoService.runDanglingRefCleanupRetry("virt", "child")).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(danglingRefCleanupDao).delete("virt", "child");
        MessageBatch batch = batchOf("virt", "child");

        consumer.handleBatch(batch);

        verify(danglingRefCleanupDao).delete("virt", "child");
        verify(batch).commit();
        assertTrue(consumer.retryCounters.isEmpty());
    }

    @Test
    void stopConsumerExecutor_doesNotThrow_whenExecutorIsNull() {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();

        consumer.stopConsumerExecutor();
    }

    @Test
    void consumerLoop_exitsCleanly_whenEventingClientNotConfigured() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        when(jfbusEventingService.consumeTopic(any(TopicId.class), anyString(),
                any(ConsumeOptions.class), any())).thenReturn(false);

        consumer.consumerLoop();

        verify(jfbusEventingService).consumeTopic(any(TopicId.class), anyString(),
                any(ConsumeOptions.class), any());
    }

    @Test
    void consumerLoop_exitsAndReInterrupts_whenSubscribeThrowsInterrupted() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        when(jfbusEventingService.consumeTopic(any(TopicId.class), anyString(),
                any(ConsumeOptions.class), any())).thenThrow(new InterruptedException("simulated"));

        try {
            consumer.consumerLoop();
            assertTrue(Thread.currentThread().isInterrupted(),
                    "Interrupt status must be re-set after InterruptedException");
        } finally {
            // Clear interrupted flag so it does not leak into the next test on the shared thread.
            Thread.interrupted();
        }
    }

    @Test
    void consumerLoop_exitsCleanly_whenSubscribeThrowsRuntime() throws Exception {
        FederatedVirtualDanglingRefsJfbusConsumer consumer = createConsumer();
        when(jfbusEventingService.consumeTopic(any(TopicId.class), anyString(),
                any(ConsumeOptions.class), any())).thenThrow(new IllegalStateException("simulated"));

        consumer.consumerLoop();

        verify(jfbusEventingService).consumeTopic(any(TopicId.class), anyString(),
                any(ConsumeOptions.class), any());
    }
}
