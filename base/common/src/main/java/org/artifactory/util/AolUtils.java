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

package org.artifactory.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.DockerRepositoryAction;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * @author Lior Azar
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AolUtils {
    private static final Logger log = LoggerFactory.getLogger(AolUtils.class);
    public static final String NOT_SUPPORTED_FOR_CLOUD =
            "Function is not supported when running on the cloud";
    public static final String NOT_DASHBOARD_USER = "Artifactory Online does not support accessing this API endpoint" +
            ", please contact support@jfrog.com for further assistance if required.";

    public static void assertNotAol(String functionName) {
        if (isAol()) {
            log.warn("{} is not supported when running on the cloud", functionName);
            throw new ForbiddenWebAppException(NOT_SUPPORTED_FOR_CLOUD);
        }
    }

    public static void sendDockerRepoEvent(String repoKey, String version, DockerRepositoryAction action) {
        if (isAol() && !ConstantValues.dev.getBoolean()) {
            ContextHelper.get().beanForType(AolService.class).sendDockerRepoEvent(repoKey, version, action);
        }
    }

    /**
     * Throw bad request if using AOL and user is not a Dashboard user
     */
    public static void assertDashboardUser() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            throw new BadRequestException(NOT_DASHBOARD_USER);
        }
    }

    /**
     * Throw bad request if using AOL and user is not a Dashboard user or a service admin token made the request
     */
    public static void assertApiAllowedInternally(String message) {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser() && !isServiceAdminToken() && !hasMcScope()) {
            throw new BadRequestException(message);
        }
    }

    public static boolean isServiceAdminToken() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication instanceof AccessTokenAuthentication) {
            JwtAccessToken accessToken =
                    ((AccessTokenAuthentication) authentication).getAccessToken();
            return TokenType.isServiceAdminToken(accessToken.getSubject(),
                    StringUtils.join(accessToken.getScope(), " "));
        }
        return false;
    }

    public static boolean hasMcScope() {
        return ContextHelper.get().beanForType(SecurityService.class).hasMcScope();
    }

    public static boolean isAol() {
        return ContextHelper.get().beanForType(AolService.class).isAol();
    }

    public static boolean skipDockerDns() {
        return ContextHelper.get().beanForType(AolService.class).skipDockerDns();
    }
}
