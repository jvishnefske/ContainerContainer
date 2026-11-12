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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Test fixture: a plugins directory, a fake registry, a recording failure reporter, and an engine and
 * addon wired to them. Scripts see the production bindings ({@code log}, {@code repositories},
 * {@code security}, {@code realm}, {@code pluginDir}) plus a {@code calls} list they can append to so tests observe
 * hook invocations, and every other entry of {@code extraBindings}.
 */
final class PluginTestRig {

    /** One swallowed failure reported by the engine. */
    record Report(String pluginName, String what, Throwable error) {
    }

    final Path dir;
    final FakePackageTypeRegistry registry = new FakePackageTypeRegistry();
    final List<Report> reports = Collections.synchronizedList(new ArrayList<>());
    final List<Object> calls = Collections.synchronizedList(new ArrayList<>());
    final Map<String, Object> extraBindings = new HashMap<>();
    final GroovyPluginEngine engine;
    Caller caller = new Caller("admin", true, Set.of());
    final List<Runnable> asyncTasks = new ArrayList<>();
    final GroovyPluginsAddon addon;

    PluginTestRig(Path dir) {
        this.dir = dir;
        HookFailureReporter reporter = (plugin, what, error) -> reports.add(new Report(plugin, what, error));
        this.engine = new GroovyPluginEngine(() -> dir,
                new GroovyPluginCompiler(PluginTestRig.class.getClassLoader(), reporter),
                registry,
                name -> {
                    Map<String, Object> bindings = GroovyPluginsAddon.bindings(name,
                            extraBindings.get("repositories"), extraBindings.get("security"));
                    bindings.put("calls", calls);
                    extraBindings.forEach(bindings::putIfAbsent);
                    return bindings;
                },
                reporter);
        Executor executor = asyncTasks::add;
        this.addon = new GroovyPluginsAddon(engine, () -> caller, executor);
    }

    PluginTestRig write(String fileName, String text) throws IOException {
        Files.writeString(dir.resolve(fileName), text, StandardCharsets.UTF_8);
        return this;
    }

    PluginTestRig delete(String fileName) throws IOException {
        Files.delete(dir.resolve(fileName));
        return this;
    }

    PluginSnapshot reload() {
        return engine.reload();
    }
}
