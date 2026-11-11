package org.artifactory.security.token;

import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.authentication.OauthAuthentication;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.security.providermgr.ArtifactoryCacheKey;
import org.artifactory.security.providermgr.ProviderMgr;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
class ArtifactoryTokenProviderTest {

    private static MockedStatic<SecurityContextHolder> securityContextHolder;

    @BeforeAll
    static void beforeAll() {
        securityContextHolder = mockStatic(SecurityContextHolder.class);
    }

    @AfterAll
    static void afterAll() {
        securityContextHolder.close();
    }

    @Test
    void testGetToken() {
        authenticate();
        ArtifactoryTokenProvider tokenProvider = new ArtifactoryTokenProvider();
        ArtifactoryCacheKey cacheKey = mock(ArtifactoryCacheKey.class);
        when(cacheKey.getUser()).thenReturn("test");
        when(cacheKey.getTokenType()).thenReturn(TokenType.CONAN);
        ProviderMgr providerMgr = mock(ProviderMgr.class);
        AuthenticationModel oauthModel = new AuthenticationModel();
        oauthModel.setExpiresIn(10);
        oauthModel.setToken("token");
        when(providerMgr.fetchAndStoreTokenFromProvider(new TokenManagerKey("test", TokenType.CONAN)))
                .thenReturn(oauthModel);
        when(cacheKey.getProviderMgr()).thenReturn(providerMgr);
        OauthModel token = tokenProvider.getToken(cacheKey);
        assertNotNull(token);
        assertTrue(token instanceof AuthenticationModel);
        assertEquals(10, (int) ((AuthenticationModel) token).getExpiresIn());
        assertEquals("token", ((AuthenticationModel) token).getToken());
    }

    @Test
    void testGetTokenOauth() {
        OauthAuthentication oauthAuthentication = mock(OauthAuthentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(oauthAuthentication);
        securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        OauthModel oauthModel = mock(OauthModel.class);
        when(oauthAuthentication.getModel()).thenReturn(oauthModel);
        ArtifactoryTokenProvider tokenProvider = new ArtifactoryTokenProvider();
        OauthModel token = tokenProvider.getToken(mock(ArtifactoryCacheKey.class));
        assertEquals(token, oauthModel);
    }

    @Test
    void testGetTokenWithBearer() {
        authenticate();
        String givenToken = "someToken";
        ArtifactoryCacheKey artifactoryCacheKey = mock(ArtifactoryCacheKey.class, CALLS_REAL_METHODS);
        artifactoryCacheKey.setBasicauth("Bearer " + givenToken);
        clearInvocations(artifactoryCacheKey);
        ArtifactoryTokenProvider tokenProvider = new ArtifactoryTokenProvider();
        AuthenticationModel token = (AuthenticationModel) tokenProvider.getToken(artifactoryCacheKey);
        assertEquals(givenToken, token.getToken());
        assertNull(token.getExpiresIn());
        assertNull(token.getIssuedAt());
        verify(artifactoryCacheKey).getBearerToken();
        verify(artifactoryCacheKey).getUser();
        verifyNoMoreInteractions(artifactoryCacheKey);
    }

    @Test
    void testGetTokenWithAccessToken() {
        String token = "access";
        AuthenticationModel authenticationModel = new AuthenticationModel();
        authenticationModel.setToken(token);
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(accessTokenAuthentication);
        securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(accessTokenAuthentication.getAccessTokenValue()).thenReturn(token);
        ArtifactoryTokenProvider tokenProvider = new ArtifactoryTokenProvider();
        AuthenticationModel res = (AuthenticationModel) tokenProvider.getToken(mock(ArtifactoryCacheKey.class));
        assertEquals(res.getToken(), authenticationModel.getToken());
        assertEquals(res.getIssuedAt(), authenticationModel.getIssuedAt());
        assertEquals(res.getExpiresIn(), authenticationModel.getExpiresIn());
        verify(accessTokenAuthentication).getAccessTokenValue();
        verify(accessTokenAuthentication).getName();
        verifyNoMoreInteractions(accessTokenAuthentication);
    }

    private void authenticate() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.getName()).thenReturn("test");
        SimpleUser simpleUser = mock(SimpleUser.class);
        when(simpleUser.isTransientUser()).thenReturn(false);
        when(authentication.getPrincipal()).thenReturn(simpleUser);
    }
}