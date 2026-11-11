package org.artifactory.jfbus;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.TopicId;
import org.jfrog.eventing.messagebus.unified.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UemV2PublisherConfigurationTest.TestConfig.class)
@TestPropertySource(properties = {
        "artifactory.jfbus.working.mode=OFF",
        "artifactory.uemv2.publisher.enabled=false"
})
class UemV2PublisherConfigurationTest extends ArtifactoryHomeBoundJUnitTest {

    @Configuration
    @Import({UemV2PublisherConfiguration.class, DisabledUemV2PublisherConfiguration.class})
    static class TestConfig {
    }

    @Autowired
    ApplicationContext context;

    @Test
    void disabledPublisherBeanIsDisabledUemV2Publisher() {
        assertThat(context.getBean(UemV2Publisher.class)).isInstanceOf(DisabledUemV2Publisher.class);
    }

    @Test
    void disabledPublisherShouldIgnorePublishAndTopicCreationWhenFeatureIsOff() {
        // R-LOG-16: The disabled flag path must remain a no-op for callers.
        UemV2Publisher publisher = context.getBean(UemV2Publisher.class);

        assertThatCode(() -> {
            publisher.publish(createRequest());
            publisher.createTopic("repository", 1);
        }).doesNotThrowAnyException();
    }

    @Test
    void enabledPublisherBeansShouldPublishThroughArtifactoryNamespaceWhenCreatedByConfiguration() throws Exception {
        // R-LOG-16: The enabled flag path must wire a real publisher with the expected topic namespace.
        // Pin outbox on so the test asserts the expected produceViaOutbox path consistently.
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.jfbusUseOutboxEnabled, "true");
        UemV2PublisherConfiguration configuration = new UemV2PublisherConfiguration();
        EventingFacade eventingFacade = mock(EventingFacade.class);
        JfbusEventingService jfbusEventingService = mock(JfbusEventingService.class);
        EventPublisher eventPublisher = configuration.uemV2EventPublisher(eventingFacade);
        UemV2Publisher publisher = configuration.uemV2Publisher(eventPublisher, jfbusEventingService);

        publisher.publish(createRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TopicId> topicCaptor = ArgumentCaptor.forClass(TopicId.class);
        // Default config has the outbox enabled, so v2 publishes through the transactional outbox path.
        verify(eventingFacade).produceViaOutbox(messagesCaptor.capture(), topicCaptor.capture(), any());
        assertThat(messagesCaptor.getValue()).hasSize(1);
        assertThat(topicCaptor.getValue().getFullyQualifiedName()).isEqualTo("artifactory.mb_repository");
    }

    private static UemV2PublishRequest createRequest() {
        return UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();
    }
}
