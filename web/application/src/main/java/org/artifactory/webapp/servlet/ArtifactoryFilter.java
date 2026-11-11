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

package org.artifactory.webapp.servlet;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.util.DockerInternalRewrite;
import org.artifactory.webapp.servlet.redirection.AccessProxyRedirectHandler;
import org.artifactory.webapp.servlet.redirection.OldBuildsRedirectionHandler;
import org.artifactory.webapp.servlet.redirection.OldHomeRedirectionHandler;
import org.artifactory.webapp.servlet.redirection.OldLoginRedirectionHandler;
import org.artifactory.webapp.servlet.redirection.RedirectionHandler;
import org.artifactory.webapp.servlet.redirection.SamlRedirectionHandler;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.common.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.util.List;

import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.artifactory.util.HttpUtils.sendErrorResponse;
import static org.artifactory.webapp.servlet.DelayedInit.FILTER_SHORTCUT_ATTR;

/**
 * @author yoavl
 */
public class ArtifactoryFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryFilter.class);
    private static final String PRESTOP_URL = "/artifactory/api/v1/system/preStop";

    private boolean contextFailed = false;

    private FilterConfig filterConfig;
    private final List<RedirectionHandler> redirectionHandlers = List.of(new SamlRedirectionHandler());

    private final List<RedirectionHandler> withContextRedirectionHandlers = List.of(
            new OldBuildsRedirectionHandler(), new OldHomeRedirectionHandler(), new OldLoginRedirectionHandler(),
            new AccessProxyRedirectHandler(), new DockerInternalRewrite());

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setAttribute(ServletUtils.ATTR_REQUEST_START_TIME, System.currentTimeMillis());

        // Redirect or forward if needed
        if (redirectIfNeeded(redirectionHandlers, request, response)) {
            return;
        }

        HttpServletRequest servletRequest = (HttpServletRequest) request;
        String requestURI = servletRequest.getRequestURI();
        if (filterConfig.getServletContext().getAttribute(DelayedInit.APPLICATION_CONTEXT_LOCK_KEY) != null) {
            if (requestURI.endsWith("artifactory-splash.gif")) {
                ((HttpServletResponse) response).setStatus(200);
                ServletOutputStream out = response.getOutputStream();
                ResourceUtils.copyResource("/artifactory-splash.gif", out, null, getClass());
                return;
            } else if (requestURI.endsWith("favicon.ico")) {
                ((HttpServletResponse) response).setStatus(200);
                ServletOutputStream out = response.getOutputStream();
                ResourceUtils.copyResource("/favicon.ico", out, null, getClass());
                return;
            }
            response.setContentType("text/html");
            ((HttpServletResponse) response).setStatus(HttpStatus.SC_SERVICE_UNAVAILABLE);
            ServletOutputStream out = response.getOutputStream();
            ResourceUtils.copyResource("/startup.html", out, null, getClass());
            return;
        }

        ArtifactoryContext context = getArtifactoryContext();
        if (context == null) {
            respondFailedToInitialize(response);
            return;
        }

        try {
            //Context is required for both Artifactory and Binary Store servlets
            bind(context);

            // Internal Rewrites - requires configuration (needs bound context)
            if (redirectIfNeeded(withContextRedirectionHandlers, request, response)) {
                return;
            }

            if (response instanceof HttpServletResponse &&
                    ServletUtils.isBinaryStoreServletRequest(request.getServletContext().getContextPath(),
                            requestURI)) {
                request.setAttribute(FILTER_SHORTCUT_ATTR, "binaryStore");
            }

            chain.doFilter(request, response);
        } catch (Exception e) {
            String logMessage = String.format("Caught exception in %s, %s", servletRequest.getMethod(), requestURI);
            
            // Sending proper error message to the client in case server error occurred
            if (response instanceof HttpServletResponse httpServletResponse) {
                processExceptionForHttpServletResponse(httpServletResponse, servletRequest, e, logMessage);
            } else {
                log.error(logMessage, e);
                throw e;
            }
        }
        finally {
            unbind();
            TransactionLeakDetector.detectAndRelease(context, request);
        }
    }

    void processExceptionForHttpServletResponse(HttpServletResponse response, ServletRequest request,
            Exception e, String origLogString) throws IOException {
        // Validating that we didn't already send another error message to the client
        if (response.isCommitted() || response.getStatus() != HttpStatus.SC_OK) {
            return;
        }
        if (isPreStopRequest(request)) {
            handlePreStopException(response, e, origLogString);
            return;
        }
        if (e instanceof AccessClientHttpException accessClientHttpException) {
            handleAccessClientHttpException(response, accessClientHttpException, origLogString);
            return;
        }
        if (e instanceof BadCredentialsException || e instanceof AccessClientException) {
            handleUnauthorizedException(response, e, origLogString);
            return;
        }
        InvalidParameterException invalidParameter =
                ExceptionUtils.throwableOfType(e, InvalidParameterException.class);
        if (invalidParameter != null) {
            handleInvalidParameterException(response, e, invalidParameter, origLogString);
            return;
        }
        handleInternalServerError(response, e, origLogString);
    }

    private void handlePreStopException(HttpServletResponse response, Exception e, String origLogString)
            throws IOException {
        log.debug("Expected error during shutdown: {}", origLogString, e);
        sendErrorResponse(response, HttpStatus.SC_SERVICE_UNAVAILABLE, "Service is shutting down.");
    }

    private void handleAccessClientHttpException(HttpServletResponse response, AccessClientHttpException e,
            String origLogString) throws IOException {
        log.warn(origLogString, e);
        sendErrorResponse(response, e.getStatusCode(), e.getMessage());
    }

    private void handleUnauthorizedException(HttpServletResponse response, Exception e, String origLogString)
            throws IOException {
        log.warn(origLogString, e);
        sendErrorResponse(response, HttpStatus.SC_UNAUTHORIZED, e.getMessage());
    }

    private void handleInvalidParameterException(HttpServletResponse response, Exception e,
            InvalidParameterException invalidParameter, String origLogString) throws IOException {
        int status = invalidParameter.getErrorCode() > 0
                ? invalidParameter.getErrorCode() : HttpStatus.SC_BAD_REQUEST;
        log.warn("{} - malformed request parameters (status {}): {}",
                origLogString, status, invalidParameter.getMessage());
        log.debug(origLogString, e);
        sendErrorResponse(response, status, "Malformed request parameters");
    }

    private void handleInternalServerError(HttpServletResponse response, Exception e, String origLogString)
            throws IOException {
        log.error(origLogString, e);
        sendErrorResponse(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private ArtifactoryContext getArtifactoryContext() {
        ServletContext servletContext = filterConfig.getServletContext();
        return RequestUtils.getArtifactoryContext(servletContext);
    }

    @VisibleForTesting
    boolean redirectIfNeeded(List<RedirectionHandler> redirectionHandlers, ServletRequest request,
            ServletResponse response) {
        for (RedirectionHandler redirectionHandler : redirectionHandlers) {
            if (redirectionHandler.shouldRedirect(request)) {
                redirectionHandler.redirect(request, response);
                return true;
            }
        }
        return false;
    }

    private void bind(ArtifactoryContext context) {
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(context.getArtifactoryHome());
    }

    private void unbind() {
        EventLogHintManager.remove(RemoveRepo);
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        EventLogHintManager.clear();
    }

    private void respondFailedToInitialize(ServletResponse response) throws IOException {
        if (!contextFailed) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(ArtifactoryFilter.class);
            logger.error("Artifactory failed to initialize: Context is null");
            contextFailed = true;
        }

        if (response instanceof HttpServletResponse servletResponse) {
            sendErrorResponse(servletResponse, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Artifactory failed to initialize: check Artifactory logs for errors.");
        }
    }

    private boolean isPreStopRequest(final ServletRequest request) {
        return request instanceof HttpServletRequest httpRequest && PRESTOP_URL.equals(httpRequest.getRequestURI());
    }

    @Override
    public void destroy() {
        unbind();
    }
}