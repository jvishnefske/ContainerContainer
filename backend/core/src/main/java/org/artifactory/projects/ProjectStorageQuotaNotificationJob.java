package org.artifactory.projects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.mail.MailService;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.StorageService;
import org.artifactory.util.EmailException;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.rest.user.UserBase;
import org.jfrog.common.FileUtils;
import org.joda.time.DateTime;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.schedule.JobName.PROJECT_STORAGE_QUOTA_NOTIFICATION;

/**
 * @author Yevdo Abramov
 * Created on 22/12/2020
 */
@Slf4j
@JobCommand(jobName = PROJECT_STORAGE_QUOTA_NOTIFICATION,
        singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Trigger Project storage quota notification of admin users.")
public class ProjectStorageQuotaNotificationJob extends QuartzCommand {

    private static final String STORAGE_QUOTA_NOTIFICATION_EMAIL =
            "/org.artifactory.projects/storageQuotaNotificationEmail.properties";

    private final ProjectsService projectsService;
    private final StorageService storageService;
    private final MailService mailService;
    private final CoreAddons coreAddons;

    public ProjectStorageQuotaNotificationJob() {
        AddonsManager addonsManager = InternalContextHelper.get().beanForType(AddonsManager.class);
        this.projectsService = InternalContextHelper.get().beanForType(ProjectsService.class);
        this.storageService = InternalContextHelper.get().beanForType(StorageService.class);
        this.mailService = InternalContextHelper.get().beanForType(MailService.class);
        this.coreAddons = addonsManager.addonByType(CoreAddons.class);
    }

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        List<StorageQuotaNotification> projectsToBeNotified = projectsService.getAllProjectsEntities()
                .stream()
                .map(this::prepareNotificationPayloadIfNeeded)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        if (projectsToBeNotified.isEmpty()) {
            return;
        }
        log.debug("There are {} projects who's storage quota limit has been reached or close to it",
                projectsToBeNotified.size());

        excludeRecentlyNotifiedProjects(projectsToBeNotified);
        storageService.updateProjectStorageQuotaNotificationTimestamp(projectsToBeNotified.stream()
                .map(StorageQuotaNotification::getProjectKey)
                .collect(Collectors.toSet()));

        log.debug("Attempting to send storage quota limit notification emails to admins of {} projects",
                projectsToBeNotified.size());

        sendEmailNotificationToProjectsAdmins(projectsToBeNotified);
    }

    private void excludeRecentlyNotifiedProjects(List<StorageQuotaNotification> projectsToBeNotified) {
        Map<String, Long> allNotifiedProjects = storageService.getAllProjectsNotifiedOnStorageQuota();
        long lastNotifiedBefore = getLastNotifiedBeforeTimestamp();
        if (MapUtils.isEmpty(allNotifiedProjects)) {
            return;
        }

        // remove from the list all those who where recently notified
        projectsToBeNotified.removeIf(project ->
                allNotifiedProjects.getOrDefault(project.getProjectKey(), Long.MIN_VALUE) >= lastNotifiedBefore);
    }

    private long getLastNotifiedBeforeTimestamp() {
        int notificationPeriodInMinutes = ConstantValues.projectsStorageQuotaNotificationPeriodMinutes.getInt();
        return DateTime.now().minusMinutes(notificationPeriodInMinutes).getMillis();
    }

    private void sendEmailNotificationToProjectsAdmins(
            @NonNull List<StorageQuotaNotification> projectsAvailableToBeNotified) {
        if (projectsAvailableToBeNotified.isEmpty()) {
            return;
        }

        Set<String> platformAdminEmails = new HashSet<>(coreAddons.getPlatformAdminUsersEmails());
        ResourceBundle emailTemplateResource;
        try (InputStream stream = getClass().getResourceAsStream(STORAGE_QUOTA_NOTIFICATION_EMAIL)) {
            emailTemplateResource = new PropertyResourceBundle(stream);
        } catch (Exception e) {
            log.error("Error reading project storage quota notification email template", e);
            throw new RuntimeException(e); // NOSONAR
        }

        for (StorageQuotaNotification notificationPayload : projectsAvailableToBeNotified) {
            sendStorageQuotaEmailNotification(notificationPayload, platformAdminEmails, emailTemplateResource);
        }
    }

    private Optional<StorageQuotaNotification> prepareNotificationPayloadIfNeeded(@NonNull ProjectEntity project) {
        if (!project.isStorageQuotaEmailNotification()) {
            return Optional.empty();
        }
        if (ProjectsService.DEFAULT_PROJECT.equals(project.getProjectKey())) {
            return Optional.empty();
        }

        long projectLimit = project.getStorageQuotaBytes();
        if (projectLimit == ProjectsService.UNLIMITED_STORAGE) {
            return Optional.empty();
        }
        long projectStorageUsage = storageService.getProjectStorageUsage(project.getProjectKey());
        // project.isSoftLimit() always false if in AOL environment.
        double thresholdRate = project.isSoftLimit() ? 1 : getHardLimitThreshold() / 100.0;

        if (projectStorageUsage <= projectLimit * thresholdRate) {
            return Optional.empty();
        }

        return Optional.of(new StorageQuotaNotification(project, projectStorageUsage));
    }

    private int getHardLimitThreshold() {
        int configuredHardLimitThreshold =
                ConstantValues.projectsStorageQuotaNotificationThresholdHardLimitPercentage.getInt();
        return configuredHardLimitThreshold < 1 || configuredHardLimitThreshold > 100 ?
                100 : configuredHardLimitThreshold;
    }

    private void sendStorageQuotaEmailNotification(
            @NonNull ProjectStorageQuotaNotificationJob.StorageQuotaNotification notificationPayload,
            @NonNull Set<String> platformAdminEmails, @NonNull ResourceBundle emailTemplateResource) {

        ProjectEntity project = notificationPayload.getProject();
        long currentStorageUsage = notificationPayload.currentStorageUsage;
        long storageLimit = project.getStorageQuotaBytes();

        Set<String> projectAdminEmails = getProjectAdminEmails(project);
        Set<String> allRecipients = new HashSet<>(projectAdminEmails);
        allRecipients.addAll(platformAdminEmails);

        if (allRecipients.isEmpty()) {
            log.warn("No recipients found for storage quota limit exceeded notification email for project: {}",
                    project.getProjectKey());
            return;
        }

        String subject = MessageFormat.format(
                emailTemplateResource.getString("subject"), project.getName());

        String body = MessageFormat.format(emailTemplateResource.getString("body"),
                project.getName(),
                FileUtils.bytesToDisplaySize(currentStorageUsage),
                FileUtils.bytesToDisplaySize(storageLimit));

        try {
            log.debug("Sending storage quota notification for project: {} to {} recipient(s)",
                    project.getProjectKey(), allRecipients.size());
            mailService.sendMail(allRecipients.toArray(new String[0]), subject, body);
        } catch (EmailException e) {
            log.error("Error sending storage quota notification for project: {}",
                    project.getProjectKey(), e);
        }
    }

    @NonNull
    private Set<String> getProjectAdminEmails(@NonNull ProjectEntity project) {
        return projectsService.getProjectAdmins(project).stream()
                .map(UserBase::getEmail)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }

    @Data
    @AllArgsConstructor
    private static class StorageQuotaNotification {
        private ProjectEntity project;
        private long currentStorageUsage;

        public String getProjectKey() {
            return project.getProjectKey();
        }
    }
}
