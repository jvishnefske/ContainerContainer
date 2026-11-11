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

package org.artifactory.schedule.quartz;

import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.spring.StorageContextHelper;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A quartz command job that runs a plugin closure
 *
 * @author Yoav Landman
 */
@JobCommand(jobName = JobName.PLUGIN_COMMAND, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM,
        commandsToStop = {@StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE)}
)
public class PluginCommand extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(PluginCommand.class);

    public static final String ACTION_CLOSURE = "action-closure";

    @Override
    protected List<String> getLockKeyAttributes() {
        return List.of(Task.CONCURRENCY_KEY);
    }

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        AddonsManager addonsManager = StorageContextHelper.get().beanForType(AddonsManager.class);
        if (!ConstantValues.pluginInfrastructureEnabled.getBoolean()
                || addonsManager.isEntitled(EntitlementType.DISABLE_PLUGIN_INFRA)) {
            log.debug("Plugin infrastructure is disabled. Skipping scheduled plugin command.");
            return;
        }
        JobDetail jobDetail = callbackContext.getJobDetail();
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        ExecuteAction action = (ExecuteAction) jobDataMap.get(ACTION_CLOSURE);
        String jobName = jobDetail.getKey().toString();
        log.debug("Executing plugin command '{}'...", jobName);
        action.execute();
        log.debug("Plugin command '{}'executed.", jobName);
    }
}
