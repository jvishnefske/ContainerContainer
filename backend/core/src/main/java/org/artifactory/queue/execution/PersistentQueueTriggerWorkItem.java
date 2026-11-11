package org.artifactory.queue.execution;

import jakarta.annotation.Nonnull;
import lombok.NonNull;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.workqueue.WorkItem;

import java.util.Objects;

/**
 * Passing PersistentQueue trigger through the work queue to allow control of the concurrent running queues number.
 * using the ConstantValues.persistentQueueTriggerNumberOfThread value.
 *
 * @author avishayh
 */
public class PersistentQueueTriggerWorkItem extends WorkItem {

    private final PersistentQueue queue;

    public PersistentQueueTriggerWorkItem(@NonNull PersistentQueue queue) {
        this.queue = queue;
    }

    public PersistentQueue getQueue() {
        return queue;
    }

    @Nonnull
    @Override
    public String getUniqueKey() {
        return queue.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        PersistentQueueTriggerWorkItem that = (PersistentQueueTriggerWorkItem) o;
        return Objects.equals(queue.getName(), that.queue.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(queue.getName());
    }

    /**
     * Display the queue name in logs
     */
    @Override
    public String toString() {
        return "PersistentQueueTriggerWorkItem{" + queue.getName() + '}';
    }
}
