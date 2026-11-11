package org.artifactory.unifiedevent.dispatchers;

import lombok.RequiredArgsConstructor;
import org.artifactory.jfbus.JfbusEventingService;
import org.artifactory.unifiedevent.config.JfbusTopicConfig;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.serializer.SerializationService;
import org.jfrog.eventing.facade.TopicId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.artifactory.jfbus.JfbusConstants.HEADER_CONTENT_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_DATA_SCHEMA;
import static org.artifactory.jfbus.JfbusConstants.HEADER_ID;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SOURCE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SPEC_VERSION;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SUBJECT;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TIME;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;

/**
 * Shared JFBus topic provisioning and publish helpers for async {@link EventDispatcher} implementations.
 */
@Component
@RequiredArgsConstructor
public class JfbusAsyncEventSupport {

    private final SerializationService serializationService;
    private final JfbusEventingService jfbusEventingService;

    /**
     * Topics already provisioned on the JFBus broker in this JVM, keyed by topic id, so each topic is created at most
     * once. This tracks broker {@code createTopic} calls, not enum lookup.
     */
    private final Map<TopicId, Boolean> provisionedTopics = new ConcurrentHashMap<>();

    /**
     * Provisions {@code config}'s topic with its configured partition count exactly once per topic. The JFBus broker
     * auto-creates an unknown topic with a single partition on first produce/consume, so a topic published earlier in
     * startup (before context creation listeners run) would be created under-partitioned and a later
     * {@code createTopic} with the intended count would fail with a 409 forever. Calling this before every produce
     * guarantees creation precedes production. {@link Map#computeIfAbsent} serializes concurrent first-callers for the
     * same topic; a failed create is not cached and the exception propagates to the caller.
     */
    public void ensureTopicCreated(JfbusTopicConfig config) {
        provisionedTopics.computeIfAbsent(config.getTopicId(), topicId -> {
            jfbusEventingService.createTopic(topicId, config.getPartitionCount(), true);
            return Boolean.TRUE;
        });
    }

    public <T> byte[] serialize(EventEnvelope<T> eventEnvelope) {
        return serializationService.toBytes(eventEnvelope);
    }

    public <T> void publish(EventEnvelope<T> eventEnvelope, JfbusTopicConfig topicConfig, byte[] payload)
            throws Exception {
        Map<String, String> headers = buildTopLevelHeaders(eventEnvelope);
        jfbusEventingService.sendEvent(topicConfig.getTopicId(), eventEnvelope.subject(), payload, headers);
    }

    public <T> Map<String, String> buildTopLevelHeaders(EventEnvelope<T> eventEnvelope) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_SPEC_VERSION, eventEnvelope.specVersion());
        headers.put(HEADER_ID, eventEnvelope.id());
        headers.put(HEADER_TYPE, eventEnvelope.type().getType());
        headers.put(HEADER_SOURCE, eventEnvelope.source());
        headers.put(HEADER_TIME, eventEnvelope.time());
        headers.put(HEADER_SUBJECT, eventEnvelope.subject());
        headers.put(HEADER_CONTENT_TYPE, eventEnvelope.dataContentType());
        headers.put(HEADER_DATA_SCHEMA, eventEnvelope.dataSchema());
        return headers;
    }
}
