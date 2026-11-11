/*
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

package org.artifactory.repo.interceptor.download;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;

import org.artifactory.entitlement.EntitlementType;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.api.request.ArtifactoryResponse;

import org.artifactory.api.security.SecurityService;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.HaSystemAuthenticationToken;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.TokenType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;


/**
 * This interceptor blocks operations for non-service admin tokens and non-system users when Cold Storage
 * entitlement is enabled.
 * Service admin tokens and system users are allowed all operations.
 * @author Pankaj Sharma
 */
@Component
@Slf4j
public class ColdStorageOperationsInterceptorImpl implements ColdStorageOperationsInterceptor {

    private static final int FORBIDDEN_STATUS = 403;

    private static final String COLD_STORAGE_OPERATION_BLOCKED_ERROR_MESSAGE = "The requested operation is " +
            "forbidden. Any operations on the repositories in the archive instance are not permitted.";

    private final AddonsManager addonsManager;

    public ColdStorageOperationsInterceptorImpl(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void interceptColdStorageOperations(ArtifactoryRequest request, ArtifactoryResponse response) {

        if (shouldBlockOperation(request)) {
            String username = getCurrentUsername();
            String uri = request != null ? request.getUri() : "null";
            log.info("Blocking operation on cold storage instance: {} - User: {}", uri, username);
            try {
                response.sendError(FORBIDDEN_STATUS, COLD_STORAGE_OPERATION_BLOCKED_ERROR_MESSAGE, log);
            } catch (IOException e) {
                log.warn("Failed to send error response for blocked operation: {}", e.getMessage());
                // Handle gracefully by not re-throwing the exception
            }
        }
    }



    private boolean shouldBlockOperation(ArtifactoryRequest request) {

        // Check if the feature toggle is enable
        if (!isRetentionArchiveDirectOperationsBlocked()) {
            log.debug("Cold storage direct operations block is disabled by feature toggle");
            return false;
        }

        //check if this is a cold instance
        if (!addonsManager.isEntitled(EntitlementType.COLD_STORAGE)) {
            log.debug("Not a cold instance, allowing operation");
            return false;
        }
        
        // Check if request is null
        if (request == null) {
            log.debug("Null request received, blocking operation");
            return true;
        }

        // Check if this is a service admin token
        if (isServiceAdminToken()) {
            log.debug("Service admin token detected, allowing all operations");
            return false;
        }
        
        // Check if this is the system user (for observability and other system operations)
        if (isSystemUser()) {
            log.debug("System user detected, allowing all operations");
            return false;
        }

        // For non-service admin tokens and non-system users, block all operations
        log.debug("Service Token not Found and not system user - blocking operation on cold storage instance");
        return true;
    }





    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null ? authentication.getName() : "unknown";
        } catch (Exception e) {
            log.debug("Error getting current username", e);
            return "unknown";
        }
    }

    private boolean isRetentionArchiveDirectOperationsBlocked() {
        return ConstantValues.retentionArchiveDirectOperationsBlocked.getBoolean();
    }

    /**
     * Checks if the current user is the system user.
     * System user is identified by either:
     * 1. Username equals SecurityService.USER_SYSTEM ("_system_")
     * 2. Authentication token is SystemAuthenticationToken or HaSystemAuthenticationToken
     * 
     * @return true if current user is system user, false otherwise
     */
    private boolean isSystemUser() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            return false;
        }
        
        // Check if authentication token is system token
        if (authentication instanceof SystemAuthenticationToken || 
                authentication instanceof HaSystemAuthenticationToken) {
            return true;
        }
        
        // Check if username is system user
        String username = getCurrentUsername();
        return SecurityService.USER_SYSTEM.equals(username);
    }

    public static boolean isServiceAdminToken() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication instanceof AccessTokenAuthentication accessAuthenticationToken) {
            JwtAccessToken accessToken = accessAuthenticationToken.getAccessToken();
            if (accessToken != null) {
                return TokenType.isServiceAdminToken(accessToken.getSubject(),
                        StringUtils.join(accessToken.getScope(), " "));
            }
        }
        return false;
    }
}
