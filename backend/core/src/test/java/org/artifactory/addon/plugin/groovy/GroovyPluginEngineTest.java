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

import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.storage.AfterCreateAction;
import org.artifactory.addon.plugin.storage.BeforeCreateAction;
import org.artifactory.addon.plugin.storage.BeforeDeleteAction;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Loading (G1), reloading (G2) and the hook error policy (G7) of {@link GroovyPluginEngine}. */
class GroovyPluginEngineTest {

    @TempDir
    Path dir;

    private PluginTestRig rig;
    private ItemInfo item;

    @BeforeEach
    void setUp() {
        rig = new PluginTestRig(dir);
        item = mock(ItemInfo.class);
        when(item.getRepoPath()).thenReturn(RepoPathFactory.create("libs-local", "a/b.jar"));
    }

    @Test
    void badScriptsAreReportedAndDoNotBlockOtherPlugins() throws Exception {
        rig.write("a-syntax.groovy", "storage { afterCreate { item -> ")
                .write("b-runtime.groovy", "throw new IllegalStateException('boom at load')")
                .write("c-unknown-hook.groovy", "storage { afterCreat { item -> } }")
                .write("d-good.groovy", "storage { afterCreate { item -> calls << 'good' } }")
                .write("notes.txt", "not a plugin");

        PluginSnapshot snapshot = rig.reload();

        Map<String, String> statuses = rig.addon.getPluginsStatus();
        assertEquals(Map.of(
                "a-syntax.groovy", PluginsAddon.PLUGIN_STATUS_ERROR,
                "b-runtime.groovy", PluginsAddon.PLUGIN_STATUS_ERROR,
                "c-unknown-hook.groovy", PluginsAddon.PLUGIN_STATUS_ERROR,
                "d-good.groovy", PluginsAddon.PLUGIN_STATUS_LOADED), statuses);
        PluginLoadResult.Failed runtime = assertInstanceOf(PluginLoadResult.Failed.class, snapshot.results().get(1));
        assertTrue(runtime.error().contains("boom at load"), runtime.error());
        PluginLoadResult.Failed unknown = assertInstanceOf(PluginLoadResult.Failed.class, snapshot.results().get(2));
        assertTrue(unknown.error().contains("afterCreat"), unknown.error());

        rig.addon.execPluginActions(AfterCreateAction.class, null, item);
        assertEquals(List.of("good"), rig.calls);
    }

    @Test
    void missingPluginsDirectoryLoadsNothing() {
        PluginTestRig noDir = new PluginTestRig(dir.resolve("does-not-exist"));
        PluginSnapshot snapshot = assertDoesNotThrow(noDir::reload);
        assertTrue(snapshot.results().isEmpty());
        assertFalse(noDir.addon.isPluginSupported(AfterCreateAction.class));
    }

    @Test
    void startupLoadsPluginsThroughContextReadiness() throws Exception {
        rig.write("hello.groovy", "storage { afterCreate { item -> calls << 'loaded' } }");
        rig.addon.onContextReady();
        assertTrue(rig.addon.isPluginSupported(AfterCreateAction.class));
    }

    @Test
    void reloadReplacesHooksAndPackageTypes() throws Exception {
        rig.write("p.groovy", """
                storage { afterCreate { item -> calls << 'v1' } }
                packageType('alpha') { displayName 'Alpha' }
                """);
        rig.reload();
        assertTrue(rig.registry.find("alpha").isPresent());
        assertTrue(rig.addon.isPluginSupported(AfterCreateAction.class));

        rig.write("p.groovy", """
                storage { beforeDelete { item -> calls << 'v2' } }
                packageType('beta') { displayName 'Beta' }
                """);
        String message = rig.addon.reloadPlugins(true, false).getMessage();

        assertTrue(message.contains("p.groovy: Loaded"), message);
        assertFalse(rig.addon.isPluginSupported(AfterCreateAction.class));
        assertTrue(rig.addon.isPluginSupported(BeforeDeleteAction.class));
        assertTrue(rig.registry.find("alpha").isEmpty());
        assertEquals("Beta", rig.registry.find("beta").orElseThrow().displayName());
        rig.addon.execPluginActions(AfterCreateAction.class, null, item);
        rig.addon.execPluginActions(BeforeDeleteAction.class, null, item);
        assertEquals(List.of("v2"), rig.calls);

        rig.delete("p.groovy");
        rig.reload();
        assertFalse(rig.addon.isPluginSupported(BeforeDeleteAction.class));
        assertTrue(rig.registry.all().isEmpty());
        assertTrue(rig.registry.removed.contains("p"));
    }

    @Test
    void pluginThatStopsCompilingLosesItsPackageTypes() throws Exception {
        rig.write("p.groovy", "packageType('alpha') { }");
        rig.reload();
        rig.write("p.groovy", "packageType('alpha') { ");
        rig.reload();
        assertTrue(rig.registry.find("alpha").isEmpty());
        assertEquals(PluginsAddon.PLUGIN_STATUS_ERROR, rig.addon.getPluginsStatus().get("p.groovy"));
    }

    @Test
    void rejectedPackageTypesFailThePluginWithTheRegistryReason() throws Exception {
        rig.write("bad-type.groovy", """
                storage { afterCreate { item -> calls << 'should not fire' } }
                packageType('Maven') { }
                """);
        PluginSnapshot snapshot = rig.reload();

        PluginLoadResult.Failed failed = assertInstanceOf(PluginLoadResult.Failed.class, snapshot.results().get(0));
        assertTrue(failed.error().startsWith("Package types rejected: "), failed.error());
        assertTrue(failed.error().contains("Maven"), failed.error());
        assertFalse(rig.addon.isPluginSupported(AfterCreateAction.class));
    }

    @Test
    void packageTypeMovedBetweenPluginsInOneReloadIsAccepted() throws Exception {
        rig.write("b-owner.groovy", "packageType('shared') { }");
        rig.reload();
        rig.write("a-new-owner.groovy", "packageType('shared') { }")
                .write("b-owner.groovy", "// gave the type away");
        PluginSnapshot snapshot = rig.reload();

        assertTrue(snapshot.results().stream().allMatch(PluginLoadResult.Loaded.class::isInstance));
        assertEquals("a-new-owner", rig.registry.find("shared").orElseThrow().pluginName());
    }

    @Test
    void duplicateExecutionNamesFailTheLaterPlugin() throws Exception {
        rig.write("a.groovy", "executions { hello { params -> } }")
                .write("b.groovy", "executions { hello { params -> } }");
        PluginSnapshot snapshot = rig.reload();
        PluginLoadResult.Failed failed = assertInstanceOf(PluginLoadResult.Failed.class, snapshot.results().get(1));
        assertTrue(failed.error().contains("already declared by plugin 'a'"), failed.error());
    }

    @Test
    void hookExceptionIsReportedWithPluginNameAndDoesNotFailTheCall() throws Exception {
        rig.write("a-throws.groovy", "storage { afterCreate { item -> throw new RuntimeException('hook broke') } }")
                .write("b-fine.groovy", "storage { afterCreate { item -> calls << item.repoPath.path } }");
        rig.reload();

        assertDoesNotThrow(() -> rig.addon.execPluginActions(AfterCreateAction.class, null, item));

        assertEquals(List.of("a/b.jar"), rig.calls);
        assertEquals(1, rig.reports.size());
        PluginTestRig.Report report = rig.reports.get(0);
        assertEquals("a-throws", report.pluginName());
        assertEquals("storage.afterCreate", report.what());
        assertTrue(HookInvoker.describe(report.error()).contains("hook broke"));
    }

    @Test
    void cancelExceptionInBeforeCreateFailsTheOperation() throws Exception {
        rig.write("guard.groovy", """
                storage { beforeCreate { item -> throw new CancelException('no jars here', 403) } }
                """)
                .write("later.groovy", "storage { beforeCreate { item -> calls << 'later' } }");
        rig.reload();

        CancelException cancel = assertThrows(CancelException.class,
                () -> rig.addon.execPluginActions(BeforeCreateAction.class, null, item));

        assertEquals(403, cancel.getErrorCode());
        assertEquals("no jars here", cancel.getMessage());
        assertTrue(rig.calls.isEmpty(), "hooks after a cancel do not run");
        assertTrue(rig.reports.isEmpty(), "a cancel is not a swallowed failure");
    }
}
