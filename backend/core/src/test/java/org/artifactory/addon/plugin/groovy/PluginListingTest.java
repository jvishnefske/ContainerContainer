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

import org.artifactory.addon.plugin.PluginInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G5: the addon lists loaded plugins with their hooks, executions and package types. */
class PluginListingTest {

    @TempDir
    Path dir;

    @Test
    void listsPluginsHooksExecutionsAndFailures() throws Exception {
        PluginTestRig rig = new PluginTestRig(dir);
        rig.write("audit.groovy", """
                download { altResponse { r, p -> } }
                storage { afterCreate { item -> } ; beforeDelete { item -> } }
                executions { cleanup(users: ['bob'], description: 'removes junk') { params -> } }
                packageType('audit-type') { }
                """)
                .write("broken.groovy", "download {");
        rig.reload();

        Map<String, Map<String, String>> plugins = rig.addon.getPluginsInfoSupportBundleDumpAsMap();
        assertEquals(List.of("audit", "broken"), List.copyOf(plugins.keySet()));
        Map<String, String> audit = plugins.get("audit");
        assertEquals("Loaded", audit.get("status"));
        assertEquals("audit.groovy", audit.get("fileName"));
        assertEquals("download.altResponse,storage.afterCreate,storage.beforeDelete", audit.get("hooks"));
        assertEquals("cleanup", audit.get("executions"));
        assertEquals("audit-type", audit.get("packageTypes"));
        assertEquals("Error", plugins.get("broken").get("status"));
        assertFalse(plugins.get("broken").get("error").isBlank());

        Map<String, List<PluginInfo>> info = rig.addon.getPluginInfo(null);
        PluginInfo cleanup = info.get("executions").get(0);
        assertEquals("cleanup", cleanup.getName());
        assertEquals("removes junk", cleanup.getDescription());
        assertTrue(cleanup.isUserPermitted("bob"));
        assertEquals(Map.of("staging", List.of()), rig.addon.getPluginInfo("staging"));
    }

    @Test
    void reloadMessageWithNoPlugins() {
        PluginTestRig rig = new PluginTestRig(dir);
        assertEquals("No plugins found in the plugins directory",
                rig.addon.reloadPlugins(true, false).getMessage());
        assertEquals(200, rig.addon.getInstalledPluginsStatus().getStatus());
    }
}
