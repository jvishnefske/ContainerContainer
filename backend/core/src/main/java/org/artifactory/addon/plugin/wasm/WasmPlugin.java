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
import org.artifactory.addon.plugin.groovy.ExecutionHook;
import org.artifactory.addon.plugin.groovy.HostedOutcome;
import org.artifactory.addon.plugin.groovy.LoadedPlugin;
import org.artifactory.addon.plugin.groovy.PluginExecution;
import org.artifactory.addon.plugin.groovy.PluginRealm;
import org.artifactory.addon.plugin.groovy.RealmHook;
import org.artifactory.addon.plugin.groovy.RealmResult;
import org.artifactory.resource.ResourceStreamHandle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * One loaded WASM plugin: its manifest, its state, and the realm and execution hooks it contributes to the plugin
 * snapshot as {@link RealmHook.Hosted} and {@link ExecutionHook.Hosted}. Everything these hooks answer goes through
 * the same Java enforcement as Groovy plugins: realm results through {@code PluginRealmAuthenticator} and
 * {@code RealmGrantPolicy} (P1-P6), executions through {@code ExecutionAuthorizer} and the HTTP method rule (G4),
 * all before or after the module runs, never inside it.
 *
 * <p>Each hook call is one {@link StepLoop} run. A completed call commits its replacement state, if any, and
 * answers; a failed one answers {@link HostedOutcome.Failed}, which the caller turns into "not mine" (realms) or 500
 * (executions), and leaves the state untouched (W4).
 *
 * <p>An execution's {@code request} carries only the request headers in {@link #PASSED_REQUEST_HEADERS}, an allowlist
 * of headers that never carry credentials, so the module never sees the caller's password, token, API key, cookie or
 * any authentication header added later; the body (read up to
 * {@code limits.maxStdinBytes}; a larger body fails the call) and the caller, including the plugin realm that
 * authenticated it.
 */
public final class WasmPlugin {

    /**
     * The only request headers passed to a module (lower case). An allowlist rather than a list of credential headers
     * to remove, because Artifactory accepts credentials in many headers (and may add more), and WASM modules are not
     * trusted with them.
     */
    public static final Set<String> PASSED_REQUEST_HEADERS = Set.of("accept", "accept-charset", "accept-encoding",
            "accept-language", "content-type", "content-length", "content-encoding", "content-language", "user-agent",
            "if-match", "if-none-match", "if-modified-since", "if-unmodified-since", "if-range", "range",
            "x-request-id", "x-correlation-id", "traceparent", "tracestate");

    private final String name;
    private final WasmManifest manifest;
    private final StepLoop.Runner runner;
    private final StepLoop.Performer performer;
    private final RequestContext context;
    private final LongSupplier nowSeconds;
    private final PluginState state = new PluginState();

    /** What an execution call may learn about the HTTP request beyond its parameters and body. */
    public interface RequestContext {

        /** No request: no headers and no realm. */
        RequestContext NONE = new RequestContext() {
            @Override
            public Map<String, List<String>> headers() {
                return Map.of();
            }

            @Override
            public Optional<AbiCodec.CallerRealm> callerRealm() {
                return Optional.empty();
            }
        };

        /** @return the current request's headers by name */
        Map<String, List<String>> headers();

        /** @return the plugin realm that authenticated the current request, if one did */
        Optional<AbiCodec.CallerRealm> callerRealm();
    }

    /**
     * @param name       the plugin name
     * @param runner     runs one step of the module (production: {@link EndiveRuntime} on {@link WasmWorkers})
     * @param performer  performs allowed effects (production: {@link HostEffects})
     * @param context    the current request, for executions
     * @param nowSeconds the clock, epoch seconds
     */
    public WasmPlugin(String name, WasmManifest manifest, StepLoop.Runner runner, StepLoop.Performer performer,
            RequestContext context, LongSupplier nowSeconds) {
        this.name = Objects.requireNonNull(name, "name");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.performer = Objects.requireNonNull(performer, "performer");
        this.context = Objects.requireNonNull(context, "context");
        this.nowSeconds = Objects.requireNonNull(nowSeconds, "nowSeconds");
    }

    /** @return the plugin's state store */
    public PluginState state() {
        return state;
    }

    /** @return the snapshot entry: the manifest's realms and executions, served by this plugin */
    public LoadedPlugin toLoadedPlugin(String fileName) {
        List<PluginRealm> realms = new ArrayList<>();
        for (WasmManifest.RealmDecl realm : manifest.realms()) {
            Optional<RealmHook> authenticate = realm.hooks().contains(WasmManifest.RealmHookKind.AUTHENTICATE)
                    ? Optional.of(new RealmHook.Hosted(credentials -> authenticate(realm.name(), credentials)))
                    : Optional.empty();
            Optional<RealmHook> bearer = realm.hooks().contains(WasmManifest.RealmHookKind.BEARER)
                    ? Optional.of(new RealmHook.Hosted(credentials -> bearer(realm.name(), credentials)))
                    : Optional.empty();
            realms.add(new PluginRealm(name, realm.name(), PluginRealm.Policy.EXCLUSIVE, realm.autoCreateUsers(),
                    authenticate, bearer, Optional.empty()));
        }
        List<PluginExecution> executions = new ArrayList<>();
        for (WasmManifest.ExecutionDecl execution : manifest.executions()) {
            executions.add(new PluginExecution(name, execution.name(), execution.users(), execution.groups(),
                    execution.httpMethod(), "undefined", "", new ExecutionHook.Hosted(this::execute)));
        }
        return new LoadedPlugin(name, fileName, List.of(), executions, List.of(), realms);
    }

    private HostedOutcome<RealmResult> authenticate(String realm, List<String> credentials) {
        String username = credentials.isEmpty() ? null : credentials.get(0);
        String password = credentials.size() < 2 ? null : credentials.get(1);
        return realm(Hook.REALM_AUTHENTICATE, AbiCodec.realmAuthenticate(realm, username, password));
    }

    private HostedOutcome<RealmResult> bearer(String realm, List<String> credentials) {
        return realm(Hook.REALM_BEARER, AbiCodec.realmBearer(realm, credentials.isEmpty() ? null : credentials.get(0)));
    }

    private HostedOutcome<RealmResult> realm(Hook hook, JsonNode request) {
        return switch (call(hook, request)) {
            case StepLoop.Completed done when done.result() instanceof GuestResult.Realm realm -> {
                state.commit(done.replacement());
                yield new HostedOutcome.Answered<>(realm.result());
            }
            case StepLoop.Completed done -> new HostedOutcome.Failed<>("the module answered a realm hook with an "
                    + "execution result");
            case StepLoop.Failed failed -> new HostedOutcome.Failed<>(failed.reason());
        };
    }

    private HostedOutcome<ExecutionHook.Response> execute(ExecutionHook.Request request) {
        Optional<byte[]> body = readBody(request.body(), manifest.limits().maxStdinBytes());
        if (body.isEmpty()) {
            return new HostedOutcome.Failed<>("the request body is larger than " + manifest.limits().maxStdinBytes()
                    + " bytes or cannot be read");
        }
        AbiCodec.CallerView caller = new AbiCodec.CallerView(request.caller().username(), request.caller().admin(),
                request.caller().anonymous(), context.callerRealm());
        JsonNode json = AbiCodec.execution(request.name(), request.method().toUpperCase(Locale.ROOT),
                params(request.params()), headers(context.headers()), body.get(), caller);
        return switch (call(Hook.EXECUTION, json)) {
            case StepLoop.Completed done when done.result() instanceof GuestResult.Execution execution -> {
                state.commit(done.replacement());
                yield new HostedOutcome.Answered<>(execution.response());
            }
            case StepLoop.Completed done -> new HostedOutcome.Failed<>("the module answered an execution with a "
                    + "realm result");
            case StepLoop.Failed failed -> new HostedOutcome.Failed<>(failed.reason());
        };
    }

    private StepLoop.Outcome call(Hook hook, JsonNode request) {
        StepRules.CallSpec spec = new StepRules.CallSpec(hook, request, manifest.config(), state.current(),
                nowSeconds.getAsLong(), manifest.limits(), manifest.httpHosts());
        return StepLoop.run(spec, runner, performer);
    }

    /** @return request parameters as lists of strings. Pure. */
    static Map<String, List<String>> params(Map<String, ?> params) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            List<String> values = new ArrayList<>();
            if (value instanceof Iterable<?> items) {
                items.forEach(item -> values.add(String.valueOf(item)));
            } else if (value instanceof Object[] items) {
                for (Object item : items) {
                    values.add(String.valueOf(item));
                }
            } else if (value != null) {
                values.add(String.valueOf(value));
            }
            result.put(String.valueOf(key), List.copyOf(values));
        });
        return result;
    }

    /** @return {@code headers} with lower-case names, keeping only {@link #PASSED_REQUEST_HEADERS}. Pure. */
    static Map<String, List<String>> headers(Map<String, List<String>> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (PASSED_REQUEST_HEADERS.contains(lower)) {
                result.computeIfAbsent(lower, k -> new ArrayList<>()).addAll(values);
            }
        });
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        result.forEach((key, values) -> frozen.put(key, List.copyOf(values)));
        return frozen;
    }

    private static Optional<byte[]> readBody(Object body, int max) {
        if (!(body instanceof ResourceStreamHandle handle)) {
            return Optional.of(new byte[0]);
        }
        try {
            InputStream in = handle.getInputStream();
            if (in == null) {
                return Optional.of(new byte[0]);
            }
            byte[] bytes = in.readNBytes(max + 1);
            return bytes.length > max ? Optional.empty() : Optional.of(bytes);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
