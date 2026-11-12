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

import run.endive.log.Logger;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.TrapException;
import run.endive.runtime.WasmInterruptedException;
import run.endive.wasi.WasiExitException;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmEngineException;
import run.endive.wasm.WasmModule;
import run.endive.wasm.types.Export;
import run.endive.wasm.types.ExportSection;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.FunctionImport;
import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.Import;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.MemorySection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The imperative edge to Endive (W3, W4): parsing and describing modules, and running one step in a fresh instance.
 * Only Endive's interpreter is used; the optional compiler is not on the class path.
 *
 * <p>Each step builds a new WASI preview 1 host and a new {@link Instance} of the parsed module: stdin is the
 * envelope, stdout is captured up to the cap (one byte more marks an overflow, the rest is discarded), stderr is
 * captured up to {@value #MAX_STDERR_BYTES} bytes and handed to the plugin log. WASI gets no arguments, no
 * environment, no preopened directories, the UTC wall clock and {@link SecureRandom} as its random source. Memory
 * is created with the limits {@link ModuleRules#memoryLimits} computed, so {@code memory.grow} past the manifest's
 * cap fails inside the module. {@code _start} is called explicitly (never during instantiation) and its outcome
 * classified: a return or {@code proc_exit(0)} is {@link StepOutput.Exited}; a non-zero exit, a trap, an interrupt
 * (the deadline), a stack overflow or an out-of-memory error is a {@link StepOutput.Failed}.
 */
public final class EndiveRuntime {

    /** Most stderr bytes kept per step. */
    public static final int MAX_STDERR_BYTES = 16 * 1024;
    /** Largest module file accepted. */
    public static final int MAX_MODULE_BYTES = 64 * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger QUIET = new Logger() {
        @Override
        public void log(Level level, String message, Throwable throwable) {
            // Endive's WASI tracing is not wanted in the server log; module stderr goes to the plugin log instead.
        }

        @Override
        public boolean isLoggable(Level level) {
            return false;
        }
    };

    private EndiveRuntime() {
    }

    /** A parsed module and its shape, or why it could not be parsed. */
    public sealed interface Parsed {
    }

    /** The module parsed and validated as WebAssembly. */
    public record Module(WasmModule module, ModuleRules.Shape shape) implements Parsed {
        public Module {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(shape, "shape");
        }
    }

    /** The bytes are not a valid module. */
    public record Invalid(String reason) implements Parsed {
        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** @return the names of the WASI preview 1 functions Endive implements */
    public static Set<String> wasiFunctions() {
        Set<String> names = new TreeSet<>();
        try (WasiPreview1 wasi = WasiPreview1.builder().withLogger(QUIET)
                .withOptions(WasiOptions.builder().build()).build()) {
            for (HostFunction function : wasi.toHostFunctions()) {
                if (ModuleRules.WASI_MODULE.equals(function.module())) {
                    names.add(function.name());
                }
            }
        }
        return Set.copyOf(names);
    }

    /** Parses and validates {@code bytes}. Never throws. */
    public static Parsed parse(byte[] bytes) {
        if (bytes.length > MAX_MODULE_BYTES) {
            return new Invalid("module is larger than " + MAX_MODULE_BYTES + " bytes");
        }
        try {
            WasmModule module = Parser.parse(bytes);
            return new Module(module, shape(module));
        } catch (WasmEngineException | IllegalArgumentException | IllegalStateException
                | IndexOutOfBoundsException e) {
            return new Invalid("module is not valid WebAssembly: " + e.getMessage());
        } catch (StackOverflowError e) {
            return new Invalid("module is not valid WebAssembly: nesting too deep");
        }
    }

    private static ModuleRules.Shape shape(WasmModule module) {
        List<ModuleRules.Import> imports = new ArrayList<>();
        List<Integer> functionTypes = new ArrayList<>();
        for (int i = 0; i < module.importSection().importCount(); i++) {
            Import imported = module.importSection().getImport(i);
            imports.add(new ModuleRules.Import(imported.module(), imported.name(), kind(imported.importType())));
            if (imported instanceof FunctionImport function) {
                functionTypes.add(function.typeIndex());
            }
        }
        for (int i = 0; i < module.functionSection().functionCount(); i++) {
            functionTypes.add(module.functionSection().getFunctionType(i));
        }
        List<ModuleRules.Export> exports = new ArrayList<>();
        ExportSection exportSection = module.exportSection();
        for (int i = 0; i < exportSection.exportCount(); i++) {
            Export export = exportSection.getExport(i);
            ModuleRules.Kind kind = kind(export.exportType());
            boolean nullary = false;
            if (kind == ModuleRules.Kind.FUNCTION && export.index() >= 0 && export.index() < functionTypes.size()) {
                FunctionType type = module.typeSection().getType(functionTypes.get(export.index()));
                nullary = type != null && type.params().isEmpty() && type.returns().isEmpty();
            }
            exports.add(new ModuleRules.Export(export.name(), kind, nullary));
        }
        List<Integer> memories = new ArrayList<>();
        OptionalInt max = OptionalInt.empty();
        if (module.memorySection().isPresent()) {
            MemorySection section = module.memorySection().get();
            for (int i = 0; i < section.memoryCount(); i++) {
                MemoryLimits limits = section.getMemory(i).limits();
                memories.add(limits.initialPages());
                if (i == 0 && limits.maximumPages() < MemoryLimits.MAX_PAGES) {
                    max = OptionalInt.of(limits.maximumPages());
                }
            }
        }
        return new ModuleRules.Shape(imports, exports, memories, max);
    }

    private static ModuleRules.Kind kind(ExternalType type) {
        return switch (type) {
            case FUNCTION -> ModuleRules.Kind.FUNCTION;
            case TABLE -> ModuleRules.Kind.TABLE;
            case MEMORY -> ModuleRules.Kind.MEMORY;
            case GLOBAL -> ModuleRules.Kind.GLOBAL;
            case TAG -> ModuleRules.Kind.TAG;
        };
    }

    /**
     * Instantiates {@code module} without running {@code _start}, to check at load time that it links against the
     * host's WASI and fits its memory limits. Runs on the calling thread; call it through {@link WasmWorkers}.
     */
    public static StepOutput instantiate(WasmModule module, ModuleRules.MemoryPages memory) {
        try (WasiPreview1 wasi = wasi(new byte[0], OutputStream.nullOutputStream(), OutputStream.nullOutputStream())) {
            instance(module, memory, wasi);
            return new StepOutput.Exited(new byte[0]);
        } catch (WasmInterruptedException e) {
            return new StepOutput.Failed(StepOutput.Failure.TIMEOUT, "instantiation was interrupted");
        } catch (WasmEngineException | IllegalArgumentException | IllegalStateException
                | IndexOutOfBoundsException e) {
            return new StepOutput.Failed(StepOutput.Failure.INSTANTIATION, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Runs one step in a fresh instance on the calling thread; call it through {@link WasmWorkers} so the deadline
     * applies. Never throws.
     *
     * @param stderr receives what the module wrote to stderr, possibly truncated, after the step
     */
    public static StepOutput step(WasmModule module, ModuleRules.MemoryPages memory, byte[] stdin,
            int maxStdoutBytes, Consumer<String> stderr) {
        CappedOutput out = new CappedOutput(maxStdoutBytes);
        CappedOutput err = new CappedOutput(MAX_STDERR_BYTES);
        try (WasiPreview1 wasi = wasi(stdin, out, err)) {
            Instance instance = instance(module, memory, wasi);
            try {
                instance.export(Instance.START_FUNCTION_NAME).apply();
            } catch (WasiExitException e) {
                if (e.exitCode() != 0) {
                    return new StepOutput.Failed(StepOutput.Failure.EXIT_CODE, "exit code " + e.exitCode());
                }
            }
            if (out.overflowed()) {
                return new StepOutput.Failed(StepOutput.Failure.STDOUT_OVERFLOW,
                        "more than " + maxStdoutBytes + " bytes of output");
            }
            return new StepOutput.Exited(out.bytes());
        } catch (WasmInterruptedException e) {
            return new StepOutput.Failed(StepOutput.Failure.TIMEOUT, "interrupted at the deadline");
        } catch (TrapException e) {
            return new StepOutput.Failed(StepOutput.Failure.TRAP, String.valueOf(e.getMessage()));
        } catch (WasmEngineException | IllegalArgumentException | IllegalStateException
                | IndexOutOfBoundsException | ArithmeticException e) {
            return new StepOutput.Failed(StepOutput.Failure.TRAP, e.getClass().getSimpleName() + ": "
                    + e.getMessage());
        } catch (StackOverflowError e) {
            return new StepOutput.Failed(StepOutput.Failure.TRAP, "call stack exhausted");
        } catch (OutOfMemoryError e) {
            return new StepOutput.Failed(StepOutput.Failure.OUT_OF_MEMORY, "the host ran out of memory");
        } finally {
            if (err.size() > 0) {
                stderr.accept(new String(err.bytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private static WasiPreview1 wasi(byte[] stdin, OutputStream stdout, OutputStream stderr) {
        WasiOptions options = WasiOptions.builder()
                .withStdin(new ByteArrayInputStream(stdin))
                .withStdout(stdout)
                .withStderr(stderr)
                .withClock(Clock.systemUTC())
                .withRandom(RANDOM)
                .withArguments(List.of())
                .build();
        return WasiPreview1.builder().withLogger(QUIET).withOptions(options).build();
    }

    private static Instance instance(WasmModule module, ModuleRules.MemoryPages memory, WasiPreview1 wasi) {
        return Instance.builder(module)
                .withImportValues(ImportValues.builder().addFunction(wasi.toHostFunctions()).build())
                .withMemoryLimits(new MemoryLimits(memory.initial(), memory.maximum()))
                .withStart(false)
                .build();
    }

    /** An output stream that keeps at most {@code cap} bytes and remembers whether more were written. */
    static final class CappedOutput extends OutputStream {
        private final int cap;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean overflowed;

        CappedOutput(int cap) {
            this.cap = cap;
        }

        @Override
        public void write(int b) {
            if (bytes.size() < cap) {
                bytes.write(b);
            } else {
                overflowed = true;
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            int room = Math.max(0, cap - bytes.size());
            bytes.write(buffer, offset, Math.min(room, length));
            if (length > room) {
                overflowed = true;
            }
        }

        boolean overflowed() {
            return overflowed;
        }

        int size() {
            return bytes.size();
        }

        byte[] bytes() {
            return bytes.toByteArray();
        }
    }
}
