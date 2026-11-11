package org.artifactory.repo.db;

import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.RestCoreAddon;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileAdditionalInfo;
import org.artifactory.model.fs.InternalFileInfo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.VfsItemProvider;
import org.artifactory.storage.fs.VfsItemProviderFactory;
import org.artifactory.storage.fs.VfsMutableFolderProvider;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.tree.VfsImmutableProvider;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.enumeration;
import static org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor.UNIQUE;
import static org.artifactory.repo.db.DbLocalRepo.BYPASS_UNSCANNED_BLOCKING_HEADER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DbLocalRepoTest extends ArtifactoryHomeBoundJUnitTest {
    private AutoCloseable mocks;
    private DbLocalRepo localRepoSpy;
    private LocalRepoDescriptor repoDescriptor;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private InternalRepositoryService internalRepoService;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private StorageInterceptors storageInterceptors;
    @Mock
    private FileService fileService;
    @Mock
    private StatsServiceImpl statsService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private VfsItemProviderFactory vfsItemProviderFactory;
    @Mock
    private VfsMutableFolderProvider vfsMutableFolderProvider;
    @Mock
    private VfsImmutableProvider vfsImmutableProvider;
    @Mock
    private Request request;
    @Mock
    private XrayAddon xrayAddon;
    @Mock
    private RestCoreAddon restCoreAddon;
    @Mock
    private HaAddon haAddon;
    @Mock
    private FilteredResourcesAddon filteredResourcesAddon;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(requestContext.getRequest()).thenReturn(request);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(AuthorizationService.class)).thenReturn(authorizationService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(context.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);
        when(context.beanForType(FileService.class)).thenReturn(fileService);
        when(context.beanForType(StatsServiceImpl.class)).thenReturn(statsService);
        when(context.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(internalRepoService);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(context.beanForType(VfsItemProviderFactory.class)).thenReturn(vfsItemProviderFactory);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(addonsManager.addonByType(RestCoreAddon.class)).thenReturn(restCoreAddon);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(addonsManager.addonByType(FilteredResourcesAddon.class)).thenReturn(filteredResourcesAddon);
        when(restCoreAddon.getDynamicVersionContext(any(), any(), anyBoolean())).thenReturn(requestContext);
        when(vfsItemProviderFactory.createImmutableFileProvider(any(), any())).thenReturn(vfsImmutableProvider);
        when(context.getAuthorizationService()).thenReturn(authorizationService);
        when(context.getSecurityService()).thenReturn(securityService);
        when(vfsItemProviderFactory.createFolderProvider(any(), any(), any(), any()))
                .thenReturn(vfsMutableFolderProvider);
        when(internalRepoService.storingRepositoryByKey(any())).thenReturn(mock(StoringRepo.class));
        ArtifactoryContextThreadBinder.bind(context);
        repoDescriptor = new LocalRepoDescriptor();
        repoDescriptor.setKey("repoKey");
        repoDescriptor.setSnapshotVersionBehavior(UNIQUE);
        localRepoSpy =
                spy(new DbLocalRepo(repoDescriptor, repoDescriptor.toRepositoryConfig(), internalRepoService, null,
                        centralConfigService));
        localRepoSpy.init(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGetInfoPermissionsCheckNotCalledIfMavenAndNotFoundAndConstEnabled() {
        repoDescriptor.setType(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");
        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(0)).checkDownloadIsAllowed(any());
    }

    @Test
    void testGetInfoPermissionsCheckCalledAfterIfMavenAndFoundAndConstEnabledButNotSnapshot() {
        repoDescriptor.setType(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");
        prepareMocksForFilename("test.jar");

        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(1)).checkDownloadIsAllowed(any());

        // Back to default
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "false");
    }

    @Test
    void testGetInfoPermissionsCheckCalledAfterIfMavenAndFoundAndConstEnabledAndSnapshot() {
        repoDescriptor.setType(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");
        prepareMocksForFilename("test-SNAPSHOT.jar");

        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(0)).checkDownloadIsAllowed(any());

        // And now without any children in the repo
        when(internalRepoService.hasChildren(any())).thenReturn(false);
        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(1)).checkDownloadIsAllowed(any());

        // Back to default
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "false");
    }

    @Test
    void testGetInfoPermissionsCheckCalledAfterIfMavenAndConstEnabledAndSnapshotIgnoreAllForRemote() {
        repoDescriptor.setType(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "true");
        prepareMocksForFilename("test-SNAPSHOT.jar");

        // Let's pretend it's a remote repo
        when(internalRepoService.remoteRepositoryByKey(any())).thenReturn(mock(RemoteRepo.class));
        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(1)).checkDownloadIsAllowed(any());

        // Back to default
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "false");
    }

    private void prepareMocksForFilename(String filename) {
        String path = "path/to/some-SNAPSHOT/" + filename;
        VfsFile vfsFile = mock(VfsFile.class);
        when(vfsFile.getName()).thenReturn(filename);
        FileInfo fileInfo = mockFileInfo(path);
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(requestContext.getResourcePath()).thenReturn(path);
        Properties properties = mock(Properties.class);
        when(properties.getFirst("sha1")).thenReturn("sha1");
        when(properties.getFirst("md5")).thenReturn("md5");
        when(requestContext.getProperties()).thenReturn(properties);
        when(propertiesService.getProperties(vfsFile.getInfo())).thenReturn(properties);
        when(filteredResourcesAddon.isFilteredResourceFile(any(Properties.class))).thenReturn(false);
        when(vfsImmutableProvider.getImmutableFile()).thenReturn(vfsFile);
        when(internalRepoService.hasChildren(any())).thenReturn(true);
    }

    @NonNull
    private FileInfo mockFileInfo(String path) {
        InternalFileInfo fileInfo = mock(InternalFileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(new RepoPathImpl(repoDescriptor.getKey(), path));
        when(fileInfo.getId()).thenReturn(1L);
        when(fileInfo.getCreatedBy()).thenReturn("admin");
        when(fileInfo.getModifiedBy()).thenReturn("admin");
        when(fileInfo.getLastModified()).thenReturn(1L);
        when(fileInfo.getSize()).thenReturn(1L);
        when(fileInfo.isIdentical(any())).thenReturn(false);
        when(fileInfo.getSha1()).thenReturn("sha1");
        when(fileInfo.getSha2()).thenReturn("sha2");
        when(fileInfo.getMd5()).thenReturn("md5");
        ChecksumsInfo checksumsInfo = mock(ChecksumsInfo.class);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        FileAdditionalInfo fileAdditionalInfo = new FileAdditionalInfo();
        fileAdditionalInfo.setCreatedBy("admin");
        fileAdditionalInfo.setModifiedBy("admin");
        fileAdditionalInfo.setLastUpdated(1L);
        when(fileInfo.getAdditionalInfo()).thenReturn(fileAdditionalInfo);
        return fileInfo;
    }

    @Test
    void testGetInfoPermissionsCheckCalledIfNotMaven() {
        repoDescriptor.setType(RepoType.Docker);
        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(1)).checkDownloadIsAllowed(any());
    }

    @Test
    void testGetInfoPermissionsCheckCalledIfConstDisabled() {
        repoDescriptor.setType(RepoType.Maven);
        ArtifactorySystemProperties artifactoryProperties = ArtifactoryHome.get().getArtifactoryProperties();
        artifactoryProperties.setProperty(ConstantValues.enableMavenNonPreemptiveAuthentication.getPropertyName(),
                "false");
        localRepoSpy.getInfo(requestContext);
        verify(localRepoSpy, times(1)).checkDownloadIsAllowed(any());
    }

    @ParameterizedTest
    @MethodSource("bypassDownloadBlockingTestEntries")
    void checkFilesDownloadAllowedForBypassHeader(String repoType, String fileName, String headerValue,
            boolean expectedResult) {
        repoDescriptor.setType(RepoType.fromType(repoType));
        when(request.getHeaderValues(BYPASS_UNSCANNED_BLOCKING_HEADER)).thenReturn(enumeration(List.of(headerValue)));
        Assertions.assertEquals(expectedResult, localRepoSpy.isDownloadWithBypassHeader(
                requestContext, new RepoPathImpl(repoDescriptor.getKey(), fileName)));
    }

    @Test
    void checkAbsentBypassHeader() {
        repoDescriptor.setType(RepoType.Docker);
        when(request.getHeaderValues(BYPASS_UNSCANNED_BLOCKING_HEADER)).thenReturn(null);
        assertFalse(localRepoSpy.isDownloadWithBypassHeader(requestContext,
                new RepoPathImpl(repoDescriptor.getKey(), "manifest.json")));
        when(request.getHeaderValues(BYPASS_UNSCANNED_BLOCKING_HEADER)).thenReturn(Collections.emptyEnumeration());
        assertFalse(localRepoSpy.isDownloadWithBypassHeader(requestContext,
                new RepoPathImpl(repoDescriptor.getKey(), "manifest.json")));
    }

    @Test
    void testUndeploy() {
        VfsMutableFolderProvider vfsMutableFolderProvider = Mockito.mock(VfsMutableFolderProvider.class);
        when(vfsItemProviderFactory.createFolderProvider(any(), any(), any(), any()))
                .thenReturn(vfsMutableFolderProvider);
        VfsItemProvider vfsItemProvider = Mockito.mock(VfsItemProvider.class);
        MutableVfsItem<MutableItemInfo> mutableFsItem = Mockito.mock(MutableVfsItem.class);
        when(vfsItemProvider.getMutableFsItem()).thenReturn(mutableFsItem);
        when(vfsItemProviderFactory.createItemProvider(any(), any(), any(), any())).thenReturn(vfsItemProvider);

        when(context.beanForType(VfsItemProviderFactory.class)).thenReturn(vfsItemProviderFactory);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(Mockito.mock(HaAddon.class));
        when(context.beanForType(FileService.class)).thenReturn(Mockito.mock(FileService.class));

        DeleteContext deleteContext = new DeleteContext(new RepoPathImpl("repoKey", "path"));
        localRepoSpy.undeploy(deleteContext);
        verify(mutableFsItem, times(1)).delete(any(), any());
    }

    @Test
    void testCheckResourceStatusBeforeDownload() {
        repoDescriptor.setIncludesPattern("nothing");
        localRepoSpy =
                spy(new DbLocalRepo(repoDescriptor, repoDescriptor.toRepositoryConfig(), internalRepoService, null,
                                    centralConfigService));
        localRepoSpy.init(false);
        when(requestContext.getResourcePath()).thenReturn("path");
        when(request.getRepoPath()).thenReturn(new RepoPathImpl("repoKey", "path"));

        RepoResource info = localRepoSpy.getInfo(requestContext);
        Assertions.assertNotNull(info);
        Assertions.assertInstanceOf(UnfoundRepoResource.class, info);
        UnfoundRepoResource unfoundRepoResource = (UnfoundRepoResource) info;
        Assertions.assertEquals(404, unfoundRepoResource.getStatusCode());
        Assertions.assertEquals("The repository 'repoKey' rejected the artifact 'repoKey:path' due to its" +
                                        " include/exclude pattern settings.", unfoundRepoResource.getDetail());
        Assertions.assertEquals(UnfoundRepoResource.Reason.INCLUDE_EXCLUDE_PATTERN, unfoundRepoResource.getReason());
    }

    private static Stream<Arguments> bypassDownloadBlockingTestEntries() {
        return Stream.of(
                Arguments.of("docker", "manifest.json", "true", true),
                Arguments.of("docker", "manifest.json", "false", false),
                Arguments.of("maven", "manifest.json", "true", false),
                Arguments.of("conan", "conanmanifest.txt", "true", true),
                Arguments.of("conan", "conanmanifest.txt", "false", false),
                Arguments.of("nuget", "conanmanifest.txt", "true", false),
                Arguments.of("go", "v1.5.2.mod", "true", true),
                Arguments.of("go", "v1.5.2.mod", "false", false),
                Arguments.of("go", "v1.5.2.info", "true", true),
                Arguments.of("go", "v1.5.2.info", "false", false),
                Arguments.of("go", "v1.5.2.zip", "true", false),
                Arguments.of("maven", "some-artifact-1.0.mod", "true", false)
        );
    }
}