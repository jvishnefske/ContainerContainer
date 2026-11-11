/*
 *
 * Copyright 2019 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.webapp.servlet.authentication;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.signature.IssuerModel;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.webapp.servlet.RequestUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static java.util.Objects.requireNonNull;
import static org.artifactory.webapp.servlet.RequestUtils.getServletPathFromRequest;

/**
 * Filter for handling authentication with Signed URL
 *
 * @author Rotem Kfir
 */
public class ArtifactorySignedUrlAuthenticationFilter implements ArtifactoryAuthenticationFilter {
    private static final Logger log = LoggerFactory.getLogger(ArtifactorySignedUrlAuthenticationFilter.class);
    private AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource =
            new WebAuthenticationDetailsSource();

    @Autowired
    SignedUrlService signedUrlService;

    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to init
    }

    @Override
    public boolean requiresReAuthentication(ServletRequest request, Authentication authentication) {
        // Caching may collide with the expiration
        return true;
    }

    @Override
    public boolean acceptFilter(ServletRequest request) {
        HttpServletRequest servletRequest = (HttpServletRequest) request;
        return !RequestUtils.isUiRequest(servletRequest) &&
                StringUtils.isNotBlank(servletRequest.getParameter(SystemRestConstants.PATH_PARAM_SIGNATURE));
    }

    @Override
    public String getCacheKey(ServletRequest request) {
        // Caching may collide with the expiration. See this.requiresReAuthentication
        return null;
    }

    @Override
    public String getLoginIdentifier(ServletRequest request) {
        return null;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String servletPath = getServletPathFromRequest(httpRequest);
        boolean repoRequest = servletPath != null && RequestUtils.isRepoRequest(httpRequest, false)
                || httpRequest.getPathInfo() == null;
        String path;
        if (repoRequest) {
            path = httpRequest.getServletPath();
        } else {
            requireNonNull(httpRequest.getServletPath(), "Servlet path should not be null");
            path = httpRequest.getServletPath() + httpRequest.getPathInfo();
        }
        path = requireNonNull(PathUtils.trimLeadingSlashes(path), "Servlet path should not be null");
        String sig = request.getParameter(SystemRestConstants.PATH_PARAM_SIGNATURE);
        try {
            log.trace("Trying to authenticate with query param signature {}", sig);
            IssuerModel issuer = signedUrlService.verifySignedToken(path, sig);
            // update security context with new authentication
            MutableUserInfo issuerInfo = new UserInfoBuilder(issuer.getTokenIssuer()).transientUser().build();

            SignedUrlAuthenticationToken authentication =
                    new SignedUrlAuthenticationToken(new SimpleUser(issuerInfo), sig, issuer.getInternalRepo());
            authentication.setDetails(authenticationDetailsSource.buildDetails((HttpServletRequest) request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.trace("Authentication with query param signature {} succeeded", sig);
        } catch (SignedUrlException e) {
            sendForbiddenError(response, "Authentication of signed URL failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            log.debug("Unable to authenticate signed URL for repo path " + path, e);
            sendForbiddenError(response,
                    "Authentication of signed URL failed for repo path " + path + " : " + e.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    private void sendForbiddenError(ServletResponse response, String message) throws IOException {
        log.debug(message);
        ((HttpServletResponse) response).sendError(HttpStatus.SC_FORBIDDEN, message);
    }

    @Override
    public void destroy() {
        // Nothing to destroy
    }

    @Override
    public ArtifactoryAuthenticationType getType() {
        return ArtifactoryAuthenticationType.ARTIFACTORY_SIGNED_URL;
    }
}