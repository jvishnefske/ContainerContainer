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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Test fixture run in a separate JVM by {@link NativeAccessDeniedTest} (W7): first proves that native access is
 * denied in this JVM (loading a library throws {@link IllegalCallerException}), then parses the example module,
 * checks it against {@link ModuleRules} and runs a bearer step on the worker pool, printing the reply. Exit status
 * 0 means the module ran with native access denied; anything else is a failure.
 */
public final class NativeAccessProbe {

    private NativeAccessProbe() {
    }

    public static void main(String[] args) {
        try {
            System.loadLibrary("wasm-probe-does-not-exist");
            System.out.println("PROBE native access is NOT denied");
            System.exit(2);
        } catch (IllegalCallerException e) {
            System.out.println("PROBE native access denied: " + e.getClass().getSimpleName());
        } catch (UnsatisfiedLinkError e) {
            System.out.println("PROBE native access is NOT denied (the library lookup ran)");
            System.exit(2);
        }
        EndiveRuntime.Module module = (EndiveRuntime.Module) EndiveRuntime.parse(EchoRealmFixture.bytes());
        List<String> errors = ModuleRules.check(module.shape(), EndiveRuntime.wasiFunctions(), 256);
        if (!errors.isEmpty()) {
            System.out.println("PROBE rules " + errors);
            System.exit(3);
        }
        byte[] envelope = AbiCodec.envelope(Hook.REALM_BEARER,
                AbiCodec.realmBearer("echo", EchoRealmFixture.token(EchoRealmFixture.ISSUER, "ok:carol")),
                new JsonText("{\"issuer\":\"" + EchoRealmFixture.ISSUER + "\",\"groups\":[\"readers\"]}"),
                Optional.empty(), 1, 1, List.of());
        StepOutput output = new WasmWorkers(1, 1).run(() -> EndiveRuntime.step(module.module(),
                ModuleRules.memoryLimits(module.shape(), 256), envelope, 65536, err -> { }), 30_000);
        if (output instanceof StepOutput.Exited exited) {
            System.out.println("PROBE reply " + new String(exited.stdout(), StandardCharsets.UTF_8));
            System.exit(0);
        }
        System.out.println("PROBE step " + output);
        System.exit(4);
    }
}
