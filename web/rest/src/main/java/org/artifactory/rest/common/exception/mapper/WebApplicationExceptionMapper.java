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

import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * @author NadavY
 */
@Component
@Provider
public class WebApplicationExceptionMapper extends ArtifactoryRestExceptionMapperBase<WebApplicationException> {

    @Autowired
    public WebApplicationExceptionMapper(CentralConfigService centralConfig, SecurityService securityService,
            BasicAuthenticationEntryPoint authenticationEntryPoint) {
        super(centralConfig, securityService, authenticationEntryPoint);
    }

    @Override
    boolean isForbiddenException(WebApplicationException exception) {
        return exception.getResponse().getStatus() == HttpStatus.SC_FORBIDDEN;
    }

    @Override
    boolean isUnauthorizedException(WebApplicationException exception) {
        return exception.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED;
    }

    @Override
    Response getExceptionResponse(WebApplicationException exception) {
        return exception.getResponse();
    }
}
