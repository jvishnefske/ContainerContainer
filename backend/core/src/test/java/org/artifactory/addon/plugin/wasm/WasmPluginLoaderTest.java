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

package org.artifactory.addon.plugin.wasm;

import org.artifactory.addon.plugin.groovy.PluginLoadResult;
import org.artifactory.addon.plugin.groovy.PluginSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W1: what loads, and that a plugin that fails to load registers nothing. */
class WasmPluginLoaderTest {

    private static final String MANIFEST = "abi: 1\nrealms: [{name: r}]\nexecutions: [{name: e}]\n";

    @TempDir
    Path dir;

    private final WasmPluginLoader loader = new WasmPluginLoader(new WasmWorkers(2, 4),
            (name, limits) -> effect -> EffectResult.HttpGet.failed("unused"), WasmPlugin.RequestContext.NONE,
            () -> 1);

    private List<PluginLoadResult> load(String name, byte[] module, String manifest) throws Exception {
        if (module != null) {
            Files.write(dir.resolve(name + ".wasm"), module);
        }
        if (manifest != null) {
            Files.writeString(dir.resolve(name + ".wasm.yaml"), manifest);
        }
        return loader.load(dir);
    }

    private String failed(String name, byte[] module, String manifest) throws Exception {
        List<PluginLoadResult> results = load(name, module, manifest);
        PluginSnapshot snapshot = new PluginSnapshot(results);
        assertEquals(List.of(), snapshot.realms(), "a failed plugin registers no realm");
        assertEquals(List.of(), snapshot.executions(), "a failed plugin registers no execution");
        assertEquals(1, results.size());
        return assertInstanceOf(PluginLoadResult.Failed.class, results.get(0)).error();
    }

    @Test
    void aValidModuleWithManifestLoadsItsRealmsAndExecutions() throws Exception {
        List<PluginLoadResult> results = load("ok", TinyWasm.exit(0), MANIFEST);
        PluginLoadResult.Loaded loaded = assertInstanceOf(PluginLoadResult.Loaded.class, results.get(0));
        assertEquals("ok", loaded.name());
        assertEquals("ok.wasm", loaded.fileName());
        assertEquals("r", loaded.plugin().realms().get(0).name());
        assertEquals("plugin:r", loaded.plugin().realms().get(0).userRealm());
        assertEquals("e", loaded.plugin().executions().get(0).name());
        assertEquals("POST", loaded.plugin().executions().get(0).httpMethod());
        assertTrue(loaded.plugin().executions().get(0).users().isEmpty(), "admin-only by default");
    }

    @Test
    void missingOrInvalidManifestsFailTheLoad() throws Exception {
        assertTrue(failed("a", TinyWasm.exit(0), null).contains("has no manifest"));
        Files.delete(dir.resolve("a.wasm"));
        assertTrue(failed("b", TinyWasm.exit(0), "abi: 2\nrealms: [{name: r}]").contains("abi must be 1"));
        Files.delete(dir.resolve("b.wasm"));
        Files.delete(dir.resolve("b.wasm.yaml"));
        assertTrue(failed("c", null, MANIFEST).contains("without its module"));
    }

    @Test
    void modulesThatBreakTheRulesFailTheLoad() throws Exception {
        Map<String, byte[]> modules = Map.of(
                "garbage", new byte[] {0, 1, 2, 3},
                "custom-import", new TinyWasm().importFunction("env", "f", TinyWasm.VOID).build(),
                "no-start", new TinyWasm().withoutStartExport().build(),
                "no-memory", new TinyWasm().withoutMemoryExport().build(),
                "huge-memory", new TinyWasm().memory(300, null).build(),
                "bad-signature", new TinyWasm().importFunction(ModuleRules.WASI_MODULE, "proc_exit",
                        TinyWasm.VOID).build());
        Map<String, String> expected = Map.of(
                "garbage", "not valid WebAssembly",
                "custom-import", "is not from wasi_snapshot_preview1",
                "no-start", "_start",
                "no-memory", "'memory'",
                "huge-memory", "memoryPages",
                "bad-signature", "cannot be instantiated");
        for (Map.Entry<String, byte[]> module : modules.entrySet()) {
            Path file = dir.resolve(module.getKey() + ".wasm");
            String reason = failed(module.getKey(), module.getValue(), MANIFEST);
            assertTrue(reason.contains(expected.get(module.getKey())), module.getKey() + " -> " + reason);
            Files.delete(file);
            Files.delete(dir.resolve(module.getKey() + ".wasm.yaml"));
        }
    }

    @Test
    void aMissingDirectoryLoadsNothing() {
        assertEquals(List.of(), loader.load(dir.resolve("absent")));
        assertEquals(List.of(), loader.load(null));
    }
}
