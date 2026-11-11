package org.artifactory.unifiedevent;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.unifiedevent.dispatchers.EventDispatcher;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EventDispatcherHandler {
    private final ArtifactoryExecutor executorService;
    private final List<EventDispatcher> eventDispatchers;

    public EventDispatcherHandler(@Autowired(required = false) List<EventDispatcher> eventDispatchers) {
        this.executorService = initExecutor();
        this.eventDispatchers = eventDispatchers != null ? eventDispatchers : List.of();
    }

    public <T> List<DispatcherResponse> dispatchEvent(EventEnvelope<T> unifiedEvent) {
        List<EventDispatcher> syncDispatchers = new ArrayList<>();
        List<EventDispatcher> asyncDispatchers = new ArrayList<>();
        for (EventDispatcher dispatcher : eventDispatchers) {
            if (!dispatcher.isEnabled()) {
                log.debug("Found disabled {} dispatcher: {}", dispatcher.isSync(), dispatcher);
                continue;
            }
            log.debug("Available {} dispatcher found: {}", dispatcher.isSync(), dispatcher);
            (dispatcher.isSync() ? syncDispatchers : asyncDispatchers).add(dispatcher);
        }
        // Send the event to async dispatchers without waiting for a response
        sendEventToAsyncDispatchers(asyncDispatchers, unifiedEvent);
        // Do not use the thread pool if there is only one sync dispatcher
        log.debug("Total sync dispatchers to send the event to: {}", syncDispatchers.size());
        if (syncDispatchers.size() == 1) {
            try {
                return List.of(syncDispatchers.getFirst().dispatchEvent(unifiedEvent));
            } catch (Exception exception) {
                log.warn("Sync dispatcher '{}' has exception '{}' on event '{}'", syncDispatchers.getFirst(),
                        exception.getMessage(), unifiedEvent);
                return List.of();
            }
        }
        // Send the event to sync dispatchers using the thread pool and wait for all responses
        return sendEventToSyncDispatchers(syncDispatchers, unifiedEvent);
    }

    public <T> List<DispatcherResponse> sendEventToSyncDispatchers(List<EventDispatcher> syncDispatchers,
            EventEnvelope<T> unifiedEvent) {
        List<CompletableFuture<DispatcherResponse>> futures = new ArrayList<>();
        syncDispatchers.forEach(dispatcher -> futures.add(
                CompletableFuture.supplyAsync(() -> dispatcher.dispatchEvent(unifiedEvent), executorService)
                        .exceptionally(exception -> {
                            log.warn("Sync dispatcher '{}' thread pool has exception '{}' on event '{}'",
                                    dispatcher, exception.getMessage(), unifiedEvent);
                            return null;
                        })));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public <T> void sendEventToAsyncDispatchers(List<EventDispatcher> asyncDispatchers, EventEnvelope<T> unifiedEvent) {
        log.debug("Total async dispatchers to send the event to: {}", asyncDispatchers.size());
        asyncDispatchers.forEach(
                dispatcher -> CompletableFuture.runAsync(() -> dispatcher.dispatchEvent(unifiedEvent), executorService)
                        .exceptionally(exception -> {
                            log.warn("Async dispatcher '{}' has exception '{}' on event '{}'", dispatcher,
                                    exception.getMessage(), unifiedEvent);
                            return null;
                        }));
    }

    @Nonnull
    public ArtifactoryExecutor initExecutor() {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(ConstantValues.unifiedEventDispatcherCorePoolSize.getInt())
                .maxThreads(ConstantValues.unifiedEventDispatcherMaxThreads.getInt())
                .keepAliveTimeout(ConstantValues.unifiedEventDispatcherKeepAliveTimeoutSecs.getInt())
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .queue(new ArrayBlockingQueue<>(ConstantValues.unifiedEventDispatcherQueueSize.getInt()))
                .threadNameFormat("unified-event-%s")
                .rejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .build(), ContextHelper.get(), true);
    }

}
