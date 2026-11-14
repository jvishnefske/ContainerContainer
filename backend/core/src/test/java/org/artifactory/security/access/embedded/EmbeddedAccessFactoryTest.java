/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.bootstrap.BootstrapStatePort;
import org.artifactory.security.access.embedded.bootstrap.PasswordHasher;
import org.artifactory.security.access.embedded.core.BootstrapRules;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaToken;
import org.artifactory.security.access.embedded.core.EaUser;
import org.artifactory.security.access.embedded.store.EaKvEntry;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EmbeddedAccessFactory} over in-memory Derby: what a first start creates, what a restart keeps, and the
 * refusals that must stop startup. Also covers {@link StoreBootstrapStatePort}, {@link SigningKeyVault} and
 * {@link EaTokenPurgeJob}.
 */
class EmbeddedAccessFactoryTest {

    private static final ServiceId RT = ServiceId.fromFormattedName("jfrt@01factorytest");
    private static final String MASTER_KEY = "f".repeat(32);

    private static EncryptionWrapper encryption;

    @TempDir
    Path security;

    private DerbyMemoryDatabase database;
    private DataSource dataSource;
    private final List<EmbeddedAccess> started = new ArrayList<>();
    // Fixed, but at the real current time: tokens are minted with this clock while JFrog's verifier checks their
    // expiry against the system clock, so a fixed date in the past would make every minted token expired.
    private final Clock clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);

    @BeforeAll
    static void createEncryption() {
        encryption = EncryptionWrapperFactory.aesKeyWrapperFromString(MASTER_KEY);
    }

    @BeforeEach
    void setUp() {
        database = new DerbyMemoryDatabase("ea-factory");
        dataSource = database.dataSource();
    }

    @AfterEach
    void tearDown() {
        started.forEach(EmbeddedAccess::close);
        database.close();
    }

    private EmbeddedAccessFactory.Settings settings(DbType type, EncryptionWrapper wrapper,
            Function<BootstrapStatePort, AdminBootstrapService> bootstrap) {
        return new EmbeddedAccessFactory.Settings(dataSource, type, RT, wrapper, clock, Duration.ofHours(1),
                bootstrap);
    }

    private AdminBootstrapService.Builder bootstrapBuilder(
            BootstrapStatePort port) {
        return AdminBootstrapService.builder(port, security).env(name -> null).audit(client -> { });
    }

    private EmbeddedAccess start() {
        EmbeddedAccess access = ok(EmbeddedAccessFactory.create(settings(DbType.DERBY, encryption,
                port -> bootstrapBuilder(port).build())));
        started.add(access);
        return access;
    }

    @Test
    void firstStartSeedsUsersEncryptsTheKeyAndOpensTheSetupToken() throws Exception {
        EmbeddedAccess access = start();

        EaUser admin = ok(access.store().getUser("admin")).orElseThrow();
        assertNull(admin.passwordHash(), "admin has no password until the server is claimed (IA1)");
        EaUser anonymous = ok(access.store().getUser(EmbeddedAccessFactory.ANONYMOUS_USERNAME)).orElseThrow();
        assertNull(anonymous.passwordHash());
        assertFalse(anonymous.admin());
        assertEquals(EaUser.Status.ENABLED, anonymous.status());

        String storedKey = ok(access.store().getKv(SigningKeyVault.KEY)).orElseThrow().value();
        assertTrue(encryption.isEncodedByMe(storedKey), "the signing key is stored encrypted");
        assertFalse(storedKey.contains("BEGIN CERTIFICATE"));
        assertFalse(storedKey.startsWith("v1:"));

        assertFalse(access.bootstrap().isClaimed());
        assertTrue(Files.exists(access.bootstrap().tokenFile()));
        EaKvEntry state = ok(access.store().getKv(StoreBootstrapStatePort.STATE_KEY)).orElseThrow();
        assertInstanceOf(BootstrapRules.Unclaimed.class, ok(BootstrapRules.parse(state.value())));

        assertEquals("jfac@01factorytest", access.client().accessServiceId().getFormattedName());
    }

    @Test
    void restartKeepsTheSigningKeySoOldTokensVerify() {
        EmbeddedAccess first = start();
        TokenResponse token = first.client().token().create(TokenRequest.builder()
                .subject(RT.getFormattedName() + "/users/admin").scopes(List.of("applied-permissions/user"))
                .expiresIn(3600L).refreshable(false).build());
        first.close();
        started.remove(first);

        EmbeddedAccess second = start();
        assertEquals(first.client().certificate(), second.client().certificate());
        assertTrue(second.client().token().verify(token.getTokenValue()).isSuccessful());
        assertEquals(1, ok(second.store().listUsers()).stream()
                .filter(u -> u.username().equals(EmbeddedAccessFactory.ANONYMOUS_USERNAME)).count());
    }

    @Test
    void claimThroughTheStorePortSetsThePasswordAndStateTogether() {
        EmbeddedAccess access = start();
        String token = readToken(access);
        assertEquals(AdminBootstrapService.ClaimResult.CLAIMED,
                access.bootstrap().claim(token, "Correct-Horse-Battery-9", "10.0.0.7", clock.instant()));
        assertTrue(access.bootstrap().isClaimed());
        EaUser admin = ok(access.store().getUser("admin")).orElseThrow();
        assertTrue(admin.passwordHash().startsWith(PasswordHasher.BCRYPT_ID));
        assertFalse(admin.mustChangePassword());
        assertInstanceOf(Claimed.class, ok(new StoreBootstrapStatePort(access.store(), clock).load()).orElseThrow());

        // the admin can now log in; the anonymous user still cannot log in by any password
        access.client().cachedAuth().authenticate(new LoginRequest().username("admin")
                .password("Correct-Horse-Battery-9"));
        assertThrows(AccessClientHttpException.class, () -> access.client().cachedAuth()
                .authenticate(new LoginRequest().username("anonymous").password("")));

        // a restart finds the claimed state
        access.close();
        started.remove(access);
        assertTrue(start().bootstrap().isClaimed());
    }

    @Test
    void anonymousLogsInOnlyWhileAnonymousAccessIsEnabled() {
        EmbeddedAccess access = start();
        LoginRequest anonymous = new LoginRequest().username("anonymous").password("");
        assertThrows(AccessClientHttpException.class, () -> access.client().cachedAuth().authenticate(anonymous));
        access.client().config().updateConfigurationModel(Map.of("security",
                Map.of("anonymous-access-enabled", true)));
        assertEquals("anonymous", access.client().cachedAuth().authenticate(anonymous).getUsername());
        ok(access.store().setStatus("anonymous", EaUser.Status.DISABLED, clock.instant()));
        assertThrows(AccessClientHttpException.class, () -> access.client().cachedAuth().authenticate(anonymous));
    }

    @Test
    void passwordExpiryForAllUsersSkipsPasswordlessUsersAndRoundTrips() {
        EmbeddedAccess access = start();
        ok(access.store().createUser(EaUser.builder("bob", clock.instant()).build()));
        ok(access.store().setPassword("bob", ok(PasswordHasher.standard().encode("Bob-Password-1234")), false,
                clock.instant(), clock.instant()));
        LoginRequest bob = new LoginRequest().username("bob").password("Bob-Password-1234");
        access.client().cachedAuth().authenticate(bob);

        assertEquals(1, access.client().setPasswordExpiredForAllUsers(true), "admin and anonymous have none");
        AccessClientHttpException expired = assertThrows(AccessClientHttpException.class,
                () -> access.client().cachedAuth().authenticate(bob));
        assertTrue(expired.getMessage().toLowerCase().contains("expired"), expired.getMessage());
        assertEquals(0, access.client().setPasswordExpiredForAllUsers(true));

        assertEquals(1, access.client().setPasswordExpiredForAllUsers(false));
        access.client().cachedAuth().authenticate(bob);
    }

    @Test
    void storePortSaveIsCompareAndSwap() {
        EmbeddedAccess access = start();
        StoreBootstrapStatePort port = new StoreBootstrapStatePort(access.store(), clock);
        String current = ok(access.store().getKv(StoreBootstrapStatePort.STATE_KEY)).orElseThrow().value();
        Claimed claimed = new Claimed(clock.instant(), "test");
        assertInstanceOf(EaOutcome.Refused.class, port.save(claimed, Optional.empty()));
        assertInstanceOf(EaOutcome.Refused.class, port.save(claimed, Optional.of("stale")));
        assertInstanceOf(EaOutcome.Refused.class, port.claim(claimed, Optional.of("stale"), "{bcrypt}x", false));
        assertNull(ok(access.store().getUser("admin")).orElseThrow().passwordHash(), "refused claim wrote nothing");
        assertTrue(port.save(claimed, Optional.of(current)).isOk());
        assertInstanceOf(Claimed.class, ok(port.load()).orElseThrow());
    }

    @Test
    void refusedBootstrapStartKeepsTheServerClosedWithoutFailing() throws Exception {
        Path supplied = security.resolve("supplied.token");
        Files.writeString(supplied, "x".repeat(40));
        // an operator-supplied token that has already expired is refused on start
        EmbeddedAccess access = ok(EmbeddedAccessFactory.create(settings(DbType.DERBY, encryption,
                port -> bootstrapBuilder(port).tokenTtl(Duration.ofHours(1))
                        .env(Map.of(AdminBootstrapService.TOKEN_FILE_ENV, supplied.toString())::get).build())));
        started.add(access);
        access.close();
        started.remove(access);
        Clock later = Clock.offset(clock, Duration.ofHours(2));
        EmbeddedAccess restarted = ok(EmbeddedAccessFactory.create(new EmbeddedAccessFactory.Settings(dataSource,
                DbType.DERBY, RT, encryption, later, Duration.ofHours(1),
                port -> bootstrapBuilder(port).tokenTtl(Duration.ofHours(1))
                        .env(Map.of(AdminBootstrapService.TOKEN_FILE_ENV, supplied.toString())::get).build())));
        started.add(restarted);
        assertFalse(restarted.bootstrap().isClaimed());
    }

    @Test
    void unsupportedDatabaseAndMissingEncryptionStopStartup() {
        EaOutcome<EmbeddedAccess> mysql = EmbeddedAccessFactory.create(settings(DbType.MYSQL, encryption,
                port -> bootstrapBuilder(port).build()));
        EaOutcome.Refused<?> refused = assertInstanceOf(EaOutcome.Refused.class, mysql);
        assertTrue(refused.reason().contains("supports only Derby and PostgreSQL"), refused.reason());

        EaOutcome<EmbeddedAccess> clear = EmbeddedAccessFactory.create(settings(DbType.DERBY, null,
                port -> bootstrapBuilder(port).build()));
        refused = assertInstanceOf(EaOutcome.Refused.class, clear);
        assertTrue(refused.reason().contains("never stored in clear"), refused.reason());
    }

    @Test
    void keyEncryptedWithAnotherMasterKeyIsRefused() {
        start().close();
        started.clear();
        EncryptionWrapper other = EncryptionWrapperFactory.aesKeyWrapperFromString("e".repeat(32));
        EaOutcome<EmbeddedAccess> outcome = EmbeddedAccessFactory.create(settings(DbType.DERBY, other,
                port -> bootstrapBuilder(port).build()));
        assertInstanceOf(EaOutcome.Refused.class, outcome);
    }

    @Test
    void purgeKeepsRecordsForTheRefreshWindow() {
        EmbeddedAccess access = start();
        Instant now = clock.instant();
        Duration window = Duration.ofDays(7);
        ok(access.store().insertToken(token("recent", now.minus(Duration.ofDays(1)))));
        ok(access.store().insertToken(token("old", now.minus(Duration.ofDays(8)))));
        ok(access.store().insertToken(token("never", null)));
        assertEquals(now.minus(window), EaTokenPurgeJob.cutoff(now, window));
        assertEquals(1, ok(EaTokenPurgeJob.unscheduled(access.store(), clock, window).runOnce()));
        assertTrue(ok(access.store().getToken("recent")).isPresent());
        assertTrue(ok(access.store().getToken("never")).isPresent());
        assertTrue(ok(access.store().getToken("old")).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> EaTokenPurgeJob.start(access.store(), clock, window, Duration.ZERO));
    }

    @Test
    void passwordVerifierRefusesOverlongPasswords() {
        PasswordHasher hasher = PasswordHasher.standard();
        var verifier = EmbeddedAccessFactory.verifier(hasher);
        String hash = verifier.encode("Correct-Horse-Battery-9");
        assertTrue(verifier.matches("Correct-Horse-Battery-9", hash));
        assertFalse(verifier.matches("wrong", hash));
        assertFalse(verifier.matches("anything", null));
        assertThrows(IllegalArgumentException.class, () -> verifier.encode("x".repeat(100)));
        assertNotEquals(hash, verifier.encode("Correct-Horse-Battery-9"));
    }

    private static EaToken token(String jti, Instant exp) {
        return new EaToken(jti, RT.getFormattedName() + "/users/admin", "jfac@01factorytest",
                "applied-permissions/user", null, exp == null ? Instant.parse("2026-01-01T00:00:00Z")
                : exp.minusSeconds(60), exp, false, null, false);
    }

    private static String readToken(EmbeddedAccess access) {
        try {
            return Files.readString(access.bootstrap().tokenFile()).strip();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T ok(EaOutcome<T> outcome) {
        assertInstanceOf(EaOutcome.Ok.class, outcome, () -> "expected Ok but was " + outcome);
        return ((EaOutcome.Ok<T>) outcome).value();
    }
}
