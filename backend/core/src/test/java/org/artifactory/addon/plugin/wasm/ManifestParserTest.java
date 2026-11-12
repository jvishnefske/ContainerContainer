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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W1: the manifest schema, its defaults and hard bounds. */
class ManifestParserTest {

    private static WasmManifest valid(String yaml) {
        ManifestParser.Result result = ManifestParser.parse(yaml);
        return assertInstanceOf(ManifestParser.Valid.class, result, String.valueOf(result)).manifest();
    }

    private static String invalid(String yaml) {
        return assertInstanceOf(ManifestParser.Invalid.class, ManifestParser.parse(yaml)).reason();
    }

    @Test
    void fullManifestIsReadWithEveryField() {
        WasmManifest manifest = valid(EchoRealmFixture.manifest("limits: {memoryPages: 64, maxSteps: 3}"));
        WasmManifest.RealmDecl realm = manifest.realms().get(0);
        assertEquals("echo", realm.name());
        assertTrue(realm.autoCreateUsers());
        assertEquals(Set.of(WasmManifest.RealmHookKind.BEARER, WasmManifest.RealmHookKind.AUTHENTICATE),
                realm.hooks());
        WasmManifest.ExecutionDecl execution = manifest.executions().get(0);
        assertEquals("echoMint", execution.name());
        assertEquals(Set.of("*"), execution.users());
        assertEquals("POST", execution.httpMethod());
        assertEquals(Set.of(new WasmManifest.AllowedHost("stub.wasm-it.test", 8443)), manifest.httpHosts());
        assertEquals(64, manifest.limits().memoryPages());
        assertEquals(3, manifest.limits().maxSteps());
        assertEquals(PluginLimits.Limit.STEP_TIMEOUT_MS.defaultValue(), manifest.limits().stepTimeoutMs());
        assertTrue(manifest.config().json().contains("\"issuer\":\"echo-it\""), manifest.config().json());
    }

    @Test
    void defaultsApplyWhenOptionalFieldsAreAbsent() {
        WasmManifest manifest = valid("abi: 1\nrealms: [{name: r}]\nexecutions: [{name: e}]\n");
        assertFalse(manifest.realms().get(0).autoCreateUsers());
        assertEquals(Set.of(WasmManifest.RealmHookKind.values()), manifest.realms().get(0).hooks());
        assertEquals(Set.of(), manifest.executions().get(0).users());
        assertEquals("POST", manifest.executions().get(0).httpMethod());
        assertEquals(PluginLimits.defaults(), manifest.limits());
        assertEquals(JsonText.NULL, manifest.config());
        assertEquals(Set.of(), manifest.httpHosts());
        assertEquals(new WasmManifest.AllowedHost("example.com", 443), ManifestParser.host("example.com"));
    }

    @Test
    void theShippedExampleManifestIsValid() throws IOException {
        Path example = Path.of("../../plugins/wasm-examples/echo-realm/echo-realm.wasm.yaml");
        if (Files.exists(example)) {
            valid(Files.readString(example));
        }
    }

    @Test
    void invalidManifestsAreRejectedWithAReason() {
        List<String[]> cases = List.of(
                new String[] {"realms: [{name: r}]", "abi must be 1"},
                new String[] {"abi: 2\nrealms: [{name: r}]", "abi must be 1"},
                new String[] {"abi: 1", "neither realms nor executions"},
                new String[] {"abi: 1\nrealms: [{name: r}]\nextra: 1", "unknown key 'extra'"},
                new String[] {"abi: 1\nrealms: [{name: r, policy: additive}]", "policy must be 'exclusive'"},
                new String[] {"abi: 1\nrealms: [{name: r, autoCreateUsers: 'yes'}]", "autoCreateUsers"},
                new String[] {"abi: 1\nrealms: [{name: r, hooks: [userExists]}]", "unknown hook"},
                new String[] {"abi: 1\nrealms: [{name: r, hooks: []}]", "hooks must not be empty"},
                new String[] {"abi: 1\nrealms: [{name: 'a b'}]", "must match"},
                new String[] {"abi: 1\nrealms: [{name: r}, {name: r}]", "declared twice"},
                new String[] {"abi: 1\nexecutions: [{name: e, httpMethod: PATCH}]", "httpMethod"},
                new String[] {"abi: 1\nexecutions: [{name: e, users: [1]}]", "must be a non-empty string"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\ncapabilities: {httpGet: {hosts: ['*.x.com']}}",
                        "lower-case host name"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\ncapabilities: {httpGet: {hosts: ['X.com']}}",
                        "lower-case host name"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\ncapabilities: {httpGet: {hosts: ['x.com:0']}}",
                        "invalid port"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\ncapabilities: {fs: {}}", "unknown key 'fs'"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nlimits: {memoryPages: 100000}",
                        "memoryPages must be between 1 and 1024"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nlimits: {stepTimeoutMs: 600000}",
                        "stepTimeoutMs must be between"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nlimits: {maxSteps: 0}", "maxSteps must be between"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nlimits: {cpu: 1}", "unknown limit 'cpu'"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nlimits: {maxSteps: '4'}", "must be an integer"},
                new String[] {"abi: 1\nabi: 1\nexecutions: [{name: e}]", "not valid YAML"},
                new String[] {"abi: 1\nexecutions: [{name: e}]\nconfig: !!java.io.File /etc", "not valid YAML"},
                new String[] {"- a\n- b", "must be a mapping"});
        for (String[] c : cases) {
            String reason = invalid(c[0]);
            assertTrue(reason.contains(c[1]), c[0] + " -> " + reason);
        }
        assertTrue(invalid("x".repeat(ManifestParser.MAX_MANIFEST_CHARS + 1)).contains("longer than"));
        assertTrue(invalid(null).contains("missing"));
    }

    @Test
    void aliasBombsAreRefused() {
        StringBuilder yaml = new StringBuilder("abi: 1\nexecutions: [{name: e}]\nconfig:\n  a: &a [x, x, x]\n");
        for (int i = 0; i < 12; i++) {
            yaml.append("  b").append(i).append(": [*a, *a, *a]\n");
        }
        assertTrue(invalid(yaml.toString()).contains("not valid YAML"));
    }
}
