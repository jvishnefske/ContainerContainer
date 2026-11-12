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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The load-time rules a module must meet before it is ever run (W1, W3). Pure: it judges a {@link Shape}, the
 * runtime-independent description of a module's imports, exports and memories that {@link WasmModules} extracts.
 *
 * <ul>
 *     <li>Every import is a function of {@value #WASI_MODULE} that the host's WASI preview 1 implements; any other
 *         import (another module, or a memory, table, global or tag) fails the load, so a module can reach nothing
 *         but WASI.</li>
 *     <li>The module exports a function {@code _start} taking and returning nothing (a WASI command) and a memory
 *         named {@code memory}.</li>
 *     <li>It defines exactly one memory, whose initial size fits the manifest's {@code limits.memoryPages}.</li>
 * </ul>
 */
public final class ModuleRules {

    /** The only import module a WASM plugin may use. */
    public static final String WASI_MODULE = "wasi_snapshot_preview1";

    private ModuleRules() {
    }

    /** Kinds of imports and exports, as in the WebAssembly specification. */
    public enum Kind {
        FUNCTION, TABLE, MEMORY, GLOBAL, TAG
    }

    /** One import: module name, field name and kind. */
    public record Import(String module, String name, Kind kind) {
        public Import {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * One export.
     *
     * @param nullary for functions, whether it takes no parameters and returns no results
     */
    public record Export(String name, Kind kind, boolean nullary) {
        public Export {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * What the rules look at.
     *
     * @param imports        imports in module order
     * @param exports        exports in module order
     * @param memoryMinPages initial size of each memory the module defines, in pages
     * @param memoryMaxPages declared maximum of the first defined memory, if it declares one
     */
    public record Shape(List<Import> imports, List<Export> exports, List<Integer> memoryMinPages,
            OptionalInt memoryMaxPages) {
        public Shape {
            imports = List.copyOf(imports);
            exports = List.copyOf(exports);
            memoryMinPages = List.copyOf(memoryMinPages);
            Objects.requireNonNull(memoryMaxPages, "memoryMaxPages");
        }
    }

    /**
     * @param wasiFunctions names of the WASI preview 1 functions the host implements
     * @param maxPages      the manifest's {@code limits.memoryPages}
     * @return every rule the module breaks, empty when it may load
     */
    public static List<String> check(Shape shape, Set<String> wasiFunctions, int maxPages) {
        List<String> errors = new ArrayList<>();
        for (Import imported : shape.imports()) {
            String what = imported.module() + "." + imported.name();
            if (!WASI_MODULE.equals(imported.module())) {
                errors.add("import " + what + " is not from " + WASI_MODULE);
            } else if (imported.kind() != Kind.FUNCTION) {
                errors.add("import " + what + " is a " + imported.kind().name().toLowerCase(Locale.ROOT)
                        + ", not a function");
            } else if (!wasiFunctions.contains(imported.name())) {
                errors.add("import " + what + " is not a WASI preview 1 function this host provides");
            }
        }
        boolean start = false;
        boolean memory = false;
        for (Export export : shape.exports()) {
            if ("_start".equals(export.name()) && export.kind() == Kind.FUNCTION && export.nullary()) {
                start = true;
            }
            if ("memory".equals(export.name()) && export.kind() == Kind.MEMORY) {
                memory = true;
            }
        }
        if (!start) {
            errors.add("module does not export a function _start without parameters and results");
        }
        if (!memory) {
            errors.add("module does not export its memory as 'memory'");
        }
        if (shape.memoryMinPages().size() != 1) {
            errors.add("module must define exactly one memory, not " + shape.memoryMinPages().size());
        } else if (shape.memoryMinPages().get(0) > maxPages) {
            errors.add("module memory starts at " + shape.memoryMinPages().get(0)
                    + " pages, more than limits.memoryPages " + maxPages);
        }
        return List.copyOf(errors);
    }

    /**
     * The memory size the host gives each instance: the module's initial size, growing at most to the smaller of
     * its declared maximum and {@code maxPages}. Pure.
     *
     */
    public static MemoryPages memoryLimits(Shape shape, int maxPages) {
        int initial = shape.memoryMinPages().isEmpty() ? 0 : shape.memoryMinPages().get(0);
        int maximum = Math.min(maxPages, shape.memoryMaxPages().orElse(maxPages));
        return new MemoryPages(initial, Math.max(initial, maximum));
    }

    /** Initial and maximum size of an instance's memory, in 64 KiB pages. */
    public record MemoryPages(int initial, int maximum) {
    }
}
