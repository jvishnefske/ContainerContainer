/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An immutable user account held by embedded access.
 * <p>
 * A {@code null} {@link #passwordHash()} means the account has no password: the unclaimed built-in administrator
 * (IA1) and just-in-time users from external realms. Such an account, and any account that is not
 * {@link Status#ENABLED}, never authenticates by password ({@link #canAuthenticateByPassword()}).
 * {@link #mustChangePassword()} is the IA5 flag. Group names and custom data are copied into sorted, unmodifiable
 * collections. Build instances with {@link #builder(String, Instant)} and derive changed copies with
 * {@link #toBuilder()}.
 *
 * @param username               the unique login name, not blank
 * @param realm                  the realm the account belongs to, for example {@code internal}, not blank
 * @param status                 the account status
 * @param passwordHash           the encoded password hash (for example {@code {bcrypt}...}), or {@code null}
 * @param mustChangePassword     whether the holder must set a new password before doing anything else (IA5)
 * @param passwordLastModified   when the password was last set, or {@code null} if never
 * @param email                  the e-mail address, or {@code null}
 * @param groups                 the names of the groups the user belongs to
 * @param admin                  whether the user is an administrator in its own right
 * @param customData             free-form string properties Artifactory keeps on the user
 * @param lastLoginTime          the last successful login, or {@code null}
 * @param lastLoginIp            the client address of the last successful login, or {@code null}
 * @param created                when the account was created
 * @param modified               when the account was last modified
 */
public record EaUser(String username, String realm, Status status, String passwordHash, boolean mustChangePassword,
        Instant passwordLastModified, String email, Set<String> groups, boolean admin,
        Map<String, String> customData, Instant lastLoginTime, String lastLoginIp, Instant created,
        Instant modified) {

    /** The realm of locally managed accounts. */
    public static final String INTERNAL_REALM = "internal";

    /** The closed set of account states. */
    public enum Status {
        /** The account may authenticate. */
        ENABLED,
        /** An administrator disabled the account. */
        DISABLED,
        /** The account is locked, for example after repeated failed logins. */
        LOCKED
    }

    /** Validates the fields and copies the collections. */
    public EaUser {
        requireNonBlank(username, "username");
        requireNonBlank(realm, "realm");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(modified, "modified");
        groups = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(groups, "groups")));
        customData = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(customData,
                "customData")));
    }

    /**
     * Tells whether a password login may even be attempted for this account: it is enabled and has a hash.
     *
     * @return {@code true} if the password hash should be checked
     */
    public boolean canAuthenticateByPassword() {
        return status == Status.ENABLED && passwordHash != null;
    }

    /**
     * Returns the password hash if the account has one.
     *
     * @return the hash, or empty
     */
    public Optional<String> passwordHashIfSet() {
        return Optional.ofNullable(passwordHash);
    }

    /**
     * Starts a builder for an enabled internal user with no password, no groups and both timestamps set to
     * {@code now}.
     *
     * @param username the login name
     * @param now      the creation time
     * @return a new builder
     */
    public static Builder builder(String username, Instant now) {
        return new Builder(username, now);
    }

    /**
     * Returns a builder initialised from this user.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder b = new Builder(username, created);
        b.realm = realm;
        b.status = status;
        b.passwordHash = passwordHash;
        b.mustChangePassword = mustChangePassword;
        b.passwordLastModified = passwordLastModified;
        b.email = email;
        b.groups = groups;
        b.admin = admin;
        b.customData = customData;
        b.lastLoginTime = lastLoginTime;
        b.lastLoginIp = lastLoginIp;
        b.modified = modified;
        return b;
    }

    private static void requireNonBlank(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /** A single-use builder for {@link EaUser}; {@link #build()} validates. */
    public static final class Builder {
        private final String username;
        private final Instant created;
        private String realm = INTERNAL_REALM;
        private Status status = Status.ENABLED;
        private String passwordHash;
        private boolean mustChangePassword;
        private Instant passwordLastModified;
        private String email;
        private Set<String> groups = Set.of();
        private boolean admin;
        private Map<String, String> customData = Map.of();
        private Instant lastLoginTime;
        private String lastLoginIp;
        private Instant modified;

        private Builder(String username, Instant created) {
            this.username = username;
            this.created = created;
            this.modified = created;
        }

        /** Sets the realm and returns this builder. */
        public Builder realm(String value) {
            this.realm = value;
            return this;
        }

        /** Sets the status and returns this builder. */
        public Builder status(Status value) {
            this.status = value;
            return this;
        }

        /** Sets the encoded hash ({@code null} for none) and returns this builder. */
        public Builder passwordHash(String value) {
            this.passwordHash = value;
            return this;
        }

        /** Sets the IA5 must-change flag and returns this builder. */
        public Builder mustChangePassword(boolean value) {
            this.mustChangePassword = value;
            return this;
        }

        /** Sets when the password was last set ({@code null} if never) and returns this builder. */
        public Builder passwordLastModified(Instant value) {
            this.passwordLastModified = value;
            return this;
        }

        /** Sets the e-mail address ({@code null} if none) and returns this builder. */
        public Builder email(String value) {
            this.email = value;
            return this;
        }

        /** Sets the group names and returns this builder. */
        public Builder groups(Set<String> value) {
            this.groups = value;
            return this;
        }

        /** Sets the administrator flag and returns this builder. */
        public Builder admin(boolean value) {
            this.admin = value;
            return this;
        }

        /** Sets the custom data and returns this builder. */
        public Builder customData(Map<String, String> value) {
            this.customData = value;
            return this;
        }

        /** Sets the last login time ({@code null} if none) and returns this builder. */
        public Builder lastLoginTime(Instant value) {
            this.lastLoginTime = value;
            return this;
        }

        /** Sets the last login address ({@code null} if none) and returns this builder. */
        public Builder lastLoginIp(String value) {
            this.lastLoginIp = value;
            return this;
        }

        /** Sets the modification time and returns this builder. */
        public Builder modified(Instant value) {
            this.modified = value;
            return this;
        }

        /**
         * Builds the user.
         *
         * @return the user
         * @throws IllegalArgumentException if a required field is blank
         * @throws NullPointerException     if a required field is {@code null}
         */
        public EaUser build() {
            return new EaUser(username, realm, status, passwordHash, mustChangePassword, passwordLastModified, email,
                    groups, admin, customData, lastLoginTime, lastLoginIp, created, modified);
        }
    }
}
