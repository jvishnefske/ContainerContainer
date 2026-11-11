package org.artifactory.lock.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.service.NativeDbLocksActivationService;
import org.artifactory.schedule.SingletonTaskAlreadyScheduledException;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.util.AolUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Schedules {@link NativeDbLocksActivationJob} when native-lock activation is pending in AUTO mode on SaaS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NativeDbLocksActivationJobScheduler implements ContextReadinessListener {

    private final NativeDbLocksActivationService statusService;
    private final TaskService taskService;

    @Override
    public void onContextReady() {
        if (!ConstantValues.dbLockNativeActivationJobEnabled.getBoolean()) {
            log.debug("Native DB locks auto-activation job is disabled");
            return;
        }
        if (!AolUtils.isAol()) {
            log.debug("Skipping native DB locks auto-activation scheduling on self-hosted");
            return;
        }
        if (statusService.resolveLocalStatus() != NativeDbLocksMechanismStatus.READY_NOT_IN_USE) {
            return;
        }
        scheduleActivationJobIfNeeded();
    }

    private void scheduleActivationJobIfNeeded() {
        if (taskService.hasActiveTasks(task -> NativeDbLocksActivationJob.class.equals(task.getType()))) {
            log.info("Native DB locks activation job is already scheduled");
            return;
        }
        try {
            long intervalMillis = TimeUnit.SECONDS.toMillis(
                    ConstantValues.dbLockNativeActivationJobIntervalSec.getLong());
            long initialDelayMillis = TimeUnit.SECONDS.toMillis(
                    ConstantValues.dbLockNativeActivationJobInitialDelaySec.getLong());
            Task activationJob = TaskUtils.createRepeatingTask(
                    NativeDbLocksActivationJob.class, intervalMillis, initialDelayMillis);
            taskService.startTask(activationJob, false);
            log.info("Scheduled native DB locks activation job with initial delay of {} " +
                            "seconds and interval of {} seconds",
                    ConstantValues.dbLockNativeActivationJobInitialDelaySec.getLong(),
                    ConstantValues.dbLockNativeActivationJobIntervalSec.getLong());
        } catch (SingletonTaskAlreadyScheduledException e) {
            log.debug("Native DB locks activation job is already scheduled on this node");
        } catch (Exception e) {
            log.error("Failed to schedule native DB locks activation job", e);
        }
    }
}
