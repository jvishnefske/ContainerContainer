package org.artifactory.webapp.servlet;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.common.ConstantValues;
import org.artifactory.probes.ProbesService;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessTokenImpl;

import java.io.IOException;
import java.io.PrintWriter;

import static org.apache.http.HttpStatus.SC_OK;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class ArtifactoryProbesFilter extends DelayedFilterBase {

    private static final String LIVENESS_PROBES_URL = "/artifactory/api/v1/system/liveness";
    private static final String BLOCKED_REQUEST_MESSAGE =
            "Request was blocked because Artifactory is not in a ready state";
    private static final String FALLBACK_BLOCK_REQUESTS_ON_READINESS_PROPERTY_KEY =
            "artifactory.block.requests.until.ready";

    private ProbesService probesService;

    @Override
    protected void initLater(FilterConfig config) throws ServletException {
        ServletContext servletContext = config.getServletContext();
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
        probesService = context.beanForType(ProbesService.class);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (super.shouldSkipFilter(servletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else if (isProbesResource(servletRequest)) {
            CurrentActivity.GROUP.put(RestApiGroup.PROBES_API);
            execute((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
        } else if (shouldBlockAndAppIsNotReady(servletRequest)) {
            log.trace(BLOCKED_REQUEST_MESSAGE);
            sendUnavailableResponse((HttpServletResponse) servletResponse, BLOCKED_REQUEST_MESSAGE);
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private void execute(@Nonnull HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isLivenessRequest(request)) {
            handleLivenessRequest(response);
        } else if (isReadinessRequest(request)) {
            boolean noCache = Boolean.parseBoolean(request.getParameter("noCache"));
            handleReadinessRequest(response, noCache);
        }
    }

    private void handleLivenessRequest(HttpServletResponse response) throws IOException {
        try {
            log.trace("Received a liveness probe");
            probesService.checkLivenessStatus();
            sendOkResponse(response);
        } catch (ServiceUnavailableException e) {
            log.trace("Liveness probe has failed: {} ", e.getMessage(), e);
            sendUnavailableResponse(response, e.getMessage());
        }
    }

    private void handleReadinessRequest(HttpServletResponse response, boolean noCache) throws IOException {
        try {
            log.trace("Received a readiness probe");
            probesService.checkReadinessStatus(noCache);
            sendOkResponse(response);
        } catch (ServiceUnavailableException e) {
            log.trace("readiness probe has failed: {} ", e.getMessage(), e);
            sendUnavailableResponse(response, e.getMessage());
        }
    }

    private boolean shouldBlockAndAppIsNotReady(ServletRequest request) {
        return isRequestBlockingEnabled() && !isAppReady() && !isInternalRequest(request);
    }

    private boolean isRequestBlockingEnabled() {
        try {
            return ConstantValues.blockRequestsWhenApplicationNotReady.getBoolean();
        } catch (Exception e) {
            log.warn("Failed to load request blocking configuration", e);
            return Boolean.parseBoolean(System.getProperty(FALLBACK_BLOCK_REQUESTS_ON_READINESS_PROPERTY_KEY, "true"));
        }
    }

    private boolean isAppReady() {
        if (probesService == null) {
            log.warn("ProbesService is null. Artifactory is considered 'Not Ready'." +
                     "Request will be blocked (unless it's an internal request).");
            return false;
        }
        return probesService.isReady();
    }

    private boolean isProbesResource(ServletRequest request) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        return isLivenessRequest(httpRequest) || isReadinessRequest(httpRequest);
    }

    private boolean isLivenessRequest(@Nonnull HttpServletRequest request) {
        return LIVENESS_PROBES_URL.equals(request.getRequestURI());
    }

    /**
     * Checks whether the request originates from an internal JFrog platform service.
     * Service-to-service tokens have a ServiceId as subject (e.g. "jffs@01234"), while user tokens
     * have a user-scoped subject (e.g. "jfrt@serviceId/users/admin").
     * <p>
     * Uses {@link JwtAccessTokenImpl#parseTokenValue} for lightweight decode without signature
     * verification. This is safe because AccessFilter — which performs full token verification —
     * is initialized before this filter runs (both share the same {@code delayedInit} lifecycle),
     * so bypassed requests will still be fully authenticated downstream.
     */
    private boolean isInternalRequest(ServletRequest request) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String bearerToken = RequestUtils.getBearerAuthenticationValue(httpRequest).orElse(null);
        if (StringUtils.isNotBlank(bearerToken)) {
            try {
                String subject = JwtAccessTokenImpl.parseTokenValue(bearerToken).getSubject();
                if (ServiceId.isServiceId(subject)) {
                    log.trace("Identified an internal request from service: {}", subject);
                    return true;
                }
            } catch (Exception e) {
                log.trace("Token is not a service token or could not be parsed", e);
            }
        }
        return false;
    }

    private void sendOkResponse(HttpServletResponse response) throws IOException {
        String jsonString = "{\"code\" : \"OK\"}";
        response.setStatus(SC_OK);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.println(jsonString);
        out.close();
    }
}
