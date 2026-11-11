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

package org.artifactory.backup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.mail.MailService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.BaseTaskServiceDescriptorHandler;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.EmailException;
import org.artifactory.util.MailUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.api.config.PathValidatorUtil.isArtifactoryHomePath;
import static org.artifactory.api.config.PathValidatorUtil.isBackupPathInAllowList;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getExcludedFromImportSystemRepos;

/**
 * @author Yoav Landman
 */
@Slf4j
@Service
@Reloadable(beanClass = InternalBackupService.class,
        initAfter = {InternalRepositoryService.class, TaskService.class},
        listenOn = CentralConfigKey.backups)
public class BackupServiceImpl implements InternalBackupService {

    private final CentralConfigService centralConfig;

    private final InternalRepositoryService repositoryService;

    private final MailService mailService;

    private final AddonsManager addonsManager;

    private final ConfigsService configsService;

    @Autowired
    public BackupServiceImpl(CentralConfigService centralConfig, InternalRepositoryService repositoryService,
            MailService mailService, AddonsManager addonsManager, ConfigsService configsService) {
        this.centralConfig = centralConfig;
        this.repositoryService = repositoryService;
        this.mailService = mailService;
        this.addonsManager = addonsManager;
        this.configsService = configsService;
    }

    @Override
    public void init() {
        reload(null, ImmutableList.of());
    }

    @Override
    public void reload(@Nullable CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        List<BackupDescriptor> backupDescriptors = centralConfig.getDescriptor().getBackups();
        BackupDescriptorHandler backupDescriptorHandler = new BackupDescriptorHandler(backupDescriptors,
                (oldDescriptor != null) ? oldDescriptor.getBackups() : null);
        backupDescriptorHandler.reschedule();
    }

    @Override
    public void destroy() {
        new BackupDescriptorHandler(null, null).unschedule();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    @Override
    public void backupRepos(File backupDir, ExportSettingsImpl exportSettings) {
        Set<String> backedupRepos = getBackedUpRepos(new HashSet<>(getExcludedFromImportSystemRepos()));
        ExportSettingsImpl settings = new ExportSettingsImpl(backupDir, exportSettings);
        settings.setRepositories(new ArrayList<>(backedupRepos));
        repositoryService.exportTo(settings);
    }


    @Override
    public void scheduleImmediateSystemBackup(BackupDescriptor backupDescriptor, BasicStatusHolder statusHolder) {
        if (isArtifactoryHomePath(backupDescriptor.getDir())) {
            statusHolder.error("Creating a backup inside '" + backupDescriptor.getDir() + "' is not allowed.", log);
            return;
        }
        File backupDir = backupDescriptor.getDir();
        if (backupDir != null && !isBackupPathInAllowList(backupDir.getAbsolutePath())) {
            statusHolder.error(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST + ": '" + backupDir + "'.", log);
            return;
        }
        TaskService taskService = InternalContextHelper.get().getTaskService();
        String backupKey = backupDescriptor.getKey();
        Task task = TaskUtils.createManualTask(BackupJob.class, 0L, "Backup - " + backupKey);
        taskService.checkCanStartManualTask(task, BackupJob.class, statusHolder, backupKey);
        if (!statusHolder.isError()) {
            try {
                statusHolder.status("Activating manual system backup '" + backupKey + "'", log);
                task.addAttribute(BackupJob.MANUAL_BACKUP, backupDescriptor);
                task.addAttribute(BackupJob.BACKUP_KEY, backupKey);
                String taskToken = taskService.startTask(task, true, true);
                statusHolder.status("Started " + taskToken + " successfully", log);
            } catch (Exception e) {
                statusHolder.error("Error scheduling manual system backup '" + backupKey + "'", e, log);
            }
        }
    }

    @Override
    public boolean isEnoughFreeSpace(BackupDescriptor backupDescriptor) {
        return isEnoughFreeSpace(backupDescriptor, details -> {});
    }

    @Override
    public boolean isEnoughFreeSpace(ExportSettingsImpl settings) {
        return getBackupSizeCalculator().isEnoughFreeSpace(settings);
    }

    @Override
    public boolean isEnoughFreeSpace(BackupDescriptor backup, Consumer<String> detailsConsumer) {
        BackupSizeCalculator sizeCalculator = getBackupSizeCalculator();
        // Check if a max used space threshold is already exceeded before the backup
        if (sizeCalculator.isMaxUsedSpaceExceeded(backup)) {
            detailsConsumer.accept("Not enough free space to perform backup. A threshold for max used space " +
                    "is exceeded before the backup" + backup.getKey());
            return false;
        }
        // Check if a max used space threshold is going to be exceeded after the backup
        if (backup.isPrecalculate() && sizeCalculator.willExceedMaxUsedSpaceAfterBackup(backup)) {
            detailsConsumer.accept("Not enough free space to perform backup. A threshold for max used space " +
                    "is going to be exceeded after the backup" + backup.getKey());
            return false;
        }
        return true;
    }

    private BackupSizeCalculator getBackupSizeCalculator() {
        InternalArtifactoryContext context = InternalContextHelper.get();
        File dataDir = context.getArtifactoryHome().getDataDir();
        File etcDir = context.getArtifactoryHome().getEtcDir();
        return new BackupSizeCalculator(repositoryService, configsService, this, dataDir, etcDir);
    }

    @Override
    public BasicStatusHolder backupSystem(InternalArtifactoryContext context, @Nonnull BackupDescriptor backup) {
        ImportExportStatusHolder status = new ImportExportStatusHolder();
        if (isArtifactoryHomePath(backup.getDir())) {
            status.error("Creating a backup inside '" + backup.getDir() + "' is not allowed.", log);
            return status;
        }
        File requestedBackupDir = backup.getDir();
        if (requestedBackupDir != null && !isBackupPathInAllowList(requestedBackupDir.getAbsolutePath())) {
            status.error(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST + ": '" + requestedBackupDir + "'.", log);
            return status;
        }
        if (!backup.isEnabled()) {
            status.error("Backup: '" + backup.getKey() + "' is disabled. Backup was not performed.", log);
            return status;
        }
        Set<String> excludeRepositories =
                Stream.concat(getExcludedFromImportSystemRepos().stream(),
                                backup.getExcludedRepositories().stream())
                        .collect(Collectors.toSet());
        Set<String> backedUpRepos = getBackedUpRepos(excludeRepositories);
        File backupDir = getBackupDir(backup);
        boolean createArchive = backup.isCreateArchive();
        boolean incremental = backup.isIncremental();
        boolean precalculate = backup.isPrecalculate();
        if (incremental && createArchive) {
            status.warn("An incremental backup cannot be archived!\n" +
                    "Please change the configuration of backup " + backup.getKey() + ".", log);
            createArchive = false;
        }
        ExportSettingsImpl settings = new ExportSettingsImpl(backupDir, status);
        settings.setRepositories(new ArrayList<>(backedUpRepos));
        settings.setCreateArchive(createArchive);
        settings.setIncremental(incremental);
        settings.addCallback(new SystemBackupPauseCallback());
        settings.setPrecalculate(precalculate);
        settings.setShouldActOnMissionControl(backup.isExportMissionControl());
        context.exportTo(settings);

        return status;
    }

    @Override
    public void cleanupOldBackups(Date now, String backupKey) {
        BackupDescriptor descriptor = getBackup(backupKey);
        if (descriptor == null) {
            return;
        }
        int retentionPeriodHours = descriptor.getRetentionPeriodHours();
        //No action if retention is 0 (or less)
        if (retentionPeriodHours <= 0) {
            return;
        }
        File backupDir = getBackupDir(descriptor);
        File[] children = backupDir.listFiles();
        if (children == null || CollectionUtils.isNullOrEmpty(children)) {
            log.debug("No old backup files to remove.");
            return;
        }

        //Calculate last valid time
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.HOUR, -retentionPeriodHours);
        Date validFrom = calendar.getTime();
        log.debug("Removing backups older than {}.", validFrom);
        //Delete anything not newer than the last valid time
        for (File child : children) {
            if (!FileUtils.isFileNewer(child, validFrom)) {
                try {
                    log.debug("Removing old backup file '{}'.", child.getPath());
                    FileUtils.forceDelete(child);
                } catch (IOException e) {
                    log.warn("Failed to remove old backup file or folder '" + child.getPath() + "'.", e);
                }
            } else {
                log.debug("Skipping new backup file '{}'.", child.getPath());
            }
        }
    }

    @Override
    public File getBackupDir(BackupDescriptor descriptor) {
        File dir = descriptor.getDir();
        File backupDir;
        if (dir == null) {
            ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
            backupDir = new File(artifactoryHome.getBackupDir(), descriptor.getKey());
        } else {
            backupDir = dir;
            try {
                FileUtils.forceMkdir(backupDir);
            } catch (IOException e) {
                throw new IllegalArgumentException("Backup directory provided in configuration: '" +
                        backupDir.getAbsolutePath() + "' cannot be created or is not a directory.");
            }
        }
        return backupDir;
    }

    @Override
    public void sendBackupErrorNotification(String backupName, BasicStatusHolder statusHolder) throws Exception {
        try (InputStream stream = getClass()
                .getResourceAsStream("/org/artifactory/email/messages/backupError.properties")) {
            ResourceBundle resourceBundle = new PropertyResourceBundle(stream);
            String body = resourceBundle.getString("body");
            String errorListBlock = getErrorListBlock(statusHolder);

            CoreAddons coreAddons = addonsManager.addonByType(CoreAddons.class);
            List<String> adminEmails = coreAddons.getPlatformAdminUsersEmails();
            if (CollectionUtils.isNullOrEmpty(adminEmails)) {
                log.warn("Couldn't find admin account with valid email address. " +
                        "Skipping backup failure email notification");
            }
            adminEmails.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(adminEmail -> sendBackupErrorMail(backupName, body, errorListBlock, adminEmail));
        }
    }

    private void sendBackupErrorMail(String backupName, String body, String errorListBlock, String adminEmail) {
        try {
            log.debug("Sending backup error notification to '{}'.", adminEmail);
            StringBuilder artifactoryUrlMessage =
                    MailUtils.getMessage(centralConfig.getDescriptor().getServerUrlForEmail());
            String message = MessageFormat.format(body, backupName, artifactoryUrlMessage, errorListBlock);
            mailService.sendMail(new String[] {adminEmail}, "Backup Error Notification", message);
        } catch (EmailException e) {
            log.error("Error while trying to send Email to: {}, due to: {}", adminEmail,
                    e.getMessage());
        }
    }

    @Override
    public Set<String> getBackedUpRepos(Set<String> excludeRepositories) {
        List<LocalConfig> localRepos = repositoryService.getLocalFederatedAndCachedRepoConfigs();
        Set<String> localRepoKeys = localRepos.stream()
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());

        repositoryService.getReleaseBundleRepoConfigs().forEach(releaseBundleRepoConfig -> {
            localRepos.add(releaseBundleRepoConfig);
            localRepoKeys.add(releaseBundleRepoConfig.getKey());
        });

        if (CollectionUtils.isNullOrEmpty(excludeRepositories)) {
            return localRepoKeys; // nothing is excluded return all local repositories
        }
        // Backed up repos are all those which are not excluded
        return localRepos.stream().filter(repo -> !excludeRepositories.contains(repo.isCache() ?
                        ((LocalCacheRepoConfig) repo).getRemoteRepoConfig().getKey() : repo.getKey()))
                .map(LocalConfig::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public BackupDescriptor getBackup(String backupKey) {
        final List<BackupDescriptor> list = centralConfig.getDescriptor().getBackups();
        for (BackupDescriptor backupDescriptor : list) {
            if (backupKey.equals(backupDescriptor.getKey())) {
                return backupDescriptor;
            }
        }
        //This might happen after the first time a backup has been turned off if the scheduler
        //wakes up before old jobs were cleaned up
        log.warn("Skipping empty backup config " + backupKey + "!\n" +
                "Probably a leftover from an old to-be-deleted backup job.");
        return null;
    }

    /**
     * Returns an HTML list block of errors extracted from the status holder
     *
     * @param statusHolder Status holder containing errors that should be included in the notification
     * @return HTML list block
     */
    private String getErrorListBlock(BasicStatusHolder statusHolder) {
        StringBuilder builder = new StringBuilder();
        List<StatusEntry> errors = statusHolder.getErrors();
        if (errors.size() > 0) {
            for (StatusEntry errorEntry : errors) {
                convertErrorEntryToString(builder, errorEntry);
            }
        } else {
            convertErrorEntryToString(builder, statusHolder.getLastError());
        }
        builder.append("<p>");
        return builder.toString();
    }

    private void convertErrorEntryToString(StringBuilder builder, StatusEntry errorEntry) {
        //Make one error per row
        builder.append(errorEntry.getMessage());
        Throwable throwable = errorEntry.getException();
        if (throwable != null) {
            String throwableMessage = throwable.getMessage();
            if (StringUtils.isNotBlank(throwableMessage)) {
                builder.append(": ");
                builder.append(throwableMessage);
            }
        }
        builder.append("<br>");
    }

    static class BackupDescriptorHandler extends BaseTaskServiceDescriptorHandler<BackupDescriptor> {
        final List<BackupDescriptor> newBackupDescriptors;
        final List<BackupDescriptor> oldBackupDescriptors;

        BackupDescriptorHandler(List<BackupDescriptor> newBackupDescriptors,
                List<BackupDescriptor> oldBackupDescriptors) {
            this.newBackupDescriptors = newBackupDescriptors;
            if (oldBackupDescriptors != null) {
                this.oldBackupDescriptors = oldBackupDescriptors;
            } else {
                this.oldBackupDescriptors = Lists.newArrayList();
            }
        }

        @Override
        public String jobName() {
            return "Backup";
        }

        @Override
        public List<BackupDescriptor> getNewDescriptors() {
            return this.newBackupDescriptors;
        }

        @Override
        public List<BackupDescriptor> getOldDescriptors() {
            return this.oldBackupDescriptors;
        }

        @Override
        public Predicate<Task> getAllPredicate() {
            return input -> BackupJob.class.isAssignableFrom(input.getType());
        }

        @Override
        public Predicate<Task> getPredicate(@Nonnull final BackupDescriptor descriptor) {
            return input -> BackupJob.class.isAssignableFrom(input.getType()) &&
                    descriptor.getKey().equals(input.getAttribute(BackupJob.BACKUP_KEY));
        }

        @Override
        public void activate(BackupDescriptor descriptor, boolean manual, BasicStatusHolder statusHolder) {
            //Schedule the cron'd backup
            String key = descriptor.getKey();
            String cronExp = descriptor.getCronExp();
            if (descriptor.isEnabled()) {
                if (cronExp == null) {
                    log.warn("No backup cron expression is configured. Backup will {} be disabled.", key);
                    return;
                }
                try {
                    Task task = TaskUtils.createCronTask(BackupJob.class, cronExp, "Backup - " + key);
                    task.addAttribute(BackupJob.BACKUP_KEY, key);
                    InternalContextHelper.get().getBean(TaskService.class).startTask(task, false, manual);
                    log.debug("Backup '{}' activated with cron expression '{}'.", key, cronExp);
                } catch (Exception e) {
                    log.warn("Activation of backup " + key + ":" + descriptor + " failed:" + e.getMessage(), e);
                }
            }
        }

        @Override
        public BackupDescriptor findOldFromNew(@Nonnull BackupDescriptor newDescriptor) {
            for (BackupDescriptor oldBackupDescriptor : oldBackupDescriptors) {
                if (oldBackupDescriptor.getKey().equals(newDescriptor.getKey())) {
                    return oldBackupDescriptor;
                }
            }
            return null;
        }
    }
}
