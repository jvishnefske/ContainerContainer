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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7: the example module executes in a JVM started with {@code --illegal-native-access=deny} and without any
 * {@code --enable-native-access}, so the WASM path (Endive's interpreter, WASI, the step runner) uses no JNI, FFM
 * or other restricted method. The probe JVM gets this test JVM's class path and runs {@link NativeAccessProbe}.
 */
class NativeAccessDeniedTest {

    @Test
    void theExampleModuleRunsWithNativeAccessDenied() throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = List.of(java.toString(), "--illegal-native-access=deny", "-Xmx256m",
                "-cp", System.getProperty("java.class.path"), NativeAccessProbe.class.getName());
        assertTrue(command.stream().noneMatch(arg -> arg.startsWith("--enable-native-access")));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(finished, "probe JVM did not finish: " + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("PROBE native access denied"), output);
        assertTrue(output.contains("\"outcome\":\"authenticated\"") && output.contains("\"username\":\"carol\""),
                output);
        assertTrue(!output.contains("WARNING: A restricted method"), output);
    }
}
