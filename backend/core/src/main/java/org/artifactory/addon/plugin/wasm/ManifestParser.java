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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and validates a WASM plugin manifest. Pure: text in, {@link Result} out, never an exception.
 *
 * <p>The manifest is YAML read with SnakeYAML's safe constructor (plain maps, lists and scalars only; no duplicate
 * keys; at most {@value #MAX_MANIFEST_CHARS} characters, {@value #MAX_ALIASES} aliases and nesting depth
 * {@value #MAX_DEPTH}). Its schema, with every key outside it failing the load so a typo cannot silently weaken a
 * rule:
 * <ul>
 *     <li>{@code abi}: required, must be {@value WasmManifest#ABI_VERSION};</li>
 *     <li>{@code realms}: list of {@code {name, policy, autoCreateUsers, hooks}}; {@code name} matches
 *         {@code [A-Za-z0-9_-]{1,64}} and is unique in the manifest; {@code policy} defaults to and must be
 *         {@code exclusive}; {@code autoCreateUsers} is a boolean, default false; {@code hooks} is a non-empty
 *         subset of {@code [authenticate, bearer]}, default both;</li>
 *     <li>{@code executions}: list of {@code {name, users, groups, httpMethod}}; {@code name} matches
 *         {@code [A-Za-z0-9_.-]{1,64}} and is unique; {@code users} and {@code groups} are lists of strings (empty
 *         means administrators only, {@code '*'} in {@code users} means every authenticated caller);
 *         {@code httpMethod} is one of GET, POST, PUT, DELETE, default POST;</li>
 *     <li>{@code capabilities}: {@code {httpGet: {hosts: [...]}}}; each host is a lower-case DNS name or IPv4
 *         address, optionally with {@code :port} (without one only port 443 is allowed); no wildcards;</li>
 *     <li>{@code limits}: integers within the bounds of {@link PluginLimits.Limit};</li>
 *     <li>{@code config}: any value, converted to JSON and handed to the module verbatim.</li>
 * </ul>
 * A manifest must declare at least one realm or execution.
 */
public final class ManifestParser {

    /** Longest manifest accepted, in characters. */
    public static final int MAX_MANIFEST_CHARS = 64 * 1024;
    static final int MAX_ALIASES = 10;
    static final int MAX_DEPTH = 32;
    private static final int MAX_ENTRIES = 64;

    private static final Pattern REALM_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern EXECUTION_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern HOST = Pattern.compile("(" + LABEL + "(?:\\." + LABEL + ")*)(?::([0-9]{1,5}))?");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private static final Set<String> TOP_KEYS = Set.of("abi", "realms", "executions", "capabilities", "limits",
            "config");
    private static final Set<String> REALM_KEYS = Set.of("name", "policy", "autoCreateUsers", "hooks");
    private static final Set<String> EXECUTION_KEYS = Set.of("name", "users", "groups", "httpMethod");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ManifestParser() {
    }

    /** The outcome of parsing a manifest. */
    public sealed interface Result {
    }

    /** The manifest is valid. */
    public record Valid(WasmManifest manifest) implements Result {
        public Valid {
            Objects.requireNonNull(manifest, "manifest");
        }
    }

    /** The manifest is not valid; {@code reason} says why, for plugin status. */
    public record Invalid(String reason) implements Result {
        public Invalid {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Thrown and caught inside this class only, to unwind validation. */
    private static final class Problem extends RuntimeException {
        Problem(String message) {
            super(message, null, false, false);
        }
    }

    /** Parses {@code text}. Pure. */
    public static Result parse(String text) {
        if (text == null) {
            return new Invalid("manifest is missing");
        }
        if (text.length() > MAX_MANIFEST_CHARS) {
            return new Invalid("manifest is longer than " + MAX_MANIFEST_CHARS + " characters");
        }
        Object root;
        try {
            root = yaml().load(text);
        } catch (RuntimeException e) {
            return new Invalid("manifest is not valid YAML: " + firstLine(e.getMessage()));
        }
        try {
            return new Valid(manifest(root));
        } catch (Problem problem) {
            return new Invalid(problem.getMessage());
        } catch (IllegalArgumentException e) {
            return new Invalid(String.valueOf(e.getMessage()));
        }
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setNestingDepthLimit(MAX_DEPTH);
        options.setCodePointLimit(MAX_MANIFEST_CHARS);
        options.setAllowRecursiveKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private static WasmManifest manifest(Object root) {
        Map<?, ?> top = map(root, "manifest");
        keys(top, TOP_KEYS, "manifest");
        Object abi = top.get("abi");
        if (!(abi instanceof Integer version) || version != WasmManifest.ABI_VERSION) {
            throw new Problem("abi must be " + WasmManifest.ABI_VERSION + ", not " + abi);
        }
        List<WasmManifest.RealmDecl> realms = realms(top.get("realms"));
        List<WasmManifest.ExecutionDecl> executions = executions(top.get("executions"));
        if (realms.isEmpty() && executions.isEmpty()) {
            throw new Problem("manifest declares neither realms nor executions");
        }
        Set<WasmManifest.AllowedHost> hosts = capabilities(top.get("capabilities"));
        PluginLimits limits = limits(top.get("limits"));
        JsonText config;
        try {
            config = JsonText.of(JSON.valueToTree(top.get("config")));
        } catch (IllegalArgumentException e) {
            throw new Problem("config cannot be converted to JSON: " + firstLine(e.getMessage()));
        }
        return new WasmManifest(realms, executions, hosts, limits, config);
    }

    private static List<WasmManifest.RealmDecl> realms(Object value) {
        List<WasmManifest.RealmDecl> realms = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object item : list(value, "realms")) {
            Map<?, ?> entry = map(item, "realms entry");
            keys(entry, REALM_KEYS, "realms entry");
            String name = string(entry.get("name"), "realms[].name");
            if (!REALM_NAME.matcher(name).matches()) {
                throw new Problem("realm name '" + name + "' must match [A-Za-z0-9_-]{1,64}");
            }
            if (!names.add(name)) {
                throw new Problem("realm '" + name + "' is declared twice");
            }
            Object policy = entry.get("policy");
            if (policy != null && !"exclusive".equals(policy)) {
                throw new Problem("realm '" + name + "': policy must be 'exclusive' (additive realms are not "
                        + "available to WASM plugins in ABI v1), not '" + policy + "'");
            }
            Object auto = entry.get("autoCreateUsers");
            if (auto != null && !(auto instanceof Boolean)) {
                throw new Problem("realm '" + name + "': autoCreateUsers must be true or false");
            }
            Set<WasmManifest.RealmHookKind> hooks = EnumSet.noneOf(WasmManifest.RealmHookKind.class);
            if (entry.containsKey("hooks")) {
                for (String hook : strings(entry.get("hooks"), "realm '" + name + "' hooks")) {
                    hooks.add(switch (hook) {
                        case "authenticate" -> WasmManifest.RealmHookKind.AUTHENTICATE;
                        case "bearer" -> WasmManifest.RealmHookKind.BEARER;
                        default -> throw new Problem("realm '" + name + "': unknown hook '" + hook
                                + "' (expected authenticate or bearer)");
                    });
                }
                if (hooks.isEmpty()) {
                    throw new Problem("realm '" + name + "': hooks must not be empty");
                }
            } else {
                hooks = EnumSet.allOf(WasmManifest.RealmHookKind.class);
            }
            realms.add(new WasmManifest.RealmDecl(name, Boolean.TRUE.equals(auto), hooks));
        }
        return realms;
    }

    private static List<WasmManifest.ExecutionDecl> executions(Object value) {
        List<WasmManifest.ExecutionDecl> executions = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object item : list(value, "executions")) {
            Map<?, ?> entry = map(item, "executions entry");
            keys(entry, EXECUTION_KEYS, "executions entry");
            String name = string(entry.get("name"), "executions[].name");
            if (!EXECUTION_NAME.matcher(name).matches()) {
                throw new Problem("execution name '" + name + "' must match [A-Za-z0-9_.-]{1,64}");
            }
            if (!names.add(name)) {
                throw new Problem("execution '" + name + "' is declared twice");
            }
            Object method = entry.get("httpMethod");
            String httpMethod = method == null ? "POST" : string(method, "execution '" + name + "' httpMethod");
            if (!METHODS.contains(httpMethod)) {
                throw new Problem("execution '" + name + "': httpMethod must be one of GET, POST, PUT, DELETE");
            }
            executions.add(new WasmManifest.ExecutionDecl(name,
                    new LinkedHashSet<>(strings(entry.get("users"), "execution '" + name + "' users")),
                    new LinkedHashSet<>(strings(entry.get("groups"), "execution '" + name + "' groups")),
                    httpMethod));
        }
        return executions;
    }

    private static Set<WasmManifest.AllowedHost> capabilities(Object value) {
        if (value == null) {
            return Set.of();
        }
        Map<?, ?> capabilities = map(value, "capabilities");
        keys(capabilities, Set.of("httpGet"), "capabilities");
        Object httpGet = capabilities.get("httpGet");
        if (httpGet == null) {
            return Set.of();
        }
        Map<?, ?> http = map(httpGet, "capabilities.httpGet");
        keys(http, Set.of("hosts"), "capabilities.httpGet");
        Set<WasmManifest.AllowedHost> hosts = new LinkedHashSet<>();
        for (String entry : strings(http.get("hosts"), "capabilities.httpGet.hosts")) {
            hosts.add(host(entry));
        }
        return hosts;
    }

    /** Parses one {@code host[:port]} allowlist entry. Pure. */
    static WasmManifest.AllowedHost host(String entry) {
        Matcher matcher = HOST.matcher(entry);
        if (entry.length() > 260 || !matcher.matches()) {
            throw new Problem("capabilities.httpGet.hosts entry '" + entry
                    + "' must be a lower-case host name or IPv4 address with an optional :port");
        }
        String host = matcher.group(1);
        int port = 443;
        if (matcher.group(2) != null) {
            port = Integer.parseInt(matcher.group(2));
            if (port < 1 || port > 65535) {
                throw new Problem("capabilities.httpGet.hosts entry '" + entry + "' has an invalid port");
            }
        }
        return new WasmManifest.AllowedHost(host.toLowerCase(Locale.ROOT), port);
    }

    private static PluginLimits limits(Object value) {
        if (value == null) {
            return PluginLimits.defaults();
        }
        Map<?, ?> limits = map(value, "limits");
        Map<PluginLimits.Limit, Integer> values = new EnumMap<>(PluginLimits.Limit.class);
        for (Map.Entry<?, ?> entry : limits.entrySet()) {
            String key = String.valueOf(entry.getKey());
            PluginLimits.Limit limit = PluginLimits.Limit.byKey(key)
                    .orElseThrow(() -> new Problem("unknown limit '" + key + "'"));
            if (!(entry.getValue() instanceof Integer number)) {
                throw new Problem("limits." + key + " must be an integer");
            }
            values.put(limit, number);
        }
        return PluginLimits.of(values);
    }

    private static Map<?, ?> map(Object value, String what) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new Problem(what + " must be a mapping");
        }
        return map;
    }

    private static List<?> list(Object value, String what) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new Problem(what + " must be a list");
        }
        if (list.size() > MAX_ENTRIES) {
            throw new Problem(what + " has more than " + MAX_ENTRIES + " entries");
        }
        return list;
    }

    private static List<String> strings(Object value, String what) {
        List<String> strings = new ArrayList<>();
        for (Object item : list(value, what)) {
            strings.add(string(item, what));
        }
        return strings;
    }

    private static String string(Object value, String what) {
        if (!(value instanceof String text) || text.isEmpty()) {
            throw new Problem(what + " must be a non-empty string");
        }
        return text;
    }

    private static void keys(Map<?, ?> map, Set<String> allowed, String what) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String name) || !allowed.contains(name)) {
                throw new Problem("unknown key '" + key + "' in " + what);
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
