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


import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;

/**
 * DB token manager. Should be replaced with Access in the future
 *
 * @author Chen Keinan
 */
public interface TokenManager {

    /**
     * create token for given user
     *
     * @param tokenManagerKey - tokenManagerKey that holds the target token's username and his type
     * @return Token data
     */
    TokenKeyValue createToken(TokenManagerKey tokenManagerKey);

    /**
     * get token for given user
     *
     * @param tokenManagerKey - tokenManagerKey that holds the target token's username and his type
     * @return Token data
     */
    TokenKeyValue getToken(TokenManagerKey tokenManagerKey);
}