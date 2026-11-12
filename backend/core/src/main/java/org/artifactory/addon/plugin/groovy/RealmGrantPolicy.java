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

import org.artifactory.security.UserInfo;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Decides what an {@link RealmResult.Authenticated} answer of a plugin realm actually grants (P3). Pure: the
 * shell looks up the user and groups and hands them in as facts, then carries out the returned {@link Grant}.
 *
 * <p>The rules, which do not trust the plugin:
 * <ul>
 *     <li>A realm can never grant admin. A result naming an existing user who is an administrator, directly or
 *         through a group, is denied; just-in-time users are created without the admin flag.</li>
 *     <li>Groups are applied only if they exist and do not carry admin privileges; other names are dropped.</li>
 *     <li>A missing user is created just in time only when the result asks for it ({@code jit: true}) and the
 *         realm allows it ({@code autoCreateUsers: true}), in the realm {@code plugin:<name>}, with no
 *         password. A {@code jit} result naming an existing user of another realm is denied, so a realm cannot
 *         take over an internal or LDAP account by creating "its" user under the same name.</li>
 *     <li>A user created by this realm has its stored groups replaced by the granted ones on every login, so
 *         tokens minted for it carry them; any other user gets the granted groups for the request only.</li>
 *     <li>The user name must be a valid Artifactory name of at most 64 characters and not {@code anonymous};
 *         an {@code expiresAt} that has passed is denied; disabled users are denied; just-in-time names must be
 *         lower case.</li>
 * </ul>
 */
public final class RealmGrantPolicy {

    /** Longest user name a realm may authenticate; matches the database columns that hold user names. */
    public static final int MAX_USERNAME_LENGTH = 64;

    private static final char[] FORBIDDEN = {'/', '\\', ':', '|', '?', '*', '"', '<', '>'};

    private RealmGrantPolicy() {
    }

    /**
     * Facts about an existing user.
     *
     * @param realm          the user's Artifactory realm, may be {@code null}
     * @param effectiveAdmin admin flag or membership of an admin group
     * @param storedGroups   names of the groups stored on the user
     */
    public record ExistingUser(String username, String realm, boolean effectiveAdmin, boolean enabled,
            Set<String> storedGroups) {
        public ExistingUser {
            Objects.requireNonNull(username, "username");
            storedGroups = Set.copyOf(storedGroups);
        }

        /** @return the facts of {@code user} */
        public static ExistingUser of(UserInfo user) {
            Set<String> groups = new TreeSet<>();
            if (user.getGroups() != null) {
                user.getGroups().forEach(group -> groups.add(group.getGroupName()));
            }
            return new ExistingUser(user.getUsername(), user.getRealm(), user.isEffectiveAdmin(), user.isEnabled(),
                    groups);
        }
    }

    /** Facts about an existing group. */
    public record GroupFacts(String name, boolean adminPrivileges) {
        public GroupFacts {
            Objects.requireNonNull(name, "name");
        }
    }

    /** What to do with an authenticated result; the closed set the shell carries out. */
    public sealed interface Grant {
    }

    /** Authenticate as an existing user, adding {@code groups} for this authentication only. */
    public record UseExisting(String username, Set<String> groups) implements Grant {
        public UseExisting {
            groups = Set.copyOf(groups);
        }
    }

    /** Authenticate as a user this realm created, after storing exactly {@code groups} on it if they differ. */
    public record SyncRealmUser(String username, Set<String> groups, boolean groupsChanged) implements Grant {
        public SyncRealmUser {
            groups = Set.copyOf(groups);
        }
    }

    /** Create the user in {@code userRealm} with {@code groups} and no password, then authenticate as it. */
    public record CreateRealmUser(String username, String userRealm, Set<String> groups, Optional<String> email)
            implements Grant {
        public CreateRealmUser {
            groups = Set.copyOf(groups);
        }
    }

    /** Refuse the authentication; {@code reason} is logged, never sent to the client. */
    public record Deny(String reason) implements Grant {
    }

    /**
     * Decides the grant for {@code result} of {@code realm}.
     *
     * @param user       the user named by the result, if it exists
     * @param findGroup  looks up a group by name
     * @param nowSeconds the current time in epoch seconds
     */
    public static Grant decide(PluginRealm realm, RealmResult.Authenticated result, Optional<ExistingUser> user,
            Function<String, Optional<GroupFacts>> findGroup, long nowSeconds) {
        Optional<String> invalid = invalidUsername(result.username());
        if (invalid.isPresent()) {
            return new Deny(invalid.get());
        }
        if (result.expiresAt().isPresent() && result.expiresAt().getAsLong() <= nowSeconds) {
            return new Deny("the realm's result expired at " + result.expiresAt().getAsLong());
        }
        Set<String> groups = allowedGroups(result.groups(), findGroup);
        if (user.isEmpty()) {
            if (!result.jit()) {
                return new Deny("user '" + result.username() + "' does not exist");
            }
            if (!realm.autoCreateUsers()) {
                return new Deny("realm '" + realm.name() + "' may not create users (autoCreateUsers is false)");
            }
            if (!result.username().equals(result.username().toLowerCase(Locale.ROOT))) {
                return new Deny("just-in-time user names must be lower case, as Artifactory stores them");
            }
            return new CreateRealmUser(result.username(), realm.userRealm(), groups, result.email());
        }
        ExistingUser existing = user.get();
        if (existing.effectiveAdmin()) {
            return new Deny("user '" + existing.username() + "' is an administrator; a realm cannot grant admin");
        }
        if (!existing.enabled()) {
            return new Deny("user '" + existing.username() + "' is disabled");
        }
        boolean ownedByRealm = realm.userRealm().equals(existing.realm());
        if (ownedByRealm) {
            return new SyncRealmUser(existing.username(), groups, !groups.equals(existing.storedGroups()));
        }
        if (result.jit()) {
            return new Deny("user '" + existing.username() + "' exists in realm '" + existing.realm()
                    + "', not in '" + realm.userRealm() + "'");
        }
        return new UseExisting(existing.username(), groups);
    }

    /**
     * Keeps the names that exist as groups without admin privileges. Pure apart from {@code findGroup}.
     *
     * @return the allowed names, sorted
     */
    public static Set<String> allowedGroups(Set<String> requested, Function<String, Optional<GroupFacts>> findGroup) {
        Set<String> allowed = new TreeSet<>();
        for (String name : requested) {
            Optional<GroupFacts> group = findGroup.apply(name);
            if (group.isPresent() && !group.get().adminPrivileges()) {
                allowed.add(group.get().name());
            }
        }
        return allowed;
    }

    /** @return why {@code username} cannot be authenticated by a realm, empty when it can. Pure. */
    public static Optional<String> invalidUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.of("empty user name");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            return Optional.of("user name longer than " + MAX_USERNAME_LENGTH + " characters");
        }
        if (UserInfo.isAnonymous(username) || UserInfo.SIGNED_ANONYMOUS.equals(username)) {
            return Optional.of("a realm cannot authenticate the anonymous user");
        }
        if (!username.equals(username.trim()) || ".".equals(username) || "..".equals(username)
                || "&".equals(username)) {
            return Optional.of("invalid user name");
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if (Character.isISOControl(c)) {
                return Optional.of("user name contains a control character");
            }
            for (char forbidden : FORBIDDEN) {
                if (c == forbidden) {
                    return Optional.of("user name contains '" + c + "'");
                }
            }
        }
        return Optional.empty();
    }
}
