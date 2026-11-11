/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.addon.plugin.pkgtype.PluginPackageTypes.CONFIG_KEY;
import static org.artifactory.addon.plugin.pkgtype.PluginPackageTypes.packageTypeConfigForUpdate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** An update that does not mention the plugin package type keeps it; explicit values set or clear it. */
class PluginPackageTypesTest {

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void updateWithoutTheKeyKeepsStoredType() {
        Map<String, String> requested = map("other", "v");
        Map<String, String> result = packageTypeConfigForUpdate(RepoType.Generic, requested, RepoType.Generic,
                map(CONFIG_KEY, "simple", "other", "old"));
        assertEquals(map(CONFIG_KEY, "simple", "other", "v"), result);
        assertEquals(map("other", "v"), requested, "the request map is not modified");
    }

    @Test
    void updateSettingTheKeyWins() {
        Map<String, String> requested = map(CONFIG_KEY, "other-type");
        assertSame(requested, packageTypeConfigForUpdate(RepoType.Generic, requested, RepoType.Generic,
                map(CONFIG_KEY, "simple")));
    }

    @Test
    void updateWithBlankOrNullValueClearsTheKey() {
        Map<String, String> stored = map(CONFIG_KEY, "simple");
        Map<String, String> blank = packageTypeConfigForUpdate(RepoType.Generic, map(CONFIG_KEY, " ", "o", "v"),
                RepoType.Generic, stored);
        assertEquals(map("o", "v"), blank);
        Map<String, String> nullValue = map("o", "v");
        nullValue.put(CONFIG_KEY, null);
        assertFalse(packageTypeConfigForUpdate(RepoType.Generic, nullValue, RepoType.Generic, stored)
                .containsKey(CONFIG_KEY));
    }

    @Test
    void nothingToCarryOverLeavesRequestAsIs() {
        Map<String, String> requested = map("o", "v");
        assertSame(requested, packageTypeConfigForUpdate(RepoType.Generic, requested, RepoType.Generic, map()));
        assertSame(requested, packageTypeConfigForUpdate(RepoType.Generic, requested, RepoType.Generic, null));
        assertSame(requested, packageTypeConfigForUpdate(RepoType.Generic, requested, RepoType.Generic,
                map(CONFIG_KEY, "")));
    }

    @Test
    void nonGenericRepositoriesAreUntouched() {
        Map<String, String> requested = map(CONFIG_KEY, "");
        assertSame(requested, packageTypeConfigForUpdate(RepoType.Npm, requested, RepoType.Generic,
                map(CONFIG_KEY, "simple")));
        Map<String, String> fromNonGeneric = map();
        assertSame(fromNonGeneric, packageTypeConfigForUpdate(RepoType.Generic, fromNonGeneric, RepoType.Npm,
                map(CONFIG_KEY, "simple")));
    }
}
