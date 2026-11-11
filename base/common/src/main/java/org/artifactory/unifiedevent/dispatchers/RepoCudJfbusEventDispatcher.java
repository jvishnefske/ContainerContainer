package org.artifactory.unifiedevent.dispatchers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.unifiedevent.config.JfbusTopicConfig;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.stereotype.Component;

/**
 * Async JFBus dispatcher for AFTER repo CUD events. Repo-specific rollout (DC6/RS1) and TR1 payload-size policy live
 * here instead of the generic {@link UemV1EventDispatcher}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepoCudJfbusEventDispatcher implements EventDispatcher, ContextCreationListener {

    private final JfbusAsyncEventSupport jfbusAsyncEventSupport;

    @Override
    public void onContextCreated() {
        for (JfbusTopicConfig config : JfbusTopicConfig.repoCudAfterTopicConfigs()) {
            try {
                jfbusAsyncEventSupport.ensureTopicCreated(config);
                log.debug("Ensured JFBus topic '{}' with {} partitions for event '{}'", config.getTopicId(),
                        config.getPartitionCount(), config.getEvent());
            } catch (Exception e) {
                log.error("Failed to create JFBus topic '{}' for event '{}': {}", config.getTopicId(),
                        config.getEvent(), e.getMessage());
            }
        }
    }

    @Override
    public <T> DispatcherResponse dispatchEvent(EventEnvelope<T> eventEnvelope) {
        DispatcherResponse response = DispatcherResponse.builder().shouldProceed(true).build();
        JfbusTopicConfig topicConfig = JfbusTopicConfig.getRepoCudAfterTopic(eventEnvelope.type());
        if (topicConfig == null) {
            return response;
        }
        try {
            jfbusAsyncEventSupport.ensureTopicCreated(topicConfig);
            byte[] payload = jfbusAsyncEventSupport.serialize(eventEnvelope);
            if (exceedsHardSizeLimit(eventEnvelope, payload)) {
                return response;
            }
            jfbusAsyncEventSupport.publish(eventEnvelope, topicConfig, payload);
            return response;
        } catch (Exception e) {
            log.warn("Failure in sending repo CUD Jfbus event: {}", eventEnvelope, e);
            return response;
        }
    }

    @Override
    public boolean isEnabled() {
        return ConstantValues.asyncEventDispatcherEnabled.getBoolean()
                && ConstantValues.repoCudJfbusEventsEnabled.getBoolean();
    }

    @Override
    public boolean isSync() {
        return false;
    }

    /**
     * Enforces the TR1 payload-size policy for AFTER repo CUD JFBus events (R11). When the serialized payload exceeds
     * {@link ConstantValues#repoCudJfbusEventsMaxPayloadBytes}, the publish is skipped and a structured WARN outcome
     * is logged. The originating repo operation is never failed by an oversize publish.
     */
    private <T> boolean exceedsHardSizeLimit(EventEnvelope<T> eventEnvelope, byte[] payload) {
        int limit = ConstantValues.repoCudJfbusEventsMaxPayloadBytes.getInt();
        if (payload.length <= limit) {
            return false;
        }
        log.warn("Skipped oversize repo CUD JFBus event: type={} phase=after eventId={} subject={} "
                        + "payloadSize={} limit={}",
                eventEnvelope.type(), eventEnvelope.id(), eventEnvelope.subject(), payload.length, limit);
        return true;
    }
}
