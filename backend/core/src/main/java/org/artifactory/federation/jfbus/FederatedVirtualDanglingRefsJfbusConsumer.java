package org.artifactory.federation.jfbus;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.federation.DeferredDanglingRefCleanupDao;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.jfbus.JfbusConstants;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.spring.ContextReadinessListener;
import org.jfrog.common.JsonParsingException;
import org.jfrog.common.JsonUtils;
import org.jfrog.eventing.facade.ConsumeOptions;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.MessageBatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JFBus consumer for deferred federated virtual dangling-ref cleanup tasks.
 * Relies on JFBus's native retry/redeliver mechanism (maxRedeliveryCount).
 * A process-scoped counter passively tracks failures per key; when it reaches
 * the configured max, the entry is persisted to DB so startup replay can
 * pick it up after JFBus discards the message.
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "artifactory.virtual.repo.federation.enabled", havingValue = "true")
public class FederatedVirtualDanglingRefsJfbusConsumer implements ContextReadinessListener {

    private final JfbusEventingService jfbusEventingService;
    private final FederatedRepoService federatedRepoService;
    private final SecurityService securityService;
    private final ArtifactoryContext artifactoryContext;
    private final DeferredDanglingRefCleanupDao danglingRefCleanupDao;
    private ArtifactoryExecutor consumerExecutor;

    @VisibleForTesting
    final ConcurrentHashMap<String, AtomicInteger> retryCounters =
            new ConcurrentHashMap<>();

    @Autowired
    public FederatedVirtualDanglingRefsJfbusConsumer(
            JfbusEventingService jfbusEventingService,
            FederatedRepoService federatedRepoService,
            SecurityService securityService,
            ApplicationContext applicationContext,
            DeferredDanglingRefCleanupDao danglingRefCleanupDao) {
        this.jfbusEventingService = jfbusEventingService;
        this.federatedRepoService = federatedRepoService;
        this.securityService = securityService;
        this.artifactoryContext = (ArtifactoryContext) applicationContext;
        this.danglingRefCleanupDao = danglingRefCleanupDao;
    }

    @Override
    public void onContextReady() {
        consumerExecutor = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .daemon(true)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat("federated-dangling-refs-jfbus-consumer-%d")
                .build(), artifactoryContext, true);
        consumerExecutor.execute(this::consumerLoop);
    }

    @PreDestroy
    void stopConsumerExecutor() {
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
    }

    /**
     * Blocks until shutdown/interrupt or eventing is absent. This class does not hold per-loop Closeables:
     * {@link JfbusEventingService#consumeTopic} owns subscription/client lifecycle with the shared
     * {@link JfbusEventingService} bean. This loop runs inside {@link org.artifactory.concurrent.ArtifactoryRunnable}
     * (see {@link #onContextReady}), which binds context for the thread; {@link #handleBatch} does not bind/unbind.
     */
    @VisibleForTesting
    void consumerLoop() {
        try {
            ConsumeOptions options = ConsumeOptions.builder()
                    .maxRedeliveryCount(ConstantValues.virtualRepoRefRemovalJfbusMaxRedeliveryCount.getInt())
                    .errorRetryDelayMs(ConstantValues.virtualRepoRefRemovalJfbusConsumerErrorBackoffMs.getInt())
                    .build();
            boolean running = true;
            while (running) {
                try {
                    boolean subscribed = jfbusEventingService.consumeTopic(
                            FederatedVirtualDanglingRefsJfbusProducer.DEFERRED_TOPIC,
                            ConstantValues.virtualRepoRefRemovalJfbusConsumerSubscription.getString(),
                            options, this::handleBatch);
                    if (!subscribed) {
                        log.info("Federated dangling refs JFBus consumer: eventing client not configured; "
                                + "exiting consumer thread.");
                        running = false;
                    }
                } catch (InterruptedException e) {
                    handleInterrupted(e);
                    running = false;
                } catch (Exception e) {
                    log.error("Federated dangling refs JFBus consumer: fatal error, stopping consumer thread", e);
                    running = false;
                }
            }
        } finally {
            log.debug("Federated dangling refs JFBus consumer loop ended");
        }
    }

    private void handleInterrupted(InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("Federated dangling refs JFBus consumer interrupted, stopping loop");
        if (log.isDebugEnabled()) {
            log.debug("Interrupted exception details", e);
        }
    }

    /**
     * JFBus delivers batches on the consumer thread that runs {@link #consumerLoop}, already wrapped by
     * {@link org.artifactory.concurrent.ArtifactoryRunnable} — no per-batch bind/unbind here.
     */
    void handleBatch(MessageBatch batch) {
        try {
            AtomicBoolean shouldCommit = new AtomicBoolean(true);
            securityService.doAsSystem(() -> {
                for (Message msg : batch.getMessages()) {
                    if (!handleOneMessage(msg)) {
                        shouldCommit.set(false);
                    }
                }
            });
            if (shouldCommit.get()) {
                batch.commit();
            } else {
                log.warn("Skipping batch commit to allow broker redelivery.");
            }
        } catch (Exception e) {
            log.error("Federated dangling refs JFBus batch handling failed", e);
        }
    }

    private boolean handleOneMessage(Message msg) {
        Map<String, String> headers = msg.getHeaders();
        if (headers == null || !"true".equals(headers.get(JfbusConstants.HEADER_VIRTUAL_FEDERATED))) {
            return true;
        }
        byte[] raw = msg.getPayload();
        if (raw == null || raw.length == 0) {
            return true;
        }
        final DeferredDanglingRefMessage payload;
        try {
            payload = JsonUtils.getInstance().readValue(raw, DeferredDanglingRefMessage.class);
        } catch (JsonParsingException e) {
            log.warn("Federated dangling refs JFBus: invalid message payload (JSON parse/deserialize failed)", e);
            return true;
        }
        try {
            String virtual = payload.getVirtualRepoKey();
            String child = payload.getChildRepoKey();
            if (virtual == null || child == null) {
                return true;
            }
            return handleCleanupWithRetryCounter(virtual, child);
        } catch (Exception e) {
            log.error("Federated dangling refs JFBus: unexpected error"
                    + " processing dangling ref cleanup retry message", e);
            return false;
        }
    }

    /**
     * @return {@code true} to commit (cleanup succeeded);
     *         {@code false} to nack (JFBus handles redelivery)
     */
    private boolean handleCleanupWithRetryCounter(
            String virtual, String child) {
        String key = virtual + "|" + child;
        boolean done = federatedRepoService
                .runDanglingRefCleanupRetry(virtual, child);
        if (done) {
            retryCounters.remove(key);
            deleteFromDaoSilently(virtual, child);
            return true;
        }
        int attempts = retryCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
        int maxRetries = ConstantValues
                .virtualRepoRefRemovalJfbusMaxRedeliveryCount
                .getInt();
        if (attempts >= maxRetries) {
            log.info("Dangling ref cleanup failed {} times for {} in"
                            + " {}; persisting to DB for startup replay",
                    attempts, child, virtual);
            upsertToDaoSilently(virtual, child);
            retryCounters.remove(key);
        }
        return false;
    }

    private void upsertToDaoSilently(
            String virtual, String child) {
        try {
            danglingRefCleanupDao.upsert(virtual, child);
        } catch (Exception e) {
            log.error("Failed to persist dangling ref to DB for {} in {}"
                    + "; cleanup may be lost", child, virtual, e);
        }
    }

    private void deleteFromDaoSilently(
            String virtual, String child) {
        try {
            danglingRefCleanupDao.delete(virtual, child);
        } catch (Exception e) {
            log.warn("Failed to delete dangling ref from DB for {} in {}",
                    child, virtual, e);
        }
    }

}
