/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.HttpUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IA5: {@link AccessFilter#useAuthentication} answers 403 "password change required" to a user who must change the
 * password, except on the change-password and logout endpoints; other users are unaffected.
 */
class MustChangePasswordGateTest extends ArtifactoryHomeBoundJUnitTest {

    private final UserGroupService userGroupService = mock(UserGroupService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private AccessFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        ArtifactoryHome home = ArtifactoryHome.get();
        home.getArtifactoryProperties()
                .setProperty(ConstantValues.securityUiAuthenticationCacheIdleTimeSecs.getPropertyName(), "3");
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR)).thenReturn(home);
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        when(context.beanForType(BasicAuthenticationEntryPoint.class))
                .thenReturn(mock(BasicAuthenticationEntryPoint.class));
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(context.beanForType(UserGroupService.class)).thenReturn(userGroupService);
        filter = new AccessFilter();
        filter.initLater(filterConfig);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication authenticated(UserInfo user) {
        SimpleUser principal = new SimpleUser(user);
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }

    private static UserInfo flagged() {
        return new UserInfoBuilder("bob").mustChangePassword(true).build();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/artifactory" + path);
        request.setContextPath("/artifactory");
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, UserInfo user) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.useAuthentication(request, response, chain, authenticated(user));
        return response;
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/repositories",
            "PUT, /libs-release-local/a.jar",
            "POST, /api/security/token",
            "POST, /api/security/apiKey",
            "POST, /ui/api/v1/ui/auth/changePassword",
            "GET, /api/security/users/authorization/changePassword",
            "POST, /api/security/users/authorization/changePassword/x",
            "POST, /api/security/users/authorization/unExpirePasswordForAllUsers",
            "GET, /ui/auth/logout"
    })
    void flaggedUserIsRefusedOnOtherRequests(String method, String path) throws Exception {
        when(userGroupService.findUser("bob", false)).thenReturn(flagged());
        MockHttpServletResponse response = run(request(method, path), flagged());
        assertEquals(403, response.getStatus());
        assertEquals("password change required", response.getContentAsString());
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "/api/security/users/authorization/changePassword",
            "/ui/auth/changePassword",
            "/api/security/logout",
            "/ui/auth/logout"
    })
    void flaggedUserMayChangePasswordAndLogOut(String path) throws Exception {
        MockHttpServletResponse response = run(request("POST", path), flagged());
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        verify(userGroupService, never()).findUser(anyString(), anyBoolean());
    }

    @Test
    void unflaggedUserIsUnaffectedAndNotLookedUp() throws Exception {
        MockHttpServletResponse response = run(request("GET", "/api/repositories"), new UserInfoBuilder("bob").build());
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        verify(userGroupService, never()).findUser(anyString(), anyBoolean());
    }

    @Test
    void staleFlagIsLiftedOnceTheStoreNoLongerHasIt() throws Exception {
        when(userGroupService.findUser("bob", false)).thenReturn(new UserInfoBuilder("bob").build());
        MockHttpServletResponse response = run(request("GET", "/api/repositories"), flagged());
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void failedLookupFailsClosed() throws Exception {
        when(userGroupService.findUser("bob", false)).thenThrow(new IllegalStateException("store down"));
        assertEquals(403, run(request("GET", "/api/repositories"), flagged()).getStatus());
        doReturn(null).when(userGroupService).findUser("bob", false);
        assertEquals(403, run(request("GET", "/api/repositories"), flagged()).getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void missingAndNonSimpleUserPrincipalsAreUnaffected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.useAuthentication(request("GET", "/api/repositories"), response, chain,
                new UsernamePasswordAuthenticationToken("bob", ""));
        assertEquals(200, response.getStatus());
        assertFalse(MustChangePasswordGate.blocks(null, request("GET", "/api/repositories"), name -> null));
    }

    @Test
    void pathMatchingWorksWithoutAContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ui/auth/changePassword");
        assertTrue(HttpUtils.isChangePasswordRequest(request));
        assertFalse(HttpUtils.isLogoutRequest(request));
        MockHttpServletRequest otherContext = new MockHttpServletRequest("POST", "/other/ui/auth/changePassword");
        otherContext.setContextPath("/artifactory");
        assertFalse(HttpUtils.isChangePasswordRequest(otherContext));
    }
}
