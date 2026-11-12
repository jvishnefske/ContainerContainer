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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W3, W4: running steps on Endive's interpreter in fresh instances, with the outcome of every failure kind. */
class EndiveRuntimeTest {

    private static final WasmWorkers WORKERS = new WasmWorkers(2, 4);
    private static final ModuleRules.MemoryPages ONE_PAGE = new ModuleRules.MemoryPages(1, 2);

    private static EndiveRuntime.Module module(byte[] bytes) {
        return assertInstanceOf(EndiveRuntime.Module.class, EndiveRuntime.parse(bytes));
    }

    private static StepOutput step(byte[] wasm, int maxStdout, long timeoutMs) {
        EndiveRuntime.Module module = module(wasm);
        return WORKERS.run(() -> EndiveRuntime.step(module.module(), ONE_PAGE, new byte[0], maxStdout, err -> { }),
                timeoutMs);
    }

    private static StepOutput.Failure failure(StepOutput output) {
        return assertInstanceOf(StepOutput.Failed.class, output).failure();
    }

    @Test
    void cleanExitReturnsStdout() {
        StepOutput output = step(TinyWasm.writes("{\"abi\":1}"), 1024, 5000);
        assertEquals("{\"abi\":1}", new String(assertInstanceOf(StepOutput.Exited.class, output).stdout(),
                StandardCharsets.UTF_8));
        assertInstanceOf(StepOutput.Exited.class, step(TinyWasm.exit(0), 1024, 5000));
    }

    @Test
    void trapNonZeroExitAndOversizedStdoutFail() {
        assertEquals(StepOutput.Failure.TRAP, failure(step(TinyWasm.trap(), 1024, 5000)));
        StepOutput.Failed exit = assertInstanceOf(StepOutput.Failed.class, step(TinyWasm.exit(3), 1024, 5000));
        assertEquals(StepOutput.Failure.EXIT_CODE, exit.failure());
        assertEquals("exit code 3", exit.detail());
        byte[] big = new byte[3000];
        Arrays.fill(big, (byte) 'x');
        assertEquals(StepOutput.Failure.STDOUT_OVERFLOW, failure(step(TinyWasm.writes(big), 1024, 5000)));
    }

    @Test
    void anInfiniteLoopIsInterruptedAtTheDeadlineAndTheWorkerIsFreedAgain() throws Exception {
        long start = System.nanoTime();
        assertEquals(StepOutput.Failure.TIMEOUT, failure(step(TinyWasm.infiniteLoop(), 1024, 200)));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000, "the deadline must cut the step short");
        long waited = 0;
        while (WORKERS.active() > 0 && waited < 5000) {
            Thread.sleep(20);
            waited += 20;
        }
        assertEquals(0, WORKERS.active(), "the interrupted worker must stop running the module");
        assertInstanceOf(StepOutput.Exited.class, step(TinyWasm.exit(0), 1024, 5000));
    }

    @Test
    void aFullPoolRefusesInsteadOfWaiting() throws Exception {
        WasmWorkers tiny = new WasmWorkers(1, 1);
        EndiveRuntime.Module loop = module(TinyWasm.infiniteLoop());
        List<Thread> threads = new ArrayList<>();
        List<StepOutput> outputs = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 3; i++) {
            Thread thread = new Thread(() -> outputs.add(tiny.run(() -> EndiveRuntime.step(loop.module(), ONE_PAGE,
                    new byte[0], 1024, err -> { }), 500)));
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(10_000);
        }
        assertEquals(3, outputs.size());
        assertTrue(outputs.stream().anyMatch(o -> failure(o) == StepOutput.Failure.BUSY), String.valueOf(outputs));
        assertTrue(outputs.stream().allMatch(o -> o instanceof StepOutput.Failed));
    }

    @Test
    void memoryCannotGrowPastTheCap() {
        // memory.grow 1 page beyond the 1-page cap returns -1; trap when it does: grow, -1, eq, if unreachable end
        byte[] wasm = new TinyWasm().body(0x41, 0x01, 0x40, 0x00, 0x41, 0x7f, 0x46, 0x04, 0x40, 0x00, 0x0b).build();
        EndiveRuntime.Module module = module(wasm);
        StepOutput capped = WORKERS.run(() -> EndiveRuntime.step(module.module(),
                new ModuleRules.MemoryPages(1, 1), new byte[0], 1024, err -> { }), 5000);
        assertEquals(StepOutput.Failure.TRAP, failure(capped));
        StepOutput roomy = WORKERS.run(() -> EndiveRuntime.step(module.module(),
                new ModuleRules.MemoryPages(1, 2), new byte[0], 1024, err -> { }), 5000);
        assertInstanceOf(StepOutput.Exited.class, roomy);
    }

    @Test
    void everyStepGetsAFreshInstanceAndStdinIsTheEnvelope() {
        EndiveRuntime.Module echo = module(EchoRealmFixture.bytes());
        byte[] envelope = AbiCodec.envelope(Hook.REALM_BEARER, AbiCodec.realmBearer("echo", "x"), JsonText.NULL,
                java.util.Optional.empty(), 1, 1, List.of());
        List<String> stderr = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            StepOutput output = WORKERS.run(() -> EndiveRuntime.step(echo.module(),
                    ModuleRules.memoryLimits(echo.shape(), 256), envelope, 65536, stderr::add), 10_000);
            String reply = new String(assertInstanceOf(StepOutput.Exited.class, output).stdout(),
                    StandardCharsets.UTF_8);
            // Without config the realm claims nothing, and the counter starts from the envelope's null state.
            assertTrue(reply.contains("\"not_mine\"") && reply.contains("\"calls\":1"), reply);
        }
        assertEquals(2, stderr.size());
        assertTrue(stderr.get(0).contains("hook=realm.bearer"), stderr.get(0));
    }

    @Test
    void invalidBytesAreNotAModule() {
        assertInstanceOf(EndiveRuntime.Invalid.class, EndiveRuntime.parse(new byte[] {1, 2, 3}));
        assertTrue(EndiveRuntime.wasiFunctions().containsAll(List.of("fd_write", "fd_read", "proc_exit",
                "environ_get", "environ_sizes_get", "random_get", "clock_time_get")));
    }
}
