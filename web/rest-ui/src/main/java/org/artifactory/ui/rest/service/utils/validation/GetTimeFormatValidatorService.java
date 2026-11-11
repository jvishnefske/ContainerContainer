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

package org.artifactory.ui.rest.service.utils.validation;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Validates input according to {@link SimpleDateFormat} time format.
 *
 * @author Yossi Shaul
 */
@Component
public class GetTimeFormatValidatorService implements RestService {

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String dateFormat = request.getQueryParamByKey("dateformat");
        if (StringUtils.isEmpty(dateFormat)) {
            response.error("Please enter a valid date format");
        } else {
            try {
                new SimpleDateFormat(dateFormat);
                response.info("Date format validated");
            } catch (IllegalArgumentException e) {
                response.error("Please enter a valid date format");
            }
        }
    }
}
