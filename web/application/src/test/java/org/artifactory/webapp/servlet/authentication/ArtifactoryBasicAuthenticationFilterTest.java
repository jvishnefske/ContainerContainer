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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.authentication.ArtifactoryBasicAuthenticationFilter;
import org.artifactory.security.ldap.LdapRealmAwareAuthentication;
import org.artifactory.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author dudim
 */
class ArtifactoryBasicAuthenticationFilterTest {

    @Mock
    private ArtifactoryBasicAuthenticationFilter artifactoryBasicAuthenticationFilter;
    @Mock
    private LdapRealmAwareAuthentication ldapRealmAwareAuthentication;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() throws Exception {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    /**
     * RTFACT-15967
     * BasicAuthenticationFilter is used when authenticating LDAP users,
     * when first time authenticate success, the user creds is stored in AccessFilter:nonUiAuthCache,
     * when doing second time authentication, we check if user creds already exist,
     * this check is failed due case sensitive issue.
     */
    @Test
    void testRequiresReAuthentication() {
        when(artifactoryBasicAuthenticationFilter.acceptFilter(any())).thenReturn(true);
        when(ldapRealmAwareAuthentication.getPrincipal()).thenReturn("dudim");
        when(artifactoryBasicAuthenticationFilter.requiresReAuthentication(any(), any())).thenCallRealMethod();
        HttpServletRequest httpRequestMock = mock(HttpServletRequest.class);
        String base64UsernamePassword = Base64.encodeBase64String("DUDIM:password".getBytes());
        when(httpRequestMock.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Basic " + base64UsernamePassword);
        assertFalse(
                artifactoryBasicAuthenticationFilter
                        .requiresReAuthentication(httpRequestMock, ldapRealmAwareAuthentication));
    }

    @Test
    void testAcceptFilterNotAnonymous() {
        String auth = Base64.encodeBase64String("admin:password".getBytes());
        ArtifactoryBasicAuthenticationFilter filter = new ArtifactoryBasicAuthenticationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "api/repodata");

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);

        filter.setAuthorizationService(authorizationService);
        filter.setSecurityService(securityService);

        request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Basic %s", auth));
        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            assertTrue(filter.acceptFilter(request));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "Anonymous", "anonymouS"})
    void testAcceptFilterAnonymous(String username) {
        String auth = Base64.encodeBase64String(String.format("%s:", username).getBytes());
        ArtifactoryBasicAuthenticationFilter filter = new ArtifactoryBasicAuthenticationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "api/repodata");

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);

        filter.setAuthorizationService(authorizationService);
        filter.setSecurityService(securityService);

        request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Basic %s", auth));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            assertFalse(filter.acceptFilter(request));
        }
    }

    @Test
    void testAcceptFilterDoesNotApplyThePasswordPolicyAtLogin() {
        String auth = Base64.encodeBase64String("someone:short".getBytes());
        ArtifactoryBasicAuthenticationFilter filter = new ArtifactoryBasicAuthenticationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "api/repodata");
        filter.setAuthorizationService(mock(AuthorizationService.class));
        filter.setSecurityService(mock(SecurityService.class));
        request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Basic %s", auth));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            assertTrue(filter.acceptFilter(request));
            securityUtils.verifyNoInteractions();
        }
    }

    @Test
    void getType() {
        ArtifactoryBasicAuthenticationFilter artifactoryBasicAuthenticationFilter =
                new ArtifactoryBasicAuthenticationFilter();
        Assertions.assertEquals(ArtifactoryAuthenticationType.BASIC_AUTH,
                artifactoryBasicAuthenticationFilter.getType());
    }
}