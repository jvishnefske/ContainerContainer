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

package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.webapp.servlet.RequestUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Allows anonymous to ping this instance even if anonymous access is disabled  when the 'ping.allowUnauthenticated'
 * flag is set (RTFACT-8239).
 *
 * @author Dan Feldman
 */
@Slf4j
public class AnonymousPingInterceptor implements AnonymousAuthenticationInterceptor {
    private static final String PING_ENDPOINT =
            "/api/" + SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_PING;
    private static final String SERVICE_ID_ENDPOINT =
            "/api/" + SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_SERVICE_ID;
    private static final String PRESTOP_ENDPOINT =
            "/api/v1/" + SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_PRESTOP;

    @Override
    public boolean accept(HttpServletRequest request) {
        return unauthenticatedPingAllowed(request);
    }

    private boolean unauthenticatedPingAllowed(HttpServletRequest request) {
        try {
            return request.getMethod().equals("GET") && isProbeEndpoint(request)
                    && (!RequestUtils.isBasicAuthHeaderPresent(request)
                    || RequestUtils.extractUsernameFromRequest(request).equalsIgnoreCase("anonymous"));
        } catch (Exception e) {
            log.debug("Caught exception: ", e);
            return false;
        }
    }

    private boolean isProbeEndpoint(HttpServletRequest request) {
        String pathFromRequest = RequestUtils.getServletPathFromRequest(request);
        return StringUtils.startsWith(pathFromRequest, PING_ENDPOINT) ||
                StringUtils.startsWith(pathFromRequest, PRESTOP_ENDPOINT) ||
                StringUtils.startsWith(pathFromRequest, SERVICE_ID_ENDPOINT);
    }
}
