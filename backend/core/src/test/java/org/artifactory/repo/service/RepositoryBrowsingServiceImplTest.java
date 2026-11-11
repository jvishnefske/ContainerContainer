package org.artifactory.repo.service;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class RepositoryBrowsingServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private RepositoryBrowsingServiceImpl service;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private FileService fileService;

    private AutoCloseable autoCloseable;

    static Stream<Arguments> provideInputs() {
        return Stream.of(Arguments.of(MavenNaming.NEXUS_INDEX_DIR, true, false),
                Arguments.of(MavenNaming.NEXUS_INDEX_PREFIX, true, false),
                Arguments.of(MavenNaming.NEXUS_INDEX_DIR, false, true), Arguments.of("/path", true, true));
    }

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(AuthorizationService.class)).thenReturn(authorizationService);
        when(artifactoryContext.beanForType(FileService.class)).thenReturn(fileService);
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(artifactoryContext.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(artifactoryContext.getRepositoryService()).thenReturn(internalRepositoryService);
        when(artifactoryContext.getAuthorizationService()).thenReturn(authorizationService);
        service = new RepositoryBrowsingServiceImpl(authorizationService, internalRepositoryService, propertiesService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource({"provideInputs"})
    void testVirtualRepoAccepts(String path, boolean isMavenGroup, boolean expectedResult) {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getPath()).thenReturn(path);
        when(repoPath.isFolder()).thenReturn(false);
        when(virtualRepo.accepts(repoPath)).thenReturn(true);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        RepoType repoType = mock(RepoType.class);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        when(repoType.isMavenGroup()).thenReturn(isMavenGroup);
        boolean virtualRepoAccepts = service.virtualRepoAccepts(virtualRepo, repoPath);
        assertEquals(expectedResult, virtualRepoAccepts);
    }

    @Test
    void testLocalBrowsableChildrenReturnsEmptyListOnNonAccessibleResource() {
        List<BaseBrowsableItem> items = runLocalBrowsableChildrenWithMocksAndIncludePattern();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    void testLocalBrowsableChildrenThrowsExceptionOnNonAccessibleResource() {
        getBound().getArtifactoryProperties().setProperty(
                ConstantValues.nativeBrowserReturnEmptyListForNonAccessibleResources.getPropertyName(), "false");
        Throwable t = assertThrows(ItemNotFoundRuntimeException.class,
                this::runLocalBrowsableChildrenWithMocksAndIncludePattern);
        assertEquals("Repo 'someRepoKey' rejected the path 'someRepoKey/some-dir/temp/'", t.getMessage());
    }

    private List<BaseBrowsableItem> runLocalBrowsableChildrenWithMocksAndIncludePattern() {
        RepoPath repoPath = RepoPathFactory.create("someRepoKey", "some-dir/temp/");
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        when(criteria.getRepoPath()).thenReturn(repoPath);
        var repoMock = mock(LocalRepo.class);
        when(repoMock.isBlackedOut()).thenReturn(false);
        when(repoMock.accepts(any())).thenReturn(false); // means the path is rejected by include/exclude patterns
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(repoMock);
        ReflectionTestUtils.setField(service, "repoService", repoService);
        return service.getLocalRepoBrowsableChildren(criteria);
    }

    @Test
    void testStreamOfLocalAndCachedRepoChildrenItemsWithMocksAndIncludePattern() {
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        when(criteria.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        var repoMock = mock(LocalRepo.class);
        when(repoMock.isBlackedOut()).thenReturn(false);
        when(repoMock.accepts(any())).thenReturn(false);
        InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        when(repoService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(repoMock);
        ReflectionTestUtils.setField(service, "repoService", repoService);
        List<BaseBrowsableItem> items = service.getLocalRepoBrowsableChildrenStream(criteria, false, null).toList();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    void testStreamOfLocalRepoChildren() {
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        when(criteria.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        var repoMock = mock(LocalRepo.class);
        var localRepoConfig = mock(LocalRepoConfig.class);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repoMock.isBlackedOut()).thenReturn(false);
        when(repoMock.accepts(any())).thenReturn(true);
        when(repoMock.getRepoConfig()).thenReturn(localRepoConfig);
        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.isFolder()).thenReturn(true);
        when(folderInfo.getName()).thenReturn("temp");
        when(folderInfo.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        when(fileService.loadItem(any())).thenReturn(folderInfo);
        when(fileService.getChildrenStream(any())).thenReturn(Stream.of(folderInfo, folderInfo));
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(repoMock);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canManage((RepoPath) any())).thenReturn(true);
        when(internalRepositoryService.isLocalFederatedOrCachedRepoPathAccepted(any())).thenReturn(true);
        List<BaseBrowsableItem> items = service.getLocalRepoBrowsableChildrenStream(criteria, false, null).toList();
        assertNotNull(items);
        assertEquals(2, items.size());
    }

    @Test
    void testStreamOfLocalRepoChildrenWithNoReadPermission() {
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        when(criteria.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        var repoMock = mock(LocalRepo.class);
        var localRepoConfig = mock(LocalRepoConfig.class);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repoMock.isBlackedOut()).thenReturn(false);
        when(repoMock.accepts(any())).thenReturn(true);
        when(repoMock.getRepoConfig()).thenReturn(localRepoConfig);
        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.isFolder()).thenReturn(true);
        when(folderInfo.getName()).thenReturn("temp");
        when(folderInfo.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        when(fileService.loadItem(any())).thenReturn(folderInfo);
        when(fileService.getChildrenStream(any())).thenReturn(Stream.of(folderInfo, folderInfo));
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(repoMock);
        when(authorizationService.canRead(any())).thenReturn(false);
        when(authorizationService.canManage((RepoPath) any())).thenReturn(true);
        when(internalRepositoryService.isLocalFederatedOrCachedRepoPathAccepted(any())).thenReturn(true);
        List<BaseBrowsableItem> items = service.getLocalRepoBrowsableChildrenStream(criteria, false, null).toList();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    void testStreamOfRemoteRepoChildren() {
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        when(criteria.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        var repoMock = mock(RemoteRepo.class);
        var localRepoConfig = mock(LocalRepoConfig.class);
        var localCacheRepoConfig = mock(LocalCacheRepoConfig.class);
        var cacheRepo = mock(LocalCacheRepo.class);
        when(cacheRepo.getKey()).thenReturn("someRepoKey-cache");
        when(cacheRepo.accepts(any())).thenReturn(true);
        when(cacheRepo.isBlackedOut()).thenReturn(false);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(localCacheRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repoMock.isBlackedOut()).thenReturn(false);
        when(repoMock.accepts(any())).thenReturn(true);
        when(repoMock.getRepoConfig()).thenReturn(localRepoConfig);
        when(cacheRepo.getRepoConfig()).thenReturn(localCacheRepoConfig);
        when(repoMock.isStoreArtifactsLocally()).thenReturn(true);
        when(repoMock.isListRemoteFolderItems()).thenReturn(false);
        when(repoMock.getLocalCacheRepo()).thenReturn(cacheRepo);
        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.isFolder()).thenReturn(true);
        when(folderInfo.getName()).thenReturn("temp");
        when(folderInfo.getRepoPath()).thenReturn(RepoPathFactory.create("someRepoKey", "some-dir/temp/"));
        when(fileService.loadItem(any())).thenReturn(folderInfo);
        when(fileService.getChildrenStream(any())).thenReturn(Stream.of(folderInfo, folderInfo));
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(repoMock);
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey("someRepoKey-cache")).thenReturn(
                cacheRepo);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canManage((RepoPath) any())).thenReturn(true);
        when(internalRepositoryService.isLocalFederatedOrCachedRepoPathAccepted(any())).thenReturn(true);
        List<BaseBrowsableItem> items = service.getRemoteRepoBrowsableChildrenStream(criteria, false, null).toList();
        assertNotNull(items);
        assertEquals(2, items.size());
    }

    @Test
    void testFilterStreamItemsIfNeeded_HiddenReleaseBundlePaths() {
        String repoKey = DEFAULT_RBV2_REPO;
        ItemInfo bundleManifest = mock(ItemInfo.class);
        String bundleManifestPath = "some-RB-name/some-RB-version/release-bundle.json.evd";
        when(bundleManifest.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, bundleManifestPath));

        ItemInfo promotionManifest = mock(ItemInfo.class);
        String promotionManifestPath = "some-RB-name/some-RB-version/promotion-1234567890.json.evd";
        when(promotionManifest.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, promotionManifestPath));

        ItemInfo bundleArtifact = mock(ItemInfo.class);
        String bundleArtifactPath = "some-RB-name/some-RB-version/artifacts/generic/file.zip";
        when(bundleArtifact.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, bundleArtifactPath));

        Stream<ItemInfo> itemInfos = Stream.of(bundleManifest, promotionManifest, bundleArtifact);
        String repoType = RepoType.ReleaseBundles.getType();
        List<ItemInfo> result = service.filterStreamItemsIfNeeded(itemInfos, repoType, true).toList();

        assertEquals(1, result.size());
        assertEquals(bundleArtifact, result.getFirst());
    }

    @Test
    void testRemoteCachedNodesStreamUsesCorrectIsFolderValue() {
        testRemoteCachedNodesStreamWithIsFolderValue(true);
        testRemoteCachedNodesStreamWithIsFolderValue(false);
    }

    @Test
    void testVirtualIncludeExcludePatternOnRemoteRepo() {
        String virtualRepoKey = "test-virtual-repo";
        String remoteRepoKey = "test-remote-repo";
        String folderPath = "maven/maven-abbot-plugin";
        String remoteBaseUrl = "http://mock-server/libs-release";

        // Build a real VirtualRepoConfig with actual include/exclude pattern strings so that
        // the real RepoBase.accepts() → PathMatcher chain is exercised, not a simulated lambda
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create(virtualRepoKey, RepoType.Maven);
        repoConfig.getBaseConfig().setIncludesPattern("maven/maven-abbot-plugin/**");
        repoConfig.getBaseConfig().setExcludesPattern("maven/maven-abbot-plugin/1.1");

        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey(virtualRepoKey);
        descriptor.setType(RepoType.Maven);

        VirtualRepo virtualRepo = spy(new VirtualRepo(
                descriptor, repoConfig, internalRepositoryService, mock(CentralConfigService.class)));

        var remoteRepo = mock(RemoteRepo.class);
        doReturn(List.of(virtualRepo)).when(virtualRepo).getResolvedVirtualRepos();
        doReturn(List.of()).when(virtualRepo).getLocalRepositories();
        doReturn(List.of(remoteRepo)).when(virtualRepo).getRemoteRepositories();

        when(internalRepositoryService.virtualRepositoryByKey(virtualRepoKey)).thenReturn(virtualRepo);

        when(remoteRepo.getKey()).thenReturn(remoteRepoKey);
        when(remoteRepo.isBlackedOut()).thenReturn(false);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getUrl()).thenReturn(remoteBaseUrl);
        when(remoteRepo.getRepoPath(folderPath)).thenReturn(RepoPathFactory.create(remoteRepoKey, folderPath));
        when(internalRepositoryService.remoteRepositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        when(remoteRepo.listRemoteResources(folderPath)).thenReturn(List.of(
                new RemoteItem(remoteBaseUrl + "/" + folderPath + "/1.0", true),
                new RemoteItem(remoteBaseUrl + "/" + folderPath + "/1.1", true)));
        when(authorizationService.canRead(any())).thenReturn(true);

        RepoPath virtualRepoPath = InternalRepoPathFactory.create(virtualRepoKey, folderPath, true);
        BrowsableItemCriteria criteria = new BrowsableItemCriteria.Builder(virtualRepoPath).build();

        List<BaseBrowsableItem> result = service.getVirtualRepoBrowsableChildren(criteria);

        assertEquals(1, result.size());
        assertEquals(folderPath + "/1.0", result.getFirst().getRelativePath());
    }

    private void testRemoteCachedNodesStreamWithIsFolderValue(boolean isFolder) {
        BrowsableItemCriteria criteria = mock(BrowsableItemCriteria.class);
        RepoPath originalRepoPath = mock(RepoPath.class);
        when(originalRepoPath.getRepoKey()).thenReturn("someRepoKey");
        when(originalRepoPath.getPath()).thenReturn("some-dir/temp");
        when(originalRepoPath.isFolder()).thenReturn(isFolder);
        when(criteria.getRepoPath()).thenReturn(originalRepoPath);
        when(criteria.isIncludeRemoteResources()).thenReturn(false);

        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        LocalCacheRepo cacheRepo = mock(LocalCacheRepo.class);
        when(cacheRepo.getKey()).thenReturn("someRepoKey-cache");
        when(remoteRepo.getLocalCacheRepo()).thenReturn(cacheRepo);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.isBlackedOut()).thenReturn(false);
        when(remoteRepo.accepts(any())).thenReturn(true);

        when(internalRepositoryService.remoteRepositoryByKey("someRepoKey")).thenReturn(remoteRepo);

        RepositoryBrowsingServiceImpl serviceSpy = spy(service);

        doReturn(Stream.empty()).when(serviceSpy).getLocalRepoBrowsableChildrenStream(
                any(BrowsableItemCriteria.class), eq(false), eq(null));

        RepoPath mockCacheRepoPath = mock(RepoPath.class);
        when(mockCacheRepoPath.getRepoKey()).thenReturn("someRepoKey-cache");
        when(mockCacheRepoPath.getPath()).thenReturn("some-dir/temp");
        when(mockCacheRepoPath.isFolder()).thenReturn(isFolder);

        try (MockedStatic<InternalRepoPathFactory> mockedFactory = mockStatic(InternalRepoPathFactory.class)) {
            mockedFactory.when(() -> InternalRepoPathFactory.create(
                    eq("someRepoKey-cache"),
                    eq("some-dir/temp"),
                    eq(isFolder)
            )).thenReturn(mockCacheRepoPath);

            serviceSpy.getRemoteRepoBrowsableChildrenStream(criteria, false, null);

            mockedFactory.verify(() -> InternalRepoPathFactory.create(
                    eq("someRepoKey-cache"),
                    eq("some-dir/temp"),
                    eq(isFolder)
            ));
        }
    }
}