/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.api.security.SecurityService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A2: Basic credentials authenticate {@code /api/docker/} requests once a plugin HTTP API exists. */
class PluginApiAuthenticationAdapterTest {

    @Test
    void acceptanceRule() {
        assertTrue(PluginApiAuthenticationAdapter.acceptsBasic(true, true, true, false, true, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(false, true, true, false, true, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(true, false, true, false, true, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(true, true, false, false, true, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(true, true, true, true, true, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(true, true, true, false, false, false, false));
        assertFalse(PluginApiAuthenticationAdapter.acceptsBasic(true, true, true, false, true, false, true));
        assertTrue(PluginApiAuthenticationAdapter.acceptsBasic(true, true, true, false, true, true, true));
    }

    @Test
    void onlyTheDockerEndpointUnderTheContextPathIsHandled() {
        assertTrue(PluginApiAuthenticationAdapter.isDockerEndpoint("/artifactory/api/docker/r/v2/x", "/artifactory"));
        assertTrue(PluginApiAuthenticationAdapter.isDockerEndpoint("/api/docker/v2/", ""));
        assertTrue(PluginApiAuthenticationAdapter.isDockerEndpoint("/api/docker/v2/", null));
        assertFalse(PluginApiAuthenticationAdapter.isDockerEndpoint("/artifactory/api/pkg/r/x", "/artifactory"));
        assertFalse(PluginApiAuthenticationAdapter.isDockerEndpoint("/artifactory/api/dockerish", "/artifactory"));
        assertFalse(PluginApiAuthenticationAdapter.isDockerEndpoint(null, "/artifactory"));
    }

    @Test
    void withoutAnApiTypeOrOutsideTheEndpointNothingIsHandledAndSecurityIsNotConsulted() {
        PluginPackageTypeRegistry registry = mock(PluginPackageTypeRegistry.class);
        AtomicInteger securityLookups = new AtomicInteger();
        PluginApiAuthenticationAdapter adapter = new PluginApiAuthenticationAdapter(registry, () -> {
            securityLookups.incrementAndGet();
            return mock(SecurityService.class);
        });
        HttpServletRequest docker = request("/artifactory/api/docker/r/v2/");
        when(registry.all()).thenReturn(List.of(PluginPackageType.builder("plain", "p").build()));
        assertFalse(adapter.getContext(docker).isAuthenticationAllowed());
        when(registry.all()).thenReturn(List.of(PluginPackageType.builder("oci", "p")
                .api(r -> PluginApiResponse.status(200)).build()));
        assertFalse(adapter.getContext(request("/artifactory/api/npm/r/x")).isAuthenticationAllowed());
        assertEquals(0, securityLookups.get());
    }

    @Test
    void dockerRequestWithoutBasicHeaderIsNotHandled() {
        PluginPackageTypeRegistry registry = mock(PluginPackageTypeRegistry.class);
        when(registry.all()).thenReturn(List.of(PluginPackageType.builder("oci", "p")
                .api(r -> PluginApiResponse.status(200)).build()));
        PluginApiAuthenticationAdapter adapter = new PluginApiAuthenticationAdapter(registry,
                () -> mock(SecurityService.class));
        assertFalse(adapter.getContext(request("/artifactory/api/docker/r/v2/")).isAuthenticationAllowed());
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn("/artifactory");
        return request;
    }
}
