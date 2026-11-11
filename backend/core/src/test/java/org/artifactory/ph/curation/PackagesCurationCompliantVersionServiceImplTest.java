package org.artifactory.ph.curation;

import com.google.common.collect.ImmutableSetMultimap;
import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.model.curation.CurationPackageForResponse;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.service.PackageCurationService;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSecurityService;
import com.jfrog.ph.acl.service.PackageUploadService;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.addon.curation.packages.PackageVersionCurationController;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.guard.ConflictsGuardProvider;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.fs.FileAdditionalInfo;
import org.artifactory.model.fs.InternalFileInfo;
import org.artifactory.repo.RemoteRepoBase;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResolvedResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.gradleCurationComplaintVersionEnabled;
import static org.artifactory.common.ConstantValues.mavenCurationComplaintVersionEnabled;
import static org.artifactory.common.ConstantValues.sbtCurationComplaintVersionEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.FORBIDDEN_MARKER_SUFFIX;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.getDefaultForbiddenMessage;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.getForbiddenMarkerPath;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.CurationLockKind;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.getCuratedLockKey;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackagesCurationCompliantVersionServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private PackageCurationService packageCurationService;
    @Mock
    private PackageArtifactService packageArtifactService;
    @Mock
    private PackageUploadService packageUploadService;
    @Mock
    private PackageRepoService packageRepoService;
    @Mock
    private PackageSecurityService packageSecurityService;
    @Mock
    private ConflictsGuardProvider<String> conflictsGuardProvider;
    @Mock
    private ConflictsGuard<Object> conflictsGuard;
    @Mock
    private RemoteRepoBase<?, RemoteRepoConfig> remoteRepo;
    @Mock
    private RepoResource repoResource;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private RemoteRepoConfig repoConfig;
    @Mock
    private ResourceStreamHandle resourceStreamHandle;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private InternalFileInfo fileInfo;
    @Mock
    private CurationResponse curationResponse;

    private PackagesCurationCompliantVersionServiceImpl service;
    ObjectWrapper metadataWrapper;

    @BeforeEach
    void setUp() throws PackageException {
        when(repositoryService.remoteRepositoryByKey(anyString())).thenReturn(remoteRepo);
        when(remoteRepo.getRepositoryService()).thenReturn(repositoryService);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        service = new PackagesCurationCompliantVersionServiceImpl(
                packageCurationService,
                packageArtifactService,
                packageUploadService,
                packageRepoService,
                packageSecurityService,
                conflictsGuardProvider
        );
        lenient().doThrow(new PackageNotFoundException("forbidden marker not found"))
                .when(packageArtifactService).getArtifact(anyString(),
                        argThat(path -> path != null && path.endsWith(FORBIDDEN_MARKER_SUFFIX)));
        Object metadata = new Object();
        metadataWrapper = new ObjectWrapper(metadata);
    }

    @Test
    void testCanCallFilterBlockedVersionsWithMavenRepoShouldReturnTrue() throws Exception {
        // Given
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        // When
        boolean result = spyService.canCallFilterBlockedVersions(repoResource, remoteRepo);

        // Then
        assertTrue(result);
        verify(packageCurationService).canCallFilterBlockedVersions("test-repo");
    }

    @Test
    void testCanCallFilterBlockedVersionsWithGradleRepoShouldReturnTrue() throws Exception {
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Gradle);
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        boolean result = spyService.canCallFilterBlockedVersions(repoResource, remoteRepo);

        assertTrue(result);
        verify(packageCurationService).canCallFilterBlockedVersions("test-repo");
    }

    @Test
    void testCanCallFilterBlockedVersionsWithSbtRepoShouldReturnTrue() throws Exception {
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.SBT);
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        boolean result = spyService.canCallFilterBlockedVersions(repoResource, remoteRepo);

        assertTrue(result);
        verify(packageCurationService).canCallFilterBlockedVersions("test-repo");
    }

    @Test
    void canCallFilterBlockedVersions_realMavenController_mavenFlagOn_returnsTrue() {
        try {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertTrue(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realMavenController_mavenFlagOff_returnsFalse() {
        try {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertFalse(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realGradleController_gradleFlagOff_returnsFalse() {
        try {
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Gradle);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertFalse(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realGradleController_gradleFlagOn_returnsTrue() {
        try {
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Gradle);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertTrue(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realSbtController_sbtFlagOff_returnsFalse() {
        try {
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.SBT);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertFalse(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realSbtController_sbtFlagOn_returnsTrue() {
        try {
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.SBT);
            when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertTrue(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void canCallFilterBlockedVersions_realMavenController_nonMetadataPath_returnsFalse() {
        try {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
            when(repoResource.getPath()).thenReturn("/org/foo/bar/1.0/bar-1.0.jar");
            when(repoResource.getRepoKey()).thenReturn("test-repo");
            when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);

            assertFalse(service.canCallFilterBlockedVersions(repoResource, remoteRepo));
        } finally {
            setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        }
    }

    @Test
    void testCanCallFilterBlockedVersionsWithUnsupportedRepoShouldReturnFalse() {
        // Given
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Npm);

        // When
        boolean result = service.canCallFilterBlockedVersions(repoResource, remoteRepo);

        // Then
        assertFalse(result);
    }

    @Test
    void testCanCallFilterBlockedVersionsWithNotStoredArtifactLocallyShouldReturnFalse() {
        // Given
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(true);
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        PackageVersionCurationController<?> controller = mock(PackageVersionCurationController.class);
        when(controller.shouldCheckForCuration(anyString())).thenReturn(true);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        // When
        boolean result = spyService.canCallFilterBlockedVersions(repoResource, remoteRepo);

        // Then
        assertFalse(result);
    }

    @Test
    void testCanCallFilterBlockedVersionsWhenCurationServiceReturnsFalseShouldReturnFalse() {
        // Given
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageCurationService.canCallFilterBlockedVersions("test-repo")).thenReturn(false);
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        PackageVersionCurationController<?> controller = mock(PackageVersionCurationController.class);
        when(controller.shouldCheckForCuration(anyString())).thenReturn(true);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        // When
        boolean result = spyService.canCallFilterBlockedVersions(repoResource, remoteRepo);

        // Then
        assertFalse(result);
    }

    @Test
    void testGetCuratedMetadataResourceWithNotFoundResourceShouldReturnNull() throws PackageBlockedByCurationException {
        // Given
        when(repoResource.isFound()).thenReturn(false);

        // When
        RepoResource result = service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
    }

    @Test
    void testGetCuratedMetadataResourceWithUnsupportedRepoTypeShouldReturnNull()
            throws PackageBlockedByCurationException {
        // Given
        when(repoResource.isFound()).thenReturn(true);
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Npm);

        // When
        RepoResource result = service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
    }

    @Test
    void testGetCuratedMetadataResourceWithBlankRemoteUrlShouldReturnNull() throws PackageException {
        // Given
        when(repoResource.isFound()).thenReturn(true);
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("");

        // When
        RepoResource result = service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
    }

    @Test
    void testGetCuratedMetadataResourceWithCachedResourceShouldReturnCachedResource() throws PackageException {
        // Given
        when(repoResource.isFound()).thenReturn(true);
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(repoResource.getPath()).thenReturn("/maven-metadata.xml");
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("http://example.com");
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");

        RepoResource cachedResource = mock(RepoResource.class);
        when(cachedResource.isFound()).thenReturn(true);
        when(cachedResource.isExpired()).thenReturn(false);
        when(packageArtifactService.getResource(any(PackageDownloadContext.class))).thenReturn(cachedResource);

        // When
        RepoResource result = service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertEquals(cachedResource, result);
    }

    @Test
    void testGetCuratedMetadataResourceWithExpiredCacheShouldCreateNewResource() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(List.of(new CurationPackageForResponse("test-package",
                List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(mock(FileResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        // When
        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNotNull(result);
        verify(packageCurationService).filterBlockedVersions(any(CurationRequest.class));
        verify(packageUploadService).uploadToVirtualOrRemoteCache(eq("test-repo"), anyString(),
                any(InputStream.class));
    }

    @Test
    void getCuratedMetadataResource_whenNoVersionsToBlock_cachesOriginMetadata() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(List.of());
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        FileResource originResource = mock(FileResource.class);
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(originResource).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        // When
        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertEquals(originResource, result);
        verify(controller, never()).getCuratedMetadataModel(any(CurationResponse.class), any());
        verify(packageUploadService).uploadToVirtualOrRemoteCache(eq("test-repo"),
                eq(".jfrog/curated/maven-metadata.xml"), any(InputStream.class));
    }

    @Test
    void getCuratedMetadataResource_whenNoVersionsToBlockAndPassThrough_doesNotCache() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(requestContext.getRequestHeaders()).thenReturn(Map.of(ArtifactoryHeaders.PASS_THROUGH_KEY, "true"));
        when(curationResponse.blockedPackages()).thenReturn(List.of());
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        // When
        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
        verify(packageUploadService, never()).uploadToVirtualOrRemoteCache(anyString(), anyString(),
                any(InputStream.class));
    }

    @Test
    void testGetCuratedMetadataResourceWithLockTimeoutShouldThrowException() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        when(conflictsGuardProvider.getConflictsGuard("PACKAGE-METADATA-CURATION")).thenReturn(conflictsGuard);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        // When & Then
        assertNull(service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));
    }

    @Test
    void testGetCuratedMetadataResourceWithCurationExceptionShouldReturnNull() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        setupLockSuccess();
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class)))
                .thenReturn(null);
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        // When
        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
        verify(conflictsGuard).unlock(anyString());
    }

    @Test
    void testGetCuratedMetadataResourceWithUploadExceptionShouldReturnNull() throws Exception {
        // Given
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        doThrow(new PackageException(404, "Upload failed", "Upload failed"))
                .when(packageUploadService)
                .uploadToVirtualOrRemoteCache(anyString(), anyString(), any(InputStream.class));

        // When
        RepoResource result = service.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNull(result);
    }


    @Test
    void testCreateGAVCController() {
        // When
        var controller = service.createGAVCController(PackageType.MAVEN);

        // Then
        assertNotNull(controller);
    }

    @Test
    void getPackageVersionCurationController_mavenGradleSbt_returnsController() {
        assertNotNull(service.getPackageVersionCurationController(RepoType.Maven));
        assertNotNull(service.getPackageVersionCurationController(RepoType.Gradle));
        assertNotNull(service.getPackageVersionCurationController(RepoType.SBT));
    }

    @Test
    void mavenMetadataRepoTypeToCurationPackageType_mapsMavenLayoutFamily() {
        assertEquals(PackageType.MAVEN,
                PackagesCurationCompliantVersionServiceImpl.mavenMetadataRepoTypeToCurationPackageType(
                        RepoType.Maven));
        assertEquals(PackageType.GRADLE,
                PackagesCurationCompliantVersionServiceImpl.mavenMetadataRepoTypeToCurationPackageType(
                        RepoType.Gradle));
        assertEquals(PackageType.SBT,
                PackagesCurationCompliantVersionServiceImpl.mavenMetadataRepoTypeToCurationPackageType(RepoType.SBT));
    }

    @Test
    void mavenMetadataRepoTypeToCurationPackageType_unknownRepoType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PackagesCurationCompliantVersionServiceImpl.mavenMetadataRepoTypeToCurationPackageType(
                        RepoType.Npm));
    }

    @Test
    void getPackageVersionCurationController_otherMavenLayoutRepos_returnsNull() {
        assertNull(service.getPackageVersionCurationController(RepoType.Ivy));
        assertNull(service.getPackageVersionCurationController(RepoType.P2));
    }

    @Test
    void getPackageVersionCurationController_nonMavenGradleNonGems_returnsNull() {
        assertNull(service.getPackageVersionCurationController(RepoType.Npm));
    }

    @Test
    void testCreateGemsController() {
        // When
        var controller = service.createGemsController();

        // Then
        assertNotNull(controller);
    }

    @Test
    void testGetCuratedMetadataResourceForGemsFiltersBlockedInfoEntries()
            throws Exception {
        // Given
        setupBasicGemsMocks();
        setupExpiredCache();
        setupLockSuccess();
        setupGemsVersionApiFetch();
        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("""
                ---
                1.0.0 |checksum:aaa
                1.1.0-java |checksum:bbb
                """.getBytes(StandardCharsets.UTF_8)));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(
                new CurationResponse(List.of(new CurationPackageForResponse("mygem", List.of("1.0.0"))))
        );
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(mock(RepoResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        // When
        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        // Then
        assertNotNull(result);
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(packageUploadService).uploadToVirtualOrRemoteCache(eq("test-repo"), anyString(),
                streamCaptor.capture());
        String curated = new String(streamCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(curated.contains("1.0.0 |checksum:aaa"));
        assertTrue(curated.contains("1.1.0-java |checksum:bbb"));
    }

    @Test
    void testCreateMetadataResourceSuccess() throws PackageNotFoundException {
        // Given
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        when(remoteRepo.getRepositoryService()).thenReturn(repositoryService);
        when(fileInfo.getRepoPath()).thenReturn(RepoPathFactory.create("test-repo-cache", "path/to/file"));
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
        FileAdditionalInfo additionalInfo = mock(FileAdditionalInfo.class);
        ChecksumsInfo checksumsInfo = mock(ChecksumsInfo.class);
        when(checksumsInfo.isEmpty()).thenReturn(true);
        when(additionalInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        when(additionalInfo.getCreatedBy()).thenReturn("admin");
        when(additionalInfo.getModifiedBy()).thenReturn("admin");
        when(additionalInfo.getLastUpdated()).thenReturn(123456L);
        when(fileInfo.getAdditionalInfo()).thenReturn(additionalInfo);

        // When
        RepoResource result = service.createFileResource(remoteRepo, "test-repo", "path/to/file");

        // Then
        assertNotNull(result);
        verify(packageRepoService).getRemoteCacheRepoKey("test-repo");
        verify(repositoryService).getFileInfo(any());
    }

    @Test
    void testCreateMetadataResourceThrowsExceptionShouldReturnNull() throws PackageNotFoundException {
        // Given
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        when(remoteRepo.getRepositoryService()).thenReturn(repositoryService);
        when(repositoryService.getFileInfo(any())).thenThrow(new ItemNotFoundRuntimeException("File not found"));

        // When
        RepoResource result = service.createFileResource(remoteRepo, "test-repo", "path/to/file");

        // Then
        assertNull(result);
        verify(packageRepoService).getRemoteCacheRepoKey("test-repo");
        verify(repositoryService).getFileInfo(any());
    }

    @Test
    void testGetResourceFromCacheThrowsExceptionShouldReturnNull() throws PackageException {
        // Given
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        when(packageArtifactService.getResource(any(PackageDownloadContext.class)))
                .thenThrow(new PackageException(404, "Resource not found", "Resource not found"));

        // When
        RepoResource result = service.getResourceFromCache("test-repo", "path/to/file");

        // Then
        assertNull(result);
        verify(packageArtifactService).getResource(any(PackageDownloadContext.class));
    }

    private void setupBasicMocks() throws PackageException {
        when(repoResource.isFound()).thenReturn(true);
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(repoResource.getPath()).thenReturn("maven-metadata.xml");
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("http://example.com");
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
    }

    private void setupBasicGemsMocks() throws PackageException {
        when(repoResource.isFound()).thenReturn(true);
        when(remoteRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Gems);
        when(repoResource.getRepoKey()).thenReturn("test-repo");
        when(repoResource.getPath()).thenReturn("info/mygem");
        when(packageRepoService.getRemoteRepoBaseUrl("test-repo")).thenReturn("http://example.com/");
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
    }

    private void setupGemsVersionApiFetch() throws Exception {
        // Mock the RubyGems API response for fetching version info
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity httpEntity = mock(HttpEntity.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        String versionJson = """
                [
                  {"number": "1.0.0", "platform": "ruby", "created_at": "2024-01-01T00:00:00.000Z"},
                  {"number": "1.1.0", "platform": "java", "created_at": "2024-02-01T00:00:00.000Z"}
                ]
                """;
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(versionJson.getBytes(StandardCharsets.UTF_8)));
        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any(HttpRequestBase.class)))
                .thenReturn(httpResponse);
    }

    private void setupExpiredCache() throws PackageException {
        RepoResource expiredResource = mock(RepoResource.class);
        when(expiredResource.isFound()).thenReturn(false);
        when(packageArtifactService.getResource(any(PackageDownloadContext.class))).thenReturn(expiredResource);
    }

    private void setupLockSuccess() throws InterruptedException {
        when(conflictsGuardProvider.getConflictsGuard("PACKAGE-METADATA-CURATION")).thenReturn(conflictsGuard);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    private void setupSuccessfulCuration() throws Exception {
        setupLockSuccess();
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(curationResponse);
    }


    private PackageVersionCurationController<ObjectWrapper> createMockController() throws Exception {
        PackageVersionCurationController<ObjectWrapper> controller = mock(PackageVersionCurationController.class);

        when(controller.getCuratedFilePath(any(PackageDownloadContext.class)))
                .thenReturn(".jfrog/curated/maven-metadata.xml");
        when(controller.getMetadataModelOrigin(any(InputStream.class))).thenReturn(metadataWrapper);
        when(controller.createMetadataCurationRequest(any(), anyString(), anyString()))
                .thenReturn(new CurationRequest("test-repo", PackageType.MAVEN, List.of(), "user"));
        when(controller.shouldCheckForCuration(anyString())).thenReturn(true);
        when(controller.getCuratedMetadataModel(any(CurationResponse.class), any()))
                .thenReturn(metadataWrapper);
        when(controller.toInputStream(any())).thenReturn(new ByteArrayInputStream("curated".getBytes()));
        return controller;
    }

    private PackagesCurationCompliantVersionServiceImpl spyWithController(
            PackageVersionCurationController<ObjectWrapper> controller) {
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(controller).when(spyService).createGAVCController(any(PackageType.class));
        return spyService;
    }

    @Test
    void getCuratedMetadataResource_whenForbiddenMarkerExpired_continuesCuration() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(mock(FileResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageExpiredException("marker expired"))
                .doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);

        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        assertNotNull(result);
        verify(packageCurationService).filterBlockedVersions(any(CurationRequest.class));
        verify(controller, never()).throwPackageBlockedByCurationException(anyString());
    }

    @Test
    void getCuratedMetadataResource_whenForbiddenMarkerHasNoMessage_usesDefaultForbiddenMessage()
            throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        String expectedMessage = getDefaultForbiddenMessage(null, "maven-metadata.xml");
        PackageArtifact markerArtifact = PackageArtifact.builder()
                .repoKey("test-repo-cache")
                .path(markerPath)
                .properties(ImmutableSetMultimap.of())
                .build();
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doReturn(markerArtifact).when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        stubThrowPackageBlockedByCurationOnMock(controller);

        assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        verify(controller).throwPackageBlockedByCurationException(expectedMessage);
    }

    @Test
    void getCuratedMetadataResource_whenForbiddenMarkerExists_throwsPackageBlockedByCurationException()
            throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        PackageArtifact markerArtifact = PackageArtifact.builder()
                .repoKey("test-repo-cache")
                .path(markerPath)
                .properties(ImmutableSetMultimap.of("message", "cached block reason"))
                .build();
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doReturn(markerArtifact).when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        stubThrowPackageBlockedByCurationOnMock(controller);

        assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        verify(controller).throwPackageBlockedByCurationException("cached block reason");
        verify(packageCurationService, never()).filterBlockedVersions(any());
        verify(conflictsGuard, never()).tryToLock(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void getCuratedMetadataResource_whenControllerThrowsBlocked_savesMarkerAndRethrows() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupLockSuccess();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(curationResponse);
        doThrow(new PackageBlockedByCurationException("blocked"))
                .when(controller).getCuratedMetadataModel(any(CurationResponse.class), any());

        assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        verify(packageUploadService).upload(any());
    }

    @Test
    void getCuratedMetadataResource_whenCuratedSaved_removesForbiddenMarker() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(mock(FileResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        when(packageRepoService.existLocally("test-repo", markerPath)).thenReturn(true);

        spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        verify(packageRepoService).undeployArtifact("test-repo", markerPath);
    }

    @Test
    void getCuratedMetadataResource_whenForbiddenMarkerDoesNotExist_skipsUndeploy() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(mock(FileResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        when(packageRepoService.existLocally("test-repo", markerPath)).thenReturn(false);

        spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        verify(packageRepoService).existLocally("test-repo", markerPath);
        verify(packageRepoService, never()).undeployArtifact("test-repo", markerPath);
    }

    @Test
    void getCuratedMetadataResource_whenForbiddenMarkerReadFailsWithPackageException_continuesCuration()
            throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));
        doReturn(mock(FileResource.class)).when(spyService)
                .createFileResource(any(RemoteRepoBase.class), anyString(), anyString());

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageException(500, "marker read failed", "marker read failed"))
                .doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);

        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        assertNotNull(result);
        verify(packageCurationService).filterBlockedVersions(any(CurationRequest.class));
        verify(controller, never()).throwPackageBlockedByCurationException(anyString());
    }

    @Test
    void getCuratedMetadataResource_whenControllerThrowsBlockedWithCustomMessage_savesMarkerAttribute()
            throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupLockSuccess();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(curationResponse);
        doThrow(new PackageBlockedByCurationException("custom block reason"))
                .when(controller).getCuratedMetadataModel(any(CurationResponse.class), any());

        assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        verify(packageUploadService).upload(any());
        verify(packageRepoService).addAttribute("test-repo", markerPath, "message", Set.of("custom block reason"));
    }

    @Test
    void getCuratedMetadataResource_whenMarkerSaveFails_stillThrowsBlockedException() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupLockSuccess();
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spy(service);
        doReturn(controller).when(spyService).getPackageVersionCurationController(any(RepoType.class));

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        doThrow(new PackageNotFoundException("marker not found"))
                .when(packageArtifactService).getArtifact("test-repo-cache", markerPath);
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(curationResponse);
        doThrow(new PackageBlockedByCurationException("blocked by policy"))
                .when(controller).getCuratedMetadataModel(any(CurationResponse.class), any());
        doThrow(new PackageException(500, "storage throttled", "storage throttled"))
                .when(packageUploadService).upload(any());

        PackageBlockedByCurationException thrown = assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        assertEquals("blocked by policy", thrown.getPublicReason());
        verify(packageRepoService, never()).addAttribute(anyString(), anyString(), anyString(), any());
    }

    @Test
    void getCuratedMetadataResource_whenPassThrough_skipsCacheAndUsesPassThroughLock() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupSuccessfulCuration();
        when(requestContext.getRequestHeaders()).thenReturn(Map.of(ArtifactoryHeaders.PASS_THROUGH_KEY, "true"));
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0", "2.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spyWithController(controller);

        RepoResource result = spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource);

        assertNotNull(result);
        assertTrue(result instanceof ResolvedResource);
        verify(packageArtifactService, never()).getResource(any(PackageDownloadContext.class));
        verify(packageUploadService, never()).uploadToVirtualOrRemoteCache(anyString(), anyString(), any());
        ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(conflictsGuard).tryToLock(lockKeyCaptor.capture(), anyLong(), any(TimeUnit.class));
        assertEquals(getCuratedLockKey("test-repo", ".jfrog/curated/maven-metadata.xml", CurationLockKind.PASS_THROUGH),
                lockKeyCaptor.getValue());
        ArgumentCaptor<CurationRequest> curationRequestCaptor = ArgumentCaptor.forClass(CurationRequest.class);
        verify(packageCurationService).filterBlockedVersions(curationRequestCaptor.capture());
        assertTrue(curationRequestCaptor.getValue().passThrough());
    }

    @Test
    void getCuratedMetadataResource_whenPassThroughAndBlocked_doesNotSaveForbiddenMarker() throws Exception {
        setupBasicMocks();
        setupExpiredCache();
        setupLockSuccess();
        when(requestContext.getRequestHeaders()).thenReturn(Map.of(ArtifactoryHeaders.PASS_THROUGH_KEY, "true"));
        when(curationResponse.blockedPackages()).thenReturn(
                List.of(new CurationPackageForResponse("test-package", List.of("1.0.0"))));
        PackageVersionCurationController<ObjectWrapper> controller = createMockController();
        PackagesCurationCompliantVersionServiceImpl spyService = spyWithController(controller);

        String curatePath = ".jfrog/curated/maven-metadata.xml";
        String markerPath = getForbiddenMarkerPath(curatePath);
        when(packageRepoService.getRemoteCacheRepoKey("test-repo")).thenReturn("test-repo-cache");
        when(remoteRepo.getResourceStreamHandle(requestContext, repoResource)).thenReturn(resourceStreamHandle);
        when(resourceStreamHandle.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        when(packageCurationService.filterBlockedVersions(any(CurationRequest.class))).thenReturn(curationResponse);
        doThrow(new PackageBlockedByCurationException("blocked by policy"))
                .when(controller).getCuratedMetadataModel(any(CurationResponse.class), any());

        assertThrows(PackageBlockedByCurationException.class,
                () -> spyService.getCuratedMetadataResource(requestContext, remoteRepo, repoResource));

        verify(packageUploadService, never()).upload(any());
        verify(packageRepoService, never()).addAttribute(eq("test-repo"), eq(markerPath), anyString(), any());
    }

    private void stubThrowPackageBlockedByCurationOnMock(PackageVersionCurationController<?> mockController)
            throws PackageBlockedByCurationException {
        doAnswer(invocation -> {
            throw new PackageBlockedByCurationException(invocation.getArgument(0));
        }).when(mockController).throwPackageBlockedByCurationException(anyString());
    }

    private static class ObjectWrapper extends PackagesCurationMetadataWrapper {
        public ObjectWrapper(Object metadata) {
            super(metadata);
        }
    }
}

