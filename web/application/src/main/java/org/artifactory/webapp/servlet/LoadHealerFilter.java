package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.throttling.lh.service.LoadHealerService;

import java.io.IOException;

import static org.artifactory.throttling.lh.config.LoadHealerActivity.LIMIT_END_POINT;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.LIMIT_MODULE;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.LIMIT_USER;

/**
 * Filter responsible for load healer throttling at user, module, and endpoint levels.
 *
 * @author Amir Avidan
 */
public class LoadHealerFilter extends DelayedFilterBase {

    private static final String API = "/api/";

    private LoadHealerService loadHealerService;
    private SecurityService securityService;

    @Override
    public void initLater(FilterConfig filterConfig) throws ServletException {
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(filterConfig.getServletContext());
        this.loadHealerService = context.beanForType(LoadHealerService.class);
        this.securityService = context.beanForType(SecurityService.class);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (shouldSkipFilter(req) || !loadHealerService.isEnabled()) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        String servletPath = RequestUtils.getServletPathFromRequest(request);
        loadHealerService.executeWithActivity(LIMIT_USER, securityService.currentUsername(),
                () -> executeWithLimitUserActivity(req, resp, chain, servletPath));
    }

    private void executeWithLimitUserActivity(ServletRequest req, ServletResponse resp, FilterChain chain,
            String servletPath) throws IOException, ServletException {
        if (servletPath != null && servletPath.contains(API)) {
            String[] pathParts = servletPath.split("/");
            String module = extractModule(pathParts);
            String endPoint = extractEndPoint(pathParts);
            loadHealerService.executeWithActivity(LIMIT_MODULE, module,
                    () -> executeWithLimitModuleActivity(req, resp, chain, endPoint));
        } else {
            chain.doFilter(req, resp);
        }
    }

    private void executeWithLimitModuleActivity(ServletRequest req, ServletResponse resp, FilterChain chain,
            String endPoint) throws IOException, ServletException {
        if (!endPoint.isEmpty()) {
            loadHealerService.executeWithActivity(LIMIT_END_POINT, endPoint, () -> chain.doFilter(req, resp));
        } else {
            chain.doFilter(req, resp);
        }
    }

    /**
     * Extracts the module from the path parts.
     * Example: "/api/search/aql..." → "/api/search"
     */
    private String extractModule(String[] pathParts) {
        return pathParts.length > 2 ? API + pathParts[2] : "";
    }

    /**
     * Extracts the endpoint from the path parts.
     * Example: "/api/search/aql..." → "/api/search/aql
     */
    private String extractEndPoint(String[] pathParts) {
        return pathParts.length > 3 ? API + pathParts[2] + "/" + pathParts[3] : "";
    }
}

