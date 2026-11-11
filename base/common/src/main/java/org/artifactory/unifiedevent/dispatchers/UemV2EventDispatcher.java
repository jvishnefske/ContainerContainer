package org.artifactory.unifiedevent.dispatchers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.UemV2AsyncPublisher;
import org.artifactory.jfbus.UemV2PublishRequest;
import org.artifactory.jfbus.UemV2Publisher;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.unifiedevent.config.JfbusTopicConfig;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * UEM v2 asynchronous event dispatcher. Maps a unified {@link EventEnvelope} onto a {@link UemV2PublishRequest}
 * and emits it through {@link UemV2Publisher} (CloudEvents binary-mode wire format), in contrast to the legacy
 * {@link UemV1EventDispatcher} which publishes via the manual {@code JfbusEventingService.sendEvent} path.
 * <p>
 * Wired into the {@link EventDispatcherHandler} dispatch loop but gated off by default via
 * {@link ConstantValues#uemV2PublisherEnabled}; only events with a configured UEM v2 entity type
 * ({@link JfbusTopicConfig#getEntityType}) are published. Fire-and-forget: failures are logged and never break
 * the calling flow.
 * <p>
 * Implements {@link ContextCreationListener} so the {@code artifactory.mb_<entityType>} topics are registered at
 * startup (mirroring {@link UemV1EventDispatcher}); when UEM v2 is disabled the injected {@link UemV2Publisher}
 * is the no-op fallback, so {@link #onContextCreated()} does nothing.
 * <p>
 * Publishing is delegated to {@link UemV2AsyncPublisher} so the broker/outbox round-trip runs off the shared
 * dispatch pool (mirroring V1, where {@code JfbusEventingService.sendEvent} is {@code @Async}). Topic creation
 * stays on the synchronous {@link UemV2Publisher} because it must complete during context startup.
 * <p>
 * <b>Lifecycle note:</b> {@link #isEnabled()} reads the flag dynamically per call, but the injected
 * {@link UemV2Publisher} is wired once at Spring context startup. Toggling
 * {@code artifactory.uemv2.publisher.enabled} at runtime without a restart causes the dispatcher to route
 * events to whichever publisher was wired at startup (real or no-op). A restart is required for the flag
 * change to take effect.
 */
@Slf4j
@Component
public class UemV2EventDispatcher implements EventDispatcher, ContextCreationListener {

    private final UemV2Publisher uemV2Publisher;
    private final UemV2AsyncPublisher uemV2AsyncPublisher;

    public UemV2EventDispatcher(UemV2Publisher uemV2Publisher, UemV2AsyncPublisher uemV2AsyncPublisher) {
        this.uemV2Publisher = uemV2Publisher;
        this.uemV2AsyncPublisher = uemV2AsyncPublisher;
    }

    @Override
    public void onContextCreated() {
        for (Map.Entry<String, Integer> entity : JfbusTopicConfig.entityTypePartitions().entrySet()) {
            try {
                uemV2Publisher.createTopic(entity.getKey(), entity.getValue());
            } catch (Exception e) {
                // Never let a broker/topic hiccup abort context refresh.
                log.error("Failed to create UEM v2 topic for entity type '{}'", entity.getKey(), e);
            }
        }
    }

    @Override
    public <T> DispatcherResponse dispatchEvent(EventEnvelope<T> eventEnvelope) {
        // UEM v2 is fire-and-forget; always allow the event flow to proceed.
        DispatcherResponse response = DispatcherResponse.builder().shouldProceed(true).build();
        try {
            String entityType = JfbusTopicConfig.getEntityType(eventEnvelope.type());
            if (entityType == null) {
                log.debug("No UEM v2 entity type configured for event type: {}", eventEnvelope.type());
                return response;
            }
            String dataSchema = eventEnvelope.dataSchema();
            if (dataSchema == null) {
                log.warn("UEM v2 event dropped: missing dataSchema for event type {}", eventEnvelope.type());
                return response;
            }
            uemV2AsyncPublisher.publish(UemV2PublishRequest.builder()
                    .data(eventEnvelope.data())
                    .source(eventEnvelope.source())
                    .type(eventEnvelope.type().getType())
                    .subject(eventEnvelope.subject())
                    .dataSchema(dataSchema)
                    .entityType(entityType)
                    .id(eventEnvelope.id())
                    .build());
        } catch (RejectedExecutionException e) {
            // The async executor queue is saturated; the event is permanently dropped.
            log.error("UEM v2 async executor saturated, event dropped for type {}", eventEnvelope.type(), e);
        } catch (Exception e) {
            // Only event-mapping/build errors surface here; broker failures are owned by UemV2PublisherImpl
            // (debugOrError) and never propagate back through the @Async publish hop.
            log.warn("Failure publishing UEM v2 event type={} subject={} id={}",
                    eventEnvelope.type(), eventEnvelope.subject(), eventEnvelope.id(), e);
        }
        return response;
    }

    @Override
    public boolean isEnabled() {
        return ConstantValues.uemV2PublisherEnabled.getBoolean();
    }

    @Override
    public boolean isSync() {
        return false;
    }
}
