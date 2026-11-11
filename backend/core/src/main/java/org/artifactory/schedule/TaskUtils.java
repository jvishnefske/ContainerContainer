/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.schedule;

import jakarta.annotation.Nonnull;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.InternalStringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import static org.artifactory.schedule.Task.MAX_LOCK_NAME_PREFIX;

/**
 * User: freds
 * Date: 7/6/11
 * Time: 2:54 PM
 */
public abstract class TaskUtils {
    public static final String DESC_MANUAL_TRIGGER = " (manual trigger)";
    static final String QUARTZ_CMD = "Quartz command ";

    private TaskUtils() {
        // utility class
    }

    public static Task createManualTask(@Nonnull Class<? extends QuartzCommand> commandClass, long initialDelay) {
        return createManualTask(commandClass, initialDelay, null);
    }

    public static Task createManualTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            long initialDelay, String externalId, String description) {
        checkCommandClass(quartzCommand, true);

        description = getTaskDescription(quartzCommand, description) + DESC_MANUAL_TRIGGER;

        JobDetail jobDetail = JobBuilder.newJob(quartzCommand)
                .withIdentity(generateUniqueName(quartzCommand), Task.ARTIFACTORY_GROUP)
                .withDescription(description).build();

        Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(generateUniqueName(quartzCommand))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .startAt(new Date(System.currentTimeMillis() + initialDelay)).build();

        return fillProperties(
                (InternalTask) TaskFactory.createQuartzTask(quartzCommand, trigger, jobDetail, externalId, true), true);
    }

    public static Task createManualTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            long initialDelay, String description) {
        return createManualTask(quartzCommand, initialDelay, null, description);
    }

    public static Task createRepeatingTask(@Nonnull Class<? extends QuartzCommand> commandClass,
            long interval, long initialDelay) {
        return createRepeatingTask(commandClass, interval, initialDelay, null);
    }

    public static Task createRepeatingTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            long interval, long initialDelay, String description) {
        return createRepeatingTask(quartzCommand, interval, initialDelay, null, description, false);
    }

    public static Task createRepeatingTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            long interval, long initialDelay, String description,
            boolean misfireHandlingInstructionFireNow) {
        checkCommandClass(quartzCommand, false);

        JobDetail jobDetail = JobBuilder.newJob(quartzCommand)
                .withIdentity(generateUniqueName(quartzCommand), Task.ARTIFACTORY_GROUP)
                .withDescription(getTaskDescription(quartzCommand, description)).build();

        SimpleScheduleBuilder schedulerBuilder = SimpleScheduleBuilder.simpleSchedule();
        if (misfireHandlingInstructionFireNow) {
            schedulerBuilder = schedulerBuilder.withMisfireHandlingInstructionFireNow();
        }
        if (interval > 0) {
            schedulerBuilder.repeatForever().withIntervalInMilliseconds(interval);
        }
        Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(generateUniqueName(quartzCommand))
                .withSchedule(schedulerBuilder).startAt(new Date(System.currentTimeMillis() + initialDelay)).build();

        return fillProperties(
                (InternalTask) TaskFactory.createQuartzTask(quartzCommand, trigger, jobDetail, null, false), false);
    }

    public static Task createRepeatingTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            long interval, long initialDelay, String externalId, String description,
            boolean misfireHandlingInstructionFireNow) {
        checkCommandClass(quartzCommand, false);

        JobDetail jobDetail = JobBuilder.newJob(quartzCommand)
                .withIdentity(generateUniqueName(quartzCommand), Task.ARTIFACTORY_GROUP)
                .withDescription(getTaskDescription(quartzCommand, description)).build();

        SimpleScheduleBuilder schedulerBuilder = SimpleScheduleBuilder.simpleSchedule();
        if (misfireHandlingInstructionFireNow) {
            schedulerBuilder = schedulerBuilder.withMisfireHandlingInstructionFireNow();
        }
        if (interval > 0) {
            schedulerBuilder.repeatForever().withIntervalInMilliseconds(interval);
        }
        Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(generateUniqueName(quartzCommand))
                .withSchedule(schedulerBuilder).startAt(new Date(System.currentTimeMillis() + initialDelay)).build();

        return fillProperties(
                (InternalTask) TaskFactory.createQuartzTask(quartzCommand, trigger, jobDetail, externalId, false),
                false);
    }

    public static Task createCronTask(@Nonnull Class<? extends QuartzCommand> commandClass,
            @Nonnull String cronExpression) {
        return createCronTask(commandClass, cronExpression, null);
    }

    public static Task createCronTask(@Nonnull Class<? extends QuartzCommand> quartzCommand,
            @Nonnull String cronExpression, String description) {
        return createCronTask(quartzCommand, cronExpression, null, description);
    }

    public static Task createCronTask(Class<? extends QuartzCommand> quartzCommand,
            @Nonnull String cronExpression, String externalId, String description) {
        checkCommandClass(quartzCommand, false);

        JobDetail jobDetail = JobBuilder.newJob(quartzCommand)
                .withIdentity(generateUniqueName(quartzCommand), Task.ARTIFACTORY_GROUP)
                .withDescription(getTaskDescription(quartzCommand, description)).build();

        Trigger trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(generateUniqueName(quartzCommand))
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();

        return fillProperties(
                (InternalTask) TaskFactory.createQuartzTask(quartzCommand, trigger, jobDetail, externalId, false),
                false);
    }

    private static String getTaskDescription(Class<? extends QuartzCommand> quartzCommand, String inDescription) {
        if (inDescription == null) {
            JobCommand jobCommand = quartzCommand.getAnnotation(JobCommand.class);
            return jobCommand.description();
        } else {
            return inDescription;
        }
    }

    private static Task fillProperties(InternalTask task, boolean manual) {
        task.addAttribute(Task.TASK_TOKEN, task.getTaskId());
        JobCommand jobCommand = task.getType().getAnnotation(JobCommand.class);
        task.addAttribute(Task.TASK_RUN_CLUSTER_SINGLETON, jobCommand.clusterSingleton());
        task.addAttribute(Task.JOB_NAME, jobCommand.jobName());
        task.addAttribute(Task.ALLOWED_AFFINITY, jobCommand.allowedAffinity());
        task.addAttribute(Task.HARD_AFFINITY, jobCommand.hardAffinity());
        // Set good state for singleton
        if (jobCommand.singleton()) {
            // Manual activation of a singleton task is not singleton
            task.setSingleton(!manual);
        }
        task.setClusterSingleton(jobCommand.clusterSingleton());
        return task;
    }

    private static void checkCommandClass(Class<? extends TaskCallback> commandClass, boolean manual) {
        if (commandClass == null) {
            throw new IllegalArgumentException("Command class mandatory");
        }
        JobCommand jobCommand = commandClass.getAnnotation(JobCommand.class);
        if (jobCommand == null) {
            throw new IllegalArgumentException(
                    QUARTZ_CMD + commandClass + " does not have the " + JobCommand.class + " annotation");
        }
        if (manual && jobCommand.manualUser() == TaskUser.INVALID) {
            throw new IllegalArgumentException(
                    QUARTZ_CMD + commandClass + " does not support manual activation!");
        }
        if (!manual && jobCommand.schedulerUser() == TaskUser.INVALID) {
            throw new IllegalArgumentException(
                    QUARTZ_CMD + commandClass + " does not support scheduled activation!");
        }
    }

    public static boolean pauseOrBreak() {
        InternalArtifactoryContext context = InternalContextHelper.get();
        return context != null && context.getTaskService().pauseOrBreak();
    }

    public static long copyLarge(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024 * 8];
        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
            if (pauseOrBreak()) {
                throw new IOException("Stopped copy on demand!");
            }
        }
        return count;
    }

    private static String generateUniqueName(Class<? extends QuartzCommand> commandClass) {
        return commandClass.getSimpleName() + "#" + UUID.randomUUID().toString();
    }

    public static Predicate<Task> createPredicateForType(final Class<? extends TaskCallback> callbackType) {
        return new TaskTypePredicate(callbackType);
    }

    public static String getTaskLockKeyIncludingKeyAttributes(String jobName, Map<String, Object> jobDataMap,
            List<String> keyAttributes) {
        if (keyAttributes.isEmpty()) {
            return jobName;
        }
        // Has key attributes - incorporate them into the lock key
        String lockPrefix = jobName + "_" + jobDataMap.get(keyAttributes.get(0));
        List<String> list = keyAttributes.stream()
                .skip(1)
                .map(jobDataMap::get)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        String suffix = CollectionUtils.notNullOrEmpty(list) ? "_" + Objects.hash(list.toArray()) : "";
        return InternalStringUtils.getFirstNChars(lockPrefix, MAX_LOCK_NAME_PREFIX) + suffix;
    }

    @Nonnull
    public static List<Object> extractAttributeValues(Map<String, Object> jobDataMap, String[] keyAttributes) {
        return jobDataMap.entrySet().stream()
                .filter(entry -> Arrays.asList(keyAttributes)
                        .contains(entry.getKey())).map(Map.Entry::getValue).toList();
    }
}