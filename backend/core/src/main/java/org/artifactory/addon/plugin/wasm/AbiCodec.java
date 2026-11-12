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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.artifactory.addon.plugin.groovy.ExecutionHook;
import org.artifactory.addon.plugin.groovy.RealmResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Encodes envelopes and decodes replies of ABI v1 ({@code plugins/wasm-abi/abi-v1.schema.json}). Pure: bytes and
 * values in, bytes and values out; a reply that does not match the schema is {@link Malformed}, never an exception.
 *
 * <p>Replies are read strictly: one JSON object and nothing after it, no duplicate keys, nesting depth at most
 * {@value #MAX_DEPTH}, {@code abi} equal to 1, exactly one of {@code result} and {@code effects}, a result of the
 * kind the hook expects, field types as in the schema, at most {@value #MAX_EFFECTS} effects per step and at most
 * {@value #MAX_GROUPS} groups. Unknown fields are ignored, as the schema requires. Execution response headers must be
 * HTTP tokens with values free of control characters, at most {@value #MAX_RESPONSE_HEADERS} names, and none of
 * {@link #FORBIDDEN_RESPONSE_HEADERS}, which belong to the server (framing, cookies, authentication challenges).
 */
public final class AbiCodec {

    /** Most effects one reply may ask for. */
    public static final int MAX_EFFECTS = 8;
    /** Most groups an {@code authenticated} result may name. */
    public static final int MAX_GROUPS = 64;
    /** Most distinct response header names of an execution result. */
    public static final int MAX_RESPONSE_HEADERS = 32;
    /** Response headers an execution may not set. */
    public static final Set<String> FORBIDDEN_RESPONSE_HEADERS = Set.of("content-length", "transfer-encoding",
            "connection", "keep-alive", "upgrade", "trailer", "te", "set-cookie", "set-cookie2", "www-authenticate",
            "proxy-authenticate");
    static final int MAX_DEPTH = 64;
    private static final int MAX_RESPONSE_HEADER_VALUES = 64;
    private static final int MAX_EFFECT_HEADERS = 64;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AbiCodec() {
    }

    /** A decoded reply. */
    public sealed interface Reply {
    }

    /**
     * {@code {"result": ...}}: the call ends with {@code result}.
     *
     * @param state the replacement state, if the reply carries one
     */
    public record Final(GuestResult result, Optional<JsonText> state) implements Reply {
        public Final {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * {@code {"effects": [...]}}: the module asks for effects and another step.
     *
     * @param state the replacement state, if the reply carries one
     */
    public record Ask(List<Effect> effects, Optional<JsonText> state) implements Reply {
        public Ask {
            effects = List.copyOf(effects);
            Objects.requireNonNull(state, "state");
        }
    }

    /** The reply does not match the schema; the call fails closed. */
    public record Malformed(String reason) implements Reply {
        public Malformed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Thrown and caught inside this class only, to unwind decoding. */
    private static final class Problem extends RuntimeException {
        Problem(String message) {
            super(message, null, false, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ requests

    /** @return the {@code request} of a {@code realm.authenticate} call; a {@code null} password is sent as "" */
    public static JsonNode realmAuthenticate(String realm, String username, String password) {
        ObjectNode request = NODES.objectNode();
        request.put("realm", realm);
        request.put("username", username == null ? "" : username);
        request.put("password", password == null ? "" : password);
        return request;
    }

    /** @return the {@code request} of a {@code realm.bearer} call */
    public static JsonNode realmBearer(String realm, String token) {
        ObjectNode request = NODES.objectNode();
        request.put("realm", realm);
        request.put("token", token == null ? "" : token);
        return request;
    }

    /**
     * The plugin realm that authenticated an execution's caller.
     *
     * @param expiresAt epoch seconds, if the realm credential expires
     */
    public record CallerRealm(String plugin, String realm, OptionalLong expiresAt) {
        public CallerRealm {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(realm, "realm");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    /** The {@code caller} of an execution request. */
    public record CallerView(String username, boolean admin, boolean anonymous, Optional<CallerRealm> realm) {
        public CallerView {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(realm, "realm");
        }
    }

    /**
     * @param params  request parameters
     * @param headers request headers by lower-case name, already stripped of credentials by the caller
     * @param body    the request body
     * @return the {@code request} of an {@code execution} call
     */
    public static JsonNode execution(String name, String method, Map<String, List<String>> params,
            Map<String, List<String>> headers, byte[] body, CallerView caller) {
        ObjectNode request = NODES.objectNode();
        request.put("name", name);
        request.put("method", method);
        request.set("params", multiMap(params));
        request.set("headers", multiMap(headers));
        request.put("body", Base64.getEncoder().encodeToString(body));
        ObjectNode who = request.putObject("caller");
        who.put("username", caller.username());
        who.put("admin", caller.admin());
        who.put("anonymous", caller.anonymous());
        if (caller.realm().isPresent()) {
            CallerRealm realm = caller.realm().get();
            ObjectNode node = who.putObject("realm");
            node.put("plugin", realm.plugin());
            node.put("realm", realm.realm());
            if (realm.expiresAt().isPresent()) {
                node.put("expires_at", realm.expiresAt().getAsLong());
            } else {
                node.putNull("expires_at");
            }
        } else {
            who.putNull("realm");
        }
        return request;
    }

    private static ObjectNode multiMap(Map<String, List<String>> values) {
        ObjectNode node = NODES.objectNode();
        values.forEach((key, list) -> {
            ArrayNode array = node.putArray(key);
            list.forEach(array::add);
        });
        return node;
    }

    // ------------------------------------------------------------------------------------------------ envelope

    /**
     * Encodes one envelope as UTF-8 JSON.
     *
     * @param state   the plugin state as seen by this step; empty for {@code null}
     * @param effects results of the previous step's effects, in request order; empty on step 1
     */
    public static byte[] envelope(Hook hook, JsonNode request, JsonText config, Optional<JsonText> state, long now,
            int step, List<EffectResult> effects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator json = JSON.getFactory().createGenerator(out)) {
            json.writeStartObject();
            json.writeNumberField("abi", WasmManifest.ABI_VERSION);
            json.writeStringField("hook", hook.wireName());
            json.writeFieldName("request");
            JSON.writeTree(json, request);
            json.writeFieldName("config");
            json.writeRawValue(config.json());
            json.writeFieldName("state");
            json.writeRawValue(state.orElse(JsonText.NULL).json());
            json.writeNumberField("now", now);
            json.writeNumberField("step", step);
            json.writeArrayFieldStart("effects");
            for (EffectResult result : effects) {
                JSON.writeTree(json, effectResult(result));
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("writing to memory failed", e);
        }
        return out.toByteArray();
    }

    /** @return the JSON form of an effect result */
    static ObjectNode effectResult(EffectResult result) {
        ObjectNode node = NODES.objectNode();
        switch (result) {
            case EffectResult.HttpGet get -> {
                node.put("type", "http_get");
                node.put("ok", get.ok());
                if (get.ok()) {
                    node.put("status", get.status());
                    node.set("headers", multiMap(get.headers()));
                    node.put("body", Base64.getEncoder().encodeToString(get.body()));
                } else {
                    node.put("error", get.error());
                }
            }
            case EffectResult.Mint mint -> {
                node.put("type", "mint_access_token");
                node.put("ok", mint.ok());
                if (mint.ok()) {
                    node.put("access_token", mint.accessToken());
                    node.put("expires_in", mint.expiresIn());
                } else {
                    node.put("error", mint.error());
                }
            }
        }
        return node;
    }

    // ------------------------------------------------------------------------------------------------ replies

    /** Decodes the module's stdout as the reply to a {@code hook} step. Pure. */
    public static Reply reply(byte[] stdout, Hook hook) {
        JsonNode root;
        try {
            root = JSON.readTree(stdout);
        } catch (IOException | RuntimeException e) {
            return new Malformed("reply is not one JSON value: " + firstLine(e.getMessage()));
        }
        try {
            return decode(root, hook);
        } catch (Problem problem) {
            return new Malformed(problem.getMessage());
        }
    }

    private static Reply decode(JsonNode root, Hook hook) {
        if (root == null || !root.isObject()) {
            throw new Problem("reply is not a JSON object");
        }
        JsonNode abi = root.get("abi");
        if (abi == null || !abi.isIntegralNumber() || abi.asLong() != WasmManifest.ABI_VERSION) {
            throw new Problem("reply abi must be " + WasmManifest.ABI_VERSION);
        }
        boolean hasResult = root.has("result");
        boolean hasEffects = root.has("effects");
        if (hasResult == hasEffects) {
            throw new Problem("reply must have exactly one of result and effects");
        }
        Optional<JsonText> state = root.has("state") ? Optional.of(JsonText.of(root.get("state"))) : Optional.empty();
        if (hasResult) {
            JsonNode result = root.get("result");
            return new Final(hook.isRealm() ? new GuestResult.Realm(realmResult(result))
                    : new GuestResult.Execution(executionResult(result)), state);
        }
        JsonNode effects = root.get("effects");
        if (!effects.isArray() || effects.isEmpty()) {
            throw new Problem("effects must be a non-empty array");
        }
        if (effects.size() > MAX_EFFECTS) {
            throw new Problem("a reply may ask for at most " + MAX_EFFECTS + " effects");
        }
        List<Effect> decoded = new ArrayList<>();
        for (JsonNode effect : effects) {
            decoded.add(effect(effect));
        }
        return new Ask(decoded, state);
    }

    private static RealmResult realmResult(JsonNode result) {
        object(result, "result");
        String outcome = text(result.get("outcome"), "result.outcome");
        return switch (outcome) {
            case "authenticated" -> {
                String username = text(result.get("username"), "result.username");
                if (username.isEmpty()) {
                    throw new Problem("result.username must not be empty");
                }
                Set<String> groups = new LinkedHashSet<>();
                JsonNode list = result.get("groups");
                if (list != null && !list.isNull()) {
                    if (!list.isArray() || list.size() > MAX_GROUPS) {
                        throw new Problem("result.groups must be an array of at most " + MAX_GROUPS + " strings");
                    }
                    for (JsonNode group : list) {
                        groups.add(text(group, "result.groups[]"));
                    }
                }
                JsonNode jit = result.get("jit");
                if (jit != null && !jit.isNull() && !jit.isBoolean()) {
                    throw new Problem("result.jit must be a boolean");
                }
                yield new RealmResult.Authenticated(username, groups, jit != null && jit.asBoolean(),
                        optionalLong(result.get("expires_at"), "result.expires_at"),
                        optionalText(result.get("email"), "result.email"));
            }
            case "not_mine" -> new RealmResult.NotMine();
            case "rejected" -> new RealmResult.Rejected(text(result.get("reason"), "result.reason"));
            default -> throw new Problem("result.outcome '" + outcome + "' is not authenticated, not_mine or rejected");
        };
    }

    private static ExecutionHook.Response executionResult(JsonNode result) {
        object(result, "result");
        JsonNode status = result.get("status");
        if (status == null || !status.isIntegralNumber() || !status.canConvertToInt()
                || status.asInt() < 100 || status.asInt() > 599) {
            throw new Problem("result.status must be an integer from 100 to 599");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        JsonNode headerNode = result.get("headers");
        int values = 0;
        if (headerNode != null && !headerNode.isNull()) {
            object(headerNode, "result.headers");
            if (headerNode.size() > MAX_RESPONSE_HEADERS) {
                throw new Problem("result.headers has more than " + MAX_RESPONSE_HEADERS + " names");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = headerNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!EffectPolicy.isToken(name)) {
                    throw new Problem("result.headers name '" + name + "' is not a valid header name");
                }
                if (FORBIDDEN_RESPONSE_HEADERS.contains(lower) || lower.startsWith("proxy-")) {
                    throw new Problem("result.headers may not set '" + name + "'");
                }
                if (!field.getValue().isArray()) {
                    throw new Problem("result.headers." + name + " must be an array of strings");
                }
                List<String> list = new ArrayList<>();
                for (JsonNode value : field.getValue()) {
                    String text = text(value, "result.headers." + name + "[]");
                    if (!EffectPolicy.isFieldValue(text)) {
                        throw new Problem("result.headers." + name + " has an invalid value");
                    }
                    list.add(text);
                    values++;
                }
                if (headers.putIfAbsent(lower, List.copyOf(list)) != null) {
                    throw new Problem("result.headers names '" + name + "' twice");
                }
            }
            if (values > MAX_RESPONSE_HEADER_VALUES) {
                throw new Problem("result.headers has more than " + MAX_RESPONSE_HEADER_VALUES + " values");
            }
        }
        return new ExecutionHook.Response(status.asInt(), headers, base64(result.get("body"), "result.body"));
    }

    private static Effect effect(JsonNode effect) {
        object(effect, "effects[]");
        String type = text(effect.get("type"), "effects[].type");
        return switch (type) {
            case "http_get" -> {
                String url = text(effect.get("url"), "http_get url");
                Map<String, String> headers = new LinkedHashMap<>();
                JsonNode node = effect.get("headers");
                if (node != null && !node.isNull()) {
                    object(node, "http_get headers");
                    if (node.size() > MAX_EFFECT_HEADERS) {
                        throw new Problem("http_get has more than " + MAX_EFFECT_HEADERS + " headers");
                    }
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        headers.put(field.getKey(), text(field.getValue(), "http_get header " + field.getKey()));
                    }
                }
                yield new Effect.HttpGet(url, headers);
            }
            case "mint_access_token" -> {
                JsonNode expiresIn = effect.get("expires_in");
                if (expiresIn == null || !expiresIn.isIntegralNumber() || !expiresIn.canConvertToLong()) {
                    throw new Problem("mint_access_token expires_in must be an integer");
                }
                yield new Effect.MintAccessToken(expiresIn.asLong(),
                        optionalLong(effect.get("not_after"), "mint_access_token not_after"));
            }
            default -> throw new Problem("unknown effect type '" + type + "'");
        };
    }

    private static void object(JsonNode node, String what) {
        if (node == null || !node.isObject()) {
            throw new Problem(what + " must be an object");
        }
    }

    private static String text(JsonNode node, String what) {
        if (node == null || !node.isTextual()) {
            throw new Problem(what + " must be a string");
        }
        return node.asText();
    }

    private static Optional<String> optionalText(JsonNode node, String what) {
        return node == null || node.isNull() ? Optional.empty() : Optional.of(text(node, what));
    }

    private static OptionalLong optionalLong(JsonNode node, String what) {
        if (node == null || node.isNull()) {
            return OptionalLong.empty();
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new Problem(what + " must be an integer or null");
        }
        return OptionalLong.of(node.asLong());
    }

    private static byte[] base64(JsonNode node, String what) {
        if (node == null || node.isNull()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(text(node, what));
        } catch (IllegalArgumentException e) {
            throw new Problem(what + " is not valid base64");
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        String line = newline < 0 ? message : message.substring(0, newline);
        return line.length() > 200 ? line.substring(0, 200) : line;
    }
}
