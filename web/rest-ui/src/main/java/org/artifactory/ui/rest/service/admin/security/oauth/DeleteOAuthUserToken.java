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

package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUserToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteOAuthUserToken<T extends OAuthUserToken> implements RestService<T> {
    private static final Logger log = LoggerFactory.getLogger(DeleteOAuthProviderSettings.class);

    @Autowired
    private InternalSecurityService internalSecurityService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        T userToken = request.getImodel();
        log.debug("Deleting OAuth token for provider: '{}' for user: '{}'", userToken.getProviderName(),
                userToken.getUserName());
        boolean toDeleteUserProperty = false;
        // user can only delete his own tokens, admin can delete any.
        if (internalSecurityService.isAdmin()) {
            toDeleteUserProperty = true;
        } else {
            if (userToken.getUserName().equals(internalSecurityService.currentUser().getUsername())) {
                toDeleteUserProperty = true;
            }
        }
        if (toDeleteUserProperty) {
            internalSecurityService.deleteUserProperty(userToken.getUserName(),
                    OAUTH_INFO_PROPERTY + userToken.getProviderName());
            log.debug("Successfully deleted token for provider: '{}' for user: '{}'", userToken.getProviderName(),
                    userToken.getUserName());
        }else {
            response.error("no privilege to delete provider for other user");
            response.responseCode(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
