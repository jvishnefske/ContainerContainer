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
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Closes the server until the initial administrator is claimed (IA3).
 * <p>
 * While {@link AdminBootstrapService#isClaimed()} is false, every request answers 503 with the fixed
 * {@code text/plain} body {@value #BLOCKED_BODY}, except these, matched exactly on the un-decoded path below the
 * context path:
 * <ul>
 *     <li>{@code GET} {@value #PING_PATH};</li>
 *     <li>the probes {@value #READINESS_PATH} and {@value #LIVENESS_PATH} (normally answered earlier by
 *     {@link ArtifactoryProbesFilter});</li>
 *     <li>{@code POST} {@value #CLAIM_PATH}, only while the bootstrap service is available.</li>
 * </ul>
 * That covers anonymous access, plugin executions and realms, the UI and the OCI {@code /v2} API. When the
 * bootstrap service cannot be found in the application context (not ready, or embedded access not wired), the
 * filter fails closed: only ping and the probes pass. The filter ignores the filter shortcut attribute, so no
 * request skips it.
 * <p>
 * <b>Stock-Access mode.</b> When {@code artifactory.access.embedded.enabled} is {@code false}, Artifactory uses an
 * external JFrog Access server, no bootstrap service exists and IA1 to IA8 do not apply: the filter latches open and
 * passes every request (an explicit opt-out; {@code AccessServiceImpl} logs a WARN at startup). While the mode
 * cannot be read yet (no context or home), the filter behaves as in embedded mode and fails closed.
 * <p>
 * Once a claim is observed the filter latches open and costs one volatile read per request. It sits in
 * {@code web.xml} between {@code ArtifactoryProbesFilter} and {@code AccessFilter}.
 */
public class InitialSetupFilter extends DelayedFilterBase {

    /** The body of every blocked response. */
    public static final String BLOCKED_BODY = "initial setup required";
    /** The ping endpoint. */
    public static final String PING_PATH = "/api/system/ping";
    /** The readiness probe. */
    public static final String READINESS_PATH = "/api/v1/system/readiness";
    /** The liveness probe. */
    public static final String LIVENESS_PATH = "/api/v1/system/liveness";
    /** The claim endpoint. */
    public static final String CLAIM_PATH = "/api/setup/initial-admin";

    private static final Logger log = LoggerFactory.getLogger(InitialSetupFilter.class);

    private final Supplier<Optional<AdminBootstrapService>> explicitLookup;
    private final Supplier<Optional<Boolean>> explicitEmbeddedMode;
    private volatile AdminBootstrapService service;
    private volatile boolean open;

    /** Creates the filter that finds the bootstrap service in the Artifactory application context. */
    public InitialSetupFilter() {
        this(null);
    }

    /**
     * Creates the filter with an explicit service lookup, for tests.
     *
     * @param lookup supplies the bootstrap service, or empty while unavailable; {@code null} for the context lookup
     */
    InitialSetupFilter(Supplier<Optional<AdminBootstrapService>> lookup) {
        this(lookup, null);
    }

    /**
     * Creates the filter with an explicit service lookup and an explicit embedded-mode answer, for tests.
     *
     * @param lookup       supplies the bootstrap service, or empty while unavailable; {@code null} for the context
     *                     lookup
     * @param embeddedMode supplies whether embedded access is enabled, or empty while unknown; {@code null} to read
     *                     {@code artifactory.access.embedded.enabled} from the context's home
     */
    InitialSetupFilter(Supplier<Optional<AdminBootstrapService>> lookup, Supplier<Optional<Boolean>> embeddedMode) {
        this.explicitLookup = lookup;
        this.explicitEmbeddedMode = embeddedMode;
    }

    @Override
    protected void initLater(FilterConfig config) {
        findService();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (open) {
            chain.doFilter(request, response);
            return;
        }
        if (embeddedMode().filter(Boolean.FALSE::equals).isPresent()) {
            open = true;
            log.warn("Embedded access is disabled: the initial setup filter passes every request (IA1 to IA8 do "
                    + "not apply with an external Access server)");
            chain.doFilter(request, response);
            return;
        }
        Optional<AdminBootstrapService> bootstrap = findService();
        if (bootstrap.isPresent() && bootstrap.get().isClaimed()) {
            open = true;
            log.info("Initial admin claimed; the server is open");
            chain.doFilter(request, response);
            return;
        }
        if (request instanceof HttpServletRequest http && isAllowedWhileUnclaimed(http, bootstrap.isPresent())) {
            chain.doFilter(request, response);
            return;
        }
        if (response instanceof HttpServletResponse http) {
            block(http);
        }
    }

    /**
     * Tells whether a request may pass while the server is unclaimed. Pure.
     *
     * @param method           the HTTP method
     * @param path             the un-decoded path below the context path
     * @param serviceAvailable whether the bootstrap service is available
     * @return {@code true} for ping, the probes, and the claim endpoint when the service is available
     */
    static boolean isAllowedWhileUnclaimed(String method, String path, boolean serviceAvailable) {
        if (path == null || method == null) {
            return false;
        }
        return switch (path) {
            case PING_PATH -> "GET".equals(method);
            case READINESS_PATH, LIVENESS_PATH -> true;
            case CLAIM_PATH -> serviceAvailable && "POST".equals(method);
            default -> false;
        };
    }

    private static boolean isAllowedWhileUnclaimed(HttpServletRequest request, boolean serviceAvailable) {
        return isAllowedWhileUnclaimed(request.getMethod(), RequestUtils.getServletPathFromRequest(request),
                serviceAvailable);
    }

    private Optional<AdminBootstrapService> findService() {
        AdminBootstrapService found = service;
        if (found != null) {
            return Optional.of(found);
        }
        Optional<AdminBootstrapService> looked = explicitLookup != null ? explicitLookup.get()
                : AdminBootstrapService.lookup(getArtifactoryContext());
        looked.ifPresent(s -> service = s);
        return looked;
    }

    private Optional<Boolean> embeddedMode() {
        if (explicitEmbeddedMode != null) {
            return explicitEmbeddedMode.get();
        }
        ArtifactoryContext context = getArtifactoryContext();
        ArtifactoryHome home = context == null ? null : context.getArtifactoryHome();
        return home == null ? Optional.empty()
                : Optional.of(ConstantValues.accessEmbeddedEnabled.getBoolean(home));
    }

    private static void block(HttpServletResponse response) throws IOException {
        byte[] body = BLOCKED_BODY.getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
