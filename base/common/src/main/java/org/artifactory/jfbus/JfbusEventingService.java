package org.artifactory.jfbus;

import com.google.common.collect.Maps;
import jakarta.ws.rs.core.MediaType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.async.Async;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.jfrog.eventing.facade.ConsumeOptions;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.MessageBatch;
import org.jfrog.eventing.facade.TopicCreationOptions;
import org.jfrog.eventing.facade.TopicId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.artifactory.jfbus.JfbusConstants.HEADER_CONTENT_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_ID;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SOURCE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SPEC_VERSION;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TIME;
import static org.jfrog.common.ExceptionLogUtils.debugOrError;

/**
 * Generic JFBus eventing service for sending events to JFBus topics.
 * This service handles the core event sending infrastructure and generic header population.
 *
 * @author IgorG
 */
@Slf4j
@Service
public class JfbusEventingService {

    public static final boolean IS_OUTBOX_ENABLED = ConstantValues.jfbusUseOutboxEnabled.getBoolean();
    public enum JfbusOperationMode { OFF, SHADOW, ACTIVE }

    private final Optional<EventingFacade> eventingClient;
    private final AccessService accessService;
    private final ArtifactoryHome artifactoryHome;

    public JfbusEventingService(Optional<EventingFacade> eventingClient) {
        this.eventingClient = eventingClient;
        accessService = ContextHelper.get().beanForType(AccessService.class);
        artifactoryHome = ContextHelper.get().getArtifactoryHome();
    }

    public void createTopic(@NonNull TopicId topicId, int partitions, boolean skipIfExists) {
        TopicCreationOptions options = TopicCreationOptions.builder()
                .skipIfExists(skipIfExists)
                .partitions(partitions)
                .build();
        eventingClient.ifPresent(eventingFacade -> {
            log.info("Creating JFBus topic '{}' with {} partitions (skipIfExists={})",
                    topicId.getFullyQualifiedName(), partitions, skipIfExists);
            eventingFacade.createTopic(topicId, options);
        });
    }

    /**
     * @return {@code true} if the JFBus {@link EventingFacade} bean is present so produce/consume can run;
     *         {@code false} when JFBus is not configured (callers that need a delivery guarantee should treat
     *         this as failure, same as {@link #consumeTopic} returning {@code false} when the client is absent).
     */
    public boolean isConfigured() {
        return eventingClient.isPresent();
    }

    /**
     * Subscribes to a topic and delivers batches to the handler (blocking behaviour depends on broker).
     *
     * @return {@code false} if JFBus is not configured (no subscription); {@code true} after
     *         {@link EventingFacade#consume} returns (normally blocks until the subscription ends)
     */
    public boolean consumeTopic(TopicId topicId, String subscriptionKey, ConsumeOptions options,
            Consumer<MessageBatch> batchHandler) throws Exception {
        if (eventingClient.isEmpty()) {
            return false;
        }
        eventingClient.get().consume(topicId, subscriptionKey, options, batchHandler);
        return true;
    }

    /**
     * Sends an event synchronously (same pipeline as {@link #sendEvent} including headers).
     * Use when the caller must observe send completion or errors inline (e.g. consumer republish).
     * When {@link #isConfigured()} is {@code false}, returns without producing (no exception);
     * callers that require delivery must check {@link #isConfigured()} first or treat that as failure.
     */
    public void sendEventBlocking(TopicId topicId, String key, byte[] payload, Map<String, String> headers)
            throws Exception {
        doProduce(topicId, key, payload, headers);
    }

    /**
     * Sends an event to the specified JFBus topic.
     *
     * @param topicId the topic to send the event to
     * @param key     the message key
     * @param payload the event payload as byte array
     * @param headers additional headers to include (generic headers will be added automatically)
     * @throws Exception if sending fails
     */
    @Async
    public void sendEvent(TopicId topicId, String key, byte[] payload, Map<String, String> headers) throws Exception {
        doProduce(topicId, key, payload, headers);
    }

    private void doProduce(TopicId topicId, String key, byte[] payload, Map<String, String> headers)
            throws Exception {
        if (eventingClient.isEmpty()) {
            log.debug("JFBus Eventing client is not configured, skipping sending event to topic {}", topicId);
            return;
        }
        try {
            Map<String, String> allHeaders = Maps.newHashMap(headers);
            populateGenericHeaders(allHeaders);
            log.trace("JFBus event headers for topic {}: {}", topicId, allHeaders);
            Message message = Message.builder()
                    .key(key)
                    .headers(allHeaders)
                    .payload(payload)
                    .build();
            if (IS_OUTBOX_ENABLED) {
                eventingClient.get().produceViaOutbox(List.of(message), topicId);
            } else {
                eventingClient.get().produce(List.of(message), topicId);
            }
            log.trace("Sent JFBus event to topic {} with key {}", topicId, key);
        } catch (Exception e) {
            debugOrError(log, "JFBus Service Failed to send event to topic " + topicId + " key: " + key, e);
            throw e;
        }
    }

    private void populateGenericHeaders(Map<String, String> headers) {
        headers.putIfAbsent(HEADER_SPEC_VERSION, "1.0");
        headers.putIfAbsent(HEADER_ID, String.valueOf(java.util.UUID.randomUUID()));
        headers.putIfAbsent(HEADER_SOURCE, buildSourceHeader());
        headers.putIfAbsent(HEADER_TIME, String.valueOf(System.currentTimeMillis()));
        headers.putIfAbsent(HEADER_CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
    }

    String buildSourceHeader() {
        return "artifactory://" + accessService.getArtifactoryServiceId().getFormattedName() + "@" +
                artifactoryHome.getServerId();
    }
}
