package org.artifactory.queue.monitor;

import org.artifactory.event.queue.QueueEvent;
import org.artifactory.queue.services.PersistentQueue;

import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Interface for monitoring the persistent queue
 *
 * @author asafz
 */
public interface PersistentQueueMonitor {

    void start(PersistentQueue persistentQueue);

    void end(String queueName, PersistentQueue.TriggerResult triggerResult);

    void addFilterEvents(String queueName, List<? extends QueueEvent> eventsAfterConfigurationFilter,
            List<? extends QueueEvent> eventsAfterReductionFilter);

    void loadedEvents(String queueName, List<? extends QueueEvent> events);

    void startMarker(String queueName, long startMarker);

    void endMarker(String queueName, long endMarker);

    void beforeImplementor(String queueName, String key, int eventsSize);

    void afterImplementor(String queueName, String key, @Nullable Exception exception);

    void includedErrors(String queueName, Collection<Long> errors);
}
