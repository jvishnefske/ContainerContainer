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

package org.artifactory.security.token;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.authentication.OauthAuthentication;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthDockerErrorModel;
import org.artifactory.security.props.auth.model.OauthErrorModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.providermgr.ArtifactoryCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Artifactory specific token cache implementation
 *
 * @author Chen Keinan
 */
@Component
public class ArtifactoryTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryTokenProvider.class);

    public OauthModel getToken(@NonNull ArtifactoryCacheKey artifactoryCacheKey) {
        OauthModel oauthModel = null;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            if (authentication instanceof OauthAuthentication) {
                log.debug("Found Oauth type authentication for user: '{}'", username);
                return ((OauthAuthentication) authentication).getModel();
            }
            if (authentication instanceof AccessTokenAuthentication) {
                log.debug("Found AccessToken type authentication for user: '{}'", username);
                AuthenticationModel authenticationModel = new AuthenticationModel();
                authenticationModel.setToken(((AccessTokenAuthentication) authentication).getAccessTokenValue());
                return authenticationModel;
            }
            String bearerToken = artifactoryCacheKey.getBearerToken();
            if (StringUtils.isNotBlank(bearerToken)) {
                log.debug("Got a bearer token for user: '{}', returning the same one", artifactoryCacheKey.getUser());
                AuthenticationModel authenticationModel = new AuthenticationModel();
                authenticationModel.setToken(bearerToken);
                return authenticationModel;
            }
            log.trace("Generating token for user: '{}'", artifactoryCacheKey.getUser());
            oauthModel = artifactoryCacheKey.getProviderMgr()
                    .fetchAndStoreTokenFromProvider(new TokenManagerKey(username, artifactoryCacheKey.getTokenType()));
            return oauthModel;
        } finally {
            if ((oauthModel instanceof OauthErrorModel || oauthModel instanceof OauthDockerErrorModel)) {
                log.trace("An error occurred while trying to generate token for user: '{}'",
                        artifactoryCacheKey.getUser());
            }
        }
    }
}
