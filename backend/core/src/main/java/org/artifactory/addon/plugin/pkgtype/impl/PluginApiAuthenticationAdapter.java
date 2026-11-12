/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import com.jfrog.ph.acl.model.PackageAuthenticationRequestContext;
import com.jfrog.ph.acl.service.PackageAuthenticationAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.AuthenticationFilterUtils;
import org.artifactory.security.UserInfo;
import org.artifactory.util.SecurityUtils;
import org.artifactory.webapp.servlet.RequestUtils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lets HTTP Basic credentials authenticate requests to {@code /api/docker/}, the endpoint the plugin HTTP API and
 * the {@code /v2/} rewrite use.
 *
 * <p>{@code ArtifactoryBasicAuthenticationFilter} refuses Basic credentials on the package endpoints listed in
 * {@code RequestUtils.PACKAGES_ENDPOINT_USE_BASIC_AUTH} ({@code /api/docker/} among them) and leaves them to the
 * package-handler authentication filter, whose adapter the Pro edition provides. OSS ships an adapter that handles
 * nothing, so without this one {@code docker login} credentials would be ignored and every client would stay
 * anonymous. This adapter is the OSS default ({@code ArtifactoryPackageBeansConfig}); a Pro adapter still replaces
 * it. It accepts a request, handing it to the Basic filter through {@code PackageAuthenticationFilterImpl}, only when
 * all of these hold ({@link #acceptsBasic}):
 * <ul>
 *     <li>the request URI is under {@code <context>/api/docker/};</li>
 *     <li>at least one registered plugin package type has an HTTP API handler, so with no such plugin the endpoint
 *         behaves exactly as in stock OSS;</li>
 *     <li>the same conditions under which the Basic filter accepts any other request: a Basic header is present,
 *         no HTTP SSO user is set, the password passes the password policy, and the user is not anonymous unless
 *         anonymous access is enabled.</li>
 * </ul>
 * Everything else gets an empty context, which the package filter reports as not handled.
 */
public class PluginApiAuthenticationAdapter implements PackageAuthenticationAdapter {

    static final String DOCKER_ENDPOINT = "/api/docker/";

    private final PluginPackageTypeRegistry registry;
    private final Supplier<SecurityService> securityService;

    /**
     * @param securityService resolved on first use, because the adapter is created while security beans are still
     *                        being wired
     */
    public PluginApiAuthenticationAdapter(PluginPackageTypeRegistry registry,
            Supplier<SecurityService> securityService) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.securityService = Objects.requireNonNull(securityService, "securityService");
    }

    @Override
    public PackageAuthenticationRequestContext getContext(HttpServletRequest request) {
        if (!isDockerEndpoint(request.getRequestURI(), request.getContextPath()) || !apiTypeRegistered()) {
            return PackageAuthenticationRequestContext.builder().build();
        }
        SecurityService security = securityService.get();
        boolean basicPresent = RequestUtils.isBasicAuthHeaderPresent(request);
        String username = basicPresent ? RequestUtils.extractUsernameFromRequest(request) : null;
        boolean sso = StringUtils.isNotBlank(AuthenticationFilterUtils.getRemoteUserName(
                security.getHttpSsoRemoteUserRequestVariable(), request));
        boolean policy = basicPresent &&
                SecurityUtils.validatePasswordPolicy(username, RequestUtils.extractPasswordFromRequest(request));
        boolean accepted = acceptsBasic(true, true, basicPresent, sso, policy,
                security.isAnonAccessEnabled(), username != null && UserInfo.isAnonymous(username));
        return PackageAuthenticationRequestContext.builder().authenticationAllowed(accepted).build();
    }

    /** Whether {@code requestUri} is under {@code <contextPath>/api/docker/}. Pure. */
    static boolean isDockerEndpoint(String requestUri, String contextPath) {
        return requestUri != null && requestUri.startsWith(StringUtils.defaultString(contextPath) + DOCKER_ENDPOINT);
    }

    /**
     * The acceptance rule. Pure.
     *
     * @param dockerEndpoint     the request is under {@code /api/docker/}
     * @param apiTypeRegistered  some registered plugin package type has an API handler
     * @param basicPresent       a non-empty Basic {@code Authorization} header is present
     * @param sso                an HTTP SSO remote user is set on the request
     * @param passwordPolicyOk   the password passes the password policy
     * @param anonAccessEnabled  anonymous access is enabled
     * @param anonymousUsername  the Basic username is the anonymous user
     */
    static boolean acceptsBasic(boolean dockerEndpoint, boolean apiTypeRegistered, boolean basicPresent, boolean sso,
            boolean passwordPolicyOk, boolean anonAccessEnabled, boolean anonymousUsername) {
        return dockerEndpoint && apiTypeRegistered && basicPresent && !sso && passwordPolicyOk &&
                (anonAccessEnabled || !anonymousUsername);
    }

    private boolean apiTypeRegistered() {
        return registry.all().stream().anyMatch(type -> type.api().isPresent());
    }
}
