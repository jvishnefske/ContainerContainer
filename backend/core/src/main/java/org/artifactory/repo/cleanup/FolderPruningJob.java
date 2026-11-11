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

package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * A quartz command job that periodically prune empty folders
 *
 * @author Shay Yaakov with kind help from Fred Simon
 */
@JobCommand(jobName = JobName.FOLDER_PRUNING,
        singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Empty Folders Pruning Job",
        allowedAffinity = false,
        clusterSingleton = false)
public class FolderPruningJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalFolderPruningService integrationCleaner = ContextHelper.get()
                .beanForType(InternalFolderPruningService.class);
        withQueryRateLimiter(LOW_PRIORITY, integrationCleaner::doPrune);
    }
}
