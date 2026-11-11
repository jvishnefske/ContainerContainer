package org.artifactory.queue.error;

import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.queue.register.RepoAndCursor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author avishayh
 */
public interface PersistentQueueErrorService {

    /**
     * get error count for this queue
     */
    int getErrorCount(PersistentQueue queue);

    /**
     * retrieve previous errors for this queue
     */
    @Nonnull
    List<EventAndErrorInfo> getErrors(PersistentQueue queue);

    /**
     * process errors from the queue, take necessary db action: add,update or delete
     * @param queue the queue
     * @param runningContext the running context with the events and failures data
     */
    void processErrors(PersistentQueue queue, PersistentQueueRunningContext runningContext);

    /**
     * delete all errors relevant for this queue
     */
    void deleteErrors(PersistentQueue queue);

    /**
     * delete the specified errors
     */
    @Transactional
    void deleteErrors(Collection<Long> errors);

    /**
     * Return set of erroneous repos
     */
    @Nonnull
    Set<RepoAndCursor> getErroneousQueues();

    void triggerRecovery(PersistentQueue queue);
}
