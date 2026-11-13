/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IA7: external groups map to administrator only through the explicit setting, and only once claimed.
 */
class ExternalAdminMappingTest {

    @Test
    void parsesACommaSeparatedListTrimmingAndDroppingEmptyEntries() {
        assertEquals(Set.of(), ExternalAdminMapping.parseGroups(null));
        assertEquals(Set.of(), ExternalAdminMapping.parseGroups("  "));
        assertEquals(Set.of("ops", "platform admins"), ExternalAdminMapping.parseGroups(" ops,, platform admins ,"));
    }

    @Test
    void grantsAdminOnlyWhenClaimedAndAGroupIsConfigured() {
        Set<String> external = Set.of("developers", "ops");
        assertTrue(ExternalAdminMapping.of(true, "ops").grantsAdmin(external));
        assertFalse(ExternalAdminMapping.of(false, "ops").grantsAdmin(external), "unclaimed: never");
        assertFalse(ExternalAdminMapping.of(true, "").grantsAdmin(external), "empty by default");
        assertFalse(ExternalAdminMapping.of(true, "admins").grantsAdmin(external));
        assertFalse(ExternalAdminMapping.of(true, "OPS").grantsAdmin(external), "names are case sensitive");
        assertFalse(ExternalAdminMapping.of(true, "ops").grantsAdmin(Set.of()));
        assertFalse(ExternalAdminMapping.of(true, "ops").grantsAdmin(null));
    }

    @Test
    void disabledNeverGrantsAdmin() {
        assertFalse(ExternalAdminMapping.DISABLED.grantsAdmin(Set.of("ops", "admins")));
        assertTrue(ExternalAdminMapping.DISABLED.configuredGroups().isEmpty());
    }
}
