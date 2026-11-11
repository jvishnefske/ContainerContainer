package org.artifactory.jfbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.eventing.facade.PublishOptions;
import org.jfrog.eventing.facade.TopicId;
import org.jfrog.eventing.messagebus.unified.EventPublisher;
import org.jfrog.eventing.uem.Event;
import org.jfrog.eventing.uem.EventBuilder;

import static org.jfrog.common.ExceptionLogUtils.debugOrError;

@Slf4j
public class UemV2PublisherImpl implements UemV2Publisher {

    private static final String MESSAGEBUS_TOPIC_PREFIX = "mb_";

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final JfbusEventingService jfbusEventingService;
    // Sampled once at construction (the publisher is a singleton), mirroring V1's static-final
    // JfbusEventingService.IS_OUTBOX_ENABLED so both paths read the outbox toggle at the same point.
    private final boolean outboxEnabled;

    public UemV2PublisherImpl(EventPublisher eventPublisher,
                               ObjectMapper objectMapper,
                               JfbusEventingService jfbusEventingService) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.jfbusEventingService = jfbusEventingService;
        this.outboxEnabled = ConstantValues.jfbusUseOutboxEnabled.getBoolean();
    }

    @Override
    public void publish(UemV2PublishRequest request) {
        try {
            EventBuilder builder = Event.builder()
                    .dataMapper(objectMapper)
                    .source(request.source())
                    .type(request.type())
                    .subject(request.subject())
                    .dataSchema(request.dataSchema())
                    .data(request.data());
            if (request.id() != null) {
                builder.id(request.id());
            }
            if (request.partitionKeyOverride() != null) {
                builder.key(request.partitionKeyOverride());
            }
            request.eventHeaders().forEach(builder::eventHeader);
            request.transportHeaders().forEach(builder::transportHeader);
            Event event = builder.build();
            // When the transactional outbox is enabled, route through it so the event is tied to the business
            // DB transaction and survives broker hiccups; otherwise publish directly. Mirrors V1 behaviour.
            if (outboxEnabled) {
                eventPublisher.publishViaOutbox(event, PublishOptions.defaults(), request.entityType());
            } else {
                eventPublisher.publish(event, PublishOptions.defaults(), request.entityType());
            }
        } catch (Exception e) {
            // Match V1 (JfbusEventingService.doProduce): route through debugOrError so a broker/outbox outage
            // does not flood ERROR + full stack trace per event. Transient faults are downgraded to debug.
            debugOrError(log, "UEM v2 publish failed: type='" + request.type() + "' subject='"
                    + request.subject() + "'", e);
        }
    }

    @Override
    public void createTopic(String entityType, int partitions) {
        TopicId topicId = new TopicId(ArtifactoryHome.ARTIFACTORY_SERVICE_NAME, MESSAGEBUS_TOPIC_PREFIX + entityType);
        jfbusEventingService.createTopic(topicId, partitions, true);
    }
}
