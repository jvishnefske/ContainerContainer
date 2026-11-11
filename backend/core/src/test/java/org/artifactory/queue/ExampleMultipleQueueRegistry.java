package org.artifactory.queue;

import org.artifactory.common.repo.PackageType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExampleMultipleQueueRegistry implements QueueConfigurationRegistrar {

    private final List<String> destinations;

    public ExampleMultipleQueueRegistry(String... destinations) {
        this.destinations = List.of(destinations);
    }

    @Override
    public PackageType getPackageType() {
        return PackageType.MAVEN;
    }

    @Override
    public List<QueueConfiguration> getConfigurations(String repoKey) {
        List<QueueConfiguration> configurations = new ArrayList<>();
        for (String destination : destinations) {
            QueueConfiguration configuration = QueueConfiguration.builder()
                    .name("test-maven")
                    .cursorType(QueueCursorType.FEDERATED_REPLICATION)
                    .variant(destination)
                    .implementor((events, key) -> {})
                    .build();
            configurations.add(configuration);
        }
        return configurations;
    }


}
