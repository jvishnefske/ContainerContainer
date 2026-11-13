/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.webapp.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * The forced password change gate of {@link AccessFilter} (requirement IA5).
 * <p>
 * A request authenticated as a user whose descriptor carries {@link UserInfo#isMustChangePassword()} may only call
 * the change-password endpoints ({@link HttpUtils#isChangePasswordRequest}) or log out
 * ({@link HttpUtils#isLogoutRequest}); every other request answers 403 with the plain-text body
 * {@value #MESSAGE}. Because the authenticated principal may be cached (the filter's authentication caches, an HTTP
 * session), a flagged descriptor is confirmed against the user store before refusing, so the gate lifts on the next
 * request after the holder changed the password. Unflagged principals are never looked up, so the gate adds no work
 * to ordinary requests. When the store lookup fails or finds no user, the request is refused (fail closed).
 */
final class MustChangePasswordGate {

    /** The body of the 403 answer. */
    static final String MESSAGE = "password change required";

    private static final Logger log = LoggerFactory.getLogger(MustChangePasswordGate.class);

    private MustChangePasswordGate() {
    }

    /**
     * Decides whether a request must be refused. Pure apart from the {@code currentUser} lookup it is handed.
     *
     * @param authentication the request's authentication, may be null
     * @param request        the request
     * @param currentUser    looks up the stored user by name (null if absent); called only for flagged principals
     * @return true if the request must be answered with 403 {@value #MESSAGE}
     */
    static boolean blocks(Authentication authentication, HttpServletRequest request,
            Function<String, UserInfo> currentUser) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SimpleUser principal)) {
            return false;
        }
        UserInfo descriptor = principal.getDescriptor();
        if (descriptor == null || !descriptor.isMustChangePassword()) {
            return false;
        }
        if (HttpUtils.isChangePasswordRequest(request) || HttpUtils.isLogoutRequest(request)) {
            return false;
        }
        try {
            UserInfo stored = currentUser.apply(descriptor.getUsername());
            return stored == null || stored.isMustChangePassword();
        } catch (RuntimeException e) {
            log.debug("Could not confirm the must-change-password flag of '{}'", descriptor.getUsername(), e);
            return true;
        }
    }

    /**
     * Writes the refusal: status 403 and the plain-text body {@value #MESSAGE}.
     *
     * @param response the response to write to
     * @throws IOException if the body cannot be written
     */
    static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MESSAGE);
    }
}
