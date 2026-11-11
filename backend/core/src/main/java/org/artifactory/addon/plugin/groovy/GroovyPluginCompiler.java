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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.util.Map;
import java.util.Objects;

/**
 * Compiles and evaluates one plugin script, producing a {@link PluginLoadResult}. Never throws.
 *
 * <p>Each plugin gets its own {@link GroovyClassLoader}, child of the loader of this class, so scripts with
 * clashing class names cannot interfere and the classes of a replaced generation become collectable once
 * its closures are no longer referenced. Scripts extend {@link PluginScript} and get default imports for
 * {@code RepoPath}, {@code RepoPathFactory}, {@code ResponseCtx}, {@code CancelException} and
 * {@code UploadDecision}.
 */
public final class GroovyPluginCompiler {

    private final ClassLoader parent;
    private final HookFailureReporter reporter;

    /**
     * @param parent   class loader plugin classes are loaded under
     * @param reporter given to package type handlers for failures at request time
     */
    public GroovyPluginCompiler(ClassLoader parent, HookFailureReporter reporter) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    /**
     * Compiles {@code source} and runs its top level, which declares the plugin's hooks.
     *
     * @param bindings script binding variables ({@code log}, {@code repositories}, {@code security})
     * @return {@link PluginLoadResult.Loaded} or {@link PluginLoadResult.Failed} with the compiler or runtime error
     */
    public PluginLoadResult compile(PluginSource source, Map<String, Object> bindings) {
        PluginDefinitionBuilder definition = new PluginDefinitionBuilder(source.name(), source.fileName(), reporter);
        Binding binding = new Binding();
        bindings.forEach(binding::setVariable);
        binding.setVariable(PluginScript.DEFINITION_VARIABLE, definition);
        try {
            CompilerConfiguration config = configuration();
            GroovyShell shell = new GroovyShell(new GroovyClassLoader(parent, config), binding, config);
            Script script = shell.parse(source.text(), scriptClassName(source.name()) + PluginSource.EXTENSION);
            script.run();
            return new PluginLoadResult.Loaded(definition.build());
        } catch (Exception | LinkageError | AssertionError | StackOverflowError e) {
            definition.build();
            return new PluginLoadResult.Failed(source.name(), source.fileName(), HookInvoker.describe(e));
        }
    }

    /**
     * Turns a plugin name into a valid Groovy class name: {@code UserPlugin_} followed by the name with
     * characters other than ASCII letters, digits and {@code _} replaced by {@code _}. The prefix keeps
     * names such as {@code short} or {@code 1st} from becoming keywords or starting with a digit. Pure.
     */
    public static String scriptClassName(String pluginName) {
        StringBuilder result = new StringBuilder("UserPlugin_");
        for (int i = 0; i < pluginName.length(); i++) {
            char c = pluginName.charAt(i);
            boolean plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            result.append(plain ? c : '_');
        }
        return result.toString();
    }

    private static CompilerConfiguration configuration() {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(PluginScript.class.getName());
        ImportCustomizer imports = new ImportCustomizer();
        imports.addImports(
                "org.artifactory.repo.RepoPath",
                "org.artifactory.repo.RepoPathFactory",
                "org.artifactory.addon.plugin.ResponseCtx",
                "org.artifactory.exception.CancelException",
                "org.artifactory.addon.plugin.pkgtype.UploadDecision");
        config.addCompilationCustomizers(imports);
        return config;
    }
}
