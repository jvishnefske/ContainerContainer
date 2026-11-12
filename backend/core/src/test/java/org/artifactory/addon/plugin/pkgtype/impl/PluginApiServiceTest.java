/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginApiHandler;
import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A3, A5: the backend shell resolves, checks permissions as the caller before plugin code, and fails closed. */
class PluginApiServiceTest {

    private final InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
    private final PluginPackageTypeResolver resolver = mock(PluginPackageTypeResolver.class);
    private final PluginPackageTypeRegistry registry = mock(PluginPackageTypeRegistry.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final SecurityService securityService = mock(SecurityService.class);
    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final PluginApiHandler handler = request -> {
        handlerCalls.incrementAndGet();
        return PluginApiResponse.status(204);
    };
    private final PluginPackageType type = PluginPackageType.builder("oci", "registry").api(handler).build();
    @SuppressWarnings("rawtypes")
    private final LocalRepo repo = mock(LocalRepo.class);
    private PluginApiService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new PluginApiService(repositoryService, resolver, registry, authorizationService, securityService);
        when(repositoryService.repositoryByKey("images")).thenReturn(repo);
        when(repo.isLocal()).thenReturn(true);
        when(resolver.routableTypeOf(repo)).thenReturn(Optional.of(type));
        when(securityService.currentUsername()).thenReturn("alice");
    }

    @Test
    void permissionsAreCheckedOnTheRepositoryRootAsTheCaller() {
        when(authorizationService.canRead(RepoPathFactory.create("images", ""))).thenReturn(true);
        when(authorizationService.canDeploy(any())).thenReturn(false);
        assertInstanceOf(PluginApiGate.Decision.Dispatch.class, service.authorize("GET", "images"));
        assertEquals(403, assertInstanceOf(PluginApiGate.Decision.Refuse.class,
                service.authorize("PATCH", "images")).status());
        verify(authorizationService).canDeploy(RepoPathFactory.create("images", ""));
        assertEquals(403, assertInstanceOf(PluginApiGate.Decision.Refuse.class,
                service.authorize("DELETE", "images")).status());
        verify(authorizationService).canDelete(RepoPathFactory.create("images", ""));
        assertEquals(0, handlerCalls.get(), "authorize never runs plugin code");
    }

    @Test
    void anonymousCallerWithoutPermissionIsChallenged() {
        when(securityService.isAnonymous()).thenReturn(true);
        PluginApiGate.Decision.Refuse refuse =
                assertInstanceOf(PluginApiGate.Decision.Refuse.class, service.authorize("GET", "images"));
        assertEquals(401, refuse.status());
        assertEquals(true, refuse.challenge());
    }

    @Test
    void remoteMissingAndNonApiRepositoriesAre404WithoutPermissionChecks() {
        RemoteRepo<?, ?> remote = mock(RemoteRepo.class);
        when(repositoryService.repositoryByKey("remote")).thenReturn(remote);
        when(resolver.routableTypeOf(remote)).thenReturn(Optional.of(type));
        assertEquals(404, ((PluginApiGate.Decision.Refuse) service.authorize("GET", "remote")).status());
        assertEquals(404, ((PluginApiGate.Decision.Refuse) service.authorize("GET", "missing")).status());
        when(resolver.routableTypeOf(repo)).thenReturn(Optional.of(PluginPackageType.builder("x", "p").build()));
        assertEquals(404, ((PluginApiGate.Decision.Refuse) service.authorize("GET", "images")).status());
        verify(authorizationService, never()).canRead(any());
    }

    @Test
    void blackedOutRepositoryIsRefused() {
        when(repo.isBlackedOut()).thenReturn(true);
        when(authorizationService.canRead(any())).thenReturn(true);
        PluginApiGate.Decision.Refuse refuse =
                assertInstanceOf(PluginApiGate.Decision.Refuse.class, service.authorize("GET", "images"));
        assertEquals(404, refuse.status());
    }

    @Test
    void handlerExceptionAndNullBecomeAGeneric500() {
        PluginApiRequest request = PluginApiRequest.builder("GET", "images", "v2/").build();
        PluginApiGate.Decision.Dispatch throwing = new PluginApiGate.Decision.Dispatch(type, r -> {
            throw new IllegalStateException("secret detail");
        });
        PluginApiResponse failed = service.invoke(throwing, request);
        assertEquals(500, failed.status());
        String body = new String(((PluginApiResponse.Body.Bytes) failed.body()).content(), StandardCharsets.UTF_8);
        assertFalse(body.contains("secret"));
        assertEquals(PluginApiService.FAILURE_MESSAGE, body);
        assertEquals(500, service.invoke(new PluginApiGate.Decision.Dispatch(type, r -> null), request).status());
        PluginApiResponse ok = PluginApiResponse.status(202);
        assertSame(ok, service.invoke(new PluginApiGate.Decision.Dispatch(type, r -> ok), request));
    }

    @Test
    void pingNeedsARegisteredApiTypeAndChallengesAnonymousUnlessAnonymousAccessIsOn() {
        when(registry.all()).thenReturn(List.of(PluginPackageType.builder("x", "p").build()));
        assertEquals(PluginApiGate.Ping.NOT_FOUND, service.ping());
        when(registry.all()).thenReturn(List.of(type));
        assertEquals(PluginApiGate.Ping.OK, service.ping());
        when(securityService.isAnonymous()).thenReturn(true);
        assertEquals(PluginApiGate.Ping.CHALLENGE, service.ping());
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        assertEquals(PluginApiGate.Ping.OK, service.ping());
    }

    @Test
    void callerFacts() {
        when(securityService.isAdmin()).thenReturn(true);
        assertEquals(new PluginApiService.CallerFacts("alice", true, false), service.caller());
        when(securityService.currentUsername()).thenReturn(null);
        assertEquals(new PluginApiService.CallerFacts("anonymous", true, true), service.caller());
    }
}
