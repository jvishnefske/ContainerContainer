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

package org.artifactory.webapp.servlet.authentication;

import org.apache.http.HttpHeaders;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.TestUtils;
import org.artifactory.util.SessionUtils;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.List;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_IDN_TOKEN_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers RTDEV-94514 (issue #1 - filter selection): {@link ArtifactoryRememberMeFilter#acceptFilter} must only be
 * selected when the request carries Artifactory's actual remember-me cookie
 * ({@link SecurityConstants#REMEMBER_ME_COOKIE_NAME}). Selecting it on Spring's default {@code remember-me}
 * cookie name let an arbitrary cookie route the request into the remember-me flow whose delegate could never
 * authenticate it. Also covers the {@link ConstantValues#securityRememberMeCookieAuthFixEnabled} kill-switch, which
 * fully restores the legacy behavior when disabled.
 *
 * @author Fawaz Atwa
 */
public class ArtifactoryRememberMeFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String SSO_REMOTE_USER_HEADER = "REMOTE_USER";

    /**
     * The exact reported exploit: a cookie literally named {@code remember-me} (Spring's default cookie name) must NOT
     * select the remember-me filter.
     */
    @Test
    public void springDefaultRememberMeCookieNameIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "1"));

        assertFalse(filter.acceptFilter(request),
                "A cookie named 'remember-me' (Spring default) must not select the remember-me filter (RTDEV-94514)");
    }

    /**
     * A request carrying Artifactory's real remember-me cookie is the only one that should select the filter.
     */
    @Test
    public void artifactoryRememberMeCookieNameIsAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token-value"));

        assertTrue(filter.acceptFilter(request),
                "A cookie with Artifactory's real remember-me name must select the remember-me filter");
    }

    @Test
    public void unrelatedCookieIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("foo", "1"));

        assertFalse(filter.acceptFilter(request), "An unrelated cookie must not select the remember-me filter");
    }

    @Test
    public void requestWithoutCookiesIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        assertFalse(filter.acceptFilter(new MockHttpServletRequest()),
                "A request without cookies must not select the remember-me filter");
    }

    /**
     * When remember-me is disabled the delegate filter is never wired, so the filter must never be selected - even for
     * the real cookie name.
     */
    @Test
    public void notAcceptedWhenRememberMeDisabled() {
        ArtifactoryRememberMeFilter filter = new ArtifactoryRememberMeFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token-value"));

        assertFalse(filter.acceptFilter(request),
                "The remember-me filter must not be selected when the remember-me feature is disabled");
    }

    /**
     * Kill-switch off: the legacy behavior is fully restored - the (buggy) Spring default 'remember-me' cookie name is
     * matched again and the real cookie name is not.
     */
    @Test
    public void killSwitchOffRestoresLegacyCookieNameMatching() {
        setCookieAuthFixEnabled(false);
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();

        MockHttpServletRequest legacyCookieRequest = new MockHttpServletRequest();
        legacyCookieRequest.setCookies(
                new Cookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "1"));
        assertTrue(filter.acceptFilter(legacyCookieRequest),
                "With the fix disabled, the legacy 'remember-me' cookie name is matched again");

        MockHttpServletRequest realCookieRequest = new MockHttpServletRequest();
        realCookieRequest.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token"));
        assertFalse(filter.acceptFilter(realCookieRequest),
                "With the fix disabled, the real cookie name is not matched (legacy behavior)");
    }

    @Test
    void requestWithAuthorizationHeaderIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = rememberMeCookieRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some-access-token");

        assertFalse(filter.acceptFilter(request),
                "A request carrying an Authorization header must be left to the credential filter that owns it");
    }

    @Test
    void killSwitchOffIgnoresAuthorizationHeaderWhenSelectingTheFilter() {
        setCookieAuthFixEnabled(false);
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "1"));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some-access-token");

        assertTrue(filter.acceptFilter(request),
                "With the fix disabled, the Authorization header must not affect filter selection (legacy behavior)");
    }

    @Test
    void requestWithIdentityTokenCookieIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/artifactory/api/artifact/upload");
        request.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token-value"),
                new Cookie(OAUTH_IDN_TOKEN_NAME, "eyJhbGciOiJSUzI1NiJ9.scoped-token"));

        assertFalse(filter.acceptFilter(request),
                "A request carrying an identity token cookie must be left to the props filter that owns it");
    }

    @Test
    void killSwitchOffIgnoresIdentityTokenCookieWhenSelectingTheFilter() {
        setCookieAuthFixEnabled(false);
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "1"),
                new Cookie(OAUTH_IDN_TOKEN_NAME, "eyJhbGciOiJSUzI1NiJ9.scoped-token"));

        assertTrue(filter.acceptFilter(request),
                "With the fix disabled, the identity token cookie must not affect filter selection (legacy behavior)");
    }

    @Test
    void requestWithApiKeyHeaderIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = rememberMeCookieRequest();
        request.addHeader(ApiKeyManager.API_KEY_HEADER, "some-api-key");

        assertFalse(filter.acceptFilter(request),
                "A request carrying an API key header must be left to the props filter that owns it");
    }

    @Test
    void requestWithHttpSsoRemoteUserIsNotAccepted() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isHttpSsoProxied()).thenReturn(true);
        when(securityService.getHttpSsoRemoteUserRequestVariable()).thenReturn(SSO_REMOTE_USER_HEADER);
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate(securityService, mock(AccessService.class));
        MockHttpServletRequest request = rememberMeCookieRequest();
        request.addHeader(SSO_REMOTE_USER_HEADER, "sso-user");

        assertFalse(filter.acceptFilter(request),
                "A request carrying an HTTP SSO remote user must be left to the SSO filter that owns it");
    }

    @Test
    void requestWithMtlsIdentityIsNotAccepted() {
        AccessService accessService = mock(AccessService.class);
        when(accessService.extractMtlsUsername(any())).thenReturn("mtls-user");
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate(mock(SecurityService.class), accessService);

        assertFalse(filter.acceptFilter(rememberMeCookieRequest()),
                "A request carrying an mTLS identity must be left to the mTLS filter that owns it");
    }

    @Test
    void requestWithAuthenticatedHttpSessionIsNotAccepted() {
        ArtifactoryRememberMeFilter filter = filterWithEnabledDelegate();
        MockHttpServletRequest request = rememberMeCookieRequest();
        SessionUtils.setAuthentication(request, authenticatedUser(), true);

        assertFalse(filter.acceptFilter(request),
                "A request whose session is already authenticated does not need remember-me to re-authenticate it");
    }

    /**
     * RTDEV-97497: acceptFilter returning false is only half the contract - what matters is that the chain then hands
     * the request to the filter that owns the credential. REMEMBER_ME is ordered ahead of PROPS_AUTH and only the
     * first match is selected, so this asserts on the selection the chain actually makes.
     */
    @Test
    void propsFilterIsSelectedWhenTheRequestAlsoCarriesAnIdentityToken() throws IOException {
        ArtifactoryAuthenticationFilter propsFilter = acceptingFilter(ArtifactoryAuthenticationType.PROPS_AUTH);
        ArtifactoryRememberMeFilter rememberMeFilter = filterWithEnabledDelegate();
        MockHttpServletRequest request = rememberMeCookieRequest();
        request.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token-value"),
                new Cookie(OAUTH_IDN_TOKEN_NAME, "eyJhbGciOiJSUzI1NiJ9.scoped-token"));

        ArtifactoryFilterHolder selected = select(request, rememberMeFilter, propsFilter);

        assertNotNull(selected, "A credentialed request must still be claimed by some filter");
        assertSame(propsFilter, selected.getFilter(),
                "The props filter owns the identity token cookie and must be the one the chain selects");
    }

    /**
     * The counterpart: with no other credential on the request, remember-me is still the filter the chain selects.
     */
    @Test
    void rememberMeIsSelectedWhenNoOtherCredentialIsPresent() throws IOException {
        ArtifactoryAuthenticationFilter propsFilter = mock(ArtifactoryAuthenticationFilter.class);
        when(propsFilter.getType()).thenReturn(ArtifactoryAuthenticationType.PROPS_AUTH);
        ArtifactoryRememberMeFilter rememberMeFilter = filterWithEnabledDelegate();

        ArtifactoryFilterHolder selected = select(rememberMeCookieRequest(), rememberMeFilter, propsFilter);

        assertNotNull(selected, "A remember-me cookie on its own must still select the remember-me filter");
        assertSame(rememberMeFilter, selected.getFilter(),
                "With no competing credential the remember-me filter remains the selected filter");
    }

    private ArtifactoryFilterHolder select(MockHttpServletRequest request, ArtifactoryAuthenticationFilter... filters)
            throws IOException {
        ArtifactoryAuthenticationFilterChain chain =
                new ArtifactoryAuthenticationFilterChain(mock(BasicAuthenticationEntryPoint.class));
        chain.addFilters(List.of(filters), List.of());
        return chain.acceptFilter(request, new MockHttpServletResponse());
    }

    private ArtifactoryAuthenticationFilter acceptingFilter(ArtifactoryAuthenticationType type) {
        ArtifactoryAuthenticationFilter filter = mock(ArtifactoryAuthenticationFilter.class);
        when(filter.getType()).thenReturn(type);
        when(filter.acceptFilter(any())).thenReturn(true);
        return filter;
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return new UsernamePasswordAuthenticationToken("deployer", null, List.of());
    }

    private MockHttpServletRequest rememberMeCookieRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/artifactory/api/artifact/upload");
        request.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, "some-token-value"));
        return request;
    }

    private void setCookieAuthFixEnabled(boolean enabled) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.securityRememberMeCookieAuthFixEnabled.getPropertyName(),
                        Boolean.toString(enabled));
    }

    private ArtifactoryRememberMeFilter filterWithEnabledDelegate() {
        return filterWithEnabledDelegate(mock(SecurityService.class), mock(AccessService.class));
    }

    private ArtifactoryRememberMeFilter filterWithEnabledDelegate(SecurityService securityService,
            AccessService accessService) {
        ArtifactoryRememberMeFilter filter = new ArtifactoryRememberMeFilter();
        // acceptFilter short-circuits to false unless the delegate is wired (i.e. remember-me is enabled).
        TestUtils.setField(filter, "rememberMeDelegateFilter", mock(RememberMeAuthenticationFilter.class));
        filter.setSecurityService(securityService);
        filter.setAccessService(accessService);
        return filter;
    }
}
