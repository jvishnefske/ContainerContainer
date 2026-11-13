/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.webapp.servlet.authentication.interceptor.anonymous.AnonymousInitialAdminClaimInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests {@link InitialSetupFilter} (IA3) and {@link AnonymousInitialAdminClaimInterceptor}. */
class InitialSetupFilterTest {

    private AdminBootstrapService service;
    private AtomicReference<Optional<AdminBootstrapService>> available;
    private InitialSetupFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        service = mock(AdminBootstrapService.class);
        available = new AtomicReference<>(Optional.of(service));
        filter = new InitialSetupFilter(() -> available.get());
        chain = mock(FilterChain.class);
    }

    private static HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory" + path);
        return request;
    }

    /** A response capturing status, content type and body. */
    private static final class Captured {
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        Captured() throws Exception {
            when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }

                @Override
                public void write(int b) {
                    body.write(b);
                }
            });
        }
    }

    private void assertBlocked(String method, String path) throws Exception {
        HttpServletRequest request = request(method, path);
        Captured captured = new Captured();
        filter.doFilter(request, captured.response, chain);
        verify(chain, never()).doFilter(request, captured.response);
        verify(captured.response).setStatus(503);
        verify(captured.response).setContentType("text/plain;charset=UTF-8");
        assertEquals("initial setup required", captured.body.toString(StandardCharsets.UTF_8));
    }

    private void assertPassed(String method, String path) throws Exception {
        HttpServletRequest request = request(method, path);
        HttpServletResponse response = mock(HttpServletResponse.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(503);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/repositories",
            "GET,/v2/",
            "GET,/api/docker/oci/v2/library/busybox/manifests/latest",
            "POST,/api/plugins/execute/echo",
            "GET,/webapp/",
            "GET,/ui/api/v1/ui/auth/screen/footer",
            "GET,/libs-release-local/a/b.jar",
            "POST,/api/security/token",
            "GET,/api/setup/initial-admin",
            "POST,/api/system/ping",
            "GET,/api/system/ping/",
            "GET,/api/system/ping;x=y",
            "POST,/api/setup/initial-admin/",
            "GET,/api/v1/system/preStop",
    })
    void blocksEverythingElseBeforeTheClaim(String method, String path) throws Exception {
        assertBlocked(method, path);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/system/ping",
            "GET,/api/v1/system/readiness",
            "GET,/api/v1/system/liveness",
            "POST,/api/setup/initial-admin",
    })
    void letsPingProbesAndTheClaimThroughBeforeTheClaim(String method, String path) throws Exception {
        assertPassed(method, path);
    }

    @Test
    void passesEverythingThroughAfterTheClaimAndStopsAsking() throws Exception {
        when(service.isClaimed()).thenReturn(true);
        assertPassed("GET", "/api/repositories");
        assertPassed("GET", "/v2/");
        assertPassed("POST", "/api/plugins/execute/echo");
        verify(service).isClaimed();
    }

    @Test
    void opensWhenTheClaimHappensWhileRunning() throws Exception {
        assertBlocked("GET", "/api/repositories");
        when(service.isClaimed()).thenReturn(true);
        assertPassed("GET", "/api/repositories");
    }

    @Test
    void failsClosedWhenTheServiceIsMissing() throws Exception {
        available.set(Optional.empty());
        assertBlocked("GET", "/api/repositories");
        assertBlocked("POST", "/api/setup/initial-admin");
        assertPassed("GET", "/api/system/ping");
        assertPassed("GET", "/api/v1/system/readiness");
    }

    @Test
    void findsTheServiceLaterOnceTheContextPublishesIt() throws Exception {
        available.set(Optional.empty());
        assertBlocked("GET", "/api/repositories");
        AtomicInteger lookups = new AtomicInteger();
        available.set(Optional.of(service));
        filter = new InitialSetupFilter(() -> {
            lookups.incrementAndGet();
            return available.get();
        });
        assertBlocked("GET", "/api/repositories");
        assertBlocked("GET", "/api/repositories");
        assertEquals(1, lookups.get(), "the service is cached once found");
    }

    @Test
    void stockAccessModePassesEverythingAsAnExplicitOptOut() throws Exception {
        filter = new InitialSetupFilter(Optional::empty, () -> Optional.of(false));
        assertPassed("GET", "/api/repositories");
        assertPassed("PUT", "/libs/a.jar");
    }

    @Test
    void embeddedOrUnknownModeStaysClosedWithoutService() throws Exception {
        filter = new InitialSetupFilter(Optional::empty, () -> Optional.of(true));
        assertBlocked("GET", "/api/repositories");
        filter = new InitialSetupFilter(Optional::empty, Optional::empty);
        assertBlocked("GET", "/api/repositories");
        assertPassed("GET", "/api/system/ping");
    }

    @Test
    void failsClosedWhenTheContextHasNoService() throws Exception {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beansForType(AdminBootstrapService.class)).thenReturn(Map.of());
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        FilterConfig config = mock(FilterConfig.class);
        when(config.getServletContext()).thenReturn(servletContext);
        filter = new InitialSetupFilter();
        filter.init(config);
        assertBlocked("GET", "/api/repositories");
        assertBlocked("POST", "/api/setup/initial-admin");
        assertPassed("GET", "/api/system/ping");
    }

    @Test
    void findsTheServiceInTheContext() throws Exception {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beansForType(AdminBootstrapService.class)).thenReturn(Map.of("bootstrap", service));
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        FilterConfig config = mock(FilterConfig.class);
        when(config.getServletContext()).thenReturn(servletContext);
        filter = new InitialSetupFilter();
        filter.init(config);
        assertPassed("POST", "/api/setup/initial-admin");
        when(service.isClaimed()).thenReturn(true);
        assertPassed("GET", "/api/repositories");
    }

    @Test
    void anonymousInterceptorAcceptsOnlyTheClaim() {
        AnonymousInitialAdminClaimInterceptor interceptor = new AnonymousInitialAdminClaimInterceptor();
        assertTrue(interceptor.accept(request("POST", "/api/setup/initial-admin")));
        assertFalse(interceptor.accept(request("GET", "/api/setup/initial-admin")));
        assertFalse(interceptor.accept(request("POST", "/api/setup/initial-admin/x")));
        assertFalse(interceptor.accept(request("POST", "/api/security/token")));
    }
}
