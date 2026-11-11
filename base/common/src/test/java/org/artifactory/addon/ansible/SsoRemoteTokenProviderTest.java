package org.artifactory.addon.ansible;

import org.artifactory.addon.AddonsManager;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoRemoteTokenProviderTest {

    private static final String REPO_KEY = "ansible-remote";
    private static final String AUTH_URL = "https://sso.redhat.com/token";
    private static final String REFRESH_TOKEN = "test-refresh-token";

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ArtifactoryContext mockContext;

    @Mock
    private AddonsManager mockAddonsManager;

    @Mock
    private AnsibleAddon mockAnsibleAddon;

    private MockedStatic<ContextHelper> contextHelperMock;
    private SsoRemoteTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        contextHelperMock = mockStatic(ContextHelper.class);
        contextHelperMock.when(ContextHelper::get).thenReturn(mockContext);
        lenient().when(mockContext.beanForType(AddonsManager.class)).thenReturn(mockAddonsManager);
        lenient().when(mockAddonsManager.addonByType(AnsibleAddon.class)).thenReturn(mockAnsibleAddon);

        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        tokenProvider = new SsoRemoteTokenProvider(repositoryService);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        if (contextHelperMock != null) {
            contextHelperMock.close();
        }
    }

    @Test
    void getTokenReturnsNullWhenRepoKeyIsNull() {
        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", null));
    }

    @Test
    void getTokenReturnsNullWhenRepoConfigNotFound() {
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(null);

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void getTokenReturnsNullWhenAuthUrlMissing() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of(), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void getTokenReturnsNullWhenPasswordMissing() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), null);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void getTokenReturnsNullWhenPackageTypeConfigIsNull() {
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageTypeConfig()).thenReturn(null);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void getTokenReturnsAccessTokenOnSuccess() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(3600);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(tokenModel);

        String token = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);

        assertEquals("test-access-token", token);
        verify(mockAnsibleAddon, times(1)).fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY);
    }

    @Test
    void getTokenReturnsCachedTokenOnSecondCall() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(3600);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(tokenModel);

        String token1 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);
        String token2 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);

        assertEquals("test-access-token", token1);
        assertEquals("test-access-token", token2);
        verify(mockAnsibleAddon, times(1)).fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY);
    }

    @Test
    void getTokenFetchesNewTokenWhenCachedTokenExpired() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        OAuth2TokenModel tokenModel1 = new OAuth2TokenModel("token1");
        tokenModel1.setExpiresIn(-1);
        OAuth2TokenModel tokenModel2 = new OAuth2TokenModel("token2");
        tokenModel2.setExpiresIn(3600);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(tokenModel1)
                .thenReturn(tokenModel2);

        String token1 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);
        String token2 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);

        assertEquals("token1", token1);
        assertEquals("token2", token2);
        verify(mockAnsibleAddon, times(2)).fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY);
    }

    @Test
    void getTokenFallsBackToExpiringSoonTokenWhenRefreshFails() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        OAuth2TokenModel expiringSoonToken = new OAuth2TokenModel("expiring-soon-token");
        expiringSoonToken.setExpiresIn(30);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(expiringSoonToken)
                .thenReturn(null);

        String token1 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);
        String token2 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);

        assertEquals("expiring-soon-token", token1);
        assertEquals("expiring-soon-token", token2);
        verify(mockAnsibleAddon, times(2)).fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY);
    }

    @Test
    void getTokenReturnsNullWhenRefreshFailsAndTokenDefinitivelyExpired() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        OAuth2TokenModel expiredToken = new OAuth2TokenModel("dead-token");
        expiredToken.setExpiresIn(-1);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(expiredToken)
                .thenReturn(null);

        String token1 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);
        String token2 = tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY);

        assertEquals("dead-token", token1);
        assertNull(token2);
        verify(mockAnsibleAddon, times(2)).fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY);
    }

    @Test
    void getTokenReturnsNullWhenAddonReturnsNull() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(null);

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void getTokenReturnsNullWhenAddonReturnsEmptyToken() {
        RemoteRepoConfig repoConfig = mockRepoConfig(Map.of("authUrl", AUTH_URL), REFRESH_TOKEN);
        when(repositoryService.getRemoteRepoConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        when(mockAnsibleAddon.fetchSsoToken(AUTH_URL, REFRESH_TOKEN, REPO_KEY)).thenReturn(new OAuth2TokenModel(""));

        assertNull(tokenProvider.getToken(new HashMap<>(), "GET", "/api/collections", REPO_KEY));
    }

    @Test
    void cacheKeyEqualsWhenRepoKeyAuthUrlAndRefreshTokenMatch() {
        SsoTokenCacheKey key1 = SsoTokenCacheKey.of(REPO_KEY, AUTH_URL, REFRESH_TOKEN);
        SsoTokenCacheKey key2 = SsoTokenCacheKey.of(REPO_KEY, AUTH_URL, REFRESH_TOKEN);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void cacheKeyNotEqualsWhenRepoKeysDiffer() {
        SsoTokenCacheKey key1 = SsoTokenCacheKey.of(REPO_KEY, AUTH_URL, REFRESH_TOKEN);
        SsoTokenCacheKey key2 = SsoTokenCacheKey.of("other-repo", AUTH_URL, REFRESH_TOKEN);

        assertNotEquals(key1, key2);
    }

    @Test
    void cacheKeyNotEqualsWhenRefreshTokensDiffer() {
        SsoTokenCacheKey key1 = SsoTokenCacheKey.of(REPO_KEY, AUTH_URL, "refresh-token-1");
        SsoTokenCacheKey key2 = SsoTokenCacheKey.of(REPO_KEY, AUTH_URL, "refresh-token-2");

        assertNotEquals(key1, key2);
    }

    @Test
    void cachedTokenNotExpiredWhenExpiresInFuture() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(3600);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertFalse(cached.isExpiredOrExpiringSoon());
        assertEquals("test-access-token", cached.getAccessToken());
    }

    @Test
    void cachedTokenExpiredWhenExpiresInPast() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(-100);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertTrue(cached.isExpiredOrExpiringSoon());
    }

    @Test
    void cachedTokenNotExpiredWhenExpiresInZero() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(0);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertFalse(cached.isExpiredOrExpiringSoon());
    }

    @Test
    void cachedTokenIsExpiringSoonButNotDefinitivelyExpired() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(30);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertTrue(cached.isExpiredOrExpiringSoon());
        assertFalse(cached.isDefinitivelyExpired());
    }

    @Test
    void isDefinitivelyExpiredReturnsTrueWhenExpiresInNegative() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(-1);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertTrue(cached.isDefinitivelyExpired());
    }

    @Test
    void isDefinitivelyExpiredReturnsFalseWhenExpiresInZero() {
        OAuth2TokenModel tokenModel = new OAuth2TokenModel("test-access-token");
        tokenModel.setExpiresIn(0);
        CachedOAuth2Token cached = new CachedOAuth2Token(tokenModel);

        assertFalse(cached.isDefinitivelyExpired());
    }

    private RemoteRepoConfig mockRepoConfig(Map<String, String> packageTypeConfig, String password) {
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        lenient().when(repoTypeConfig.getPassword()).thenReturn(password);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        return repoConfig;
    }
}
