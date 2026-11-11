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

import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenManagerKey;

/**
 * @author Chen  Keinan
 */
public interface LoginHandler {

    /**
     * do basic authentication
     *
     * @return {@link org.artifactory.security.props.auth.model.AuthenticationModel} with the user token if succeed
     */
    OauthModel doBasicAuthWithDb(TokenManagerKey tokenManagerKey);

    /**
     * Create a generic token for the given user
     *
     * @return {@link org.artifactory.security.props.auth.model.OAuth2TokenModel} with the user token if succeed
     */
    OauthModel getOrCreateOauth2Token(TokenManagerKey tokenManagerKey);
}
