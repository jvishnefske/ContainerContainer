/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.google.common.cache.CacheBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.artifactory.security.access.embedded.core.EaHashes;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaToken;
import org.artifactory.security.access.embedded.core.Revocability;
import org.artifactory.security.access.embedded.core.ScopeGrammar;
import org.artifactory.security.access.embedded.core.TokenClaimsFactory;
import org.jfrog.access.client.token.TokenInfo;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.client.token.TokensInfoResponse;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.common.token.VerifyResultAttributes;
import org.jfrog.access.rest.token.TokenVerifyResponseImpl;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.access.token.verifier.ClientTokenVerifyContext;
import org.jfrog.access.token.verifier.TokenVerificationManager;
import org.jfrog.access.token.verifier.VerifyFailureReason;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The {@code token()} surface: mints, refreshes, revokes, verifies and lists tokens.
 * <ul>
 *     <li><b>Mint.</b> Claims come from {@link TokenClaimsFactory} ({@code sub}, {@code scp}, {@code aud},
 *     {@code iss}, {@code exp}, {@code iat}, {@code jti}, plus {@code ext} when the request has an extension) and
 *     are signed RS256 with the embedded key. The issuer is the request's issuer, or the Artifactory service id when
 *     the request has none, because Artifactory refuses to refresh or revoke a token whose issuer is not its own
 *     service id; an issuer belonging to any other service is refused. A missing expiry means
 *     {@value #DEFAULT_EXPIRES_IN_SECONDS} seconds, {@code 0} means no expiry, and a missing audience means the
 *     Artifactory service id. Every minted token is recorded in the store with its full scope.</li>
 *     <li><b>Refresh.</b> A refresh token is 32 random bytes, base64url, handed out once and stored only as its
 *     SHA-256 hex. It is consumed atomically ({@code consumeRefreshToken}), which also revokes the old access token,
 *     so each refresh token works once. Refreshing an expired access token is the point of the flow, so it is
 *     allowed until {@code exp + refreshWindow}; after that the refresh is refused.</li>
 *     <li><b>Revoke.</b> Only tokens that {@link Revocability} calls revocable can be revoked; revoking one
 *     records it and the store publishes {@code TokenRevoked} on the change bus.</li>
 *     <li><b>Verify.</b> Verification runs Access's own {@link TokenVerificationManager} with a
 *     {@link ClientTokenVerifyContext} over the embedded certificate and the Artifactory service id, which checks
 *     signature, expiry and audience exactly as the stock client does. Its validity hook (which the stock
 *     context consults for revocable tokens, and which answers {@code true} while a token is still valid) asks
 *     the store, and once the verifier accepts a token the store is asked again for every token: a token whose
 *     record is revoked, missing or unreadable counts as revoked. Reference tokens are not supported.</li>
 * </ul>
 */
final class EaTokenClient {

    /** The lifetime of a token whose request names none. */
    static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;

    /** The token type reported in responses. */
    static final String TOKEN_TYPE = "Bearer";

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final EaRuntime rt;
    private final Duration refreshWindow;
    private final TokenClaimsFactory claims;
    private final SecureRandom random = new SecureRandom();
    private final TokenVerificationManager verifier =
            new TokenVerificationManager(CacheBuilder.newBuilder().maximumSize(0).build());

    EaTokenClient(EaRuntime rt, Duration refreshWindow) {
        this.rt = rt;
        this.refreshWindow = refreshWindow;
        this.claims = new TokenClaimsFactory(rt.grammar());
    }

    /**
     * Mints a token.
     *
     * @param request the request
     * @return the token and, for a refreshable request, its refresh token
     */
    public TokenResponse create(TokenRequest request) {
        if (request.isIncludeReferenceToken()) {
            throw EaErrors.http(EaErrors.BAD_REQUEST, "reference tokens are not supported by embedded access");
        }
        long expiresIn = request.getExpiresIn() == null ? DEFAULT_EXPIRES_IN_SECONDS : request.getExpiresIn();
        return mint(request.getSubject(), request.getIssuer(), request.getScope(), request.getAudience(), expiresIn,
                request.isRefreshable(), request.getExtension(), request.getDescription());
    }

    /**
     * Exchanges a refresh token for a new token; the old token is revoked.
     *
     * @param refreshToken the refresh token
     * @param request      the new token's scope, audience, expiry and refreshability; empty values keep the old
     *                     token's subject and scope
     * @return the new token
     */
    public TokenResponse refresh(String refreshToken, TokenRequest request) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw EaErrors.http(EaErrors.NOT_FOUND, "refresh token is invalid");
        }
        EaOutcome<EaToken> consumed = rt.store().consumeRefreshToken(EaHashes.sha256Hex(refreshToken));
        if (!(consumed instanceof EaOutcome.Ok<EaToken> ok)) {
            throw EaErrors.http(EaErrors.NOT_FOUND, "refresh token is invalid or was already used");
        }
        EaToken old = ok.value();
        Instant now = rt.now();
        if (old.expiresAt() != null && !now.isBefore(old.expiresAt().plus(refreshWindow))) {
            throw EaErrors.http(EaErrors.NOT_FOUND, "refresh token has expired");
        }
        if (request.getSubject() != null && !request.getSubject().equals(old.subject())) {
            throw EaErrors.http(EaErrors.BAD_REQUEST, "refresh cannot change the token subject");
        }
        List<String> scope = request.getScope() == null || request.getScope().isEmpty()
                ? Arrays.asList(old.scope().split(ScopeGrammar.DELIMITER)) : request.getScope();
        long expiresIn = request.getExpiresIn() != null ? request.getExpiresIn()
                : old.expiresAt() == null ? 0L : Duration.between(old.issuedAt(), old.expiresAt()).toSeconds();
        String description = request.getDescription() != null ? request.getDescription() : old.description();
        return mint(old.subject(), old.issuer(), scope, request.getAudience(), expiresIn, request.isRefreshable(),
                request.getExtension(), description);
    }

    /**
     * Revokes a token by its value.
     *
     * @param tokenValue the token
     */
    public void revoke(String tokenValue) {
        JwtAccessToken token = parseOrNull(tokenValue);
        if (token == null || token.getTokenId() == null) {
            throw EaErrors.http(EaErrors.NOT_FOUND, "token not found");
        }
        if (!revokeById(token.getTokenId())) {
            throw EaErrors.http(EaErrors.NOT_FOUND, "token not found or not revocable");
        }
    }

    /**
     * Revokes a token by id.
     *
     * @param tokenId the jti
     * @return {@code true} if a revocable token with that id exists (it is now revoked)
     */
    public boolean revokeById(String tokenId) {
        Optional<EaToken> found = EaErrors.orThrow(rt.store().getToken(tokenId));
        if (found.isEmpty() || !found.get().revocable()) {
            return false;
        }
        EaErrors.orThrow(rt.store().revokeToken(tokenId));
        return true;
    }

    /**
     * Revokes every revocable token of a user.
     *
     * @param username the user
     */
    public void revokeAllForUser(String username) {
        revokeMatching(username, null);
    }

    /**
     * Revokes every revocable token of a user that carries a scope token.
     *
     * @param username   the user
     * @param scopeToken the scope token, for example {@code authentication:remember-me}
     */
    public void revokeAllForUserAndScope(String username, String scopeToken) {
        revokeMatching(username, scopeToken);
    }

    private void revokeMatching(String username, String scopeToken) {
        for (EaToken t : EaErrors.orThrow(rt.store().listTokens())) {
            if (!t.revoked() && t.revocable() && isUserSubject(t.subject(), username)
                    && (scopeToken == null || Arrays.asList(t.scope().split(ScopeGrammar.DELIMITER))
                    .contains(scopeToken))) {
                EaErrors.orThrow(rt.store().revokeToken(t.jti()));
            }
        }
    }

    /**
     * Verifies a token for Artifactory: signature against the embedded certificate, expiry, audience, and that the
     * store has not revoked it.
     *
     * @param tokenValue the token
     * @return the result; never throws for a bad token
     */
    public TokenVerifyResult verify(String tokenValue) {
        JwtAccessToken parsed = parseOrNull(tokenValue);
        if (parsed == null) {
            return new Failure(null, VerifyFailureReason.PARSE.label());
        }
        ClientTokenVerifyContext context = new ClientTokenVerifyContext(rt.artifactoryServiceId(),
                rt.certificate(), t -> !isRevoked(t),
                t -> new TokenVerifyResponseImpl(t.getTokenValue(), false, VerifyFailureReason.SIGNATURE.label()),
                reference -> null, Revocability.NON_REVOCABLE_MAX_LIFETIME_SECONDS);
        TokenVerifyResult result = verifier.verifyNoCache(tokenValue, context);
        if (result.isSuccessful() && isRevoked(parsed)) {
            return new Failure(parsed, VerifyFailureReason.REVOKED.label());
        }
        return result;
    }

    /**
     * Tells whether an unrevoked record exists for a token.
     *
     * @param tokenValue the token
     * @return {@code true} if it was minted here and is not revoked
     */
    public boolean exists(String tokenValue) {
        JwtAccessToken parsed = parseOrNull(tokenValue);
        return parsed != null && !isRevoked(parsed);
    }

    /**
     * Decodes a token without verifying it.
     *
     * @param tokenValue the token
     * @return the decoded token
     * @throws IllegalArgumentException if it is not a JWT
     */
    public JwtAccessToken parse(String tokenValue) {
        return JwtAccessTokenImpl.parseTokenValue(tokenValue);
    }

    /**
     * Classifies a token value. Reference tokens are not supported, so they are {@code NOT_VALID_TOKEN}.
     *
     * @param tokenValue the value
     * @return {@code JWT_ACCESS_TOKEN} or {@code NOT_VALID_TOKEN}
     */
    public AccessTokenType getAccessTokenType(String tokenValue) {
        return parseOrNull(tokenValue) == null ? AccessTokenType.NOT_VALID_TOKEN : AccessTokenType.JWT_ACCESS_TOKEN;
    }

    /**
     * Lists the unrevoked, unexpired tokens.
     *
     * @return the tokens
     */
    public TokensInfoResponse getTokensInfo() {
        return infos(EaErrors.orThrow(rt.store().listTokens()));
    }

    /**
     * Lists the unrevoked, unexpired tokens of a subject.
     *
     * @param subject the exact subject
     * @return the tokens
     */
    public TokensInfoResponse getTokensInfosForSubject(String subject) {
        return infos(EaErrors.orThrow(rt.store().listTokensBySubject(subject)));
    }

    // ---- internals ----

    private TokensInfoResponse infos(List<EaToken> tokens) {
        Instant now = rt.now();
        List<TokenInfo> out = new ArrayList<>();
        for (EaToken t : tokens) {
            if (!t.revoked() && !t.isExpiredAt(now)) {
                out.add(new Info(t.jti(), t.issuer(), t.subject(),
                        t.expiresAt() == null ? null : t.expiresAt().getEpochSecond(), t.issuedAt().getEpochSecond(),
                        t.refreshable(), t.description()));
            }
        }
        return new InfoList(List.copyOf(out));
    }

    private TokenResponse mint(String subject, String requestedIssuer, List<String> scope, List<String> audience,
            long expiresIn, boolean refreshable, String extension, String description) {
        String issuer = requestedIssuer == null || requestedIssuer.isBlank()
                ? rt.artifactoryServiceId().getFormattedName() : requestedIssuer;
        String issuerService = issuer.contains("/") ? issuer.substring(0, issuer.indexOf('/')) : issuer;
        if (!issuerService.equals(rt.artifactoryServiceId().getFormattedName())
                && !issuerService.equals(rt.accessServiceId().getFormattedName())) {
            throw EaErrors.http(EaErrors.BAD_REQUEST, "issuer belongs to another service: " + issuer);
        }
        List<String> aud = audience == null || audience.isEmpty()
                ? List.of(rt.artifactoryServiceId().getFormattedName()) : audience;
        String scopeText = scope == null ? "" : String.join(ScopeGrammar.DELIMITER, scope);
        Instant now = rt.now();
        String jti = UUID.randomUUID().toString();
        TokenClaimsFactory.TokenClaims c = EaErrors.orThrow(
                claims.create(subject, issuerService, scopeText, aud, now, expiresIn, jti));
        Map<String, Object> claimMap = new LinkedHashMap<>(c.asMap());
        claimMap.put("iss", issuer);
        if (extension != null && !extension.isBlank()) {
            claimMap.put("ext", extension);
        }
        String value = sign(claimMap);
        String refreshToken = null;
        String refreshHash = null;
        if (refreshable) {
            byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
            random.nextBytes(bytes);
            refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            refreshHash = EaHashes.sha256Hex(refreshToken);
        }
        Instant issuedAt = Instant.ofEpochSecond(c.issuedAt());
        Instant expiresAt = c.expiresAt() == null ? null : Instant.ofEpochSecond(c.expiresAt());
        EaToken record = new EaToken(jti, subject, issuer, c.scope(), description, issuedAt, expiresAt, refreshable,
                refreshHash, false);
        EaErrors.orThrow(rt.store().insertToken(record));
        return new Response(value, refreshToken, expiresAt == null ? null : expiresIn, description);
    }

    private String sign(Map<String, Object> claimMap) {
        try {
            JWTClaimsSet set = JWTClaimsSet.parse(claimMap);
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
            SignedJWT jwt = new SignedJWT(header, set);
            jwt.sign(new RSASSASigner(rt.signingKey()));
            return jwt.serialize();
        } catch (ParseException | JOSEException e) {
            throw EaErrors.failure("cannot sign token", e);
        }
    }

    private boolean isRevoked(JwtAccessToken token) {
        String jti = token.getTokenId();
        if (jti == null) {
            return true;
        }
        EaOutcome<Optional<EaToken>> found = rt.store().getToken(jti);
        return !(found instanceof EaOutcome.Ok<Optional<EaToken>> ok) || ok.value().isEmpty()
                || ok.value().get().revoked();
    }

    private static JwtAccessToken parseOrNull(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        try {
            return JwtAccessTokenImpl.parseTokenValue(tokenValue);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Tells whether a token subject names a user, as Artifactory's user tokens do.
     *
     * @param subject  the subject
     * @param username the user
     * @return {@code true} if the subject is a user subject for {@code username}
     */
    static boolean isUserSubject(String subject, String username) {
        try {
            return SubjectFQN.isUserTokenSubject(subject) && SubjectFQN.extractUsername(subject).equals(username);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A failed verification decided before or after the Access verifier. */
    private record Failure(JwtAccessToken getAccessToken, String getReason) implements TokenVerifyResult {
        @Override
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public String getReasonFullMessage() {
            return getReason;
        }

        @Override
        public Map<VerifyResultAttributes, String> getAttributes() {
            return Map.of();
        }
    }

    /** A minted token. */
    private record Response(String getTokenValue, String refresh, Long expiresIn, String getDescription)
            implements TokenResponse {
        @Override
        public Optional<String> getRefreshToken() {
            return Optional.ofNullable(refresh);
        }

        @Override
        public String getTokenType() {
            return TOKEN_TYPE;
        }

        @Override
        public OptionalLong getExpiresIn() {
            return expiresIn == null ? OptionalLong.empty() : OptionalLong.of(expiresIn);
        }

        @Override
        public Optional<String> getReferenceToken() {
            return Optional.empty();
        }
    }

    /** One listed token. */
    private record Info(String getTokenId, String getIssuer, String getSubject, Long getExpirationTime,
            long getIssuedAt, boolean isRefreshable, String getDescription) implements TokenInfo {
    }

    /** A token list. */
    private record InfoList(List<TokenInfo> getTokens) implements TokensInfoResponse {
    }
}
