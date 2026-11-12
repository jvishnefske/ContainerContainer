/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import groovy.lang.Closure;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3: what a realm's answer may grant, decided in Java without trusting the plugin. */
class RealmGrantPolicyTest {

    private static final long NOW = 1_800_000_000L;
    private static final Closure<?> NOOP = new Closure<>(null) {
        @Override
        public Object call(Object... args) {
            return null;
        }
    };

    private static final Map<String, RealmGrantPolicy.GroupFacts> GROUPS = Map.of(
            "readers", new RealmGrantPolicy.GroupFacts("readers", false),
            "ci", new RealmGrantPolicy.GroupFacts("ci", false),
            "admins", new RealmGrantPolicy.GroupFacts("admins", true));
    private static final Function<String, Optional<RealmGrantPolicy.GroupFacts>> FIND =
            name -> Optional.ofNullable(GROUPS.get(name));

    private static PluginRealm realm(boolean autoCreate) {
        return new PluginRealm("p", "corp", PluginRealm.Policy.EXCLUSIVE, autoCreate, Optional.empty(),
                Optional.of(new RealmHook.Script(NOOP)), Optional.empty());
    }

    private static RealmResult.Authenticated as(String user, boolean jit, String... groups) {
        return new RealmResult.Authenticated(user, Set.of(groups), jit, OptionalLong.empty(), Optional.empty());
    }

    private static RealmGrantPolicy.Grant decide(boolean autoCreate, RealmResult.Authenticated result,
            RealmGrantPolicy.ExistingUser user) {
        return RealmGrantPolicy.decide(realm(autoCreate), result, Optional.ofNullable(user), FIND, NOW);
    }

    private static RealmGrantPolicy.ExistingUser existing(String name, String realm, boolean admin,
            String... groups) {
        return new RealmGrantPolicy.ExistingUser(name, realm, admin, true, Set.of(groups));
    }

    @Test
    void aRealmCanNeverGrantAnAdministrator() {
        assertInstanceOf(RealmGrantPolicy.Deny.class,
                decide(true, as("admin", false), existing("admin", "internal", true)));
        assertInstanceOf(RealmGrantPolicy.Deny.class,
                decide(true, as("boss", true), existing("boss", "plugin:corp", true)));
    }

    @Test
    void onlyExistingNonAdminGroupsAreGranted() {
        RealmGrantPolicy.UseExisting grant = assertInstanceOf(RealmGrantPolicy.UseExisting.class,
                decide(false, as("alice", false, "readers", "admins", "ghosts"), existing("alice", "internal",
                        false)));
        assertEquals(Set.of("readers"), grant.groups());
        assertEquals(Set.of("ci"), RealmGrantPolicy.allowedGroups(Set.of("ci", "admins", "nope"), FIND));
    }

    @Test
    void justInTimeUsersAreCreatedInThePluginRealmOnlyWhenAllowed() {
        RealmGrantPolicy.CreateRealmUser create = assertInstanceOf(RealmGrantPolicy.CreateRealmUser.class,
                decide(true, as("oidc-abc", true, "readers", "admins"), null));
        assertEquals("plugin:corp", create.userRealm());
        assertEquals(Set.of("readers"), create.groups());
        assertInstanceOf(RealmGrantPolicy.Deny.class, decide(false, as("oidc-abc", true), null));
        assertInstanceOf(RealmGrantPolicy.Deny.class, decide(true, as("oidc-abc", false), null));
        assertInstanceOf(RealmGrantPolicy.Deny.class, decide(true, as("Oidc-Abc", true), null));
    }

    @Test
    void justInTimeCannotTakeOverAUserOfAnotherRealm() {
        assertInstanceOf(RealmGrantPolicy.Deny.class,
                decide(true, as("alice", true), existing("alice", "internal", false)));
        assertInstanceOf(RealmGrantPolicy.Deny.class,
                decide(true, as("alice", true), existing("alice", "plugin:other", false)));
    }

    @Test
    void usersOfTheRealmHaveTheirGroupsReplaced() {
        RealmGrantPolicy.SyncRealmUser sync = assertInstanceOf(RealmGrantPolicy.SyncRealmUser.class,
                decide(true, as("oidc-abc", true, "ci"), existing("oidc-abc", "plugin:corp", false, "readers")));
        assertEquals(Set.of("ci"), sync.groups());
        assertTrue(sync.groupsChanged());
        RealmGrantPolicy.SyncRealmUser same = assertInstanceOf(RealmGrantPolicy.SyncRealmUser.class,
                decide(true, as("oidc-abc", true, "ci"), existing("oidc-abc", "plugin:corp", false, "ci")));
        assertEquals(false, same.groupsChanged());
    }

    @Test
    void invalidNamesExpiredResultsAndDisabledUsersAreDenied() {
        for (String name : new String[]{"", " alice", "anonymous", "a|b", "a/b", "a:b", "x".repeat(65), "a\nb"}) {
            assertInstanceOf(RealmGrantPolicy.Deny.class, decide(true, as(name, true), null), name);
        }
        RealmResult.Authenticated expired = new RealmResult.Authenticated("alice", Set.of(), false,
                OptionalLong.of(NOW), Optional.empty());
        assertInstanceOf(RealmGrantPolicy.Deny.class, decide(true, expired, existing("alice", "internal", false)));
        RealmGrantPolicy.ExistingUser disabled = new RealmGrantPolicy.ExistingUser("alice", "internal", false, false,
                Set.of());
        assertInstanceOf(RealmGrantPolicy.Deny.class, decide(true, as("alice", false), disabled));
    }
}
