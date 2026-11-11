package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageUploadContextImpl;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class PackageUploadServiceImplTest extends ArtifactoryHomeBoundTest {

    private PackageUploadServiceImpl packageUploadService;

    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private UploadService uploadService;
    @Mock
    private SecurityService securityService;
    @Mock
    private TrafficService trafficService;
    AutoCloseable closeable;

    @BeforeMethod
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepoMock = mock(LocalRepo.class);
        LocalRepoConfig configMock = mock(LocalRepoConfig.class);
        when(localRepoMock.getRepoConfig()).thenReturn(configMock);
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey(anyString())).thenReturn(localRepoMock);
        packageUploadService =
                spy(new PackageUploadServiceImpl(internalRepositoryService, uploadService, securityService,
                        trafficService));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test(dataProvider = "booleanProvider")
    public void testUpload(boolean constantValue, boolean isSystemUser) throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.packageHandlerInternalFilesChargeFix.getPropertyName(), String.valueOf(constantValue));
        when(securityService.isAuthenticatedAsSystem()).thenReturn(isSystemUser);
        InputStream is = mock(InputStream.class);
        packageUploadService.upload(
                new PackageUploadContextImpl("repo", "path").setContent(is).setSystemGenerated(false));
        verify(internalRepositoryService, times(1)).deploy(RepoPathFactory.create("repo", "path"), is);
        verify(internalRepositoryService, times(0)).saveFileInternal(RepoPathFactory.create("repo", ".jfrog/path"), is);
    }

    @Test
    public void testUploadThrow() {
        InputStream is = mock(InputStream.class);
        StatusHolder statusHolder = mock(StatusHolder.class);
        when(internalRepositoryService.deploy(any(), any())).thenReturn(statusHolder);
        when(statusHolder.isError()).thenReturn(true);
        when(statusHolder.getStatusCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
        when(statusHolder.getStatusMsg()).thenReturn("forbidden");
        assertThrows(PackageForbiddenException.class, () -> packageUploadService.upload(
                new PackageUploadContextImpl("repo", "path").setContent(is).setSystemGenerated(false)));
    }

    @Test(dataProvider = "booleanProvider")
    public void testUploadToJfrogFolder(boolean constantValue, boolean isSystemUser) throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.packageHandlerInternalFilesChargeFix.getPropertyName(), String.valueOf(constantValue));
        when(securityService.isAuthenticatedAsSystem()).thenReturn(isSystemUser);
        InputStream is = mock(InputStream.class);
        packageUploadService.upload(
                new PackageUploadContextImpl("repo", ".jfrog/path").setContent(is).setSystemGenerated(true));
        verify(internalRepositoryService, times(1)).saveFileInternal(
                RepoPathFactory.create("repo", ".jfrog/path"), is);
        verify(internalRepositoryService, times(0)).deploy(RepoPathFactory.create("repo", "path"),
                is);
    }

    @Test(dataProvider = "booleanProvider")
    public void testUploadInternalFile(boolean constantValue, boolean isSystemUser) throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(
                ConstantValues.packageHandlerInternalFilesChargeFix.getPropertyName(), String.valueOf(constantValue));
        when(securityService.isAuthenticatedAsSystem()).thenReturn(isSystemUser);
        InputStream is = mock(InputStream.class);
        packageUploadService.upload(
                new PackageUploadContextImpl("repo", ".metadata/path").setContent(is).setSystemGenerated(true));
        verify(internalRepositoryService,
                times(shouldSaveInternalToGeneralMetadataFolder(constantValue, isSystemUser) ? 1 : 0)).saveFileInternal(
                RepoPathFactory.create("repo", ".metadata/path"), is);
        verify(internalRepositoryService, times(0)).deploy(RepoPathFactory.create("repo", "path"),
                is);
    }

    boolean shouldSaveInternalToGeneralMetadataFolder(boolean constantValue, boolean isSystemUser) {
        return constantValue && isSystemUser;
    }

    @Test
    public void testUploadWithRemoteCache() throws PackageException {
        InputStream is = mock(InputStream.class);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = mock(LocalRepo.class);
        when(localRepo.isReal()).thenReturn(true);
        when(localRepo.isCache()).thenReturn(true);
        doAnswer(invocation -> localRepo).when(internalRepositoryService)
                .localFederatedOrCachedRepositoryByKey(anyString());
        doReturn(null).when(packageUploadService).uploadToVirtualOrRemoteCache("repo", "path", is, false);
        packageUploadService.upload(
                new PackageUploadContextImpl("repo", "path").setContent(is).setSystemGenerated(false));
        verify(packageUploadService, times(1)).uploadToVirtualOrRemoteCache(anyString(), anyString(), any(), eq(false));
        verify(internalRepositoryService, times(1)).localFederatedOrCachedRepositoryByKey("repo");
        verifyNoMoreInteractions(internalRepositoryService);
    }

    @Test
    public void testUploadWithResponse() throws PackageException, RepoRejectException, IOException {
        InputStream is = mock(InputStream.class);
        PackageUploadContextImpl uploadContext =
                new PackageUploadContextImpl("repo", "path").setContent(is).setContentLength(1);
        packageUploadService.uploadWithResponse(uploadContext);
        verify(uploadService, times(1)).upload(any(ArtifactoryRequest.class), any(ArtifactoryResponse.class));
    }

    @Test
    public void testUploadWithResponseWithSkipPackageUsageTrack()
            throws PackageException, RepoRejectException, IOException {
        InputStream is = mock(InputStream.class);
        PackageUploadContextImpl uploadContext =
                new PackageUploadContextImpl("repo", "path").setContent(is).setContentLength(1)
                        .setSkipPackageUsageTrack(true);
        ArgumentCaptor<ArtifactoryRequest> argumentCaptor = ArgumentCaptor.forClass(ArtifactoryRequest.class);
        packageUploadService.uploadWithResponse(uploadContext);
        verify(uploadService, times(1)).upload(argumentCaptor.capture(), any(ArtifactoryResponse.class));
        ArtifactoryRequest artifactoryRequest = argumentCaptor.getValue();
        assertTrue(artifactoryRequest.shouldSkipPackageUsageTrack());
    }

    @Test
    public void testUploadToVirtualCacheRepoNotFound() {
        try {
            InputStream is = mock(InputStream.class);
            packageUploadService.uploadToVirtualOrRemoteCache("repo", "path", is);
            Assert.fail("Should get PackageException:SC_NOT_FOUND");
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_NOT_FOUND);
        }
    }

    @Test
    public void testUploadToVirtualCache() throws RepoRejectException, IOException, PackageException {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.isReal()).thenReturn(false);
        when(virtualRepo.isCache()).thenReturn(false);
        doAnswer(invocation -> virtualRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(
                mock(RepoResource.class));
        InputStream is = mock(InputStream.class);
        BaseResource resource = packageUploadService.uploadToVirtualOrRemoteCache("repo", "path", is);
        Assert.assertNotNull(resource);
        ArgumentCaptor<VirtualRepo> vr = ArgumentCaptor.forClass(VirtualRepo.class);
        ArgumentCaptor<SaveResourceContext> rc = ArgumentCaptor.forClass(SaveResourceContext.class);
        verify(internalRepositoryService, times(1)).saveResource(
                vr.capture(), rc.capture());

        assertEquals(vr.getValue(), virtualRepo);
        assertEquals(rc.getValue().getRepoResource().getInfo().getRepoPath(), RepoPathFactory.create("repo", "path"));
        assertEquals(rc.getValue().getRepoResource().getInfo().getChecksumsInfo().size(),
                ChecksumType.BASE_CHECKSUM_TYPES.size());
        assertEquals(rc.getValue().getInputStream(), is);
        verify(trafficService, times(0)).handleTrafficEntry(any(TrafficEntry.class));
    }

    @Test
    public void testUploadToRemoteCacheWithTrafficLogging() throws RepoRejectException, IOException, PackageException {
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isCache()).thenReturn(true);
        doAnswer(invocation -> localRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        RepoResource savedResource = mock(RepoResource.class);
        when(savedResource.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        when(savedResource.getSize()).thenReturn(123L);
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(savedResource);
        InputStream is = mock(InputStream.class);
        BaseResource resource = packageUploadService.uploadToVirtualOrRemoteCache("repo", "path", is, true);
        Assert.assertEquals(resource, savedResource);
        ArgumentCaptor<TrafficEntry> trafficEntryCaptor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryCaptor.capture());
        TrafficEntry trafficEntry = trafficEntryCaptor.getValue();
        assertEquals(trafficEntry.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        assertEquals(trafficEntry.getActor(), TrafficEntry.TrafficActor.USER);
        assertEquals(trafficEntry.getRedirectTarget(), TrafficEntry.TrafficTarget.DIRECT);
        assertEquals(trafficEntry.getRepoPath(), RepoPathFactory.create("repo", "path").getId());
        assertEquals(trafficEntry.getSize(), 123L);
        assertEquals(trafficEntry.getRequestedSize(), 123L);
        assertEquals(trafficEntry.getTransferredSize(), 123L);
    }

    @Test
    public void testUploadToRemoteCacheWithoutTrafficLogging() throws RepoRejectException, IOException,
            PackageException {
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isCache()).thenReturn(true);
        doAnswer(invocation -> localRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(mock(RepoResource.class));
        InputStream is = mock(InputStream.class);
        packageUploadService.uploadToVirtualOrRemoteCache("repo", "path", is, false);
        verify(trafficService, times(0)).handleTrafficEntry(any(TrafficEntry.class));
    }

    @Test
    public void testUploadToVirtualCacheException() throws RepoRejectException, IOException {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.isReal()).thenReturn(false);
        when(virtualRepo.isCache()).thenReturn(false);
        doAnswer(invocation -> virtualRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        when(internalRepositoryService.saveResource(any(), any())).thenThrow(new RuntimeException());
        InputStream is = mock(InputStream.class);
        try {
            packageUploadService.uploadToVirtualOrRemoteCache("repo", "path", is);
            Assert.fail("Should get PackageException:SC_INTERNAL_SERVER_ERROR");
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    public void testUploadToVirtualCacheV2() throws RepoRejectException, IOException, PackageException {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.isReal()).thenReturn(false);
        when(virtualRepo.isCache()).thenReturn(false);
        doAnswer(invocation -> virtualRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(
                mock(RepoResource.class));
        InputStream is = mock(InputStream.class);
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        RepoResource repoResourceMock = mock(RepoResource.class);
        org.artifactory.resource.RepoResourceInfo repoResourceInfoMock =
                mock(org.artifactory.resource.RepoResourceInfo.class);
        when(repoResourceInfoMock.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        when(repoResourceInfoMock.getChecksumsInfo()).thenReturn(new org.artifactory.checksum.ChecksumsInfo());
        when(repoResourceMock.getInfo()).thenReturn(repoResourceInfoMock);
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(repoResourceMock);
        PackageArtifact artifact = packageUploadService.uploadToVirtualOrRemoteCacheV2(
                "repo", "path", is);
        Assert.assertNotNull(artifact);
        ArgumentCaptor<VirtualRepo> vr = ArgumentCaptor.forClass(VirtualRepo.class);
        ArgumentCaptor<SaveResourceContext> rc = ArgumentCaptor.forClass(SaveResourceContext.class);
        verify(internalRepositoryService, times(1)).saveResource(
                vr.capture(), rc.capture());
        assertEquals(artifact.getRepo(), "repo");
        assertEquals(artifact.getPath(), "path");
        assertEquals(artifact.getPackageType(), PackageType.NPM);
        assertEquals(vr.getValue(), virtualRepo);
        assertEquals(rc.getValue().getRepoResource().getInfo().getRepoPath(), RepoPathFactory.create("repo", "path"));
        assertEquals(rc.getValue().getRepoResource().getInfo().getChecksumsInfo().size(),
                     ChecksumType.BASE_CHECKSUM_TYPES.size());
        assertEquals(rc.getValue().getInputStream(), is);
        verify(trafficService, times(0)).handleTrafficEntry(any(TrafficEntry.class));
    }

    @Test
    public void testUploadToRemoteCacheV2WithTrafficLogging() throws RepoRejectException, IOException,
            PackageException {
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isCache()).thenReturn(true);
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(localRepo.getRepoConfig()).thenReturn(localRepoConfig);
        doAnswer(invocation -> localRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        RepoResource savedResource = mock(RepoResource.class);
        org.artifactory.resource.RepoResourceInfo repoResourceInfoMock =
                mock(org.artifactory.resource.RepoResourceInfo.class);
        when(repoResourceInfoMock.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        when(repoResourceInfoMock.getChecksumsInfo()).thenReturn(new org.artifactory.checksum.ChecksumsInfo());
        when(savedResource.getInfo()).thenReturn(repoResourceInfoMock);
        when(savedResource.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        when(savedResource.getSize()).thenReturn(123L);
        when(internalRepositoryService.saveResource(any(), any())).thenReturn(savedResource);
        InputStream is = mock(InputStream.class);
        PackageArtifact artifact = packageUploadService.uploadToVirtualOrRemoteCacheV2("repo", "path", is, true);
        Assert.assertNotNull(artifact);
        ArgumentCaptor<TrafficEntry> trafficEntryCaptor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService, times(1)).handleTrafficEntry(trafficEntryCaptor.capture());
        TrafficEntry trafficEntry = trafficEntryCaptor.getValue();
        assertEquals(trafficEntry.getAction(), TrafficEntry.TrafficAction.UPLOAD);
        assertEquals(trafficEntry.getRepoPath(), RepoPathFactory.create("repo", "path").getId());
        assertEquals(trafficEntry.getTransferredSize(), 123L);
    }

    @Test
    public void testUploadToVirtualCacheExceptionV2() throws RepoRejectException, IOException {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.isReal()).thenReturn(false);
        when(virtualRepo.isCache()).thenReturn(false);
        doAnswer(invocation -> virtualRepo).when(internalRepositoryService).storingRepositoryByKey(anyString());
        when(internalRepositoryService.saveResource(any(), any())).thenThrow(new RuntimeException());
        InputStream is = mock(InputStream.class);
        try {
            packageUploadService.uploadToVirtualOrRemoteCacheV2("repo", "path", is);
            Assert.fail("Should get PackageException:SC_INTERNAL_SERVER_ERROR");
        } catch (PackageException e) {
            assertEquals(e.getStatus(), SC_INTERNAL_SERVER_ERROR);
        }
    }

    @DataProvider
    public static Object[][] booleanProvider() {
        return new Object[][] {
                {true, true},
                {true, false},
                {false, true},
                {false, false}
        };
    }
}