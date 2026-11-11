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

package org.artifactory.security.props.auth;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Set;

/**
 * @author Chen Keinan
 */
@Component
@Slf4j
public class ApiKeyManager implements TokenManager, EncryptedTokenManager {

    public static final String API_KEY = "apiKey";

    public static final String API_KEY_HEADER = "X-JFrog-Art-Api";

    public static final String OLD_API_KEY_HEADER = "X-Api-Key";

    private static final Set<String> PROP_KEYS = Set.of(API_KEY);

    private UserGroupService userGroupService;

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Override
    public Set<String> getPropKeys() {
        return PROP_KEYS;
    }

    @Override
    public TokenKeyValue createToken(TokenManagerKey tokenManagerKey) {
        String userName = tokenManagerKey.username();
        TokenKeyValue token = null;
        String tokenValue = null;
        String key = API_KEY;
        try {
            tokenValue = JFrogCryptoHelper.generateUniqueApiKeyToken();
            boolean tokenPropCreated = userGroupService.createPropsToken(userName, key, tokenValue);
            if (tokenPropCreated) {
                token = new TokenKeyValue(key, tokenValue);
            }
        } catch (GeneralSecurityException e) {
            log.debug("error with generating token for user: '{}' with key {}", userName, key, e);
        } catch (Exception e) {
            log.debug("error with adding token for user: '{}' with key {} and value {}", userName, key, tokenValue, e);
        }
        return token;
    }

    public TokenKeyValue createToken(String username) {
        return createToken(new TokenManagerKey(username, TokenType.APIKEY));
    }

    @Override
    public TokenKeyValue getToken(TokenManagerKey tokenManagerKey) {
        String userName = tokenManagerKey.username();
        TokenKeyValue token = null;
        String value = userGroupService.getPropsToken(userName, API_KEY);
        if (value != null) {
            token = new TokenKeyValue(API_KEY, CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), value));
        }
        return token;
    }

    public TokenKeyValue getToken(String username) {
        return getToken(new TokenManagerKey(username, TokenType.APIKEY));
    }

    /**
     * delete current token and create  new token (user props) and store it in DB
     *
     * @param userName - userName to refresh token for
     * @return Token data
     */
    public TokenKeyValue refreshToken(String userName) {
        TokenKeyValue token = null;
        try {
            String value = JFrogCryptoHelper.generateUniqueApiKeyToken();
            token = updateToken(userName, value);
        } catch (GeneralSecurityException e) {
            log.debug("error with refreshing token for user: '{}'", userName, e);
        }
        return token;
    }

    private TokenKeyValue updateToken(String userName, String value) {
        TokenKeyValue token = null;
        try {
            boolean propsToken = userGroupService.updatePropsToken(userName, API_KEY, value);
            if (propsToken) {
                token = new TokenKeyValue(API_KEY, value);
            }
        } catch (Exception e) {
            log.debug("error with updating token for user: '{}' with key {} and value {}", userName, API_KEY, value, e);
        }
        return token;
    }

    /**
     * delete user's token from db
     *
     * @param userName - userName to delete token for
     * @return Token data
     */
    public boolean revokeToken(String userName) {
        boolean tokenRevokeSucceeded = false;
        try {
            tokenRevokeSucceeded = userGroupService.revokePropsToken(userName, API_KEY, true);
        } catch (Exception e) {
            log.debug("error with revoking token for user: '{}' with key {}", userName, API_KEY, e);
        }
        return tokenRevokeSucceeded;
    }

    /**
     * delete all tokens in user props table DB
     *
     * @return Token data
     */
    public boolean revokeAllTokens() {
        boolean tokenRevokeSucceeded = false;
        try {
            userGroupService.revokeAllPropsTokens(API_KEY, true);
            tokenRevokeSucceeded = true;
        } catch (Exception e) {
            log.debug("error with revoking all tokens with key {}", API_KEY, e);
        }
        return tokenRevokeSucceeded;
    }
}
