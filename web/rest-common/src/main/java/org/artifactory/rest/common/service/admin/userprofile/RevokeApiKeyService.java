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

package org.artifactory.rest.common.service.admin.userprofile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class RevokeApiKeyService implements RestService {

    private final SecurityService securityService;
    private final ApiKeyManager apiKeyManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (securityService.isAnonymous()) {
            return;
        }
        String userName = request.getPathParamByKey("id");
        boolean deleteAll = request.getQueryParamByKey("deleteAll").equals("1");
        String authUserName = securityService.currentUsername();
        if (StringUtils.isEmpty(userName) && !deleteAll) {
            if (securityService.isApiKeyAuthentication()) {
                setError(response);
                return;
            }
            userName = authUserName;
        }
        boolean isAdmin = securityService.isAdmin();
        if (((!StringUtils.isEmpty(userName) && isAdmin) || (userName.equals(authUserName)) && !deleteAll)) {
            // revoke apiKey
            revokeApiKey(response, userName);
        } else {
            if (StringUtils.isEmpty(userName) && isAdmin && deleteAll) {
                // revoke all api keys
                revokeAllApiKeys(response);
            } else {
                response.responseCode(HttpServletResponse.SC_FORBIDDEN);
            }
        }
    }

    private void setError(RestResponse response) {
        response.responseCode(HttpServletResponse.SC_FORBIDDEN);
        response.error("The use of apiKey as authentication is forbidden for this api call");
    }

    /**
     * revoke all api keys
     *
     * @param response - encapsulate data related tto response
     */
    private void revokeAllApiKeys(RestResponse response) {
        boolean revokeSucceeded = apiKeyManager.revokeAllTokens();
        if (revokeSucceeded) {
            response.info("All api keys have been successfully revoked");
            if (log.isDebugEnabled()) {
                log.debug("All api keys have been successfully revoked by: '{}'",
                        securityService.currentUsername());
            }
        } else {
            log.error("Error revoking all api keys");
            response.error("Error revoking all api keys");
        }
    }

    /**
     * revoke api key for specific user
     *
     * @param response - artifactory rest response
     * @param userName - user name to revoke api
     */
    private void revokeApiKey(RestResponse response, String userName) {
        if (apiKeyManager.revokeToken(userName)) {
            response.info("Api key for user: '" + userName + "' has been successfully revoked");
            if (log.isDebugEnabled()) {
                log.debug("Api key for user: '{}' has been successfully revoked by user : '{}'", userName,
                        securityService.currentUsername());
            }
        } else {
            response.error("Unable to revoke API key for user: " + userName +
                    ". The user may not exist or does not have an API key property.");
        }
    }
}
