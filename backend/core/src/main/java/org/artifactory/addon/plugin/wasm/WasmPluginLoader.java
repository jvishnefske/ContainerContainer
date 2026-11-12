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
import org.artifactory.addon.plugin.groovy.PluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Loads WASM plugins from the plugins directory (W1): every regular file {@code <name>.wasm} directly in it, with
 * its manifest {@code <name>.wasm.yaml}. The {@link org.artifactory.addon.plugin.groovy.GroovyPluginEngine} calls
 * it at startup and on every reload, next to the Groovy compiler, so WASM plugins share plugin status, listing,
 * reload and name uniqueness with Groovy plugins.
 *
 * <p>A plugin loads only when all of these hold, and otherwise is a {@link PluginLoadResult.Failed} that registers
 * nothing: the manifest exists and is valid ({@link ManifestParser}, which also checks {@code abi: 1}); the module
 * is at most {@link EndiveRuntime#MAX_MODULE_BYTES} and valid WebAssembly; it meets {@link ModuleRules} (WASI-only
 * imports, {@code _start} and {@code memory} exports, memory within {@code limits.memoryPages}); and a trial
 * instantiation, run on the workers with the step deadline, succeeds. A manifest without a module is reported as
 * failed too.
 */
public final class WasmPluginLoader implements PluginLoader {

    /** Extension of WASM modules. */
    public static final String MODULE_EXTENSION = ".wasm";
    /** Suffix of a module's manifest, appended to the module file name. */
    public static final String MANIFEST_SUFFIX = ".yaml";

    private static final Logger log = LoggerFactory.getLogger(WasmPluginLoader.class);
    private static final String PLUGIN_LOGGER_PREFIX = "org.artifactory.addon.plugin.wasm.user.";
    private static final Set<String> WASI_FUNCTIONS = EndiveRuntime.wasiFunctions();

    private final WasmWorkers workers;
    private final BiFunction<String, PluginLimits, StepLoop.Performer> performers;
    private final WasmPlugin.RequestContext context;
    private final LongSupplier nowSeconds;

    /**
     * @param workers    runs every step and trial instantiation
     * @param performers builds the effect performer of a plugin, by plugin name and limits
     * @param context    the current request, for executions
     * @param nowSeconds the clock, epoch seconds
     */
    public WasmPluginLoader(WasmWorkers workers, BiFunction<String, PluginLimits, StepLoop.Performer> performers,
            WasmPlugin.RequestContext context, LongSupplier nowSeconds) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.performers = Objects.requireNonNull(performers, "performers");
        this.context = Objects.requireNonNull(context, "context");
        this.nowSeconds = Objects.requireNonNull(nowSeconds, "nowSeconds");
    }

    @Override
    public List<PluginLoadResult> load(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> modules = new ArrayList<>();
        List<Path> manifests = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (fileName.endsWith(MODULE_EXTENSION)) {
                    modules.add(file);
                } else if (fileName.endsWith(MODULE_EXTENSION + MANIFEST_SUFFIX)) {
                    manifests.add(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            return List.of(new PluginLoadResult.Failed("*" + MODULE_EXTENSION, dir.toString(),
                    "Cannot list plugins directory for WASM plugins: " + e.getMessage()));
        }
        modules.sort(Comparator.comparing(file -> file.getFileName().toString()));
        List<PluginLoadResult> results = new ArrayList<>();
        for (Path module : modules) {
            results.add(load(module, dir.resolve(module.getFileName() + MANIFEST_SUFFIX)));
        }
        for (Path manifest : manifests) {
            String fileName = manifest.getFileName().toString();
            String moduleName = fileName.substring(0, fileName.length() - MANIFEST_SUFFIX.length());
            if (!Files.isRegularFile(dir.resolve(moduleName))) {
                results.add(new PluginLoadResult.Failed(nameOf(moduleName), fileName,
                        "WASM manifest without its module " + moduleName));
            }
        }
        return results;
    }

    /** @return the plugin name of module file {@code fileName}, without {@code .wasm} */
    public static String nameOf(String fileName) {
        return fileName.endsWith(MODULE_EXTENSION)
                ? fileName.substring(0, fileName.length() - MODULE_EXTENSION.length()) : fileName;
    }

    private PluginLoadResult load(Path module, Path manifestFile) {
        String fileName = module.getFileName().toString();
        String name = nameOf(fileName);
        if (!Files.isRegularFile(manifestFile)) {
            return new PluginLoadResult.Failed(name, fileName, "WASM plugin has no manifest "
                    + manifestFile.getFileName());
        }
        String text;
        byte[] bytes;
        try {
            if (Files.size(manifestFile) > 4L * ManifestParser.MAX_MANIFEST_CHARS) {
                return new PluginLoadResult.Failed(name, fileName, "WASM manifest is too large");
            }
            text = Files.readString(manifestFile, StandardCharsets.UTF_8);
            if (Files.size(module) > EndiveRuntime.MAX_MODULE_BYTES) {
                return new PluginLoadResult.Failed(name, fileName, "WASM module is larger than "
                        + EndiveRuntime.MAX_MODULE_BYTES + " bytes");
            }
            bytes = Files.readAllBytes(module);
        } catch (IOException | RuntimeException e) {
            return new PluginLoadResult.Failed(name, fileName, "Cannot read WASM plugin: " + e.getMessage());
        }
        return load(name, fileName, text, bytes);
    }

    /** Loads a plugin from its manifest text and module bytes. Never throws. */
    PluginLoadResult load(String name, String fileName, String manifestText, byte[] moduleBytes) {
        ManifestParser.Result parsedManifest = ManifestParser.parse(manifestText);
        if (parsedManifest instanceof ManifestParser.Invalid invalid) {
            return new PluginLoadResult.Failed(name, fileName, "Invalid WASM manifest: " + invalid.reason());
        }
        WasmManifest manifest = ((ManifestParser.Valid) parsedManifest).manifest();
        EndiveRuntime.Parsed parsed = EndiveRuntime.parse(moduleBytes);
        if (parsed instanceof EndiveRuntime.Invalid invalid) {
            return new PluginLoadResult.Failed(name, fileName, invalid.reason());
        }
        EndiveRuntime.Module module = (EndiveRuntime.Module) parsed;
        List<String> errors = ModuleRules.check(module.shape(), WASI_FUNCTIONS, manifest.limits().memoryPages());
        if (!errors.isEmpty()) {
            return new PluginLoadResult.Failed(name, fileName, "WASM module rejected: " + String.join("; ", errors));
        }
        ModuleRules.MemoryPages memory = ModuleRules.memoryLimits(module.shape(), manifest.limits().memoryPages());
        StepOutput trial = workers.run(() -> EndiveRuntime.instantiate(module.module(), memory),
                manifest.limits().stepTimeoutMs());
        if (trial instanceof StepOutput.Failed failed) {
            return new PluginLoadResult.Failed(name, fileName, "WASM module cannot be instantiated: "
                    + failed.detail());
        }
        Logger pluginLog = LoggerFactory.getLogger(PLUGIN_LOGGER_PREFIX + name);
        PluginLimits limits = manifest.limits();
        StepLoop.Runner runner = stdin -> workers.run(() -> EndiveRuntime.step(module.module(), memory, stdin,
                limits.maxStdoutBytes(), stderr -> stderr.lines().forEach(line -> pluginLog.info("{}", line))),
                limits.stepTimeoutMs());
        WasmPlugin plugin = new WasmPlugin(name, manifest, runner, performers.apply(name, limits), context,
                nowSeconds);
        log.debug("Loaded WASM plugin '{}' ({} realms, {} executions)", name, manifest.realms().size(),
                manifest.executions().size());
        return new PluginLoadResult.Loaded(plugin.toLoadedPlugin(fileName));
    }
}
