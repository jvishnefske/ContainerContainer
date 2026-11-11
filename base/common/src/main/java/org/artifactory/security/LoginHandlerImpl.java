/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.TokenManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.util.date.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author Chen Keinan
 */
@Component
public class LoginHandlerImpl implements LoginHandler {

    private final TokenManager oauthManager;

    @Autowired
    public LoginHandlerImpl(OauthManager oauthManager) {
        this.oauthManager = oauthManager;
    }

    @Override
    public OauthModel doBasicAuthWithDb(TokenManagerKey tokenManagerKey) {
        return doBasicAuthWithDb(tokenManagerKey, false);
    }

    @Override
    public OauthModel getOrCreateOauth2Token(TokenManagerKey tokenManagerKey) {
        return doBasicAuthWithDb(tokenManagerKey, true);
    }

    private OauthModel doBasicAuthWithDb(TokenManagerKey tokenManagerKey, boolean isOAuth2) {
        TokenKeyValue tokenKeyValue = oauthManager.getToken(tokenManagerKey);
        if (isOAuth2) {
            OAuth2TokenModel oAuth2TokenModel = new OAuth2TokenModel(tokenKeyValue.getToken());
            oAuth2TokenModel.setExpiresIn(tokenKeyValue.getExpiresIn());
            return oAuth2TokenModel;
        }
        return new AuthenticationModel(tokenKeyValue.getToken(),
                DateUtils.formatBuildDate(System.currentTimeMillis()), tokenKeyValue.getExpiresIn());
    }
}
