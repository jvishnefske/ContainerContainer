/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.webapp.servlet.InitialSetupFilter;
import org.artifactory.webapp.servlet.RequestUtils;

/**
 * Lets an unauthenticated {@code POST} to the initial-admin claim endpoint ({@value InitialSetupFilter#CLAIM_PATH})
 * through {@code AccessFilter} as anonymous even when anonymous access is disabled, as
 * {@link AnonymousPingInterceptor} does for ping. The claim needs no identity: the setup token is its credential,
 * and the endpoint refuses every request once the server is claimed. Registered in {@code security.xml}.
 */
public class AnonymousInitialAdminClaimInterceptor implements AnonymousAuthenticationInterceptor {

    @Override
    public boolean accept(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && InitialSetupFilter.CLAIM_PATH.equals(RequestUtils.getServletPathFromRequest(request));
    }
}
