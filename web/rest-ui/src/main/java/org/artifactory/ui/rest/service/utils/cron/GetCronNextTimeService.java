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

package org.artifactory.ui.rest.service.utils.cron;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.cron.CronUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.utils.cron.CronTime;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Chen Keinan
 */
@Component
public class GetCronNextTimeService implements RestService {
    static final String EMPTY_CRON_ERROR_MESSAGE = "emptyCron";
    static final String INVALID_CRON_ERROR_MESSAGE = "invalidCron";
    static final String SHORT_CRON_ERROR_MESSAGE = "shortCron";
    static final String PAST_CRON_WARNING_MESSAGE = "pastCron";

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CronTime cronTime = getCronTime(request, response);
        response.iModel(cronTime);
    }

    /**
     * get cron time model
     *
     * @param request  - encapsulate data related to request
     * @param response - encapsulate data require for response
     * @return cron time model
     */
    private CronTime getCronTime(ArtifactoryRestRequest request, RestResponse response) {
        CronTime cronTime = null;
        String cronExp = request.getQueryParamByKey("cron");
        String nextExecutionTime = getNextRunTime(cronExp, response);
        if (nextExecutionTime != null) {
            if (Boolean.valueOf(request.getQueryParamByKey(ReplicationConfigService.IS_REPLICATION_QUERY_PARAM))
                    && CronUtils.isCronIntervalLessThanMinimum(cronExp)) {
                response.error(SHORT_CRON_ERROR_MESSAGE);
            }
            cronTime = new CronTime(nextExecutionTime);
        }
        return cronTime;
    }

    /**
     * return next execution time from cron exp
     *
     * @param cronExpression      - cron expression
     * @param artifactoryResponse - encapsulate data require for response
     * @return next execution time
     */
    @VisibleForTesting
    String getNextRunTime(String cronExpression, RestResponse artifactoryResponse) {
        if (StringUtils.isBlank(cronExpression)) {
            artifactoryResponse.error(EMPTY_CRON_ERROR_MESSAGE);
            return null;
        } else {
            return calculateNextRunTime(cronExpression, artifactoryResponse);
        }
    }

    private String calculateNextRunTime(String cronExpression, RestResponse artifactoryResponse) {
        try {
            Date nextExecution = CronUtils.getNextExecution(cronExpression);
            if (nextExecution != null) {
                return formatDate(nextExecution);
            }
        } catch (Exception e) {
            artifactoryResponse.error(INVALID_CRON_ERROR_MESSAGE);
            return null;
        }
        artifactoryResponse.warn(PAST_CRON_WARNING_MESSAGE);
        return null;
    }

    /**
     * format date to String
     *
     * @param nextRunDate next execution time in date format
     * @return next execution time as String
     */
    private String formatDate(Date nextRunDate) {
        return nextRunDate.toString();
    }
}
