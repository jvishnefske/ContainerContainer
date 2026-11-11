package org.artifactory.queue.register;

import org.artifactory.event.queue.QueueConfiguration;

public interface QueueConfigurationRegistrationService {

    void addConfiguration(QueueConfiguration configuration);
}
