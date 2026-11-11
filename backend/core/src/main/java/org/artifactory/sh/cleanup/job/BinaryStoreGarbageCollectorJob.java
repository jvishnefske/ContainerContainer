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

package org.artifactory.sh.cleanup.job;

import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.ScheduleJobEnum;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.storage.spring.StorageContextHelper;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.schedule.StopStrategy.PAUSE;
import static org.jfrog.common.ExceptionLogUtils.debugOrError;

/**
 * Date: 11/26/12
 * Time: 9:53 PM
 *
 * @author freds
 */
@JobCommand(jobName = JobName.BINARY_STORE_GARBAGE_COLLECTOR,
        singleton = true, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM,
        description = "Binaries Garbage Collector",
        commandsToStop = {
                //todo consider have group of index jobs/maintenance jobs/import-export jobs
                @StopCommand(strategy = PAUSE, commandName = ScheduleJobEnum.SHA256_MIGRATION_JOB),
                @StopCommand(strategy = PAUSE, commandName = ScheduleJobEnum.BINARY_STORE_TRASH_CLEANUP_GC_JOB),
        })
public class BinaryStoreGarbageCollectorJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(BinaryStoreGarbageCollectorJob.class);
    private GCService gcService;
    private GarbageCollectorInfo gcInfo;

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.debug("Triggered BinaryStoreGarbageCollectorJob started");
            gcService = StorageContextHelper.get().beanForType(GCService.class);
            gcInfo = gcService.startGarbageCollect();
            log.debug("Triggered BinaryStoreGarbageCollectorJob finished");
        } catch (Exception e) {
            gcInfo = null;
            debugOrError(log, "BinaryStoreGarbageCollectorJob failed", e);
            throw new JobExecutionException(e);
        }
    }

    @Override
    protected void afterExecute() {
        super.afterExecute();
        if (gcService != null && gcInfo != null &&
                GarbageCollectorStrategy.FULL.name().equals(gcInfo.getStrategy())) {
            log.info("Checking if sharding balancer should run after full gc");
            gcService.runShardingBalancerTask();
        }
    }
}