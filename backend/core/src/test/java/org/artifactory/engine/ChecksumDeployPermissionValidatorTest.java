package org.artifactory.engine;

import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.search.exception.InvalidChecksumException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.search.InternalSearchService;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

class ChecksumDeployPermissionValidatorTest extends ArtifactoryHomeBoundJUnitTest {

    public static final String X_CHECKSUM_DEPLOY_TOKEN = "X-Checksum-Deploy-Token";
    public static final String INTERNAL_CHECKSUM_DEPLOY_W = "internal:checksum-deploy:w";
    private final static String sha1 = "f8237d8959e03355010bb85cc3dc46a46fb31110";

    @Mock
    private InternalSearchService searchService;

    @Mock
    private AuthorizationService authService;

    @Mock
    private SecurityService securityService;

    @Mock
    private AccessService accessService;

    @Mock
    private ArtifactoryRequest request;

    @Mock
    private ArtifactoryResponse response;

    @Mock
    private AccessClient accessClient;

    private ChecksumDeployPermissionValidator validator;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        validator = new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Check if the checksums are not matched, the checksum from the request and the one from the scoped token.
     */
    @ParameterizedTest
    @CsvSource({
            "7a2235055a6f627b1c2dc764afb2d920d83fa30b, " +
                    "74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14, false",
            ", 74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14, false",
            ", ,false"
    })
    void mpuUnmatchedChecksums(String sha1, String sha2, boolean expectedResult)
            throws IOException {
        when(request.getRepoKey()).thenReturn("repoKey");
        when(request.getPath()).thenReturn("repoPath");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(
                "637828c03aae38af639cc721200f2584864e8797");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(
                "102d0177f8ce6b0f5ada79780043ec90440529a53c98bd6043419e64c1d4274f");
        when(request.getHeaderValues("X-Checksum-Deploy-Token"))
                .thenReturn(Collections.enumeration(List.of("validToken")));
        when(request.getRepoKey()).thenReturn("repoKey");
        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenVerifyResult tokenVerifyResult = mock(TokenVerifyResult.class);
        when(tokenClient.verify("validToken")).thenReturn(tokenVerifyResult);
        when(tokenVerifyResult.isSuccessful()).thenReturn(true);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(tokenVerifyResult.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getScope()).thenReturn(List.of(INTERNAL_CHECKSUM_DEPLOY_W));
        when(accessToken.hasScopeMatching(any())).thenReturn(true);
        when(accessToken.getExtension()).thenReturn(
                "{\"repoKey\":\"repoKey\",\"repoPath\":\"repoPath\",\"sha1\":\"" + sha1 + "\"," +
                        "\"sha2\":\"" + sha2 + "\",\"md5\":\"md5checksum\"}");
        when(securityService.isAdmin()).thenReturn(false);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @CsvSource({
            "7a2235055a6f627b1c2dc764afb2d920d83fa30b, 7a2235055a6f627b1c2dc764afb2d920d83fa30b, " +
                    ", 74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14, true",
            ", , 74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14, " +
                    "74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14, true",
    })
    void mpuAllowedChecksumDeploy(String headerSha1, String tokenSha1, String headerSha2, String tokenSha2,
            boolean expectedResult)
            throws IOException, InvalidChecksumException {
        when(request.getRepoKey()).thenReturn("repoKey");
        when(request.getPath()).thenReturn("repoPath");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(
                headerSha1);
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(
                headerSha2);
        when(request.getHeader("X-Checksum-Deploy-Token")).thenReturn("validToken");
        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenVerifyResult tokenVerifyResult = mock(TokenVerifyResult.class);
        when(tokenClient.verify("validToken")).thenReturn(tokenVerifyResult);
        when(tokenVerifyResult.isSuccessful()).thenReturn(true);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(tokenVerifyResult.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getScope()).thenReturn(List.of(INTERNAL_CHECKSUM_DEPLOY_W));
        when(accessToken.hasScopeMatching(any())).thenReturn(true);
        when(accessToken.getExtension()).thenReturn(
                "{\"repoKey\":\"repoKey\",\"repoPath\":\"repoPath\",\"sha1\":\"" + tokenSha1 + "\"," +
                        "\"sha2\":\"" + tokenSha2 + "\",\"md5\":\"md5checksum\"}");
        when(securityService.isAdmin()).thenReturn(false);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertEquals(expectedResult, result);
    }

    @Test
    void noMpuHeader()
            throws IOException {
        when(request.getHeader("X-Checksum-Deploy-Token")).thenReturn("");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(
                "7a2235055a6f627b1c2dc764afb2d920d83fa30b");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(
                "74bf26f6666ee8a561d5a8fa2d5e9246e4de4dd53bb7b1f3fe86a84662536a14");

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(true);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertTrue(result);
        Mockito.verify(securityService, times(1)).isAdmin();
    }

    @Test
    void notVerifiedToken()
            throws IOException {
        when(request.getRepoKey()).thenReturn("repoKey");
        when(request.getPath()).thenReturn("repoPath");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(
                "637828c03aae38af639cc721200f2584864e8797");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(
                "102d0177f8ce6b0f5ada79780043ec90440529a53c98bd6043419e64c1d4274f");
        when(request.getHeader("X-Checksum-Deploy-Token")).thenReturn("validToken");

        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenVerifyResult tokenVerifyResult = mock(TokenVerifyResult.class);
        when(tokenClient.verify("validToken")).thenReturn(tokenVerifyResult);
        when(tokenVerifyResult.isSuccessful()).thenReturn(false);
        when(securityService.isAdmin()).thenReturn(true);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertTrue(result);
        Mockito.verify(securityService, times(1)).isAdmin();
    }

    @ParameterizedTest
    @CsvSource({
            "repoKey1, repoPath1, repoKey2, repoPath2, false"
    })
    void testUnmatchedRepoKeyAndRepoPath(String requestRepoKey, String requestRepoPath, String tokenRepoKey,
            String tokenRepoPath, boolean expectedResult)
            throws IOException {
        when(request.getRepoKey()).thenReturn(requestRepoKey);
        when(request.getPath()).thenReturn(requestRepoPath);
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(
                "637828c03aae38af639cc721200f2584864e8797");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(
                "102d0177f8ce6b0f5ada79780043ec90440529a53c98bd6043419e64c1d4274f");
        when(request.getHeaderValues(X_CHECKSUM_DEPLOY_TOKEN)).thenReturn(
                Collections.enumeration(List.of("validToken")));

        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenVerifyResult tokenVerifyResult = mock(TokenVerifyResult.class);
        when(tokenClient.verify("validToken")).thenReturn(tokenVerifyResult);
        when(tokenVerifyResult.isSuccessful()).thenReturn(true);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(tokenVerifyResult.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getScope()).thenReturn(List.of(INTERNAL_CHECKSUM_DEPLOY_W));
        when(accessToken.hasScopeMatching(any())).thenReturn(true);
        when(accessToken.getExtension()).thenReturn(
                "{\"repoKey\":\"" + tokenRepoKey + "\",\"repoPath\":\"" + tokenRepoPath +
                        "\",\"sha1\":\"sha1checksum\",\"sha2\":\"sha2checksum\",\"md5\":\"md5checksum\"}");
        when(securityService.isAdmin()).thenReturn(false);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertEquals(expectedResult, result);
    }


    @ParameterizedTest
    @CsvSource({
            "repoKey1, repoPath1, repoKey2, repoPath2, false"
    })
    void testNoChecksumRepoKeyAndRepoPath(String requestRepoKey, String requestRepoPath, String tokenRepoKey,
            String tokenRepoPath, boolean expectedResult)
            throws IOException {
        when(request.getRepoKey()).thenReturn(requestRepoKey);
        when(request.getPath()).thenReturn(requestRepoPath);
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1)).thenReturn(null);
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256)).thenReturn(null);
        when(request.getHeaderValues(X_CHECKSUM_DEPLOY_TOKEN)).thenReturn(
                Collections.enumeration(List.of("validToken")));

        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenVerifyResult tokenVerifyResult = mock(TokenVerifyResult.class);
        when(tokenClient.verify("validToken")).thenReturn(tokenVerifyResult);
        when(tokenVerifyResult.isSuccessful()).thenReturn(true);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(tokenVerifyResult.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getScope()).thenReturn(List.of(INTERNAL_CHECKSUM_DEPLOY_W));
        when(accessToken.hasScopeMatching(any())).thenReturn(true);
        when(accessToken.getExtension()).thenReturn(
                "{\"repoKey\":\"" + tokenRepoKey + "\",\"repoPath\":\"" + tokenRepoPath +
                        "\",\"sha1\":\"sha1checksum\",\"sha2\":\"sha2checksum\",\"md5\":\"md5checksum\"}");
        when(securityService.isAdmin()).thenReturn(false);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService);

        boolean result = validator.hasReadPermission(request, response);

        assertEquals(expectedResult, result);
    }

    @Test
    void testIsAllowedByChecksumDeployTokenException() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getHeader(X_CHECKSUM_DEPLOY_TOKEN)).thenReturn("invalidToken");

        // Mock the behavior to throw an exception
        AccessService accessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.verify(anyString())).thenThrow(
                new RuntimeException("Test Exception"));

        SecurityService securityService = mock(SecurityService.class);
        ChecksumDeployPermissionValidator validator =
                new ChecksumDeployPermissionValidator(searchService,
                        authService, securityService, accessService);
        // Verify that the exception is logged
        assertFalse(validator.isAllowedByChecksumDeployToken(request, ChecksumType.sha1, "checksum"));
    }

    @Test
    void testHasReadPermissionValidPermission() throws InvalidChecksumException {
        ChecksumInfo checksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, sha1);
        RepoPath repoPath = mock(RepoPath.class);
        when(searchService.searchArtifactsByChecksum(any())).thenReturn(Set.of(repoPath));
        when(authService.canRead(repoPath)).thenReturn(true);

        boolean result = validator.hasReadPermission(checksumInfo, "repoKey");

        assertTrue(result);
        verify(searchService, times(1)).searchArtifactsByChecksum(any());
        verify(authService, times(1)).canRead(repoPath);
    }

    @Test
    void testHasReadPermissionNoPermission() throws InvalidChecksumException {
        ChecksumInfo checksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, sha1);
        RepoPath repoPath = mock(RepoPath.class);
        when(searchService.searchArtifactsByChecksum(any())).thenReturn(Set.of(repoPath));
        when(authService.canRead(repoPath)).thenReturn(false);

        boolean result = validator.hasReadPermission(checksumInfo, "repoKey");

        assertFalse(result);
        verify(searchService, times(1)).searchArtifactsByChecksum(any());
        verify(authService, times(1)).canRead(any());
    }

    @Test
    void testHasReadPermissionInvalidChecksumException() throws InvalidChecksumException {
        ChecksumInfo checksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, sha1);
        when(searchService.searchArtifactsByChecksum(any())).thenThrow(InvalidChecksumException.class);

        assertThrows(InvalidChecksumException.class, () -> validator.hasReadPermission(checksumInfo, "repoKey"));
        verify(searchService, times(1)).searchArtifactsByChecksum(any());
    }

    @Test
    void testHasReadPermissionAdminSkippValidation() throws InvalidChecksumException {
        ChecksumInfo checksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, sha1);
        RepoPath repoPath = mock(RepoPath.class);
        when(securityService.isAdmin()).thenReturn(true);

        boolean result = validator.hasReadPermission(checksumInfo, "repoKey");

        assertTrue(result);
        verify(securityService, times(1)).isAdmin();
        verify(searchService, never()).searchArtifactsByChecksum(any());
        verify(authService, never()).canRead(repoPath);
    }

    @Test
    void testHasReadPermissionSkippValidationCheckIgnored() throws InvalidChecksumException {
        homeStub.setProperty(ConstantValues.ignoreChecksumDeployPermissionCheck, "true");
        ChecksumInfo checksumInfo = new ChecksumInfo(ChecksumType.sha1, sha1, sha1);
        RepoPath repoPath = mock(RepoPath.class);

        boolean result = validator.hasReadPermission(checksumInfo, "repoKey");

        assertTrue(result);
        verify(securityService, never()).isAdmin();
        verify(searchService, never()).searchArtifactsByChecksum(any());
        verify(authService, never()).canRead(repoPath);
    }

    @Test
    void testHasReadPermissionTrue() throws IOException, InvalidChecksumException {
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader("X-Checksum-Sha1")).thenReturn(sha1);
        when(request.getRepoKey()).thenReturn("repoKey");
        RepoPath repoPath = mock(RepoPath.class);
        when(searchService.searchArtifactsByChecksum(any())).thenReturn(Set.of(repoPath));
        when(authService.canRead(repoPath)).thenReturn(true);

        boolean result = validator.hasReadPermission(request, response);

        assertTrue(result);
        verify(authService, times(1)).canRead(repoPath);
        verify(searchService, times(1)).searchArtifactsByChecksum(any());
        verify(response, never()).sendError(anyInt(), anyString(), any());
    }

    @Test
    void testHasReadPermissionByToken() throws IOException, InvalidChecksumException {
        validator =
                spy(new ChecksumDeployPermissionValidator(searchService, authService, securityService, accessService));
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader("X-Checksum-Sha1")).thenReturn(sha1);
        when(validator.isAllowedByChecksumDeployToken(any(), any(), any())).thenReturn(true);
        when(request.getHeaderValues(X_CHECKSUM_DEPLOY_TOKEN)).thenReturn(
                Collections.enumeration(List.of(sha1)));
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(request.getRepoKey()).thenReturn("repoKey");

        boolean result = validator.hasReadPermission(request, response);

        assertTrue(result);
        verify(authService, never()).canRead(any());
        verify(searchService, never()).searchArtifactsByChecksum(any());
        verify(response, never()).sendError(anyInt(), anyString(), any());
    }

    @Test
    void testHasReadPermissionRepoNotFound() throws IOException, InvalidChecksumException {
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader("X-Checksum-Sha1")).thenReturn(sha1);
        when(request.getRepoKey()).thenReturn("repoKey");
        when(searchService.searchArtifactsByChecksum(any())).thenReturn(Set.of());

        boolean result = validator.hasReadPermission(request, response);

        assertFalse(result);
        verify(response, times(1)).sendError(eq(404), contains("Checksum"), any());
    }

    @Test
    void testHasReadPermissionInvalidChecksum() throws IOException, InvalidChecksumException {
        when(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY)).thenReturn("true");
        when(request.getHeader("X-Checksum-Sha1")).thenReturn(sha1);
        when(searchService.searchArtifactsByChecksum(any())).thenThrow(
                new InvalidChecksumException("Invalid checksum"));

        boolean result = validator.hasReadPermission(request, response);

        assertFalse(result);
        verify(response, times(1)).sendError(eq(404), contains("Invalid checksum"), any());
    }
}