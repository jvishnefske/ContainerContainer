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

package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Intercepts and maps any un-caught exceptions thrown by the REST API.
 *
 * @author Shay Yaakov
 */
@Component
@Provider
@Slf4j
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        Response blockedResponse = BlockedQueryResponseHelper.blockedQueryResponseOrNull(exception);
        if (blockedResponse != null) {
            return blockedResponse;
        }
        String message = exception.getMessage();
        if (StringUtils.isBlank(message)) {
            message = "An unexpected error has occurred, please check Artifactory logs for further details.";
            log.error("An unexpected error has occurred", exception);
        } else {
            log.error(message, exception);
        }
        ErrorResponse errorResponse = new ErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
        return Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON).entity(
                errorResponse).build();
    }
}
