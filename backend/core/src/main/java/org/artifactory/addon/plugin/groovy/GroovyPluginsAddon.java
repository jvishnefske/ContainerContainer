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
import org.artifactory.addon.plugin.PluginInfo;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.build.staging.BuildStagingStrategy;
import org.artifactory.request.Request;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.schedule.ArtifactoryExecutorService;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.spring.ContextReadinessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The Spring shell of the Groovy plugin engine and the primary {@link PluginsAddon} of the fork, replacing
 * the no-op {@code PluginsAddonDefaultImpl} through {@link Primary}.
 *
 * <p>It loads plugins once the context is ready ({@link #onContextReady()}), reloads them on request, and
 * forwards {@link #execPluginActions} from the existing call sites to {@link GroovyPluginEngine#dispatch}.
 * With no plugin files present every hook lookup is empty, so behaviour matches stock OSS.
 *
 * <p>Collaborators are resolved lazily through {@link ObjectProvider} when the bindings are created, which
 * avoids construction cycles with the repository services that themselves call this addon. The package type
 * registry is optional so the engine still starts in a build without it; plugins that declare package types
 * then fail to load with an explanatory status.
 *
 * <p>Deploying, downloading and deleting plugin files over REST, staging strategies and build promotions
 * remain unsupported and throw {@link UnsupportedOperationException} as in stock OSS.
 */
@Component
@Primary
public class GroovyPluginsAddon implements PluginsAddon, ContextReadinessListener {

    private static final Logger log = LoggerFactory.getLogger(GroovyPluginsAddon.class);
    private static final String PLUGIN_LOGGER_PREFIX = "org.artifactory.addon.plugin.groovy.user.";

    private final GroovyPluginEngine engine;
    private final Supplier<Caller> caller;
    private final Executor asyncExecutor;

    /** Spring constructor. */
    @Autowired
    public GroovyPluginsAddon(ObjectProvider<RepositoryService> repositoryService,
            ObjectProvider<PropertiesService> propertiesService,
            ObjectProvider<SecurityService> securityService,
            ObjectProvider<UserGroupService> userGroupService,
            ObjectProvider<PluginPackageTypeRegistry> registry,
            ObjectProvider<ArtifactoryExecutorService> executorService) {
        this(new GroovyPluginEngine(
                        GroovyPluginsAddon::homePluginsDir,
                        new GroovyPluginCompiler(GroovyPluginsAddon.class.getClassLoader(),
                                HookFailureReporter.logging()),
                        new LazyRegistry(registry),
                        pluginName -> bindings(pluginName,
                                new PluginRepositories(repositoryService.getObject(), propertiesService.getObject()),
                                new PluginSecurity(securityService.getObject())),
                        HookFailureReporter.logging()),
                () -> currentCaller(securityService.getObject(), userGroupService.getObject()),
                command -> {
                    ArtifactoryExecutorService executor = executorService.getIfUnique();
                    if (executor == null) {
                        command.run();
                    } else {
                        executor.execute(command);
                    }
                });
    }

    /**
     * Wires the addon to an explicit engine; used by tests.
     *
     * @param caller        supplies the identity of the current caller for execution authorization
     * @param asyncExecutor runs asynchronous executions
     */
    public GroovyPluginsAddon(GroovyPluginEngine engine, Supplier<Caller> caller, Executor asyncExecutor) {
        this.engine = engine;
        this.caller = caller;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Builds the binding variables of a plugin script. Pure.
     *
     * @return {@code log}, {@code repositories} and {@code security}
     */
    public static Map<String, Object> bindings(String pluginName, Object repositories, Object security) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("log", LoggerFactory.getLogger(PLUGIN_LOGGER_PREFIX + pluginName));
        bindings.put("repositories", repositories);
        bindings.put("security", security);
        return bindings;
    }

    /** @return the engine, for collaborators in this package and tests */
    GroovyPluginEngine engine() {
        return engine;
    }

    @Override
    public void onContextReady() {
        try {
            PluginSnapshot loaded = engine.reload();
            log.info("User plugins loaded:\n{}", PluginReports.reloadMessage(loaded));
        } catch (RuntimeException e) {
            // Startup must never fail because of plugins (G1).
            log.error("Failed to load user plugins: {}", e.getMessage(), e);
        }
    }

    @Override
    public <C> void execPluginActions(Class<? extends PluginAction> type, C context, Object... args) {
        engine.dispatch(type, context, args);
    }

    @Override
    public boolean isPluginSupported(Class<? extends PluginAction> type) {
        return engine.hasHooks(type);
    }

    @Override
    public ResponseCtx execute(String executionName, String method, Map params, @Nullable ResourceStreamHandle body,
            boolean async) {
        Caller current = caller.get();
        @SuppressWarnings("unchecked")
        Map<String, ?> typedParams = params == null ? Map.of() : (Map<String, ?>) params;
        if (!async) {
            return engine.execute(executionName, method, typedParams, body, current);
        }
        Optional<ResponseCtx> refusal = engine.refuse(executionName, method, current);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        asyncExecutor.execute(() -> engine.execute(executionName, method, typedParams, body, current));
        ResponseCtx accepted = new ResponseCtx();
        accepted.setStatus(202);
        return accepted;
    }

    @Override
    public ResponseCtx reloadPlugins(boolean wait, boolean internal) {
        PluginSnapshot loaded = engine.reload();
        ResponseCtx ctx = new ResponseCtx();
        ctx.setStatus(200);
        ctx.setMessage(PluginReports.reloadMessage(loaded));
        return ctx;
    }

    @Override
    public Map<String, List<PluginInfo>> getPluginInfo(@Nullable String pluginType) {
        Caller current = caller.get();
        return PluginReports.pluginInfo(engine.snapshot(), pluginType,
                execution -> ExecutionAuthorizer.isPermitted(execution, current));
    }

    @Override
    public Map<String, String> getPluginsStatus() {
        return PluginReports.statuses(engine.snapshot());
    }

    @Override
    @Nonnull
    public Map<String, Map<String, String>> getPluginsInfoSupportBundleDumpAsMap() {
        return PluginReports.describe(engine.snapshot());
    }

    @Override
    public String getPluginsInfoSupportBundleDump() {
        return PluginReports.reloadMessage(engine.snapshot());
    }

    @Override
    public ResponseCtx getInstalledPluginsStatus() {
        ResponseCtx ctx = new ResponseCtx();
        if (!caller.get().admin()) {
            // The status names plugin files and carries their compile and load errors; admins only.
            ctx.setStatus(403);
            ctx.setMessage("Plugin status is available to administrators only");
            return ctx;
        }
        ctx.setStatus(200);
        ctx.setMessage(PluginReports.reloadMessage(engine.snapshot()));
        return ctx;
    }

    @Override
    public Map<String, String> getPluginsComplianceStatus() {
        return new HashMap<>();
    }

    @Override
    public Map<String, Long> getPluginsUsageStatus() {
        return new HashMap<>();
    }

    @Override
    public BuildStagingStrategy getStagingStrategy(String strategyName, String buildName, String buildRepo,
            Map params) {
        throw new UnsupportedOperationException("Build staging strategy plugins are not supported.");
    }

    @Override
    public ResponseCtx promote(String promotionName, String buildName, String buildNumber, String buildRepo,
            Map params) {
        throw new UnsupportedOperationException("Build promotion plugins are not supported.");
    }

    @Override
    public ResponseCtx deployPlugin(Reader pluginContent, String scriptName) {
        throw new UnsupportedOperationException("Deploying user plugins over REST is not supported; "
                + "copy the file to the plugins directory and reload.");
    }

    @Override
    public ResponseCtx downloadPlugin(String pluginName) {
        throw new UnsupportedOperationException("Downloading user plugins over REST is not supported.");
    }

    @Override
    public ResponseCtx deletePlugin(String pluginName) {
        throw new UnsupportedOperationException("Deleting user plugins over REST is not supported.");
    }

    @Override
    public void executeAdditiveRealmPlugins(Request servletRequest) {
        // Realms are out of scope for this engine.
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public void exportTo(ExportSettings settings) {
        // Plugin files live in the plugins directory, which the system export already covers.
    }

    @Override
    public void importFrom(ImportSettings settings) {
        // See exportTo.
    }

    private static Path homePluginsDir() {
        File dir = ContextHelper.get().getArtifactoryHome().getPluginsDir();
        return dir == null ? null : dir.toPath();
    }

    private static Caller currentCaller(SecurityService securityService, UserGroupService userGroupService) {
        Set<String> groups = new HashSet<>();
        try {
            UserInfo user = userGroupService.currentUser();
            if (user != null && user.getGroups() != null) {
                for (UserGroupInfo group : user.getGroups()) {
                    groups.add(group.getGroupName());
                }
            }
        } catch (RuntimeException e) {
            log.debug("Could not resolve groups of the current user: {}", e.getMessage());
        }
        String username = securityService.currentUsername();
        return new Caller(username == null ? "anonymous" : username, securityService.isAdmin(), groups);
    }

    /**
     * Resolves the package type registry on first use. When no registry bean exists, registering a plugin
     * with package types fails with a reason; a plugin without package types is unaffected.
     */
    private static final class LazyRegistry implements PluginPackageTypeRegistry {

        private final ObjectProvider<PluginPackageTypeRegistry> provider;

        LazyRegistry(ObjectProvider<PluginPackageTypeRegistry> provider) {
            this.provider = provider;
        }

        @Override
        public List<String> replacePluginTypes(String pluginName, List<PluginPackageType> types) {
            PluginPackageTypeRegistry registry = provider.getIfAvailable();
            if (registry != null) {
                return registry.replacePluginTypes(pluginName, types);
            }
            return types.isEmpty() ? List.of() : List.of("No plugin package type registry is available");
        }

        @Override
        public void removePluginTypes(String pluginName) {
            PluginPackageTypeRegistry registry = provider.getIfAvailable();
            if (registry != null) {
                registry.removePluginTypes(pluginName);
            }
        }

        @Override
        public Optional<PluginPackageType> find(String name) {
            PluginPackageTypeRegistry registry = provider.getIfAvailable();
            return registry == null ? Optional.empty() : registry.find(name);
        }

        @Override
        public Collection<PluginPackageType> all() {
            PluginPackageTypeRegistry registry = provider.getIfAvailable();
            return registry == null ? List.of() : registry.all();
        }
    }
}
