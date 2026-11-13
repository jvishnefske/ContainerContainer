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
import org.artifactory.security.access.embedded.core.LastUpdatedClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * {@link EmbeddedAccessStore} over plain JDBC on the Artifactory {@link DataSource}, for the schema that
 * {@link EmbeddedAccessSchema#ensure} creates (Derby and PostgreSQL).
 * <p>
 * Every public method takes one connection from the data source, turns auto-commit off, runs its statements and
 * commits if the outcome is {@code Ok}; any other outcome, {@link SQLException} or {@link RuntimeException} rolls
 * back. Database failures become {@code Refused} with {@link #STORAGE_ERROR_PREFIX}; constraint violations from a
 * concurrent writer become {@code Refused("conflict: ...")}. Events collected during the transaction are published
 * on the {@link EaChangeBus} after the commit, in order.
 * <p>
 * The {@code permissions.last_updated} stamp is advanced at the end of each user, group, membership and permission
 * mutation, after all other statements, so mutators take the row lock in the same order. It is a compare-and-swap
 * on {@code ea_kv.revision} with up to {@link #KV_CAS_RETRIES} retries; the new value is
 * {@code LastUpdatedClock.next(clock.millis(), previous)}. The clock is the only time source; nothing reads the
 * system clock directly.
 * <p>
 * The class is stateless apart from its three collaborators, so one instance may be shared by all threads.
 */
public final class JdbcEmbeddedAccessStore implements EmbeddedAccessStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcEmbeddedAccessStore.class);

    private static final String USER_COLUMNS = "username, realm, status, password_hash, must_change_password, "
            + "password_last_modified, email, admin, last_login_time, last_login_ip, body, created, modified";
    private static final String GROUP_COLUMNS = "group_name, description, auto_join, admin_privileges, realm, "
            + "realm_attributes, external_id, body, created, modified";
    private static final String PERMISSION_COLUMNS = "perm_name, display_name, resource_type, service_id, implicit, "
            + "body, created, modified";
    private static final String TOKEN_COLUMNS = "jti, subject, issuer, scope, scope_sha256, description, iat, exp, "
            + "refreshable, refresh_hash, revoked";

    private final DataSource dataSource;
    private final Clock clock;
    private final EaChangeBus bus;

    /**
     * Creates a store.
     *
     * @param dataSource the database, whose schema {@link EmbeddedAccessSchema#ensure} has prepared
     * @param clock      the time source for {@code permissions.last_updated}
     * @param bus        where committed changes are published
     */
    public JdbcEmbeddedAccessStore(DataSource dataSource, Clock clock, EaChangeBus bus) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    // ------------------------------------------------------------------------------------------------------------
    // transactions

    /** The state of one transaction: its connection and the events to publish after commit. */
    private static final class Tx {
        private final Connection con;
        private final List<EaChange> events = new ArrayList<>();

        private Tx(Connection con) {
            this.con = con;
        }
    }

    @FunctionalInterface
    private interface TxWork<T> {
        EaOutcome<T> run(Tx tx) throws SQLException;
    }

    private <T> EaOutcome<T> inTx(String operation, TxWork<T> work) {
        try (Connection con = dataSource.getConnection()) {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                Tx tx = new Tx(con);
                EaOutcome<T> outcome = work.run(tx);
                if (outcome.isOk()) {
                    con.commit();
                    tx.events.forEach(bus::publish);
                } else {
                    con.rollback();
                }
                return outcome;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(con);
                throw e;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                log.debug("Embedded access {} hit a constraint", operation, e);
                return EaOutcome.refused("conflict: " + operation + " violates a uniqueness or reference "
                        + "constraint");
            }
            log.error("Embedded access {} failed", operation, e);
            return EaOutcome.refused(STORAGE_ERROR_PREFIX + operation + ": " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Embedded access {} failed", operation, e);
            return EaOutcome.refused(STORAGE_ERROR_PREFIX + operation + ": " + e);
        }
    }

    private static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            log.warn("Rollback failed", e);
        }
    }

    private static <T> EaOutcome<T> retype(EaOutcome<?> notOk) {
        return notOk.fold(v -> {
            throw new IllegalStateException("retype of Ok");
        }, EaOutcome::refused, EaOutcome::unsupported);
    }

    // ------------------------------------------------------------------------------------------------------------
    // users

    @Override
    public EaOutcome<EaUser> createUser(EaUser user) {
        return inTx("createUser", tx -> {
            if (readUser(tx.con, user.username()).isPresent()) {
                return EaOutcome.refused("user already exists: " + user.username());
            }
            EaOutcome<Done> groupsOk = requireGroups(tx.con, user.groups());
            if (!groupsOk.isOk()) {
                return retype(groupsOk);
            }
            insertUserRow(tx.con, user);
            insertMemberships(tx.con, user.username(), user.groups());
            tx.events.add(new EaChange.PermissionsAffected(Set.of(user.username()), user.groups()));
            return bumpThen(tx, () -> readUserOrFail(tx.con, user.username()));
        });
    }

    @Override
    public EaOutcome<Optional<EaUser>> getUser(String username) {
        return inTx("getUser", tx -> EaOutcome.ok(readUser(tx.con, username)));
    }

    @Override
    public EaOutcome<Optional<EaUser>> findUserByUsernameIgnoreCase(String username) {
        return inTx("findUserByUsernameIgnoreCase", tx -> {
            List<EaUser> found = queryUsers(tx.con, "SELECT " + USER_COLUMNS
                    + " FROM ea_users WHERE LOWER(username) = ? ORDER BY username", username.toLowerCase(Locale.ROOT));
            return EaOutcome.ok(found.stream().findFirst());
        });
    }

    @Override
    public EaOutcome<List<EaUser>> findUsersByEmail(String email) {
        return inTx("findUsersByEmail", tx -> EaOutcome.ok(queryUsers(tx.con, "SELECT " + USER_COLUMNS
                + " FROM ea_users WHERE LOWER(email) = ? ORDER BY username", email.toLowerCase(Locale.ROOT))));
    }

    @Override
    public EaOutcome<List<EaUser>> listUsers() {
        return inTx("listUsers", tx -> EaOutcome.ok(queryUsers(tx.con, "SELECT " + USER_COLUMNS
                + " FROM ea_users ORDER BY username", null)));
    }

    @Override
    public EaOutcome<EaUser> updateUser(EaUser user) {
        return inTx("updateUser", tx -> {
            Optional<EaUser> existing = readUser(tx.con, user.username());
            if (existing.isEmpty()) {
                return EaOutcome.refused("no such user: " + user.username());
            }
            EaOutcome<Done> groupsOk = requireGroups(tx.con, user.groups());
            if (!groupsOk.isOk()) {
                return retype(groupsOk);
            }
            try (PreparedStatement ps = tx.con.prepareStatement("UPDATE ea_users SET realm = ?, status = ?, "
                    + "password_hash = ?, must_change_password = ?, password_last_modified = ?, email = ?, "
                    + "admin = ?, last_login_time = ?, last_login_ip = ?, body = ?, modified = ? WHERE username = ?")) {
                ps.setString(1, user.realm());
                ps.setString(2, user.status().name());
                ps.setString(3, user.passwordHash());
                ps.setBoolean(4, user.mustChangePassword());
                setInstant(ps, 5, user.passwordLastModified());
                ps.setString(6, user.email());
                ps.setBoolean(7, user.admin());
                setInstant(ps, 8, user.lastLoginTime());
                ps.setString(9, user.lastLoginIp());
                ps.setString(10, EaJson.principalBody(user.customData()));
                setInstant(ps, 11, user.modified());
                ps.setString(12, user.username());
                ps.executeUpdate();
            }
            Set<String> before = existing.get().groups();
            Set<String> changedGroups = symmetricDifference(before, user.groups());
            deleteMemberships(tx.con, user.username(), difference(before, user.groups()));
            insertMemberships(tx.con, user.username(), difference(user.groups(), before));
            tx.events.add(new EaChange.PermissionsAffected(Set.of(user.username()), changedGroups));
            return bumpThen(tx, () -> readUserOrFail(tx.con, user.username()));
        });
    }

    @Override
    public EaOutcome<Done> deleteUser(String username) {
        return inTx("deleteUser", tx -> {
            Optional<EaUser> existing = readUser(tx.con, username);
            if (existing.isEmpty()) {
                return EaOutcome.refused("no such user: " + username);
            }
            update(tx.con, "DELETE FROM ea_users WHERE username = ?", username);
            tx.events.add(new EaChange.PermissionsAffected(Set.of(username), existing.get().groups()));
            return bumpThen(tx, () -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<EaUser> setPassword(String username, String passwordHash, boolean mustChangePassword,
            Instant passwordLastModified, Instant at) {
        return inTx("setPassword", tx -> {
            int rows;
            try (PreparedStatement ps = tx.con.prepareStatement("UPDATE ea_users SET password_hash = ?, "
                    + "must_change_password = ?, password_last_modified = ?, modified = ? WHERE username = ?")) {
                ps.setString(1, passwordHash);
                ps.setBoolean(2, mustChangePassword);
                setInstant(ps, 3, passwordLastModified);
                setInstant(ps, 4, at);
                ps.setString(5, username);
                rows = ps.executeUpdate();
            }
            if (rows == 0) {
                return EaOutcome.refused("no such user: " + username);
            }
            tx.events.add(new EaChange.PermissionsAffected(Set.of(username), Set.of()));
            return bumpThen(tx, () -> readUserOrFail(tx.con, username));
        });
    }

    @Override
    public EaOutcome<EaUser> setStatus(String username, EaUser.Status status, Instant at) {
        return inTx("setStatus", tx -> {
            int rows;
            try (PreparedStatement ps = tx.con.prepareStatement(
                    "UPDATE ea_users SET status = ?, modified = ? WHERE username = ?")) {
                ps.setString(1, status.name());
                setInstant(ps, 2, at);
                ps.setString(3, username);
                rows = ps.executeUpdate();
            }
            if (rows == 0) {
                return EaOutcome.refused("no such user: " + username);
            }
            tx.events.add(new EaChange.PermissionsAffected(Set.of(username), Set.of()));
            return bumpThen(tx, () -> readUserOrFail(tx.con, username));
        });
    }

    @Override
    public EaOutcome<Done> updateLastLogin(String username, Instant at, String clientIp) {
        return inTx("updateLastLogin", tx -> {
            int rows;
            try (PreparedStatement ps = tx.con.prepareStatement(
                    "UPDATE ea_users SET last_login_time = ?, last_login_ip = ? WHERE username = ?")) {
                setInstant(ps, 1, at);
                ps.setString(2, clientIp);
                ps.setString(3, username);
                rows = ps.executeUpdate();
            }
            return rows == 0 ? EaOutcome.refused("no such user: " + username) : EaOutcome.done();
        });
    }

    @Override
    public EaOutcome<List<EaUser>> listUsersInGroup(String groupName) {
        return inTx("listUsersInGroup", tx -> {
            if (readGroup(tx.con, groupName).isEmpty()) {
                return EaOutcome.refused("no such group: " + groupName);
            }
            return EaOutcome.ok(queryUsers(tx.con, "SELECT " + USER_COLUMNS + " FROM ea_users WHERE username IN "
                    + "(SELECT username FROM ea_user_groups WHERE group_name = ?) ORDER BY username", groupName));
        });
    }

    /**
     * Inserts the {@code ea_users} row of a user, without memberships. Shared with {@link EmbeddedAccessSchema}
     * for the admin seed.
     *
     * @param con  the connection, inside a transaction
     * @param user the user
     * @throws SQLException on a database failure
     */
    static void insertUserRow(Connection con, EaUser user) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO ea_users (" + USER_COLUMNS
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, user.username());
            ps.setString(2, user.realm());
            ps.setString(3, user.status().name());
            ps.setString(4, user.passwordHash());
            ps.setBoolean(5, user.mustChangePassword());
            setInstant(ps, 6, user.passwordLastModified());
            ps.setString(7, user.email());
            ps.setBoolean(8, user.admin());
            setInstant(ps, 9, user.lastLoginTime());
            ps.setString(10, user.lastLoginIp());
            ps.setString(11, EaJson.principalBody(user.customData()));
            setInstant(ps, 12, user.created());
            setInstant(ps, 13, user.modified());
            ps.executeUpdate();
        }
    }

    private Optional<EaUser> readUser(Connection con, String username) throws SQLException {
        return queryUsers(con, "SELECT " + USER_COLUMNS + " FROM ea_users WHERE username = ?", username).stream()
                .findFirst();
    }

    private EaOutcome<EaUser> readUserOrFail(Connection con, String username) throws SQLException {
        return readUser(con, username).map(EaOutcome::ok)
                .orElseGet(() -> EaOutcome.refused("no such user: " + username));
    }

    /** Runs a user query with at most one string parameter and attaches the memberships of the users found. */
    private List<EaUser> queryUsers(Connection con, String sql, String param) throws SQLException {
        List<EaUser.Builder> builders = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    names.add(username);
                    builders.add(EaUser.builder(username, getInstant(rs, "created"))
                            .realm(rs.getString("realm"))
                            .status(EaUser.Status.valueOf(rs.getString("status")))
                            .passwordHash(rs.getString("password_hash"))
                            .mustChangePassword(rs.getBoolean("must_change_password"))
                            .passwordLastModified(getInstant(rs, "password_last_modified"))
                            .email(rs.getString("email"))
                            .admin(rs.getBoolean("admin"))
                            .lastLoginTime(getInstant(rs, "last_login_time"))
                            .lastLoginIp(rs.getString("last_login_ip"))
                            .customData(EaJson.readPrincipalBody(rs.getString("body")))
                            .modified(getInstant(rs, "modified")));
                }
            }
        }
        Map<String, Set<String>> memberships = names.size() == 1 ? Map.of(names.get(0), groupsOf(con, names.get(0)))
                : names.isEmpty() ? Map.of() : allMemberships(con);
        List<EaUser> users = new ArrayList<>(builders.size());
        for (int i = 0; i < builders.size(); i++) {
            users.add(builders.get(i).groups(memberships.getOrDefault(names.get(i), Set.of())).build());
        }
        return List.copyOf(users);
    }

    private static Set<String> groupsOf(Connection con, String username) throws SQLException {
        return new TreeSet<>(queryStrings(con, "SELECT group_name FROM ea_user_groups WHERE username = ?",
                username));
    }

    private static Map<String, Set<String>> allMemberships(Connection con) throws SQLException {
        Map<String, Set<String>> out = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT username, group_name FROM ea_user_groups")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2));
                }
            }
        }
        return out;
    }

    private EaOutcome<Done> requireGroups(Connection con, Set<String> groups) throws SQLException {
        for (String group : groups) {
            if (readGroup(con, group).isEmpty()) {
                return EaOutcome.refused("no such group: " + group);
            }
        }
        return EaOutcome.done();
    }

    private static void insertMemberships(Connection con, String username, Set<String> groups) throws SQLException {
        if (groups.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO ea_user_groups (username, group_name) VALUES (?, ?)")) {
            for (String group : groups) {
                ps.setString(1, username);
                ps.setString(2, group);
                ps.executeUpdate();
            }
        }
    }

    private static void deleteMemberships(Connection con, String username, Set<String> groups) throws SQLException {
        if (groups.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM ea_user_groups WHERE username = ? AND group_name = ?")) {
            for (String group : groups) {
                ps.setString(1, username);
                ps.setString(2, group);
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // groups

    @Override
    public EaOutcome<EaGroup> createGroup(EaGroup group) {
        return inTx("createGroup", tx -> {
            if (readGroup(tx.con, group.name()).isPresent()) {
                return EaOutcome.refused("group already exists: " + group.name());
            }
            try (PreparedStatement ps = tx.con.prepareStatement("INSERT INTO ea_groups (" + GROUP_COLUMNS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, group.name());
                ps.setString(2, group.description());
                ps.setBoolean(3, group.autoJoin());
                ps.setBoolean(4, group.adminPrivileges());
                ps.setString(5, group.realm());
                ps.setString(6, group.realmAttributes());
                ps.setString(7, group.externalId());
                ps.setString(8, EaJson.principalBody(group.customData()));
                setInstant(ps, 9, group.created());
                setInstant(ps, 10, group.modified());
                ps.executeUpdate();
            }
            tx.events.add(new EaChange.PermissionsAffected(Set.of(), Set.of(group.name())));
            return bumpThen(tx, () -> readGroupOrFail(tx.con, group.name()));
        });
    }

    @Override
    public EaOutcome<Optional<EaGroup>> getGroup(String name) {
        return inTx("getGroup", tx -> EaOutcome.ok(readGroup(tx.con, name)));
    }

    @Override
    public EaOutcome<List<EaGroup>> listGroups() {
        return inTx("listGroups", tx -> EaOutcome.ok(queryGroups(tx.con, "SELECT " + GROUP_COLUMNS
                + " FROM ea_groups ORDER BY group_name", null)));
    }

    @Override
    public EaOutcome<EaGroup> updateGroup(EaGroup group) {
        return inTx("updateGroup", tx -> {
            int rows;
            try (PreparedStatement ps = tx.con.prepareStatement("UPDATE ea_groups SET description = ?, "
                    + "auto_join = ?, admin_privileges = ?, realm = ?, realm_attributes = ?, external_id = ?, "
                    + "body = ?, modified = ? WHERE group_name = ?")) {
                ps.setString(1, group.description());
                ps.setBoolean(2, group.autoJoin());
                ps.setBoolean(3, group.adminPrivileges());
                ps.setString(4, group.realm());
                ps.setString(5, group.realmAttributes());
                ps.setString(6, group.externalId());
                ps.setString(7, EaJson.principalBody(group.customData()));
                setInstant(ps, 8, group.modified());
                ps.setString(9, group.name());
                rows = ps.executeUpdate();
            }
            if (rows == 0) {
                return EaOutcome.refused("no such group: " + group.name());
            }
            tx.events.add(new EaChange.PermissionsAffected(membersOf(tx.con, group.name()), Set.of(group.name())));
            return bumpThen(tx, () -> readGroupOrFail(tx.con, group.name()));
        });
    }

    @Override
    public EaOutcome<Done> deleteGroup(String name) {
        return inTx("deleteGroup", tx -> {
            if (readGroup(tx.con, name).isEmpty()) {
                return EaOutcome.refused("no such group: " + name);
            }
            Set<String> members = membersOf(tx.con, name);
            update(tx.con, "DELETE FROM ea_groups WHERE group_name = ?", name);
            tx.events.add(new EaChange.PermissionsAffected(members, Set.of(name)));
            return bumpThen(tx, () -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<Done> addGroupMembers(String groupName, Set<String> usernames) {
        return inTx("addGroupMembers", tx -> {
            if (readGroup(tx.con, groupName).isEmpty()) {
                return EaOutcome.refused("no such group: " + groupName);
            }
            for (String username : new TreeSet<>(usernames)) {
                if (!exists(tx.con, "SELECT 1 FROM ea_users WHERE username = ?", username)) {
                    return EaOutcome.refused("no such user: " + username);
                }
            }
            Set<String> added = difference(new TreeSet<>(usernames), membersOf(tx.con, groupName));
            for (String username : added) {
                insertMemberships(tx.con, username, Set.of(groupName));
            }
            if (added.isEmpty()) {
                return EaOutcome.done();
            }
            tx.events.add(new EaChange.PermissionsAffected(added, Set.of(groupName)));
            return bumpThen(tx, () -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<Done> removeGroupMembers(String groupName, Set<String> usernames) {
        return inTx("removeGroupMembers", tx -> {
            if (readGroup(tx.con, groupName).isEmpty()) {
                return EaOutcome.refused("no such group: " + groupName);
            }
            Set<String> removed = new TreeSet<>(usernames);
            removed.retainAll(membersOf(tx.con, groupName));
            for (String username : removed) {
                deleteMemberships(tx.con, username, Set.of(groupName));
            }
            if (removed.isEmpty()) {
                return EaOutcome.done();
            }
            tx.events.add(new EaChange.PermissionsAffected(removed, Set.of(groupName)));
            return bumpThen(tx, () -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<List<String>> groupMembers(String groupName) {
        return inTx("groupMembers", tx -> {
            if (readGroup(tx.con, groupName).isEmpty()) {
                return EaOutcome.refused("no such group: " + groupName);
            }
            return EaOutcome.ok(List.copyOf(membersOf(tx.con, groupName)));
        });
    }

    private static Set<String> membersOf(Connection con, String groupName) throws SQLException {
        return new TreeSet<>(queryStrings(con, "SELECT username FROM ea_user_groups WHERE group_name = ?",
                groupName));
    }

    private Optional<EaGroup> readGroup(Connection con, String name) throws SQLException {
        return queryGroups(con, "SELECT " + GROUP_COLUMNS + " FROM ea_groups WHERE group_name = ?", name).stream()
                .findFirst();
    }

    private EaOutcome<EaGroup> readGroupOrFail(Connection con, String name) throws SQLException {
        return readGroup(con, name).map(EaOutcome::ok).orElseGet(() -> EaOutcome.refused("no such group: " + name));
    }

    private static List<EaGroup> queryGroups(Connection con, String sql, String param) throws SQLException {
        List<EaGroup> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EaGroup(rs.getString("group_name"), rs.getString("description"),
                            rs.getBoolean("auto_join"), rs.getBoolean("admin_privileges"), rs.getString("realm"),
                            rs.getString("realm_attributes"), rs.getString("external_id"),
                            EaJson.readPrincipalBody(rs.getString("body")), getInstant(rs, "created"),
                            getInstant(rs, "modified")));
                }
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------------------
    // permissions

    @Override
    public EaOutcome<EaPermission> createPermission(EaPermission permission) {
        return inTx("createPermission", tx -> {
            if (readPermission(tx.con, permission.name()).isPresent()) {
                return EaOutcome.refused("permission already exists: " + permission.name());
            }
            try (PreparedStatement ps = tx.con.prepareStatement("INSERT INTO ea_permissions (" + PERMISSION_COLUMNS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, permission.name());
                ps.setString(2, permission.displayName());
                ps.setString(3, permission.resourceType());
                ps.setString(4, permission.serviceId());
                ps.setBoolean(5, permission.implicit());
                ps.setString(6, EaJson.permissionBody(permission.customData(), permission.userActions(),
                        permission.groupActions()));
                setInstant(ps, 7, permission.created());
                setInstant(ps, 8, permission.modified());
                ps.executeUpdate();
            }
            tx.events.add(principalsOf(permission, null));
            return bumpThen(tx, () -> readPermissionOrFail(tx.con, permission.name()));
        });
    }

    @Override
    public EaOutcome<Optional<EaPermission>> getPermission(String name) {
        return inTx("getPermission", tx -> EaOutcome.ok(readPermission(tx.con, name)));
    }

    @Override
    public EaOutcome<List<EaPermission>> listPermissions() {
        return inTx("listPermissions", tx -> EaOutcome.ok(queryPermissions(tx.con, "SELECT " + PERMISSION_COLUMNS
                + " FROM ea_permissions ORDER BY perm_name")));
    }

    @Override
    public EaOutcome<List<EaPermission>> findPermissions(String resourceType, String displayName) {
        return inTx("findPermissions", tx -> EaOutcome.ok(queryPermissions(tx.con, "SELECT " + PERMISSION_COLUMNS
                + " FROM ea_permissions WHERE resource_type = ? AND display_name = ? ORDER BY perm_name",
                resourceType, displayName)));
    }

    @Override
    public EaOutcome<List<EaPermission>> listPermissionsMentioning(String principal, boolean isGroup) {
        return listPermissions().map(all -> all.stream().filter(p -> p.mentions(principal, isGroup)).toList());
    }

    @Override
    public EaOutcome<EaPermission> updatePermission(EaPermission permission) {
        return inTx("updatePermission", tx -> {
            Optional<EaPermission> existing = readPermission(tx.con, permission.name());
            if (existing.isEmpty()) {
                return EaOutcome.refused("no such permission: " + permission.name());
            }
            try (PreparedStatement ps = tx.con.prepareStatement("UPDATE ea_permissions SET display_name = ?, "
                    + "resource_type = ?, service_id = ?, implicit = ?, body = ?, modified = ? WHERE perm_name = ?")) {
                ps.setString(1, permission.displayName());
                ps.setString(2, permission.resourceType());
                ps.setString(3, permission.serviceId());
                ps.setBoolean(4, permission.implicit());
                ps.setString(5, EaJson.permissionBody(permission.customData(), permission.userActions(),
                        permission.groupActions()));
                setInstant(ps, 6, permission.modified());
                ps.setString(7, permission.name());
                ps.executeUpdate();
            }
            tx.events.add(principalsOf(permission, existing.get()));
            return bumpThen(tx, () -> readPermissionOrFail(tx.con, permission.name()));
        });
    }

    @Override
    public EaOutcome<Done> deletePermission(String name) {
        return inTx("deletePermission", tx -> {
            Optional<EaPermission> existing = readPermission(tx.con, name);
            if (existing.isEmpty()) {
                return EaOutcome.refused("no such permission: " + name);
            }
            update(tx.con, "DELETE FROM ea_permissions WHERE perm_name = ?", name);
            tx.events.add(principalsOf(existing.get(), null));
            return bumpThen(tx, () -> EaOutcome.done());
        });
    }

    @Override
    public EaOutcome<Long> permissionsLastUpdated() {
        return inTx("permissionsLastUpdated", tx -> EaOutcome.ok(readKv(tx.con, PERMISSIONS_LAST_UPDATED_KEY)
                .map(e -> Long.parseLong(e.value())).orElse(0L)));
    }

    private static EaChange principalsOf(EaPermission permission, EaPermission previous) {
        Set<String> users = new TreeSet<>(permission.userActions().keySet());
        Set<String> groups = new TreeSet<>(permission.groupActions().keySet());
        if (previous != null) {
            users.addAll(previous.userActions().keySet());
            groups.addAll(previous.groupActions().keySet());
        }
        return new EaChange.PermissionsAffected(users, groups);
    }

    private Optional<EaPermission> readPermission(Connection con, String name) throws SQLException {
        return queryPermissions(con, "SELECT " + PERMISSION_COLUMNS + " FROM ea_permissions WHERE perm_name = ?",
                name).stream().findFirst();
    }

    private EaOutcome<EaPermission> readPermissionOrFail(Connection con, String name) throws SQLException {
        return readPermission(con, name).map(EaOutcome::ok)
                .orElseGet(() -> EaOutcome.refused("no such permission: " + name));
    }

    private static List<EaPermission> queryPermissions(Connection con, String sql, String... params)
            throws SQLException {
        List<EaPermission> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EaJson.PermissionBody body = EaJson.readPermissionBody(rs.getString("body"));
                    out.add(new EaPermission(rs.getString("perm_name"), rs.getString("display_name"),
                            rs.getString("resource_type"), rs.getString("service_id"), body.customData(),
                            body.userActions(), body.groupActions(), rs.getBoolean("implicit"),
                            getInstant(rs, "created"), getInstant(rs, "modified")));
                }
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------------------
    // tokens

    @Override
    public EaOutcome<Done> insertToken(EaToken token) {
        return inTx("insertToken", tx -> {
            if (readToken(tx.con, token.jti()).isPresent()) {
                return EaOutcome.refused("token already exists: " + token.jti());
            }
            if (token.refreshHash() != null && exists(tx.con, "SELECT 1 FROM ea_tokens WHERE refresh_hash = ?",
                    token.refreshHash())) {
                return EaOutcome.refused("refresh hash already in use");
            }
            try (PreparedStatement ps = tx.con.prepareStatement("INSERT INTO ea_tokens (" + TOKEN_COLUMNS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, token.jti());
                ps.setString(2, token.subject());
                ps.setString(3, token.issuer());
                ps.setString(4, token.scope());
                ps.setString(5, token.scopeSha256());
                ps.setString(6, token.description());
                setInstant(ps, 7, token.issuedAt());
                setInstant(ps, 8, token.expiresAt());
                ps.setBoolean(9, token.refreshable());
                ps.setString(10, token.refreshHash());
                ps.setBoolean(11, token.revoked());
                ps.executeUpdate();
            }
            return EaOutcome.done();
        });
    }

    @Override
    public EaOutcome<Optional<EaToken>> getToken(String jti) {
        return inTx("getToken", tx -> EaOutcome.ok(readToken(tx.con, jti)));
    }

    @Override
    public EaOutcome<List<EaToken>> listTokens() {
        return inTx("listTokens", tx -> EaOutcome.ok(queryTokens(tx.con, "SELECT " + TOKEN_COLUMNS
                + " FROM ea_tokens ORDER BY jti", null)));
    }

    @Override
    public EaOutcome<List<EaToken>> listTokensBySubject(String subject) {
        return inTx("listTokensBySubject", tx -> EaOutcome.ok(queryTokens(tx.con, "SELECT " + TOKEN_COLUMNS
                + " FROM ea_tokens WHERE subject = ? ORDER BY jti", subject)));
    }

    @Override
    public EaOutcome<Done> revokeToken(String jti) {
        return inTx("revokeToken", tx -> {
            Optional<EaToken> token = readToken(tx.con, jti);
            if (token.isEmpty()) {
                return EaOutcome.refused("no such token: " + jti);
            }
            if (markRevoked(tx.con, jti) > 0) {
                tx.events.add(new EaChange.TokenRevoked(token.get().subject()));
            }
            return EaOutcome.done();
        });
    }

    @Override
    public EaOutcome<Integer> revokeTokensBySubject(String subject) {
        return inTx("revokeTokensBySubject", tx -> {
            int rows;
            try (PreparedStatement ps = tx.con.prepareStatement(
                    "UPDATE ea_tokens SET revoked = ? WHERE subject = ? AND revoked = ?")) {
                ps.setBoolean(1, true);
                ps.setString(2, subject);
                ps.setBoolean(3, false);
                rows = ps.executeUpdate();
            }
            if (rows > 0) {
                tx.events.add(new EaChange.TokenRevoked(subject));
            }
            return EaOutcome.ok(rows);
        });
    }

    @Override
    public EaOutcome<EaToken> consumeRefreshToken(String refreshHash) {
        return inTx("consumeRefreshToken", tx -> {
            List<EaToken> found = queryTokens(tx.con, "SELECT " + TOKEN_COLUMNS
                    + " FROM ea_tokens WHERE refresh_hash = ?", refreshHash);
            Optional<EaToken> live = found.stream().filter(t -> !t.revoked()).findFirst();
            // The conditional update is the atomic step: of two concurrent consumers only one changes the row.
            if (live.isEmpty() || markRevoked(tx.con, live.get().jti()) == 0) {
                return EaOutcome.refused("no unused refresh token matches");
            }
            tx.events.add(new EaChange.TokenRevoked(live.get().subject()));
            return EaOutcome.ok(live.get().asRevoked());
        });
    }

    @Override
    public EaOutcome<Integer> purgeExpiredTokens(Instant before) {
        return inTx("purgeExpiredTokens", tx -> {
            try (PreparedStatement ps = tx.con.prepareStatement(
                    "DELETE FROM ea_tokens WHERE exp IS NOT NULL AND exp < ?")) {
                ps.setLong(1, before.toEpochMilli());
                return EaOutcome.ok(ps.executeUpdate());
            }
        });
    }

    private static int markRevoked(Connection con, String jti) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE ea_tokens SET revoked = ? WHERE jti = ? AND revoked = ?")) {
            ps.setBoolean(1, true);
            ps.setString(2, jti);
            ps.setBoolean(3, false);
            return ps.executeUpdate();
        }
    }

    private static Optional<EaToken> readToken(Connection con, String jti) throws SQLException {
        return queryTokens(con, "SELECT " + TOKEN_COLUMNS + " FROM ea_tokens WHERE jti = ?", jti).stream()
                .findFirst();
    }

    private static List<EaToken> queryTokens(Connection con, String sql, String param) throws SQLException {
        List<EaToken> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EaToken(rs.getString("jti"), rs.getString("subject"), rs.getString("issuer"),
                            rs.getString("scope"), rs.getString("description"), getInstant(rs, "iat"),
                            getInstant(rs, "exp"), rs.getBoolean("refreshable"), rs.getString("refresh_hash"),
                            rs.getBoolean("revoked")));
                }
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------------------
    // key-value

    @Override
    public EaOutcome<Optional<EaKvEntry>> getKv(String key) {
        return inTx("getKv", tx -> EaOutcome.ok(readKv(tx.con, key)));
    }

    @Override
    public EaOutcome<EaKvEntry> compareAndSetKv(String key, long expectedRevision, String value) {
        Objects.requireNonNull(value, "value");
        return inTx("compareAndSetKv", tx -> casKv(tx.con, key, expectedRevision, value)
                ? EaOutcome.ok(new EaKvEntry(key, expectedRevision + 1, value))
                : EaOutcome.refused("conflict: " + key + " is not at revision " + expectedRevision));
    }

    @Override
    public EaOutcome<EaKvEntry> updateKv(String key, Function<Optional<String>, EaOutcome<String>> update) {
        for (int attempt = 0; attempt <= KV_CAS_RETRIES; attempt++) {
            EaOutcome<Optional<EaKvEntry>> current = getKv(key);
            if (!(current instanceof EaOutcome.Ok<Optional<EaKvEntry>> ok)) {
                return retype(current);
            }
            long revision = ok.value().map(EaKvEntry::revision).orElse(EaKvEntry.ABSENT_REVISION);
            EaOutcome<String> next = update.apply(ok.value().map(EaKvEntry::value));
            if (!(next instanceof EaOutcome.Ok<String> value)) {
                return retype(next);
            }
            EaOutcome<EaKvEntry> written = compareAndSetKv(key, revision, value.value());
            if (written.isOk() || !isCasConflict(written)) {
                return written;
            }
            log.debug("Compare-and-swap conflict on {} (attempt {})", key, attempt + 1);
        }
        return EaOutcome.refused("conflict: " + key + " kept changing; gave up after " + KV_CAS_RETRIES
                + " retries");
    }

    @Override
    public EaOutcome<Boolean> deleteKv(String key) {
        return inTx("deleteKv", tx -> EaOutcome.ok(update(tx.con, "DELETE FROM ea_kv WHERE k = ?", key) > 0));
    }

    private static boolean isCasConflict(EaOutcome<?> outcome) {
        return outcome instanceof EaOutcome.Refused<?> refused && refused.reason().startsWith("conflict: ");
    }

    private static Optional<EaKvEntry> readKv(Connection con, String key) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT revision, v FROM ea_kv WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new EaKvEntry(key, rs.getLong(1), rs.getString(2))) : Optional.empty();
            }
        }
    }

    /**
     * One compare-and-swap step inside the caller's transaction. Creating an absent key uses a savepoint, so a
     * duplicate-key failure (a concurrent creator) is a conflict rather than an aborted transaction.
     *
     * @return {@code true} if the row was written
     */
    private static boolean casKv(Connection con, String key, long expectedRevision, String value)
            throws SQLException {
        if (expectedRevision == EaKvEntry.ABSENT_REVISION) {
            if (readKv(con, key).isPresent()) {
                return false;
            }
            Savepoint savepoint = con.setSavepoint();
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO ea_kv (k, revision, v) VALUES (?, ?, ?)")) {
                ps.setString(1, key);
                ps.setLong(2, 1L);
                ps.setString(3, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!isConstraintViolation(e)) {
                    throw e;
                }
                con.rollback(savepoint);
                return false;
            }
            con.releaseSavepoint(savepoint);
            return true;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE ea_kv SET revision = ?, v = ? WHERE k = ? AND revision = ?")) {
            ps.setLong(1, expectedRevision + 1);
            ps.setString(2, value);
            ps.setString(3, key);
            ps.setLong(4, expectedRevision);
            return ps.executeUpdate() == 1;
        }
    }

    /** Advances {@code permissions.last_updated} inside the caller's transaction. */
    private EaOutcome<?> bumpLastUpdated(Connection con) throws SQLException {
        for (int attempt = 0; attempt <= KV_CAS_RETRIES; attempt++) {
            Optional<EaKvEntry> current = readKv(con, PERMISSIONS_LAST_UPDATED_KEY);
            long previous = current.map(e -> Long.parseLong(e.value())).orElse(0L);
            EaOutcome<Long> next = LastUpdatedClock.next(clock.millis(), previous);
            if (!(next instanceof EaOutcome.Ok<Long> stamp)) {
                return next;
            }
            long revision = current.map(EaKvEntry::revision).orElse(EaKvEntry.ABSENT_REVISION);
            if (casKv(con, PERMISSIONS_LAST_UPDATED_KEY, revision, Long.toString(stamp.value()))) {
                return EaOutcome.done();
            }
        }
        return EaOutcome.refused("conflict: " + PERMISSIONS_LAST_UPDATED_KEY + " kept changing; gave up after "
                + KV_CAS_RETRIES + " retries");
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        EaOutcome<T> get() throws SQLException;
    }

    private <T> EaOutcome<T> bumpThen(Tx tx, SqlSupplier<T> then) throws SQLException {
        EaOutcome<?> bumped = bumpLastUpdated(tx.con);
        return bumped.isOk() ? then.get() : retype(bumped);
    }

    // ------------------------------------------------------------------------------------------------------------
    // helpers

    private static int update(Connection con, String sql, String param) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            return ps.executeUpdate();
        }
    }

    private static boolean exists(Connection con, String sql, String param) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<String> queryStrings(Connection con, String sql, String param) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value.toEpochMilli());
        }
    }

    private static Instant getInstant(ResultSet rs, String column) throws SQLException {
        long millis = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static Set<String> symmetricDifference(Set<String> a, Set<String> b) {
        Set<String> out = difference(a, b);
        out.addAll(difference(b, a));
        return out;
    }
}
