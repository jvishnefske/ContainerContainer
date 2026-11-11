package org.artifactory.webapp.servlet.util;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.authentication.ArtifactoryBasicAuthenticationFilter;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.BrowserRequestHelper;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.artifactory.webapp.servlet.RequestUtils;
import org.artifactory.webapp.servlet.authentication.ArtifactoryAccessTokenAuthenticationFilter;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.UnsupportedEncodingException;

import static org.artifactory.webapp.servlet.RequestUtils.getServletPathFromRequest;
import static org.jfrog.access.common.RestConstants.VERSION_HEADER;

/**
 * @author Mor Merhav
 */
public final class ServletUtils {

    private static final Logger log = LoggerFactory.getLogger(ServletUtils.class);
    private static final String UI_NATIVE_BROWSER = "/ui/native";
    private static final String SEC_FETCH_MODE_HEADER = "Sec-Fetch-Mode";
    private static final String SEC_FETCH_MODE_NAVIGATE = "navigate";
    public static final String ATTR_REQUEST_START_TIME = "request-start-time";

    private ServletUtils() {

    }

    /**
     * @return login identifier
     */
    public static String getLoginIdentifier(ServletRequest servletRequest, ArtifactoryFilterHolder filterHolder) {

        String loginIdentifier = filterHolder.getCacheKey(servletRequest);
        try {
            // fetch context LoginIdentifier
            loginIdentifier = filterHolder.getLoginIdentifier(servletRequest);
            Filter filter = filterHolder.getFilter();
            if (filter instanceof ArtifactoryAccessTokenAuthenticationFilter ||
                    filter instanceof ArtifactoryBasicAuthenticationFilter) {
                log.debug("Found loginIdentifier {}", loginIdentifier);
            }
        } catch (BadCredentialsException e) {
            log.debug("Resolving uses access details has failed, {}", e.getMessage());
            if (loginIdentifier == null) {loginIdentifier = "";}
        }
        return loginIdentifier;
    }

    public static void addSensitiveHeadersToResponse(ServletRequest request, ServletResponse response,
            ArtifactoryContext artifactoryContext) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            String requestURI = ((HttpServletRequest) request).getRequestURI();
            if (isBinaryStoreServletRequest(request.getServletContext().getContextPath(), requestURI)) {
                addBinStoreHeaders(httpResponse);
            } else {
                addArtifactoryHeaders(httpResponse, artifactoryContext);
            }
        }
    }

    public static boolean isBinaryStoreServletRequest(@NotNull String servletContextPath, @NotNull String requestURI) {
        return requestURI.startsWith(String.join("/", servletContextPath, "binarystore"));
    }

    private static void addArtifactoryHeaders(HttpServletResponse httpResponse, ArtifactoryContext artifactoryContext) {
        if (ConstantValues.addTraceIdResponseHeader.getBoolean()) {
            httpResponse.setHeader(ArtifactoryResponse.ARTIFACTORY_TRACE_ID, MDC.get("uber-trace-id"));
        }

        if (!httpResponse.containsHeader(VERSION_HEADER)) {
            //Add the server header (curl -I http://localhost:8080/artifactory/)
            httpResponse.setHeader(VERSION_HEADER, HttpUtils.getArtifactoryUserAgent());
        }

        // set the Artifactory instance id header
        String hostId =
                artifactoryContext.beanForType(AddonsManager.class).addonByType(HaCommonAddon.class)
                        .getHostId();
        httpResponse.setHeader(ArtifactoryResponse.ARTIFACTORY_ID, hostId);

        String serverId = artifactoryContext.getServerId();
        if (StringUtils.isNotBlank(serverId) && !ArtifactoryHome.ARTIFACTORY_PRO.equals(serverId)) {
            httpResponse.setHeader(HaCommonAddon.ARTIFACTORY_NODE_ID, serverId);
        }
    }

    private static void addBinStoreHeaders(HttpServletResponse httpResponse) {
        if (!httpResponse.containsHeader(VERSION_HEADER)) {
            httpResponse.setHeader(VERSION_HEADER, "BinaryStore/1.0");
        }
    }

    public static boolean shouldRedirectToUiNativeBrowserRelativeToAuthChain(HttpServletRequest request,
            boolean afterAuthChain) {
        if (!shouldRedirectToUiNativeBrowser(request)) {
            return false;
        }
        if (afterAuthChain) {
            // After authChain we know the user is unauthorized.
            // Therefore, we don't care if it is a file or folder request.
            return true;
        }
        try {
            ArtifactoryRequest artifactoryRequest = new HttpArtifactoryRequest(request);
            return artifactoryRequest.isDirectoryRequest();
        } catch (UnsupportedEncodingException e) {
            log.debug("", e);
            return false;
        }
    }

    public static boolean shouldRedirectToUiNativeBrowser(HttpServletRequest request) {
        if (!ConstantValues.redirectNativeBrowserRequestsToUi.getBoolean()) {
            return false;
        }
        String userAgent = HttpUtils.getRequestUserAgent(request);
        if (!BrowserRequestHelper.isBrowserUserAgent(userAgent)) {
            return false;
        }
        if (!isTopLevelBrowserNavigation(request)) {
            return false;
        }
        String servletPath = getServletPathFromRequest(request);
        boolean repoRequest = servletPath != null && RequestUtils.isRepoRequest(request, false);
        if (!repoRequest || !HttpMethod.GET.equals(request.getMethod())) {
            return false;
        }

        return true;
    }

    /**
     * Distinguishes a real top-level browser navigation (address bar / link click) from a programmatic
     * request issued by page scripts (fetch/XHR). Relies on the Fetch Metadata request headers that all
     * modern browsers send: a navigation carries {@code Sec-Fetch-Mode: navigate}, whereas fetch()/XHR
     * carry {@code cors}/{@code same-origin}/{@code no-cors}. When the header is absent (legacy clients
     * that predate Fetch Metadata) the previous behavior is preserved by treating the request as a
     * navigation, so old-browser navigations keep redirecting to the native UI.
     */
    private static boolean isTopLevelBrowserNavigation(HttpServletRequest request) {
        String secFetchMode = request.getHeader(SEC_FETCH_MODE_HEADER);
        return secFetchMode == null || SEC_FETCH_MODE_NAVIGATE.equalsIgnoreCase(secFetchMode);
    }

    public static void redirectToNewNativeBrowser(HttpServletRequest request, HttpServletResponse response) {
        String servletPath = getServletPathFromRequest(request);
        String pathPrefix = PathUtils.getFirstPathElement(servletPath);
        if (ArtifactoryRequest.LIST_BROWSING_PATH.equals(pathPrefix)) {
            servletPath = servletPath.substring("list/".length());
        }
        if (ArtifactoryRequest.SIMPLE_BROWSING_PATH.equals(pathPrefix)) {
            servletPath = servletPath.substring("simple/".length());
        }
        String targetUrl = HttpUtils.getPlatformServletContextUrl(request) + UI_NATIVE_BROWSER + servletPath;
        response.setHeader(HttpHeaders.LOCATION, targetUrl);
        response.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
    }
}
