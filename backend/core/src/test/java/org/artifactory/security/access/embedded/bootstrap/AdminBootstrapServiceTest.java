/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService.ClaimResult;
import org.artifactory.security.access.embedded.core.BootstrapRules;
import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.BootstrapRules.Keep;
import org.artifactory.security.access.embedded.core.BootstrapRules.Source;
import org.artifactory.security.access.embedded.core.BootstrapRules.Unclaimed;
import org.artifactory.security.access.embedded.core.ClaimRateLimit;
import org.artifactory.security.access.embedded.core.EaHashes;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link AdminBootstrapService} against a temporary directory and {@link InMemoryBootstrapStatePort}. */
class AdminBootstrapServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");
    private static final String GOOD_PASSWORD = "a-long-unusual-passphrase-42";
    private static final String CLIENT = "192.0.2.10";
    private static PasswordHasher hasher;

    @TempDir
    Path security;
    @TempDir
    Path secrets;

    private InMemoryBootstrapStatePort port;
    private final List<String> audited = new ArrayList<>();

    @BeforeAll
    static void createHasher() {
        hasher = PasswordHasher.standard();
    }

    @BeforeEach
    void setUp() {
        port = new InMemoryBootstrapStatePort();
        audited.clear();
    }

    private AdminBootstrapService service(UnaryOperator<String> env, ClaimRateLimit limit) {
        return AdminBootstrapService.builder(port, security)
                .hasher(hasher)
                .policy(new PasswordPolicy(Set.of("passwordpassword")))
                .rateLimit(limit)
                .env(env)
                .audit(audited::add)
                .build();
    }

    private AdminBootstrapService service() {
        return service(name -> null, new ClaimRateLimit(100, 100, Duration.ofMinutes(15), 16));
    }

    private static UnaryOperator<String> envWith(Path file) {
        Map<String, String> vars = Map.of(AdminBootstrapService.TOKEN_FILE_ENV, file.toString());
        return vars::get;
    }

    private String generatedToken() throws IOException {
        return Files.readString(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)).strip();
    }

    private BootstrapState stored() {
        return port.load().toOptional().orElseThrow().orElseThrow();
    }

    @Test
    void firstStartWritesOwnerOnlyTokenFileAndStoresOnlyItsHash() throws IOException {
        AdminBootstrapService service = service();
        assertTrue(service.start(T0).isOk());

        Path file = security.resolve(AdminBootstrapService.TOKEN_FILE_NAME);
        assertEquals(file, service.tokenFile());
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        String token = generatedToken();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
        assertFalse(port.serialised().contains(token));
        Unclaimed state = assertInstanceOf(Unclaimed.class, stored());
        assertEquals(EaHashes.sha256Hex(token), state.tokenSha256());
        assertEquals(Source.GENERATED, state.source());
        assertEquals(T0.plus(BootstrapRules.DEFAULT_TOKEN_TTL), state.expiresAt());
        assertFalse(service.isClaimed());
        try (var listing = Files.list(security)) {
            assertEquals(1, listing.count(), "no temporary file is left behind");
        }
    }

    @Test
    void restartWithValidTokenKeepsIt() throws IOException {
        service().start(T0);
        String token = generatedToken();
        assertTrue(service().start(T0.plus(Duration.ofHours(1))).isOk());
        assertEquals(token, generatedToken());
        assertEquals(1, port.saves());
    }

    @Test
    void expiredTokenIsReplacedOnNextStart() throws IOException {
        service().start(T0);
        String first = generatedToken();
        assertTrue(service().start(T0.plus(Duration.ofHours(25))).isOk());
        String second = generatedToken();
        assertNotEquals(first, second);
        assertEquals(EaHashes.sha256Hex(second), ((Unclaimed) stored()).tokenSha256());
    }

    @Test
    void missingTokenFileIsReplacedOnNextStart() throws IOException {
        service().start(T0);
        Files.delete(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME));
        assertTrue(service().start(T0.plus(Duration.ofHours(1))).isOk());
        assertEquals(EaHashes.sha256Hex(generatedToken()), ((Unclaimed) stored()).tokenSha256());
    }

    @Test
    void suppliedTokenFileIsUsedAndNothingIsGenerated() throws IOException {
        String token = "operator-supplied-token-0123456789";
        Path supplied = Files.writeString(secrets.resolve("token"), token + "\n");
        AdminBootstrapService service = service(envWith(supplied), ClaimRateLimit.defaults());
        assertTrue(service.start(T0).isOk());

        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        Unclaimed state = assertInstanceOf(Unclaimed.class, stored());
        assertEquals(Source.FILE, state.source());
        assertEquals(EaHashes.sha256Hex(token), state.tokenSha256());
    }

    @Test
    void suppliedTokenReplacesAGeneratedOneAndDeletesItsFile() throws IOException {
        service().start(T0);
        Path supplied = Files.writeString(secrets.resolve("token"), "operator-supplied-token-0123456789");
        assertTrue(service(envWith(supplied), ClaimRateLimit.defaults()).start(T0).isOk());
        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        assertEquals(Source.FILE, ((Unclaimed) stored()).source());
    }

    @Test
    void oversizedEmptyOrMissingSuppliedFileIsRefused() throws IOException {
        Path big = Files.writeString(secrets.resolve("big"),
                "x".repeat(AdminBootstrapService.MAX_SUPPLIED_FILE_BYTES + 1));
        Path empty = Files.writeString(secrets.resolve("empty"), " \n");
        Path missing = secrets.resolve("missing");
        for (Path file : List.of(big, empty, missing)) {
            assertFalse(service(envWith(file), ClaimRateLimit.defaults()).start(T0).isOk(), file.toString());
        }
        assertNull(port.serialised());
        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
    }

    @Test
    void claimSetsHashDeletesGeneratedFileAndAudits() throws IOException {
        AdminBootstrapService service = service();
        service.start(T0);
        String token = generatedToken();

        assertEquals(ClaimResult.CLAIMED, service.claim(token, GOOD_PASSWORD, CLIENT, T0.plusSeconds(60)));

        assertTrue(service.isClaimed());
        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        assertTrue(port.adminPasswordHash().startsWith("{bcrypt}$2a$12$"));
        assertTrue(hasher.matches(GOOD_PASSWORD, port.adminPasswordHash()));
        Claimed claimed = assertInstanceOf(Claimed.class, stored());
        assertEquals(CLIENT, claimed.clientIp());
        assertEquals(List.of(CLIENT), audited);
        assertTrue(service().start(T0.plus(Duration.ofDays(3))).isOk());
    }

    @Test
    void claimWithSuppliedTokenLeavesTheOperatorsFile() throws IOException {
        String token = "operator-supplied-token-0123456789";
        Path supplied = Files.writeString(secrets.resolve("token"), token);
        AdminBootstrapService service = service(envWith(supplied), ClaimRateLimit.defaults());
        service.start(T0);
        assertEquals(ClaimResult.CLAIMED, service.claim(token, GOOD_PASSWORD, CLIENT, T0));
        assertTrue(Files.exists(supplied));
    }

    @Test
    void claimedServerReportsClaimedOnStartWithoutReadingTheSuppliedFile() {
        port = InMemoryBootstrapStatePort.holding(new Claimed(T0, CLIENT));
        AdminBootstrapService service = service(envWith(secrets.resolve("gone")), ClaimRateLimit.defaults());
        assertTrue(service.start(T0).isOk());
        assertTrue(service.isClaimed());
    }

    @Test
    void everyFailureIsTheSameGenericRefusal() throws IOException {
        AdminBootstrapService service = service();
        service.start(T0);
        String token = generatedToken();
        Instant later = T0.plusSeconds(60);

        assertEquals(ClaimResult.REFUSED, service.claim("wrong-token-wrong-token-wrong", GOOD_PASSWORD, CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(null, GOOD_PASSWORD, CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim("short", GOOD_PASSWORD, CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(token, "passwordpassword", CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(token, "short", CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(token, "my-admin-password-1", CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(token, null, CLIENT, later));
        assertEquals(ClaimResult.REFUSED,
                service.claim(token, GOOD_PASSWORD, CLIENT, T0.plus(BootstrapRules.DEFAULT_TOKEN_TTL)));
        assertFalse(service.isClaimed());
        assertNull(port.adminPasswordHash());

        assertEquals(ClaimResult.CLAIMED, service.claim(token, GOOD_PASSWORD, CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service.claim(token, GOOD_PASSWORD + "-again", CLIENT, later));
        assertEquals(ClaimResult.REFUSED, service().claim(token, GOOD_PASSWORD + "-again", CLIENT, later));
        assertEquals(1, audited.size());
    }

    @Test
    void wrongTokenAttemptsAreRateLimitedPerClientAndGlobally() throws IOException {
        AdminBootstrapService service = service(name -> null, new ClaimRateLimit(2, 3, Duration.ofMinutes(15), 16));
        service.start(T0);
        String wrong = "wrong-token-wrong-token-wrong";

        assertEquals(ClaimResult.REFUSED, service.claim(wrong, GOOD_PASSWORD, CLIENT, T0));
        assertEquals(ClaimResult.REFUSED, service.claim(wrong, GOOD_PASSWORD, CLIENT, T0));
        assertEquals(ClaimResult.RATE_LIMITED, service.claim(wrong, GOOD_PASSWORD, CLIENT, T0));
        assertEquals(ClaimResult.REFUSED, service.claim(wrong, GOOD_PASSWORD, "192.0.2.11", T0));
        assertEquals(ClaimResult.RATE_LIMITED, service.claim(wrong, GOOD_PASSWORD, "192.0.2.12", T0),
                "the global limit applies across clients");
    }

    @Test
    void validTokenIsNeverLockedOutByExhaustedLimits() throws IOException {
        AdminBootstrapService service = service(name -> null, new ClaimRateLimit(2, 3, Duration.ofMinutes(15), 16));
        service.start(T0);
        String token = generatedToken();
        String wrong = "wrong-token-wrong-token-wrong";
        for (int i = 0; i < 5; i++) {
            service.claim(wrong, GOOD_PASSWORD, "192.0.2." + (20 + i), T0);
        }
        assertEquals(ClaimResult.RATE_LIMITED, service.claim(wrong, GOOD_PASSWORD, CLIENT, T0));

        assertEquals(ClaimResult.CLAIMED, service.claim(token, GOOD_PASSWORD, CLIENT, T0),
                "the holder of the valid token claims despite the exhausted global limit");
        assertEquals(ClaimResult.RATE_LIMITED, service.claim(token, GOOD_PASSWORD, CLIENT, T0),
                "once claimed the token is no longer valid, so the attempt counts and is limited");
    }

    @Test
    void refusedAndRateLimitedAttemptsDoNotReadTheStore() throws IOException {
        AdminBootstrapService service = service(name -> null, new ClaimRateLimit(2, 3, Duration.ofMinutes(15), 16));
        service.start(T0);
        int loadsAfterStart = port.loads();
        for (int i = 0; i < 4; i++) {
            service.claim("wrong-token-wrong-token-wrong", GOOD_PASSWORD, CLIENT, T0);
        }
        assertEquals(loadsAfterStart, port.loads());
        assertEquals(ClaimResult.CLAIMED, service.claim(generatedToken(), GOOD_PASSWORD, CLIENT, T0));
        assertEquals(loadsAfterStart + 1, port.loads(), "only the valid token reaches the store");
    }

    @Test
    void conflictingClaimIsRefused() throws IOException {
        AdminBootstrapService first = service();
        first.start(T0);
        AdminBootstrapService second = service();
        second.start(T0);
        String token = generatedToken();
        assertEquals(ClaimResult.CLAIMED, first.claim(token, GOOD_PASSWORD, CLIENT, T0));
        assertEquals(ClaimResult.REFUSED, second.claim(token, GOOD_PASSWORD, CLIENT, T0));
        assertFalse(second.isClaimed());
    }

    @Test
    void onlyAKeptGeneratedTokenWithoutFileMustBeReplaced() {
        Unclaimed generated = new Unclaimed(EaHashes.sha256Hex("x".repeat(30)), T0, Source.GENERATED);
        Unclaimed supplied = new Unclaimed(EaHashes.sha256Hex("x".repeat(30)), T0, Source.FILE);
        assertTrue(AdminBootstrapService.mustReplaceMissingFile(new Keep(generated), false));
        assertFalse(AdminBootstrapService.mustReplaceMissingFile(new Keep(generated), true));
        assertFalse(AdminBootstrapService.mustReplaceMissingFile(new Keep(supplied), false));
        assertFalse(AdminBootstrapService.mustReplaceMissingFile(new Keep(new Claimed(T0, CLIENT)), false));
    }

    @Test
    void suppliedFileIsReadAsUtf8() throws IOException {
        Path supplied = Files.write(secrets.resolve("token"),
                "operator-supplied-token-0123456789".getBytes(StandardCharsets.UTF_8));
        assertTrue(service(envWith(supplied), ClaimRateLimit.defaults()).start(T0).isOk());
    }

    private static UnaryOperator<String> envWithPassword(Path file) {
        Map<String, String> vars = Map.of(AdminBootstrapService.PASSWORD_FILE_ENV, file.toString());
        return vars::get;
    }

    @Test
    void operatorSuppliedPasswordClaimsWithMustChangeAndNoTokenFile() throws IOException {
        Path supplied = Files.writeString(secrets.resolve("password"), "  " + GOOD_PASSWORD + "\n");
        AdminBootstrapService service = service(envWithPassword(supplied), ClaimRateLimit.defaults());

        assertTrue(service.start(T0).isOk());

        assertTrue(service.isClaimed());
        assertTrue(port.adminMustChangePassword());
        assertTrue(hasher.matches(GOOD_PASSWORD, port.adminPasswordHash()));
        Claimed claimed = assertInstanceOf(Claimed.class, stored());
        assertEquals(AdminBootstrapService.OPERATOR_SUPPLIED_CLIENT, claimed.clientIp());
        assertEquals(T0, claimed.at());
        assertEquals(List.of(AdminBootstrapService.OPERATOR_SUPPLIED_CLIENT), audited);
        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        assertEquals(ClaimResult.REFUSED, service.claim("any-token-any-token-any", GOOD_PASSWORD, CLIENT, T0));
    }

    @Test
    void operatorSuppliedPasswordDeletesAnEarlierGeneratedTokenFile() throws IOException {
        assertTrue(service().start(T0).isOk());
        assertTrue(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        Path supplied = Files.writeString(secrets.resolve("password"), GOOD_PASSWORD);

        AdminBootstrapService service = service(envWithPassword(supplied), ClaimRateLimit.defaults());
        assertTrue(service.start(T0.plusSeconds(5)).isOk());

        assertTrue(service.isClaimed());
        assertFalse(Files.exists(security.resolve(AdminBootstrapService.TOKEN_FILE_NAME)));
        assertInstanceOf(Claimed.class, stored());
    }

    @Test
    void operatorSuppliedPasswordIsIgnoredOnceClaimed() throws IOException {
        AdminBootstrapService first = service();
        first.start(T0);
        assertEquals(ClaimResult.CLAIMED, first.claim(generatedToken(), GOOD_PASSWORD, CLIENT, T0));
        String hash = port.adminPasswordHash();
        Path supplied = Files.writeString(secrets.resolve("password"), "another-long-unusual-phrase-7");

        AdminBootstrapService restarted = service(envWithPassword(supplied), ClaimRateLimit.defaults());
        assertTrue(restarted.start(T0.plusSeconds(60)).isOk());

        assertTrue(restarted.isClaimed());
        assertEquals(hash, port.adminPasswordHash());
        assertFalse(port.adminMustChangePassword());
        assertEquals(CLIENT, assertInstanceOf(Claimed.class, stored()).clientIp());
    }

    @Test
    void badOperatorSuppliedPasswordFileKeepsTheServerUnclaimed() throws IOException {
        Path weak = Files.writeString(secrets.resolve("weak"), "passwordpassword");
        Path shortOne = Files.writeString(secrets.resolve("short"), "short");
        Path withUser = Files.writeString(secrets.resolve("user"), "my-admin-password-1");
        Path big = Files.writeString(secrets.resolve("big"),
                "x".repeat(AdminBootstrapService.MAX_SUPPLIED_FILE_BYTES + 1));
        Path empty = Files.writeString(secrets.resolve("empty"), " \n");
        Path missing = secrets.resolve("missing");
        for (Path file : List.of(weak, shortOne, withUser, big, empty, missing)) {
            AdminBootstrapService service = service(envWithPassword(file), ClaimRateLimit.defaults());
            EaOutcome<?> outcome = service.start(T0);
            assertFalse(outcome.isOk(), file.toString());
            String reason = outcome.fold(v -> "", r -> r, op -> op);
            assertTrue(reason.contains(AdminBootstrapService.PASSWORD_FILE_ENV), reason);
            assertFalse(reason.contains("passwordpassword") || reason.contains("my-admin-password-1"), reason);
            assertFalse(service.isClaimed());
        }
        assertNull(port.serialised());
        assertNull(port.adminPasswordHash());
        assertTrue(audited.isEmpty());
    }
}
