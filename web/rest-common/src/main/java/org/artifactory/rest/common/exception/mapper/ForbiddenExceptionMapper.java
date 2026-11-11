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

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.http.ForbiddenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;

/**
 * Intercepts and maps {@link ForbiddenException} exceptions thrown by the REST API.
 *
 * @author Yuval Reches
 */
@Component
@Provider
public class ForbiddenExceptionMapper extends ArtifactoryRestExceptionMapperBase<ForbiddenException> {

    @Autowired
    public ForbiddenExceptionMapper(CentralConfigService centralConfig, SecurityService securityService,
            BasicAuthenticationEntryPoint authenticationEntryPoint) {
        super(centralConfig, securityService, authenticationEntryPoint);
    }

    @Override
    boolean isForbiddenException(ForbiddenException exception) {
        return true;
    }

    @Override
    boolean isUnauthorizedException(ForbiddenException exception) {
        return false;
    }

    @Override
    Response getExceptionResponse(ForbiddenException exception) {
        ErrorResponse errorResponse = new ErrorResponse(FORBIDDEN.getStatusCode(), exception.getMessage());
        return Response.status(FORBIDDEN).type(APPLICATION_JSON).entity(errorResponse).build();
    }
}
