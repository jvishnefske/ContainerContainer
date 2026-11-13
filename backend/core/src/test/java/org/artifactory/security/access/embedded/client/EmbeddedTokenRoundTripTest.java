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
import org.artifactory.security.access.embedded.core.EaHashes;
import org.artifactory.security.access.embedded.core.EaToken;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.artifactory.security.access.embedded.store.EaChange;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenInfo;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.rest.token.TokenVerifyResponseImpl;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.verifier.ClientTokenVerifyContext;
import org.jfrog.access.token.verifier.TokenVerificationManager;
import org.jfrog.access.token.verifier.VerifyFailureReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mint, verify, revoke and refresh through the embedded token surface and Access's own verifier. */
class EmbeddedTokenRoundTripTest {

    private static final String SUBJECT = EaFixture.USER_SUBJECT_PREFIX + "alice";
    private static final String SCOPE = "applied-permissions/user";

    private EaFixture fx;
    private TokenClient tokens;

    @BeforeEach
    void setUp() {
        fx = new EaFixture();
        tokens = fx.client.token();
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    private TokenResponse mint(long expiresIn, boolean refreshable) {
        TokenRequest.Builder b = TokenRequest.builder().subject(SUBJECT).scopes(List.of(SCOPE))
                .expiresIn(expiresIn).refreshable(refreshable);
        return tokens.create(b.build());
    }

    private static TokenRequest.Builder sameScope() {
        return TokenRequest.builder().scopes(List.of(SCOPE));
    }

    /** Verifies the way Artifactory's stock client does, with its own manager and context, independent of us. */
    private TokenVerifyResult verifyWithStockVerifier(String token) throws Exception {
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(EaFixture.signingKeys().certificatePem().getBytes(StandardCharsets.US_ASCII)));
        ClientTokenVerifyContext context = new ClientTokenVerifyContext(EaFixture.RT, cert,
                t -> fx.store.getToken(t.getTokenId()).toOptional().flatMap(o -> o).map(r -> !r.revoked())
                        .orElse(false),
                t -> new TokenVerifyResponseImpl(t.getTokenValue(), false, "no fallback"), r -> null, 21600L);
        return new TokenVerificationManager(CacheBuilder.newBuilder().maximumSize(0).build())
                .verifyNoCache(token, context);
    }

    @Test
    void mintedTokenHasTheClaimsArtifactoryExpectsAndVerifies() throws Exception {
        TokenResponse response = mint(3600, false);
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn().getAsLong());
        assertTrue(response.getRefreshToken().isEmpty());

        JwtAccessToken jwt = tokens.parse(response.getTokenValue());
        assertEquals(SUBJECT, jwt.getSubject());
        assertEquals(List.of(SCOPE), jwt.getScope());
        assertEquals(List.of(EaFixture.RT.getFormattedName()), jwt.getAudience());
        assertEquals(EaFixture.RT.getFormattedName(), jwt.getIssuer());
        assertEquals(jwt.getIssuedAt() + 3_600_000L, jwt.getExpirationTime(), "Access reports epoch milliseconds");
        assertEquals(AccessTokenType.JWT_ACCESS_TOKEN, tokens.getAccessTokenType(response.getTokenValue()));
        assertEquals(AccessTokenType.NOT_VALID_TOKEN, tokens.getAccessTokenType("not-a-token"));

        assertTrue(tokens.verify(response.getTokenValue()).isSuccessful());
        assertTrue(verifyWithStockVerifier(response.getTokenValue()).isSuccessful());

        EaToken record = EaFixture.ok(fx.store.getToken(jwt.getTokenId())).orElseThrow();
        assertEquals(SCOPE, record.scope());
        assertEquals(EaHashes.sha256Hex(SCOPE), record.scopeSha256());
        assertFalse(record.revocable(), "a non-refreshable token of at most 6 hours is not revocable");
    }

    @Test
    void revokedTokenFailsVerificationAndRevocationIsPublished() throws Exception {
        TokenResponse response = mint(Duration.ofDays(1).toSeconds(), false);
        String jti = tokens.parse(response.getTokenValue()).getTokenId();
        assertTrue(tokens.revokeById(jti));

        TokenVerifyResult result = tokens.verify(response.getTokenValue());
        assertFalse(result.isSuccessful());
        assertEquals(VerifyFailureReason.REVOKED.label(), result.getReason());
        // The stock context consults its hook (true = the token is still valid) for revocable tokens.
        assertFalse(verifyWithStockVerifier(response.getTokenValue()).isSuccessful());
        EaChange event = fx.awaitEvent(e -> e instanceof EaChange.TokenRevoked);
        assertEquals(SUBJECT, ((EaChange.TokenRevoked) event).subject());
        assertTrue(tokens.getTokensInfo().getTokens().stream().noneMatch(t -> t.getTokenId().equals(jti)));
    }

    @Test
    void revokeByValueAndNonRevocableTokens() {
        TokenResponse revocable = mint(Duration.ofDays(2).toSeconds(), false);
        tokens.revoke(revocable.getTokenValue());
        assertFalse(tokens.verify(revocable.getTokenValue()).isSuccessful());

        TokenResponse shortLived = mint(600, false);
        String jti = tokens.parse(shortLived.getTokenValue()).getTokenId();
        assertFalse(tokens.revokeById(jti));
        AccessClientHttpException e = assertThrows(AccessClientHttpException.class,
                () -> tokens.revoke(shortLived.getTokenValue()));
        assertEquals(404, e.getStatusCode());
        assertTrue(tokens.verify(shortLived.getTokenValue()).isSuccessful());
        assertFalse(tokens.revokeById("no-such-jti"));
    }

    @Test
    void refreshWorksOnceAndRevokesTheOldToken() {
        TokenResponse first = mint(3600, true);
        String refresh = first.getRefreshToken().orElseThrow();
        String jti = tokens.parse(first.getTokenValue()).getTokenId();
        EaToken record = EaFixture.ok(fx.store.getToken(jti)).orElseThrow();
        assertEquals(EaHashes.sha256Hex(refresh), record.refreshHash(), "only the hash is stored");
        assertTrue(record.revocable());

        TokenResponse second = tokens.refresh(refresh, sameScope().refreshable(true).build());
        assertNotEquals(first.getTokenValue(), second.getTokenValue());
        assertTrue(tokens.verify(second.getTokenValue()).isSuccessful());
        assertEquals(SUBJECT, tokens.parse(second.getTokenValue()).getSubject());
        assertEquals(List.of(SCOPE), tokens.parse(second.getTokenValue()).getScope());
        assertFalse(tokens.verify(first.getTokenValue()).isSuccessful(), "the old token is revoked");

        AccessClientHttpException again = assertThrows(AccessClientHttpException.class,
                () -> tokens.refresh(refresh, sameScope().build()));
        assertEquals(404, again.getStatusCode());
        assertTrue(second.getRefreshToken().isPresent());
    }

    @Test
    void refreshOfAnExpiredTokenWorksInsideTheWindowOnly() {
        Instant real = fx.clock.instant();
        fx.clock.set(real.minus(Duration.ofDays(1)));
        TokenResponse recent = mint(60, true);
        fx.clock.set(real.minus(Duration.ofDays(10)));
        TokenResponse old = mint(60, true);
        fx.clock.set(real);

        assertTrue(tokens.verify(tokens.refresh(recent.getRefreshToken().orElseThrow(),
                sameScope().build()).getTokenValue()).isSuccessful());
        AccessClientHttpException e = assertThrows(AccessClientHttpException.class,
                () -> tokens.refresh(old.getRefreshToken().orElseThrow(), sameScope().build()));
        assertEquals(404, e.getStatusCode());
    }

    @Test
    void expiredTokenIsRefused() throws Exception {
        fx.clock.set(fx.clock.instant().minus(Duration.ofHours(2)));
        TokenResponse response = mint(60, false);
        TokenVerifyResult result = tokens.verify(response.getTokenValue());
        assertFalse(result.isSuccessful());
        assertEquals(VerifyFailureReason.EXPIRED.label(), result.getReason());
        assertFalse(verifyWithStockVerifier(response.getTokenValue()).isSuccessful());
    }

    @Test
    void tokenSignedByAnotherKeyIsRefused() throws Exception {
        SigningKeys other = EaFixture.ok(EaKeyMaterial.generate(ServiceId.fromFormattedName("jfac@01other"),
                Clock.systemUTC()));
        EmbeddedAccessClient forger = EaFixture.ok(EmbeddedAccessClient.builder().store(fx.store)
                .changeBus(fx.bus).serviceId(EaFixture.RT).signingKeys(other).passwordVerifier(fx.hasher)
                .clock(fx.clock).build());
        TokenResponse forged = forger.token().create(TokenRequest.builder().subject(SUBJECT)
                .scopes(List.of(SCOPE)).expiresIn(3600L).build());
        TokenVerifyResult result = tokens.verify(forged.getTokenValue());
        assertFalse(result.isSuccessful());
        assertFalse(verifyWithStockVerifier(forged.getTokenValue()).isSuccessful());
        assertFalse(tokens.verify("garbage").isSuccessful());
    }

    @Test
    void foreignIssuerAndReferenceTokensAreRefused() {
        AccessClientHttpException e = assertThrows(AccessClientHttpException.class, () -> tokens.create(
                TokenRequest.builder().subject(SUBJECT).issuer("jfrt@someoneelse").scopes(List.of(SCOPE)).build()));
        assertEquals(400, e.getStatusCode());
        assertThrows(AccessClientHttpException.class, () -> tokens.create(TokenRequest.builder().subject(SUBJECT)
                .scopes(List.of(SCOPE)).includeReferenceToken(true).build()));
        String userIssued = tokens.create(TokenRequest.builder().subject(SUBJECT)
                .issuer(EaFixture.USER_SUBJECT_PREFIX + "admin").scopes(List.of(SCOPE)).build()).getTokenValue();
        assertEquals(EaFixture.USER_SUBJECT_PREFIX + "admin", tokens.parse(userIssued).getIssuer());
    }

    @Test
    void noExpiryAndDefaultExpiry() {
        TokenResponse forever = mint(0, false);
        assertTrue(forever.getExpiresIn().isEmpty());
        JwtAccessToken jwt = tokens.parse(forever.getTokenValue());
        assertNull(jwt.getExpirationTime());
        assertTrue(EaFixture.ok(fx.store.getToken(jwt.getTokenId())).orElseThrow().revocable());

        TokenResponse dflt = tokens.create(TokenRequest.builder().subject(SUBJECT).scopes(List.of(SCOPE)).build());
        assertEquals(EaTokenClient.DEFAULT_EXPIRES_IN_SECONDS, dflt.getExpiresIn().getAsLong());
    }

    @Test
    void revokeAllForUserAndScopeAndTokenList() {
        TokenResponse rememberMe = tokens.create(TokenRequest.builder().subject(SUBJECT)
                .scopes(List.of(SCOPE, "authentication:remember-me")).expiresIn(0L).build());
        TokenResponse plain = mint(0, false);
        List<TokenInfo> listed = tokens.getTokensInfo().getTokens();
        assertEquals(2, listed.size());
        tokens.revokeAllForUserAndScope("alice", "authentication:remember-me");
        assertFalse(tokens.verify(rememberMe.getTokenValue()).isSuccessful());
        TokenVerifyResult plainResult = tokens.verify(plain.getTokenValue());
        assertTrue(plainResult.isSuccessful(), plainResult::getReasonFullMessage);
        Optional<TokenInfo> left = tokens.getTokensInfo().getTokens().stream().findFirst();
        assertEquals(tokens.parse(plain.getTokenValue()).getTokenId(), left.orElseThrow().getTokenId());
    }

    @Test
    void scopesArtifactoryRequestsAreAccepted() {
        for (String scope : List.of("authentication:remember-me", "internal:event-stream:x",
                "internal:checksum-deploy:w", "internal:federated-repo/full-sync:x", "internal:mpu:x",
                "applied-permissions/admin", "applied-permissions/groups:readers", "artifact:*:r")) {
            TokenResponse r = tokens.create(TokenRequest.builder().subject(SUBJECT).scopes(List.of(scope)).build());
            assertEquals(List.of(scope), tokens.parse(r.getTokenValue()).getScope(), scope);
        }
    }

    @Test
    void invalidScopeIsRefused() {
        assertThrows(AccessClientHttpException.class, () -> tokens.create(TokenRequest.builder().subject(SUBJECT)
                .scopes(List.of("!!bad-scope")).build()));
    }
}
