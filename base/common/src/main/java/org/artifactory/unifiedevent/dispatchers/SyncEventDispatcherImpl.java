package org.artifactory.unifiedevent.dispatchers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.security.access.AccessService;
import org.artifactory.unifiedevent.dispatchers.outbound.OutboundDispatcher;
import org.artifactory.unifiedevent.mapper.DispatcherResponseMapper;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.registry.ConsumerConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Harendra Rathore
 */
@Slf4j
@Component
public class SyncEventDispatcherImpl implements EventDispatcher {

    protected AccessService accessService;
    protected ConsumerConfig consumerConfig;
    protected Map<String, OutboundDispatcher> outboundDispatchers;
    protected final ArtifactoryExecutor executorService;

    public SyncEventDispatcherImpl(AccessService accessService, ConsumerConfig consumerConfig,
            Map<String, OutboundDispatcher> outboundDispatchers) {
        this.accessService = accessService;
        this.consumerConfig = consumerConfig;
        this.outboundDispatchers = outboundDispatchers;
        this.executorService = initExecutor();
    }

    @Override
    public <T> DispatcherResponse dispatchEvent(EventEnvelope<T> unifiedEvent) {
        List<Consumer> consumers = consumerConfig.getConsumersForAction(unifiedEvent.type());
        log.debug("Total sync consumers to send the event to: {}", consumers.size());
        // Do not use the thread pool if there is only one sync consumer
        if (consumers.size() == 1) {
            try {
                DispatcherResponse response = sendRequestToConsumer(consumers.getFirst(), unifiedEvent);
                return DispatcherResponseMapper.toConsolidatedDispatcherResponse(Collections.singletonList(response));
            } catch (Exception exception) {
                log.warn("Sync consumer '{}' has exception '{}' on event '{}'",
                        consumers.getFirst(), exception.getMessage(), unifiedEvent);
                return DispatcherResponse.builder().shouldProceed(true).build();
            }
        }
        // Send the event to sync consumers using the thread pool and wait for all responses
        return sendEventToSyncConsumers(consumers, unifiedEvent);
    }

    protected <T> DispatcherResponse sendEventToSyncConsumers(List<Consumer> consumers,
            EventEnvelope<T> unifiedEvent) {
        List<CompletableFuture<DispatcherResponse>> futures = new ArrayList<>();
        consumers.forEach(consumer -> futures.add(CompletableFuture
                .supplyAsync(() -> sendRequestToConsumer(consumer, unifiedEvent), executorService)
                .exceptionally(exception -> {
                    log.warn("Sync consumer '{}' thread pool has exception '{}' on event '{}'",
                            unifiedEvent, exception.getMessage(), unifiedEvent);
                    return null;
                })
        ));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return DispatcherResponseMapper.toConsolidatedDispatcherResponse(
                futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isSync() {
        return true;
    }

    protected <T> DispatcherResponse sendRequestToConsumer(Consumer consumer, EventEnvelope<T> unifiedEvent) {
        OutboundDispatcher outboundDispatcher = outboundDispatchers.get(consumer.outbound().getName());
        if (outboundDispatcher == null) {
            log.warn("No outbound dispatcher: {}, found for consumer: {}", consumer.name(), consumer.outbound());
            return null;
        }
        log.debug("Sending event to sync consumer: '{}' Using outbound: '{}'", consumer, outboundDispatcher);
        return outboundDispatcher.dispatchEvent(consumer, unifiedEvent);
    }

    @Nonnull
    private ArtifactoryExecutor initExecutor() {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(ConstantValues.syncEventDispatcherCorePoolSize.getInt())
                .maxThreads(ConstantValues.syncEventDispatcherMaxThreads.getInt())
                .keepAliveTimeout(ConstantValues.syncEventDispatcherKeepAliveTimeoutSecs.getInt())
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .queue(new ArrayBlockingQueue<>(ConstantValues.syncEventDispatcherQueueSize.getInt()))
                .threadNameFormat("sync-event-dispatcher-%s")
                .rejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .build(), ContextHelper.get(), true);
    }

}
