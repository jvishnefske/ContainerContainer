package org.artifactory.engine;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.upload.UploadCtx;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.InternalSystemFolderUploadRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.RepositoryServiceImpl;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.jfrog.storage.binstore.ifc.BinaryServiceInputStream;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.artifactory.api.request.ArtifactoryHeaders.CHECKSUM_SHA1;
import static org.artifactory.common.ConstantValues.deployFallbackOnUpload;
import static org.artifactory.common.ConstantValues.deployFallbackOnUploadThresholdMs;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author dudim
 */
public class UploadServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    private PropertiesService propertiesServiceMock;
    @Mock
    private AuthorizationService authorizationServiceMock;
    @Mock
    private SecurityService securityServiceMock;
    @Mock
    private InternalRepositoryService repoServiceMock;
    @Mock
    private UnifiedEventService unifiedEventServiceMock;

    private AutoCloseable mocks;

    private static final String SAME_CHECKSUM = "bf8b4530d8d246dd74ac53a13471bba17941dff7";
    private static final String DIFF_CHECKSUM = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SAME_REPO = "gen-local";
    private static final String DIFF_REPO = "mvn-local";
    private static final String USER = "user1";
    private static final String DIFF_USER = "user2";


    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        homeStub.setProperty(deployFallbackOnUpload, SAME_REPO);
        homeStub.setProperty(deployFallbackOnUploadThresholdMs, "50");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @DataProvider
    public static Object[][] deployFallbackDataProvider() {
        return new Object[][] {
                {USER, SAME_CHECKSUM, true, SAME_REPO, -500, true}, // same user, checksum & repo, checksum in header
                {USER, SAME_CHECKSUM, false, SAME_REPO, -500, true}, // same user, checksum & repo, no checksum header
                {DIFF_USER, SAME_CHECKSUM, false, SAME_REPO, -500, false}, // wrong user
                {USER, DIFF_CHECKSUM, true, SAME_REPO, -500, false}, // wrong checksum, checksum in header
                {USER, DIFF_CHECKSUM, false, SAME_REPO, -500, false}, // wrong checksum, no checksum header
                {USER, SAME_CHECKSUM, false, DIFF_REPO, -500, false}, // wrong repo
                {USER, SAME_CHECKSUM, false, SAME_REPO, 500, false}, // time passed
                {DIFF_USER, DIFF_CHECKSUM, false, DIFF_REPO, 500, false}, // everything is wrong, no checksum header
                {DIFF_USER, DIFF_CHECKSUM, true, DIFF_REPO, 500, false}, // everything is wrong, checksum in header
        };
    }

    /**
     * New request is expected to return 201 if:
     * - uploaded by `user1`
     * - request has checksum of bf8b4530d8d246dd74ac53a13471bba17941dff7, either by header or by content
     * - uploaded to a supported repo
     * - uploaded at current time - delta. (delta below zero is a safe gaurd)
     */
    @Test(dataProvider = "deployFallbackDataProvider")
    public void testDeployFallbackSameChecksum(String username, String oldChecksum, boolean checksumInHeader,
            String repoKey, int deltaTime, boolean skipped) throws IOException {
        UploadServiceImpl uploadServiceSpy = new UploadServiceImpl();
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setSecurityService(securityServiceMock);
        when(securityServiceMock.currentUsername()).thenReturn(username);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        if (checksumInHeader) {
            when(request.getHeader(CHECKSUM_SHA1)).thenReturn(SAME_CHECKSUM);
        }
        when(request.getRepoKey()).thenReturn(repoKey);
        when(request.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        ArtifactoryResponse response = new InternalArtifactoryResponse();
        response.setStatus(HttpStatus.SC_FORBIDDEN);
        RepoPath repoPath = RepoPathFactory.create(repoKey, "file");
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn(repoKey);
        when(repoServiceMock.localOrFederatedRepositoryByKey(repoKey)).thenReturn(localRepo);
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getCreatedBy()).thenReturn(USER);
        when(fileInfo.getCreated()).thenReturn(System.currentTimeMillis() - deltaTime);
        when(fileInfo.getSha1()).thenReturn(oldChecksum);
        when(repoServiceMock.getFileInfo(repoPath)).thenReturn(fileInfo);

        uploadServiceSpy.handleDeployFallbackIfEnabled(request, response, repoPath, HttpStatus.SC_FORBIDDEN);
        assertEquals(response.getStatus(), skipped ? HttpStatus.SC_CREATED : HttpStatus.SC_FORBIDDEN);
    }

    @Test
    public void testDeployFallbackItemNotFound() {
        UploadServiceImpl uploadServiceSpy = new UploadServiceImpl();
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setSecurityService(securityServiceMock);
        when(securityServiceMock.currentUsername()).thenReturn(USER);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoPath repoPath = RepoPathFactory.create(SAME_REPO, "file");
        ArtifactoryResponse response = new InternalArtifactoryResponse();
        response.setStatus(HttpStatus.SC_FORBIDDEN);
        when(repoServiceMock.getFileInfo(repoPath)).thenThrow(new ItemNotFoundRuntimeException(repoPath));

        boolean fallbackDeployed = uploadServiceSpy.handleDeployFallbackIfEnabled(request, response, repoPath,
                HttpStatus.SC_FORBIDDEN);
        assertFalse(fallbackDeployed);
    }

    @Test
    public void testDeployFallbackNotForbidden() {
        UploadServiceImpl uploadServiceSpy = new UploadServiceImpl();
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setSecurityService(securityServiceMock);
        when(securityServiceMock.currentUsername()).thenReturn(USER);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoPath repoPath = RepoPathFactory.create(SAME_REPO, "file");
        ArtifactoryResponse response = new InternalArtifactoryResponse();
        response.setStatus(HttpStatus.SC_NOT_FOUND);
        boolean fallbackDeployed = uploadServiceSpy.handleDeployFallbackIfEnabled(request, response, repoPath,
                HttpStatus.SC_NOT_FOUND);
        assertFalse(fallbackDeployed);
    }

    @Test
    public void testAnnotateWithRequestPropertiesIfPermittedNoAnnotatePermission()
            throws RepoRejectException, IOException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setSecurityService(securityServiceMock);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);

        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("propKey", "propValue");
        when(artifactoryRequestMock.getProperties()).thenReturn(properties);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/folder");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath);
        when(authorizationServiceMock.canAnnotate(repoPath)).thenReturn(false);
        when(repoServiceMock.mkdirs(repoPath)).thenReturn(true);
        doNothing().when(uploadServiceSpy).sendSuccessfulResponse(any(), any(), any(), anyBoolean());
        uploadServiceSpy.createDirectory(artifactoryRequestMock, mock(ArtifactoryResponse.class));
        verify(propertiesServiceMock, times(0)).setProperties(eq(repoPath), any(), anyBoolean());
        verify(uploadServiceSpy, times(0)).validateRequestProperties(any());
    }

    @Test
    public void testAnnotateWithRequestPropertiesIfPermittedNoRequestProps() throws RepoRejectException, IOException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);

        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        when(artifactoryRequestMock.getProperties()).thenReturn(null);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/folder");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath);
        when(authorizationServiceMock.canAnnotate(repoPath)).thenReturn(true);
        when(repoServiceMock.mkdirs(repoPath)).thenReturn(true);
        doNothing().when(uploadServiceSpy).sendSuccessfulResponse(any(), any(), any(), anyBoolean());
        uploadServiceSpy.createDirectory(artifactoryRequestMock, mock(ArtifactoryResponse.class));
        verify(propertiesServiceMock, times(0)).setProperties(eq(repoPath), any(), anyBoolean());
        verify(uploadServiceSpy, times(1)).validateRequestProperties(any());
    }

    @Test
    public void testAnnotateWithRequestPropertiesIfPermittedEmptyRequestProps() throws RepoRejectException,
            IOException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);

        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        when(artifactoryRequestMock.getProperties()).thenReturn(new PropertiesImpl());
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/folder");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath);
        when(authorizationServiceMock.canAnnotate(repoPath)).thenReturn(true);
        when(repoServiceMock.mkdirs(repoPath)).thenReturn(true);
        doNothing().when(uploadServiceSpy).sendSuccessfulResponse(any(), any(), any(), anyBoolean());
        uploadServiceSpy.createDirectory(artifactoryRequestMock, mock(ArtifactoryResponse.class));
        verify(propertiesServiceMock, times(0)).setProperties(eq(repoPath), any(), anyBoolean());
        verify(uploadServiceSpy, times(1)).validateRequestProperties(any());
    }

    @Test
    public void testAnnotateWithRequestPropertiesIfPermittedUnvalidProps() throws RepoRejectException, IOException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);

        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("11propKey", "propValue");
        when(artifactoryRequestMock.getProperties()).thenReturn(properties);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/folder");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath);
        when(authorizationServiceMock.canAnnotate(repoPath)).thenReturn(true);
        RepoRejectException repoRejectException = null;

        try {
            uploadServiceSpy.createDirectory(artifactoryRequestMock, mock(ArtifactoryResponse.class));
        } catch (RepoRejectException e) {
            assertEquals(e.getErrorCode(), 400);
            repoRejectException = e;
        }
        verify(propertiesServiceMock, times(0)).setProperties(eq(repoPath), any(), anyBoolean());
        verify(uploadServiceSpy, times(1)).validateRequestProperties(any());
        verify(repoServiceMock, times(0)).mkdirs(repoPath);
        assertNotNull(repoRejectException);
    }

    @Test
    public void testAnnotateWithRequestPropertiesIfPermittedValidCase() throws RepoRejectException, IOException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setRepoService(repoServiceMock);
        uploadServiceSpy.setPropertiesService(propertiesServiceMock);

        ArtifactoryRequest artifactoryRequestMock = mock(ArtifactoryRequest.class);
        PropertiesImpl properties = new PropertiesImpl();
        properties.put("propKey", "propValue");
        when(artifactoryRequestMock.getProperties()).thenReturn(properties);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/folder");
        when(artifactoryRequestMock.getRepoPath()).thenReturn(repoPath);
        when(authorizationServiceMock.canAnnotate(repoPath)).thenReturn(true);
        when(repoServiceMock.mkdirs(repoPath)).thenReturn(true);
        doNothing().when(uploadServiceSpy).sendSuccessfulResponse(any(), any(), any(), anyBoolean());
        uploadServiceSpy.createDirectory(artifactoryRequestMock, mock(ArtifactoryResponse.class));
        verify(propertiesServiceMock, times(1)).setProperties(repoPath, properties, false);
        verify(uploadServiceSpy, times(1)).validateRequestProperties(any());
    }

    @Test
    public void testUploadWithChecksum() throws IOException, RepoRejectException {
        UploadServiceImpl uploadServiceSpy = spy(new UploadServiceImpl());
        InternalRepositoryService repositoryService = mock(RepositoryServiceImpl.class);
        uploadServiceSpy.setRepoService(repositoryService);
        uploadServiceSpy.setAuthService(authorizationServiceMock);
        uploadServiceSpy.setSecurityService(securityServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        RepoResource res = mock(RepoResource.class);
        when(res.getRepoPath()).thenReturn(RepoPathFactory.create("test"));
        when(res.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        when(res.isFound()).thenReturn(Boolean.TRUE);
        LocalRepo<?, ?> repo = mock(LocalRepo.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        uploadServiceSpy.setAddonsManager(addonsManager);
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        uploadServiceSpy.setBinaryService(binaryService);
        BinaryElement binaryElement = mock(BinaryElement.class);
        when(binaryService.createBinaryElement(any(), any(), any(), anyLong(), any())).thenReturn(binaryElement);

        MockInputStream inputStream = mock(MockInputStream.class);
        when(inputStream.getBinaryInfo()).thenReturn(binaryElement);
        when(binaryService.getBinaryForChecksumDeploy(any(), anyBoolean())).thenReturn(inputStream);

        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        try {
            HaAddon haAddon = mock(HaAddon.class);
            ReflectionTestUtils.setField(repositoryService, "addonsManager", addonsManager);
            when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
            when(haAddon.isHaEnabled()).thenReturn(false);
            when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
            when(repositoryService.saveResourceInTransaction(any(), any())).thenReturn(res);
            doCallRealMethod().when(repositoryService).saveResource(any(), any());

            FileInfo fileInfo = mock(FileInfo.class);
            when(fileInfo.getChecksumsInfo()).thenReturn(mock(ChecksumsInfo.class));
            when(repoServiceMock.getFileInfo(any())).thenReturn(fileInfo);

            doNothing().when(uploadServiceSpy).sendSuccessfulResponse(any(ArtifactoryRequest.class),
                    any(ArtifactoryResponse.class), any(RepoPath.class), any(Boolean.class));

            uploadServiceSpy.doChecksumDeploy(request, response, repo, res, ChecksumType.sha1,
                    "53e2d2a89b113769cd7a65a7be214da663d20d76");

            verify(response, times(1)).checksumDeployed();
            //should not reach addBinary flow
            verify(binaryService, times(0)).addBinary(any(), any());
            verify(binaryService, times(1)).getBinaryForChecksumDeploy(any(), anyBoolean());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test(dataProvider = "provideUserAgents")
    public void testIncludeSha256Value(String headerValue, boolean expected) {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        when(requestMock.getHeaderValues(HttpHeaders.USER_AGENT))
                .thenReturn(Collections.enumeration(List.of(headerValue)));
        assertEquals(uploadService.includeSha256Value(requestMock), expected);
    }

    @Test
    public void testUploadForbiddenForExternalRequest() throws IOException {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("repo/.jfrog/path");
        var httpArtifactoryRequest = new HttpArtifactoryRequest(request);
        uploadService.setAddonsManager(mock(AddonsManager.class));
        var artifactoryResponse = new InternalArtifactoryResponse();
        uploadService.processUpload(httpArtifactoryRequest, artifactoryResponse);
        assertTrue(artifactoryResponse.getStatusHolder().isError());
        assertEquals(artifactoryResponse.getStatus(), HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testUploadAllowedForInternalRequest() throws IOException, RepoRejectException {
        UploadServiceImpl uploadService = mock(UploadServiceImpl.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("repo/.jfrog/path");
        RepoPath repoPath = RepoPathFactory.create("repo/.jfrog/path");
        var internalRequest = new InternalArtifactoryRequest(repoPath, false);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(mock(PluginsAddon.class));
        uploadService.setAddonsManager(addonsManager);
        var artifactoryResponse = new InternalArtifactoryResponse();
        LocalRepo localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("repo");
        when(localRepo.getDescriptor()).thenReturn(mock(LocalRepoDescriptor.class));
        when(localRepo.getRepoPath(".jfrog/path")).thenReturn(repoPath);
        when(repoServiceMock.localOrFederatedRepositoryByKey("repo")).thenReturn(localRepo);
        RepoResource repoResource = mock(RepoResource.class);
        when(repoResource.isFound()).thenReturn(true);
        when(repoServiceMock.saveResource(eq(localRepo), any())).thenReturn(repoResource);
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getChecksumsInfo()).thenReturn(new ChecksumsInfo());
        when(repoServiceMock.getFileInfo(repoPath)).thenReturn(fileInfo);
        uploadService.setRepoService(repoServiceMock);
        uploadService.setChecksumDeployPermissionValidator(mock(ChecksumDeployPermissionValidator.class));
        uploadService.setTrafficService(mock(TrafficService.class));
        uploadService.setAuthService(authorizationServiceMock);
        uploadService.setSecurityService(securityServiceMock);

        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        when(context.beanForType(AuthorizationService.class)).thenReturn(authorizationServiceMock);
        when(context.beanForType(SecurityService.class)).thenReturn(securityServiceMock);
        try {
            ArtifactoryContextThreadBinder.bind(context);
            uploadService.processUpload(internalRequest, artifactoryResponse);
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }


        assertFalse(artifactoryResponse.getStatusHolder().isError());
        assertEquals(artifactoryResponse.getStatus(), HttpStatus.SC_OK);
    }

    /**
     * Verifies that {@link InternalSystemFolderUploadRequest} flows through the same
     * {@code validateRequestAndUpload} pipeline as a regular deploy — in particular that
     * {@code assertValidDeployPathAndPermissions} (deploy-permission enforcement) IS invoked.
     * The internal request no longer bypasses authorization; its only special handling is that
     * {@code getTargetRepository} resolves the key via {@code localFederatedOrCachedRepositoryByKey}
     * so remote keys route to their {@code -cache} half.
     *
     * <p>The target repository resolves to a valid local repo, then the flow is made to exit at
     * {@code assertValidDeployPathAndPermissions} by having it throw, which proves the permission
     * check runs before any storage write.
     */
    @Test
    public void testInternalSystemFolderRequest_enforcesAssertValidDeployPathAndPermissions()
            throws IOException, RepoRejectException {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        when(addonsManagerMock.addonByType(PluginsAddon.class)).thenReturn(mock(PluginsAddon.class));
        uploadService.setAddonsManager(addonsManagerMock);
        uploadService.setRepoService(repoServiceMock);
        uploadService.setAuthService(authorizationServiceMock);
        uploadService.setSecurityService(securityServiceMock);
        uploadService.setUnifiedEventService(unifiedEventServiceMock);
        uploadService.setChecksumDeployPermissionValidator(mock(ChecksumDeployPermissionValidator.class));
        uploadService.setTrafficService(mock(TrafficService.class));

        DispatcherResponse dispatcherResponse = mock(DispatcherResponse.class);
        when(dispatcherResponse.data()).thenReturn(null);
        when(unifiedEventServiceMock.sendArtifactEvent(any(), any(), any())).thenReturn(dispatcherResponse);
        LocalRepo localRepoMock = mock(LocalRepo.class);
        when(localRepoMock.getKey()).thenReturn("my-local");
        when(repoServiceMock.localFederatedOrCachedRepositoryByKey(any())).thenReturn(localRepoMock);
        // Throw from the permission check to prove it is reached (and to stop before the storage write).
        doThrow(new RepoRejectException("denied", HttpStatus.SC_FORBIDDEN))
                .when(repoServiceMock).assertValidDeployPathAndPermissions(any());

        RepoPath repoPath = RepoPathFactory.create("my-local", ".evidence/sha256:abc/sig.json");
        InternalSystemFolderUploadRequest request = new InternalSystemFolderUploadRequest(
                repoPath, new ByteArrayInputStream(new byte[] {1, 2, 3}), 3);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();

        uploadService.processUpload(request, response);

        verify(repoServiceMock, times(1)).assertValidDeployPathAndPermissions(any());
    }

    @DataProvider
    public Object[][] provideUserAgents() {
        return new Object[][] {
                {"", true},
                {"blabla", true},
                {"Xray/1.0.0", true},
                {"JFrog-Cli/1.0.0", true},
                {"ArtifactoryBuildClient/3.1.0", true},
                {"ArtifactoryBuildClient/2.27.0", true},
                {"ArtifactoryBuildClient/2.27.1", true},
                {"ArtifactoryBuildClient/2.28.0", true},
                {"ArtifactoryBuildClient/3.0.0", true},
                {"ArtifactoryBuildClient/2.27.x-SNAPSHOT", true},
                {"ArtifactoryBuildClient/2.27.X-SNAPSHOT", true},
                {"ArtifactoryBuildClient/2.26.x-SNAPSHOT", false},
                {"ArtifactoryBuildClient/2.25.X-SNAPSHOT", false},
                {"ArtifactoryBuildClient/2.24.x-SNAPSHOT", false},
                {"ArtifactoryBuildClient/2.1.0", false},
                {"ArtifactoryBuildClient/1.1.0", false},
                {"ArtifactoryBuildClient/2.24.0", false},
                {"ArtifactoryBuildClient/2.25.0", false},
                {"ArtifactoryBuildClient/2.25.1", false},
                {"ArtifactoryBuildClient/2.26.0", false},
        };
    }

    abstract static class MockInputStream extends BufferedInputStream implements BinaryServiceInputStream {
        public MockInputStream() {
            super(new ByteArrayInputStream(new byte[] {}));
        }
    }

    @Test
    public void testSendUnifiedEventSuccessWithData() {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        uploadService.setUnifiedEventService(unifiedEventServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        UploadCtx uploadCtx = new UploadCtx();
        DispatcherResponse dispatcherResponse = mock(DispatcherResponse.class);
        ResponseContextData data = mock(ResponseContextData.class);
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        when(data.modifiedRepoPath()).thenReturn(repoPath);
        when(data.headers()).thenReturn(Collections.singletonMap("foo", "bar"));
        when(dispatcherResponse.data()).thenReturn(data);
        when(unifiedEventServiceMock.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_UPLOAD, request, null))
                .thenReturn(dispatcherResponse);

        uploadService.sendUnifiedEvent(request, uploadCtx);

        assertEquals(repoPath, uploadCtx.getModifiedRepoPath());
        assertEquals(uploadCtx.getHeaders().get("foo"), "bar");
    }

    @Test
    public void testSendUnifiedEventSuccessWithNoData() {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        uploadService.setUnifiedEventService(unifiedEventServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        UploadCtx uploadCtx = new UploadCtx();
        DispatcherResponse dispatcherResponse = mock(DispatcherResponse.class);
        when(dispatcherResponse.data()).thenReturn(null);
        when(unifiedEventServiceMock.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_UPLOAD, request, null))
                .thenReturn(dispatcherResponse);

        uploadService.sendUnifiedEvent(request, uploadCtx);

        assertNull(uploadCtx.getModifiedRepoPath());
        assertNull(uploadCtx.getHeaders());
    }

    @Test
    public void testSendUnifiedEventThrowsConflict() {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        uploadService.setUnifiedEventService(unifiedEventServiceMock);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        UploadCtx uploadCtx = new UploadCtx();
        when(unifiedEventServiceMock.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_UPLOAD, request, null))
                .thenThrow(new UnifiedEventException(HttpStatus.SC_CONFLICT, false, "dont proceed"));

        assertThrows(UnifiedEventException.class, () -> uploadService.sendUnifiedEvent(request, uploadCtx));
    }
}
