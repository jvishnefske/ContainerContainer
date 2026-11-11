package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.audit.HttpHeadersAuditService;
import org.artifactory.request.guard.InboundRequestGuardService;

import java.io.IOException;

/**
 * Filter that inspects incoming HTTP requests for two purposes:
 * <ul>
 *     <li>Inbound request guard ({@link InboundRequestGuardService}) — rejects requests carrying a configured
 *     header name or a User-Agent substring with a 403, short-circuiting the chain.</li>
 *     <li>Header-name audit ({@link HttpHeadersAuditService}) — records distinct header names per request for
 *     periodic logging. Controlled by {@code ConstantValues.httpHeadersAuditEnabled}.</li>
 * </ul>
 *
 * @author Hussein Alshqerat
 */
@Slf4j
public class HttpHeadersAuditFilter extends DelayedFilterBase {

    static final String EULA_BLOCK_MESSAGE =
            "The request was blocked because it is being channeled through an unsupported integration." +
                    " Contact JFrog support for further discussion.";

    private HttpHeadersAuditService httpHeadersAuditService;
    private InboundRequestGuardService inboundRequestGuardService;

    @Override
    public void initLater(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
        httpHeadersAuditService = context.beanForType(HttpHeadersAuditService.class);
        inboundRequestGuardService = context.beanForType(InboundRequestGuardService.class);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse
                && rejectIfGuardBlocks(httpRequest, httpResponse)) {
            return;
        }
        if (ConstantValues.httpHeadersAuditEnabled.getBoolean() && request instanceof HttpServletRequest httpRequest) {
            try {
                httpHeadersAuditService.recordHeaderNames(httpRequest);
            } catch (Exception e) {
                log.debug("Failed to record HTTP headers for audit", e);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean rejectIfGuardBlocks(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean block;
        try {
            block = inboundRequestGuardService.shouldBlock(request);
        } catch (Exception e) {
            log.debug("Inbound request guard check failed; allowing request through", e);
            return false;
        }
        if (!block) {
            return false;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, EULA_BLOCK_MESSAGE);
        return true;
    }
}
