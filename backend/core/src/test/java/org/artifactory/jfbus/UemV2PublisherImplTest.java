package org.artifactory.jfbus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.PublishOptions;
import org.jfrog.eventing.facade.TopicId;
import org.jfrog.eventing.messagebus.unified.EventPublisher;
import org.jfrog.eventing.uem.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UemV2PublisherImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private EventingFacade eventingFacade;

    private JfbusEventingService jfbusEventingService;

    private UemV2PublisherImpl subject;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jfbusEventingService = mock(JfbusEventingService.class);
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Wire-format assertions below verify the direct produce() path; pin the outbox off so they are
        // deterministic. Outbox routing itself is covered by the dedicated routing tests.
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.jfbusUseOutboxEnabled, "false");
        EventPublisher publisher = EventPublisher.builder()
                .facade(eventingFacade)
                .namespace("artifactory")
                .build();
        subject = new UemV2PublisherImpl(publisher, objectMapper, jfbusEventingService);
    }

    private static UemV2PublishRequest.UemV2PublishRequestBuilder sampleRequest() {
        return UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"));
    }

    @Test
    void publishPopulatesEventEnvelopeFromRequest() throws Exception {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();

        subject.publish(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(TopicId.class), any(PublishOptions.class));
        Message message = messagesCaptor.getValue().getFirst();
        Map<String, String> headers = message.getHeaders();
        assertThat(headers)
                .containsEntry("ce-source", "artifactory//node-1")
                .containsEntry("ce-type", "artifactory.repository.created")
                .containsEntry("ce-subject", "repos/my-repo")
                .containsEntry("ce-dataschema", "https://schema.example.com/v1")
                .containsEntry("ce-specversion", "1.0")
                .containsEntry("ce-datacontenttype", "application/json")
                .containsKey("ce-id");
        JsonNode payload = objectMapper.readTree(message.getPayload());
        assertThat(payload.get("repoKey")).isNotNull();
    }

    @Test
    void publishDelegatesWireFormatToLibrary() throws Exception {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();

        subject.publish(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(TopicId.class), any(PublishOptions.class));
        Map<String, String> headers = messagesCaptor.getValue().getFirst().getHeaders();
        assertThat(headers)
                .containsEntry("x-schema-version", "2")
                .containsEntry("ce-specversion", "1.0")
                .containsEntry("ce-datacontenttype", "application/json");
    }

    @Test
    void publishPassesEntityTypeToPublisher() throws Exception {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();

        subject.publish(request);

        ArgumentCaptor<TopicId> topicCaptor = ArgumentCaptor.forClass(TopicId.class);
        verify(eventingFacade).produce(any(), topicCaptor.capture(), any());
        assertThat(topicCaptor.getValue().getFullyQualifiedName()).isEqualTo("artifactory.mb_repository");
    }

    @Test
    void publishDefaultsPartitionKeyToSubject() throws Exception {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();

        subject.publish(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(), any());
        assertThat(messagesCaptor.getValue().getFirst().getKey()).isEqualTo("repos/my-repo");
    }

    @Test
    void publishUsesPartitionKeyOverrideWhenSupplied() throws Exception {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .partitionKeyOverride("custom-partition-key")
                .build();

        subject.publish(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(), any());
        assertThat(messagesCaptor.getValue().getFirst().getKey()).isEqualTo("custom-partition-key");
    }

    @Test
    void publishAddsCustomHeadersWhenRequestContainsHeaders() throws Exception {
        // R-IB-1.2: Cover the non-empty header branches in the publish envelope.
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .eventHeader("ce-custom", "event-value")
                .transportHeader("x-transport", "transport-value")
                .build();

        subject.publish(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(), any());
        assertThat(messagesCaptor.getValue().getFirst().getHeaders())
                .containsEntry("eh-ce-custom", "event-value")
                .containsEntry("x-transport", "transport-value");
    }

    @Test
    void publishShouldSwallowBrokerExceptionWhenFacadeFails() throws Exception {
        // R-ERR-05: Broker failures must not propagate to fire-and-forget callers.
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();
        doThrow(new RuntimeException("broker down"))
                .when(eventingFacade)
                .produce(any(), any(TopicId.class), any(PublishOptions.class));

        assertThatCode(() -> subject.publish(request)).doesNotThrowAnyException();
    }

    @Test
    void createTopicShouldCreateArtifactoryMessageBusTopicAndSkipExisting() {
        // R-LOG-01: Topic layout construction must preserve the Artifactory message-bus namespace.
        subject.createTopic("repository", 3);

        ArgumentCaptor<TopicId> topicCaptor = ArgumentCaptor.forClass(TopicId.class);
        verify(jfbusEventingService).createTopic(topicCaptor.capture(), eq(3), eq(true));
        assertThat(topicCaptor.getValue().getFullyQualifiedName()).isEqualTo("artifactory.mb_repository");
    }

    @Test
    void publishUsesDirectPublishWhenOutboxDisabled() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.jfbusUseOutboxEnabled, "false");
        EventPublisher mockPublisher = mock(EventPublisher.class);
        UemV2PublisherImpl publisher = new UemV2PublisherImpl(mockPublisher, objectMapper, jfbusEventingService);

        publisher.publish(sampleRequest().build());

        verify(mockPublisher).publish(any(Event.class), any(PublishOptions.class), eq("repository"));
    }

    @Test
    void publishUsesOutboxWhenOutboxEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.jfbusUseOutboxEnabled, "true");
        EventPublisher mockPublisher = mock(EventPublisher.class);
        UemV2PublisherImpl publisher = new UemV2PublisherImpl(mockPublisher, objectMapper, jfbusEventingService);

        publisher.publish(sampleRequest().build());

        verify(mockPublisher).publishViaOutbox(any(Event.class), any(PublishOptions.class), eq("repository"));
    }

    @Test
    void publishForwardsOriginatingIdWhenSupplied() throws Exception {
        subject.publish(sampleRequest().id("evt-123").build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produce(messagesCaptor.capture(), any(TopicId.class), any(PublishOptions.class));
        Map<String, String> headers = messagesCaptor.getValue().getFirst().getHeaders();
        // id is forwarded as ce-id (the dedup key); without it the library would mint a fresh id.
        assertThat(headers).containsEntry("ce-id", "evt-123");
    }

}
