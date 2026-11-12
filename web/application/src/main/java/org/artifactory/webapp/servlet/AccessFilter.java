/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityListener;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.AuthenticationFilterUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.ExpiringAuthentication;
import org.artifactory.security.HttpAuthenticationDetailsSource;
import org.artifactory.security.UserInfo;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.SecurityUtils;
import org.artifactory.util.SessionUtils;
import org.artifactory.util.TomcatListeningDetector;
import org.artifactory.util.UiRequestUtils;
import org.artifactory.webapp.servlet.authentication.ArtifactoryAuthenticationFilterChain;
import org.artifactory.webapp.servlet.authentication.interceptor.anonymous.AnonymousAuthenticationInterceptor;
import org.artifactory.webapp.servlet.authentication.interceptor.anonymous.AnonymousAuthenticationInterceptors;
import org.artifactory.webapp.servlet.authentication.interceptor.anonymous.ForceAnonymousAuthenticationInterceptor;
import org.artifactory.webapp.servlet.authentication.interceptor.anonymous.ForceAnonymousAuthenticationInterceptors;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.jfrog.common.CachedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.artifactory.security.AuthenticationHelper.isJFrogFrontendAuthentication;
import static org.artifactory.security.UserInfo.SIGNED_ANONYMOUS;
import static org.artifactory.webapp.servlet.util.ServletUtils.redirectToNewNativeBrowser;
import static org.artifactory.webapp.servlet.util.ServletUtils.shouldRedirectToUiNativeBrowserRelativeToAuthChain;

/**
 * @author Yoav Landman
 */
public class AccessFilter extends DelayedFilterBase implements SecurityListener {
    private static final Logger log = LoggerFactory.getLogger(AccessFilter.class);

    public static final String AUTHENTICATED_USERNAME_ATTRIBUTE = "authenticated_username";

    private ArtifactoryContext context;
    private ArtifactoryAuthenticationFilterChain authFilterChain;
    private BasicAuthenticationEntryPoint authenticationEntryPoint;
    private AnonymousAuthenticationInterceptors authInterceptors;
    private ForceAnonymousAuthenticationInterceptors forceAnonymousAuthInterceptors;

    /**
     * holds cached Authentication instances for the non ui requests based on the Authorization header and client ip
     */
    private ConcurrentMap<AuthCacheKey, Authentication> nonUiAuthCache;
    private ConcurrentMap<AuthCacheKey, Authentication> uiAuthCache;
    private ConcurrentMap<String, AuthenticationCache> userChangedCache;
    private CachedValue<Integer> artifactoryPortCache;
    private FilterConfig filterConfig;

    private Pattern openidPattern;

    @Override
    public void initLater(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        ServletContext servletContext = filterConfig.getServletContext();
        this.context = RequestUtils.getArtifactoryContext(servletContext);
        this.authenticationEntryPoint = context.beanForType(BasicAuthenticationEntryPoint.class);
        this.authFilterChain = new ArtifactoryAuthenticationFilterChain(authenticationEntryPoint);
        // Add all the authentication filters
        authFilterChain.addFilters(context.beansForType(ArtifactoryAuthenticationFilter.class).values(),
                context.beansForType(ArtifactoryAuthenticationContextFilter.class).values());
        initCaches(filterConfig);
        authFilterChain.init(filterConfig);
        authInterceptors = new AnonymousAuthenticationInterceptors();
        RequestUtils.setPackagesEndpointUseBasicAuth();
        authInterceptors.addInterceptors(context.beansForType(AnonymousAuthenticationInterceptor.class).values());
        forceAnonymousAuthInterceptors = new ForceAnonymousAuthenticationInterceptors();
        forceAnonymousAuthInterceptors.addInterceptors(
                context.beansForType(ForceAnonymousAuthenticationInterceptor.class).values());
    }

    void initCaches(FilterConfig filterConfig) {
        ArtifactorySystemProperties properties =
                ((ArtifactoryHome) filterConfig.getServletContext().getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR))
                        .getArtifactoryProperties();
        ConstantValues idleTimeSecsProp = ConstantValues.securityAuthenticationCacheIdleTimeSecs;
        long nonUiCacheIdleSecs = properties.getLongProperty(idleTimeSecsProp);
        ConstantValues uiIdleTimeSecsProp = ConstantValues.securityUiAuthenticationCacheIdleTimeSecs;
        long uiCacheIdleSecs = properties.getLongProperty(uiIdleTimeSecsProp);

        ConstantValues initSizeProp = ConstantValues.securityAuthenticationCacheInitSize;
        long initSize = properties.getLongProperty(initSizeProp);
        nonUiAuthCache = CacheBuilder.newBuilder().softValues()
                .initialCapacity((int) initSize)
                .expireAfterWrite(nonUiCacheIdleSecs, TimeUnit.SECONDS)
                .<AuthCacheKey, Authentication>build().asMap();

        uiAuthCache = CacheBuilder.newBuilder().softValues()
                .initialCapacity((int) initSize)
                .expireAfterWrite(uiCacheIdleSecs, TimeUnit.SECONDS)
                .<AuthCacheKey, Authentication>build().asMap();

        userChangedCache = CacheBuilder.newBuilder().softValues()
                .initialCapacity((int) initSize)
                .expireAfterWrite(nonUiCacheIdleSecs, TimeUnit.SECONDS)
                .<String, AuthenticationCache>build().asMap();
        SecurityService securityService = context.beanForType(SecurityService.class);
        securityService.addListener(this);
        this.artifactoryPortCache = CachedValue.loadUsing(TomcatListeningDetector::detectMainListeningPort)
                .expireAfterRefresh(3, TimeUnit.MINUTES)
                .defaultValue(-1)
                .name("ArtifactoryPort")
                .build();
    }

    @Override
    public void onClearSecurity() {
        getNonUiAuthCache().clear();
        getUiAuthCache().clear();
        getUserChangedCache().clear();
    }

    @Override
    public void onUserUpdate(String username) {
        invalidateUserAuthCache(username);
    }

    @Override
    public void onUserDelete(String username) {
        invalidateUserAuthCache(username);
    }

    private void invalidateUserAuthCache(String username) {
        // Flag change to force re-login
        AuthenticationCache authenticationCache = getUserChangedCache().get(username);
        if (authenticationCache != null) {
            authenticationCache.changed();
        }
    }

    @Override
    public void destroy() {
        //May not be inited yet
        if (authFilterChain != null) {
            authFilterChain.destroy();
        }
        if (nonUiAuthCache != null) {
            nonUiAuthCache.clear();
            nonUiAuthCache = null;
        }
        if (uiAuthCache != null) {
            uiAuthCache.clear();
            uiAuthCache = null;
        }
        if (userChangedCache != null) {
            userChangedCache.clear();
            userChangedCache = null;
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (shouldSkipFilter(req)) {
            chain.doFilter(req, resp);
            return;
        }
        doFilterInternal((HttpServletRequest) req, ((HttpServletResponse) resp), chain);
    }

    @Override
    boolean shouldSkipFilter(ServletRequest req) {
        return super.shouldSkipFilter(req) || isOpenidGatewayResource(((HttpServletRequest) req));
    }

    ConcurrentMap<AuthCacheKey, Authentication> getUiAuthCache() {
        log.debug("UI authentication cache accessed");
        return uiAuthCache;
    }

    private ConcurrentMap<AuthCacheKey, Authentication> getNonUiAuthCache() {
        log.debug("Non-UI authentication cache accessed");
        return nonUiAuthCache;
    }

    private ConcurrentMap<String, AuthenticationCache> getUserChangedCache() {
        log.debug("User-Changed authentication cache accessed");
        return userChangedCache;
    }

    /**
     * Special temporary hack for access filter to make sure we use access audience instead of artifactory's
     */
    private boolean isOpenidGatewayResource(HttpServletRequest request) {
        return !isDefaultAuthorizationRequired(request) &&
                getOpenIdPattern().matcher(request.getRequestURI()).matches();
    }

    private Pattern getOpenIdPattern() {
        if (openidPattern == null) {
            String context = HttpUtils.getOverrideContextPath();
            if (!"/artifactory".equals(context)) {
                context = "(" + context + "|/artifactory)";
            }
            openidPattern = Pattern.compile("^" + context + "/api/system/gateway/openid/(.)+");
        }

        return openidPattern;
    }

    private boolean isDefaultAuthorizationRequired(HttpServletRequest request) {
        String defaultAuth = request.getHeader("X-Default-Authorization");
        return "true".equalsIgnoreCase(defaultAuth);
    }

    private void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (shouldRedirectWebapp(request, response, RequestUtils.getServletPathFromRequest(request),
                request.getMethod())) {
            return;
        }
        if (shouldRedirectToUiNativeBrowserRelativeToAuthChain(request, false)) {
            redirectToNewNativeBrowser(request, response);
            return;
        }

        // Reuse the authentication if it exists
        Authentication authentication = SessionUtils.getAuthentication(request);
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated();

        ArtifactoryFilterHolder filterHolder = authFilterChain.acceptFilter(request, response);

        if (filterHolder != null && filterHolder.hasErrorResponse() && response != null) {
            log.debug("Artifactory Filter returned an error in the response object, request uri:{}, status code:{} ",
                    request.getRequestURI(), response.getStatus());
            return;
        }

        // Make sure this is called only once (FRED: it's called twice ?!?)
        boolean reauthRequired = handleReauthentication(request, response, authentication, filterHolder);

        boolean authenticationRequired = !isAuthenticated || reauthRequired;
        if (authenticationRequired) {
            if (forceAnonymousAuthInterceptors.accept(request) ||
                    (filterHolder != null && filterHolder.isForcedAnonymous())) {
                useAnonymousIfPossible(request, response, chain, filterHolder, false);
                return;
            }
            if (filterHolder != null && filterHolder.isAcceptedFilter(request)) {
                authenticateAndExecute(request, response, chain, filterHolder, authFilterChain);
            } else {
                if (shouldRedirectToUiNativeBrowserRelativeToAuthChain(request, true)) {
                    redirectToNewNativeBrowser(request, response);
                    return;
                }
                useAnonymousIfPossible(request, response, chain, filterHolder, true);
            }
        } else {
            log.debug("Using authentication {} from Http session.", authentication);
            useAuthentication(request, response, chain, authentication);
        }
    }

    private boolean handleReauthentication(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication, ArtifactoryFilterHolder filterHolder) {
        if (reAuthenticationRequired(request, authentication, filterHolder)) {
            // A re-authentication is required but we might still have data that needs to be invalidated (like the
            //web session)
            Map<String, LogoutHandler> logoutHandlers = ContextHelper.get().beansForType(LogoutHandler.class);
            for (LogoutHandler logoutHandler : logoutHandlers.values()) {
                logoutHandler.logout(request, response, authentication);
            }

            return true;
        }

        return false;
    }

    private boolean shouldRedirectWebapp(HttpServletRequest request, HttpServletResponse response, String servletPath,
            String method) throws IOException {
        // add no cache header to web app request
        RequestUtils.addAdditionalHeadersToWebAppRequest(request, response);
        if ("get".equalsIgnoreCase(method)) {
            if (("/" + HttpUtils.WEBAPP_URL_PATH_PREFIX + "/").equals(servletPath)) {
                redirectToPlatformUi(request, response);
                return true;
            }
            if (servletPath == null || "/".equals(servletPath) || servletPath.length() == 0 ||
                    servletPath.equals("/" + HttpUtils.WEBAPP_URL_PATH_PREFIX)) {
                //We were called with an empty path - redirect to the app main page
                redirectToPlatformUi(request, response);
                return true;
            }
        }
        return false;
    }

    /**
     * Altering the response so it will redirect to the unified UI path.
     *
     * In case the request comes with Artifactory's port: changes the redirect URL port to be the router one's.
     * Otherwise, the request remains with same port.
     *
     * For example (Artifactory's port here is 8081):
     * http://acme.me/artifactory      --> http://acme.me/ui/
     * http://acme.me:7000/artifactory --> http://acme.me:7000/ui/
     * http://acme.me:8081/artifactory --> http://acme.me:8082/ui/
     * http://acme.me:8082/artifactory --> http://acme.me:8082/ui/
     *
     * Setting the response with redirect and marks it as committed.
     */
    private void redirectToPlatformUi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String baseUrl = HttpUtils.getServletContextUrl(request);
        int routerPort = ArtifactoryHome.get().getSysConfig().helper().getRouterExternalPort();
        int artifactoryPort = artifactoryPortCache.get();
        URL redirectUrl = getPlatformUiUrl(baseUrl, routerPort, artifactoryPort);
        response.sendRedirect(redirectUrl.toExternalForm());
    }

    URL getPlatformUiUrl(String baseUrl, int routerPort, int artifactoryPort) throws MalformedURLException {
        URL url = new URL(baseUrl);
        int urlPort = (artifactoryPort != -1 && artifactoryPort == url.getPort()) ? routerPort : url.getPort();
        log.trace("URL port of '{}' in now {}", baseUrl, urlPort);
        return new URL(url.getProtocol(), url.getHost(), urlPort, HttpUtils.UNIFIED_JFROG_PLATFORM_UI);
    }

    /**
     * Checks the authentication state and verifies the filter doesn't require re-authentication.
     * An {@link ExpiringAuthentication} that expired or was revoked always requires it, so neither the session nor the
     * header cache reuses it.
     */
    private boolean reAuthenticationRequired(HttpServletRequest request, Authentication authentication,
            ArtifactoryFilterHolder filterHolder) {
        // Not authenticated so not required to redo ;-)
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Fork: an authentication from a plugin realm is never reused past its JWT expiry or after a plugin reload
        if (ExpiringAuthentication.isUnusable(authentication, System.currentTimeMillis() / 1000)) {
            return true;
        }

        // If the user object changed in the DB: new groups or became admin since last login,
        // Then we need to force re-authentication
        String username = authentication.getName();
        AuthenticationCache authenticationCache = getUserChangedCache().get(username);
        if (authenticationCache != null && authenticationCache.isChanged(authentication)) {
            authenticationCache.loggedOut(authentication);
            return true;
        }

        if (isAuthenticatedUIRequest(request, filterHolder, authentication)) {
            return false;
        }

        // Ask the filter chain if we need to re authenticate
        return filterHolder == null || filterHolder.doesRequireReAuthentication(request, authentication);
    }

    /**
     * if request related to UI and already authenticated the it do not require re-authentication
     *
     * @param request      - http servlet request
     * @param filterHolder - accepted authentication filter holder if null no filter accept this
     *                     request
     * @return true if require authentication
     */
    private boolean isAuthenticatedUIRequest(HttpServletRequest request, ArtifactoryFilterHolder filterHolder,
            Authentication authentication) {
        // @Todo need to do the ui request identification from the UI by sending to user Agent
        // @Todo header to identified UI request for download and upload requests
        if (filterHolder == null && (authentication.getClass().getSimpleName()
                .equals("HttpSsoAuthenticationToken"))) {
            return false;
        }
        return UiRequestUtils.isUiRestRequest(request) ||
                ((request.getRequestURI().contains(HttpUtils.WEBAPP_URL_PATH_PREFIX)) &&
                        filterHolder == null) || filterHolder == null;
    }

    private void authenticateAndExecute(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            ArtifactoryFilterHolder filterHolder, ArtifactoryAuthenticationFilterChain authFilterChain)
            throws IOException, ServletException {
        String loginIdentifier = ServletUtils.getLoginIdentifier(request, filterHolder);
        if (!SecurityUtils.ensureLoginAttemptAllowed(request, response, loginIdentifier, authenticationEntryPoint)) {
            return;
        }

        // Try to see if authentication in cache based on the hashed header and client ip
        String cacheKey = filterHolder.getCacheKey(request);

        log.debug("Cached key has been found for request: '{}' with method: '{}'",
                request.getRequestURI(), request.getMethod());
        AuthCacheKey authCacheKey = new AuthCacheKey(cacheKey, request.getRemoteAddr());
        Authentication authentication = getCachedAuthentication(authCacheKey);
        if (authentication != null && authentication.isAuthenticated()
                && !isForbiddenAnonymousCacheReuse(authentication, authCacheKey, filterHolder)
                && !reAuthenticationRequired(request, authentication, filterHolder)) {
            log.debug("Header authentication {} found in cache.", authentication);
            if (ArtifactoryHome.get().getSysConfig().helper().exposeApplicationHeaders() ||
                    (isVersionHeaderNeeded(response) && !UserInfo.isAnonymous(authentication.getName()))) {
                ServletUtils.addSensitiveHeadersToResponse(request, response,
                        RequestUtils.getArtifactoryContext(filterConfig.getServletContext()));
            }
            useAuthentication(request, response, chain, authentication);
            // Add to user change cache the login state
            addToUserChange(authentication);
            return;
        }
        if (ConstantValues.skipWWWAuthenticateHeader.getBoolean()
                && !AuthenticationFilterUtils.isAuthHeaderPresent(request)
                && AuthenticationFilterUtils.getTokenKeyValue(request) == null
                && !authCacheKey.hasEmptyHeader()
                && shouldRedirectToUiNativeBrowserRelativeToAuthChain(request, true)) {
            redirectToNewNativeBrowser(request, response);
            return;
        }
        try {
            authFilterChain.doFilter(request, response, loginIdentifier, filterHolder, chain);
        } finally {
            String username = "non_authenticated_user";
            Authentication newAuthentication = SecurityContextHolder.getContext().getAuthentication();
            if (newAuthentication != null && newAuthentication.isAuthenticated()) {
                // Add to user change cache the login state
                AccessLogger.loggedIn(newAuthentication);
                addToUserChange(newAuthentication);
                // Save authentication (if session exists)
                if (SessionUtils.setAuthentication(request, newAuthentication, false)) {
                    log.debug("Added authentication {} in Http session.", newAuthentication);
                    username = newAuthentication.getName();
                } else {
                    // If it did not work use the header cache
                    // An authorization cache key with no header can only be used for Anonymous authentication
                    // SIGNED_ANONYMOUS happen with the SignedUrlServiceImpl when creating a signed token for
                    // anonymous user, we do not want to cache it.
                    username = newAuthentication.getName();
                    if (shouldStoreInCache(username, authCacheKey)) {
                        ConcurrentMap<AuthCacheKey, Authentication> authCache =
                                isJFrogFrontendAuthentication(newAuthentication) ? getUiAuthCache() :
                                        getNonUiAuthCache();
                        authCache.put(authCacheKey, newAuthentication);
                        AuthenticationCache userAuthCache = getUserChangedCache().get(username);
                        if (userAuthCache != null) {
                            userAuthCache.addAuthCacheKey(authCacheKey);
                        }
                        log.debug("Added authentication {} in cache.", newAuthentication);
                    }
                }
            } else {
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(extractUsername(request), null);
                authenticationToken.setDetails(new WebAuthenticationDetails(request));
                AccessLogger.loginDenied(authenticationToken);
            }
            SecurityContextHolder.getContext().setAuthentication(null);
            request.setAttribute(AUTHENTICATED_USERNAME_ATTRIBUTE, username);
        }
    }

    @VisibleForTesting
    boolean shouldStoreInCache(String username, AuthCacheKey authCacheKey) {
        return ((UserInfo.isAnonymous(username) && !SIGNED_ANONYMOUS.equals(username)) &&
                authCacheKey.hasEmptyHeader()) ||
                (!UserInfo.isAnonymous(username) && !authCacheKey.hasEmptyHeader());
    }

    @VisibleForTesting
    boolean isForbiddenAnonymousCacheReuse(Authentication authentication, AuthCacheKey authCacheKey,
            ArtifactoryFilterHolder filterHolder) {
        return ConstantValues.securityRememberMeCookieAuthFixEnabled.getBoolean()
                && UserInfo.isAnonymous(authentication.getName())
                && authCacheKey.hasEmptyHeader()
                && filterHolder != null
                && !filterHolder.isAnonymousAllowed()
                && !filterHolder.isForcedAnonymous();
    }

    private boolean isVersionHeaderNeeded(HttpServletResponse response) {
        return response.getStatus() != 401 && response.getStatus() < 500;
    }

    private String extractUsername(HttpServletRequest request) {
        String username = RequestUtils.extractUsernameFromRequest(request);
        return StringUtils.isNotBlank(username) ? username : "NA";
    }

    private void addToUserChange(Authentication authentication) {
        String username = authentication.getName();
        if (!UserInfo.isAnonymous(username)) {
            AuthenticationCache existingCache = getUserChangedCache().putIfAbsent(username,
                    new AuthenticationCache(authentication));
            if (existingCache != null) {
                existingCache.loggedIn(authentication);
            }
        }
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    private void useAnonymousIfPossible(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            ArtifactoryFilterHolder filterHolder, boolean shouldCacheUser)
            throws IOException, ServletException {
        boolean anonAccessEnabled = context.beanForType(SecurityService.class).isAnonAccessEnabled();
        anonAccessEnabled = anonAccessEnabled || (filterHolder != null && filterHolder.isAnonymousAllowed());
        if ((anonAccessEnabled) || authInterceptors.accept(request) ||
                (filterHolder != null && filterHolder.isForcedAnonymous())) {
            log.debug("Using anonymous");
            AuthCacheKey authCacheKey = getAuthCacheKey(request, filterHolder);
            Authentication authentication = getCachedAuthentication(authCacheKey);
            if (authentication == null) {
                log.debug("Creating the Anonymous token");
                UsernamePasswordAuthenticationToken authRequest =
                        new UsernamePasswordAuthenticationToken(UserInfo.ANONYMOUS, "");
                AuthenticationDetailsSource ads = new HttpAuthenticationDetailsSource();
                //noinspection unchecked
                authRequest.setDetails(ads.buildDetails(request));
                // explicitly ask for the default spring authentication manager by name (we have another one which
                // is only used by the basic authentication filter)
                AuthenticationManager authenticationManager =
                        context.beanForType("authenticationManager", AuthenticationManager.class);
                authentication = authenticationManager.authenticate(authRequest);
                if (shouldCacheUser && authentication != null && authentication.isAuthenticated()) {
                    if (isJFrogFrontendAuthentication(authentication)) {
                        getUiAuthCache().put(authCacheKey, authentication);
                        log.debug("Added anonymous authentication {} to UI cache", authentication);
                    } else {
                        getNonUiAuthCache().put(authCacheKey, authentication);
                        log.debug("Added anonymous authentication {} to non-UI cache", authentication);
                    }
                }
            } else {
                log.debug("Using cached anonymous authentication");
            }
            if (ArtifactoryHome.get().getSysConfig().helper().exposeApplicationHeaders()) {
                ServletUtils.addSensitiveHeadersToResponse(request, response,
                        RequestUtils.getArtifactoryContext(filterConfig.getServletContext()));
            }
            useAuthentication(request, response, chain, authentication);
        } else {
            if (!RequestUtils.isUiRequest(request)) {
                log.debug("Sending request requiring authentication");
                authenticationEntryPoint.commence(request, response,
                        new InsufficientAuthenticationException("Authentication is required"));
            } else {
                log.debug("No filter or entry just chain");
                chain.doFilter(request, response);
            }
        }
    }

    private AuthCacheKey getAuthCacheKey(HttpServletRequest request, ArtifactoryFilterHolder filterHolder) {
        AuthCacheKey authCacheKey;
        if (filterHolder != null) {
            authCacheKey = new AuthCacheKey(filterHolder.getCacheKey(request),
                    request.getRemoteAddr());
        } else {
            authCacheKey = new AuthCacheKey(null, request.getRemoteAddr());
        }
        return authCacheKey;
    }

    /**
     * @return either the non-UI cached auth or the UI cached auth. The UI cache has a short expiry time to:
     * 1. Allow Artifactory's auth logic to be fully enforced often and thus keep data that is assigned through that
     * process
     * up-to-date (e.g group membership from an external provider).
     * 2. Prevent a cached external auth from using the UI for a prolonged period if that auth is already considered
     * invalid.
     */
    private Authentication getCachedAuthentication(AuthCacheKey authCacheKey) {
        Authentication nonUiCachedAuth = getNonUiAuthCache().get(authCacheKey);
        return nonUiCachedAuth != null ? nonUiCachedAuth : getUiAuthCache().get(authCacheKey);
    }

    private void useAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authentication) throws IOException, ServletException {
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
            addToUserChange(authentication);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(null);
            request.setAttribute(AUTHENTICATED_USERNAME_ATTRIBUTE,
                    authentication != null ? authentication.getName() : "non_authenticated_user");
        }
    }

    @Override
    public int compareTo(SecurityListener o) {
        //AccessFilter should always be the first listener to gets notified
        return -1;
    }

    @VisibleForTesting
    static class AuthCacheKey {
        private static final String EMPTY_HEADER = DigestUtils.sha1Hex("");

        private final String hashedHeader;
        private final String ip;

        AuthCacheKey(String header, String ip) {
            if (header == null) {
                this.hashedHeader = EMPTY_HEADER;
            } else {
                this.hashedHeader = DigestUtils.sha1Hex(header);
            }
            this.ip = ip;
        }

        boolean hasEmptyHeader() {
            return this.hashedHeader.equals(EMPTY_HEADER);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AuthCacheKey key = (AuthCacheKey) o;
            return hashedHeader.equals(key.hashedHeader) && ip.equals(key.ip);
        }

        @Override
        public int hashCode() {
            int result = hashedHeader.hashCode();
            result = 31 * result + ip.hashCode();
            return result;
        }
    }

    class AuthenticationCache {
        Set<AuthCacheKey> authCacheKeys;
        Map<Integer, Integer> authState = new HashMap<>(3);

        AuthenticationCache(Authentication first) {
            authState.put(first.hashCode(), 0);
        }

        synchronized void addAuthCacheKey(AuthCacheKey authCacheKey) {
            if (authCacheKeys == null) {
                authCacheKeys = new HashSet<>();
            }
            authCacheKeys.add(authCacheKey);
        }

        synchronized void changed() {
            if (authCacheKeys != null) {
                for (AuthCacheKey authCacheKey : authCacheKeys) {
                    Authentication removed = getNonUiAuthCache().remove(authCacheKey);
                    if (removed == null) {
                        // Try the UI cache too (the two caches are mutually exclusive)
                        removed = getUiAuthCache().remove(authCacheKey);
                    }
                    if (removed != null) {
                        Integer key = removed.hashCode();
                        log.debug("Removed {}:{} from the authentication cache", removed.getName(), key);
                        authState.put(key, 1);
                    }
                }
                authCacheKeys.clear();
            }
            Set<Integer> keys = new HashSet<>(authState.keySet());
            for (Integer key : keys) {
                authState.put(key, 1);
            }
        }

        boolean isChanged(Authentication auth) {
            int key = auth.hashCode();
            Integer state = authState.get(key);
            if (state != null) {
                return state == 1;
            }
            return false;
        }

        void loggedOut(Authentication auth) {
            authState.put(auth.hashCode(), 2);
        }

        void loggedIn(Authentication auth) {
            authState.put(auth.hashCode(), 0);
        }
    }
}