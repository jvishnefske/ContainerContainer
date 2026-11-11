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

package org.artifactory.security.access;

import org.artifactory.api.security.access.CreatedTokenInfo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Yinon Avraham
 */
public class CreatedTokenInfoImpl implements CreatedTokenInfo {

    private final String tokenValue;
    private final String tokenType;
    private final String refreshToken;
    private final String scope;
    private final Long expiresIn;
    private final String referenceToken;

    public CreatedTokenInfoImpl(@Nonnull String tokenValue, @Nonnull String tokenType, @Nullable String refreshToken,
            @Nonnull String scope, @Nullable Long expiresIn, @Nullable String referenceToken) {
        this.tokenValue = tokenValue;
        this.tokenType = tokenType;
        this.refreshToken = refreshToken;
        this.scope = scope;
        this.expiresIn = expiresIn;
        this.referenceToken = referenceToken;
    }

    @Nonnull
    @Override
    public String getTokenValue() {
        return tokenValue;
    }

    @Nonnull
    @Override
    public String getTokenType() {
        return tokenType;
    }

    @Nullable
    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Nonnull
    @Override
    public String getScope() {
        return scope;
    }

    @Nullable
    @Override
    public Long getExpiresIn() {
        return expiresIn;
    }

    @Nullable
    @Override
    public String getReferenceToken() {
        return referenceToken;
    }
}
