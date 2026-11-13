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
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An immutable permission target held by embedded access, with what an Access {@code PermissionResponse} carries:
 * a name, display name, resource type, owning service, the target's resources as opaque custom data (the
 * repositories and include/exclude patterns Artifactory serialises there), and the actions granted to users and
 * groups. Resource types and actions are kept as the strings Access uses (for example {@code repo} and
 * {@code READ}), so this type does not depend on the Access client model. Principal maps and action sets are copied
 * into sorted, unmodifiable collections.
 *
 * @param name         the unique permission name, not blank
 * @param displayName  the name shown to people, not blank
 * @param resourceType the kind of resource, for example {@code repo}, {@code build} or {@code release-bundle}
 * @param serviceId    the owning service id, for example {@code jfrt@<id>}, not blank
 * @param customData   the resources the permission covers, as Artifactory's opaque JSON, or {@code null}
 * @param userActions  the actions granted to each user name
 * @param groupActions the actions granted to each group name
 * @param implicit     whether Access treats the permission as implicit (not user-managed)
 * @param created      when the permission was created
 * @param modified     when the permission was last modified
 */
public record EaPermission(String name, String displayName, String resourceType, String serviceId,
        String customData, Map<String, Set<String>> userActions, Map<String, Set<String>> groupActions,
        boolean implicit, Instant created, Instant modified) {

    /** Validates the fields and copies the principal maps. */
    public EaPermission {
        requireNonBlank(name, "name");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(resourceType, "resourceType");
        requireNonBlank(serviceId, "serviceId");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(modified, "modified");
        userActions = copy(userActions, "userActions");
        groupActions = copy(groupActions, "groupActions");
    }

    /**
     * Returns a copy with new resources, actions and modification time; identity fields stay unchanged.
     *
     * @param newCustomData   the resources as opaque JSON, or {@code null}
     * @param newUserActions  the actions per user
     * @param newGroupActions the actions per group
     * @param at              the modification time
     * @return the changed copy
     */
    public EaPermission withGrants(String newCustomData, Map<String, Set<String>> newUserActions,
            Map<String, Set<String>> newGroupActions, Instant at) {
        return new EaPermission(name, displayName, resourceType, serviceId, newCustomData, newUserActions,
                newGroupActions, implicit, created, at);
    }

    /**
     * Tells whether a principal is named in this permission, as a user or as a group.
     *
     * @param principal the user or group name
     * @param isGroup   {@code true} to look in the group map
     * @return {@code true} if the principal has an entry
     */
    public boolean mentions(String principal, boolean isGroup) {
        return (isGroup ? groupActions : userActions).containsKey(principal);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static Map<String, Set<String>> copy(Map<String, Set<String>> source, String field) {
        SortedMap<String, Set<String>> out = new TreeMap<>();
        Objects.requireNonNull(source, field).forEach((principal, actions) -> {
            SortedSet<String> set = new TreeSet<>(Objects.requireNonNull(actions, field + " actions"));
            out.put(Objects.requireNonNull(principal, field + " principal"), Collections.unmodifiableSortedSet(set));
        });
        return Collections.unmodifiableSortedMap(out);
    }
}
