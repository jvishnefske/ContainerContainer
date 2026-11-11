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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * @author Yinon Avraham
 */
public class AccessTokenAuthentication extends AbstractAuthenticationToken {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenAuthentication.class);

    private JwtAccessToken accessToken;
    private final String accessTokenValue;
    //TODO [YA] remove the principal once supporting access token authorization. Then the principal is actually the
    //token.
    private final Object principal;

    /**
     * Creates a token with the supplied array of authorities.
     *
     * @param tokenValue  the access token represented by this authentication.
     * @param principal   the principal represented by this authentication.
     * @param authorities the collection of <code>GrantedAuthority</code>s for the
     * @throws IllegalArgumentException if the given token value is not in the expected format.
     */
    public AccessTokenAuthentication(@Nonnull String tokenValue, @Nullable Object principal,
            @Nullable Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        try {
            this.accessToken = JwtAccessTokenImpl.parseTokenValue(tokenValue);
        } catch (IllegalArgumentException ex) {
            log.debug("Cannot parse access token value, " +
                    "this can be happening this is valid when token is reference token", ex);
        }
        this.accessTokenValue = tokenValue;
        this.principal = principal;
    }

    public AccessTokenAuthentication(@Nonnull String tokenValue, @Nonnull JwtAccessToken accessToken,
            @Nullable Object principal,
            @Nullable Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.accessToken = accessToken;
        this.accessTokenValue = tokenValue;
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * Creates a token with the supplied array of authorities.
     *
     * @param accessToken the access token represented by this authentication.
     * @param principal   the principal represented by this authentication.
     * @param authorities the collection of <code>GrantedAuthority</code>s for the
     */
    public AccessTokenAuthentication(@Nonnull JwtAccessToken accessToken, @Nullable Object principal,
            @Nullable Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.accessToken = accessToken;
        this.accessTokenValue = accessToken.getTokenValue();
        this.principal = principal;
    }

    public JwtAccessToken getAccessToken() {
        return accessToken;
    }

    @Nonnull
    public String getAccessTokenValue() {
        return accessTokenValue;
    }

    @Override
    public Object getCredentials() {
        return accessToken.getTokenValue();
    }

    @Nullable
    @Override
    public Object getPrincipal() {
        return principal;
    }
}
