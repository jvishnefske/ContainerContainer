/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.setup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService.ClaimResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests {@link InitialAdminResource}: status codes, the identical refusal body, and the client address. */
class InitialAdminResourceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final String TOKEN = "setup-token-setup-token-setup-token";
    private static final String PASSWORD = "a-long-unusual-passphrase-42";
    private static final String BODY = "{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\"}";

    private AdminBootstrapService service;
    private InitialAdminResource resource;

    @BeforeEach
    void setUp() {
        service = mock(AdminBootstrapService.class);
        resource = new InitialAdminResource(() -> Optional.of(service), Clock.fixed(NOW, ZoneOffset.UTC));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
        resource.request = request;
    }

    private static InputStream body(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertResponse(Response response, int status, String entity) {
        assertEquals(status, response.getStatus());
        assertEquals(entity, response.getEntity());
        assertEquals("application/json", response.getMediaType().toString());
    }

    @Test
    void successfulClaimAnswers200() {
        when(service.claim(TOKEN, PASSWORD, "192.0.2.10", NOW)).thenReturn(ClaimResult.CLAIMED);
        assertResponse(resource.claim(body(BODY)), 200, "{\"status\":\"claimed\"}");
    }

    @Test
    void rateLimitedClaimAnswers429() {
        when(service.claim(TOKEN, PASSWORD, "192.0.2.10", NOW)).thenReturn(ClaimResult.RATE_LIMITED);
        assertResponse(resource.claim(body(BODY)), 429, InitialAdminResource.RATE_LIMITED_BODY);
    }

    @Test
    void refusedClaimAnswersTheGeneric400() {
        when(service.claim(TOKEN, PASSWORD, "192.0.2.10", NOW)).thenReturn(ClaimResult.REFUSED);
        assertResponse(resource.claim(body(BODY)), 400, InitialAdminResource.REFUSED_BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[]",
            "\"token\"",
            "{}",
            "{\"token\":\"" + TOKEN + "\"}",
            "{\"password\":\"" + PASSWORD + "\"}",
            "{\"token\":1,\"password\":\"" + PASSWORD + "\"}",
            "{\"token\":\"" + TOKEN + "\",\"password\":null}",
            "{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\"",
    })
    void malformedBodiesAnswerTheSameGeneric400WithoutReachingTheService(String text) {
        assertResponse(resource.claim(body(text)), 400, InitialAdminResource.REFUSED_BODY);
        verify(service, never()).claim(any(), any(), any(), any());
    }

    @Test
    void oversizedAndMissingBodiesAnswerTheGeneric400() {
        String huge = "{\"token\":\"" + "x".repeat(InitialAdminResource.MAX_BODY_BYTES) + "\",\"password\":\"p\"}";
        assertResponse(resource.claim(body(huge)), 400, InitialAdminResource.REFUSED_BODY);
        assertResponse(resource.claim(null), 400, InitialAdminResource.REFUSED_BODY);
        verify(service, never()).claim(any(), any(), any(), any());
    }

    @Test
    void forwardedClientAddressIsPassedOn() {
        when(resource.request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7, 10.0.0.1");
        when(service.claim(TOKEN, PASSWORD, "198.51.100.7", NOW)).thenReturn(ClaimResult.CLAIMED);
        assertEquals(200, resource.claim(body(BODY)).getStatus());
    }

    @Test
    void missingServiceAnswers503() {
        resource = new InitialAdminResource(Optional::empty, Clock.fixed(NOW, ZoneOffset.UTC));
        assertResponse(resource.claim(body(BODY)), 503, InitialAdminResource.UNAVAILABLE_BODY);
    }
}
