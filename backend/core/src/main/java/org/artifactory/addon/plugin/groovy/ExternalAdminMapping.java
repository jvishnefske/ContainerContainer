/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The explicit Java-side mapping of external groups to administrator (IA7). Pure.
 * <p>
 * {@link RealmGrantPolicy} never lets a plugin realm grant admin (P3). An operator may still decide that members of
 * some groups of the external identity provider are administrators, by listing those group names in
 * {@code artifactory.security.externalAdminGroups}. This mapping is applied by {@link PluginRealmAuthenticator}
 * after the policy granted an exclusive realm's result, and only when
 * <ul>
 *     <li>the server is claimed: an unclaimed server's administrator is reached only through the setup token, so
 *         external identity cannot bypass the bootstrap;</li>
 *     <li>one of the group names the realm returned (the raw external groups, before the policy filters them down
 *         to existing local groups) equals, case sensitively, one of the configured names.</li>
 * </ul>
 * The resulting admin right holds for that authentication only and is never stored on the user, so the local
 * {@code admin} account stays the break-glass account and the policy's refusal of existing administrators is
 * unchanged. Additive realms are not mapped: they add groups to other providers' authentications and never decide
 * who is administrator.
 *
 * @param claimed          whether the server is claimed
 * @param configuredGroups the configured external administrator groups, empty to disable the mapping
 */
public record ExternalAdminMapping(boolean claimed, Set<String> configuredGroups) {

    /** The mapping that never grants admin. */
    public static final ExternalAdminMapping DISABLED = new ExternalAdminMapping(false, Set.of());

    /** Copies the configured groups. */
    public ExternalAdminMapping {
        configuredGroups = Collections.unmodifiableSet(new TreeSet<>(
                Objects.requireNonNull(configuredGroups, "configuredGroups")));
    }

    /**
     * Builds the mapping from the configuration value.
     *
     * @param claimed whether the server is claimed
     * @param csv     the comma-separated group names; {@code null} or blank for none
     * @return the mapping
     */
    public static ExternalAdminMapping of(boolean claimed, String csv) {
        return new ExternalAdminMapping(claimed, parseGroups(csv));
    }

    /**
     * Parses a comma-separated list of group names: entries are trimmed, empty entries dropped.
     *
     * @param csv the list, or {@code null}
     * @return the names, sorted
     */
    public static Set<String> parseGroups(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Decides whether a user with these external groups is an administrator for this authentication.
     *
     * @param externalGroups the group names the realm returned for the user
     * @return {@code true} only if the server is claimed and some external group is configured as administrator
     */
    public boolean grantsAdmin(Set<String> externalGroups) {
        if (!claimed || configuredGroups.isEmpty() || externalGroups == null) {
            return false;
        }
        return externalGroups.stream().anyMatch(configuredGroups::contains);
    }
}
