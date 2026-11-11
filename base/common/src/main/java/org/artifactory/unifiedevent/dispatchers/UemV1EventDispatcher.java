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
 * UEM v1 asynchronous event dispatcher that publishes unified events to JFBus topics via the legacy
 * {@link org.artifactory.jfbus.JfbusEventingService#sendEvent} path (manual CloudEvents headers + serialized envelope
 * payload). Repo CUD AFTER events are handled by {@link RepoCudJfbusEventDispatcher}. The UEM v2 (CloudEvents
 * binary-mode) path is handled by {@link UemV2EventDispatcher}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UemV1EventDispatcher implements EventDispatcher, ContextCreationListener {

    private final JfbusAsyncEventSupport jfbusAsyncEventSupport;

    @Override
    public void onContextCreated() {
        for (JfbusTopicConfig config : JfbusTopicConfig.genericAsyncTopicConfigs()) {
            try {
                jfbusAsyncEventSupport.ensureTopicCreated(config);
                log.debug("Created JFBus topic '{}' for event '{}'", config.getTopicId(), config.getEvent());
            } catch (Exception e) {
                log.error("Failed to create JFBus topic '{}' for event '{}': {}", config.getTopicId(),
                        config.getEvent(), e.getMessage());
            }
        }
    }

    @Override
    public <T> DispatcherResponse dispatchEvent(EventEnvelope<T> eventEnvelope) {
        DispatcherResponse response = DispatcherResponse.builder().shouldProceed(true).build();
        try {
            JfbusTopicConfig topicConfig = JfbusTopicConfig.getGenericAsyncTopic(eventEnvelope.type());
            if (topicConfig == null) {
                return response;
            }
            jfbusAsyncEventSupport.ensureTopicCreated(topicConfig);
            byte[] payload = jfbusAsyncEventSupport.serialize(eventEnvelope);
            jfbusAsyncEventSupport.publish(eventEnvelope, topicConfig, payload);
            return response;
        } catch (Exception e) {
            log.warn("Failure in sending Jfbus event: {}", eventEnvelope, e);
            return response;
        }
    }

    @Override
    public boolean isEnabled() {
        return ConstantValues.asyncEventDispatcherEnabled.getBoolean();
    }

    @Override
    public boolean isSync() {
        return false;
    }
}
