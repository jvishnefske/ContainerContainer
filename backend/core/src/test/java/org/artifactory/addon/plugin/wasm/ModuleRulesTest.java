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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W1, W3: import, export and memory validation of modules at load time. */
class ModuleRulesTest {

    private static final Set<String> WASI = EndiveRuntime.wasiFunctions();

    private static List<String> check(byte[] wasm, int maxPages) {
        EndiveRuntime.Module module = assertInstanceOf(EndiveRuntime.Module.class, EndiveRuntime.parse(wasm));
        return ModuleRules.check(module.shape(), WASI, maxPages);
    }

    private static void rejected(byte[] wasm, String expected) {
        List<String> errors = check(wasm, 256);
        assertTrue(errors.stream().anyMatch(e -> e.contains(expected)), errors + " should mention " + expected);
    }

    @Test
    void wasiCommandsWithMemoryPass() {
        assertEquals(List.of(), check(TinyWasm.exit(0), 256));
        assertEquals(List.of(), check(TinyWasm.writes("x"), 256));
    }

    @Test
    void theExampleModuleImportsOnlyWasiAndExportsStartAndMemory() {
        EndiveRuntime.Module module = assertInstanceOf(EndiveRuntime.Module.class,
                EndiveRuntime.parse(EchoRealmFixture.bytes()));
        assertEquals(List.of(), ModuleRules.check(module.shape(), WASI, 256));
        assertTrue(module.shape().imports().stream().allMatch(i -> i.module().equals(ModuleRules.WASI_MODULE)));
    }

    @Test
    void importsOutsideWasiPreview1FailTheLoad() {
        rejected(new TinyWasm().importFunction("env", "host_secret", TinyWasm.VOID).build(), "is not from");
        rejected(new TinyWasm().importFunction(ModuleRules.WASI_MODULE, "sock_magic", TinyWasm.VOID).build(),
                "not a WASI preview 1 function");
        rejected(new TinyWasm().importGlobal(ModuleRules.WASI_MODULE, "g").build(), "is a global, not a function");
    }

    @Test
    void startAndMemoryExportsAreRequired() {
        rejected(new TinyWasm().withoutStartExport().build(), "_start");
        rejected(new TinyWasm().withoutMemoryExport().build(), "'memory'");
    }

    @Test
    void initialMemoryMustFitTheLimitAndGrowthIsCapped() {
        rejected(new TinyWasm().memory(300, null).build(), "more than limits.memoryPages");
        ModuleRules.Shape shape = new ModuleRules.Shape(List.of(), List.of(), List.of(2), OptionalInt.empty());
        assertEquals(new ModuleRules.MemoryPages(2, 64), ModuleRules.memoryLimits(shape, 64));
        ModuleRules.Shape declared = new ModuleRules.Shape(List.of(), List.of(), List.of(2), OptionalInt.of(10));
        assertEquals(new ModuleRules.MemoryPages(2, 10), ModuleRules.memoryLimits(declared, 64));
        ModuleRules.Shape none = new ModuleRules.Shape(List.of(), List.of(), List.of(), OptionalInt.empty());
        assertTrue(ModuleRules.check(none, WASI, 64).stream().anyMatch(e -> e.contains("exactly one memory")));
    }
}
