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

package org.artifactory.lock.service;

import com.google.common.collect.Lists;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.locks.service.JFrogTableBasedDbLocksService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.common.ha.HaNodeProperties.PROP_ENABLED;
import static org.artifactory.common.ha.HaNodeProperties.PROP_NODE_ID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
@Test
public class LockServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    private JobExecutionContext callbackContext;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private JFrogTableBasedDbLocksService dbLocksService;
    @Mock
    private HaAddon haAddon;
    @Mock
    private TaskService taskService;
    @Mock
    private ReleaseLockStrategy releaseLockStrategy1;
    @Mock
    private ReleaseLockStrategy releaseLockStrategy2;

    private LockServiceImpl lockService;
    private final LockServiceImpl.ReleaseExpiredAndOrphanLocksJob job =
            new LockServiceImpl.ReleaseExpiredAndOrphanLocksJob();
    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setup() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();

        ArtifactoryContextThreadBinder.bind(context);
        List<ReleaseLockStrategy> releaseLockStrategies = Lists
                .newArrayList(releaseLockStrategy1, releaseLockStrategy2);

        HaNodeProperties haNodeProperties = getBound().getHaNodeProperties();
        haNodeProperties.setProperty(PROP_NODE_ID, "owner");
        haNodeProperties.setProperty(PROP_ENABLED, "true");

        lockService = new LockServiceImpl(dbLocksService, releaseLockStrategies, haAddon);

        when(context.beanForType(TaskService.class)).thenReturn(taskService);
        when(context.beanForType(LockService.class)).thenReturn(lockService);
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
        reset(context, callbackContext);
        mockitoSession.finishMocking();
    }

    @Test
    public void testReleaseAllLocksByOwner() {
        lockService.releaseAllLocksByOwner("owner");
        verify(dbLocksService).releaseAllLocksByOwner("owner");
    }

    @Test
    public void testReleaseExpiredLocks() {
        lockService.releaseExpiredLocks();
        verifyStrategiesReleaseExpiredCalled();
    }

    @Test
    public void testReleaseOrphanLocks() {
        lockService.releaseOrphanLocks();
        verifyStrategiesReleaseOrphanedCalled();
    }

    @Test
    public void testReleaseExpiredAndOrphanLocksJobOnExecuteNoContextBound() throws JobExecutionException {
        ArtifactoryContextThreadBinder.unbind();
        job.onExecute(callbackContext);
        verify(context, times(0)).beanForType(any());
    }

    @Test
    public void testReleaseExpiredAndOrphanLocksJobOnExecute() throws JobExecutionException {
        job.onExecute(callbackContext);
        verifyStrategiesReleaseExpiredCalled();
        verifyStrategiesReleaseOrphanedCalled();
        verifyDbLocksReleaseStuckLocks();
    }

    @Test
    public void releaseStuckLocksExpectCallToDbLockService() {
        lockService.releaseStuckLocks();
        verifyDbLocksReleaseStuckLocks();
    }

    private void verifyStrategiesReleaseOrphanedCalled() {
        verify(releaseLockStrategy1, times(1)).releaseOrphaned();
        verify(releaseLockStrategy2, times(1)).releaseOrphaned();
    }

    private void verifyStrategiesReleaseExpiredCalled() {
        verify(releaseLockStrategy1, times(1)).releaseExpired();
        verify(releaseLockStrategy2, times(1)).releaseExpired();
    }

    private void verifyDbLocksReleaseStuckLocks() {
        verify(dbLocksService, times(1)).forceUnlockAllStuck();
    }

}