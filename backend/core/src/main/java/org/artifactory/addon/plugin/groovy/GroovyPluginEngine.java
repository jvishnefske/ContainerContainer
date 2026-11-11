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

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.PluginAction;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.exception.CancelException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Loads plugins, holds the current {@link PluginSnapshot}, and dispatches hooks and executions against it.
 *
 * <p>Reload builds a whole new snapshot (scan, compile, register package types) and only then swaps it in
 * with one atomic write, so a request sees either the old generation or the new one, never a mix of hooks.
 * The {@link AtomicReference} is the engine's single shared handle: request threads read it concurrently
 * while reloads, serialized by {@code synchronized}, replace it.
 *
 * <p>Package types are pushed to the {@link PluginPackageTypeRegistry} during reload: every loaded plugin's
 * types replace its previous ones; plugins that disappeared or failed have theirs removed. A plugin whose
 * types the registry rejects is recorded as failed with the registry's reasons and contributes no hooks.
 * The registry and the snapshot are separate objects, so between the two updates a request may briefly see
 * new package types with old hooks.
 */
public final class GroovyPluginEngine {

    private final Supplier<Path> pluginsDir;
    private final GroovyPluginCompiler compiler;
    private final PluginPackageTypeRegistry registry;
    private final Function<String, Map<String, Object>> bindingsFor;
    private final HookFailureReporter reporter;
    private final AtomicReference<PluginSnapshot> current = new AtomicReference<>(PluginSnapshot.empty());

    /**
     * @param pluginsDir  supplies the plugins directory, read at every reload
     * @param compiler    compiles one plugin script
     * @param registry    receives the package types of loaded plugins
     * @param bindingsFor binding variables for a plugin, by plugin name
     * @param reporter    receives failures of hooks and executions
     */
    public GroovyPluginEngine(Supplier<Path> pluginsDir, GroovyPluginCompiler compiler,
            PluginPackageTypeRegistry registry, Function<String, Map<String, Object>> bindingsFor,
            HookFailureReporter reporter) {
        this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.bindingsFor = Objects.requireNonNull(bindingsFor, "bindingsFor");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    /** @return the current generation of plugins */
    public PluginSnapshot snapshot() {
        return current.get();
    }

    /**
     * Reloads every plugin from the plugins directory and replaces the current generation. Never throws:
     * per-file problems are recorded in the returned snapshot's results.
     */
    public synchronized PluginSnapshot reload() {
        PluginSources.Scan scan = PluginSources.scan(pluginsDir.get());
        List<PluginLoadResult> results = new ArrayList<>(scan.failures());
        for (PluginSource source : scan.sources()) {
            results.add(compiler.compile(source, bindingsFor.apply(source.name())));
        }
        results.sort((a, b) -> a.fileName().compareTo(b.fileName()));
        List<PluginLoadResult> registered = register(rejectDuplicateExecutions(results), current.get());
        PluginSnapshot next = new PluginSnapshot(registered);
        current.set(next);
        return next;
    }

    /**
     * Runs every hook bound to {@code type}, in firing order, with {@code context} as delegate. A failing
     * hook is reported with its plugin name and the remaining hooks still run.
     *
     * @throws CancelException when a hook cancels the operation; later hooks do not run
     */
    public void dispatch(Class<? extends PluginAction> type, Object context, Object... args) {
        for (PluginHook hook : current.get().hooksFor(type)) {
            switch (HookInvoker.invoke(hook.closure(), context, args)) {
                case HookOutcome.Completed completed -> {
                }
                case HookOutcome.Failed failed ->
                        reporter.report(hook.pluginName(), hook.qualifiedName(), failed.error());
                case HookOutcome.Cancelled cancelled -> throw cancelled.cancel();
            }
        }
    }

    /** @return whether any loaded plugin has a hook bound to {@code type} */
    public boolean hasHooks(Class<? extends PluginAction> type) {
        return !current.get().hooksFor(type).isEmpty();
    }

    /**
     * Checks whether {@code caller} may run the named execution, without running it.
     *
     * @return empty when it may, otherwise the response to send (404 unknown, 403 not permitted)
     */
    public Optional<ResponseCtx> refuse(String executionName, Caller caller) {
        return refuse(current.get().execution(executionName), executionName, caller);
    }

    private static Optional<ResponseCtx> refuse(Optional<PluginExecution> execution, String executionName,
            Caller caller) {
        if (execution.isEmpty()) {
            return Optional.of(response(404, "No plugin execution named '" + executionName + "' is loaded"));
        }
        if (!ExecutionAuthorizer.isPermitted(execution.get(), caller)) {
            return Optional.of(response(403, "User '" + caller.username()
                    + "' is not permitted to run plugin execution '" + executionName + "'"));
        }
        return Optional.empty();
    }

    /**
     * Runs a named execution with a fresh {@link ResponseCtx} as delegate, so the body sets {@code status}
     * and {@code message}. Never throws.
     *
     * @param params request parameters, passed as the closure's first argument
     * @param body   request body handle or {@code null}, passed as the second argument
     * @return the response: the execution's status (200 when unset) and message; 404/403 when refused;
     *         the cancel status for a {@link CancelException}; 500 for any other failure
     */
    public ResponseCtx execute(String executionName, Map<String, ?> params, Object body, Caller caller) {
        Optional<PluginExecution> found = current.get().execution(executionName);
        Optional<ResponseCtx> refusal = refuse(found, executionName, caller);
        if (refusal.isPresent() || found.isEmpty()) {
            return refusal.orElseGet(() -> response(404, executionName));
        }
        PluginExecution execution = found.get();
        ResponseCtx ctx = new ResponseCtx();
        switch (HookInvoker.invoke(execution.closure(), ctx, params, body)) {
            case HookOutcome.Completed completed -> {
                if (ctx.getStatus() == ResponseCtx.UNSET_STATUS) {
                    ctx.setStatus(200);
                }
            }
            case HookOutcome.Failed failed -> {
                reporter.report(execution.pluginName(), "executions." + executionName, failed.error());
                ctx.setStatus(500);
                ctx.setMessage("Plugin execution '" + executionName + "' of plugin '" + execution.pluginName()
                        + "' failed: " + HookInvoker.describe(failed.error()));
            }
            case HookOutcome.Cancelled cancelled -> {
                ctx.setStatus(cancelled.cancel().getErrorCode());
                ctx.setMessage(cancelled.cancel().getMessage());
            }
        }
        return ctx;
    }

    /**
     * Fails every loaded plugin that declares an execution name an earlier loaded plugin already
     * declared, so execution names stay unique. Pure.
     */
    static List<PluginLoadResult> rejectDuplicateExecutions(List<PluginLoadResult> results) {
        Map<String, String> owners = new HashMap<>();
        List<PluginLoadResult> checked = new ArrayList<>(results.size());
        for (PluginLoadResult result : results) {
            Optional<String> clash = Optional.empty();
            if (result instanceof PluginLoadResult.Loaded loaded) {
                for (PluginExecution execution : loaded.plugin().executions()) {
                    String owner = owners.get(execution.name());
                    if (owner != null) {
                        clash = Optional.of("Execution '" + execution.name() + "' is already declared by plugin '"
                                + owner + "'");
                        break;
                    }
                }
                if (clash.isEmpty()) {
                    loaded.plugin().executions().forEach(e -> owners.put(e.name(), loaded.name()));
                }
            }
            checked.add(clash.<PluginLoadResult>map(reason ->
                    new PluginLoadResult.Failed(result.name(), result.fileName(), reason)).orElse(result));
        }
        return checked;
    }

    /**
     * Pushes package types to the registry and turns registry rejections into failed results. A rejected
     * plugin is retried once after all others, so a type moved from one plugin to another in the same
     * reload is accepted regardless of file order.
     */
    private List<PluginLoadResult> register(List<PluginLoadResult> results, PluginSnapshot previous) {
        Set<String> loadedNow = new HashSet<>();
        for (PluginLoadResult result : results) {
            if (result instanceof PluginLoadResult.Loaded) {
                loadedNow.add(result.name());
            }
        }
        for (LoadedPlugin old : previous.loaded()) {
            if (!loadedNow.contains(old.name())) {
                registry.removePluginTypes(old.name());
            }
        }
        Map<String, List<String>> rejected = new LinkedHashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            for (PluginLoadResult result : results) {
                boolean due = pass == 0 || rejected.containsKey(result.name());
                if (result instanceof PluginLoadResult.Loaded loaded && due) {
                    List<String> errors = registry.replacePluginTypes(loaded.name(), loaded.plugin().packageTypes());
                    if (errors.isEmpty()) {
                        rejected.remove(loaded.name());
                    } else {
                        rejected.put(loaded.name(), List.copyOf(errors));
                    }
                }
            }
        }
        List<PluginLoadResult> registered = new ArrayList<>(results.size());
        for (PluginLoadResult result : results) {
            List<String> errors = rejected.get(result.name());
            if (result instanceof PluginLoadResult.Loaded && errors != null) {
                registry.removePluginTypes(result.name());
                registered.add(new PluginLoadResult.Failed(result.name(), result.fileName(),
                        "Package types rejected: " + String.join("; ", errors)));
            } else {
                registered.add(result);
            }
        }
        return registered;
    }

    private static ResponseCtx response(int status, String message) {
        ResponseCtx ctx = new ResponseCtx();
        ctx.setStatus(status);
        ctx.setMessage(message);
        return ctx;
    }
}
