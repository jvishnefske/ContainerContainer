package org.artifactory.mpu;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.mpu.MultipartUploadInitiateResponse;
import org.jfrog.storage.mpu.MultipartUploadParameters;
import org.jfrog.storage.mpu.entity.MultipartUploadData;
import org.jfrog.storage.mpu.entity.MultipartUploadStatus;
import org.jfrog.storage.mpu.entity.MultipartUploadTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.artifactory.mpu.MultipartUploadServiceImpl.FEATURE_DISABLED_MESSAGE;
import static org.artifactory.mpu.MultipartUploadServiceImpl.FORBIDDEN_DEPLOY_MESSAGE;
import static org.jfrog.storage.mpu.entity.MultipartUploadStatus.Finished;
import static org.jfrog.storage.mpu.entity.MultipartUploadStatus.Parts;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class MultipartUploadServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AccessService accessService;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ConfigsService configsService;
    @Mock
    private TaskService taskService;
    @InjectMocks
    private MultipartUploadServiceImpl mpuService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = openMocks(this);
        doAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            return callable.call();
        }).when(securityService).callAsSystem(any());
        when(binaryService.isMultipartUploadSupported()).thenReturn(true);
        getBound().setProperty(ConstantValues.mpuEnabled, "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("featureDisabled")
    void testGenerateTokenFeatureDisabled(boolean mpuEnabled, boolean mpuSupported) {
        getBound().setProperty(ConstantValues.mpuEnabled, String.valueOf(mpuEnabled));
        when(binaryService.isMultipartUploadSupported()).thenReturn(mpuSupported);
        assertThrows(NotFoundException.class, () -> mpuService.generateToken("cool-repo", "cool-path", 1),
                FEATURE_DISABLED_MESSAGE);
        verify(authorizationService, times(0)).canDeploy(any());
        verify(binaryService, times(0)).initiateMultipartUpload("cool-repo", 1);
        verify(accessService, times(0)).createToken(any());
    }

    public static Stream<Arguments> featureDisabled() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true, false),
                Arguments.of(false, true)
        );
    }

    @Test
    void testGenerateToken() {
        when(authorizationService.canDeploy(any())).thenReturn(true);
        when(repositoryService.repoExists(any())).thenReturn(true);
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(createdTokenInfo.getTokenValue()).thenReturn("cool-token");
        when(accessService.createToken(any(), Mockito.eq(true), Mockito.eq(
                true))).thenReturn(createdTokenInfo);
        MultipartUploadInitiateResponse mpuInitiateResponse =
                new MultipartUploadInitiateResponse("cool-id", "cool-temp-path");
        when(binaryService.initiateMultipartUpload("cool-repo", 1)).thenReturn(mpuInitiateResponse);
        String token = mpuService.generateToken("cool-repo", "cool-path", 1);
        assertEquals("cool-token", token);
        verify(binaryService).isMultipartUploadSupported();
        verify(authorizationService).canDeploy(any());
        verify(securityService).currentUsername();
        verify(binaryService).initiateMultipartUpload("cool-repo", 1);
        verify(accessService).createToken(any(), eq(true), eq(true));
    }

    @Test
    void testGenerateTokenException() {
        when(repositoryService.repoExists("cool-repo")).thenReturn(true);
        when(authorizationService.canDeploy(any())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> mpuService.generateToken("cool-repo", "cool-path", 1),
                FORBIDDEN_DEPLOY_MESSAGE);
        verify(binaryService).isMultipartUploadSupported();
        verify(authorizationService).canDeploy(any());
        verify(binaryService, times(0)).initiateMultipartUpload("cool-repo", 1);
        verify(accessService, times(0)).createToken(any());
    }

    @Test
    void testGenerateTokenExceptionWithInvalidPath() {
        when(authorizationService.canDeploy(any())).thenReturn(true);
        assertThrows(ForbiddenException.class, () -> mpuService.generateToken("cool-repo", "cool-path", 1),
                FORBIDDEN_DEPLOY_MESSAGE);
    }

    @Test
    void testGenerateTokenForVirtualRepo() {
        when(authorizationService.canDeploy(any())).thenReturn(true);
        when(repositoryService.repoExists("virtual-repo")).thenReturn(true);
        when(binaryService.initiateMultipartUpload("virtual-repo", 1)).thenReturn(
                new MultipartUploadInitiateResponse("id", "path"));
        when(accessService.createToken(any(UserTokenSpec.class), eq(true), eq(true))).thenAnswer(invocation -> {
            UserTokenSpec spec = invocation.getArgument(0);
            CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
            when(createdTokenInfo.getTokenValue()).thenReturn(spec.getExtension());
            return createdTokenInfo;
        });
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(repositoryService.repoConfigByKey("virtual-repo")).thenReturn(virtualRepoConfig);
        VirtualRepositoryRepoTypeConfig virtualRepositoryRepoTypeConfig = mock(VirtualRepositoryRepoTypeConfig.class);
        when(virtualRepoConfig.getRepoTypeConfig()).thenReturn(virtualRepositoryRepoTypeConfig);
        when(virtualRepositoryRepoTypeConfig.getDefaultDeploymentRepoRef()).thenReturn("cool-repo");
        String token = mpuService.generateToken("virtual-repo", "cool-path", 1);
        assertTrue(token.contains("virtual-repo"));
    }

    @Test
    void testUrlPart() {
        setAuthentication();
        when(securityService.currentUsername()).thenReturn("cool-user");
        when(projectsService.getProjectKeyOfRepository("cool-repo")).thenReturn("cool-proj");
        RepoDetailsType repoDetailsType = mock(RepoDetailsType.class);
        when(repoDetailsType.typeNameLowercase()).thenReturn("cool-type");
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(repoDetailsType);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repositoryService.repoConfigByKey("cool-repo")).thenReturn(repoConfig);
        mpuService.urlPart(0);
        verify(projectsService).getProjectKeyOfRepository("cool-repo");
        verify(securityService).currentUsername();
        verify(repositoryService).repoConfigByKey("cool-repo");
        ArgumentCaptor<MultipartUploadParameters> captor =
                ArgumentCaptor.forClass(MultipartUploadParameters.class);
        verify(binaryService).generateUploadUrl(any(), captor.capture());
        assertEquals(0, captor.getValue().getPartNumber());
    }

    @Test
    void testAbort() {
        setAuthentication();
        when(binaryService.isMultipartUploadSupported()).thenReturn(true);
        mpuService.abort();
        verify(binaryService).abortMultipartUpload(any(), any());
    }

    @Test
    void testComplete() {
        setAuthentication();
        doNothing().when(binaryService).completeMultipartUpload(any(), any(), any());
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertDoesNotThrow(() -> mpuService.complete(response, "sha1"));
    }

    @Test
    void testStatus() {
        setAuthentication();
        MultipartUploadData data = mock(MultipartUploadData.class);
        when(data.getProgress()).thenReturn(0);
        when(binaryService.getMultipartUploadStatus(any())).thenReturn(
                Pair.of(new MultipartUploadTask(MultipartUploadStatus.Parts, data), ""));
        assertDoesNotThrow(() -> mpuService.status());
    }

    @Test
    void testContextCreated() {
        when(configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_SERVICE_ID)).thenReturn("serviceId");
        mpuService.onContextCreated();
        verify(taskService, times(1)).startTask(any(), anyBoolean());
    }

    private void setAuthentication() {
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getTokenValue()).thenReturn("token");
        String extension = JsonUtils.getInstance()
                .valueToString(new TokenExtension("cool-repo", "cool-path", "cool-id", "cool-temp-path"));
        when(jwtAccessToken.getExtension()).thenReturn(extension);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        when(authentication.getAccessToken()).thenReturn(jwtAccessToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @ParameterizedTest
    @MethodSource("multipartStatusResultProvider")
    void multipartStatusResult(MultipartUploadTask task, String right, String expectedToken) {
        setAuthentication();
        MultipartUploadData data = mock(MultipartUploadData.class);
        when(data.getProgress()).thenReturn(100);
        when(data.getSha1()).thenReturn("sha1");
        when(data.getSha2()).thenReturn("sha2");
        when(data.getMd5()).thenReturn("md5");
        when(task.getData()).thenReturn(data);
        when(binaryService.getMultipartUploadStatus(any())).thenReturn(Pair.of(task, right));
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(accessService.createToken(any(UserTokenSpec.class), eq(true), eq(true))).thenReturn(
                createdTokenInfo);
        when(createdTokenInfo.getTokenValue()).thenReturn("cool-token");
        MultipartStatusResult status = mpuService.status();
        assertEquals(expectedToken, status.token());
    }

    private static Stream<Arguments> multipartStatusResultProvider() {
        MultipartUploadTask finishedTask = mock(MultipartUploadTask.class);
        when(finishedTask.getStatus()).thenReturn(Finished);
        MultipartUploadTask unfinishedTask = mock(MultipartUploadTask.class);
        when(unfinishedTask.getStatus()).thenReturn(Parts);
        when(unfinishedTask.getStatus()).thenReturn(MultipartUploadStatus.Parts);

        return Stream.of(
                Arguments.of(finishedTask, "right", "cool-token"),
                Arguments.of(unfinishedTask, "right", null)
        );
    }

    @Test
    void testGenerateTokenThrowsMultipartTokenCreationException() throws Exception {
        // Create instance of the service with mocked dependencies
        MultipartUploadServiceImpl mpuService = new MultipartUploadServiceImpl(
                authorizationService, securityService, accessService, binaryService,
                projectsService, repositoryService, taskService, configsService);

        // Set up mocks to trigger the exception
        when(authorizationService.canDeploy(any())).thenReturn(true);
        when(repositoryService.repoExists(any())).thenReturn(true);
        when(binaryService.initiateMultipartUpload(anyString(), anyInt())).thenReturn(
                new MultipartUploadInitiateResponse("id", "path"));
        doThrow(new RuntimeException("Test Exception")).when(securityService).callAsSystem(any());

        // Verify that the exception is thrown
        assertThrows(MultipartTokenCreationException.class, () -> {
            mpuService.generateToken("repoKey", "path", 1);
        });
    }

    @Test
    void testMultipartStatusResultThrowsMultipartTokenCreationException() throws Exception {
        // Create instance of the service with mocked dependencies


        MultipartUploadServiceImpl mpuService = new MultipartUploadServiceImpl(
                authorizationService, securityService, accessService, binaryService,
                projectsService, repositoryService, taskService, configsService);

        doThrow(new RuntimeException("Test Exception")).when(securityService).callAsSystem(any());

        // Verify that the exception is thrown
        assertThrows(MultipartTokenCreationException.class, () -> {
            mpuService.generateMultipartUploadChecksumDeployToken("repoKey", "path", "sha1", "sha2");
        });
    }

    @Test
    void testGetRepoPathWithLocalRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        when(repositoryService.repoConfigByKey(localRepoConfig.getKey())).thenReturn(localRepoConfig);

        RepoPath repoPath = mpuService.getRepoPath(localRepoConfig.getKey(), "path");

        assertEquals(localRepoConfig.getKey(), repoPath.getRepoKey());
        assertEquals("path", repoPath.getPath());
    }

    @ParameterizedTest
    @MethodSource("provideRepoConfigAndExpectedRepoKey")
    void testGetRepoPathWithVirtualRepo(String defaultDeploymentRepoRef, String expectedRepoKey) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(defaultDeploymentRepoRef);
        when(repositoryService.repoConfigByKey(virtualRepoConfig.getKey())).thenReturn(virtualRepoConfig);

        RepoPath repoPath = mpuService.getRepoPath(virtualRepoConfig.getKey(), "path");

        assertEquals(expectedRepoKey, repoPath.getRepoKey());
        assertEquals("path", repoPath.getPath());
    }

    @Test
    void testStatusFinishedExpectedArtifactSize() {
        long expectedSize = 100;
        setAuthentication();
        mockMultipartUploadStatus(expectedSize);
        CreatedTokenInfo createdTokenInfo = mock(CreatedTokenInfo.class);
        when(accessService.createToken(any(UserTokenSpec.class), eq(true), eq(true))).thenReturn(
                createdTokenInfo);
        when(createdTokenInfo.getTokenValue()).thenReturn("cool-token");

        try {
            initRequestThreadLocal();

            mpuService.status();

            assertEquals(expectedSize, RequestThreadLocal.getDownloadPerfCounters().getArtifactSizeBytes());
        } finally {
            RequestThreadLocal.unbind();
        }
    }

    private void mockMultipartUploadStatus(long expectedSize) {
        when(binaryService.isMultipartUploadSupported()).thenReturn(true);
        MultipartUploadTask task = mock(MultipartUploadTask.class);
        when(task.getStatus()).thenReturn(Finished);
        when(task.getData()).thenReturn(MultipartUploadData.builder().size(expectedSize).sha1("sha1").build());
        when(binaryService.getMultipartUploadStatus(any())).thenReturn(Pair.of(task, "right"));
    }

    private void initRequestThreadLocal() {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);
        RequestWrapper servletRequestWrapper = mock(RequestWrapper.class);
        when(servletRequestWrapper.getRequest()).thenReturn(httpRequest);
        RequestThreadLocal.bind(servletRequestWrapper);
    }

    private static Stream<Arguments> provideRepoConfigAndExpectedRepoKey() {
        return Stream.of(
                Arguments.of("default-deployment-repo", "default-deployment-repo"),
                Arguments.of("", "virtual-repo"),
                Arguments.of(null, "virtual-repo")
        );
    }
}
