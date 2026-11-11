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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author yoavl
 */
@Test(enabled = false, singleThreaded = true)
public class SharedTaskServiceTest extends TaskServiceTestBase {
    private static final Logger log = LoggerFactory.getLogger(SharedTaskServiceTest.class);

    protected Task task;

    @BeforeClass
    public void startTask() throws Exception {
        task = TaskUtils.createRepeatingTask(DummyQuartzCommand.class, 50, 0);
        taskService.startTask(task, false);
    }

    @AfterClass
    public void cancelTask() {
        taskService.cancelTask(task.getTaskId(), true);
    }

    @Test(enabled = false)
    public void testPause() throws Exception {
        Thread.sleep(200);
        log.debug("######### PAUSING #########");
        taskService.pauseTask(task.getTaskId(), true);
        log.debug("######### PAUSED #########");
        Thread.sleep(200);
        log.debug("######### RESUMED #########");
        taskService.resumeTask(task.getTaskId());
        Thread.sleep(200);
        log.debug("######### STOPPING #########");
        taskService.stopTask(task.getTaskId(), true);
        log.debug("######### STOPPED #########");
        Thread.sleep(200);
        log.debug("######### RESUMING #########");
        taskService.resumeTask(task.getTaskId());
        log.debug("######### RESUMED #########");
        Thread.sleep(200);
    }

    @Test(enabled = false, invocationCount = 12, threadPoolSize = 12)
    public void testConcurrentServiceAccess() throws Exception {
        taskService.pauseTask(task.getTaskId(), true);
        Thread.sleep(200);
        taskService.resumeTask(task.getTaskId());
        Thread.sleep(200);
        taskService.stopTask(task.getTaskId(), true);
        Thread.sleep(200);
        taskService.resumeTask(task.getTaskId());
        Thread.sleep(200);
    }
}