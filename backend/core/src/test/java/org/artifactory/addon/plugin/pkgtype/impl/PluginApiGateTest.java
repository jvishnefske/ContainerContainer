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
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiGate.Decision;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiGate.Permission;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiGate.Ping;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiGate.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A3: the pure permission and routing decisions in front of a plugin package type's HTTP API. */
class PluginApiGateTest {

    private static final PluginApiHandler HANDLER = request -> PluginApiResponse.status(200);
    private static final PluginPackageType TYPE =
            PluginPackageType.builder("oci", "registry").api(HANDLER).build();
    private static final Target API = new Target.Api("images", TYPE, HANDLER, false);

    @ParameterizedTest
    @CsvSource({"GET,READ", "HEAD,READ", "get,READ", "PUT,DEPLOY", "POST,DEPLOY", "PATCH,DEPLOY",
            "DELETE,DELETE"})
    void methodsMapToTheirPermission(String method, Permission permission) {
        assertEquals(Optional.of(permission), PluginApiGate.requiredPermission(method));
    }

    @Test
    void otherMethodsHaveNoPermissionAndAreRefusedWith405() {
        assertTrue(PluginApiGate.requiredPermission("OPTIONS").isEmpty());
        assertTrue(PluginApiGate.requiredPermission(null).isEmpty());
        Decision decision = PluginApiGate.decide("TRACE", API, permission -> true, false);
        assertEquals(405, assertInstanceOf(Decision.Refuse.class, decision).status());
    }

    @ParameterizedTest
    @CsvSource({"GET,READ", "HEAD,READ", "PUT,DEPLOY", "POST,DEPLOY", "PATCH,DEPLOY", "DELETE,DELETE"})
    void grantedPermissionDispatchesAndOnlyThatPermissionIsAsked(String method, Permission permission) {
        List<Permission> asked = new ArrayList<>();
        Decision decision = PluginApiGate.decide(method, API, p -> asked.add(p) && p == permission, false);
        Decision.Dispatch dispatch = assertInstanceOf(Decision.Dispatch.class, decision);
        assertSame(HANDLER, dispatch.handler());
        assertSame(TYPE, dispatch.type());
        assertEquals(List.of(permission), asked);
    }

    @Test
    void anonymousWithoutPermissionGetsTheBasicChallenge() {
        Decision.Refuse refuse = assertInstanceOf(Decision.Refuse.class,
                PluginApiGate.decide("GET", API, permission -> false, true));
        assertEquals(401, refuse.status());
        assertTrue(refuse.challenge());
        assertEquals("Basic realm=\"Artifactory Realm\"", PluginApiGate.BASIC_CHALLENGE);
    }

    @Test
    void authenticatedWithoutPermissionIsForbidden() {
        Decision.Refuse refuse = assertInstanceOf(Decision.Refuse.class,
                PluginApiGate.decide("DELETE", API, permission -> permission != Permission.DELETE, false));
        assertEquals(403, refuse.status());
        assertFalse(refuse.challenge());
        assertTrue(refuse.message().contains("delete"));
    }

    @Test
    void missingRepositoryAndRepositoryWithoutApiAre404AndAskNoPermission() {
        Set<Permission> asked = new java.util.HashSet<>();
        Decision missing = PluginApiGate.decide("GET", new Target.NoSuchRepo("nope"), p -> asked.add(p), true);
        Decision noApi = PluginApiGate.decide("PUT", new Target.NoApi("generic"), p -> asked.add(p), true);
        assertEquals(404, assertInstanceOf(Decision.Refuse.class, missing).status());
        Decision.Refuse noApiRefuse = assertInstanceOf(Decision.Refuse.class, noApi);
        assertEquals(404, noApiRefuse.status());
        assertTrue(noApiRefuse.message().contains("no plugin package type with an HTTP API"));
        assertTrue(asked.isEmpty());
    }

    @Test
    void blackedOutRepositoryIs404ForPermittedCallersAndStillChallengesAnonymous() {
        Target blackedOut = new Target.Api("images", TYPE, HANDLER, true);
        Decision.Refuse permitted = assertInstanceOf(Decision.Refuse.class,
                PluginApiGate.decide("GET", blackedOut, permission -> true, false));
        assertEquals(404, permitted.status());
        assertTrue(permitted.message().contains("blacked out"));
        Decision.Refuse anonymous = assertInstanceOf(Decision.Refuse.class,
                PluginApiGate.decide("GET", blackedOut, permission -> false, true));
        assertEquals(401, anonymous.status());
    }

    @Test
    void targetClassification() {
        PluginPackageType withoutApi = PluginPackageType.builder("plain", "p").build();
        assertInstanceOf(Target.NoSuchRepo.class, Target.of("x", false, Optional.of(TYPE), false));
        assertInstanceOf(Target.NoApi.class, Target.of("x", true, Optional.empty(), false));
        assertInstanceOf(Target.NoApi.class, Target.of("x", true, Optional.of(withoutApi), false));
        Target.Api api = assertInstanceOf(Target.Api.class, Target.of("x", true, Optional.of(TYPE), true));
        assertTrue(api.blackedOut());
        assertSame(HANDLER, api.handler());
    }

    @Test
    void pingDecision() {
        assertEquals(Ping.NOT_FOUND, PluginApiGate.ping(false, false, true));
        assertEquals(Ping.OK, PluginApiGate.ping(true, false, false));
        assertEquals(Ping.CHALLENGE, PluginApiGate.ping(true, true, false));
        assertEquals(Ping.OK, PluginApiGate.ping(true, true, true));
    }
}
