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
import java.util.TreeMap;

/**
 * An immutable group held by embedded access, carrying the fields Access exposes in a {@code GroupResponse}.
 * Custom data is copied into a sorted, unmodifiable map. Membership is recorded on {@link EaUser#groups()}, not here.
 *
 * @param name            the unique group name, not blank
 * @param description     a free-text description, or {@code null}
 * @param autoJoin        whether new users join the group automatically
 * @param adminPrivileges whether members are administrators
 * @param realm           the realm the group comes from, for example {@code internal}, not blank
 * @param realmAttributes realm-specific attributes (for example an LDAP DN), or {@code null}
 * @param externalId      an identifier in an external directory, or {@code null}
 * @param customData      free-form string properties Artifactory keeps on the group
 * @param created         when the group was created
 * @param modified        when the group was last modified
 */
public record EaGroup(String name, String description, boolean autoJoin, boolean adminPrivileges, String realm,
        String realmAttributes, String externalId, Map<String, String> customData, Instant created,
        Instant modified) {

    /** Validates the fields and copies the custom data. */
    public EaGroup {
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (Objects.requireNonNull(realm, "realm").isBlank()) {
            throw new IllegalArgumentException("realm must not be blank");
        }
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(modified, "modified");
        customData = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(customData,
                "customData")));
    }

    /**
     * Returns an internal group with no description, flags, attributes or custom data.
     *
     * @param name the group name
     * @param now  the creation and modification time
     * @return the group
     */
    public static EaGroup internal(String name, Instant now) {
        return new EaGroup(name, null, false, false, EaUser.INTERNAL_REALM, null, null, Map.of(), now, now);
    }

    /**
     * Returns a copy with a new modification time.
     *
     * @param at the modification time
     * @return the changed copy
     */
    public EaGroup modifiedAt(Instant at) {
        return new EaGroup(name, description, autoJoin, adminPrivileges, realm, realmAttributes, externalId,
                customData, created, at);
    }
}
