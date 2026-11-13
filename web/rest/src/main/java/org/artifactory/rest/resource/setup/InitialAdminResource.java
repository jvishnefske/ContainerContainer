/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.setup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService.ClaimResult;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code POST /api/setup/initial-admin} with the JSON body {@code {"token": "...", "password": "..."}} claims an
 * unclaimed server (IA4) through {@link AdminBootstrapService#claim}.
 * <p>
 * Answers: 200 {@value #CLAIMED_BODY} on success; 429 {@value #RATE_LIMITED_BODY} when rate limited; and 400 with
 * the one body {@value #REFUSED_BODY} for every other failure, including a missing, oversized or malformed body,
 * a wrong, reused or expired token, a password the policy rejects and an already claimed server. If the bootstrap
 * service is not available (embedded access not wired, or the context not ready), it answers 503
 * {@value #UNAVAILABLE_BODY}.
 * <p>
 * The endpoint is anonymous: the setup filter lets it through while unclaimed, and the anonymous interceptor
 * {@code AnonymousInitialAdminClaimInterceptor} lets it pass {@code AccessFilter} even when anonymous access is off.
 * The client address comes from {@link HttpUtils#getRemoteClientAddress(HttpServletRequest)}, which honours
 * {@code X-Forwarded-For}; the global rate limit bounds what a client can gain by spoofing it.
 */
@Component
@Path(InitialAdminResource.PATH_ROOT)
@PermitAll
public class InitialAdminResource {

    /** The resource root under {@code /api}. */
    public static final String PATH_ROOT = "setup";
    /** The claim sub-path. */
    public static final String PATH_INITIAL_ADMIN = "initial-admin";
    /** The body of a successful claim. */
    public static final String CLAIMED_BODY = "{\"status\":\"claimed\"}";
    /** The body of a rate-limited claim. */
    public static final String RATE_LIMITED_BODY = "{\"status\":\"rate_limited\"}";
    /** The one body of every refused claim. */
    public static final String REFUSED_BODY = "{\"status\":\"refused\"}";
    /** The body when the bootstrap service is not available. */
    public static final String UNAVAILABLE_BODY = "{\"status\":\"unavailable\"}";
    /** The largest accepted request body, in bytes. */
    public static final int MAX_BODY_BYTES = 8 * 1024;

    private static final Logger log = LoggerFactory.getLogger(InitialAdminResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOO_MANY_REQUESTS = 429;

    private final Supplier<Optional<AdminBootstrapService>> service;
    private final Clock clock;

    @Context
    HttpServletRequest request;

    /** Creates the resource that looks the service up in the current Artifactory context on each request. */
    public InitialAdminResource() {
        this(() -> AdminBootstrapService.lookup(ContextHelper.get()), Clock.systemUTC());
    }

    /**
     * Creates the resource with an explicit service lookup and clock, for tests.
     *
     * @param service supplies the bootstrap service, or empty if unavailable
     * @param clock   the clock of claim attempts
     */
    InitialAdminResource(Supplier<Optional<AdminBootstrapService>> service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /**
     * Claims the server.
     *
     * @param body the request body
     * @return the response, see the class description
     */
    @POST
    @Path(PATH_INITIAL_ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response claim(InputStream body) {
        Optional<AdminBootstrapService> bootstrap = service.get();
        if (bootstrap.isEmpty()) {
            return json(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), UNAVAILABLE_BODY);
        }
        Optional<ClaimRequest> parsed = parse(body);
        if (parsed.isEmpty()) {
            log.debug("Initial admin claim refused: malformed request body");
            return json(Response.Status.BAD_REQUEST.getStatusCode(), REFUSED_BODY);
        }
        ClaimResult result = bootstrap.get().claim(parsed.get().token(), parsed.get().password(), clientAddress(),
                clock.instant());
        return switch (result) {
            case CLAIMED -> json(Response.Status.OK.getStatusCode(), CLAIMED_BODY);
            case RATE_LIMITED -> json(TOO_MANY_REQUESTS, RATE_LIMITED_BODY);
            case REFUSED -> json(Response.Status.BAD_REQUEST.getStatusCode(), REFUSED_BODY);
        };
    }

    /**
     * The parsed request body.
     *
     * @param token    the setup token
     * @param password the chosen password
     */
    record ClaimRequest(String token, String password) {
    }

    /**
     * Parses the body: a JSON object whose {@code token} and {@code password} members are both strings.
     *
     * @param body the body, or {@code null}
     * @return the request, or empty if the body is missing, too large or malformed
     */
    static Optional<ClaimRequest> parse(InputStream body) {
        if (body == null) {
            return Optional.empty();
        }
        try {
            byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BODY_BYTES) {
                return Optional.empty();
            }
            JsonNode node = MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) {
                return Optional.empty();
            }
            JsonNode token = node.get("token");
            JsonNode password = node.get("password");
            if (token == null || password == null || !token.isTextual() || !password.isTextual()) {
                return Optional.empty();
            }
            return Optional.of(new ClaimRequest(token.textValue(), password.textValue()));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        } catch (IOException e) {
            log.debug("Could not read the initial admin claim body", e);
            return Optional.empty();
        }
    }

    private String clientAddress() {
        if (request == null) {
            return null;
        }
        try {
            return HttpUtils.getRemoteClientAddress(request);
        } catch (URISyntaxException e) {
            return request.getRemoteAddr();
        }
    }

    private static Response json(int status, String body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }
}
