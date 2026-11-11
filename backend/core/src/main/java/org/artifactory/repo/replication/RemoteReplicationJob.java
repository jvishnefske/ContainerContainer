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

package org.artifactory.repo.replication;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.git.GitAddon;
import org.artifactory.addon.replication.RemoteReplicationSettingsBuilder;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.replication.ReplicationHelper;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

/**
 * @author Noam Y. Tenne
 */
@Slf4j
@JobCommand(jobName = JobName.REMOTE_REPLICATION, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.CURRENT,
        keyAttributes = {Task.REPO_KEY},
        commandsToStop = {
                @StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = ArtifactCleanupJob.class, strategy = StopStrategy.STOP)
        }
)
public class RemoteReplicationJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet", this.getClass().getName());
            return;
        }

        RemoteReplicationDescriptor replication = replicationDescriptorFromJobOrConfig(context, callbackContext);
        if (replication == null) {
            log.warn("Unable to execute replication for repo: cannot find replication descriptor.");
            return;
        }

        GlobalReplicationsConfigDescriptor replications =
                context.getCentralConfig().getDescriptor().getReplicationsConfig();
        if (replications.isBlockPullReplications()) {
            log.warn("Skipping execution of '{}', pull replications are disabled globally",
                    this.getClass().getName());
            return;
        }

        RemoteReplicationSettingsBuilder settingsBuilder =
                new RemoteReplicationSettingsBuilder(ReplicationHelper.getRepoPath(replication))
                        .deleteExisting(replication.isSyncDeletes())
                        .includeProperties(replication.isSyncProperties())
                        .checkBinaryExistenceInFilestore(replication.isCheckBinaryExistenceInFilestore())
                        .includePathPrefixPattern(replication.getIncludePathPrefixPattern())
                        .excludePathPrefixPattern(replication.getExcludePathPrefixPattern());

        RemoteRepoConfig remoteRepoConfig = context.getRepositoryService().remoteRepoConfigByKey(
                replication.getRepoKey());
        if (remoteRepoConfig != null) {
            settingsBuilder.url(remoteRepoConfig.getRepoTypeConfig().getUrl())
                    .timeout(remoteRepoConfig.getRepoTypeConfig().getSocketTimeoutMillis());
        } else {
            log.warn("Unable to execute replication for repo: cannot find remote repo: '{}'.",
                    replication.getRepoKey());
            return;
        }

        SecurityService securityService = context.beanForType(SecurityService.class);
        try {
            securityService.authenticateAsSystem();
            AddonsManager addonsManager = context.beanForType(AddonsManager.class);
            ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
            replicationAddon.performRemoteReplication(settingsBuilder.build());
            afterRemoteReplication(remoteRepoConfig, addonsManager);
        } catch (Exception e) {
            log.error("An error occurred while performing replication for repository '{}': {}",
                    replication.getRepoKey(), e.getMessage());
            log.debug("An error occurred while performing replication for repository '{}'.",
                    replication.getRepoKey(), e);
        } finally {
            securityService.nullifyContext();
        }
    }

    private void afterRemoteReplication(RemoteRepoConfig remoteRepoConfig, AddonsManager addonsManager) {
        if (RepoType.Cargo.equals(remoteRepoConfig.getPackageType())) {
            GitAddon gitAddon = addonsManager.addonByType(GitAddon.class);
            gitAddon.updateGitRepository(remoteRepoConfig);
            addonsManager.addonByType(HaAddon.class).propagateGitSyncRepository(remoteRepoConfig.getKey());
        }
    }

    @Override
    protected List<String> getLockKeyAttributes() {
        return List.of(Task.REPO_KEY);
    }

    private RemoteReplicationDescriptor replicationDescriptorFromJobOrConfig(InternalArtifactoryContext context,
            JobExecutionContext callbackContext) {
        JobDetail jobDetail = callbackContext.getJobDetail();
        JobDataMap jobDataMap = jobDetail.getJobDataMap();

        Object manualInvocationDescriptor = jobDataMap.get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR);
        if (manualInvocationDescriptor != null) {
            return ((RemoteReplicationDescriptor) manualInvocationDescriptor);
        } else {
            CentralConfigService centralConfig = context.getCentralConfig();
            CentralConfigDescriptor centralConfigDescriptor = centralConfig.getDescriptor();
            return centralConfigDescriptor.getRemoteReplication(jobDataMap.getString(Task.REPO_KEY));
        }
    }
}
