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

import com.google.common.collect.ImmutableMap;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.RemoteReplicationSettings;
import org.artifactory.addon.replication.RemoteReplicationSettingsBuilder;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.replication.ReplicationHelper;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUtils;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static org.artifactory.schedule.Task.JOB_NAME;
import static org.artifactory.schedule.Task.REPO_KEY;
import static org.mockito.Mockito.spy;


/**
 * @author Noam Y. Tenne
 */
public class RemoteReplicationJobTest extends ReplicationJobTestBase {

    private static final RemoteReplicationJob job = new RemoteReplicationJob();
    private RemoteReplicationDescriptor replicationDescriptor;
    private RemoteReplicationSettings replicationSettings;
    private final RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);

    @BeforeClass
    public void setUp() throws Exception {
        replicationDescriptor = new RemoteReplicationDescriptor();
        replicationDescriptor.setRepoKey("key");
        replicationDescriptor.setEnabled(true);
        replicationDescriptor.setIncludePathPrefixPattern("path");
        replicationDescriptor.setSyncDeletes(true);
        replicationDescriptor.setSyncProperties(true);

        replicationSettings = new RemoteReplicationSettingsBuilder(ReplicationHelper.getRepoPath(replicationDescriptor))
                .deleteExisting(replicationDescriptor.isSyncDeletes())
                .includeProperties(replicationDescriptor.isSyncProperties())
                .includePathPrefixPattern(replicationDescriptor.getIncludePathPrefixPattern())
                .excludePathPrefixPattern(replicationDescriptor.getExcludePathPrefixPattern())
                .build();
    }

    @BeforeMethod
    public void setup() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterMethod
    public void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testContextNotReady() throws Exception {
        Mockito.when(artifactoryContext.isReady()).thenReturn(false);
        job.onExecute(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullRepoKey() throws Exception {
        Mockito.when(artifactoryContext.isReady()).thenReturn(true);
        Mockito.when(executionContext.getJobDetail()).thenReturn(jobDetail);
        Mockito.when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        job.onExecute(null);
    }

    @Test
    public void testExecutionException() throws Exception {
        Mockito.when(artifactoryContext.isReady()).thenReturn(true);
        Mockito.when(executionContext.getJobDetail()).thenReturn(jobDetail);
        Mockito.when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Mockito.when(jobDataMap.getString(Task.REPO_KEY)).thenReturn("key");
        Mockito.when(jobDataMap.get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR)).thenReturn(null);

        CentralConfigDescriptorImpl centralConfig = new CentralConfigDescriptorImpl();
        centralConfig.addRemoteReplication(replicationDescriptor);
        Mockito.when(configService.getDescriptor()).thenReturn(centralConfig);
        Mockito.when(artifactoryContext.getCentralConfig()).thenReturn(configService);
        Mockito.when(artifactoryContext.getRepositoryService()).thenReturn(repositoryService);
        Mockito.when(repositoryService.remoteRepoConfigByKey("key")).thenReturn(remoteRepoConfig);
        Mockito.when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        Mockito.when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        Mockito.when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
        Mockito.when(replicationAddon.performRemoteReplication(replicationSettings)).thenThrow(new IOException());
        job.onExecute(executionContext);
        Mockito.verify(securityService).authenticateAsSystem();
        Mockito.verify(securityService).nullifyContext();
        Mockito.verify(artifactoryContext).isReady();
        Mockito.verify(executionContext).getJobDetail();
        Mockito.verify(jobDetail).getJobDataMap();
        Mockito.verify(jobDataMap).getString(Task.REPO_KEY);
        Mockito.verify(jobDataMap).get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR);
        Mockito.verify(artifactoryContext).getRepositoryService();
        Mockito.verify(repositoryService).remoteRepoConfigByKey("key");
        Mockito.verify(artifactoryContext).beanForType(SecurityService.class);
        Mockito.verify(artifactoryContext).beanForType(AddonsManager.class);
        Mockito.verify(addonsManager).addonByType(ReplicationAddon.class);
        Mockito.verify(replicationAddon).performRemoteReplication(replicationSettings);
    }

    @Test
    public void testExecution() throws Exception {
        Mockito.when(artifactoryContext.isReady()).thenReturn(true);
        Mockito.when(executionContext.getJobDetail()).thenReturn(jobDetail);
        Mockito.when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Mockito.when(jobDataMap.getString(Task.REPO_KEY)).thenReturn("key");
        Mockito.when(jobDataMap.get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR)).thenReturn(null);

        CentralConfigDescriptorImpl centralConfig = new CentralConfigDescriptorImpl();
        centralConfig.addRemoteReplication(replicationDescriptor);
        Mockito.when(configService.getDescriptor()).thenReturn(centralConfig);
        Mockito.when(artifactoryContext.getCentralConfig()).thenReturn(configService);
        Mockito.when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        Mockito.when(artifactoryContext.getRepositoryService()).thenReturn(repositoryService);
        Mockito.when(repositoryService.remoteRepoConfigByKey("key")).thenReturn(remoteRepoConfig);
        Mockito.when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        Mockito.when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
        Mockito.when(replicationAddon.performRemoteReplication(replicationSettings))
                .thenReturn(new BasicStatusHolder());
        job.onExecute(executionContext);
        Mockito.verify(securityService).authenticateAsSystem();
        Mockito.verify(securityService).nullifyContext();
        Mockito.verify(artifactoryContext).beanForType(SecurityService.class);
        Mockito.verify(artifactoryContext).getRepositoryService();
        Mockito.verify(repositoryService).remoteRepoConfigByKey("key");
        Mockito.verify(artifactoryContext).beanForType(AddonsManager.class);
        Mockito.verify(addonsManager).addonByType(ReplicationAddon.class);
        Mockito.verify(replicationAddon).performRemoteReplication(replicationSettings);
        Mockito.verify(artifactoryContext).isReady();
        Mockito.verify(executionContext).getJobDetail();
        Mockito.verify(jobDetail).getJobDataMap();
        Mockito.verify(jobDataMap).getString(Task.REPO_KEY);
        Mockito.verify(jobDataMap).get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR);
    }

    @Test
    public void testManualExecution() throws Exception {
        Mockito.when(artifactoryContext.isReady()).thenReturn(true);
        Mockito.when(executionContext.getJobDetail()).thenReturn(jobDetail);
        Mockito.when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Mockito.when(jobDataMap.get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR)).thenReturn(replicationDescriptor);
        Mockito.when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        CentralConfigDescriptorImpl centralConfig = new CentralConfigDescriptorImpl();
        Mockito.when(configService.getDescriptor()).thenReturn(centralConfig);
        Mockito.when(artifactoryContext.getCentralConfig()).thenReturn(configService);
        Mockito.when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        Mockito.when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
        Mockito.when(artifactoryContext.getRepositoryService()).thenReturn(repositoryService);
        Mockito.when(repositoryService.remoteRepoConfigByKey("key")).thenReturn(remoteRepoConfig);
        Mockito.when(replicationAddon.performRemoteReplication(replicationSettings))
                .thenReturn(new BasicStatusHolder());

        job.onExecute(executionContext);
        Mockito.verify(artifactoryContext).isReady();
        Mockito.verify(executionContext).getJobDetail();
        Mockito.verify(jobDetail).getJobDataMap();
        Mockito.verify(jobDataMap).get(ReplicationAddon.TASK_MANUAL_DESCRIPTOR);

        Mockito.verify(securityService).authenticateAsSystem();
        Mockito.verify(securityService).nullifyContext();
        Mockito.verify(configService).getDescriptor();
        Mockito.verify(artifactoryContext).getCentralConfig();
        Mockito.verify(artifactoryContext).beanForType(AddonsManager.class);
        Mockito.verify(addonsManager).addonByType(ReplicationAddon.class);
        Mockito.verify(artifactoryContext).getRepositoryService();
        Mockito.verify(repositoryService).remoteRepoConfigByKey("key");
        Mockito.verify(replicationAddon).performRemoteReplication(replicationSettings);
    }

    @Test
    public void testLockName() {
        RemoteReplicationJob remoteReplicationJob = spy(new RemoteReplicationJob());
        Task task = TaskUtils.createRepeatingTask(PushReplicationJob.class, 10L, 10L);
        Map<String, Object> map = ImmutableMap.of(REPO_KEY, "some-repo-key", JOB_NAME, JobName.REMOTE_REPLICATION);
        map.forEach(task::addAttribute);
        ReflectionTestUtils.setField(remoteReplicationJob, "activeTask", task);
        Assert.assertEquals(remoteReplicationJob.getCommandLockKey(),
                JobName.REMOTE_REPLICATION.asText() + "_some-repo-key");
    }

}
