package org.artifactory.queue.register;

import org.artifactory.event.queue.QueueConfiguration;

/**
 * Interface for registering global queue configurations.
 * It is enough to implement this interface to register a global queue configuration, the registration will be done
 * automatically by the infrastructure.
 * @author Mor Merhav
 */
public interface GlobalQueueConfigurationRegistrar {

    QueueConfiguration getQueueConfiguration();

    String getQueueName();

    default boolean shouldRegister() {
        return true;
    }
}
