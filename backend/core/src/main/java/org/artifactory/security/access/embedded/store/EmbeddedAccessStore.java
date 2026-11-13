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
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaOutcome.Done;
import org.artifactory.security.access.embedded.core.EaPermission;
import org.artifactory.security.access.embedded.core.EaToken;
import org.artifactory.security.access.embedded.core.EaUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Persistent state of the embedded Access implementation: users, groups, memberships, permissions, tokens and a
 * small key-value table.
 * <p>
 * Contract shared by every method:
 * <ul>
 *     <li>Each call is one transaction: it commits completely or not at all.</li>
 *     <li>Nothing throws for expected conditions. A missing row on a lookup is an empty {@link Optional}; a missing
 *     row on a mutation, a duplicate key or a compare-and-swap conflict is {@code Refused}; a database failure is
 *     {@code Refused} with a reason starting with {@link #STORAGE_ERROR_PREFIX}.</li>
 *     <li>Every user, group, membership or permission mutation advances {@link #PERMISSIONS_LAST_UPDATED_KEY} in
 *     the same transaction, by {@code LastUpdatedClock.next}. Only {@link #updateLastLogin} does not, because a
 *     login changes no rights.</li>
 *     <li>After the commit, and never after a rollback, mutations publish {@link EaChange} events.</li>
 *     <li>Times are stored with millisecond precision; finer parts of an {@link Instant} are truncated.</li>
 *     <li>Lists are sorted by their primary key.</li>
 * </ul>
 * The {@link EaUser#groups()} of a returned user are read from the membership table; on create and update the
 * memberships are replaced by the given set, and every named group must exist.
 */
public interface EmbeddedAccessStore {

    /** The {@code ea_kv} key of the strictly increasing user, group and permission change stamp. */
    String PERMISSIONS_LAST_UPDATED_KEY = "permissions.last_updated";

    /** The prefix of every {@code Refused} reason caused by a database failure rather than by the input. */
    String STORAGE_ERROR_PREFIX = "storage error: ";

    /** How often a compare-and-swap on {@code ea_kv} is retried after a conflict before giving up. */
    int KV_CAS_RETRIES = 3;

    // ---- users ----

    /**
     * Inserts a user and its memberships.
     *
     * @param user the user; {@code created} and {@code modified} are stored as given
     * @return the stored user, or {@code Refused} if the username exists or a group is missing
     */
    EaOutcome<EaUser> createUser(EaUser user);

    /**
     * Reads a user by exact username.
     *
     * @param username the username
     * @return the user, or empty
     */
    EaOutcome<Optional<EaUser>> getUser(String username);

    /**
     * Reads a user by username, ignoring case.
     *
     * @param username the username in any case
     * @return the user whose username equals {@code username} ignoring case (the lowest username if several), or
     *         empty
     */
    EaOutcome<Optional<EaUser>> findUserByUsernameIgnoreCase(String username);

    /**
     * Finds users by e-mail address, ignoring case.
     *
     * @param email the address
     * @return the matching users, possibly empty
     */
    EaOutcome<List<EaUser>> findUsersByEmail(String email);

    /**
     * Lists all users.
     *
     * @return every user
     */
    EaOutcome<List<EaUser>> listUsers();

    /**
     * Replaces every stored field of an existing user, including its memberships, except {@code created}, which
     * keeps the stored value.
     *
     * @param user the new state; {@code modified} is stored as given
     * @return the stored user, or {@code Refused} if the user or a group is missing
     */
    EaOutcome<EaUser> updateUser(EaUser user);

    /**
     * Deletes a user; its memberships go with it.
     *
     * @param username the username
     * @return {@code Done}, or {@code Refused} if the user is missing
     */
    EaOutcome<Done> deleteUser(String username);

    /**
     * Sets or clears the password hash, the must-change flag and the password time of an existing user, and sets
     * {@code modified} to {@code at}.
     *
     * @param username             the username
     * @param passwordHash         the encoded hash, or {@code null} for no password
     * @param mustChangePassword   the IA5 flag
     * @param passwordLastModified when the password was set, or {@code null}
     * @param at                   the modification time
     * @return the stored user, or {@code Refused} if the user is missing
     */
    EaOutcome<EaUser> setPassword(String username, String passwordHash, boolean mustChangePassword,
            Instant passwordLastModified, Instant at);

    /**
     * Sets the status of an existing user and sets {@code modified} to {@code at}.
     *
     * @param username the username
     * @param status   the new status
     * @param at       the modification time
     * @return the stored user, or {@code Refused} if the user is missing
     */
    EaOutcome<EaUser> setStatus(String username, EaUser.Status status, Instant at);

    /**
     * Records a login. Does not advance {@link #PERMISSIONS_LAST_UPDATED_KEY}, does not change {@code modified} and
     * publishes nothing.
     *
     * @param username the username
     * @param at       the login time
     * @param clientIp the client address, or {@code null}
     * @return {@code Done}, or {@code Refused} if the user is missing
     */
    EaOutcome<Done> updateLastLogin(String username, Instant at, String clientIp);

    /**
     * Lists the users that belong to a group.
     *
     * @param groupName the group name
     * @return the members, or {@code Refused} if the group is missing
     */
    EaOutcome<List<EaUser>> listUsersInGroup(String groupName);

    // ---- groups ----

    /**
     * Inserts a group.
     *
     * @param group the group
     * @return the stored group, or {@code Refused} if the name exists
     */
    EaOutcome<EaGroup> createGroup(EaGroup group);

    /**
     * Reads a group by name.
     *
     * @param name the group name
     * @return the group, or empty
     */
    EaOutcome<Optional<EaGroup>> getGroup(String name);

    /**
     * Lists all groups.
     *
     * @return every group
     */
    EaOutcome<List<EaGroup>> listGroups();

    /**
     * Replaces every stored field of an existing group except {@code created}, which keeps the stored value.
     * Memberships are untouched.
     *
     * @param group the new state
     * @return the stored group, or {@code Refused} if it is missing
     */
    EaOutcome<EaGroup> updateGroup(EaGroup group);

    /**
     * Deletes a group; its memberships go with it (cascade).
     *
     * @param name the group name
     * @return {@code Done}, or {@code Refused} if the group is missing
     */
    EaOutcome<Done> deleteGroup(String name);

    /**
     * Adds users to a group; users that are already members are left as they are.
     *
     * @param groupName the group name
     * @param usernames the users to add
     * @return {@code Done}, or {@code Refused} if the group or any user is missing (then nothing is added)
     */
    EaOutcome<Done> addGroupMembers(String groupName, Set<String> usernames);

    /**
     * Removes users from a group; names that are not members are ignored.
     *
     * @param groupName the group name
     * @param usernames the users to remove
     * @return {@code Done}, or {@code Refused} if the group is missing
     */
    EaOutcome<Done> removeGroupMembers(String groupName, Set<String> usernames);

    /**
     * Lists the usernames of a group's members.
     *
     * @param groupName the group name
     * @return the sorted usernames, or {@code Refused} if the group is missing
     */
    EaOutcome<List<String>> groupMembers(String groupName);

    // ---- permissions ----

    /**
     * Inserts a permission.
     *
     * @param permission the permission
     * @return the stored permission, or {@code Refused} if the name exists
     */
    EaOutcome<EaPermission> createPermission(EaPermission permission);

    /**
     * Reads a permission by name.
     *
     * @param name the permission name
     * @return the permission, or empty
     */
    EaOutcome<Optional<EaPermission>> getPermission(String name);

    /**
     * Lists all permissions.
     *
     * @return every permission
     */
    EaOutcome<List<EaPermission>> listPermissions();

    /**
     * Finds permissions by resource type and display name (both exact).
     *
     * @param resourceType the resource type, for example {@code repo}
     * @param displayName  the display name
     * @return the matching permissions, possibly empty
     */
    EaOutcome<List<EaPermission>> findPermissions(String resourceType, String displayName);

    /**
     * Lists the permissions that name a principal, by {@link EaPermission#mentions(String, boolean)}.
     *
     * @param principal the user or group name
     * @param isGroup   {@code true} for a group
     * @return the matching permissions, possibly empty
     */
    EaOutcome<List<EaPermission>> listPermissionsMentioning(String principal, boolean isGroup);

    /**
     * Replaces every stored field of an existing permission except {@code created}, which keeps the stored value.
     *
     * @param permission the new state
     * @return the stored permission, or {@code Refused} if it is missing
     */
    EaOutcome<EaPermission> updatePermission(EaPermission permission);

    /**
     * Deletes a permission.
     *
     * @param name the permission name
     * @return {@code Done}, or {@code Refused} if it is missing
     */
    EaOutcome<Done> deletePermission(String name);

    /**
     * Reads the {@link #PERMISSIONS_LAST_UPDATED_KEY} stamp.
     *
     * @return the stamp in epoch milliseconds, or 0 if nothing was ever changed
     */
    EaOutcome<Long> permissionsLastUpdated();

    // ---- tokens ----

    /**
     * Inserts a token record. The store does not check revocability: that is the caller's rule.
     *
     * @param token the token
     * @return {@code Done}, or {@code Refused} if the jti or the refresh hash exists
     */
    EaOutcome<Done> insertToken(EaToken token);

    /**
     * Reads a token by id.
     *
     * @param jti the token id
     * @return the token, or empty
     */
    EaOutcome<Optional<EaToken>> getToken(String jti);

    /**
     * Lists all token records, revoked ones included.
     *
     * @return every token, by jti
     */
    EaOutcome<List<EaToken>> listTokens();

    /**
     * Lists the token records of a subject, revoked ones included.
     *
     * @param subject the exact subject
     * @return the tokens, by jti
     */
    EaOutcome<List<EaToken>> listTokensBySubject(String subject);

    /**
     * Marks a token revoked. Revoking a revoked token succeeds and publishes nothing; otherwise
     * {@link EaChange.TokenRevoked} is published after commit.
     *
     * @param jti the token id
     * @return {@code Done}, or {@code Refused} if the token is missing
     */
    EaOutcome<Done> revokeToken(String jti);

    /**
     * Marks every unrevoked token of a subject revoked, publishing {@link EaChange.TokenRevoked} if any was.
     *
     * @param subject the exact subject
     * @return the number of tokens revoked by this call
     */
    EaOutcome<Integer> revokeTokensBySubject(String subject);

    /**
     * Consumes a refresh token exactly once: the unrevoked token whose refresh hash matches is marked revoked
     * and returned. Of concurrent calls with the same hash at most one succeeds. Publishes
     * {@link EaChange.TokenRevoked} on success. Expiry is not checked here; the caller decides.
     *
     * @param refreshHash the SHA-256 hex of the presented refresh token
     * @return the consumed token (now revoked), or {@code Refused} if no unrevoked token has that hash
     */
    EaOutcome<EaToken> consumeRefreshToken(String refreshHash);

    /**
     * Deletes the token records that expired strictly before an instant. Tokens without expiry are kept.
     *
     * @param before the cut-off
     * @return the number of deleted records
     */
    EaOutcome<Integer> purgeExpiredTokens(Instant before);

    // ---- key-value ----

    /**
     * Reads a key.
     *
     * @param key the key
     * @return the entry, or empty
     */
    EaOutcome<Optional<EaKvEntry>> getKv(String key);

    /**
     * Writes a key if its revision is still {@code expectedRevision}. One attempt, no retry.
     *
     * @param key              the key
     * @param expectedRevision the revision last read, or {@link EaKvEntry#ABSENT_REVISION} to create the key
     * @param value            the new value
     * @return the new entry (revision {@code expectedRevision + 1}), or {@code Refused} on conflict
     */
    EaOutcome<EaKvEntry> compareAndSetKv(String key, long expectedRevision, String value);

    /**
     * Reads a key, computes its new value and writes it with compare-and-swap, retrying on conflict.
     * {@code update} runs outside any transaction and may run several times, so it must be pure; it receives the
     * current value (empty if absent). If it returns a non-{@code Ok} outcome nothing is written and that outcome is
     * returned. After {@link #KV_CAS_RETRIES} retries the call gives up with {@code Refused}.
     *
     * @param key    the key
     * @param update computes the new value from the current one
     * @return the written entry
     */
    EaOutcome<EaKvEntry> updateKv(String key, Function<Optional<String>, EaOutcome<String>> update);

    /**
     * Deletes a key.
     *
     * @param key the key
     * @return {@code true} if it existed
     */
    EaOutcome<Boolean> deleteKv(String key);
}
