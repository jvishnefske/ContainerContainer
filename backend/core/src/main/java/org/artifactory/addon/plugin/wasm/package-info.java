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

/**
 * The WASM plugin runtime of the OSS fork (design.md W1-W7): WebAssembly modules that implement plugin realms and
 * executions over ABI v1 ({@code plugins/wasm-abi/abi-v1.schema.json}), run on Endive's pure-Java interpreter.
 *
 * <p>Functional core, all pure: {@link org.artifactory.addon.plugin.wasm.ManifestParser} (the
 * {@code <name>.wasm.yaml} schema, defaults and bounds of {@link org.artifactory.addon.plugin.wasm.PluginLimits}),
 * {@link org.artifactory.addon.plugin.wasm.ModuleRules} (WASI-only imports, {@code _start} and {@code memory}
 * exports, memory cap), {@link org.artifactory.addon.plugin.wasm.AbiCodec} (envelopes and strict reply decoding),
 * {@link org.artifactory.addon.plugin.wasm.EffectPolicy} (which effects the host performs) and
 * {@link org.artifactory.addon.plugin.wasm.StepRules} (what follows each step). Values are immutable records and
 * sealed result types; nothing in the core throws across the plugin boundary.
 *
 * <p>Imperative shell: {@link org.artifactory.addon.plugin.wasm.StepLoop} drives a call through a
 * {@link org.artifactory.addon.plugin.wasm.StepLoop.Runner} (production: a fresh Endive instance per step on the
 * bounded {@link org.artifactory.addon.plugin.wasm.WasmWorkers} pool, see
 * {@link org.artifactory.addon.plugin.wasm.EndiveRuntime}) and a
 * {@link org.artifactory.addon.plugin.wasm.StepLoop.Performer} (production:
 * {@link org.artifactory.addon.plugin.wasm.HostEffects}, the JDK HTTP client and {@code PluginSecurity}).
 * {@link org.artifactory.addon.plugin.wasm.WasmPluginLoader} loads modules beside Groovy scripts, and
 * {@link org.artifactory.addon.plugin.wasm.WasmPlugin} contributes their realms and executions to the Groovy
 * engine's snapshot as hosted hooks, so the existing Java enforcement (P1-P6, G4) applies to them unchanged.
 *
 * <p>The module is untrusted: it can reach only WASI preview 1 (no files, environment or arguments) and the effects
 * the manifest allows, within its limits; every failure fails closed.
 */
package org.artifactory.addon.plugin.wasm;
