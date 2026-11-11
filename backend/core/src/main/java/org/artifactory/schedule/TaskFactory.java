package org.artifactory.schedule;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.artifactory.schedule.TaskUtils.QUARTZ_CMD;

/**
 * @author Mor Merhav
 */
@UtilityClass
public class TaskFactory {

    Task createQuartzTask(Class<? extends TaskCallback<JobExecutionContext>> command, Trigger trigger,
            JobDetail jobDetail, String externalId, boolean manual) {
        JobCommand commandAnnotation = command.getAnnotation(JobCommand.class);
        InternalTask task = new QuartzTask(command, trigger, jobDetail);
        task.addAttribute(Task.TASK_AUTHENTICATION, getAuthentication(commandAnnotation, task, manual));
        task.setManuallyActivated(manual);
        if (ArtifactoryHome.get().isHaConfigured()) {
            MarkerDbLocksService dbLocksService = ContextHelper.get().beanForType(MarkerDbLocksService.class);
            task = new HaQuartzTask(task, dbLocksService);
        }
        if (commandAnnotation.monitored()) {
            if (commandAnnotation.externalIdMandatory() && StringUtils.isBlank(externalId)) {
                throw new IllegalArgumentException(
                        String.format(
                                "JobCommand annotation 'mandatoryExternalId' for type: %s is 'true' but no external " +
                                        "ID was provided during creation", command.getName()));
            }
            QuartzTaskHistoryService quartzTaskHistoryService = ContextHelper.get().beanForType(
                    QuartzTaskHistoryService.class);
            task = new MonitoredQuartzTask(task, quartzTaskHistoryService, externalId);
        }
        return task;
    }

    private static Authentication getAuthentication(JobCommand jobCommand, Task task, boolean manual) {
        TaskUser taskUser;
        if (manual) {
            taskUser = jobCommand.manualUser();
        } else {
            taskUser = jobCommand.schedulerUser();
        }
        Authentication authentication;
        switch (taskUser) {
            case INVALID:
                throw new IllegalArgumentException(
                        QUARTZ_CMD + task.getType() + " did not set a task user for " +
                                (manual ? "manual" : "scheduled") + " activation!"
                );
            case SYSTEM:
                authentication = new SystemAuthenticationToken();
                break;
            case CURRENT:
                authentication = SecurityContextHolder.getContext().getAuthentication();
                // If authentication null continue to anonymous
                if (authentication == null) {
                    authentication = new AnonymousAuthenticationToken();
                }
                break;
            case ANONYMOUS:
                authentication = new AnonymousAuthenticationToken();
                break;
            default:
                throw new IllegalStateException("Could not find authentication credential for task " + task.getType());
        }

        return authentication;
    }
}