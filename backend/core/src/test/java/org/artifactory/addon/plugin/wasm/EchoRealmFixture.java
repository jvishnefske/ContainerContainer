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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Test fixture: the committed build of the public example module {@code plugins/wasm-examples/echo-realm}
 * ({@code src/test/resources/wasm/echo-realm.wasm}), its recorded sha256, a manifest for it, and token helpers.
 * CI rebuilds the module from source and checks it matches the recorded hash (W5).
 */
public final class EchoRealmFixture {

    /** Issuer the test manifest configures. */
    public static final String ISSUER = "echo-it";
    /** The {@code fetch_url} of the test manifest. */
    public static final String FETCH_URL = "https://stub.wasm-it.test:8443/allow";

    private EchoRealmFixture() {
    }

    /** @return the module bytes */
    public static byte[] bytes() {
        return resource("/wasm/echo-realm.wasm");
    }

    /** @return the hex sha256 recorded next to the module */
    public static String recordedSha256() {
        String line = new String(resource("/wasm/echo-realm.wasm.sha256"), StandardCharsets.US_ASCII).trim();
        return line.split("\\s+")[0];
    }

    /** @return the hex sha256 of {@code bytes} */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return a manifest for the module with the given limits block (YAML, may be empty) */
    public static String manifest(String limits) {
        return """
                abi: 1
                realms:
                  - name: echo
                    policy: exclusive
                    autoCreateUsers: true
                    hooks: [bearer, authenticate]
                executions:
                  - name: echoMint
                    users: ['*']
                    httpMethod: POST
                capabilities:
                  httpGet:
                    hosts: [stub.wasm-it.test:8443]
                %s
                config:
                  issuer: %s
                  groups: [readers, admins, ghosts]
                  fetch_url: %s
                  mint_seconds: 300
                """.formatted(limits, ISSUER, FETCH_URL);
    }

    /** @return an unsigned JWT-shaped token with {@code iss} and {@code sub}, as the example accepts */
    public static String token(String issuer, String sub) {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(("{\"iss\":\"" + issuer + "\",\"sub\":\"" + sub + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".c2ln";
    }

    private static byte[] resource(String path) {
        try (InputStream in = EchoRealmFixture.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
