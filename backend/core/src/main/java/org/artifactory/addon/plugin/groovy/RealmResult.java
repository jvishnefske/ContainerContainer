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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/**
 * The closed set of answers a plugin realm closure gives. Plugin code builds them through the {@code realm}
 * binding ({@link RealmResults}); anything else a closure returns, and any exception it throws, is treated as
 * {@link NotMine} and reported ({@link RealmInvoker}).
 *
 * <ul>
 *     <li>{@link Authenticated}: the credentials belong to this realm and identify {@code username}. Java still
 *         decides whether to grant it ({@link RealmGrantPolicy}): never as an administrator, only with groups that
 *         exist and carry no admin privileges.</li>
 *     <li>{@link NotMine}: the credentials are not this realm's; the next realm or provider is asked.</li>
 *     <li>{@link Rejected}: the credentials are this realm's and are refused; authentication stops with 401.</li>
 * </ul>
 */
public sealed interface RealmResult {

    /**
     * @param username  the Artifactory user name to authenticate as
     * @param groups    group names the realm asks for; filtered by {@link RealmGrantPolicy}
     * @param jit       whether a missing user should be created just in time (needs {@code autoCreateUsers})
     * @param expiresAt epoch seconds after which the authentication must not be used, if the credential expires
     * @param email     e-mail address to store on a just-in-time user
     */
    record Authenticated(String username, Set<String> groups, boolean jit, OptionalLong expiresAt,
            Optional<String> email) implements RealmResult {
        public Authenticated {
            Objects.requireNonNull(username, "username");
            groups = Set.copyOf(new TreeSet<>(groups));
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(email, "email");
        }
    }

    /** The credentials are not this realm's. */
    record NotMine() implements RealmResult {
    }

    /** The credentials are this realm's and are refused. The reason is logged, never sent to the client. */
    record Rejected(String reason) implements RealmResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
