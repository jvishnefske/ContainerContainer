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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.OptionalLong;
import org.artifactory.addon.plugin.groovy.PluginRealmAuthentication;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.apache.http.HttpHeaders;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.authentication.context.AuthenticationContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.test.MockConfigurationManagerAdapter;
import org.artifactory.test.TestUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.authentication.ArtifactoryBasicAuthenticationEntryPoint;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.awaitility.Awaitility;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.ifc.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static org.artifactory.authentication.context.AuthenticationContextStatus.HAS_ERROR;
import static org.artifactory.security.props.auth.OauthManager.OAUTH_IDN_TOKEN_NAME;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;


/**
 * @author Yoav Luft
 */
public class AccessFilterTest extends ArtifactoryHomeBoundJUnitTest {


    @AfterEach
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    public void testAuthenticationChallenge() throws IOException {
        ArtifactoryHomeStub artifactoryHome = new ArtifactoryHomeStub("./target/test/testAuthenticationChallenge");
        ConfigurationManager configurationManager = ConfigurationManagerImpl
                .create(new MockConfigurationManagerAdapter(artifactoryHome));
        configurationManager.initDefaultFiles();
        artifactoryHome.initArtifactorySystemProperties();
        artifactoryHome.initMimeTypes();
        ArtifactoryHomeStub.bind(artifactoryHome);
        ArtifactoryHomeStub.get().getArtifactoryProperties().setProperty(
                ConstantValues.locksTimeoutSecs.getPropertyName(), "10");
        ArtifactoryBasicAuthenticationEntryPoint authenticationEntryPoint =
                new ArtifactoryBasicAuthenticationEntryPoint();
        HttpServletRequest request = createMock(HttpServletRequest.class);

        HttpServletResponse response = createMock(HttpServletResponse.class);
        response.addHeader("WWW-Authenticate", "Basic realm=\"Artifactory Realm\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(401);
        StringWriter out = new StringWriter();
        PrintWriter printWriter = new PrintWriter(out);
        expect(response.getWriter()).andReturn(printWriter);
        expect(request.getRequestURI()).andReturn("testuri");
        expect(request.getHeader(HttpHeaders.USER_AGENT)).andStubReturn("the-agent");
        expect(request.getHeader("Request-Agent")).andStubReturn("xx");
        expect(request.getHeader(ApiKeyManager.API_KEY_HEADER)).andStubReturn(null);
        expect(request.getHeader(ApiKeyManager.OLD_API_KEY_HEADER)).andStubReturn(null);
        expect(request.getContextPath()).andReturn("/artifactory");
        replay(request, response);
        authenticationEntryPoint.commence(request, response,
                new InsufficientAuthenticationException("Authentication required"));
        verify(response);
        TestUtils.verifyErrorJson(out.toString(), 401, "Authentication required");
        configurationManager.destroy();
    }

    @Test
    public void testUiCacheExpiry() throws ServletException {
        AccessFilter accessFilter = getAccessFilter();

        ConcurrentMap<AccessFilter.AuthCacheKey, Authentication> uiAuthCache = accessFilter.getUiAuthCache();
        assertTrue(uiAuthCache.isEmpty());

        AccessFilter.AuthCacheKey cacheKey = new AccessFilter.AuthCacheKey("Authorization: foo", "127.0.0.1");
        uiAuthCache.putIfAbsent(cacheKey,
                mock(Authentication.class));
        assertEquals(uiAuthCache.size(), 1);

        Awaitility.await()
                .atMost(ConstantValues.securityUiAuthenticationCacheIdleTimeSecs.getInt() + 5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(uiAuthCache.get(cacheKey)));
    }

    /**
     * Every cache read (the header path and the anonymous fallback alike) drops a plugin-realm login that was revoked
     * (plugin reloaded or removed) or expired, so no path can reuse it.
     */
    @Test
    public void cachedPluginRealmLoginIsDroppedOnReadOnceRevokedOrExpired() throws ServletException {
        AccessFilter accessFilter = getAccessFilter();
        ConcurrentMap<AccessFilter.AuthCacheKey, Authentication> uiAuthCache = accessFilter.getUiAuthCache();
        AtomicBoolean loaded = new AtomicBoolean(true);
        AccessFilter.AuthCacheKey revokedKey = new AccessFilter.AuthCacheKey("Authorization: Bearer a", "127.0.0.1");
        PluginRealmAuthentication live = new PluginRealmAuthentication(
                new SimpleUser(new UserInfoBuilder("u").build()), "p", "r", OptionalLong.empty(), null, loaded::get);
        uiAuthCache.put(revokedKey, live);
        assertSame(live, accessFilter.getCachedAuthentication(revokedKey));

        loaded.set(false);
        assertNull(accessFilter.getCachedAuthentication(revokedKey));
        assertNull(uiAuthCache.get(revokedKey));

        AccessFilter.AuthCacheKey expiredKey = new AccessFilter.AuthCacheKey("Authorization: Bearer b", "127.0.0.1");
        uiAuthCache.put(expiredKey, new PluginRealmAuthentication(new SimpleUser(new UserInfoBuilder("u").build()),
                "p", "r", OptionalLong.of(1), null));
        assertNull(accessFilter.getCachedAuthentication(expiredKey));
        assertNull(uiAuthCache.get(expiredKey));
    }

    @ParameterizedTest
    @MethodSource("provideShouldStoreInCache")
    public void shouldStoreInCache(String username, AccessFilter.AuthCacheKey authCacheKay, boolean expected)
            throws ServletException {
        assertEquals(expected, getAccessFilter().shouldStoreInCache(username, authCacheKay));
    }

    @ParameterizedTest
    @MethodSource("provideForbiddenAnonymousCacheReuse")
    public void isForbiddenAnonymousCacheReuse(Authentication authentication, AccessFilter.AuthCacheKey authCacheKey,
            ArtifactoryFilterHolder filterHolder, boolean expected) throws ServletException {
        assertEquals(expected,
                getAccessFilter().isForbiddenAnonymousCacheReuse(authentication, authCacheKey, filterHolder));
    }

    /**
     * Kill-switch off: the guard is disabled, so even the exploit case (anonymous under an empty-header key reached via
     * a credential filter) is allowed to reuse the cached anonymous authentication - i.e. the legacy behavior.
     */
    @Test
    public void anonymousCacheReuseNotForbiddenWhenKillSwitchDisabled() throws ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.securityRememberMeCookieAuthFixEnabled.getPropertyName(), "false");
        assertFalse(getAccessFilter().isForbiddenAnonymousCacheReuse(anonymousAuthentication(),
                new AccessFilter.AuthCacheKey(null, "127.0.0.1"), filterHolder(false, false)));
    }


    private AccessFilter getAccessFilter() throws ServletException {
        AccessFilter accessFilter = new AccessFilter();
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.securityUiAuthenticationCacheIdleTimeSecs.getPropertyName(), "3");
        accessFilter.initLater(getFilterConfig(artifactoryHome));
        return accessFilter;
    }

    private FilterConfig getFilterConfig(ArtifactoryHome artifactoryHome) {
        FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
        ServletContext servletContext = Mockito.mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);
        ArtifactoryApplicationContext applicationContext = Mockito.mock(ArtifactoryApplicationContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY))
                .thenReturn(applicationContext);
        BasicAuthenticationEntryPoint basicAuthenticationEntryPoint = Mockito.mock(BasicAuthenticationEntryPoint.class);
        when(applicationContext.beanForType(BasicAuthenticationEntryPoint.class))
                .thenReturn(basicAuthenticationEntryPoint);
        SecurityService securityService = mock(SecurityService.class);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(securityService);
        return filterConfig;
    }

    @Test
    public void isJFrogFrontendAuthentication() {
        String username = "admin";
        MutableUserInfo transientUser = new UserInfoBuilder(username).transientUser().build();
        SimpleUser simpleUser = new SimpleUser(transientUser);
        String frontendTokenValue = "eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJOR0c3eV9uejl4Z0xYeFhT" +
                "bU5HX3FPMWRpSXVwRzdFOW5VWXltX1V4STVNIn0.eyJzdWIiOiJqZmZlQDAwMCIsInNjcCI6ImFwcGxpZWQtcGVybWlzc2lvbnNc" +
                "L2FkbWluIGFwaToqIiwiYXVkIjoiamZydEAqIiwiaXNzIjoiamZmZUAwMDAiLCJleHAiOjE1NzAxMTU2MTUsImlhdCI6MTU3MDEx" +
                "NTAxNSwianRpIjoiZDc3OGM0MTYtOTEyMi00ODg3LWIwZDAtODFjMWRjZTYzNjY1In0.ZkepryOCH4-RFnbYPxu7Vg3IX0Z-rKyU" +
                "-iYdj17tlYyNnmqKW9drJW_rU8VcGvCW1rYsGKQ9_r9RdRErcT_ckYVKSzT4YJVW5x_GOVriSlvop_Uidi_k_yRu_D6LGwlRa2JU" +
                "jWgDA0Kdd4j2-cP7A6hL7YAMGfswNv-5V2RkyVuiihRkq6nnYG9dRjnW9NtyHmf2yPo7huYtxvkFqF96QkCfa0Ygc90CGLEL5u4x" +
                "aeCTv0iAL_0OkO7n5xu6ewkbJlnySAFo5_9l3YsSTpRmVpkh2_xNOg1_rQVnxnAzARPCAywEcR1Mq1MTKW7kZpz9hBaowxQPW4OO" +
                "AlEgt6UYbA";
        Authentication authentication = new AccessTokenAuthentication(frontendTokenValue, simpleUser, null);

        assertTrue(AuthenticationHelper.isJFrogFrontendAuthentication(authentication));

        String nonFrontendTokenValue = "eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJheHlVMFFzc3J3S0p3Z" +
                "llqMUtObzNMbzZ6TnhLcnc3NEJ5YkVGVUJrSjB3In0.eyJzdWIiOiJqZnJ0QDAxZGtqMTR5bXNiamczMDRtbWJ4MXExbnNnXC91c" +
                "2Vyc1wvYWxleCIsInNjcCI6Im1lbWJlci1vZi1ncm91cHM6cmVhZGVycyBhcGk6KiIsImF1ZCI6ImpmcnRAMDFka2oxNHltc2JqZ" +
                "zMwNG1tYngxcTFuc2ciLCJpc3MiOiJqZnJ0QDAxZGtqMTR5bXNiamczMDRtbWJ4MXExbnNnXC91c2Vyc1wvYWRtaW4iLCJleHAiO" +
                "jE1NjczMjkyMDAsImlhdCI6MTU2NzMyNTYwMCwianRpIjoiYzBhY2QzYjMtZWI4OS00M2NhLWIxNTMtY2FlZjk5OTMxOWFhIn0.D" +
                "DACJum60qihTypKCZ7irlU1c6AfEmxqFZW5seYwxLsiumbxF3eKiVTHcCaWXNP3fOjsnRKUqie0AerJB7KLlhSSlH2Iu2tFK0tQT" +
                "hTh_Wzt_D27BD1K7ZQAaNLy5p-Leons9jK6HKaDk70wxK3FT08gHaN-HiOslfifbYA_gfOl0EeH5-iW6zOj2sK7d1ixqsNbqbbW4" +
                "VGK1lF1A9YASFWirHgH8Oph0zA02cowyAWGZ6N7NA-oinyldIGhExNkOzQ5HwrWZHyplK6UZwtZ2aXWHihOBwcSjGklzSGqA4Xs1" +
                "n5IBmlLcm__78HxUR0ptOW6U7R6mME0IZ8zJ3-uuA";
        authentication = new AccessTokenAuthentication(nonFrontendTokenValue, simpleUser, null);

        assertFalse(AuthenticationHelper.isJFrogFrontendAuthentication(authentication));
    }

    @ParameterizedTest
    @MethodSource("provideGetUrlWithModifiedPort")
    public void testGetUrlWithModifiedPort(String originalHost, int routerPort, String expectedUrl, int artifactoryPort)
            throws IOException {
        AccessFilter accessFilter = new AccessFilter();
        URL urlWithModifiedPort = accessFilter.getPlatformUiUrl(originalHost, routerPort, artifactoryPort);
        assertEquals(urlWithModifiedPort.toExternalForm(), expectedUrl);
    }

    /**
     * The test checks a case when the ArtifactoryAuthenticationContextFilter returns an error in the status field,
     * the expected behavior in this case is that the flow in the accessFilter will be stopped and the
     * doFilter of the chain object will not be invoked.
     */
    @Test
    public void doFilterWhenFilterHasError()
            throws IOException, ServletException {
        AccessFilter accessFilter = new AccessFilter();
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.securityUiAuthenticationCacheIdleTimeSecs.getPropertyName(), "3");
        FilterConfig filterConfig = getFilterConfig(artifactoryHome);
        ArtifactoryApplicationContext applicationContext =
                (ArtifactoryApplicationContext) filterConfig.getServletContext()
                        .getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY);
        ArtifactoryAuthenticationContextFilter packagesAuthenticationFilter =
                Mockito.mock(ArtifactoryAuthenticationContextFilter.class);
        when(packagesAuthenticationFilter.getAuthenticationContext(any(), any())).thenReturn(
                AuthenticationContext.builder().authenticationStatus(HAS_ERROR).build());
        when(applicationContext.beansForType(ArtifactoryAuthenticationContextFilter.class)).thenReturn(
                Map.of("PackagesFilter", packagesAuthenticationFilter));
        when(packagesAuthenticationFilter.getType()).thenReturn(ArtifactoryAuthenticationType.PACKAGES_AUTH);
        accessFilter.initLater(filterConfig);
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/artifactory/api/packageName/someapi/");
        HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(401);
        FilterChain chain = Mockito.mock(FilterChain.class);
        MemoryAppender appender = MemoryAppenderUtil
                .createMemoryAppender(AccessFilter.class, Level.DEBUG);
        accessFilter.doFilter(req, resp, chain);
        appender.stop();
        ILoggingEvent logEvent = appender.list.get(0);
        assertEquals(logEvent.getFormattedMessage(),
                "Artifactory Filter returned an error in the response object, request " +
                        "uri:/artifactory/api/packageName/someapi/, status code:401 ");
        Mockito.verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    public void shouldSkipOpenId() throws ServletException {
        AccessFilter accessFilter = getAccessFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/artifactory/notOpenId");
        assertFalse(accessFilter.shouldSkipFilter(request));
        when(request.getRequestURI()).thenReturn("/artifactory/api/system/gateway/openid/auth/jfsupport@01g2a");
        assertTrue(accessFilter.shouldSkipFilter(request));
        when(request.getRequestURI()).thenReturn("/bla/api/system/gateway/openid/auth/jfsupport@01g2a");
        assertFalse(accessFilter.shouldSkipFilter(request));
    }

    @Test
    public void acceptWithDifferentContext() throws ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/bla");
        AccessFilter accessFilter = getAccessFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/artifactory/notOpenId");
        assertFalse(accessFilter.shouldSkipFilter(request));
        when(request.getRequestURI()).thenReturn("/artifactory/api/system/gateway/openid/auth/jfsupport@01g2a");
        assertTrue(accessFilter.shouldSkipFilter(request));
        when(request.getRequestURI()).thenReturn("/bla/api/system/gateway/openid/auth/jfsupport@01g2a");
        assertTrue(accessFilter.shouldSkipFilter(request));
        when(request.getRequestURI()).thenReturn("/notBla/api/system/gateway/openid/auth/jfsupport@01g2a");
        assertFalse(accessFilter.shouldSkipFilter(request));
    }

    @Test
    public void cookieOnlyBrowserRequestRedirectsToNativeBrowser() throws IOException, ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getJFrogUrlBase(0)).thenReturn("http://localhost:8081/");
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            String location = response.getHeader(HttpHeaders.LOCATION);
            assertTrue(location != null && location.contains("/ui/native/myrepo/file.xml"),
                    "Expected redirect to /ui/native/ but got: " + location);
            Mockito.verify(chain, never()).doFilter(any(), any());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void requestWithAuthorizationHeaderDoesNotRedirectToNativeBrowser() throws IOException, ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.test");
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertNotEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            assertNull(response.getHeader(HttpHeaders.LOCATION),
                    "No redirect should occur when Authorization header is present");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void cookieTokenBrowserRequestDoesNotRedirectToNativeBrowser() throws IOException, ServletException {
        // Regression for the MFE direct download/upload flow (RTFE-3521): the scoped token is delivered in the
        // RT_IDN_TOKEN cookie (no Authorization header). Such a credentialed request must be authenticated, not
        // redirected to the native UI browser.
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.setCookies(new Cookie(OAUTH_IDN_TOKEN_NAME, "eyJhbGciOiJSUzI1NiJ9.scoped-token"));
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertNotEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            assertNull(response.getHeader(HttpHeaders.LOCATION),
                    "No redirect should occur when a valid RT_IDN_TOKEN cookie is present");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void basicAuthBrowserRequestDoesNotRedirectToNativeBrowser() throws IOException, ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertNotEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            assertNull(response.getHeader(HttpHeaders.LOCATION),
                    "No redirect should occur when Basic Auth Authorization header is present");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void skipWWWAuthenticateHeaderDisabledDoesNotRedirectToNativeBrowser() throws IOException, ServletException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.skipWWWAuthenticateHeader.getPropertyName(), "false");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertNotEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            assertNull(response.getHeader(HttpHeaders.LOCATION),
                    "No redirect should occur when skipWWWAuthenticateHeader is disabled");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void fetchBrowserRequestDoesNotRedirectToNativeBrowser() throws IOException, ServletException {
        // RTDEV-87580: an in-browser fetch()/XHR carries Sec-Fetch-Mode != "navigate". Such programmatic
        // requests must receive the artifact (or a proper auth challenge), never the native UI SPA page.
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.addHeader("Sec-Fetch-Mode", "cors");
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertNotEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            assertNull(response.getHeader(HttpHeaders.LOCATION),
                    "No redirect should occur for a programmatic fetch/XHR request (Sec-Fetch-Mode: cors)");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    public void navigationBrowserRequestRedirectsToNativeBrowser() throws IOException, ServletException {
        // RTDEV-87580: a genuine top-level browser navigation carries Sec-Fetch-Mode: navigate and should
        // still be redirected to the native UI browser (preserves the redirect feature / RTDEV-83615).
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.redirectNativeBrowserRequestsToUi.getPropertyName(), "true");

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of("myrepo"));
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getJFrogUrlBase(0)).thenReturn("http://localhost:8081/");
        ArtifactoryContextThreadBinder.bind(context);

        try {
            AccessFilter accessFilter = getAccessFilterWithAcceptingAuthFilter();

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/artifactory/myrepo/file.xml");
            request.addHeader(HttpHeaders.USER_AGENT, CHROME_UA);
            request.addHeader("Sec-Fetch-Mode", "navigate");
            request.setContextPath("/artifactory");
            request.setScheme("http");

            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            accessFilter.doFilter(request, response, chain);

            assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
            String location = response.getHeader(HttpHeaders.LOCATION);
            assertTrue(location != null && location.contains("/ui/native/myrepo/file.xml"),
                    "Expected redirect to /ui/native/ but got: " + location);
            Mockito.verify(chain, never()).doFilter(any(), any());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    private static final String CHROME_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private AccessFilter getAccessFilterWithAcceptingAuthFilter() throws ServletException {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.securityUiAuthenticationCacheIdleTimeSecs.getPropertyName(), "3");

        FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
        ServletContext servletContext = Mockito.mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(artifactoryHome);

        ArtifactoryApplicationContext applicationContext = Mockito.mock(ArtifactoryApplicationContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(applicationContext);

        BasicAuthenticationEntryPoint basicAuthEntryPoint = Mockito.mock(BasicAuthenticationEntryPoint.class);
        when(applicationContext.beanForType(BasicAuthenticationEntryPoint.class)).thenReturn(basicAuthEntryPoint);

        SecurityService securityService = mock(SecurityService.class);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(securityService);

        ArtifactoryAuthenticationFilter cookieFilter = mock(ArtifactoryAuthenticationFilter.class);
        when(cookieFilter.acceptFilter(any())).thenReturn(true);
        // Return a non-empty cache key to simulate a stale RT_IDN_TOKEN cookie being present
        when(cookieFilter.getCacheKey(any())).thenReturn("stale-rt-idn-token-value");
        when(cookieFilter.getLoginIdentifier(any())).thenReturn(null);
        when(cookieFilter.getType()).thenReturn(ArtifactoryAuthenticationType.PROPS_AUTH);

        when(applicationContext.beansForType(ArtifactoryAuthenticationFilter.class))
                .thenReturn(Map.of("cookieFilter", cookieFilter));
        when(applicationContext.beansForType(ArtifactoryAuthenticationContextFilter.class))
                .thenReturn(Map.of());

        AccessFilter accessFilter = new AccessFilter();
        accessFilter.initLater(filterConfig);
        return accessFilter;
    }

    public static Object[][] provideGetUrlWithModifiedPort() {
        String httpExpectedNoPort = "http://host" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI;
        return new Object[][] {
                {"http://host", 1337, httpExpectedNoPort, 8081},
                {"https://host", 1337, "https://host" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"https://host:8080", 1337, "https://host:8080" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"https://host:8080/", 1337, "https://host:8080" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"http://host:8080/some/path", 1337, "http://host:8080" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"http://host:8081", 1337, "http://host:1337" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"http://host:8081/artifactory", 1337, "http://host:1337" + HttpUtils.UNIFIED_JFROG_PLATFORM_UI, 8081},
                {"http://host/artifactory", 1337, httpExpectedNoPort, 8081},
                {"http://host/some/path", 1337, httpExpectedNoPort, 8081},
                {"http://host/artifactory/webapp", 1337, httpExpectedNoPort, -1},
        };
    }

    private static Stream<Arguments> provideShouldStoreInCache() {
        return Stream.of(
                Arguments.of(UserInfo.ANONYMOUS, new AccessFilter.AuthCacheKey(null, "127.0.0.1")
                        , true),
                Arguments.of(UserInfo.SIGNED_ANONYMOUS, new AccessFilter.AuthCacheKey(null, "127.0.0.1")
                        , false),
                Arguments.of("some user", new AccessFilter.AuthCacheKey(null, "127.0.0.1")
                        , false),
                Arguments.of("some user", new AccessFilter.AuthCacheKey("some header", "127.0.0.1")
                        , true)
        );
    }

    private static Stream<Arguments> provideForbiddenAnonymousCacheReuse() {
        AccessFilter.AuthCacheKey emptyHeaderKey = new AccessFilter.AuthCacheKey(null, "127.0.0.1");
        AccessFilter.AuthCacheKey headerKey = new AccessFilter.AuthCacheKey("Basic dXNlcjpwYXNz", "127.0.0.1");
        return Stream.of(
                // The exploit: anonymous cached under an empty-header key must not be reused by a credential filter.
                Arguments.of(anonymousAuthentication(), emptyHeaderKey, filterHolder(false, false), true),
                // An anonymous-allowed filter is allowed to serve the cached anonymous authentication.
                Arguments.of(anonymousAuthentication(), emptyHeaderKey, filterHolder(true, false), false),
                // A forced-anonymous filter is allowed to serve the cached anonymous authentication.
                Arguments.of(anonymousAuthentication(), emptyHeaderKey, filterHolder(false, true), false),
                // A non-empty header key cannot collide with the anonymous ping entry - nothing to forbid.
                Arguments.of(anonymousAuthentication(), headerKey, filterHolder(false, false), false),
                // A real (non-anonymous) cached user is a legitimate cache hit and must be served.
                Arguments.of(userAuthentication("alice"), emptyHeaderKey, filterHolder(false, false), false),
                // No accepted filter (anonymous flow) - handled elsewhere, nothing to forbid here.
                Arguments.of(anonymousAuthentication(), emptyHeaderKey, null, false)
        );
    }

    private static Authentication anonymousAuthentication() {
        return userAuthentication(UserInfo.ANONYMOUS);
    }

    private static Authentication userAuthentication(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }

    private static ArtifactoryFilterHolder filterHolder(boolean anonymousAllowed, boolean forcedAnonymous) {
        ArtifactoryFilterHolder filterHolder = mock(ArtifactoryFilterHolder.class);
        when(filterHolder.isAnonymousAllowed()).thenReturn(anonymousAllowed);
        when(filterHolder.isForcedAnonymous()).thenReturn(forcedAnonymous);
        return filterHolder;
    }

}
