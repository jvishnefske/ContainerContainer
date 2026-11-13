/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaUser;
import org.jfrog.access.client.user.FindUsersRequest;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.rest.user.Users;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@code users()} and {@code cachedUsers()} surfaces: user CRUD over the store, mapped by {@link EaModels}.
 * The "with cache" flags are accepted and ignored, because reads go straight to the store.
 * <p>
 * Custom data is returned in full (Artifactory's {@code UserMapper} filters what it shows). Password hashes are
 * never returned, even when a request asks to expand passwords. {@code findUsersByCustomData} with
 * {@code exactKeyMatch == false} matches keys that end with the given key, as Artifactory's searchable-property
 * suffix lookup needs.
 */
final class EaUsersClient {

    private final EaRuntime rt;
    private final EaAuthenticator authenticator;
    private final Supplier<UserLockPolicyConfig> lockPolicy;

    EaUsersClient(EaRuntime rt, EaAuthenticator authenticator, Supplier<UserLockPolicyConfig> lockPolicy) {
        this.rt = rt;
        this.authenticator = authenticator;
        this.lockPolicy = lockPolicy;
    }

    /**
     * Lists every user.
     *
     * @return the users
     */
    public Users findUsers() {
        return list(u -> true);
    }

    /**
     * Lists users matching a request's status, custom-data and password-expiry filters.
     *
     * @param request the filters
     * @return the users
     */
    public Users findUsers(FindUsersRequest request) {
        Predicate<EaUser> filter = u -> true;
        if (request.getStatus() != null) {
            EaUser.Status wanted = EaModels.fromStatus(request.getStatus());
            filter = filter.and(u -> u.status() == wanted);
        }
        Map<String, String> custom = request.getCustomData();
        if (custom != null && !custom.isEmpty()) {
            boolean exact = request.getExactKeyMatch() == null || request.getExactKeyMatch();
            for (Map.Entry<String, String> e : custom.entrySet()) {
                filter = filter.and(customDataFilter(e.getKey(), e.getValue(), exact));
            }
        }
        if (request.getDaysToExpire() != null) {
            filter = filter.and(expiresWithin(request.getDaysToExpire()));
        }
        return list(filter);
    }

    /**
     * Lists users that have a custom-data key.
     *
     * @param key           the key
     * @param exactKeyMatch {@code false} to match keys ending with {@code key}
     * @return the users
     */
    public Users findUsersByCustomData(String key, boolean exactKeyMatch) {
        return list(customDataFilter(key, null, exactKeyMatch));
    }

    /**
     * Lists users whose custom-data key has a value.
     *
     * @param key           the key
     * @param value         the value
     * @param exactKeyMatch {@code false} to match keys ending with {@code key}
     * @return the users
     */
    public Users findUsersByCustomData(String key, String value, boolean exactKeyMatch) {
        return list(customDataFilter(key, value, exactKeyMatch));
    }

    /**
     * Reads a user.
     *
     * @param username the username
     * @return the user, or empty
     */
    public Optional<User> findUserByUsername(String username) {
        return find(username).map(EaModels::toUser);
    }

    /**
     * Reads a user; the cache flag is ignored.
     *
     * @param username the username
     * @param useCache ignored
     * @return the user, or empty
     */
    public Optional<User> findUserByUsername(String username, boolean useCache) {
        return findUserByUsername(username);
    }

    /**
     * Reads a user with its groups.
     *
     * @param username the username
     * @return the user, or empty
     */
    public Optional<UserWithGroups> findUserWithGroupsByUsername(String username) {
        return find(username).map(u -> EaModels.toUserWithGroups(u, authenticator.groupsOf(u)));
    }

    /**
     * Reads a user with its groups; the cache flag is ignored.
     *
     * @param username the username
     * @param useCache ignored
     * @return the user, or empty
     */
    public Optional<UserWithGroups> findUserWithGroupsByUsername(String username, boolean useCache) {
        return findUserWithGroupsByUsername(username);
    }

    /**
     * Returns the group names of a user.
     *
     * @param username the username
     * @return the names, empty for an unknown user
     */
    public Set<String> getUserGroupsByUsername(String username) {
        return find(username).map(u -> (Set<String>) new TreeSet<>(u.groups())).orElseGet(TreeSet::new);
    }

    /**
     * Creates a user; a non-empty password is hashed.
     *
     * @param request the user
     * @return the stored user
     */
    public User createUser(UserRequest request) {
        EaUser user = EaErrors.orThrow(EaModels.fromUserRequest(request, rt.now(), rt.passwords()::encode, null));
        return EaModels.toUser(EaErrors.orThrow(rt.store().createUser(user)));
    }

    /**
     * Replaces a user; creation time and last login are kept.
     *
     * @param request the new state
     * @return the stored user
     */
    public User replaceUser(UserRequest request) {
        EaUser current = require(request.getUsername());
        EaUser user = EaErrors.orThrow(EaModels.fromUserRequest(request, rt.now(), rt.passwords()::encode,
                current));
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            user = user.toBuilder().passwordHash(current.passwordHash())
                    .passwordLastModified(current.passwordLastModified()).build();
        }
        return EaModels.toUser(EaErrors.orThrow(rt.store().updateUser(user)));
    }

    /**
     * Applies a merge patch to a user; a custom datum with a {@code null} value is deleted.
     *
     * @param request the patch
     * @return the stored user
     */
    public User updateUser(UpdateUserRequest request) {
        EaUser current = require(request.getUsername());
        EaUser patched = EaErrors.orThrow(EaModels.applyUserPatch(current, request.toJsonMerge(), rt.now(),
                rt.passwords()::encode));
        return EaModels.toUser(EaErrors.orThrow(rt.store().updateUser(patched)));
    }

    /**
     * Deletes a user.
     *
     * @param username the username
     */
    public void deleteUser(String username) {
        EaErrors.orThrow(rt.store().deleteUser(username));
    }

    /**
     * Records a login.
     *
     * @param username      the username
     * @param lastLoginTime epoch milliseconds
     * @param lastLoginIp   the client address
     */
    public void replaceUserLastLogin(String username, long lastLoginTime, String lastLoginIp) {
        EaErrors.orThrow(rt.store().updateLastLogin(username, Instant.ofEpochMilli(lastLoginTime), lastLoginIp));
    }

    // ---- internals ----

    private Optional<EaUser> find(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return EaErrors.orThrow(rt.store().getUser(username));
    }

    private EaUser require(String username) {
        return find(username).orElseThrow(() -> EaErrors.http(EaErrors.NOT_FOUND, "no such user: " + username));
    }

    private Users list(Predicate<EaUser> filter) {
        List<UserResponse> users = EaErrors.orThrow(rt.store().listUsers()).stream().filter(filter)
                .map(EaModels::toUser).toList();
        return new org.jfrog.access.rest.user.UsersResponse().users(users);
    }

    private static Predicate<EaUser> customDataFilter(String key, String value, boolean exactKeyMatch) {
        return u -> EaModels.visibleCustomData(u).entrySet().stream()
                .anyMatch(e -> (exactKeyMatch ? e.getKey().equals(key) : e.getKey().endsWith(key))
                        && (value == null || value.equals(e.getValue())));
    }

    private Predicate<EaUser> expiresWithin(int days) {
        UserLockPolicyConfig policy = lockPolicy.get();
        int expiryDays = policy == null ? 0 : policy.getPasswordExpiryDays();
        if (expiryDays <= 0) {
            return u -> false;
        }
        Instant horizon = rt.now().plus(Duration.ofDays(days));
        return u -> u.passwordHash() != null && u.passwordLastModified() != null
                && UserStatus.ENABLED.equals(EaModels.toStatus(u.status()))
                && !u.passwordLastModified().plus(Duration.ofDays(expiryDays)).isAfter(horizon);
    }
}
