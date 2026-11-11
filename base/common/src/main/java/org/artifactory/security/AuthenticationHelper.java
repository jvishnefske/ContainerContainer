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

package org.artifactory.security;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.jfrog.access.common.ServiceType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Provides an easy API for getting the Authentication object
 *
 * @author Noam Tenne
 */
public abstract class AuthenticationHelper {
    private AuthenticationHelper() {
        // utility class
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Nullable
    public static String getRemoteAddress(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails) {
            return ((WebAuthenticationDetails) details).getRemoteAddress();
        }
        if (authentication instanceof PropsAuthenticationToken || authentication instanceof AccessTokenAuthentication) {
            return RequestThreadLocal.getClientAddress();
        }
        return null;
    }

    public static boolean isJFrogFrontendAuthentication(@Nonnull Authentication authentication) {
        if (authentication.getClass().isAssignableFrom(AccessTokenAuthentication.class)) {
            String tokenIssuer = ((AccessTokenAuthentication) authentication).getAccessToken().getIssuer();
            return StringUtils.isNotBlank(tokenIssuer) && tokenIssuer.startsWith(ServiceType.FRONTEND);
        }
        return false;
    }
}
