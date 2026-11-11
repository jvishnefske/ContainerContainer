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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yoav Landman
 * @author Fred Simon
 */
@Test(singleThreaded = true)
public class TaskServiceTest extends TaskServiceTestBase {
    private static final Logger log = LoggerFactory.getLogger(TaskServiceTest.class);

    @Test(enabled = false, invocationCount = 6, threadPoolSize = 3)
    public void testServiceSynchronization() throws Exception {
        Task task1 = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 100, 200);
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        taskService.startTask(task1, false);
        taskService.pauseTask(task1.getTaskId(), false);
        homeStub.getTaskData(task1.getTaskId()).assertNbs(0, 0);
        InternalTask task2 = (InternalTask) TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 100, 0);
        task2.addAttribute(DummyQuartzCommand.MSECS_TO_RUN, "400");
        taskService.startTask(task2, false);
        Thread.sleep(600);
        taskService.stopTask(task1.getTaskId(), true);
        taskService.stopTask(task2.getTaskId(), true);
        homeStub.getTaskData(task2.getTaskId()).assertNbs(2, 1);
        homeStub.getTaskData(task1.getTaskId()).assertNbs(0, 0);
        taskService.cancelTask(task1.getTaskId(), true);
        taskService.cancelTask(task2.getTaskId(), true);
        Assert.assertNull(taskService.getActiveTask(task1.getTaskId(), false));
        Assert.assertNull(taskService.getActiveTask(task2.getTaskId(), false));
        assertFalse(taskService.hasActiveTasks(task -> task.getType().equals(DummyQuartzCommand.class)));
    }

    @Test(enabled = false)
    public void testStopWhileRunning() throws Exception {
        Task task1 = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 0, 0);
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        taskService.startTask(task1, true);
        assertTrue(task1.isRunning());
        try {
            taskService.pauseTask(task1.getTaskId(), false);
            Assert.fail("Should not be able to pause with no wait on running task");
        } catch (Exception e) {
            log.debug("Fail to pause running task OK!");
        }
        taskService.stopTask(task1.getTaskId(), true);
        // Need to wait for cancel afterExecute to be activated
        Thread.sleep(50);
        Assert.assertNull(taskService.getActiveTask(task1.getTaskId(), false));
        homeStub.getTaskData(task1.getTaskId()).assertNbs(1, 1);
    }

    @Test(enabled = false)
    public void testCancelWhileRunning() {
        InternalTask task1 = (InternalTask) TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 0, 0);
        ArtifactoryHomeTaskTestStub homeStub = getOrCreateArtifactoryHomeStub();
        taskService.startTask(task1, true);
        assertTrue(task1.isRunning());
        taskService.cancelTask(task1.getTaskId(), true);
        Assert.assertFalse(task1.isRunning());
        Assert.assertNull(taskService.getActiveTask(task1.getTaskId(), false));
        assertTrue(task1.waitForCompletion(0));
        homeStub.getTaskData(task1.getTaskId()).assertNbs(1, 1);
    }

    @Test(enabled = false, invocationCount = 6, threadPoolSize = 3)
    public void testMutliResume() throws Exception {
        final CyclicBarrier pauseBarrier1 = new CyclicBarrier(2);
        final CyclicBarrier pauseBarrier2 = new CyclicBarrier(2);
        final Task tsk = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 0, 0);
        Callable<String> c1 = () -> {
            try {
                log.debug("........... PAUSING-1");
                taskService.pauseTask(tsk.getTaskId(), true);
                pauseBarrier1.await();
                pauseBarrier2.await();
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
            return null;
        };
        Callable<String> c2 = () -> {
            boolean resumed;
            try {
                pauseBarrier1.await();
                log.debug("........... PAUSING-2");
                taskService.pauseTask(tsk.getTaskId(), true);
                pauseBarrier2.await();
                log.debug("........... RESUMING-1");
                resumed = taskService.resumeTask(tsk.getTaskId());
                if (resumed) {
                    return "Resume barriers are broken - resumed too early.";
                }
                log.debug("........... RESUMING-2");
                resumed = taskService.resumeTask(tsk.getTaskId());
                if (!resumed) {
                    return "Resume barriers are broken - expected resume was not be possible.";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
            return null;
        };
        taskService.startTask(tsk, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        Future<String> c1Result = executorService.submit(c1);
        Future<String> c2Result = executorService.submit(c2);
        String error = c2Result.get();
        Assert.assertNull(error, error);
        error = c1Result.get();
        Assert.assertNull(error, error);
        assertTrue(taskService.waitForTaskCompletion(tsk.getTaskId()));
        getOrCreateArtifactoryHomeStub().getTaskData(tsk.getTaskId()).assertNbs(1, 0);
        Thread.sleep(50);
        Assert.assertNull(taskService.getActiveTask(tsk.getTaskId(), false));
    }

    @Test(enabled = false)
    public void testCancelWhenWaitingOnStateTransition() {
        final Task tsk = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 1000, 0);
        final String tskToken = taskService.startTask(tsk, true);
        ArtifactoryHomeTaskTestStub.TaskTestData taskData = getOrCreateArtifactoryHomeStub().getTaskData(tskToken);
        assertTrue(tsk.isRunning());
        taskService.pauseTask(tskToken, true);
        assertTrue(tsk.processActive());
        taskService.cancelTask(tsk.getTaskId(), true);
        Assert.assertFalse(tsk.processActive());
        Assert.assertNull(taskService.getActiveTask(tsk.getTaskId(), false));
        taskData.assertNbs(1, 1);
    }

    @Test(enabled = false)
    public void testMultiSingleExecution() {
        taskService.cancelAllTasks(true);
        QuartzCommand cmd = new DummyQuartzCommand();
        final InternalTask tsk1 = (InternalTask) TaskUtils.createRepeatingTask(cmd.getClass(), 0, 0);
        //Make the task run at least 500ms, so that it wont finish before the second conflicting one is scheduled
        tsk1.addAttribute(DummyQuartzCommand.MSECS_TO_RUN, "500");
        tsk1.setSingleton(true);
        taskService.startTask(tsk1, false);
        log.debug("........... STARTED TSK1");
        final InternalTask tsk2 = (InternalTask) TaskUtils.createRepeatingTask(cmd.getClass(), 0, 0);
        tsk2.setSingleton(true);
        try {
            taskService.startTask(tsk2, false);
            Assert.fail("Should not be able to run 2 singleton tasks concurrently.");
        } catch (IllegalStateException e) {
            //Good - we expected it
        }
        Assert.assertTrue(taskService.waitForTaskCompletion(tsk1.getTaskId()));
        Assert.assertTrue(taskService.waitForTaskCompletion(tsk2.getTaskId()));
    }

    @Test(enabled = false)
    public void testSingleExecutionWithError() throws Exception {
        taskService.cancelAllTasks(true);
        QuartzCommand cmd = new DummyQuartzCommand();
        final InternalTask tsk1 = (InternalTask) TaskUtils.createRepeatingTask(cmd.getClass(), 0, 0);
        tsk1.setSingleton(true);
        tsk1.addAttribute(DummyQuartzCommand.FAIL, Boolean.TRUE);
        taskService.startTask(tsk1, true);
        log.debug("........... WAITING FOR TSK1");
        assertTrue(taskService.waitForTaskCompletion(tsk1.getTaskId()));
        getOrCreateArtifactoryHomeStub().getTaskData(tsk1.getTaskId()).assertFails(1, 1);
        // Need to wait for cancel afterExecute to be activated
        Thread.sleep(50);
        Assert.assertNull(taskService.getActiveTask(tsk1.getTaskId(), false));
        final InternalTask tsk2 = (InternalTask) TaskUtils.createRepeatingTask(cmd.getClass(), 0, 0);
        tsk2.setSingleton(true);
        taskService.startTask(tsk2, true);
        taskService.waitForTaskCompletion(tsk2.getTaskId());
        getOrCreateArtifactoryHomeStub().getTaskData(tsk2.getTaskId()).assertNbs(1, 0);
        // Need to wait for cancel afterExecute to be activated
        Thread.sleep(50);
        Assert.assertNull(taskService.getActiveTask(tsk2.getTaskId(), false));
    }

    @Test(enabled = false)
    public void testConcurrentStopResumes() throws Exception {
        final CyclicBarrier barrier1 = new CyclicBarrier(2);
        final CyclicBarrier barrier2 = new CyclicBarrier(2);
        final Task tsk = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 0, 0);
        Callable<String> c1 = () -> {
            barrier1.await();
            log.debug("........... PAUSING-1");
            taskService.pauseTask(tsk.getTaskId(), true);
            barrier2.await();
            return null;
        };
        Callable<String> c2 = () -> {
            barrier1.await();
            log.debug("........... STOPING-2");
            taskService.stopTask(tsk.getTaskId(), true);
            barrier2.await();
            return null;
        };
        taskService.startTask(tsk, true);
        Thread.sleep(200);
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(c1);
        executorService.submit(c2);
        Assert.assertTrue(taskService.waitForTaskCompletion(tsk.getTaskId()));
        taskService.cancelTask(tsk.getTaskId(), true);
    }

    @Test(enabled = false)
    public void testDoubleResume() {
        Task task1 = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 1000, 0);
        taskService.startTask(task1, true);
        taskService.pauseTask(task1.getTaskId(), true);
        taskService.stopTask(task1.getTaskId(), true);
        log.debug("........... RESUMING-1");
        taskService.resumeTask(task1.getTaskId());
        log.debug("........... RESUMING-2");
        taskService.resumeTask(task1.getTaskId());
        log.debug("........... RESUMING-3");
        taskService.resumeTask(task1.getTaskId());
        log.debug("........... RESUMING-4");
        taskService.resumeTask(task1.getTaskId());
        log.debug("........... DONE");
        taskService.cancelTask(task1.getTaskId(), true);
    }

    @Test(enabled = false)
    public void testMisfireIgnored() throws Exception {
        // Create task running every second and lasting for 1.2s
        Task task1 = TaskUtils.createCronTask(DummyQuartzCommand.class, "* * * * * ?");
        task1.addAttribute(DummyQuartzCommand.MSECS_TO_RUN, "1200");
        taskService.startTask(task1, true);
        Thread.sleep(3000);
        taskService.cancelTask(task1.getTaskId(), true);
        // Should have executed 3 times and one break
        getOrCreateArtifactoryHomeStub().getTaskData(task1.getTaskId()).assertNbs(3, 1);
    }

    @Test(enabled = false)
    public void testMisfireIgnoredOnPause() throws Exception {
        // Create task running every second and pause for 1.2s
        Task task1 = TaskUtils.createCronTask(DummyQuartzCommand.class, "* * * * * ?");
        taskService.startTask(task1, true);
        Thread.sleep(100);
        taskService.pauseTask(task1.getTaskId(), true);
        Thread.sleep(1200);
        Assert.assertTrue(taskService.resumeTask(task1.getTaskId()));
        Thread.sleep(100);
        taskService.cancelTask(task1.getTaskId(), true);
        // Should have executed 3 times and one break
        getOrCreateArtifactoryHomeStub().getTaskData(task1.getTaskId()).assertNbs(1, 1);
    }
}