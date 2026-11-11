package org.artifactory.queue.execution;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.register.PersistentQueueRegistrationService;
import org.artifactory.queue.register.RepoAndCursor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.Reloadable;
import org.jfrog.common.ClockUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.queue.services.PersistentQueue.TriggerResult;

/**
 * @author Avishay Halpren
 */
@Slf4j
@Service
@Reloadable(beanClass = QueueTriggeringInternalService.class, initAfter = {TaskService.class,
        InternalRepositoryService.class}, listenOn = CentralConfigKey.none)
public class QueueTriggeringServiceImpl implements QueueTriggeringService, QueueTriggeringInternalService {

    /**
     * In memory map which save the last trigger time for a specific queue
     * we use this to reduce the # of triggers when the PersistentQueueTriggerJob kicks in.
     * we don't have to trigger a queue if it was already triggered in the past X minutes.
     */
    private final Map<String, Long> lastTriggeredTime = Maps.newConcurrentMap();

    private final PersistentQueueRegistrationService queueRegistrationService;
    private final TaskService taskService;
    private final PersistentQueueErrorService errorService;

    public QueueTriggeringServiceImpl(PersistentQueueRegistrationService queueRegistrationService,
            TaskService taskService, PersistentQueueErrorService errorService) {
        this.queueRegistrationService = queueRegistrationService;
        this.taskService = taskService;
        this.errorService = errorService;
    }

    @Override
    public void init() {
        //create a background check for cases where queue was stopped in the middle and events are left unhandled
        int triggerTime = Math.min(ConstantValues.persistentQueueCheckTriggerTimeSec.getInt(),
                ConstantValues.persistentQueueErrorsCheckTriggerTimeSec.getInt());
        log.debug("registering PersistentQueueTriggerJob to run every {} minutes", triggerTime);
        Task persistentQueueCheckTask = TaskUtils.createRepeatingTask(PersistentQueueTriggerJob.class,
                TimeUnit.SECONDS.toMillis(triggerTime),
                TimeUnit.SECONDS.toMillis(0));
        taskService.startTask(persistentQueueCheckTask, false);
    }

    @Override
    public void destroy() {
        taskService.cancelTasks(PersistentQueueTriggerJob.class, true);
    }

    @Override
    public void triggerQueuesByTime() {
        AtomicReference<Set<RepoAndCursor>> errors = new AtomicReference<>();
        queueRegistrationService.getAllQueues().forEach(queue -> {
            //if not triggered before return 0 in-order to trigger now
            long lastTriggerTime = lastTriggeredTime.getOrDefault(queue.getName(), 0L);
            long now = ClockUtils.epochMillis();
            //check how much time has passed from the last triggered time
            //in case it is bigger than the threshold that was set - need to trigger again.
            long timePassedFromLastTriggerInMs = now - lastTriggerTime;

            if (timePassedFromLastTriggerInMs >=
                    TimeUnit.SECONDS.toMillis(ConstantValues.persistentQueueCheckTriggerTimeSec.getLong())) {
                log.debug("Triggering queue {} by time", queue.getName());
                scheduleAsyncQueueTrigger(queue);
            } else if (timePassedFromLastTriggerInMs >=
                    TimeUnit.SECONDS.toMillis(ConstantValues.persistentQueueErrorsCheckTriggerTimeSec.getLong())) {
                if (errors.get() == null) {
                    errors.set(errorService.getErroneousQueues());
                }
                if (errors.get().contains(new RepoAndCursor(queue.getRepoKey(), queue.getQueueConfiguration()
                        .getCursorType()))) {
                    log.debug("Triggering queue {} by time due to previous errors", queue.getName());
                    scheduleAsyncQueueTrigger(queue);
                }
            } else {
                log.debug("Queue {} was triggered recently, skipping", queue.getName());
            }
        });
    }

    @Override
    public void triggerQueues(String repoKey, QueueCursorType cursorType) {
        Collection<PersistentQueue> queues = queueRegistrationService.getQueues(repoKey, cursorType);
        if (queues.isEmpty()) {
            log.warn("Cannot find any queue for repo {} and cursor {}", repoKey, cursorType);
            return;
        }
        queues.forEach(this::scheduleAsyncQueueTrigger);
    }

    @Override
    public void triggerQueues(Set<String> reposKey) {
        queueRegistrationService.getQueues(reposKey).forEach(this::scheduleAsyncQueueTrigger);
    }

    @Override
    public void triggerConstantQueues() {
        queueRegistrationService.getConstantQueues().forEach(this::scheduleAsyncQueueTrigger);
    }

    /**
     * Scheduling an async queue triggering with a new workQueue item.
     */
    void scheduleAsyncQueueTrigger(PersistentQueue queue) {
        log.debug("Scheduling async queue triggering for: {}", queue.getName());
        QueueConfiguration configuration = queue.getQueueConfiguration();
        configuration.getTriggerDecorator()
                .trigger(() -> getInternalMe().triggerQueue(new PersistentQueueTriggerWorkItem(queue))).run();
    }

    @Override
    public void triggerQueue(PersistentQueueTriggerWorkItem workItem) {
        //saving the last trigger time to help cron job to decide if needs to call the trigger
        long triggerTime = ClockUtils.epochMillis();

        PersistentQueue queue = workItem.getQueue();
        String queueName = queue.getName();
        //triggering the queue
        boolean triggerAgain;
        do {
            lastTriggeredTime.put(queueName, triggerTime);
            log.debug("triggering queue {}", queue.getName());
            TriggerResult result = queue.trigger();
            triggerAgain = shouldTriggerAgain(queue, triggerTime, result);
        } while (triggerAgain);
    }

    private boolean shouldTriggerAgain(PersistentQueue queue, long triggerTime,
            TriggerResult result) {
        boolean finishedAll = result.equals(TriggerResult.FINISHED_ALL);
        boolean finishedWithHandledError = result.equals(TriggerResult.FINISHED_WITH_HANDLED_ERROR);
        if (!finishedAll && !finishedWithHandledError) {
            //for status other than this 2 we do not need to retry
            log.debug("queue {} is not triggering again. result is {}", queue.getName(), result);
            return false;
        }
        //checking trigger time has not passed max lock threshold
        long maxLockLeaseTime =
                TimeUnit.MINUTES.toMillis(ConstantValues.persistentQueueMaxLockLeaseTimeMinutes.getLong());
        long timePassedFromLockLease = ClockUtils.epochMillis() - triggerTime;
        boolean triggerTimePassedThreshold = timePassedFromLockLease > maxLockLeaseTime;
        if (!triggerTimePassedThreshold) {
            log.debug("queue {} is triggering again. result is {}", queue.getName(), result);
            return true;
        }
        //we might still have more work in the queue, so we use workQueue to trigger another run
        //this will release the db lock for the current queue and let other pending jobs run as well
        scheduleAsyncQueueTrigger(queue);
        log.debug("queue {} trigger added to queue due to time. result is {}", queue.getName(), result);
        return false;
    }

    private QueueTriggeringInternalService getInternalMe() {
        return ContextHelper.get().beanForType(QueueTriggeringInternalService.class);
    }
}
