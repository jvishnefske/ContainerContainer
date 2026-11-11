package org.artifactory.webapp.servlet;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.request.DownloadService;
import org.artifactory.repo.RepoPath;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.util.HttpUtils;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpMethod.HEAD;

/**
 * Filter that records traffic information for requests that does not go through {@link DownloadService}.
 * For example, if a packages API reads some file stream for some operation,
 * this filter will record the response bytes amount and write it to traffic log v3.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class TrafficFilter extends DelayedFilterBase {

    private TrafficService trafficService;

    @Override
    public void initLater(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
        trafficService = context.beanForType(TrafficService.class);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (skipFilterIfNeeded(request)) {
            chain.doFilter(request, response);
            return;
        }
        var responseWrapper = wrapResponseIfNeeded(response);
        try {
            TrafficContextThreadLocal.bind();
            chain.doFilter(request, responseWrapper);
        } finally {
            try {
                logTrafficLogV3IfNeeded(responseWrapper, request);
            } finally {
                TrafficContextThreadLocal.unbind();
            }
        }
    }

    private boolean skipFilterIfNeeded(ServletRequest request) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        return httpServletRequest.getMethod().equals(HEAD.name()) || !trafficService.isV3Active();
    }

    private CapturingHttpServletResponseWrapper wrapResponseIfNeeded(ServletResponse response) {
        if (response instanceof CapturingHttpServletResponseWrapper capturingHttpServletResponse) {
            return capturingHttpServletResponse;
        }
        return new CapturingHttpServletResponseWrapper((HttpServletResponse) response, System::currentTimeMillis);
    }

    private void logTrafficLogV3IfNeeded(CapturingHttpServletResponseWrapper capturingHttpServletResponseWrapper,
            ServletRequest request) {
        if (TrafficContextThreadLocal.isTrafficLogV3Flow()) {
            RepoPath repoPath = TrafficContextThreadLocal.getRepoPath();
            if (repoPath == null) {
                log.warn("Skipping traffic log v3 entry creation, repoPath is null");
                return;
            }
            var httpServletRequest = (HttpServletRequest) request;
            String originRepoKey = RepoKeyExtractor.extractFromRequestPath(httpServletRequest.getPathInfo());
            TrafficContextThreadLocal.fillOriginFields(originRepoKey);
            long transferredSize = capturingHttpServletResponseWrapper.getContentLength();
            TrafficEntry trafficEntry = TrafficEntry.builder()
                    .action(TrafficEntry.TrafficAction.DOWNLOAD)
                    .actor(TrafficEntry.TrafficActor.USER)
                    .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                    .userAddress(HttpUtils.getRemoteClientAddress())
                    .repoPath(repoPath.getId())
                    .size(transferredSize)
                    .requestedSize(transferredSize)
                    .transferredSize(transferredSize)
                    .build();
            trafficService.handleTrafficEntryV3(trafficEntry);
        }
    }

    /**
     * Handles extraction of the origin repository key from request URIs.
     */
    @VisibleForTesting
    @UtilityClass
    static class RepoKeyExtractor {

        private static final String TERRAFORM = "terraform";
        private static final String MODULES = "modules";
        private static final String PROVIDERS = "providers";
        private static final String REMOTE = "remote";
        private static final String STATE_VERSIONS = "state-versions";
        private static final String WORKSPACES = "workspaces";
        private static final String ORGANIZATIONS = "organizations";
        private static final String UNDERSCORE_DELIMITER = "__";
        private static final String SPECIAL_DELIMITER = "-rt-ns-";

        /**
         * Extracts the origin repository key from the request URI.
         * Terraform requests have a unique path structure that differs from other repository types.
         * For example, Terraform module and provider requests encode the repository key within specific
         * segments of the path, often using delimiters such as "__" or "-rt-ns-". As a result, resolving
         * the repository key for Terraform requests requires custom logic that parses these segments
         * differently than the default approach used for other repository types.
         *
         * @param requestURI the full request URI
         * @return the extracted origin repository key
         */
        static String extractFromRequestPath(String requestURI) {
            String[] pathParts = requestURI.split("/");
            if (pathParts.length <= 1) {
                throw new IllegalArgumentException(
                        "Cannot extract origin repo key from request URI: " + requestURI);
            }

            String repoType = pathParts[1]; // e.g., "terraform", "docker", etc.
            if (TERRAFORM.equalsIgnoreCase(repoType)) {
                return extractFromTerraformRequest(pathParts);
            } else {
                return pathParts[2]; // Default behavior for other repo types
            }
        }

        private static String extractFromTerraformRequest(String[] pathParts) {
            if (MODULES.equalsIgnoreCase(pathParts[3])) {
                return pathParts[4].split(UNDERSCORE_DELIMITER)[0];
            }
            if (PROVIDERS.equalsIgnoreCase(pathParts[3])) {
                return pathParts[4].split(SPECIAL_DELIMITER)[0];
            }
            if (REMOTE.equalsIgnoreCase(pathParts[2])) {
                if (List.of(STATE_VERSIONS, WORKSPACES).contains(pathParts[4])) {
                    return pathParts[5].split(UNDERSCORE_DELIMITER)[0];
                }
                if (ORGANIZATIONS.equalsIgnoreCase(pathParts[4])) {
                    return pathParts[5];
                }
            }
            log.warn("Couldn't extract Terraform repo key from path: {}", String.join("/", pathParts));
            return "";
        }
    }

}
