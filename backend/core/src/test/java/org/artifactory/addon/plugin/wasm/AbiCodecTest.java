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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.addon.plugin.groovy.RealmResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W2: envelope encoding and strict reply decoding against abi-v1.schema.json. */
class AbiCodecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static AbiCodec.Reply reply(String json, Hook hook) {
        return AbiCodec.reply(json.getBytes(StandardCharsets.UTF_8), hook);
    }

    private static String malformed(String json, Hook hook) {
        return assertInstanceOf(AbiCodec.Malformed.class, reply(json, hook), json).reason();
    }

    @Test
    void envelopeCarriesEveryRequiredField() throws Exception {
        byte[] bytes = AbiCodec.envelope(Hook.REALM_BEARER, AbiCodec.realmBearer("echo", "tok"),
                new JsonText("{\"a\":[1]}"), Optional.of(new JsonText("{\"calls\":2}")), 1234, 2,
                List.of(new EffectResult.HttpGet(true, 200, Map.of("x", List.of("y")), "hi".getBytes(), ""),
                        EffectResult.HttpGet.failed("nope"),
                        new EffectResult.Mint(true, "t0k", 60, ""), new EffectResult.Mint(false, "", 0, "refused")));
        JsonNode envelope = JSON.readTree(bytes);
        assertEquals(1, envelope.get("abi").asInt());
        assertEquals("realm.bearer", envelope.get("hook").asText());
        assertEquals("echo", envelope.at("/request/realm").asText());
        assertEquals("tok", envelope.at("/request/token").asText());
        assertEquals(1, envelope.at("/config/a/0").asInt());
        assertEquals(2, envelope.at("/state/calls").asInt());
        assertEquals(1234, envelope.get("now").asLong());
        assertEquals(2, envelope.get("step").asInt());
        assertEquals("aGk=", envelope.at("/effects/0/body").asText());
        assertEquals("y", envelope.at("/effects/0/headers/x/0").asText());
        assertEquals("nope", envelope.at("/effects/1/error").asText());
        assertTrue(envelope.at("/effects/2/ok").asBoolean());
        assertEquals("t0k", envelope.at("/effects/2/access_token").asText());
        assertEquals("refused", envelope.at("/effects/3/error").asText());
        JsonNode empty = JSON.readTree(AbiCodec.envelope(Hook.EXECUTION, AbiCodec.realmBearer("r", "t"),
                JsonText.NULL, Optional.empty(), 1, 1, List.of()));
        assertTrue(empty.get("state").isNull());
        assertTrue(empty.get("config").isNull());
        assertEquals(0, empty.get("effects").size());
    }

    @Test
    void executionRequestHasCallerAndBase64Body() {
        JsonNode request = AbiCodec.execution("e", "POST", Map.of("p", List.of("1")), Map.of("accept", List.of("*")),
                "body".getBytes(), new AbiCodec.CallerView("bob", false, false,
                        Optional.of(new AbiCodec.CallerRealm("echo-realm", "echo", OptionalLong.of(99)))));
        assertEquals("Ym9keQ==", request.get("body").asText());
        assertEquals("1", request.at("/params/p/0").asText());
        assertEquals("bob", request.at("/caller/username").asText());
        assertEquals(99, request.at("/caller/realm/expires_at").asLong());
        JsonNode anonymous = AbiCodec.execution("e", "GET", Map.of(), Map.of(), new byte[0],
                new AbiCodec.CallerView("anonymous", false, true, Optional.empty()));
        assertTrue(anonymous.at("/caller/realm").isNull());
    }

    @Test
    void realmResultsMapOneToOne() {
        AbiCodec.Final done = assertInstanceOf(AbiCodec.Final.class, reply("""
                {"abi":1,"result":{"outcome":"authenticated","username":"alice","groups":["a","b"],"jit":true,
                 "expires_at":42,"email":"a@x","extra":"ignored"},"state":{"n":1}}""", Hook.REALM_BEARER));
        RealmResult.Authenticated auth = assertInstanceOf(RealmResult.Authenticated.class,
                assertInstanceOf(GuestResult.Realm.class, done.result()).result());
        assertEquals("alice", auth.username());
        assertEquals(Set.of("a", "b"), auth.groups());
        assertTrue(auth.jit());
        assertEquals(OptionalLong.of(42), auth.expiresAt());
        assertEquals(Optional.of("a@x"), auth.email());
        assertEquals(Optional.of(new JsonText("{\"n\":1}")), done.state());
        assertInstanceOf(RealmResult.NotMine.class, ((GuestResult.Realm) ((AbiCodec.Final) reply(
                "{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"}}", Hook.REALM_AUTHENTICATE)).result()).result());
        RealmResult rejected = ((GuestResult.Realm) ((AbiCodec.Final) reply(
                "{\"abi\":1,\"result\":{\"outcome\":\"rejected\",\"reason\":\"no\"}}", Hook.REALM_BEARER))
                .result()).result();
        assertEquals(new RealmResult.Rejected("no"), rejected);
        AbiCodec.Final nullState = (AbiCodec.Final) reply("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"},"
                + "\"state\":null}", Hook.REALM_BEARER);
        assertEquals(Optional.of(JsonText.NULL), nullState.state());
    }

    /** Response headers are an allowlist: a module's output is served from Artifactory's own origin. */
    @Test
    void responseHeadersAreAllowlistedAndActiveContentIsRefused() {
        for (String name : List.of("Location", "Access-Control-Allow-Origin", "Content-Security-Policy",
                "Strict-Transport-Security", "Refresh", "Vary", "Set-Cookie", "WWW-Authenticate", "Content-Length",
                "X-Forwarded-Host", "X-JFrog-Version", "X-Artifactory-Id", "X-Frame-Options",
                "X-Content-Type-Options", "Proxy-Foo", "Server")) {
            String json = "{\"abi\":1,\"result\":{\"status\":200,\"headers\":{\"" + name + "\":[\"v\"]}}}";
            assertTrue(malformed(json, Hook.EXECUTION).contains("may not set"), name);
        }
        for (String type : List.of("text/html", "text/html; charset=utf-8", "image/svg+xml", "application/xml",
                "application/atom+xml", "application/javascript", "TEXT/HTML", "", "html")) {
            String json = "{\"abi\":1,\"result\":{\"status\":200,\"headers\":{\"Content-Type\":[\"" + type
                    + "\"]}}}";
            assertTrue(malformed(json, Hook.EXECUTION).contains("content-type"), type);
        }
        AbiCodec.Final ok = assertInstanceOf(AbiCodec.Final.class, reply("""
                {"abi":1,"result":{"status":200,"headers":{"Content-Type":["application/json; charset=utf-8"],
                "Cache-Control":["no-store"],"ETag":["\\"1\\""],"X-Request-Id":["r1"],
                "Content-Disposition":["attachment"]}}}""", Hook.EXECUTION));
        GuestResult.Execution execution = assertInstanceOf(GuestResult.Execution.class, ok.result());
        assertEquals(List.of("nosniff"), execution.response().headers().get("x-content-type-options"));
        assertEquals(List.of("r1"), execution.response().headers().get("x-request-id"));
    }

    /** Request headers passed to a module are an allowlist, so no credential header can reach it. */
    @Test
    void onlyAllowlistedRequestHeadersReachTheModule() {
        Map<String, List<String>> passed = WasmPlugin.headers(Map.of(
                "Authorization", List.of("Basic eDp5"), "Cookie", List.of("s=1"), "X-JFrog-Art-Api", List.of("k"),
                "X-Auth-Token-Delegate", List.of("t"), "X-NuGet-ApiKey", List.of("n"), "X-Api-Key", List.of("a"),
                "Private-Token", List.of("p"), "Accept", List.of("application/json"), "User-Agent", List.of("ua"),
                "X-Request-Id", List.of("r")));
        assertEquals(Map.of("accept", List.of("application/json"), "user-agent", List.of("ua"),
                "x-request-id", List.of("r")), passed);
    }

    @Test
    void executionResultsAndEffectsDecode() {
        AbiCodec.Final done = assertInstanceOf(AbiCodec.Final.class, reply("""
                {"abi":1,"result":{"status":201,"headers":{"Content-Type":["application/json"]},"body":"e30="}}""",
                Hook.EXECUTION));
        GuestResult.Execution execution = assertInstanceOf(GuestResult.Execution.class, done.result());
        assertEquals(201, execution.response().status());
        assertEquals(Map.of("content-type", List.of("application/json"), "x-content-type-options", List.of("nosniff")),
                execution.response().headers());
        assertArrayEquals("{}".getBytes(), execution.response().body());
        AbiCodec.Ask ask = assertInstanceOf(AbiCodec.Ask.class, reply("""
                {"abi":1,"effects":[{"type":"http_get","url":"https://a.test/x","headers":{"accept":"text/plain"}},
                 {"type":"mint_access_token","expires_in":60,"not_after":100}]}""", Hook.EXECUTION));
        assertEquals(List.of(new Effect.HttpGet("https://a.test/x", Map.of("accept", "text/plain")),
                new Effect.MintAccessToken(60, OptionalLong.of(100))), ask.effects());
        assertEquals(Optional.empty(), ask.state());
    }

    @Test
    void anythingOffSchemaIsMalformed() {
        assertTrue(malformed("not json", Hook.REALM_BEARER).contains("not one JSON value"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"}} {}", Hook.REALM_BEARER)
                .contains("not one JSON value"));
        assertTrue(malformed("{\"abi\":1,\"abi\":1,\"result\":{}}", Hook.REALM_BEARER).contains("JSON"));
        assertTrue(malformed("[]", Hook.REALM_BEARER).contains("not a JSON object"));
        assertTrue(malformed("{\"result\":{\"outcome\":\"not_mine\"}}", Hook.REALM_BEARER).contains("abi"));
        assertTrue(malformed("{\"abi\":2,\"result\":{\"outcome\":\"not_mine\"}}", Hook.REALM_BEARER)
                .contains("abi"));
        assertTrue(malformed("{\"abi\":1}", Hook.REALM_BEARER).contains("exactly one"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"},\"effects\":[]}", Hook.REALM_BEARER)
                .contains("exactly one"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"admin\"}}", Hook.REALM_BEARER)
                .contains("outcome"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"authenticated\"}}", Hook.REALM_BEARER)
                .contains("username"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"authenticated\",\"username\":\"a\",\"jit\":1}}",
                Hook.REALM_BEARER).contains("jit"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":200}}", Hook.REALM_BEARER).contains("outcome"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"outcome\":\"not_mine\"}}", Hook.EXECUTION)
                .contains("status"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":600}}", Hook.EXECUTION).contains("status"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":200,\"body\":\"!!\"}}", Hook.EXECUTION)
                .contains("base64"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":200,\"headers\":{\"Set-Cookie\":[\"a=b\"]}}}",
                Hook.EXECUTION).contains("may not set"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":200,\"headers\":{\"x-a\":[\"a\\r\\nb: c\"]}}}",
                Hook.EXECUTION).contains("invalid value"));
        assertTrue(malformed("{\"abi\":1,\"result\":{\"status\":200,\"headers\":{\"a b\":[\"c\"]}}}",
                Hook.EXECUTION).contains("not a valid header name"));
        assertTrue(malformed("{\"abi\":1,\"effects\":[]}", Hook.EXECUTION).contains("non-empty"));
        assertTrue(malformed("{\"abi\":1,\"effects\":[{\"type\":\"exec\"}]}", Hook.EXECUTION)
                .contains("unknown effect"));
        assertTrue(malformed("{\"abi\":1,\"effects\":[{\"type\":\"mint_access_token\",\"expires_in\":\"60\"}]}",
                Hook.EXECUTION).contains("expires_in"));
        String nine = "{\"type\":\"http_get\",\"url\":\"https://a\"},".repeat(9);
        assertTrue(malformed("{\"abi\":1,\"effects\":[" + nine.substring(0, nine.length() - 1) + "]}",
                Hook.EXECUTION).contains("at most"));
        assertTrue(malformed("[".repeat(100) + "]".repeat(100), Hook.EXECUTION).contains("JSON"));
    }
}
