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

package org.artifactory.rest.common;


import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.util.AuthUtils;
import org.glassfish.jersey.server.ContainerResponse;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;


/**
 * Intercepts all Jersey client error responses (status code >= 400) and sends the response
 * as a JSON object. Also adds a response entity in case it was not specified.
 *
 * @author Shay Yaakov
 */
@Component
public class RestResponseFilter implements ContainerResponseFilter {

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpServletResponse response;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        // add message to response headers
        AuthUtils.addSessionStatusToHeaders(response, uriInfo, this.request);
        if (status >= 400) {
            Object entity = response.getEntity();
            if (entity == null) {
                ErrorResponse errorResponse = new ErrorResponse(status, response.getStatusInfo().getReasonPhrase());
                createJsonErrorResponse((ContainerResponse) response, errorResponse);
            } else if (entity instanceof String && !MediaType.APPLICATION_JSON_TYPE.equals(response.getMediaType())) {
                ErrorResponse errorResponse = new ErrorResponse(status, (String) entity);
                createJsonErrorResponse((ContainerResponse) response, errorResponse);
            }
        }
    }

    private void createJsonErrorResponse(ContainerResponse response, ErrorResponse errorResponse) {
        response.setEntity(errorResponse);
        response.setMediaType(MediaType.APPLICATION_JSON_TYPE);
    }
}
