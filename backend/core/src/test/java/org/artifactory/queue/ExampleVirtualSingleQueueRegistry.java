package org.artifactory.queue;

import org.artifactory.common.repo.RepoType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ExampleVirtualSingleQueueRegistry implements QueueConfigurationRegistrar {

    private final QueueConfiguration configuration;

    public ExampleVirtualSingleQueueRegistry() {
        configuration = QueueConfiguration.builder()
                .name("test")
                .cursorType(QueueCursorType.INCREMENTAL_INDEX)
                .implementor((events, key) -> {})
                .build();
    }

    @Override
    public Set<RepoType> getRepoTypes() {
        return Set.of(RepoType.VIRTUAL);
    }

    @Override
    public List<QueueConfiguration> getConfigurations(String repoKey) {
        return List.of(configuration);
    }

}
