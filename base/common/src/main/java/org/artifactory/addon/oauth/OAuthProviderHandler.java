/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2021 JFrog Ltd.
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

package org.artifactory.addon.oauth;

import jakarta.ws.rs.core.Response;

/**
 * OAuth 2.0 Provider implementation following rfc7636:
 * https://datatracker.ietf.org/doc/html/rfc7636
 * The implementation is combined with the UI.
 *
 * 1. The UI handle the user authentication and the first 'authorise' client requests is forward to Artifactory with
 * the authenticated user session.
 * 2. Artifactory will store the codeChallenge of the authorise request on the user as property and will return to
 * the UI the callback URL of the authorise user.
 * 3. The UI will execute the callback URL and the authorise process will get completed.
 * 4. The client will try to generate a token with an un authenticated request to artifactory.
 * 5. Artifactory will verify the request by the OAUTH 2.0 protocol and will find the requested user by the code
 * challenge property.
 * 6. Artifactory will generate the user token and return it as OAUTH token response.
 *
 * @author BarakH
 */
public interface OAuthProviderHandler {

    /**
     * Handle the authorize request.
     *
     * @return to the UI the redirectUri with the encrypted codeChallenge as query param.
     */
    default Response authorize(String clientId, String codeChallenge, String codeChallengeMethod, String redirectUri,
            String responseType, String state) {
        return null;
    }

    /**
     * The method will verify the code following the OAuth 2.0 protocol as described here:
     * https://datatracker.ietf.org/doc/html/rfc7636#section-4.6
     *
     * @return A valid token response if the code verification passed or an Authentication exception
     */
    default Response token(String clientId, String code, String codeVerifier, String grantType, String redirectUri) {
        return null;
    }
}
