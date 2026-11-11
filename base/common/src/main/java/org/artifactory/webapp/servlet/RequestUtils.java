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


import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.webdav.WebdavService;
import org.artifactory.common.ConstantValues;
import org.artifactory.md.Properties;
import org.artifactory.mime.NamingUtils;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.webapp.servlet.RequestLogSanitizer.getSanitizedUriWithQueryParams;

/**
 * User: freds Date: Aug 13, 2008 Time: 10:56:25 AM
 */
public abstract class RequestUtils {
    private static final String MISSION_CONTROL_URL_PATTERN = "mc";

    static final String ATTR_ARTIFACTORY_REPOSITORY_PATH = "artifactory.repository_path";
    static final String ATTR_ARTIFACTORY_REQUEST_PROPERTIES = "artifactory.request_properties";
    private static final Logger log = LoggerFactory.getLogger(RequestUtils.class);

    private static final Set<String> NON_UI_PATH_PREFIXES = new HashSet<>();
    private static final Set<String> PACKAGES_ENDPOINT_USE_BASIC_AUTH = new HashSet<>();
    private static final Set<String> UI_PATH_PREFIXES = new HashSet<>();
    private static final String DEFAULT_ENCODING = "utf-8";
    public static final String BEARER_AUTHORIZATION_LOWERCASE = "bearer ";
    public static final String BASIC_AUTHORIZATION_HEADER = "Basic ";

    private RequestUtils() {
        // utility class
    }

    public enum RepoRequestAnalysis {
        NOT_A_REPO_REQUEST,
        REPO_REQUEST_REPO_EXISTS,
        REPO_REQUEST_REPO_NOT_EXISTS
    }

    public static void setNonUiPathPrefixes(Collection<String> uriPathPrefixes) {
        NON_UI_PATH_PREFIXES.clear();
        NON_UI_PATH_PREFIXES.addAll(uriPathPrefixes);
    }

    public static void setUiPathPrefixes(Collection<String> uriPathPrefixes) {
        UI_PATH_PREFIXES.clear();
        UI_PATH_PREFIXES.addAll(uriPathPrefixes);
    }

    public static void setPackagesEndpointUseBasicAuth() {
        PACKAGES_ENDPOINT_USE_BASIC_AUTH.clear();
        PACKAGES_ENDPOINT_USE_BASIC_AUTH.add("/api/docker/");
        PACKAGES_ENDPOINT_USE_BASIC_AUTH.add("/api/oci/");
        PACKAGES_ENDPOINT_USE_BASIC_AUTH.add("/api/nuget/");
        PACKAGES_ENDPOINT_USE_BASIC_AUTH.add("/api/vagrant/");
    }

    @SuppressWarnings({"IfMayBeConditional"})
    public static String getContextPrefix(HttpServletRequest request) {
        String contextPrefix;
        String requestUri = request.getRequestURI();
        int contextPrefixEndIdx = requestUri.indexOf('/', 1);
        if (contextPrefixEndIdx > 0) {
            contextPrefix = requestUri.substring(1, contextPrefixEndIdx);
        } else {
            contextPrefix = "";
        }
        return contextPrefix;
    }

    public static boolean matchPkgEndpoint(HttpServletRequest request, String pkgUri) {
        String requestUri = request.getRequestURI();
        String packageEndPoint = request.getContextPath() + pkgUri;
        return requestUri.startsWith(packageEndPoint);
    }


    public static boolean pkgEndpointMatchBasicAuth(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return PACKAGES_ENDPOINT_USE_BASIC_AUTH.stream().anyMatch(pkgUri ->
                requestUri.startsWith(request.getContextPath() + pkgUri));
    }

    public static boolean isRepoRequest(HttpServletRequest request) {
        return isRepoRequest(request, false);
    }

    public static boolean isRepoRequest(HttpServletRequest request, boolean warnIfRepoDoesNotExist) {
        String servletPath = getServletPathFromRequest(request);
        String repoKey = extractRepoKey(servletPath);
        if (repoKey == null) {
            return false;
        }
        RepositoryService repositoryService = ContextHelper.get().getRepositoryService();
        Set<String> allRepos = repositoryService.getAllRepoKeys();
        if (!allRepos.contains(repoKey)) {
            if (warnIfRepoDoesNotExist) {
                log.warn("Request " + servletPath + " should be a repo request and does not match any repo key");
            }
            return false;
        }
        return true;
    }

    /**
     * Analyzes the request to determine if it is a repository request.
     * @return RepoRequestAnalysis indicating the type of request.
     *        - NOT_A_REPO_REQUEST: The request is not a repository request.
     *        - REPO_REQUEST_REPO_EXISTS: The request is a repository request and the repository exists.
     *        - REPO_REQUEST_REPO_NOT_EXISTS: The request is a repository request but the repository does not exist.
     * Specifically checks for the Mission Control URL pattern to determine if it is a repository request.
     * This is needed because "mc" is not a reserved name (though it should have been), and can either be an MC
     * request or a repo request for the "mc" repo.
     * If the repository key is null or a reserved name, it returns NOT_A_REPO_REQUEST.
     * If the repository exists, it returns REPO_REQUEST_REPO_EXISTS.
     * If the repository does not exist, it checks if it is a Mission Control request and
     * returns NOT_A_REPO_REQUEST if it is, otherwise returns REPO_REQUEST_REPO_NOT_EXISTS.
     */
    public static RepoRequestAnalysis analyzeRepoRequest(HttpServletRequest request) {
        String servletPath = getServletPathFromRequest(request);
        String repoKey = extractRepoKey(servletPath);
        if (repoKey == null) {
            return RepoRequestAnalysis.NOT_A_REPO_REQUEST;
        }
        RepositoryService repositoryService = ContextHelper.get().getRepositoryService();
        if (!repositoryService.repoExists(repoKey)) {
            if (isMcRequest(repoKey)) {
                return RepoRequestAnalysis.NOT_A_REPO_REQUEST;
            }
            log.warn("Request: '{}' should be a repo request and does not match any repo key", servletPath);
            return RepoRequestAnalysis.REPO_REQUEST_REPO_NOT_EXISTS;
        }
        return RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS;
    }

    private static boolean isMcRequest(String repoKey) {
        return repoKey.equals(MISSION_CONTROL_URL_PATTERN);
    }

    public static boolean isWebdavRequest(HttpServletRequest request) {
        if (!isRepoRequest(request)) {
            return false;
        }
        if (WebdavService.WEBDAV_METHODS.contains(request.getMethod().toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        String wagonProvider = request.getHeader("X-wagon-provider");
        return wagonProvider != null && wagonProvider.contains("webdav");
    }

    public static boolean isUiRequest(HttpServletRequest request) {
        if (isWebdavRequest(request)) {
            return false;
        }
        String pathPrefix = PathUtils.getFirstPathElement(getServletPathFromRequest(request));
        return isUiPathPrefix(pathPrefix);
    }

    public static boolean isUiPathPrefix(String pathPrefix) {
        if (UI_PATH_PREFIXES.contains(pathPrefix)) {
            return true;
        }
        if (NON_UI_PATH_PREFIXES.contains(pathPrefix)) {
            return false;
        }
        return false;
    }

    public static boolean isBasicAuthHeaderPresent(HttpServletRequest request) {
        return getBasicAuthorization(request).isPresent();
    }

    public static Optional<String> getBasicAuthorization(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(value -> value.startsWith("Basic "))
                .map(value -> value.substring(BASIC_AUTHORIZATION_HEADER.length()))
                .filter(StringUtils::isNotBlank)
                .filter(value -> !"Og==".equals(value));
    }

    public static boolean isBearerAuthentication(HttpServletRequest request) {
        return getBearerAuthenticationValue(request).isPresent();
    }

    public static Optional<String> getBearerAuthenticationValue(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(value -> value.toLowerCase().startsWith(BEARER_AUTHORIZATION_LOWERCASE))
                .map(value -> value.substring(BEARER_AUTHORIZATION_LOWERCASE.length()))
                .filter(StringUtils::isNotBlank);
    }

    /**
     * Returns the un-decoded servlet path from the request
     *
     * @param req The received request
     * @return String - Servlet path
     */
    public static String getServletPathFromRequest(HttpServletRequest req) {
        return getServletPathFromRequest(req, false);
    }

    /**
     * Returns the un-decoded servlet path from the request
     *
     * @param req          The received request
     * @param includeQuery - whether to include the query params in the path
     * @return String - Servlet path
     */
    public static String getServletPathFromRequest(HttpServletRequest req, boolean includeQuery) {
        String contextPath = req.getContextPath();
        String uri = req.getRequestURI();
        if (includeQuery) {
            uri = getSanitizedUriWithQueryParams(req);
        }
        if (StringUtils.isBlank(contextPath)) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    /**
     * @param servletContext The servlet context
     * @return The artifactory spring context
     */
    public static ArtifactoryContext getArtifactoryContext(ServletContext servletContext) {
        return (ArtifactoryContext) servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY);
    }

    /**
     * Extract the username out of the request, by checking the header for the {@code Authorization} and then if it
     * starts with {@code Basic} get it as a base 64 token and decode it.
     *
     * @param request The request to examine
     * @return The extracted username
     */
    public static String extractUsernameFromRequest(ServletRequest request) {
        return getUsernameOrPasswordFromToken(request, true);
    }

    /**
     * Extract the password out of the request, by checking the header for the {@code Authorization} and then if it
     * starts with {@code Basic} get it as a base 64 token and decode it.
     *
     * @param request The request to examine
     * @return The extracted password
     */
    public static String extractPasswordFromRequest(ServletRequest request) {
        return getUsernameOrPasswordFromToken(request, false);
    }

    private static String getUsernameOrPasswordFromToken(ServletRequest request, boolean isUsername) {
        String header = ((HttpServletRequest) request).getHeader(HttpHeaders.AUTHORIZATION);
        if ((header != null) && header.startsWith("Basic ")) {
            String token;
            byte[] base64Token;
            try {
                base64Token = header.substring(6).getBytes(DEFAULT_ENCODING);
                token = new String(org.apache.commons.codec.binary.Base64.decodeBase64(base64Token), DEFAULT_ENCODING);
            } catch (UnsupportedEncodingException e) {
                log.info("the encoding is not supported");
                return EMPTY;
            }
            String res = EMPTY;
            int delim = token.indexOf(':');
            if (delim != -1) {
                res = isUsername ? token.substring(0, delim) : token.substring(delim + 1);
            }
            return res;
        }
        return EMPTY;
    }

    /**
     * add no cache and no store header to response in order to avoid java script caching on browser.
     * In addition adds a compatibility header for IE (where needed) to avoid default compatibility view for
     * intranet sites.
     *
     * @param request  - http servlet request
     * @param response - http servlet response
     */
    public static void addAdditionalHeadersToWebAppRequest(HttpServletRequest request, HttpServletResponse response) {
        String servletPath = RequestUtils.getServletPathFromRequest(request);
        if (servletPath.contains(HttpUtils.WEBAPP_URL_PATH_PREFIX)) {
            verifyExplorerUserAgentAndSetHeader(request, response);
            if (servletPath.endsWith("/app.html") || servletPath.equals("/webapp/")) {
                // don't store (cache) the app.html in the browser. other resources contain unique version identifier
                response.setHeader("Cache-Control", "no-store,max-age=0");
            }
            if (!ConstantValues.enableUiPagesInIframe.getBoolean()) {
                response.setHeader("X-FRAME-OPTIONS", "DENY");
            }
        }
    }

    /**
     * Verifies user agent is Internet Explorer according to:
     * https://msdn.microsoft.com/en-us/library/hh869301(v=vs.85).aspx
     * https://msdn.microsoft.com/en-us/library/ms537503(v=vs.85).aspx
     * http://www.useragentstring.com/pages/Internet%20Explorer/
     *
     * And adds the compatibility header to avoid explorer defaulting to IE7 mode when launching compatibility view.
     * see RTFACT-7928
     */
    private static void verifyExplorerUserAgentAndSetHeader(HttpServletRequest request, HttpServletResponse response) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (isNewExplorer(userAgent)) {
            response.setHeader("X-UA-Compatible", "IE=Edge");
        }
    }

    private static boolean isNewExplorer(String userAgent) {
        return StringUtils.isNotEmpty(userAgent) && (userAgent.contains("MSIE") || userAgent.contains("Trident")
                || (userAgent.contains("Windows") && userAgent.contains("Edge"))
                || userAgent.contains("IEMobile"));
    }

    /**
     * Extracts the repository key from the servlet path.
     * <p>
     * The method handles special cases for list and simple browsing paths, strips reserved names and metadata from
     * the path, and decodes the path using UTF-8 encoding.
     *
     * @param servletPath The servlet path to extract the repository key from.
     * @return The extracted repository key or null if it cannot be determined.
     */
    static String extractRepoKey(String servletPath) {
        String pathPrefix = PathUtils.getFirstPathElement(servletPath);
        if (StringUtils.isBlank(pathPrefix)) {
            return null;
        }
        if (ArtifactoryHeaders.LIST_BROWSING_PATH.equals(pathPrefix)) {
            pathPrefix = PathUtils.getFirstPathElement(servletPath.substring("list/".length()));
        }
        if (ArtifactoryHeaders.SIMPLE_BROWSING_PATH.equals(pathPrefix)) {
            pathPrefix = PathUtils.getFirstPathElement(servletPath.substring("simple/".length()));
        }
        if (UI_PATH_PREFIXES.contains(pathPrefix)) {
            return null;
        }
        if (NON_UI_PATH_PREFIXES.contains(pathPrefix)) {
            return null;
        }
        //Support repository-level metadata requests
        pathPrefix = NamingUtils.stripMetadataFromPath(pathPrefix);
        //Strip any matrix params
        int paramsIdx = pathPrefix.indexOf(Properties.MATRIX_PARAMS_SEP);
        if (paramsIdx > 0) {
            pathPrefix = pathPrefix.substring(0, paramsIdx);
        }
        try {
            pathPrefix = URLDecoder.decode(pathPrefix, StandardCharsets.UTF_8);
        } catch (NullPointerException | IllegalArgumentException e) {
            log.warn("Could not decode repo key '{}' in utf-8", pathPrefix);
            return null;
        }
        return pathPrefix;
    }
}
