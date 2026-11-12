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

import java.net.URI;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W3: which effects the host performs. */
class EffectPolicyTest {

    private static final Set<WasmManifest.AllowedHost> HOSTS = Set.of(
            new WasmManifest.AllowedHost("keys.example.com", 443),
            new WasmManifest.AllowedHost("stub.wasm-it.test", 8443));

    private static EffectPolicy.Decision get(String url, Map<String, String> headers) {
        return EffectPolicy.authorize(new Effect.HttpGet(url, headers), Hook.REALM_BEARER, HOSTS);
    }

    private static void denied(String url, String expected) {
        String reason = assertInstanceOf(EffectPolicy.Denied.class, get(url, Map.of()), url).reason();
        assertTrue(reason.contains(expected), url + " -> " + reason);
    }

    @Test
    void httpsToAnAllowlistedHostAndPortIsAllowed() {
        Effect.AllowedGet allowed = (Effect.AllowedGet) assertInstanceOf(EffectPolicy.Allowed.class,
                get("https://keys.example.com/.well-known/jwks.json?x=1", Map.of("Accept", "application/json")))
                .effect();
        assertEquals(URI.create("https://keys.example.com/.well-known/jwks.json?x=1"), allowed.uri());
        assertInstanceOf(EffectPolicy.Allowed.class, get("https://KEYS.example.com:443/", Map.of()));
        assertInstanceOf(EffectPolicy.Allowed.class, get("https://stub.wasm-it.test:8443/allow", Map.of()));
    }

    @Test
    void everythingElseIsDenied() {
        denied("http://keys.example.com/", "must be https");
        denied("ftp://keys.example.com/", "must be https");
        denied("/relative", "must be https");
        denied("https://evil.example.com/", "not allowed");
        denied("https://keys.example.com:8443/", "not allowed");
        denied("https://stub.wasm-it.test/", "not allowed");
        denied("https://user:pw@keys.example.com/", "user info");
        denied("https://keys.example.com@evil.example.com/", "user info");
        denied("https://keys.example.com/#frag", "fragment");
        denied("https://keys.example.com/ bad", "not a valid URI");
        denied("https:///nohost", "no host");
        denied("https://keys.example.com/" + "a".repeat(5000), "longer than");
        denied("https://127.0.0.1/", "not allowed");
    }

    @Test
    void requestHeadersAreChecked() {
        assertInstanceOf(EffectPolicy.Denied.class, get("https://keys.example.com/", Map.of("Host", "evil")));
        assertInstanceOf(EffectPolicy.Denied.class, get("https://keys.example.com/", Map.of("Proxy-X", "y")));
        assertInstanceOf(EffectPolicy.Denied.class, get("https://keys.example.com/", Map.of("a b", "c")));
        assertInstanceOf(EffectPolicy.Denied.class, get("https://keys.example.com/", Map.of("x", "a\r\nb: c")));
        assertInstanceOf(EffectPolicy.Allowed.class, get("https://keys.example.com/", Map.of("Authorization",
                "Bearer from-config")));
    }

    @Test
    void mintingIsForExecutionsOnly() {
        Effect mint = new Effect.MintAccessToken(60, OptionalLong.of(100));
        assertEquals(new EffectPolicy.Allowed(new Effect.AllowedMint(60, OptionalLong.of(100))),
                EffectPolicy.authorize(mint, Hook.EXECUTION, HOSTS));
        assertInstanceOf(EffectPolicy.Denied.class, EffectPolicy.authorize(mint, Hook.REALM_BEARER, HOSTS));
        assertInstanceOf(EffectPolicy.Denied.class, EffectPolicy.authorize(mint, Hook.REALM_AUTHENTICATE, HOSTS));
        assertInstanceOf(EffectPolicy.Denied.class, EffectPolicy.authorize(new Effect.MintAccessToken(0,
                OptionalLong.empty()), Hook.EXECUTION, HOSTS));
    }
}
