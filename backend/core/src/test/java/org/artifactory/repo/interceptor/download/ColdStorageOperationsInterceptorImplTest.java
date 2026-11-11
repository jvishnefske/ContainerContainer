/*
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
 */

package org.artifactory.repo.interceptor.download;

import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.api.request.ArtifactoryResponse;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import org.artifactory.api.security.SecurityService;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.HaSystemAuthenticationToken;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.TokenType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;

/**
 * Unit tests for {@link ColdStorageOperationsInterceptorImpl}
 *
 * @author Pankaj Sharma
 */
class ColdStorageOperationsInterceptorImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String FORBIDDEN_MESSAGE =
            "The requested operation is forbidden. Any operations on the repositories in the archive instance "
            + "are not permitted.";

    @Mock
    private AddonsManager addonsManager;


    @Mock
    private ArtifactoryRequest request;

    @Mock
    private ArtifactoryResponse response;



    @Mock
    private AccessTokenAuthentication accessTokenAuthentication;

    @Mock
    private JwtAccessToken jwtAccessToken;

    @Mock
    private org.springframework.security.core.Authentication authentication;

    @Mock
    private SystemAuthenticationToken systemAuthenticationToken;

    @Mock
    private HaSystemAuthenticationToken haSystemAuthenticationToken;

    @Mock
    private ArtifactoryHome artifactoryHome;

    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;

    private ColdStorageOperationsInterceptorImpl interceptor;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        
        // Set up ArtifactoryHome mocking
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        
        // Set up default behavior for the feature toggle
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                .thenReturn(true); // Default to enabled
        interceptor = new ColdStorageOperationsInterceptorImpl(addonsManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        closeable.close();
    }

    @Test
    void testConstructor() {
        // Then
        assertNotNull(interceptor);
    }

    @Test
    void testInterceptColdStorageOperations_WhenNotColdInstance() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(false);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testInterceptColdStorageOperations_WhenNullRequest() throws IOException {
        // Given
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(null, response);

        // Then
        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @Test
    void testInterceptColdStorageOperations_WhenOperationShouldBeBlocked() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @Test
    void testInterceptColdStorageOperations_WhenOperationShouldNotBeBlocked() throws IOException {
        // Given
        setupRequestWithUri("/api/upload/some-artifact");
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testInterceptColdStorageOperations_WhenServiceAdminToken() throws IOException {
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);
            when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn("access@instance");
            when(jwtAccessToken.getScope()).thenReturn(java.util.Arrays.asList("read", "write"));
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(true);

            interceptor.interceptColdStorageOperations(request, response);

            verify(response, never()).sendError(anyInt(), anyString());
        }
    }



    @Test
    void testInterceptColdStorageOperations_WhenIOExceptionOccurs() throws IOException {
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        org.mockito.Mockito.doThrow(new IOException("Test exception"))
                .when(response).sendError(anyInt(), anyString(), any());

        interceptor.interceptColdStorageOperations(request, response);

        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @Test
    void testInterceptColdStorageOperations_WhenFeatureToggleDisabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        // Override the feature toggle to be disabled
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                .thenReturn(false);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testInterceptColdStorageOperations_WhenFeatureToggleEnabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        // Feature toggle is enabled by default in setUp()

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @Test
    void testInterceptColdStorageOperations_WhenServiceAdminTokenAndFeatureToggleEnabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);
            when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn("access@instance");
            when(jwtAccessToken.getScope()).thenReturn(java.util.Arrays.asList("read", "write"));
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(true);
            
            // Feature toggle is enabled by default in setUp()

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenServiceAdminTokenAndFeatureToggleDisabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);
            when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn("access@instance");
            when(jwtAccessToken.getScope()).thenReturn(java.util.Arrays.asList("read", "write"));
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(true);
            
            // Override the feature toggle to be disabled
            when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                    .thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemAuthenticationToken() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(systemAuthenticationToken);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenHaSystemAuthenticationToken() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(haSystemAuthenticationToken);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserByUsername() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            // Mock authentication that returns system username
            when(authentication.getName()).thenReturn(SecurityService.USER_SYSTEM);
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserAndFeatureToggleEnabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(systemAuthenticationToken);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);
            
            // Feature toggle is enabled by default in setUp()

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserAndFeatureToggleDisabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(systemAuthenticationToken);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);
            
            // Override the feature toggle to be disabled
            when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                    .thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenNonServiceAdminTokenAndFeatureToggleDisabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);
            
            // Override the feature toggle to be disabled
            when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                    .thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenNonServiceAdminTokenAndFeatureToggleEnabled() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);
            
            // Feature toggle is enabled by default in setUp()

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenNullRequestAndFeatureToggleDisabled() throws IOException {
        // Given
        setupColdInstance(true);
        
        // Override the feature toggle to be disabled
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.retentionArchiveDirectOperationsBlocked))
                .thenReturn(false);

        // When
        interceptor.interceptColdStorageOperations(null, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testInterceptColdStorageOperations_WhenNullRequestAndFeatureToggleEnabled() throws IOException {
        // Given
        setupColdInstance(true);
        
        // Feature toggle is enabled by default in setUp()

        // When
        interceptor.interceptColdStorageOperations(null, response);

        // Then
        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @Test
    void testIsServiceAdminToken_WhenAccessTokenAuthentication() {
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {

            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);
            when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn("access@instance");
            when(jwtAccessToken.getScope()).thenReturn(java.util.Arrays.asList("read", "write"));
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(true);

            boolean result = ColdStorageOperationsInterceptorImpl.isServiceAdminToken();

            assertTrue(result);
        }
    }

    @Test
    void testIsServiceAdminToken_WhenNotAccessTokenAuthentication() {
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {

            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            boolean result = ColdStorageOperationsInterceptorImpl.isServiceAdminToken();

            assertFalse(result);
        }
    }

    @Test
    void testIsServiceAdminToken_WhenNullAccessToken() {
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {

            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);
            when(accessTokenAuthentication.getAccessToken()).thenReturn(null);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            boolean result = ColdStorageOperationsInterceptorImpl.isServiceAdminToken();

            assertFalse(result);
        }
    }

    @Test
    void testIsServiceAdminToken_WhenNullAuthentication() {
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class)) {

            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(null);

            boolean result = ColdStorageOperationsInterceptorImpl.isServiceAdminToken();

            assertFalse(result);
        }
    }

    @Test
    void testIsServiceAdminToken_WhenNonAccessTokenAuthentication() {
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class)) {

            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);

            boolean result = ColdStorageOperationsInterceptorImpl.isServiceAdminToken();

            assertFalse(result);
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserWithNullAuthentication() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            // Mock null authentication - should not be treated as system user
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(null);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserWithEmptyUsername() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            // Mock authentication with empty username - should not be treated as system user
            when(authentication.getName()).thenReturn("");
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
        }
    }

    @Test
    void testInterceptColdStorageOperations_WhenSystemUserWithNullUsername() throws IOException {
        // Given
        setupRequestWithUri("/api/download/some-artifact");
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            // Mock authentication with null username - should not be treated as system user
            when(authentication.getName()).thenReturn(null);
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then
            verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/download/artifact",
        "/ui/artifactactions/downloadfolder",
        "/api/download/",
        "/ui/artifactactions/downloadfolder/subfolder"
    })
    void testIsBlockedOperation_WhenBlockedOperations(String uri) throws IOException {
        // Given
        setupRequestWithUri(uri);
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response).sendError(eq(403), eq(FORBIDDEN_MESSAGE), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/download/artifact",
        "/ui/artifactactions/downloadfolder",
        "/api/download/",
        "/ui/artifactactions/downloadfolder/subfolder"
    })
    void testIsBlockedOperation_WhenSystemUserWithBlockedOperations(String uri) throws IOException {
        // Given
        setupRequestWithUri(uri);
        setupColdInstance(true);
        
        try (MockedStatic<AuthenticationHelper> mockedAuthHelper = mockStatic(AuthenticationHelper.class);
                MockedStatic<TokenType> mockedTokenType = mockStatic(TokenType.class)) {
            
            mockedAuthHelper.when(AuthenticationHelper::getAuthentication).thenReturn(systemAuthenticationToken);
            mockedTokenType.when(() -> TokenType.isServiceAdminToken(anyString(), anyString())).thenReturn(false);

            // When
            interceptor.interceptColdStorageOperations(request, response);

            // Then - System user should be allowed even for normally blocked operations
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/upload/artifact",
        "/ui/artifactactions/uploadfolder",
        "/api/search/",
        "/ui/artifactactions/search"
    })
    void testIsBlockedOperation_WhenNonBlockedOperations(String uri) throws IOException {
        // Given
        setupRequestWithUri(uri);
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testIsBlockedOperation_WhenNullUri() throws IOException {
        // Given
        setupRequestWithUri(null);
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testIsBlockedOperation_WhenEmptyUri() throws IOException {
        // Given
        setupRequestWithUri("");
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testIsBlockedOperation_WhenBlankUri() throws IOException {
        // Given
        setupRequestWithUri("   ");
        setupColdInstance(true);

        // When
        interceptor.interceptColdStorageOperations(request, response);

        // Then
        verify(response, never()).sendError(anyInt(), anyString());
    }

    // Helper methods to setup test scenarios
    private void setupRequestWithUri(String uri) {
        when(request.getUri()).thenReturn(uri);
    }

    private void setupColdInstance(boolean isCold) {
        when(addonsManager.isEntitled(EntitlementType.COLD_STORAGE)).thenReturn(isCold);
    }


}
