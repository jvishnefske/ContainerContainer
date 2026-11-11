package org.artifactory.ui.rest.service.setmeup;

import java.util.List;
import java.util.Set;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.setmeup.AIEditorExtensionTokenResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.artifactory.common.ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled;

/**
 * Unit tests for {@link AIEditorExtensionGenerateTokenHandler}.
 */
class AIEditorExtensionGenerateTokenHandlerTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "ai-remote";
    private static final String REFERENCE_TOKEN = "ref-token-123";
    private static final Set<ArtifactoryPermission> REPO_WRITE = Set.of(DEPLOY);

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private AccessService accessService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    @Mock
    private RestResponse<AIEditorExtensionTokenResponse> response;

    private AIEditorExtensionGenerateTokenHandler handler;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new AIEditorExtensionGenerateTokenHandler(
                authorizationService, repositoryConfigService, projectsService, accessService, securityService);
        getOrCreateArtifactoryHomeStub().setProperty(aieditorExtensionsSetMeUpRepositoryEnabled, "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void execute_shouldThrowBadRequest_whenRepoKeyIsMissing() {
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(null);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Missing repo key parameter", exception.getMessage());
    }

    @Test
    void execute_shouldThrowNullPointerException_whenRequestIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> handler.execute(null, response));

        assertEquals("request", exception.getMessage());
    }

    @Test
    void execute_shouldThrowNullPointerException_whenResponseIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> handler.execute(request, null));

        assertEquals("response", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenRepoKeyIsBlankAfterTrim() {
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn("   ");

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Missing repo key parameter", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenFeatureFlagIsDisabled() {
        getOrCreateArtifactoryHomeStub().setProperty(aieditorExtensionsSetMeUpRepositoryEnabled, "false");
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("AI Editor Extensions Set-Me-Up is not enabled", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenRepoNotFound() {
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(null);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> handler.execute(request, response));

        assertEquals("Unable to generate token for the requested repository", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenRepoIsNotAIEditorExtensions() {
        RepoConfig repoConfig = LocalRepoConfig.create(REPO_KEY, RepoType.Maven);
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Repository 'ai-remote' is not an AI Editor Extensions repository", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenRepoIsNotRemote() {
        RepoConfig repoConfig = LocalRepoConfig.create(REPO_KEY, RepoType.AIEditorExtensions);
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Repository 'ai-remote' must be a remote repository", exception.getMessage());
    }

    @Test
    void execute_shouldThrowForbidden_whenUserHasNoReadPermission() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(false);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> handler.execute(request, response));

        assertEquals("User must have read permission on repository 'ai-remote'", exception.getMessage());
    }

    @Test
    void execute_shouldCreateTokenWithReadOnlyScope_whenUserHasReadButNotDeploy() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        CreatedTokenInfo tokenInfo = createMockTokenInfo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, REPO_WRITE, null))
                .thenReturn(false);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readAll);
        when(securityService.currentUsername()).thenReturn("testuser");
        when(accessService.createToken(any(), eq(true), eq(false))).thenReturn(tokenInfo);

        handler.execute(request, response);

        ArgumentCaptor<org.artifactory.api.security.access.UserTokenSpec> specCaptor =
                ArgumentCaptor.forClass(org.artifactory.api.security.access.UserTokenSpec.class);
        verify(accessService).createToken(specCaptor.capture(), eq(true), eq(false));
        assertEquals(List.of("artifact:ai-remote:r"), specCaptor.getValue().getScope());
    }

    @Test
    void execute_shouldThrowForbidden_whenUserIsNotAuthenticated() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, REPO_WRITE, null))
                .thenReturn(true);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readAll);
        when(securityService.currentUsername()).thenReturn("");

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> handler.execute(request, response));

        assertEquals("User must be authenticated", exception.getMessage());
    }

    @Test
    void execute_shouldThrowBadRequest_whenAccessServiceThrowsException() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, REPO_WRITE, null))
                .thenReturn(true);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readAll);
        when(securityService.currentUsername()).thenReturn("testuser");
        when(accessService.createToken(any(), eq(true), eq(false)))
                .thenThrow(new RuntimeException("Token creation failed"));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Failed to generate token", exception.getMessage());
    }

    @Test
    void execute_shouldReturnTokenResponse_whenAllValidationsPass() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        CreatedTokenInfo tokenInfo = createMockTokenInfo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, REPO_WRITE, null))
                .thenReturn(true);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readAll);
        when(securityService.currentUsername()).thenReturn("testuser");
        when(accessService.createToken(any(), eq(true), eq(false))).thenReturn(tokenInfo);

        handler.execute(request, response);

        ArgumentCaptor<AIEditorExtensionTokenResponse> captor = ArgumentCaptor
                .forClass(AIEditorExtensionTokenResponse.class);
        verify(response).iModel(captor.capture());
        AIEditorExtensionTokenResponse tokenResponse = captor.getValue();
        assertNotNull(tokenResponse);
        assertEquals(REFERENCE_TOKEN, tokenResponse.getReferenceToken());
        assertEquals(REPO_KEY, tokenResponse.getRepoKey());
    }

    @Test
    void execute_shouldCreateTokenWithReadWriteScope_whenUserHasReadAndDeploy() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        CreatedTokenInfo tokenInfo = createMockTokenInfo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, REPO_WRITE, null))
                .thenReturn(true);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readAll);
        when(securityService.currentUsername()).thenReturn("testuser");
        when(accessService.createToken(any(), eq(true), eq(false))).thenReturn(tokenInfo);

        handler.execute(request, response);

        ArgumentCaptor<org.artifactory.api.security.access.UserTokenSpec> specCaptor =
                ArgumentCaptor.forClass(org.artifactory.api.security.access.UserTokenSpec.class);
        verify(accessService).createToken(specCaptor.capture(), eq(true), eq(false));
        org.artifactory.api.security.access.UserTokenSpec capturedSpec = specCaptor.getValue();
        assertEquals(List.of("artifact:ai-remote:r,w"), capturedSpec.getScope());
        assertEquals("testuser", capturedSpec.getUsername());
    }

    // R-LOG-06: a partial reader passes the repository-root check, so the repo-wide token must be refused (RTDEV-97875)
    @Test
    void execute_shouldThrowForbidden_whenUserReadIsRestrictedWithinRepository() {
        RepoConfig repoConfig = createValidAIEditorExtensionsRemoteRepo();
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.getStrongestReadPermissionTarget(REPO_KEY))
                .thenReturn(PermissionHeuristicScore.readWithExclusion);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> handler.execute(request, response));

        assertEquals("User must have unrestricted read permission on repository 'ai-remote'",
                exception.getMessage());
        verify(accessService, never()).createToken(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void execute_shouldThrowBadRequest_whenGetRepoConfigThrowsIllegalArgumentException() {
        when(request.getQueryParamByKey(BaseSetMeUpHandler.REPO_KEY)).thenReturn("invalid-key");
        when(repositoryConfigService.getRepoConfigByKey("invalid-key", true))
                .thenThrow(new IllegalArgumentException("Invalid repository key"));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> handler.execute(request, response));

        assertEquals("Invalid repository key: 'invalid-key'", exception.getMessage());
    }

    private static RemoteRepoConfig createValidAIEditorExtensionsRemoteRepo() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(REPO_KEY, RepoType.AIEditorExtensions);
        assertEquals(RepoType.AIEditorExtensions, repoConfig.getPackageType());
        assertEquals(RepoDetailsType.REMOTE, repoConfig.getRepoType());
        return repoConfig;
    }

    private static CreatedTokenInfo createMockTokenInfo() {
        CreatedTokenInfo tokenInfo = org.mockito.Mockito.mock(CreatedTokenInfo.class);
        when(tokenInfo.getReferenceToken()).thenReturn(AIEditorExtensionGenerateTokenHandlerTest.REFERENCE_TOKEN);
        return tokenInfo;
    }
}
