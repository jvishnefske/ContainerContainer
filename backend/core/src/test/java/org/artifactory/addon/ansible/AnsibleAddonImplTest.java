package org.artifactory.addon.ansible;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.http.client.methods.HttpPost;
import org.artifactory.repo.AnsibleHttpRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnsibleAddonImplTest {

    private static final String REPO_KEY = "ansible-remote";
    private static final String AUTH_URL = "https://sso.redhat.com/token";
    private static final byte[] HMAC_SECRET = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);

    @Mock
    private InternalRepositoryService repositoryService;

    private AnsibleAddonImpl ansibleAddon;

    @BeforeEach
    void setUp() {
        ansibleAddon = new AnsibleAddonImpl(repositoryService);
    }

    @Test
    void fetchSsoTokenReturnsTokenOnSuccess() throws Exception {
        String refreshToken = createJwtWithAzp("my-client");
        AnsibleHttpRepo ansibleHttpRepo = mock(AnsibleHttpRepo.class);
        OAuth2TokenModel expectedToken = new OAuth2TokenModel("access-token-123");
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(ansibleHttpRepo);
        when(ansibleHttpRepo.fetchAnsibleAuthToken(any(HttpPost.class))).thenReturn(expectedToken);

        OAuth2TokenModel result = ansibleAddon.fetchSsoToken(AUTH_URL, refreshToken, REPO_KEY);

        assertEquals(expectedToken, result);
        assertEquals("access-token-123", result.getAccessToken());
    }

    @Test
    void fetchSsoTokenSendsWellFormedRequest() throws Exception {
        String refreshToken = createJwtWithAzp("my-client");
        AnsibleHttpRepo ansibleHttpRepo = mock(AnsibleHttpRepo.class);
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(ansibleHttpRepo);
        when(ansibleHttpRepo.fetchAnsibleAuthToken(any(HttpPost.class))).thenReturn(new OAuth2TokenModel("token"));

        ansibleAddon.fetchSsoToken(AUTH_URL, refreshToken, REPO_KEY);

        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        verify(ansibleHttpRepo).fetchAnsibleAuthToken(captor.capture());
        HttpPost request = captor.getValue();
        assertEquals(AUTH_URL, request.getURI().toString());
        assertEquals("application/x-www-form-urlencoded", request.getFirstHeader("Content-Type").getValue());
    }

    @Test
    void fetchSsoTokenReturnsNullWhenNotAnsibleHttpRepo() {
        RemoteRepo genericRepo = mock(RemoteRepo.class);
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(genericRepo);

        assertNull(ansibleAddon.fetchSsoToken(AUTH_URL, "any-token", REPO_KEY));
    }

    @Test
    void fetchSsoTokenReturnsNullWhenRepoNotFound() {
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(null);

        assertNull(ansibleAddon.fetchSsoToken(AUTH_URL, "any-token", REPO_KEY));
    }

    @Test
    void fetchSsoTokenReturnsNullWhenRefreshTokenHasNoAzpClaim() throws Exception {
        AnsibleHttpRepo ansibleHttpRepo = mock(AnsibleHttpRepo.class);
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(ansibleHttpRepo);

        assertNull(ansibleAddon.fetchSsoToken(AUTH_URL, createJwtWithoutAzp(), REPO_KEY));
        verify(ansibleHttpRepo, never()).fetchAnsibleAuthToken(any());
    }

    @Test
    void fetchSsoTokenReturnsNullWhenRefreshTokenIsNotJwt() {
        AnsibleHttpRepo ansibleHttpRepo = mock(AnsibleHttpRepo.class);
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(ansibleHttpRepo);

        assertNull(ansibleAddon.fetchSsoToken(AUTH_URL, "not-a-jwt", REPO_KEY));
        verify(ansibleHttpRepo, never()).fetchAnsibleAuthToken(any());
    }

    @Test
    void fetchSsoTokenReturnsNullWhenHttpRepoReturnsNull() throws Exception {
        String refreshToken = createJwtWithAzp("my-client");
        AnsibleHttpRepo ansibleHttpRepo = mock(AnsibleHttpRepo.class);
        when(repositoryService.remoteRepositoryByKey(REPO_KEY)).thenReturn(ansibleHttpRepo);
        when(ansibleHttpRepo.fetchAnsibleAuthToken(any(HttpPost.class))).thenReturn(null);

        assertNull(ansibleAddon.fetchSsoToken(AUTH_URL, refreshToken, REPO_KEY));
    }

    private String createJwtWithAzp(String azp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("azp", azp)
                .subject("user123")
                .build();
        return createSignedJwt(claims);
    }

    private String createJwtWithoutAzp() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user123")
                .build();
        return createSignedJwt(claims);
    }

    private String createSignedJwt(JWTClaimsSet claims) throws Exception {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(HMAC_SECRET));
        return signedJWT.serialize();
    }
}
