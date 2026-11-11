/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.webapp.servlet.authentication;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.artifactory.webapp.servlet.RequestUtils;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.apache.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.artifactory.authentication.AuthenticationFilterUtils.isRequestContainsAuthentication;
import static org.artifactory.webapp.servlet.authentication.ArtifactoryBasicAuthenticationEntryPoint.REALM;

/**
 * A filter that sends a HTTP 401 challenge (WWW-Authenticate header) to all un-authenticated requests
 * going to Maven virtual repositories configured to force authentication.
 *
 * Note: this filter is extended for Pro versions at MavenForceAuthenticationFilter
 * and will not be loaded on Pro instances (due to Spring's addon.xml 'primary' attribute)
 *
 * We use the filter when ALL the following are true:
 * 1. Repo is Maven Virtual and flag {@link VirtualRepoDescriptor#forceMavenAuthentication} is turned ON
 * 2. Request is not to UI / API / WEBAPP
 * 3. Request has no authentication attached - no other filters accept the request
 *
 * @author Uriah Levy
 * @author Yuval Reches
 */
public class OssForceAuthenticationFilter implements ArtifactoryAuthenticationFilter {
    private static final Logger log = LoggerFactory.getLogger(OssForceAuthenticationFilter.class);

    private RepositoryService repoService;
    private SecurityService securityService;
    private AccessService accessService;

    @Autowired
    public void setAccessClient(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setRepoService(RepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public boolean requiresReAuthentication(ServletRequest request, Authentication authentication) {
        // For cases in which we already have cached authentication (of anonymous for example)
        return acceptFilter(request);
    }

    @Override
    public boolean acceptFilter(ServletRequest request) {
        boolean repoIsForcingAuthentication = isMavenVirtualRepoForcedAuthentication(request);
        boolean authPresent =
                isRequestContainsAuthentication((HttpServletRequest) request, securityService, accessService);
        authPresent = checkIfAuthenticationShouldBeIgnored(request, authPresent);
        String path = RequestUtils.getServletPathFromRequest((HttpServletRequest) request);
        log.trace("[{}] repoIsForcingAuthentication: {}; authPresent:{}", path, repoIsForcingAuthentication,
                authPresent);
        return repoIsForcingAuthentication && !authPresent;
    }

    private boolean isMavenVirtualRepoForcedAuthentication(ServletRequest request) {
        String repoKey = tryExtractingRepoKeyFromRequest(request);
        if (StringUtils.isBlank(repoKey)) {
            log.trace("Request is not to a repo");
            return false;
        }
        RepoDescriptor repoDescriptor = repoService.repoDescriptorByKey(repoKey);
        if (repoDescriptor instanceof VirtualRepoDescriptor virtualRepoDescriptor) {
            switch (repoDescriptor.getType()) {
                case Maven -> {
                    log.trace("Request is to repo {} which is a virtual Maven repo", repoKey);
                    return virtualRepoDescriptor.isForceMavenAuthentication();
                }
                case P2 -> {
                    log.trace("Request is to repo {} which is a virtual P2 repo", repoKey);
                    return virtualRepoDescriptor.isForceP2Authentication();
                }
                default -> {
                    log.trace("Request is to repo {} which is a virtual repo but not Maven or P2", repoKey);
                    return false;
                }
            }
        }
        log.trace("Request is to repo {} which is not a virtual Maven repo", repoKey);
        return false;
    }

    private String tryExtractingRepoKeyFromRequest(ServletRequest request) {
        ArtifactoryRequest artifactoryRequest;
        try {
            artifactoryRequest = new HttpArtifactoryRequest((HttpServletRequest) request);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            log.debug("", e);
            return null;
        }
        String repoKey = artifactoryRequest.getRepoKey();

        // The UI does not handle native browser requests authentication, so we need to handle them here
        if ("ui".equals(repoKey) || "api".equals(repoKey) || "webapp".equals(repoKey)) {
            if (!artifactoryRequest.getPath().startsWith("native")) {
                return null;
            }
            // We only need the second part of the path, so it's enough to split twice
            String[] splitPath = artifactoryRequest.getPath().split("/", 3);
            if (splitPath.length > 1) {
                repoKey = splitPath[1];
            } else {
                return null;
            }
        }
        return repoKey;
    }

    private boolean checkIfAuthenticationShouldBeIgnored(ServletRequest request, boolean authPresent) {
        if (!accessService.isAnonymousAccessEnabled() || !authPresent) {
            return authPresent;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // We need to avoid passing anonymous authentication to the filter chain
        if (RequestUtils.isBasicAuthHeaderPresent(httpRequest)) {
            String auth = RequestUtils.extractUsernameFromRequest(request);
            authPresent = StringUtils.isNotBlank(auth) && !UserInfo.isAnonymous(auth);
        }
        if (RequestUtils.isBearerAuthentication(httpRequest)) {
            authPresent = verifyBearerAuthPresent(httpRequest);
        }
        return authPresent;
    }

    /**
     * Verifies a bearer token on the request and returns whether it authenticates a
     * non-anonymous identity. Any verification failure (unsupported JWS algorithm such
     * as HS256 against the RSA verifier, signature mismatch, expired token, transient
     * Access-service error, ...) is treated as "no verified identity on this request"
     * and must NOT surface as a server error. Returning {@code false} here lets the
     * downstream authentication filters respond with a proper 401 instead of this
     * filter bubbling the exception up as an HTTP 500.
     * <p>
     * This probe runs on every request carrying a bearer header, so a single
     * misconfigured client (e.g. an OTel collector scraping metrics with an HS256
     * token) can produce thousands of failures per day. To stay visible in
     * dashboards for the legitimate cases (key rotation, Access outage, ...) without
     * degenerating into a WARN storm, failures are emitted through
     * {@link LogThrottleMarker} — the global {@code LogThrottleFilter} caps this
     * logger at 100 events per hour and drops the rest at the Logback layer. The
     * full stack trace stays available at {@code DEBUG} for on-call investigation.
     */
    private boolean verifyBearerAuthPresent(HttpServletRequest request) {
        String token = RequestUtils.getBearerAuthenticationValue(request).orElse(null);
        if (StringUtils.isBlank(token)) {
            return false;
        }
        try {
            TokenVerifyResult tokenVerifyResult = accessService.verifyAndGetResult(token);
            if (tokenVerifyResult == null || tokenVerifyResult.getAccessToken() == null) {
                log.warn(LogThrottleMarker.getInstance(),
                        "Bearer token verification returned no access token for request [{}]; " +
                                "treating as unauthenticated",
                        RequestUtils.getServletPathFromRequest(request));
                return false;
            }
            String tokenSubject = tokenVerifyResult.getAccessToken().getSubject();
            return StringUtils.isNotBlank(tokenSubject) && !tokenSubject.contains(UserInfo.ANONYMOUS);
        } catch (Exception e) {
            log.warn(LogThrottleMarker.getInstance(),
                    "Bearer token verification failed for request [{}]; treating as unauthenticated: {}",
                    RequestUtils.getServletPathFromRequest(request), ExceptionUtils.getRootCauseMessage(e));
            log.debug("Failed to verify token [{}]", token, e);
            return false;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException {
        sendHttpAuthChallenge((HttpServletResponse) response);
    }

    private void sendHttpAuthChallenge(HttpServletResponse response) throws IOException {
        log.debug("Anonymous user resolving via virtual repo with authRequired flag on - sending auth challenge");
        response.addHeader(WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
        HttpUtils.sendErrorResponse(response, HttpStatus.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Override
    public String getCacheKey(ServletRequest request) {
        return null;
    }

    @Override
    public String getLoginIdentifier(ServletRequest request) {
        return null;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public ArtifactoryAuthenticationType getType() {
        return ArtifactoryAuthenticationType.OSS_FORCE_AUTH;
    }
}

