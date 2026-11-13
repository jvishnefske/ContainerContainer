/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.MTlsClient;
import org.jfrog.access.client.auth.exception.PasswordPolicyException;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.token.TokenVerifyResponse;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.rest.token.TokenVerifyResponseImpl;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UserWithGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The {@code auth()} and {@code cachedAuth()} surfaces. Both are the same object: there is no remote call to cache.
 * <p>
 * {@code principalCanDo} answers {@code false}: embedded access does not evaluate fine-grained permission scopes
 * carried by tokens, so a token never grants more than Artifactory's own ACL checks allow. {@code mtls()} returns
 * a surface that refuses every call, because mTLS is not supported and is reported disabled by the config surface.
 */
final class EaAuthClient {

    private static final Logger log = LoggerFactory.getLogger(EaAuthClient.class);

    private final EaRuntime rt;
    private final EaAuthenticator authenticator;
    private final EaTokenClient tokens;
    private final MTlsClient mtls = FailClosed.of(MTlsClient.class, "auth.mtls");

    EaAuthClient(EaRuntime rt, EaAuthenticator authenticator, EaTokenClient tokens) {
        this.rt = rt;
        this.authenticator = authenticator;
        this.tokens = tokens;
    }

    /**
     * Authenticates a username and password.
     *
     * @param request the credentials
     * @return the user with its groups
     */
    public UserWithGroups authenticate(LoginRequest request) {
        return authenticator.principal(authenticator.byPassword(request.getUsername(), request.getPassword()),
                java.util.Set.of()).user();
    }

    /**
     * Same as {@link #authenticate}.
     *
     * @param request the credentials
     * @return the user with its groups
     */
    public UserWithGroups login(LoginRequest request) {
        return authenticate(request);
    }

    /**
     * Authenticates an {@code Authorization} header value: {@code Bearer <token>} or {@code Basic <base64>}.
     *
     * @param header the header value
     * @return the successful result
     */
    public LoginResult loginByAuthHeader(String header) {
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authenticator.byToken(header.substring(7).trim());
        }
        if (header != null && header.regionMatches(true, 0, "Basic ", 0, 6)) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_UNAUTHORIZED, "Bad credentials");
            }
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_UNAUTHORIZED, "Bad credentials");
            }
            return authenticator.login(decoded.substring(0, colon), decoded.substring(colon + 1));
        }
        throw EaErrors.http(EaErrors.UNAUTHORIZED, MessageModelCode.CODE_UNAUTHORIZED, "Unsupported authorization");
    }

    /**
     * Verifies a token.
     *
     * @param token the token
     * @return the verification response
     */
    public TokenVerifyResponse verifyToken(String token) {
        TokenVerifyResult result = tokens.verify(token);
        return new TokenVerifyResponseImpl(token, result.isSuccessful(), result.getReason());
    }

    /**
     * Verifies a token; the audience argument is ignored because verification always uses the Artifactory
     * service id.
     *
     * @param token    the token
     * @param audience ignored
     * @return the verification response
     */
    public TokenVerifyResponse verifyToken(String token, String audience) {
        return verifyToken(token);
    }

    /**
     * Checks a candidate password against the IA6 password policy.
     *
     * @param password the candidate
     * @throws PasswordPolicyException if the policy refuses it
     */
    public void verifyPassword(String password) throws PasswordPolicyException {
        EaOutcome<EaOutcome.Done> verdict = rt.passwordPolicy().validate(null, password);
        if (verdict instanceof EaOutcome.Refused<EaOutcome.Done> refused) {
            throw new PasswordPolicyException(refused.reason());
        }
    }

    /**
     * Does not grant: token permission scopes are not evaluated by embedded access.
     *
     * @param principal the principal
     * @param scope     the scope
     * @return {@code false}
     */
    public boolean principalCanDo(AccessPrincipal principal, PermissionScope scope) {
        log.debug("principalCanDo({}, {}) answered false: not evaluated by embedded access", principal, scope);
        return false;
    }

    /**
     * Does not grant: token permission scopes are not evaluated by embedded access.
     *
     * @param principal the principal
     * @param scope     the scope
     * @param useCache  ignored
     * @return {@code false}
     */
    public boolean principalCanDo(AccessPrincipal principal, PermissionScope scope, boolean useCache) {
        return principalCanDo(principal, scope);
    }

    /**
     * Returns the mTLS surface, which refuses every call.
     *
     * @return the refusing surface
     */
    public MTlsClient mtls() {
        return mtls;
    }
}
