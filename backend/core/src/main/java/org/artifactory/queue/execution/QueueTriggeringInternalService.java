package org.artifactory.queue.execution;

import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.spring.ReloadableBean;

import static org.artifactory.queue.execution.PersistentQueueWorkQueueInfoConfig.PERSISTENT_QUEUE_TRIGGERING_WQ_NAME;

public interface QueueTriggeringInternalService extends ReloadableBean {

    @Async(authenticateAsSystem = true, delayUntilAfterCommit = true, workQueue = @WorkQueueConfig(name =
            PERSISTENT_QUEUE_TRIGGERING_WQ_NAME))
    void triggerQueue(PersistentQueueTriggerWorkItem persistentQueueWorkItem);

}
