/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.auth.LoginResult;

/**
 * The {@code grpcAuth(..)} surface, used for every Basic and UI login. A password that is a JWT is verified as a
 * token, anything else as a password (see {@link EaAuthenticator}). There are no session cookies: results carry
 * no cookie. HTTP-request based variants (HTTP SSO, mTLS, servlet requests) are not implemented and fail closed.
 */
final class EaGrpcAuthClient {

    private final EaAuthenticator authenticator;

    EaGrpcAuthClient(EaAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Authenticates a username with a password or token.
     *
     * @param username the username; may be empty when {@code secret} is a token
     * @param secret   the password or token
     * @return the successful result
     */
    public LoginResult authenticate(String username, String secret) {
        return authenticator.login(username, secret);
    }

    /**
     * Authenticates a UI login; same rules as {@link #authenticate}.
     *
     * @param username the username
     * @param password the password
     * @return the successful result
     */
    public LoginResult uiLogin(String username, String password) {
        return authenticator.login(username, password);
    }

    /**
     * Authenticates a token on behalf of a user; the username is not used beyond the token's own subject.
     *
     * @param username ignored
     * @param token    the token
     * @return the successful result
     */
    public LoginResult loginByAuthToken(String username, String token) {
        return authenticator.byToken(token);
    }
}
