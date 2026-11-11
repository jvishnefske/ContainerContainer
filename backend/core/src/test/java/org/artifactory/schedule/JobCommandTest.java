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

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.UserInfo;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author freds
 */
//TODO: [by YS] this test is unclear and unstable becuase it depends a lot on sleep time
@Test(enabled = false, singleThreaded = true)
public class JobCommandTest extends TaskServiceTestBase {

    private static final String NO_DUMMY_VALUE = "no-dummy";
    private static final String DUMMY_RUNNING_VALUE = "dummy-running";
    private static final String DUMMY_STOPPED_VALUE = "dummy-stopped";
    private static final String DUMMY_PAUSED_VALUE = "dummy-paused";
    private static final String DUMMY_MANUAL_PAUSED_VALUE = "dummy-manual-paused";
    private static final String DUMMY_MANUAL_RUNNING_VALUE = "dummy-manual-stopped";

    @AfterMethod
    public void afterMethod() throws Exception {
        Thread.sleep(500);
    }

    public void testCommandToStop() throws Exception {
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        Task task1 = createRunAndPauseDummyA(500);
        ArtifactoryHomeTaskTestStub.TaskTestData task1Data = homeStub.getTaskData(task1.getTaskId());

        taskService.resumeTask(task1.getTaskId());
        Thread.sleep(500);
        taskService.pauseTask(task1.getTaskId(), true);
        task1Data.assertNbs(1, 0);
        assertTrue(task1Data.taskStoppedOnExecute.isEmpty());
        assertTrue(task1Data.taskStoppedAfterExecute.isEmpty());
        assertEquals(task1Data.userNameOnExecute, SecurityService.USER_SYSTEM);

        // Run while the other waiting
        Task task2 = createManualDummyB(task1.getTaskId());
        ArtifactoryHomeTaskTestStub.TaskTestData task2Data = homeStub.getTaskData(task2.getTaskId());
        assertTrue(taskService.resumeTask(task2.getTaskId()));
        Thread.sleep(300);
        task2Data.assertNbs(1, 0);
        assertEquals(task2Data.taskStoppedAfterExecute.size(), 0);

        taskService.stopTask(task1.getTaskId(), true);
        task2Data.assertNbs(1, 0);
        task1Data.assertNbs(1, 0);
        taskService.cancelTask(task1.getTaskId(), true);
        assertNull(taskService.getActiveTask(task1.getTaskId(), false));
        assertNull(taskService.getActiveTask(task2.getTaskId(), false));
    }

    public void testSingletonAndManual() throws Exception {
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        Task task1 = createRunAndPauseDummyA(500);
        try {
            createRunAndPauseDummyA(500);
            fail("Should get an IllegalStateException trying to start 2 singleton tasks");
        } catch (IllegalStateException e) {
        }
        ArtifactoryHomeTaskTestStub.TaskTestData task1Data = homeStub.getTaskData(task1.getTaskId());
        taskService.resumeTask(task1.getTaskId());
        assertTrue(task1.isRunning());
        try {
            Task failedTask = TaskUtils.createManualTask(DummyQuartzCommandA.class, 0L);
            taskService.startTask(failedTask, false);
            fail(
                    "Should get an IllegalStateException trying to start a manual task while the singleton running");
        } catch (IllegalStateException e) {
        }
        Thread.sleep(500);
        task1Data.assertNbs(1, 0);
        InternalTask manualTask = (InternalTask) TaskUtils.createManualTask(DummyQuartzCommandA.class, 0L);
        taskService.startTask(manualTask, true);
        assertTrue(manualTask.isRunning());
        assertFalse(task1.isRunning());
        TaskState task1State = (TaskState) ReflectionTestUtils.getField(task1, "state");
        assertEquals(task1State, TaskState.STOPPED);
        taskService.pauseTask(manualTask.getTaskId(), true);
        try {
            Task failedTask = TaskUtils.createManualTask(DummyQuartzCommandA.class, 0L);
            taskService.startTask(failedTask, false);
            fail("Should get an IllegalStateException trying to start 2 manual tasks for a singleton");
        } catch (IllegalStateException e) {
        }
        assertFalse(manualTask.isSingleton());
        assertTrue(manualTask.isManuallyActivated());
        ArtifactoryHomeTaskTestStub.TaskTestData manualTaskData = homeStub.getTaskData(manualTask.getTaskId());
        manualTaskData.assertPause(1, 1);
        assertEquals(manualTaskData.taskStoppedOnExecute.size(), 1);
        assertEquals(manualTaskData.taskStoppedOnExecute.get(0), task1.getTaskId());
        assertNull(manualTaskData.taskStoppedAfterExecute);
        assertEquals(manualTaskData.userNameOnExecute, UserInfo.ANONYMOUS);
        assertTrue(taskService.resumeTask(manualTask.getTaskId()));
        assertTrue(taskService.waitForTaskCompletion(manualTask.getTaskId()));
        Thread.sleep(50);
        manualTaskData.assertNbs(1, 0);
        assertEquals(manualTaskData.taskStoppedAfterExecute.size(), 0);
        task1State = (TaskState) ReflectionTestUtils.getField(task1, "state");
        assertEquals(task1State, TaskState.SCHEDULED);
        taskService.cancelTask(task1.getTaskId(), true);
        assertNull(taskService.getActiveTask(task1.getTaskId(), false));
        assertNull(taskService.getActiveTask(manualTask.getTaskId(), false));
    }

    private Task createManualDummyB(String stoppedToken) {
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        InternalTask task2 = (InternalTask) TaskUtils.createManualTask(DummyQuartzCommandB.class, 0L);
        task2.addAttribute(DummyQuartzCommand.MSECS_TO_RUN, "200");
        taskService.startTask(task2, true);
        ArtifactoryHomeTaskTestStub.TaskTestData taskData = homeStub.getTaskData(task2.getTaskId());
        assertTrue(task2.isRunning());
        taskService.pauseTask(task2.getTaskId(), true);
        taskData.assertPause(1, 1);
        assertFalse(task2.isRunning());
        assertFalse(task2.isSingleton());
        assertTrue(task2.isManuallyActivated());
        assertEquals(taskData.taskStoppedOnExecute.size(), 1);
        assertEquals(taskData.taskStoppedOnExecute.get(0), stoppedToken);
        assertNull(taskData.taskStoppedAfterExecute);
        assertEquals(taskData.userNameOnExecute, UserInfo.ANONYMOUS);
        return task2;
    }

    private Task createRunAndPauseDummyA(long msecToRun) throws Exception {
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        InternalTask task1 = (InternalTask) TaskUtils.createRepeatingTask(DummyQuartzCommandA.class,
                1000L + msecToRun, 100);
        task1.addAttribute(DummyQuartzCommand.MSECS_TO_RUN, msecToRun);
        taskService.startTask(task1, false);
        taskService.pauseTask(task1.getTaskId(), false);
        ArtifactoryHomeTaskTestStub.TaskTestData taskData = homeStub.getTaskData(task1.getTaskId());
        taskData.assertNbs(0, 0);
        assertTrue(task1.isSingleton());
        assertFalse(task1.isManuallyActivated());
        assertTrue(taskService.resumeTask(task1.getTaskId()));
        // Wait to start
        Thread.sleep(200);
        try {
            assertTrue(task1.isRunning());
            taskService.pauseTask(task1.getTaskId(), false);
            fail("Should get cannot stop running task exception");
        } catch (IllegalStateException e) {
        }
        taskService.pauseTask(task1.getTaskId(), true);
        taskData.assertPause(1, 1);
        assertTrue(taskData.taskStoppedOnExecute.isEmpty());
        assertNull(taskData.taskStoppedAfterExecute);
        assertEquals(taskData.userNameOnExecute, SecurityService.USER_SYSTEM);
        return task1;
    }

    public void testStopStrategyAndKey() throws Exception {
        Task dummyA = createRunAndPauseDummyA(1000);
        assertTrue(taskService.resumeTask(dummyA.getTaskId()));
        try {
            taskService.checkCanStartManualTask(dummyA, DummyQuartzCommandC.class, new BasicStatusHolder());
            fail("Should get " + IllegalArgumentException.class +
                    " since no key values provided for a command with keys");
        } catch (IllegalArgumentException e) {
        }
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(dummyA, DummyQuartzCommandC.class, statusHolder, NO_DUMMY_VALUE);
        assertTrue(statusHolder.isError(),
                "Command " + DummyQuartzCommandC.class + " should be forbidden to run due to " + dummyA + " active");
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        ArtifactoryHomeTaskTestStub.TaskTestData dummyTaskData = homeStub.getTaskData(dummyA.getTaskId());
        taskService.stopTask(dummyA.getTaskId(), true);
        dummyTaskData.assertNbs(1, 1);
        statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(dummyA, DummyQuartzCommandC.class, statusHolder, NO_DUMMY_VALUE);
        assertFalse(statusHolder.isError(),
                "Command " + DummyQuartzCommandC.class + " should be allowed to run manually");

        Task dummyCRunning = TaskUtils.createRepeatingTask(DummyQuartzCommandC.class, 2000L, 0L);
        dummyCRunning.addAttribute(DummyQuartzCommandC.TEST_KEY, DUMMY_RUNNING_VALUE);
        assertEquals(taskService.startTask(dummyCRunning, true), dummyCRunning.getTaskId());

        Task dummyCStopped = TaskUtils.createRepeatingTask(DummyQuartzCommandC.class, 2000L, 0L);
        dummyCStopped.addAttribute(DummyQuartzCommandC.TEST_KEY, DUMMY_STOPPED_VALUE);
        assertEquals(taskService.startTask(dummyCStopped, true), dummyCStopped.getTaskId());
        taskService.stopTask(dummyCStopped.getTaskId(), true);

        Task dummyCPaused = TaskUtils.createRepeatingTask(DummyQuartzCommandC.class, 2000L, 0L);
        dummyCPaused.addAttribute(DummyQuartzCommandC.TEST_KEY, DUMMY_PAUSED_VALUE);
        assertEquals(taskService.startTask(dummyCPaused, true), dummyCPaused.getTaskId());
        taskService.pauseTask(dummyCPaused.getTaskId(), true);

        Task dummyCManualPaused = TaskUtils.createManualTask(DummyQuartzCommandC.class, 0L);
        dummyCManualPaused.addAttribute(DummyQuartzCommandC.TEST_KEY, DUMMY_MANUAL_PAUSED_VALUE);
        assertEquals(taskService.startTask(dummyCManualPaused, true), dummyCManualPaused.getTaskId());
        taskService.pauseTask(dummyCManualPaused.getTaskId(), true);

        Task dummyCManualRunning = TaskUtils.createManualTask(DummyQuartzCommandC.class, 0L);
        dummyCManualRunning.addAttribute(DummyQuartzCommandC.TEST_KEY, DUMMY_MANUAL_RUNNING_VALUE);
        assertEquals(taskService.startTask(dummyCManualRunning, true), dummyCManualRunning.getTaskId());

        checkCanRunManualMethod(DummyQuartzCommandC.class, true);
        checkCanRunManualMethod(DummyQuartzCommandD.class, false);

        taskService.cancelTask(dummyA.getTaskId(), true);
        taskService.cancelTask(dummyCRunning.getTaskId(), true);
        taskService.cancelTask(dummyCPaused.getTaskId(), true);
        taskService.cancelTask(dummyCStopped.getTaskId(), true);
        taskService.cancelTask(dummyCManualPaused.getTaskId(), true);
        taskService.cancelTask(dummyCManualRunning.getTaskId(), true);
        assertNull(taskService.getActiveTask(dummyA.getTaskId(), false));
        assertNull(taskService.getActiveTask(dummyCRunning.getTaskId(), false));
        assertNull(taskService.getActiveTask(dummyCPaused.getTaskId(), false));
        assertNull(taskService.getActiveTask(dummyCStopped.getTaskId(), false));
        assertNull(taskService.getActiveTask(dummyCManualPaused.getTaskId(), false));
        assertNull(taskService.getActiveTask(dummyCManualRunning.getTaskId(), false));
    }

    private void checkCanRunManualMethod(Class<? extends QuartzCommand> typeToRun, boolean sameType)
            throws InterruptedException {
        checkShouldFail(typeToRun, DUMMY_RUNNING_VALUE, sameType);
        checkShouldStart(typeToRun, NO_DUMMY_VALUE, false);
        checkShouldStart(typeToRun, DUMMY_STOPPED_VALUE, sameType);
        checkShouldFail(typeToRun, DUMMY_PAUSED_VALUE, sameType);
        checkShouldFail(typeToRun, DUMMY_MANUAL_PAUSED_VALUE, sameType);
        checkShouldFail(typeToRun, DUMMY_MANUAL_RUNNING_VALUE, sameType);
    }

    private void checkShouldFail(Class<? extends QuartzCommand> typeToRun, String keyValue, boolean sameType)
            throws InterruptedException {
        BasicStatusHolder statusHolder1 = new BasicStatusHolder();
        taskService.checkCanStartManualTask(null, typeToRun, statusHolder1, keyValue);
        assertTrue(statusHolder1.isError(), "Command " + typeToRun + " should be forbidden to run");

        Task manualTask = TaskUtils.createManualTask(typeToRun, 0L);
        manualTask.addAttribute(DummyQuartzCommandC.TEST_KEY, keyValue);
        try {
            taskService.startTask(manualTask, true);
            fail("Task " + manualTask + " should be forbidden to start");
        } catch (IllegalStateException e) {
        }
        assertNull(taskService.getActiveTask(manualTask.getTaskId(), false));

        Task repeatTask = TaskUtils.createRepeatingTask(typeToRun, 1000L, 0L);
        repeatTask.addAttribute(DummyQuartzCommandC.TEST_KEY, keyValue);

        if (sameType) {
            try {
                taskService.startTask(repeatTask, true);
                fail("Task " + repeatTask + " should be forbidden to start");
            } catch (IllegalStateException e) {
            }
        } else {
            taskService.startTask(repeatTask, false);
            Thread.sleep(20);
            ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
            ArtifactoryHomeTaskTestStub.TaskTestData repeatTaskData = homeStub.getTaskData(repeatTask.getTaskId());
            // Never reached execution since it fails in beforeExecute
            // TODO: Monitor the before execute calls
            repeatTaskData.assertFails(0, 0);
            taskService.cancelTask(repeatTask.getTaskId(), true);
        }
        assertNull(taskService.getActiveTask(repeatTask.getTaskId(), false));
    }

    private void checkShouldStart(Class<? extends QuartzCommand> typeToRun, String keyValue, boolean onlyManual) {
        BasicStatusHolder statusHolder1 = new BasicStatusHolder();
        taskService.checkCanStartManualTask(null, typeToRun, statusHolder1, keyValue);
        assertFalse(statusHolder1.isError(), "Command " + typeToRun + " should be allowed to run");

        Task manualTask = TaskUtils.createManualTask(typeToRun, 0L);
        manualTask.addAttribute(DummyQuartzCommandC.TEST_KEY, keyValue);
        assertEquals(taskService.startTask(manualTask, true), manualTask.getTaskId());
        taskService.cancelTask(manualTask.getTaskId(), true);
        assertNull(taskService.getActiveTask(manualTask.getTaskId(), false));

        Task repeatTask = TaskUtils.createRepeatingTask(typeToRun, 1000L, 0L);
        repeatTask.addAttribute(DummyQuartzCommandC.TEST_KEY, keyValue);

        if (onlyManual) {
            try {
                taskService.startTask(repeatTask, true);
                fail("Task " + repeatTask + " should be forbidden to start");
            } catch (IllegalStateException e) {
            }
        } else {
            assertEquals(taskService.startTask(repeatTask, true), repeatTask.getTaskId());
            ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
            ArtifactoryHomeTaskTestStub.TaskTestData repeatTaskData = homeStub.getTaskData(repeatTask.getTaskId());
            taskService.cancelTask(repeatTask.getTaskId(), true);
            repeatTaskData.assertNbs(1, 1);
        }
        assertNull(taskService.getActiveTask(repeatTask.getTaskId(), false));
    }
}