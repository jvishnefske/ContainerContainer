package org.artifactory.rest.common.service.admin.userprofile;

import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class RevokeApiKeyServiceTest {

    private RevokeApiKeyService revokeApiKeyService;
    private AutoCloseable mocks;

    @Mock
    private SecurityService securityService;
    @Mock
    private ApiKeyManager apiKeyManager;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private ArtifactoryRestResponse response;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        revokeApiKeyService = new RevokeApiKeyService(securityService, apiKeyManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testExecuteAnonymousUser() {
        when(securityService.isAnonymous()).thenReturn(true);
        revokeApiKeyService.execute(request, response);
        verify(response, never()).error(anyString());
    }

    @Test
    void testExecuteApiKeyAuthentication() {
        when(securityService.isAnonymous()).thenReturn(false);
        when(request.getPathParamByKey("id")).thenReturn("");
        when(request.getQueryParamByKey("deleteAll")).thenReturn("0");
        when(securityService.isApiKeyAuthentication()).thenReturn(true);
        revokeApiKeyService.execute(request, response);
        verify(response).responseCode(HttpServletResponse.SC_FORBIDDEN);
        verify(response).error("The use of apiKey as authentication is forbidden for this api call");
    }

    @Test
    void testRevokeApiKeyUserNotExist() {
        when(securityService.isAnonymous()).thenReturn(false);
        when(request.getPathParamByKey("id")).thenReturn("nonexistentUser");
        when(request.getQueryParamByKey("deleteAll")).thenReturn("0");
        when(securityService.currentUsername()).thenReturn("admin");
        when(securityService.isAdmin()).thenReturn(true);
        when(apiKeyManager.revokeToken("nonexistentUser")).thenReturn(false);
        revokeApiKeyService.execute(request, response);
        verify(response).error(
                "Unable to revoke API key for user: nonexistentUser. The user may not exist or does not have an API " +
                        "key property.");
    }

    @Test
    void testRevokeAllApiKeysError() {
        when(securityService.isAnonymous()).thenReturn(false);
        when(request.getPathParamByKey("id")).thenReturn("");
        when(request.getQueryParamByKey("deleteAll")).thenReturn("1");
        when(securityService.isAdmin()).thenReturn(true);
        when(apiKeyManager.revokeAllTokens()).thenReturn(false);
        revokeApiKeyService.execute(request, response);
        verify(response).error("Error revoking all api keys");
    }
}