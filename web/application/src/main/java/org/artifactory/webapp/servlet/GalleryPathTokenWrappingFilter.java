package org.artifactory.webapp.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.OauthManager;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * Handles gallery requests where the token is embedded in the URL path.
 *
 * Some clients send requests like:
 * /_apis/public/gallery/<token>/vscode/...
 * or package-handler-rewritten asset URLs:
 * /api/aieditorextensions/&lt;repoKey&gt;/&lt;token&gt;/https/...
 *
 * But downstream code in Artifactory expects the same request in the usual form:
 * /_apis/public/gallery/vscode/...
 * together with:
 * Authorization: Bearer <token>
 *
 * This filter translates between those two shapes.
 *
 * If the first segment after the gallery prefix looks like an embedded token,
 * the filter removes it from the URI and exposes it as an Authorization header.
 *
 * If the request does not match that pattern, it is passed through unchanged.
 *
 * Important:
 * Not every segment after /_apis/public/gallery/ is a token (for example publisher slugs).
 * Rewriting runs only when the URI contains /aieditorextensions/, the candidate segment is an Artifactory
 * reference access token (typing + verify via AccessService), and at least one JWT scope string contains
 * "artifact:" plus the repository key taken from the first path segment after /aieditorextensions/.
 *
 * The filter is expected to run early enough so the rest of the filter chain
 * and Jersey see the normalized request.
 */
@Slf4j
public class GalleryPathTokenWrappingFilter implements Filter {

    private static final String GALLERY_PATH_PREFIX = "/_apis/public/gallery/";
    private static final String AI_EDITOR_EXTENSIONS_PATH_MARKER = "/aieditorextensions/";
    private static final String AI_EDITOR_EXTENSIONS_API_PREFIX = "/api" + AI_EDITOR_EXTENSIONS_PATH_MARKER;

    /**
     * Captures the repository key: first segment in the URI after the literal /aieditorextensions/
     */
    private static final Pattern REPO_KEY_AFTER_AI_EDITOR_EXTENSIONS =
            Pattern.compile(Pattern.quote(AI_EDITOR_EXTENSIONS_PATH_MARKER) + "([^/]+)");
    /**
     * Package-handler rewritten asset URLs: /api/aieditorextensions/<repoKey>/<access-token>/https/
     * Group 1 = prefix before /api/aieditorextensions/, group 2 = repo key, group 3 = candidate token,
     * group 4 = remainder (optional slash + upstream path).
     */
    private static final Pattern AI_EDITOR_EXTENSIONS_API_REPO_TOKEN_AND_REST =
            Pattern.compile("^(.*?)" + Pattern.quote(AI_EDITOR_EXTENSIONS_API_PREFIX) + "([^/]+)/([^/]+)(\\/?.*)$");
    /**
     * Matches request URI with groups: (1) before /_apis/public/gallery/, (2) next segment, (3) rest of path.
     * Non-greedy match so the first gallery/ occurrence wins when the path contains it more than once.
     */
    private static final Pattern GALLERY_PATH_WITH_FIRST_SEGMENT =
            Pattern.compile("^(.*?)" + Pattern.quote(GALLERY_PATH_PREFIX) + "([^/]+)(\\/?.*)$");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest incomingRequest = (HttpServletRequest) request;
        // Either returns the original request, or a wrapped request with a clean URI and bearer header.
        HttpServletRequest requestForDownstream = wrapGalleryPathBearer(incomingRequest);
        chain.doFilter(requestForDownstream, response);
    }

    /**
     * Detects gallery requests that carry the token in the path and wraps them so downstream code sees:
     * - the URI without the token segment
     * - Authorization: Bearer <token>
     * If the request does not match the token-in-path shape, the original request is returned unchanged.
     */
    public static HttpServletRequest wrapGalleryPathBearer(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (!requestUri.contains(AI_EDITOR_EXTENSIONS_PATH_MARKER)) {
            return request;
        }

        String repoKey = repoKeySegmentAfterAiEditorExtensions(requestUri);

        // Open VSX gallery URL: token is the first segment right after /_apis/public/gallery/
        Matcher galleryPath = GALLERY_PATH_WITH_FIRST_SEGMENT.matcher(requestUri);
        if (galleryPath.matches()) {
            String candidateToken = galleryPath.group(2);
            if (isEmbeddableAccessCredential(request, candidateToken, repoKey)) {
                String sanitizedUri = requestUriWithoutFirstGallerySegment(galleryPath);
                log.debug("Gallery path-token: sanitizedUri={}", sanitizedUri);
                return buildWrappedRequest(request, sanitizedUri, OauthManager.OAUTH_TOKEN_PREFIX + candidateToken);
            }
        }

        // Package-handler asset URL: /api/aieditorextensions/<repo>/<token>/https/— token is its own segment.
        Matcher apiExtensionsPath = AI_EDITOR_EXTENSIONS_API_REPO_TOKEN_AND_REST.matcher(requestUri);
        if (apiExtensionsPath.matches()) {
            String candidateToken = apiExtensionsPath.group(3);
            if (isEmbeddableAccessCredential(request, candidateToken, repoKey)) {
                String sanitizedUri = requestUriWithoutEmbeddedApiExtensionsToken(apiExtensionsPath);
                log.debug("AI Editor Extensions API path-token: sanitizedUri={}", sanitizedUri);
                return buildWrappedRequest(request, sanitizedUri, OauthManager.OAUTH_TOKEN_PREFIX + candidateToken);
            }
        }
        return request;
    }

    /**
     * Builds the same URI shape as HttpServletRequest.getRequestURI() would return, but without the first segment
     * after /_apis/public/gallery/ (typically the embedded token). The galleryPath matcher must already have matched
     * GALLERY_PATH_WITH_FIRST_SEGMENT.
     */
    private static String requestUriWithoutFirstGallerySegment(Matcher galleryPath) {
        String prefix = galleryPath.group(1);
        String rest = StringUtils.removeStart(galleryPath.group(3), "/");
        return prefix + GALLERY_PATH_PREFIX + rest;
    }

    private static String requestUriWithoutEmbeddedApiExtensionsToken(Matcher apiPath) {
        String prefix = apiPath.group(1);
        String repoKey = apiPath.group(2);
        String rest = StringUtils.removeStart(apiPath.group(4), "/");
        return prefix + AI_EDITOR_EXTENSIONS_API_PREFIX + repoKey + "/" + rest;
    }

    /**
     * Returns the first path segment (repo key) after the literal /aieditorextensions/, or null if missing/blank.
     */
    private static String repoKeySegmentAfterAiEditorExtensions(String requestUri) {
        Matcher repoNameMatch = REPO_KEY_AFTER_AI_EDITOR_EXTENSIONS.matcher(requestUri);
        if (!repoNameMatch.find()) {
            return null;
        }
        String repoKey = repoNameMatch.group(1);
        return StringUtils.isBlank(repoKey) ? null : repoKey;
    }

    /**
     * Whether tokenValue from a single URI segment is a real Artifactory access token whose scopes mention
     * artifact:<repoKey>:... Slashes/backslashes are rejected because a gallery path segment cannot span them.
     */
    private static boolean isEmbeddableAccessCredential(HttpServletRequest request, String tokenValue, String repoKey) {
        if (StringUtils.isBlank(tokenValue) || tokenValue.contains("/") || tokenValue.contains("\\") ||
                StringUtils.isBlank(repoKey)) {
            return false;
        }
        AccessService accessService = getAccessService(request);
        if (accessService == null) {
            log.debug("Path-token wrap skipped: no ArtifactoryContext on ServletContext");
            return false;
        }
        return isAccessTokenForRepo(accessService, tokenValue, repoKey);
    }

    /**
     * Returns true when the string is a reference access token that verifies, yields a non-null JWT, and at least one
     * entry in that JWT's scope list contains the substring "artifact:" immediately followed by the repo key
     */
    private static boolean isAccessTokenForRepo(AccessService accessService, String tokenValue, String repoKey) {
        try {
            if (!isReferenceToken(accessService, tokenValue)) {
                return false;
            }

            TokenVerifyResult verifyResult = accessService.verifyAndGetResult(tokenValue);
            if (!isVerifiedSuccessfully(verifyResult)) {
                return false;
            }

            JwtAccessToken accessToken = verifyResult.getAccessToken();
            if (accessToken == null) {
                return false;
            }

            return hasArtifactScopeForRepo(accessToken, repoKey);

        } catch (RuntimeException e) {
            debugOrWarn(log, "Gallery path segment could not be parsed as an access token", e);
            return false;
        }
    }

    private static boolean isReferenceToken(AccessService accessService, String tokenValue) {
        boolean isReference = accessService.getAccessTokenType(tokenValue) == AccessTokenType.REFERENCE_TOKEN;
        if (!isReference) {
            log.debug("Gallery path segment is not a valid access token; skipping path-token wrap.");
        }
        return isReference;
    }

    private static boolean isVerifiedSuccessfully(TokenVerifyResult result) {
        return result != null && result.isSuccessful();
    }

    private static boolean hasArtifactScopeForRepo(JwtAccessToken token, String repoKey) {
        boolean hasScope = token.getScope().stream().anyMatch(scope -> artifactScopeTargetsRepo(scope, repoKey));
        if (!hasScope) {
            log.warn("Gallery path access token has no artifact scope for repo '{}'; skipping path-token wrap.",
                    repoKey);
        }
        return hasScope;
    }

    private static boolean artifactScopeTargetsRepo(String scope, String repoKey) {
        return StringUtils.isNotBlank(scope) && StringUtils.isNotBlank(repoKey) && scope.contains(repoKey);
    }

    private static AccessService getAccessService(HttpServletRequest request) {
        ServletContext servletContext = request.getServletContext();
        if (servletContext == null) {
            return null;
        }
        ArtifactoryContext artifactoryContext = RequestUtils.getArtifactoryContext(servletContext);
        return artifactoryContext != null ? artifactoryContext.beanForType(AccessService.class) : null;
    }

    /**
     * Wraps the request so downstream code sees a consistent token-free request.
     * The wrapper does three things:
     * 1. Returns the sanitized URI from getRequestURI()
     * 2. Exposes the token as Authorization: Bearer ...
     * 3. Updates servletPath and pathInfo to match the sanitized URI
     *
     * The servletPath/pathInfo part is important.
     * Some routing layers do not rely only on getRequestURI(); they also use
     * the servlet dispatch split:
     *
     * requestURI = contextPath + servletPath + pathInfo
     *
     * If we only fix getRequestURI() but leave servletPath/pathInfo inconsistent,
     * routing may still behave as if the token is still in the path.
     */
    private static HttpServletRequest buildWrappedRequest(HttpServletRequest originalRequest,
            String sanitizedRequestUri, String authorizationHeaderValue) {

        // Servlet spec split anchor: contextPath + servletPath (null-safe) to parse sanitizedRequestUri consistently.
        String contextPath = StringUtils.defaultString(originalRequest.getContextPath());
        String originalServletPath = StringUtils.defaultString(originalRequest.getServletPath());
        String contextAndServletPath = contextPath + originalServletPath;

        final String wrappedServletPath;
        final String wrappedPathInfo;

        if (sanitizedRequestUri.startsWith(contextAndServletPath)) {
            // Normal: sanitized URI still matches original prefix-> keep servletPath and derive pathInfo from the
            // remainder
            wrappedServletPath = originalServletPath;
            String remainingPath = sanitizedRequestUri.substring(contextAndServletPath.length());
            wrappedPathInfo = remainingPath.isEmpty() ? null : remainingPath;
        } else {
            // Fallback: prefix mismatch-> original split is invalid, rebuild servletPath and pathInfo from sanitized
            // URI
            String pathAfterContext = sanitizedRequestUri.substring(contextPath.length());
            int firstSlashAfterMount = pathAfterContext.indexOf('/', 1);
            if (firstSlashAfterMount <= 0) { // No additional '/' -> entire path is servletPath, no pathInfo
                wrappedServletPath = pathAfterContext;
                wrappedPathInfo = null;
            } else {
                // Split into servletPath and pathInfo based on first slash position
                wrappedServletPath = pathAfterContext.substring(0, firstSlashAfterMount);
                String remainingPath = pathAfterContext.substring(firstSlashAfterMount);
                wrappedPathInfo = remainingPath.isEmpty() ? null : remainingPath;
            }
        }

        return new HttpServletRequestWrapper(originalRequest) {

            @Override
            public String getRequestURI() {
                // Downstream should see the path without the embedded token.
                return sanitizedRequestUri;
            }

            @Override
            public String getServletPath() {
                // Keep servlet dispatch aligned with the sanitized request shape.
                return wrappedServletPath;
            }

            @Override
            public String getPathInfo() {
                // Keep the remainder of the routed path aligned with the sanitized URI.
                return wrappedPathInfo;
            }

            @Override
            public String getHeader(String name) {
                // Authorization must come from the path token for wrapped requests.
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return authorizationHeaderValue;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                // Same behavior for callers that read Authorization as a header list.
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return Collections.enumeration(Collections.singletonList(authorizationHeaderValue));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                // Make sure Authorization appears in header names even if it did not exist originally.
                List<String> headerNames = new ArrayList<>(Collections.list(super.getHeaderNames()));
                if (headerNames.stream().noneMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase)) {
                    headerNames.add(HttpHeaders.AUTHORIZATION);
                }
                return Collections.enumeration(headerNames);
            }
        };
    }
}