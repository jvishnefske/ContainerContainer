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

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.security.core.Authentication;

/**
 *  this job is the default job to be used as default on Stop Command annotation
 */
public class DummyJob extends TaskCallback {

    public static String TASKTOKEN = "token";

    @Override
    protected String triggeringTaskTokenFromWorkContext(JobExecutionContext workContext) {
        return TASKTOKEN;
    }

    @Override
    protected JobName getJobName(JobExecutionContext workContext) {
        return JobName.ANY;
    }

    @Override
    protected Authentication getAuthenticationFromWorkContext(JobExecutionContext callbackContext) {
        return null;
    }

    @Override
    protected boolean isHardAffinity(JobExecutionContext callbackContext) {
        return false;
    }

    @Override
    protected boolean isAllowedAffinity(JobExecutionContext callbackContext) {
        return false;
    }

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {

    }
}
