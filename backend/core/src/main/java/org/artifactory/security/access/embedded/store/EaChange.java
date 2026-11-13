/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A committed change that caches outside the store must react to. {@link JdbcEmbeddedAccessStore} publishes these
 * on the {@link EaChangeBus} only after the transaction that made the change commits.
 */
public sealed interface EaChange {

    /**
     * Users, groups or permissions changed; the named principals may have different rights now.
     *
     * @param userNames  the affected users, possibly empty
     * @param groupNames the affected groups, possibly empty
     */
    record PermissionsAffected(Set<String> userNames, Set<String> groupNames) implements EaChange {
        /** Copies the sets into sorted, unmodifiable ones. */
        public PermissionsAffected {
            userNames = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(userNames,
                    "userNames")));
            groupNames = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(groupNames,
                    "groupNames")));
        }
    }

    /**
     * One or more tokens of a subject were revoked or consumed.
     *
     * @param subject the token subject, never {@code null}
     */
    record TokenRevoked(String subject) implements EaChange {
        /** Rejects a {@code null} subject. */
        public TokenRevoked {
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * Events were dropped because the bus queue was full; listeners must assume that anything may have changed and
     * drop all their cached state.
     */
    record EverythingChanged() implements EaChange {
    }
}
