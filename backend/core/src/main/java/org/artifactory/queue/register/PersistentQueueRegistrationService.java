package org.artifactory.queue.register;

import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.event.queue.QueueService;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.spring.RepoConfigChangeListener;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Queue API Registration interface used by the other Queue services
 *
 * @author Avishay Halpren
 */
public interface PersistentQueueRegistrationService
        extends QueueService, ReloadableBean, RepoConfigChangeListener {

    void register(String repoKey, QueueConfiguration configuration, PersistentQueue persistentQueue);

    /**
     * Registration of a repoKey:
     * - creates queues for the specific repo
     */
    void registerRepository(String repoKey);

    /**
     * Removes registration of a repoKey:
     * - removes and stop queues for the specific repo
     * - cancel triggered jobs
     * - deletes the marker of the queue
     */
    void deRegisterRepository(String repoKey);

    Collection<PersistentQueue> getQueues(String repoKey, QueueCursorType cursorType);

    Collection<PersistentQueue> getQueues(Set<String> reposKey);

    Collection<PersistentQueue> getAllQueues();

    /**
     * created new queue with for different variant with same configuration of the old queue
     *
     * @param repoKey         the repo key that the queue belongs to.
     * @param oldVariant      the variant of the queue we want to copy
     * @param newVariant      the variant of the queue we want to create
     * @param queueCursorType the type of the queue we want to copy
     * @param implementor     an implementor function that handles queue events
     */
    void copyQueue(String repoKey, String oldVariant, String newVariant, QueueCursorType queueCursorType,
            QueueEventImplementor implementor);

    Optional<PersistentQueue> findQueue(String repoKey, QueueCursorType type, String variant);

    /**
     *
     * @return all the queues that are marked to be triggered always and not depending on specific repo operations
     */
    Collection<PersistentQueue> getConstantQueues();

    /**
     * Returns the status of the queue for the provided cursor type.
     * Works for constant queues only
     * @param cursorType - The cursor type of the constant queue
     * @return the {@link NodeEventCursorStatus}
     */
    NodeEventCursorStatus getConstantQueueState(String cursorType);

    /**
     * Updates the given queue of the cursor type to the provided status and time
     * @param cursorType - The type of the queue
     * @param status - The status to be set
     * @param timeToReset - The time to be set
     */
    void updateConstantQueue(QueueCursorType cursorType, NodeEventCursorStatus status, long timeToReset);

    /**
     * Updates the status of the queue for the provided cursor type.
     * Works for constant queues only
     * @param cursorType - The cursor type of the constant queue
     * @param status - The status to be set
     */
    void updateConstantQueueStatus(QueueCursorType cursorType, NodeEventCursorStatus status);
}
