package org.artifactory.queue.model;

import lombok.Data;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.queue.QueueEventException;
import org.artifactory.queue.QueueEventImpl;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.artifactory.queue.model.PersistentQueueRunningResult.failure;
import static org.artifactory.queue.model.PersistentQueueRunningResult.success;

/**
 * inner class represent the context of a group
 *
 * @author avishayh
 */
@Data
@Slf4j
public class PersistentQueueGroupContext {

    @Delegate
    private final List<QueueEventImpl> events = new ArrayList<>();
    private String uniqKey;
    private Future<Optional<Exception>> futureException;

    public PersistentQueueGroupContext(String uniqKey) {
        this.uniqKey = uniqKey;
    }

    /**
     * synchronizing on the future and finds the failed eventId in case exist
     * if no failure returns Long.MAX_VALUE
     * if QueueEventException was thrown we will know the specific eventId
     * if other exception was thrown OR job was interrupted returns -1
     */
    public PersistentQueueRunningResult getResult() {
        Exception exception = waitForJob();
        if (exception == null) {
            //no exception -> return success
            return success();
        }
        log.error("Exception in job {}, msg: {}", uniqKey, exception.getMessage(), exception);

        if (exception instanceof QueueEventException) {
            QueueEventException qee = (QueueEventException) exception;
            QueueEventImpl failedQueueEvent = (QueueEventImpl) qee.getFailedEvent();
            long marker = !Objects.isNull(failedQueueEvent.getOriginalEvent()) ?
                    failedQueueEvent.getOriginalEvent().getEventId() : failedQueueEvent.getEventId();
            return failure(marker, qee.toString(), qee.isOmitError());
        }
        //exception other than QueueEventException (e.g: RuntimeException or InterruptedException while checking
        //failure) -> we can't be sure which event failed, returning -1 so marker cannot advance.
        return failure(-1, exception.toString(), false);
    }

    /**
     * waiting on the future and returns any exception found
     */
    @Nullable
    private Exception waitForJob() {
        try {
            Optional<Exception> exception = futureException.get();
            return exception.orElse(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            return e;
        }
    }
}
