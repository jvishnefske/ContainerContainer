/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import org.artifactory.security.access.embedded.core.EaGroup;
import org.artifactory.security.access.embedded.core.EaHashes;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaPermission;
import org.artifactory.security.access.embedded.core.EaToken;
import org.artifactory.security.access.embedded.core.EaUser;
import org.artifactory.security.access.embedded.store.TestDataSources.TestClock;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store contract, run against each supported database by a subclass that provides a fresh, empty database per
 * test.
 */
abstract class AbstractEmbeddedAccessStoreTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final String SUBJECT = "jfrt@01abc/users/alice";

    TestClock clock;
    EaChangeBus bus;
    LinkedBlockingQueue<EaChange> events;
    DataSource dataSource;
    JdbcEmbeddedAccessStore store;

    /** Returns a data source on an empty database; called once per test. */
    abstract DataSource freshDatabase() throws Exception;

    /** Releases the database of the current test. */
    abstract void dropDatabase() throws Exception;

    abstract DbType dbType();

    @BeforeEach
    void setUp() throws Exception {
        clock = new TestClock(T0);
        bus = new EaChangeBus();
        events = new LinkedBlockingQueue<>();
        bus.subscribe(events::add);
        dataSource = freshDatabase();
        assertEquals(EaOutcome.done(), EmbeddedAccessSchema.ensure(dataSource, dbType(), clock));
        store = new JdbcEmbeddedAccessStore(dataSource, clock, bus);
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
        dropDatabase();
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    static <T> T ok(EaOutcome<T> outcome) {
        assertInstanceOf(EaOutcome.Ok.class, outcome, () -> "expected Ok but was " + outcome);
        return ((EaOutcome.Ok<T>) outcome).value();
    }

    static void assertRefused(EaOutcome<?> outcome, String reasonPart) {
        EaOutcome.Refused<?> refused = assertInstanceOf(EaOutcome.Refused.class, outcome);
        assertTrue(refused.reason().contains(reasonPart), () -> "reason was: " + refused.reason());
    }

    static EaUser user(String name, Set<String> groups) {
        return EaUser.builder(name, T0).email(name + "@example.org").groups(groups)
                .customData(Map.of("k", "v-" + name)).build();
    }

    static EaToken token(String jti, String subject, Instant exp, String refreshSecret) {
        return new EaToken(jti, subject, "jfac@01abc", "applied-permissions/user", "d", T0, exp,
                refreshSecret != null, refreshSecret == null ? null : EaHashes.sha256Hex(refreshSecret), false);
    }

    long lastUpdated() {
        return ok(store.permissionsLastUpdated());
    }

    EaChange nextEvent() throws InterruptedException {
        EaChange change = events.poll(5, TimeUnit.SECONDS);
        assertTrue(change != null, "no event within 5 s");
        return change;
    }

    // ---- schema ----

    @Test
    void schemaIsIdempotentAndSeedsPasswordlessAdmin() {
        assertEquals(EaOutcome.done(), EmbeddedAccessSchema.ensure(dataSource, dbType(), clock));
        EaUser admin = ok(store.getUser(EmbeddedAccessSchema.ADMIN_USERNAME)).orElseThrow();
        assertTrue(admin.admin());
        assertNull(admin.passwordHash());
        assertFalse(admin.canAuthenticateByPassword());
        assertFalse(admin.mustChangePassword());
        assertEquals(EaUser.Status.ENABLED, admin.status());
        assertEquals(EaUser.INTERNAL_REALM, admin.realm());
        assertEquals(T0, admin.created());
        assertEquals(0L, lastUpdated());
        assertEquals(1, ok(store.listUsers()).size());
    }

    @Test
    void adminIsReseededWhenAbsent() {
        ok(store.deleteUser("admin"));
        assertEquals(EaOutcome.done(), EmbeddedAccessSchema.ensure(dataSource, dbType(), clock));
        assertTrue(ok(store.getUser("admin")).isPresent());
    }

    // ---- users ----

    @Test
    void userCreateReadFindAndList() {
        ok(store.createGroup(EaGroup.internal("devs", T0)));
        EaUser created = ok(store.createUser(user("Alice", Set.of("devs"))));
        assertEquals(Set.of("devs"), created.groups());
        assertEquals(Map.of("k", "v-Alice"), created.customData());
        assertEquals(created, ok(store.getUser("Alice")).orElseThrow());
        assertTrue(ok(store.getUser("alice")).isEmpty());
        assertEquals("Alice", ok(store.findUserByUsernameIgnoreCase("ALICE")).orElseThrow().username());
        assertEquals(List.of("Alice"), ok(store.findUsersByEmail("ALICE@example.org")).stream()
                .map(EaUser::username).toList());
        ok(store.createUser(user("bob", Set.of())));
        assertEquals(List.of("Alice", "admin", "bob"), ok(store.listUsers()).stream().map(EaUser::username)
                .toList());
        assertEquals(Set.of("devs"), ok(store.listUsers()).get(0).groups());
        assertRefused(store.createUser(user("bob", Set.of())), "already exists");
        assertRefused(store.createUser(user("carol", Set.of("nope"))), "no such group: nope");
        assertTrue(ok(store.getUser("carol")).isEmpty());
    }

    @Test
    void userUpdateReplacesFieldsAndMemberships() {
        ok(store.createGroup(EaGroup.internal("a", T0)));
        ok(store.createGroup(EaGroup.internal("b", T0)));
        EaUser alice = ok(store.createUser(user("alice", Set.of("a"))));
        Instant later = T0.plusSeconds(60);
        EaUser changed = alice.toBuilder().groups(Set.of("b")).email("new@example.org").admin(true)
                .status(EaUser.Status.LOCKED).customData(Map.of("x", "y")).modified(later).build();
        EaUser stored = ok(store.updateUser(changed));
        assertEquals(changed, stored);
        assertEquals(List.of("alice"), ok(store.groupMembers("b")));
        assertEquals(List.of(), ok(store.groupMembers("a")));
        assertRefused(store.updateUser(user("ghost", Set.of())), "no such user");
        assertRefused(store.updateUser(alice.toBuilder().groups(Set.of("zzz")).build()), "no such group");
    }

    @Test
    void passwordStatusAndLastLogin() {
        ok(store.createUser(user("alice", Set.of())));
        Instant at = T0.plusSeconds(10);
        EaUser withPassword = ok(store.setPassword("alice", "$pbkdf2$hash", true, at, at));
        assertEquals("$pbkdf2$hash", withPassword.passwordHash());
        assertTrue(withPassword.mustChangePassword());
        assertEquals(at, withPassword.passwordLastModified());
        assertEquals(at, withPassword.modified());
        EaUser cleared = ok(store.setPassword("alice", null, false, null, at));
        assertNull(cleared.passwordHash());
        assertNull(cleared.passwordLastModified());
        assertEquals(EaUser.Status.DISABLED, ok(store.setStatus("alice", EaUser.Status.DISABLED, at)).status());

        long before = lastUpdated();
        ok(store.updateLastLogin("alice", T0.plusSeconds(99), "10.0.0.1"));
        EaUser afterLogin = ok(store.getUser("alice")).orElseThrow();
        assertEquals(T0.plusSeconds(99), afterLogin.lastLoginTime());
        assertEquals("10.0.0.1", afterLogin.lastLoginIp());
        assertEquals(at, afterLogin.modified());
        assertEquals(before, lastUpdated(), "a login must not advance permissions.last_updated");

        assertRefused(store.setPassword("ghost", null, false, null, at), "no such user");
        assertRefused(store.setStatus("ghost", EaUser.Status.ENABLED, at), "no such user");
        assertRefused(store.updateLastLogin("ghost", at, null), "no such user");
    }

    @Test
    void deleteUserRemovesMemberships() {
        ok(store.createGroup(EaGroup.internal("g", T0)));
        ok(store.createUser(user("alice", Set.of("g"))));
        ok(store.deleteUser("alice"));
        assertTrue(ok(store.getUser("alice")).isEmpty());
        assertEquals(List.of(), ok(store.groupMembers("g")));
        assertRefused(store.deleteUser("alice"), "no such user");
    }

    // ---- groups ----

    @Test
    void groupCrud() {
        EaGroup group = new EaGroup("readers", "Read only", true, false, "internal", "attrs", "ext-1",
                Map.of("c", "d"), T0, T0);
        assertEquals(group, ok(store.createGroup(group)));
        assertRefused(store.createGroup(group), "already exists");
        assertEquals(group, ok(store.getGroup("readers")).orElseThrow());
        EaGroup changed = new EaGroup("readers", "Readers", false, true, "ldap", null, null, Map.of(),
                T0.plusSeconds(5), T0.plusSeconds(7));
        EaGroup stored = ok(store.updateGroup(changed));
        assertEquals(T0, stored.created(), "created is kept");
        assertEquals(T0.plusSeconds(7), stored.modified());
        assertEquals("ldap", stored.realm());
        assertNull(stored.realmAttributes());
        ok(store.createGroup(EaGroup.internal("admins", T0)));
        assertEquals(List.of("admins", "readers"), ok(store.listGroups()).stream().map(EaGroup::name).toList());
        assertRefused(store.updateGroup(EaGroup.internal("ghost", T0)), "no such group");
        ok(store.deleteGroup("readers"));
        assertTrue(ok(store.getGroup("readers")).isEmpty());
        assertRefused(store.deleteGroup("readers"), "no such group");
    }

    @Test
    void membershipOperations() {
        ok(store.createGroup(EaGroup.internal("g", T0)));
        ok(store.createUser(user("alice", Set.of())));
        ok(store.createUser(user("bob", Set.of())));
        ok(store.addGroupMembers("g", Set.of("alice", "bob")));
        ok(store.addGroupMembers("g", Set.of("alice")));
        assertEquals(List.of("alice", "bob"), ok(store.groupMembers("g")));
        assertEquals(List.of("alice", "bob"), ok(store.listUsersInGroup("g")).stream().map(EaUser::username)
                .toList());
        assertEquals(Set.of("g"), ok(store.getUser("bob")).orElseThrow().groups());
        assertRefused(store.addGroupMembers("g", Set.of("alice", "ghost")), "no such user: ghost");
        assertRefused(store.addGroupMembers("nope", Set.of("alice")), "no such group");
        ok(store.removeGroupMembers("g", Set.of("bob", "ghost")));
        assertEquals(List.of("alice"), ok(store.groupMembers("g")));
        assertRefused(store.removeGroupMembers("nope", Set.of()), "no such group");
        assertRefused(store.groupMembers("nope"), "no such group");
        assertRefused(store.listUsersInGroup("nope"), "no such group");
    }

    @Test
    void groupDeleteCascadesToMemberships() {
        ok(store.createGroup(EaGroup.internal("g", T0)));
        ok(store.createGroup(EaGroup.internal("h", T0)));
        ok(store.createUser(user("alice", Set.of("g", "h"))));
        ok(store.deleteGroup("g"));
        assertEquals(Set.of("h"), ok(store.getUser("alice")).orElseThrow().groups());
    }

    // ---- permissions ----

    static EaPermission permission(String name, String displayName, Map<String, Set<String>> users,
            Map<String, Set<String>> groups) {
        return new EaPermission(name, displayName, "repo", "jfrt@01abc", "{\"repositories\":[\"libs\"]}", users,
                groups, false, T0, T0);
    }

    @Test
    void permissionCrudFindAndMentions() {
        EaPermission p = permission("p1", "Deploy", Map.of("alice", Set.of("read", "write")),
                Map.of("devs", Set.of("read")));
        assertEquals(p, ok(store.createPermission(p)));
        assertRefused(store.createPermission(p), "already exists");
        assertEquals(p, ok(store.getPermission("p1")).orElseThrow());
        ok(store.createPermission(permission("p2", "Deploy", Map.of(), Map.of("ops", Set.of("manage")))));
        ok(store.createPermission(new EaPermission("p3", "Deploy", "build", "jfrt@01abc", null, Map.of(),
                Map.of(), true, T0, T0)));
        assertEquals(List.of("p1", "p2"), ok(store.findPermissions("repo", "Deploy")).stream()
                .map(EaPermission::name).toList());
        assertEquals(List.of("p3"), ok(store.findPermissions("build", "Deploy")).stream().map(EaPermission::name)
                .toList());
        assertNull(ok(store.getPermission("p3")).orElseThrow().customData());
        assertTrue(ok(store.getPermission("p3")).orElseThrow().implicit());
        assertEquals(List.of("p1"), ok(store.listPermissionsMentioning("alice", false)).stream()
                .map(EaPermission::name).toList());
        assertEquals(List.of("p2"), ok(store.listPermissionsMentioning("ops", true)).stream()
                .map(EaPermission::name).toList());
        assertEquals(List.of(), ok(store.listPermissionsMentioning("alice", true)));

        EaPermission updated = p.withGrants("{}", Map.of(), Map.of("ops", Set.of("read")), T0.plusSeconds(3));
        assertEquals(updated, ok(store.updatePermission(updated)));
        assertEquals(3, ok(store.listPermissions()).size());
        assertRefused(store.updatePermission(permission("ghost", "x", Map.of(), Map.of())), "no such permission");
        ok(store.deletePermission("p1"));
        assertRefused(store.deletePermission("p1"), "no such permission");
        assertEquals(List.of("p2", "p3"), ok(store.listPermissions()).stream().map(EaPermission::name).toList());
    }

    // ---- last_updated ----

    @Test
    void everyMutationAdvancesLastUpdatedMonotonically() {
        clock.set(Instant.ofEpochMilli(5_000));
        ok(store.createGroup(EaGroup.internal("g", T0)));
        assertEquals(5_000, lastUpdated());
        ok(store.createUser(user("alice", Set.of())));
        assertEquals(5_001, lastUpdated(), "a clock that stands still still advances the stamp");
        clock.set(Instant.ofEpochMilli(1_000));
        ok(store.addGroupMembers("g", Set.of("alice")));
        assertEquals(5_002, lastUpdated(), "a clock that steps back still advances the stamp");
        clock.set(Instant.ofEpochMilli(9_000));
        ok(store.createPermission(permission("p", "P", Map.of(), Map.of())));
        assertEquals(9_000, lastUpdated());
        long previous = lastUpdated();
        ok(store.setPassword("alice", "h", false, T0, T0));
        ok(store.setStatus("alice", EaUser.Status.LOCKED, T0));
        ok(store.removeGroupMembers("g", Set.of("alice")));
        ok(store.updateGroup(EaGroup.internal("g", T0)));
        ok(store.updatePermission(permission("p", "P", Map.of("x", Set.of("r")), Map.of())));
        ok(store.deletePermission("p"));
        ok(store.deleteUser("alice"));
        ok(store.deleteGroup("g"));
        assertEquals(previous + 8, lastUpdated());
    }

    @Test
    void lastUpdatedBumpIsInTheSameTransaction() throws InterruptedException {
        long before = lastUpdated();
        clock.failWith(new IllegalStateException("clock broke"));
        assertRefused(store.createUser(user("alice", Set.of())), EmbeddedAccessStore.STORAGE_ERROR_PREFIX);
        clock.set(T0);
        assertTrue(ok(store.getUser("alice")).isEmpty(), "the user insert must roll back with the bump");
        assertEquals(before, lastUpdated());

        EaKvEntry entry = ok(store.getKv(EmbeddedAccessStore.PERMISSIONS_LAST_UPDATED_KEY)).orElseThrow();
        ok(store.compareAndSetKv(entry.key(), entry.revision(), Long.toString(Long.MAX_VALUE)));
        assertRefused(store.createGroup(EaGroup.internal("g", T0)), "Long.MAX_VALUE");
        assertTrue(ok(store.getGroup("g")).isEmpty());

        assertTrue(events.isEmpty(), "rolled back mutations publish nothing");
        ok(store.deleteKv(EmbeddedAccessStore.PERMISSIONS_LAST_UPDATED_KEY));
        ok(store.createGroup(EaGroup.internal("g2", T0)));
        assertEquals(new EaChange.PermissionsAffected(Set.of(), Set.of("g2")), nextEvent());
        assertEquals(T0.toEpochMilli(), lastUpdated(), "an absent stamp is recreated");
    }

    // ---- events ----

    @Test
    void mutationsPublishAfterCommitWithAffectedPrincipals() throws InterruptedException {
        ok(store.createGroup(EaGroup.internal("g", T0)));
        assertEquals(new EaChange.PermissionsAffected(Set.of(), Set.of("g")), nextEvent());
        ok(store.createUser(user("alice", Set.of("g"))));
        assertEquals(new EaChange.PermissionsAffected(Set.of("alice"), Set.of("g")), nextEvent());
        assertRefused(store.createUser(user("alice", Set.of())), "already exists");
        ok(store.deleteGroup("g"));
        assertEquals(new EaChange.PermissionsAffected(Set.of("alice"), Set.of("g")), nextEvent(),
                "the refused create published nothing; the delete names the former members");
        ok(store.createPermission(permission("p", "P", Map.of("bob", Set.of("r")), Map.of("ops", Set.of("r")))));
        assertEquals(new EaChange.PermissionsAffected(Set.of("bob"), Set.of("ops")), nextEvent());
        ok(store.updatePermission(permission("p", "P", Map.of("carol", Set.of("r")), Map.of())));
        assertEquals(new EaChange.PermissionsAffected(Set.of("bob", "carol"), Set.of("ops")), nextEvent());
        ok(store.insertToken(token("j1", SUBJECT, null, null)));
        ok(store.revokeToken("j1"));
        assertEquals(new EaChange.TokenRevoked(SUBJECT), nextEvent());
        ok(store.revokeToken("j1"));
        ok(store.updateLastLogin("alice", T0, null));
        ok(store.createGroup(EaGroup.internal("sentinel", T0)));
        assertEquals(new EaChange.PermissionsAffected(Set.of(), Set.of("sentinel")), nextEvent(),
                "re-revoking and logins publish nothing");
    }

    // ---- tokens ----

    @Test
    void tokenInsertReadListAndRevoke() throws InterruptedException {
        EaToken t1 = token("j1", SUBJECT, T0.plusSeconds(3600), "refresh-1");
        ok(store.insertToken(t1));
        assertEquals(t1, ok(store.getToken("j1")).orElseThrow());
        assertEquals(t1.scopeSha256(), EaHashes.sha256Hex("applied-permissions/user"));
        assertRefused(store.insertToken(t1), "already exists");
        assertRefused(store.insertToken(token("j9", SUBJECT, null, "refresh-1")), "refresh hash");
        ok(store.insertToken(token("j2", SUBJECT, null, null)));
        ok(store.insertToken(token("j3", "other", null, null)));
        assertEquals(List.of("j1", "j2"), ok(store.listTokensBySubject(SUBJECT)).stream().map(EaToken::jti)
                .toList());
        assertEquals(3, ok(store.listTokens()).size());
        ok(store.revokeToken("j3"));
        assertTrue(ok(store.getToken("j3")).orElseThrow().revoked());
        assertRefused(store.revokeToken("nope"), "no such token");
        assertEquals(2, ok(store.revokeTokensBySubject(SUBJECT)));
        assertEquals(0, ok(store.revokeTokensBySubject(SUBJECT)));
        assertTrue(ok(store.listTokensBySubject(SUBJECT)).stream().allMatch(EaToken::revoked));
        assertEquals(new EaChange.TokenRevoked("other"), nextEvent());
        assertEquals(new EaChange.TokenRevoked(SUBJECT), nextEvent());
    }

    @Test
    void refreshTokenIsConsumedOnce() throws Exception {
        ok(store.insertToken(token("j1", SUBJECT, T0.plusSeconds(60), "secret")));
        EaToken consumed = ok(store.consumeRefreshToken(EaHashes.sha256Hex("secret")));
        assertEquals("j1", consumed.jti());
        assertTrue(consumed.revoked());
        assertTrue(ok(store.getToken("j1")).orElseThrow().revoked());
        assertRefused(store.consumeRefreshToken(EaHashes.sha256Hex("secret")), "no unused refresh token");
        assertRefused(store.consumeRefreshToken(EaHashes.sha256Hex("other")), "no unused refresh token");

        ok(store.insertToken(token("j2", SUBJECT, T0.plusSeconds(60), "race")));
        String hash = EaHashes.sha256Hex("race");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = Collections.nCopies(8,
                    () -> store.consumeRefreshToken(hash).isOk());
            int wins = 0;
            for (Future<Boolean> f : pool.invokeAll(calls)) {
                wins += f.get() ? 1 : 0;
            }
            assertEquals(1, wins, "exactly one concurrent consumer wins");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void purgeDeletesOnlyTokensExpiredBeforeTheCutOff() {
        ok(store.insertToken(token("old", SUBJECT, T0.plusSeconds(10), null)));
        ok(store.insertToken(token("edge", SUBJECT, T0.plusSeconds(20), null)));
        ok(store.insertToken(token("never", SUBJECT, null, null)));
        assertEquals(1, ok(store.purgeExpiredTokens(T0.plusSeconds(20))));
        assertEquals(List.of("edge", "never"), ok(store.listTokens()).stream().map(EaToken::jti).toList());
        assertEquals(1, ok(store.purgeExpiredTokens(T0.plusSeconds(10_000))));
        assertEquals(List.of("never"), ok(store.listTokens()).stream().map(EaToken::jti).toList());
    }

    // ---- kv ----

    @Test
    void kvCompareAndSet() {
        assertTrue(ok(store.getKv("cfg")).isEmpty());
        assertEquals(new EaKvEntry("cfg", 1, "a"), ok(store.compareAndSetKv("cfg", EaKvEntry.ABSENT_REVISION, "a")));
        assertRefused(store.compareAndSetKv("cfg", EaKvEntry.ABSENT_REVISION, "b"), "conflict");
        assertRefused(store.compareAndSetKv("cfg", 7, "b"), "conflict");
        assertEquals(new EaKvEntry("cfg", 2, "b"), ok(store.compareAndSetKv("cfg", 1, "b")));
        assertEquals(new EaKvEntry("cfg", 2, "b"), ok(store.getKv("cfg")).orElseThrow());
        String large = "x".repeat(100_000);
        assertEquals(large, ok(store.compareAndSetKv("cfg", 2, large)).value());
        assertEquals(large, ok(store.getKv("cfg")).orElseThrow().value());
        assertTrue(ok(store.deleteKv("cfg")));
        assertFalse(ok(store.deleteKv("cfg")));
    }

    @Test
    void kvUpdateRetriesAfterAConflict() {
        ok(store.compareAndSetKv("n", EaKvEntry.ABSENT_REVISION, "1"));
        AtomicInteger calls = new AtomicInteger();
        EaKvEntry written = ok(store.updateKv("n", current -> {
            if (calls.incrementAndGet() == 1) {
                // a concurrent writer gets in between our read and our write
                ok(store.compareAndSetKv("n", 1, "10"));
            }
            return EaOutcome.ok(Integer.toString(Integer.parseInt(current.orElse("0")) + 1));
        }));
        assertEquals(2, calls.get());
        assertEquals(new EaKvEntry("n", 3, "11"), written);
        assertEquals(new EaKvEntry("new", 1, "1"), ok(store.updateKv("new",
                current -> EaOutcome.ok(current.isEmpty() ? "1" : "x"))));
    }

    @Test
    void kvUpdateGivesUpAfterThreeRetriesAndPassesRefusals() {
        ok(store.compareAndSetKv("n", EaKvEntry.ABSENT_REVISION, "0"));
        AtomicInteger calls = new AtomicInteger();
        EaOutcome<EaKvEntry> outcome = store.updateKv("n", current -> {
            calls.incrementAndGet();
            EaKvEntry now = ok(store.getKv("n")).orElseThrow();
            ok(store.compareAndSetKv("n", now.revision(), "interfered"));
            return EaOutcome.ok("mine");
        });
        assertRefused(outcome, "gave up after 3 retries");
        assertEquals(1 + EmbeddedAccessStore.KV_CAS_RETRIES, calls.get());
        assertRefused(store.updateKv("n", current -> EaOutcome.refused("bad value")), "bad value");
        assertEquals("interfered", ok(store.getKv("n")).orElseThrow().value());
        assertEquals(Optional.empty(), ok(store.getKv("absent")));
    }

    // ---- one-transaction claim and bulk user update (T4) ----

    @Test
    void setPasswordAndReplaceKvWritesBothOrNeither() throws InterruptedException {
        Instant at = T0.plusSeconds(5);
        long before = lastUpdated();
        events.clear();
        EaUser admin = ok(store.setPasswordAndReplaceKv("admin", "{bcrypt}h1", true, at, "bootstrap.state",
                Optional.empty(), "s1"));
        assertEquals("{bcrypt}h1", admin.passwordHash());
        assertTrue(admin.mustChangePassword());
        assertEquals(at, admin.passwordLastModified());
        assertEquals("s1", ok(store.getKv("bootstrap.state")).orElseThrow().value());
        assertTrue(lastUpdated() > before);
        assertEquals(new EaChange.PermissionsAffected(Set.of("admin"), Set.of()), nextEvent());

        // the stored value no longer matches what the caller read: nothing is written
        assertRefused(store.setPasswordAndReplaceKv("admin", "{bcrypt}h2", false, at, "bootstrap.state",
                Optional.empty(), "s2"), "conflict: ");
        assertRefused(store.setPasswordAndReplaceKv("admin", "{bcrypt}h2", false, at, "bootstrap.state",
                Optional.of("other"), "s2"), "conflict: ");
        assertEquals("{bcrypt}h1", ok(store.getUser("admin")).orElseThrow().passwordHash());
        assertEquals("s1", ok(store.getKv("bootstrap.state")).orElseThrow().value());

        // a missing user rolls the key-value write back
        assertRefused(store.setPasswordAndReplaceKv("ghost", "{bcrypt}h3", false, at, "bootstrap.state",
                Optional.of("s1"), "s3"), "no such user");
        assertEquals("s1", ok(store.getKv("bootstrap.state")).orElseThrow().value());

        EaUser second = ok(store.setPasswordAndReplaceKv("admin", "{bcrypt}h4", false, at, "bootstrap.state",
                Optional.of("s1"), "s4"));
        assertFalse(second.mustChangePassword());
        assertEquals(new EaKvEntry("bootstrap.state", 2, "s4"), ok(store.getKv("bootstrap.state")).orElseThrow());
    }

    @Test
    void updateAllUsersWritesOnlyChangedUsersInOneTransaction() throws InterruptedException {
        ok(store.createGroup(EaGroup.internal("g", T0)));
        ok(store.createUser(user("alice", Set.of("g"))));
        ok(store.createUser(user("bob", Set.of())));
        // Delivery is asynchronous but ordered: consume the setup events instead of clearing the queue, which races
        // with their delivery.
        assertEquals(new EaChange.PermissionsAffected(Set.of(), Set.of("g")), nextEvent());
        assertEquals(new EaChange.PermissionsAffected(Set.of("alice"), Set.of("g")), nextEvent());
        assertEquals(new EaChange.PermissionsAffected(Set.of("bob"), Set.of()), nextEvent());
        long before = lastUpdated();
        int changed = ok(store.updateAllUsers(u -> u.username().equals("admin") ? u
                : u.toBuilder().email("new-" + u.username() + "@example.org").modified(T0.plusSeconds(1)).build()));
        assertEquals(2, changed);
        assertEquals("new-alice@example.org", ok(store.getUser("alice")).orElseThrow().email());
        assertEquals(Set.of("g"), ok(store.getUser("alice")).orElseThrow().groups());
        assertTrue(lastUpdated() > before);
        assertEquals(new EaChange.PermissionsAffected(Set.of("alice", "bob"), Set.of()), nextEvent());

        long unchanged = lastUpdated();
        assertEquals(0, ok(store.updateAllUsers(u -> u)));
        assertEquals(unchanged, lastUpdated(), "no change, no stamp");

        // a forbidden change anywhere rolls back every earlier write of the same call
        assertRefused(store.updateAllUsers(u -> u.username().equals("bob")
                ? u.toBuilder().groups(Set.of("g")).build()
                : u.toBuilder().email("x@example.org").build()), "may not change");
        assertEquals("new-alice@example.org", ok(store.getUser("alice")).orElseThrow().email());
    }
}
