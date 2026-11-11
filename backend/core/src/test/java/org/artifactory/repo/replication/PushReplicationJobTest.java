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
import org.apache.commons.lang3.RandomStringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static org.artifactory.schedule.Task.JOB_NAME;
import static org.artifactory.schedule.Task.PUSH_REPLICATION_URL;
import static org.artifactory.schedule.Task.REPO_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * @author Shay Bagants
 */
public class PushReplicationJobTest extends ArtifactoryHomeBoundTest {

    private final String SRC_REPO_KEY = "myRepo";
    private final String TARGET_URL = "http://momo.koko.com";
    private final String REPLICATION_KEY = SRC_REPO_KEY + ":" + TARGET_URL;
    private PushReplicationJob replicationJob;
    private MockitoSession mockitoSession;

    @Mock
    private LocalReplicationDescriptor replicationDescriptor;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JobExecutionContext jobExecutionContext;
    @Mock
    private InternalArtifactoryContext artifactoryContext;
    @Mock
    private CentralConfigService configService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ReplicationAddon replicationAddon;
    @Mock
    private CentralConfigDescriptor configDescriptor;
    @Mock
    private GlobalReplicationsConfigDescriptor globalReplicationsConfigDescriptor;

    @BeforeMethod
    public void beforeMethod() {
        mockitoSession = Mockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();
        replicationJob = new PushReplicationJob();

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.isReady()).thenReturn(true);
        when(globalReplicationsConfigDescriptor.isBlockPullReplications()).thenReturn(false);
        when(artifactoryContext.getCentralConfig()).thenReturn(configService);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getReplicationsConfig()).thenReturn(globalReplicationsConfigDescriptor);
        when(replicationDescriptor.getRepoKey()).thenReturn(SRC_REPO_KEY);
        when(replicationDescriptor.getUrl()).thenReturn(TARGET_URL);
        when(replicationDescriptor.getReplicationKey()).thenReturn(REPLICATION_KEY);
        when(configDescriptor.getLocalReplication(any(), any())).thenReturn(replicationDescriptor);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
        mockitoSession.finishMocking();
    }

    @Test
    public void testContextNotReadyReplicationSkipped() throws Exception {
        when(artifactoryContext.isReady()).thenReturn(false);
        testOnExecute(jobExecutionContext, false);
    }

    @Test
    public void testReplicationDescriptorNullReplicationSkipped() throws Exception {
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getLocalReplication(any(), any())).thenReturn(null);
        testOnExecute(jobExecutionContext, false);
    }

    @Test
    public void testReplicationsBlockedReplicationSkipped() throws Exception {
        when(globalReplicationsConfigDescriptor.isBlockPushReplications()).thenReturn(true);
        testOnExecute(jobExecutionContext, false);
    }

    @Test
    public void testOnExecuteReplicationExecuted() throws Exception {
        testOnExecute(jobExecutionContext, true);
    }

    @Test
    public void testUniqueLockName() {
        PushReplicationJob pushReplicationJob = spy(new PushReplicationJob());
        Task task = TaskUtils.createRepeatingTask(PushReplicationJob.class, 10L, 10L);
        Map<String, Object> map = ImmutableMap.of(REPO_KEY, SRC_REPO_KEY, PUSH_REPLICATION_URL, TARGET_URL,
                JOB_NAME, JobName.PUSH_REPLICATION);
        map.forEach(task::addAttribute);
        ReflectionTestUtils.setField(pushReplicationJob, "activeTask", task);
        assertEquals(pushReplicationJob.getCommandLockKey(), JobName.PUSH_REPLICATION.asText() + "_" +
                SRC_REPO_KEY + "_" + Objects.hash(TARGET_URL));
    }

    @Test
    public void testUniqueLockNameTrimmed() {
        PushReplicationJob pushReplicationJob = spy(new PushReplicationJob());
        Task task = TaskUtils.createRepeatingTask(PushReplicationJob.class, 10L, 10L);
        String longRepoKey = RandomStringUtils.randomAlphanumeric(300);
        Map<String, Object> map = ImmutableMap.of(REPO_KEY, longRepoKey, PUSH_REPLICATION_URL, TARGET_URL,
                JOB_NAME, JobName.PUSH_REPLICATION);
        map.forEach(task::addAttribute);
        ReflectionTestUtils.setField(pushReplicationJob, "activeTask", task);
        assertEquals(pushReplicationJob.getCommandLockKey(), JobName.PUSH_REPLICATION.asText() + "_" +
                longRepoKey.substring(0, Task.MAX_LOCK_NAME_PREFIX - (JobName.PUSH_REPLICATION.asText().length() + 1))
                + "_" + Objects.hash(TARGET_URL));
    }

    @Test
    public void testDifferentLockNamesDueToTargetUrlAttribute() {
        PushReplicationJob firstPushReplicationJob = spy(new PushReplicationJob());
        PushReplicationJob secondPushReplicationJob = spy(new PushReplicationJob());

        Task task1 = TaskUtils.createRepeatingTask(PushReplicationJob.class, 10L, 10L);
        Task task2 = TaskUtils.createRepeatingTask(PushReplicationJob.class, 10L, 10L);

        String targetUrl1 = "http://target.url1.com";
        String targetUrl2 = "http://target.url2.com";

        Map<String, Object> firstAttributesMap =
                ImmutableMap.of(REPO_KEY, SRC_REPO_KEY, PUSH_REPLICATION_URL, targetUrl1,
                        JOB_NAME, JobName.PUSH_REPLICATION);
        Map<String, Object> secondAttributesMap =
                ImmutableMap.of(REPO_KEY, SRC_REPO_KEY, PUSH_REPLICATION_URL, targetUrl2,
                        JOB_NAME, JobName.PUSH_REPLICATION);

        firstAttributesMap.forEach(task1::addAttribute);
        secondAttributesMap.forEach(task2::addAttribute);

        ReflectionTestUtils.setField(firstPushReplicationJob, "activeTask", task1);
        ReflectionTestUtils.setField(secondPushReplicationJob, "activeTask", task2);

        String firstJobLockKey = firstPushReplicationJob.getCommandLockKey();
        String secondJobLockKey = secondPushReplicationJob.getCommandLockKey();

        assertEquals(firstJobLockKey, JobName.PUSH_REPLICATION.asText() + "_" +
                SRC_REPO_KEY + "_" + Objects.hash(targetUrl1));
        assertEquals(secondJobLockKey, JobName.PUSH_REPLICATION.asText() + "_" +
                SRC_REPO_KEY + "_" + Objects.hash(targetUrl2));
        assertNotEquals(firstJobLockKey, secondJobLockKey,
                "Expected command lock key to be unique in case of different repo URLs");
    }

    private void testOnExecute(JobExecutionContext callbackContext, boolean expectedReplication)
            throws Exception {
        replicationJob.onExecute(callbackContext);
        verifyHandleReplicatrionExecuted(expectedReplication);
    }

    private void verifyHandleReplicatrionExecuted(boolean expectedReplication) throws IOException {
        int expectedCalls = expectedReplication ? 1 : 0;
        verify(replicationAddon, times(expectedCalls)).performLocalReplication(any());
    }
}