package org.artifactory.jfbus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

/**
 * {@code @Async} is applied by the Spring proxy at runtime, so a plain unit test only exercises the delegation
 * contract: this bean forwards to {@link UemV2Publisher#publish}. The off-the-dispatch-pool behaviour itself is a
 * property of the async advice (mirroring V1's {@code JfbusEventingService.sendEvent}) and is not unit-testable here.
 */
@ExtendWith(MockitoExtension.class)
class UemV2AsyncPublisherTest {

    @Mock
    private UemV2Publisher uemV2Publisher;

    @InjectMocks
    private UemV2AsyncPublisher subject;

    @Test
    void publishDelegatesToPublisher() {
        UemV2PublishRequest request = UemV2PublishRequest.builder()
                .source("artifactory//node-1")
                .type("artifactory.repository.created")
                .subject("repos/my-repo")
                .dataSchema("https://schema.example.com/v1")
                .entityType("repository")
                .data(Map.of("repoKey", "my-repo"))
                .build();

        subject.publish(request);

        verify(uemV2Publisher).publish(request);
    }
}
