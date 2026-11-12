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

import groovy.lang.Closure;
import groovy.lang.Script;

/**
 * Base class of every compiled plugin script; its public methods are the top-level DSL.
 *
 * <pre>
 * download { beforeDownloadRequest { request, repoPath -&gt; } ; altResponse { request, repoPath -&gt; status = 404 } }
 * storage  { beforeCreate { item -&gt; } ; afterCreate { item -&gt; } }
 * storage  { beforeDelete { item -&gt; } ; afterDelete { item -&gt; } }
 * upload   { beforeUploadRequest { request, repoPath -&gt; } }
 * executions { hello(users: ['bob'], groups: ['ops']) { params -&gt; message = 'hi'; status = 200 } }
 * packageType('mytype') { displayName 'My type'; layout 'simple-default'; download { r, p, resp -&gt; false } }
 * realms { corp(autoCreateUsers: true) { bearer { token -&gt; realm.notMine() } } }
 * </pre>
 *
 * <p>Hook names accepted in each block are those of {@link HookCatalog}; an unknown name fails the load of
 * the plugin. The script's binding holds {@code log}, {@code repositories}, {@code security}, {@code realm}
 * ({@link RealmResults}) and {@code pluginDir} (the plugins directory, a {@code java.nio.file.Path}), and the
 * {@link PluginDefinitionBuilder} under {@link #DEFINITION_VARIABLE}.
 */
public abstract class PluginScript extends Script {

    /** Binding variable holding the {@link PluginDefinitionBuilder} of the script being evaluated. */
    public static final String DEFINITION_VARIABLE = "__artifactoryPluginDefinition";

    /** Declares download hooks. */
    public void download(Closure<?> block) {
        hookBlock("download", block);
    }

    /** Declares storage hooks. */
    public void storage(Closure<?> block) {
        hookBlock("storage", block);
    }

    /** Declares upload hooks. */
    public void upload(Closure<?> block) {
        hookBlock("upload", block);
    }

    /** Declares named executions. */
    public void executions(Closure<?> block) {
        runDelegated(block, new ExecutionsDelegate(definition()));
    }

    /** Declares authentication realms; see {@link PluginRealm} and {@link RealmsDelegate}. */
    public void realms(Closure<?> block) {
        runDelegated(block, new RealmsDelegate(definition()));
    }

    /** Declares a plugin package type; see {@link PackageTypeDelegate} for the keywords of the block. */
    public void packageType(String name, Closure<?> block) {
        PluginDefinitionBuilder definition = definition();
        PackageTypeDelegate delegate = new PackageTypeDelegate(name, definition.pluginName(), definition.reporter());
        runDelegated(block, delegate);
        definition.addPackageType(delegate.build());
    }

    private void hookBlock(String block, Closure<?> body) {
        runDelegated(body, new HookBlockDelegate(block, definition()));
    }

    private PluginDefinitionBuilder definition() {
        return (PluginDefinitionBuilder) getBinding().getVariable(DEFINITION_VARIABLE);
    }

    static void runDelegated(Closure<?> block, Object delegate) {
        Closure<?> call = (Closure<?>) block.clone();
        call.setDelegate(delegate);
        call.setResolveStrategy(Closure.DELEGATE_FIRST);
        call.call();
    }
}
