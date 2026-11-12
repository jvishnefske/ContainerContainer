/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The backend half of the plugin HTTP API shell: gathers the facts {@link PluginApiGate} decides on, and calls a
 * handler behind the plugin boundary.
 *
 * <p>{@link #authorize} runs before any plugin code: it resolves the repository, asks {@link AuthorizationService}
 * for the method's permission on the repository root as the current (request) user, and returns the gate's
 * decision. Only a {@link PluginApiGate.Decision.Dispatch} lets the caller go on to {@link #invoke}, which turns a
 * handler failure or a {@code null} response into a generic 500 and logs the details with the type and plugin
 * name.
 */
@Component
public class PluginApiService {
    private static final Logger log = LoggerFactory.getLogger(PluginApiService.class);

    /** Body of the 500 sent when a handler fails; deliberately free of failure details. */
    static final String FAILURE_MESSAGE = "The repository's package type failed to handle the request";

    private final InternalRepositoryService repositoryService;
    private final PluginPackageTypeResolver resolver;
    private final PluginPackageTypeRegistry registry;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;

    /** The Artifactory services are injected lazily so this bean adds no ordering constraint to their startup. */
    @Autowired
    public PluginApiService(@Lazy InternalRepositoryService repositoryService, PluginPackageTypeResolver resolver,
            PluginPackageTypeRegistry registry, @Lazy AuthorizationService authorizationService,
            @Lazy SecurityService securityService) {
        this.repositoryService = repositoryService;
        this.resolver = resolver;
        this.registry = registry;
        this.authorizationService = authorizationService;
        this.securityService = securityService;
    }

    /** The current caller, as the request's security context has it. */
    public record CallerFacts(String username, boolean admin, boolean anonymous) {
    }

    /** @return the current caller; a missing username is reported as {@code anonymous} */
    public CallerFacts caller() {
        String username = securityService.currentUsername();
        boolean anonymous = username == null || securityService.isAnonymous();
        return new CallerFacts(username == null ? "anonymous" : username, securityService.isAdmin(), anonymous);
    }

    /**
     * Decides whether the current caller's {@code method} request to repository {@code repoKey} reaches the
     * handler. Runs no plugin code.
     */
    public PluginApiGate.Decision authorize(String method, String repoKey) {
        Repo<?, ?> repo = repoKey == null || repoKey.isBlank() ? null : repositoryService.repositoryByKey(repoKey);
        boolean local = repo != null && repo.isLocal() && !repo.isCache();
        Optional<PluginPackageType> type = local ? resolver.routableTypeOf(repo) : Optional.empty();
        boolean blackedOut = repo instanceof RealRepo<?, ?> real && real.isBlackedOut();
        PluginApiGate.Target target = PluginApiGate.Target.of(String.valueOf(repoKey), local, type, blackedOut);
        RepoPath root = local ? RepoPathFactory.create(repoKey, "") : null;
        return PluginApiGate.decide(method, target, permission -> granted(permission, root),
                caller().anonymous());
    }

    /** Decides the answer to the registry ping for the current caller. */
    public PluginApiGate.Ping ping() {
        boolean apiTypeRegistered = registry.all().stream().anyMatch(type -> type.api().isPresent());
        return PluginApiGate.ping(apiTypeRegistered, caller().anonymous(), securityService.isAnonAccessEnabled());
    }

    /**
     * Calls the dispatched handler. Never throws for a handler failure: an exception, assertion or linkage error,
     * stack overflow or a {@code null} response becomes a 500 with {@link #FAILURE_MESSAGE}, logged with the type
     * and plugin name. Other virtual machine errors propagate.
     */
    public PluginApiResponse invoke(PluginApiGate.Decision.Dispatch dispatch, PluginApiRequest request) {
        PluginPackageType type = dispatch.type();
        try {
            PluginApiResponse response = dispatch.handler().handle(request);
            if (response != null) {
                return response;
            }
            log.error("API handler of plugin package type '{}' (plugin '{}') returned no response for {} {}/{}",
                    type.name(), type.pluginName(), request.method(), request.repoKey(), request.path());
        } catch (RuntimeException | LinkageError | AssertionError | StackOverflowError e) {
            log.error("API handler of plugin package type '{}' (plugin '{}') failed for {} {}/{}: {}",
                    type.name(), type.pluginName(), request.method(), request.repoKey(), request.path(),
                    e.toString(), e);
        }
        return failure();
    }

    /** The generic 500 a failed handler is answered with. */
    public static PluginApiResponse failure() {
        return PluginApiResponse.text(500, "text/plain", FAILURE_MESSAGE);
    }

    private boolean granted(PluginApiGate.Permission permission, RepoPath root) {
        if (root == null) {
            return false;
        }
        return switch (permission) {
            case READ -> authorizationService.canRead(root);
            case DEPLOY -> authorizationService.canDeploy(root);
            case DELETE -> authorizationService.canDelete(root);
        };
    }
}
