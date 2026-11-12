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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture: assembles minimal WebAssembly modules in the binary format, so trap, loop, exit, output and import
 * cases need no committed binaries. Every module has three types ({@code ()->()}, {@code (i32)->()} for
 * {@code proc_exit} and {@code (i32,i32,i32,i32)->i32} for {@code fd_write}), the imports added, one function
 * {@code _start} of type {@code ()->()} with the given body, one memory and optional data at address 16.
 */
final class TinyWasm {

    /** Type index of {@code ()->()}. */
    static final int VOID = 0;
    /** Type index of {@code (i32)->()}. */
    static final int I32_TO_VOID = 1;
    /** Type index of {@code (i32,i32,i32,i32)->i32}. */
    static final int FD_WRITE = 2;

    private record Imported(String module, String name, int kind, int type) {
    }

    private final List<Imported> imports = new ArrayList<>();
    private byte[] body = {0x0b};
    private int memoryPages = 1;
    private Integer memoryMax;
    private boolean exportStart = true;
    private boolean exportMemory = true;
    private byte[] data = new byte[0];

    /** Imports function {@code module.name} with type {@code type}. */
    TinyWasm importFunction(String module, String name, int type) {
        imports.add(new Imported(module, name, 0, type));
        return this;
    }

    /** Imports a global {@code module.name} of type i32, immutable. */
    TinyWasm importGlobal(String module, String name) {
        imports.add(new Imported(module, name, 3, -1));
        return this;
    }

    /** @return the function index of the n-th import (all imports are functions in these fixtures) */
    int functionIndex(String name) {
        for (int i = 0; i < imports.size(); i++) {
            if (imports.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    /** Sets the instructions of {@code _start}, without the final {@code end}. */
    TinyWasm body(int... instructions) {
        byte[] code = new byte[instructions.length + 1];
        for (int i = 0; i < instructions.length; i++) {
            code[i] = (byte) instructions[i];
        }
        code[instructions.length] = 0x0b;
        this.body = code;
        return this;
    }

    TinyWasm memory(int pages, Integer max) {
        this.memoryPages = pages;
        this.memoryMax = max;
        return this;
    }

    TinyWasm withoutStartExport() {
        this.exportStart = false;
        return this;
    }

    TinyWasm withoutMemoryExport() {
        this.exportMemory = false;
        return this;
    }

    /** Places {@code bytes} at address 16 and an iovec {@code {16, length}} at address 0. */
    TinyWasm data(byte[] bytes) {
        this.data = bytes.clone();
        return this;
    }

    // ------------------------------------------------------------------ canned modules

    /** {@code _start} loops forever: {@code loop br 0 end}. */
    static byte[] infiniteLoop() {
        return new TinyWasm().body(0x03, 0x40, 0x0c, 0x00, 0x0b).build();
    }

    /** {@code _start} executes {@code unreachable}. */
    static byte[] trap() {
        return new TinyWasm().body(0x00).build();
    }

    /** {@code _start} calls {@code proc_exit(code)}; code below 64. */
    static byte[] exit(int code) {
        TinyWasm wasm = new TinyWasm().importFunction(ModuleRules.WASI_MODULE, "proc_exit", I32_TO_VOID);
        return wasm.body(0x41, code, 0x10, wasm.functionIndex("proc_exit")).build();
    }

    /** {@code _start} writes {@code output} to stdout with one {@code fd_write} and returns. */
    static byte[] writes(String output) {
        return writes(output.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code _start} writes {@code output} to stdout with one {@code fd_write} and returns. */
    static byte[] writes(byte[] output) {
        TinyWasm wasm = new TinyWasm().importFunction(ModuleRules.WASI_MODULE, "fd_write", FD_WRITE)
                .memory(Math.max(1, (output.length + 16) / 65536 + 1), null).data(output);
        // fd_write(fd=1, iovs=0, iovs_len=1, nwritten=8); drop
        return wasm.body(0x41, 0x01, 0x41, 0x00, 0x41, 0x01, 0x41, 0x08, 0x10, wasm.functionIndex("fd_write"), 0x1a)
                .build();
    }

    // ------------------------------------------------------------------ encoding

    byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00});
        ByteArrayOutputStream types = new ByteArrayOutputStream();
        u32(types, 3);
        types.writeBytes(new byte[] {0x60, 0x00, 0x00});
        types.writeBytes(new byte[] {0x60, 0x01, 0x7f, 0x00});
        types.writeBytes(new byte[] {0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f});
        section(out, 1, types);
        if (!imports.isEmpty()) {
            ByteArrayOutputStream section = new ByteArrayOutputStream();
            u32(section, imports.size());
            for (Imported imported : imports) {
                name(section, imported.module());
                name(section, imported.name());
                section.write(imported.kind());
                if (imported.kind() == 0) {
                    u32(section, imported.type());
                } else {
                    section.writeBytes(new byte[] {0x7f, 0x00});
                }
            }
            section(out, 2, section);
        }
        ByteArrayOutputStream functions = new ByteArrayOutputStream();
        u32(functions, 1);
        u32(functions, VOID);
        section(out, 3, functions);
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        u32(memory, 1);
        if (memoryMax == null) {
            memory.write(0x00);
            u32(memory, memoryPages);
        } else {
            memory.write(0x01);
            u32(memory, memoryPages);
            u32(memory, memoryMax);
        }
        section(out, 5, memory);
        ByteArrayOutputStream exports = new ByteArrayOutputStream();
        int functionImports = (int) imports.stream().filter(i -> i.kind() == 0).count();
        u32(exports, (exportStart ? 1 : 0) + (exportMemory ? 1 : 0));
        if (exportStart) {
            name(exports, "_start");
            exports.write(0x00);
            u32(exports, functionImports);
        }
        if (exportMemory) {
            name(exports, "memory");
            exports.write(0x02);
            u32(exports, 0);
        }
        section(out, 7, exports);
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        u32(code, 1);
        ByteArrayOutputStream function = new ByteArrayOutputStream();
        u32(function, 0);
        function.writeBytes(body);
        u32(code, function.size());
        code.writeBytes(function.toByteArray());
        section(out, 10, code);
        if (data.length > 0) {
            ByteArrayOutputStream segments = new ByteArrayOutputStream();
            u32(segments, 2);
            // iovec at 0: ptr 16, len data.length
            ByteArrayOutputStream iovec = new ByteArrayOutputStream();
            le32(iovec, 16);
            le32(iovec, data.length);
            segment(segments, 0, iovec.toByteArray());
            segment(segments, 16, data);
            section(out, 11, segments);
        }
        return out.toByteArray();
    }

    private static void segment(ByteArrayOutputStream out, int offset, byte[] bytes) {
        u32(out, 0);
        out.write(0x41);
        s32(out, offset);
        out.write(0x0b);
        u32(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void section(ByteArrayOutputStream out, int id, ByteArrayOutputStream content) {
        out.write(id);
        u32(out, content.size());
        out.writeBytes(content.toByteArray());
    }

    private static void name(ByteArrayOutputStream out, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        u32(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void u32(ByteArrayOutputStream out, long value) {
        long rest = value;
        do {
            int b = (int) (rest & 0x7f);
            rest >>>= 7;
            out.write(rest != 0 ? b | 0x80 : b);
        } while (rest != 0);
    }

    private static void s32(ByteArrayOutputStream out, int value) {
        int rest = value;
        boolean more = true;
        while (more) {
            int b = rest & 0x7f;
            rest >>= 7;
            more = !((rest == 0 && (b & 0x40) == 0) || (rest == -1 && (b & 0x40) != 0));
            out.write(more ? b | 0x80 : b);
        }
    }

    private static void le32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }
}
