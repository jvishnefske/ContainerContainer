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

import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.exception.RestException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Intercepts and maps {@link RestException} exceptions thrown by the REST API.
 *
 * @author Shay Yaakov
 */
@Component
@Provider
public class RestExceptionMapper implements ExceptionMapper<RestException> {

    @Override
    public Response toResponse(RestException exception) {
        int statusCode = exception.getStatusCode();
        ErrorResponse errorResponse = new ErrorResponse(statusCode, exception.getMessage());
        Response.ResponseBuilder responseBuilder = Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse);
        Map<String, String> headers = exception.getHeaders();
        if (headers != null) {
            headers.forEach(responseBuilder::header);
        }
        return responseBuilder.build();
    }
}
