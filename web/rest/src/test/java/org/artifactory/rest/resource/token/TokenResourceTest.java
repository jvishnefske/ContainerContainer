package org.artifactory.rest.resource.token;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.token.TokenResponseErrorModel;
import org.artifactory.security.token.exception.TokenRequestException;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static jakarta.ws.rs.core.Response.Status.OK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.artifactory.common.ConstantValues.accessTokenExpiresInDefault;
import static org.artifactory.common.ConstantValues.accessEmbeddedEnabled;
import static org.artifactory.common.ConstantValues.accessTokenProxyGenerationToAccess;
import static org.artifactory.common.ConstantValues.accessTokenRejectServiceSubject;
import static org.artifactory.common.ConstantValues.blockReferenceTokenOnLegacyTokenEndpoint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Eli Fishman
 */
@ExtendWith(MockitoExtension.class)
class TokenResourceTest {

    @Mock
    private TokenResource tokenResource;
    @Mock
    private AccessService accessService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HttpServletRequest request;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;

    @BeforeEach
    void setUp() {
        ArtifactoryHome.bind(artifactoryHome);
        tokenResource = new TokenResource();
        setField(tokenResource, "accessService", accessService);
        setField(tokenResource, "securityService", securityService);
        setField(tokenResource, "addonsManager", addonsManager);
        setField(tokenResource, "request", request);
        // The reference-token guard runs on every request, so every test needs the flag resolvable.
        lenient().when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        lenient().when(artifactorySystemProperties.getBooleanProperty(blockReferenceTokenOnLegacyTokenEndpoint))
                .thenReturn(FALSE);
        // Most tests cover the stock proxy-to-Access path; embedded mode has its own test.
        lenient().when(artifactorySystemProperties.getBooleanProperty(accessEmbeddedEnabled)).thenReturn(FALSE);
    }

    @Test
    void embeddedAccessNeverProxiesTokenCreationToAccess() {
        String grantType = "client_credentials";
        String username = "user1";
        when(artifactorySystemProperties.getBooleanProperty(accessEmbeddedEnabled)).thenReturn(TRUE);
        lenient().when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess))
                .thenReturn(TRUE);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(securityService.isAdmin()).thenReturn(true);
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(accessService.createToken(any(TokenSpec.class))).thenReturn(createdTokenInfo);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("cool-name");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);

        Response response =
                tokenResource.createOrRefreshToken(grantType, username, null, null, null, null, false, null);

        verify(accessService, never()).createTokenAsProxy(any(TokenSpec.class), any(), any());
        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        SecurityContextHolder.clearContext();
    }

    private void stubReferenceTokenBlockFlag(boolean enabled) {
        when(artifactorySystemProperties.getBooleanProperty(blockReferenceTokenOnLegacyTokenEndpoint))
                .thenReturn(enabled);
    }

    private void setAccessTokenAuthentication(String tokenValue) {
        SecurityContextHolder.getContext()
                .setAuthentication(new AccessTokenAuthentication(tokenValue, "user1", null));
    }

    @Test
    void testLegacyTokenCreation() {
        String grantType = "client_credentials";
        String username = "user1";

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess)).thenReturn(FALSE);

        when(securityService.isAdmin()).thenReturn(true);
        ProxyTokenResponse proxyTokenResponse = mock(ProxyTokenResponse.class);
        when(accessService.createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType))).thenReturn(
                proxyTokenResponse);
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(accessService.createToken(any(TokenSpec.class))).thenReturn(createdTokenInfo);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("cool-name");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);

        Response response =
                tokenResource.createOrRefreshToken(grantType, username, null, null, null, null, false, null);

        verify(accessService).createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType));
        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertNotNull(response);
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testLegacyTokenCreationException() {
        String grantType = "client_credentials";
        String username = "user1";

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess)).thenReturn(FALSE);

        when(securityService.isAdmin()).thenReturn(true);
        when(accessService.createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType)))
                .thenThrow(new IllegalArgumentException());
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(accessService.createToken(any(TokenSpec.class))).thenReturn(createdTokenInfo);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("cool-name");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);

        Response response =
                tokenResource.createOrRefreshToken(grantType, username, null, null, null, null, false, null);

        verify(accessService).createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType));
        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertNotNull(response);
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testProxyTokenCreationToAccess() {
        String grantType = "client_credentials";
        String username = "user1";

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess)).thenReturn(TRUE);

        when(securityService.isAdmin()).thenReturn(true);
        ProxyTokenResponse proxyTokenResponse = mock(ProxyTokenResponse.class);
        when(accessService.createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType)))
                .thenReturn(proxyTokenResponse);

        Response response =
                tokenResource.createOrRefreshToken(grantType, username, null, null, null, null, false, null);

        verify(accessService).createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType));
        verify(accessService, times(0)).createToken(any(TokenSpec.class));
        assertNotNull(response);
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testProxyTokenCreationToAccessException() {
        String grantType = "client_credentials";
        String username = "user1";

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess)).thenReturn(TRUE);

        when(securityService.isAdmin()).thenReturn(true);
        when(accessService.createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType)))
                .thenThrow(new IllegalArgumentException());

        assertThrows(TokenRequestException.class,
                () -> tokenResource.createOrRefreshToken(grantType, username, null, null, null, null, false, null));

        verify(accessService).createTokenAsProxy(any(TokenSpec.class), eq(username), eq(grantType));
        verify(accessService, times(0)).createToken(any(TokenSpec.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "anyservice@inst1",
            "jfrt@inst1",
            "jf-artifactory@inst1",
            "anyservice@inst1/nodes/node1",
            "jfrt@inst1/nodes/node1",
            "JFRT@INST1",
            "jfrt@inst1/foo",
            "jfac@inst1/foo",
            "jf-access@inst1/foo",
            "jfac@inst1/users/alice",
            "jfmc@inst1/foo",
            "jfxr@inst1/foo",
            "jfrt@inst1/users/alice/extra",
            "jfrt@inst1/USERS/alice",
            "JFAC@inst1/foo",
            "jfrtfs@inst1/users/alice"
    })
    void testServiceSubjectIsRejectedAndNeverReachesAccess(String serviceSubject) {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        when(securityService.isAdmin()).thenReturn(true);

        assertRejectedAsInvalidUsername(
                () -> tokenResource.createOrRefreshToken("client_credentials", serviceSubject, null, null, null, null,
                        false, null));

        verify(accessService, never()).createToken(any(TokenSpec.class));
        verify(accessService, never()).createTokenAsProxy(any(TokenSpec.class), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "a@b@c"})
    void testPlainUsernameIsAccepted(String username) {
        stubTokenCreation(username);

        Response response = tokenResource.createOrRefreshToken("client_credentials", username, null, null, null, null,
                false, null);

        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jfrt@inst1/users/alice", "jfrt@other1/users/alice",
            "jf-artifactory@inst1/users/alice"})
    void testArtifactoryUserSubjectIsAccepted(String username) {
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        stubTokenCreation(username);

        Response response = tokenResource.createOrRefreshToken("client_credentials", username, null, null, null, null,
                false, null);

        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    private void stubTokenCreation(String username) {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenProxyGenerationToAccess)).thenReturn(FALSE);
        when(securityService.isAdmin()).thenReturn(true);
        when(accessService.createTokenAsProxy(any(TokenSpec.class), eq(username), eq("client_credentials")))
                .thenReturn(mock(ProxyTokenResponse.class));
        when(accessService.createToken(any(TokenSpec.class))).thenReturn(mock(CreatedTokenInfo.class));
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("jfrt@inst1");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
    }

    @Test
    void testServiceSubjectIsRejectedForNonAdmin() {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(accessTokenExpiresInDefault)).thenReturn(0L);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAuthenticated()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);

        assertRejectedAsInvalidUsername(
                () -> tokenResource.createOrRefreshToken("client_credentials", "anyservice@inst1", null, null, null,
                        null, false, null));

        verify(accessService, never()).createToken(any(TokenSpec.class));
        verify(accessService, never()).createTokenAsProxy(any(TokenSpec.class), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"anyservice@inst1", "jfac@inst1/foo", "jfrt@inst1/nodes/node1"})
    void testServiceSubjectIsAcceptedWhenRejectionFlagIsOff(String serviceSubject) {
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(FALSE);
        stubTokenCreation(serviceSubject);

        Response response = tokenResource.createOrRefreshToken("client_credentials", serviceSubject, null, null, null,
                null, false, null);

        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jfac@inst1/foo", "anyservice@inst1", "jfrt@inst1/nodes/node1"})
    void testRefreshIsRejectedWhenPresentedTokenHasDisallowedSubject(String tokenSubject) {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        when(securityService.isAdmin()).thenReturn(true);
        JwtAccessToken presented = mock(JwtAccessToken.class);
        when(presented.getSubject()).thenReturn(tokenSubject);
        when(accessService.parseToken("presented-access-token")).thenReturn(presented);

        assertRejectedAsInvalidUsername(
                () -> tokenResource.createOrRefreshToken("refresh_token", null, "some-refresh-token",
                        "presented-access-token", null, null, false, null));

        verify(accessService, never()).refreshToken(any(TokenSpec.class), any(), any());
    }

    @Test
    void testRefreshIsAllowedWhenPresentedTokenHasUserSubject() {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        when(securityService.isAdmin()).thenReturn(true);
        JwtAccessToken presented = mock(JwtAccessToken.class);
        when(presented.getSubject()).thenReturn("jfrt@inst1/users/alice");
        when(accessService.parseToken("presented-access-token")).thenReturn(presented);
        when(accessService.refreshToken(any(TokenSpec.class), eq("presented-access-token"), eq("some-refresh-token")))
                .thenReturn(mock(CreatedTokenInfo.class));

        Response response = tokenResource.createOrRefreshToken("refresh_token", null, "some-refresh-token",
                "presented-access-token", null, null, false, null);

        verify(accessService, times(1)).refreshToken(any(TokenSpec.class), any(), any());
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"token:jfac@inst1/foo", "jfac@inst1@x/foo", "jfrt@inst1 /users/alice"})
    void testNonFqnUsernameFallsThroughToUserTokenSpec(String username) {
        stubTokenCreation(username);

        Response response = tokenResource.createOrRefreshToken("client_credentials", username, null, null, null, null,
                false, null);

        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testRefreshIgnoresUsernameAndValidatesOnlyPresentedTokenSubject() {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(accessTokenRejectServiceSubject)).thenReturn(TRUE);
        when(securityService.isAdmin()).thenReturn(true);
        JwtAccessToken presented = mock(JwtAccessToken.class);
        when(presented.getSubject()).thenReturn("jfrt@inst1/users/alice");
        when(accessService.parseToken("presented-access-token")).thenReturn(presented);
        when(accessService.refreshToken(any(TokenSpec.class), eq("presented-access-token"), eq("some-refresh-token")))
                .thenReturn(mock(CreatedTokenInfo.class));

        Response response = tokenResource.createOrRefreshToken("refresh_token", "jfac@inst1/foo",
                "some-refresh-token", "presented-access-token", null, null, false, null);

        verify(accessService, times(1)).refreshToken(any(TokenSpec.class), any(), any());
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    private void assertRejectedAsInvalidUsername(Executable call) {
        TokenRequestException exception = assertThrows(TokenRequestException.class, call);
        TokenResponseErrorModel error = (TokenResponseErrorModel) exception.getResponse().getEntity();
        assertEquals("Invalid username", error.getErrorDescription());
    }

    @Test
    void testReferenceTokenRejectedOnLegacyEndpointWhenFlagEnabled() {
        stubReferenceTokenBlockFlag(true);
        setAccessTokenAuthentication("presented-reference-token");
        when(securityService.isAdmin()).thenReturn(true);
        when(accessService.getAccessTokenType("presented-reference-token"))
                .thenReturn(AccessTokenType.REFERENCE_TOKEN);

        TokenRequestException exception = assertThrows(TokenRequestException.class,
                () -> tokenResource.createOrRefreshToken("client_credentials", "user1", null, null, null, null,
                        false, null));
        TokenResponseErrorModel error = (TokenResponseErrorModel) exception.getResponse().getEntity();
        assertEquals("invalid_request", error.getError());

        verify(accessService, never()).createToken(any(TokenSpec.class));
        verify(accessService, never()).createTokenAsProxy(any(TokenSpec.class), any(), any());
    }

    @Test
    void testReferenceTokenRejectedOnLegacyEndpointForRefreshGrantWhenFlagEnabled() {
        stubReferenceTokenBlockFlag(true);
        setAccessTokenAuthentication("presented-reference-token");
        when(securityService.isAdmin()).thenReturn(true);
        when(accessService.getAccessTokenType("presented-reference-token"))
                .thenReturn(AccessTokenType.REFERENCE_TOKEN);

        TokenRequestException exception = assertThrows(TokenRequestException.class,
                () -> tokenResource.createOrRefreshToken("refresh_token", null, "some-refresh-token",
                        "presented-access-token", null, null, false, null));
        TokenResponseErrorModel error = (TokenResponseErrorModel) exception.getResponse().getEntity();
        assertEquals("invalid_request", error.getError());

        verify(accessService, never()).parseToken(any());
        verify(accessService, never()).refreshToken(any(TokenSpec.class), any(), any());
    }

    @Test
    void testJwtAccessTokenAllowedOnLegacyEndpointWhenFlagEnabled() {
        stubReferenceTokenBlockFlag(true);
        setAccessTokenAuthentication("presented-jwt-token");
        when(accessService.getAccessTokenType("presented-jwt-token")).thenReturn(AccessTokenType.JWT_ACCESS_TOKEN);
        stubTokenCreation("user1");

        Response response = tokenResource.createOrRefreshToken("client_credentials", "user1", null, null, null, null,
                false, null);

        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testReferenceTokenAllowedOnLegacyEndpointWhenFlagDisabled() {
        stubReferenceTokenBlockFlag(false);
        setAccessTokenAuthentication("presented-reference-token");
        stubTokenCreation("user1");

        Response response = tokenResource.createOrRefreshToken("client_credentials", "user1", null, null, null, null,
                false, null);

        verify(accessService, never()).getAccessTokenType(any());
        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void testNonAccessTokenAuthenticationSkipsReferenceTokenCheck() {
        stubReferenceTokenBlockFlag(true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user1", "credentials"));
        stubTokenCreation("user1");

        Response response = tokenResource.createOrRefreshToken("client_credentials", "user1", null, null, null, null,
                false, null);

        verify(accessService, never()).getAccessTokenType(any());
        verify(accessService, times(1)).createToken(any(TokenSpec.class));
        assertEquals(OK.getStatusCode(), response.getStatus());
    }
}
