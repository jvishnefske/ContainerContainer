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

/**
 * The Groovy user-plugin engine of the OSS fork.
 *
 * <p>Every {@code *.groovy} file in {@code ArtifactoryHome.getPluginsDir()} is read by
 * {@link org.artifactory.addon.plugin.groovy.PluginSources} and compiled by
 * {@link org.artifactory.addon.plugin.groovy.GroovyPluginCompiler} against the
 * {@link org.artifactory.addon.plugin.groovy.PluginScript} DSL base class. Evaluating a script yields an
 * immutable {@link org.artifactory.addon.plugin.groovy.LoadedPlugin} (its hooks, executions and package
 * types) or a {@link org.artifactory.addon.plugin.groovy.PluginLoadResult.Failed} recording why the file
 * could not be loaded. One file failing never prevents the others from loading.
 *
 * <p>{@link org.artifactory.addon.plugin.groovy.GroovyPluginEngine} holds the current
 * {@link org.artifactory.addon.plugin.groovy.PluginSnapshot} and swaps it atomically on reload, so request
 * threads always see one complete generation of plugins.
 * {@link org.artifactory.addon.plugin.groovy.GroovyPluginsAddon} is the Spring shell that exposes the engine
 * as the primary {@code PluginsAddon}.
 *
 * <p>Hooks bind to the existing {@code PluginAction} interfaces through
 * {@link org.artifactory.addon.plugin.groovy.HookCatalog}; the existing call sites in the download, upload
 * and storage code reach them through {@code PluginsAddon.execPluginActions}. A hook that throws is
 * reported with its plugin name and otherwise ignored, except for {@code CancelException}, which is the
 * explicit way for a hook to fail the operation (see {@link org.artifactory.addon.plugin.groovy.HookOutcome}).
 */
package org.artifactory.addon.plugin.groovy;
