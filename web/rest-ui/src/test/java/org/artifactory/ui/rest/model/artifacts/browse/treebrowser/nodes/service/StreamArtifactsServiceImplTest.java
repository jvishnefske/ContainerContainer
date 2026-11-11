package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;


import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class StreamArtifactsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ItemInfo itemInfo1;
    @Mock
    private ItemInfo itemInfo2;
    @Mock
    private ItemInfo itemInfo3;
    @Mock
    private FolderInfo folderInfo1;
    @Mock
    private RepoPath filePath;
    @Mock
    private RepoPath folderPath;
    @Mock
    private RepoPath restrictedPath;

    private StreamArtifactsServiceImpl streamArtifactsService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        streamArtifactsService =
                new StreamArtifactsServiceImpl(repositoryService, internalRepositoryService, authorizationService);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(authorizationService.canRead(any())).thenReturn(true);
        prepareItems();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    private void prepareItems() {
        when(filePath.getPath()).thenReturn("cool-path");
        when(folderPath.getPath()).thenReturn("cool-folder-path");

        when(itemInfo1.getName()).thenReturn("cool-file");
        when(itemInfo1.getRelPath()).thenReturn("cool-path");
        when(itemInfo1.getRepoPath()).thenReturn(filePath);

        when(itemInfo2.getName()).thenReturn("cool-file-2");
        when(itemInfo2.getRelPath()).thenReturn("cool-path-2");
        when(itemInfo2.getRepoPath()).thenReturn(filePath);

        when(itemInfo3.getName()).thenReturn("cool-file-3");
        when(itemInfo3.getRelPath()).thenReturn("cool-path-3");
        when(itemInfo3.getRepoPath()).thenReturn(filePath);

        when(folderInfo1.getName()).thenReturn("cool-folder");
        when(folderInfo1.getRelPath()).thenReturn("cool-folder-path");
        when(folderInfo1.getRepoPath()).thenReturn(folderPath);
        when(folderInfo1.isFolder()).thenReturn(true);
    }

    @Test
    void testGetNodesStreamFromLocal() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("testRepo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoType()).thenReturn("local");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("testRepo", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        Stream<BaseNode> result = streamArtifactsService.getNodesStreamFromLocal(artifactFilter);
        assertEquals(2, result.count());
    }

    @Test
    void testGetNodesStreamFromRemoteCache() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        LocalCacheRepo remoteCacheRepo = mock(LocalCacheRepo.class);
        when(artifactFilter.getRepoKey()).thenReturn("remote-repo");
        when(artifactFilter.getRepoType()).thenReturn("remote");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(internalRepositoryService.remoteRepositoryByKey(artifactFilter.getRepoKey())).thenReturn(remoteRepo);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(false);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getLocalCacheRepo()).thenReturn(remoteCacheRepo);
        when(remoteCacheRepo.getKey()).thenReturn("remote-repo-cache");
        when(folderInfo1.getRepoKey()).thenReturn("remote-repo-cache");
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(folderPath)).thenReturn(true);
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("remote-repo-cache", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2, itemInfo3, folderInfo1));
        Stream<BaseNode> result = streamArtifactsService.getNodesStreamFromRemote(artifactFilter);
        assertEquals(4, result.count());
    }

    @Test
    void testGetNodesStreamFromRemoteResource() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("remote-repo");
        when(artifactFilter.getRepoType()).thenReturn("remote");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.listRemoteResources(any())).thenReturn(List.of(
                new RemoteItem("item1", false),
                new RemoteItem("item2", true)));
        when(internalRepositoryService.remoteRepositoryByKey(artifactFilter.getRepoKey())).thenReturn(remoteRepo);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getUrl()).thenReturn("http://remote-repo.com");
        when(remoteRepo.getRepoPath(any())).thenReturn(InternalRepoPathFactory.create("remote-repo", "item1"));
        Stream<BaseNode> result = streamArtifactsService.getNodesStreamFromRemote(artifactFilter);
        assertEquals(2, result.count());
    }

    @Test
    void testGetNodesStreamFromVirtual() {
        final VirtualRepoConfig repoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Generic);
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("virtual-repo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(artifactFilter.getRepoType()).thenReturn("virtual");


        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        LocalCacheRepo remoteCacheRepo = mock(LocalCacheRepo.class);


        when(localRepo.getKey()).thenReturn("local-repo");
        when(remoteRepo.getKey()).thenReturn("remote-repo");
        when(virtualRepo.getKey()).thenReturn("virtual-repo");
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);

        when(internalRepositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);
        when(internalRepositoryService.remoteRepositoryByKey("remote-repo")).thenReturn(remoteRepo);

        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isReal()).thenReturn(true);
        when(remoteRepo.isLocal()).thenReturn(false);
        when(remoteRepo.isReal()).thenReturn(true);
        when(virtualRepo.isLocal()).thenReturn(false);
        when(virtualRepo.isReal()).thenReturn(false);

        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);

        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getUrl()).thenReturn("http://remote-repo.com");
        when(remoteRepo.getLocalCacheRepo()).thenReturn(remoteCacheRepo);
        when(remoteRepo.getRepoPath(any())).thenReturn(InternalRepoPathFactory.create("remote-repo", "item1"));
        when(remoteCacheRepo.getKey()).thenReturn("remote-repo-cache");

        when(virtualRepo.getLocalRepositories()).thenReturn(List.of(localRepo));
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of(remoteRepo));
        when(virtualRepo.getVirtualRepositories()).thenReturn(List.of());
        when(virtualRepo.isPathPatternValid(any(), any())).thenReturn(true);
        when(virtualRepo.accepts(any())).thenReturn(true);

        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(any())).thenReturn(true);
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("local-repo", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2, folderInfo1));
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("remote-repo-cache", "")))
                .thenReturn(Stream.of(itemInfo3));

        Stream<BaseNode> result = streamArtifactsService.getNodesStreamFromVirtual(artifactFilter);
        assertEquals(4, result.count());
    }

    @Test
    void testGetNodesStreamFromVirtualIgnoringRepoKey() {
        final VirtualRepoConfig parentRepoConfig = VirtualRepoConfig.create("parent-virtual", RepoType.Generic);
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserMaxNumberOfLocalsToBrowseIndividually,
                "1");
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("parent-virtual");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(artifactFilter.getRepoType()).thenReturn("virtual");

        VirtualRepo parentRepo = mock(VirtualRepo.class);
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);

        when(localRepo.getKey()).thenReturn("local-repo");
        when(virtualRepo.getKey()).thenReturn("virtual-repo");
        when(parentRepo.getKey()).thenReturn("parent-virtual");
        when(parentRepo.getRepoConfig()).thenReturn(parentRepoConfig);

        when(internalRepositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);
        when(internalRepositoryService.virtualRepositoryByKey("parent-virtual")).thenReturn(parentRepo);
        when(internalRepositoryService.localFederatedOrCachedRepositoryByKey("local-repo")).thenReturn(localRepo);

        when(parentRepo.isLocal()).thenReturn(false);
        when(parentRepo.isReal()).thenReturn(false);
        when(virtualRepo.isLocal()).thenReturn(false);
        when(virtualRepo.isReal()).thenReturn(false);
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isReal()).thenReturn(true);

        when(parentRepo.getVirtualRepositories()).thenReturn(List.of(virtualRepo));
        when(virtualRepo.getLocalRepositories()).thenReturn(List.of(localRepo));

        when(virtualRepo.accepts(any())).thenReturn(true);
        when(parentRepo.accepts(any())).thenReturn(true);
        when(localRepo.accepts(any())).thenReturn(true);

        when(virtualRepo.isPathPatternValid(any(), any())).thenReturn(true);
        when(parentRepo.isPathPatternValid(any(), any())).thenReturn(true);

        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(any())).thenReturn(true);
        when(filePath.getRepoKey()).thenReturn("local-repo");
        when(folderPath.getRepoKey()).thenReturn("local-repo");
        
        when(repositoryService.getNodesStreamIgnoringRepo(any())).thenReturn(
                Stream.of(itemInfo1, itemInfo2, folderInfo1));

        Stream<BaseNode> result = streamArtifactsService.getNodesStreamFromVirtual(artifactFilter);
        assertEquals(3, result.count());
    }

    @Test
    void testGetRemoteNodesStreamRepoInaccessibleReturnsEmptyStream() {
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.listRemoteResources(anyString())).thenThrow(new RuntimeException("Thrown by test"));

        Stream<BaseNode> remoteNodesStream =
                streamArtifactsService.getRemoteNodesStream(mock(ArtifactFilter.class), remoteRepo);

        assertNotNull(remoteNodesStream);
        assertEquals(0, remoteNodesStream.count());
    }

    @Test
    void testGetNodesStreamFromRemoteReturnsEmptyStreamWhenRepoNotFound() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("deleted-remote-repo");
        when(artifactFilter.getPath()).thenReturn("");
        when(internalRepositoryService.remoteRepositoryByKey("deleted-remote-repo")).thenReturn(null);

        Stream<BaseNode> result = assertDoesNotThrow(
                () -> streamArtifactsService.getNodesStreamFromRemote(artifactFilter));

        assertNotNull(result);
        assertEquals(0, result.count());
    }

    @Test
    void testGetNodesStreamFromVirtualWithStaleRemoteReturnsLocalNodesOnly() {
        final VirtualRepoConfig repoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Generic);
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("virtual-repo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("docker");
        when(artifactFilter.getRepoType()).thenReturn("virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        RemoteRepo<?, ?> staleRemoteRepo = mock(RemoteRepo.class);

        when(localRepo.getKey()).thenReturn("local-repo");
        when(staleRemoteRepo.getKey()).thenReturn("stale-remote-repo");
        when(virtualRepo.getKey()).thenReturn("virtual-repo");
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);

        when(internalRepositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);
        when(internalRepositoryService.remoteRepositoryByKey("stale-remote-repo")).thenReturn(null);

        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isReal()).thenReturn(true);
        when(staleRemoteRepo.isLocal()).thenReturn(false);
        when(staleRemoteRepo.isReal()).thenReturn(true);
        when(virtualRepo.isLocal()).thenReturn(false);
        when(virtualRepo.isReal()).thenReturn(false);

        when(virtualRepo.getLocalRepositories()).thenReturn(List.of(localRepo));
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of(staleRemoteRepo));
        when(virtualRepo.getVirtualRepositories()).thenReturn(List.of());
        when(virtualRepo.isPathPatternValid(any(), any())).thenReturn(true);
        when(virtualRepo.accepts(any())).thenReturn(true);

        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(any())).thenReturn(true);
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("local-repo", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2));

        Stream<BaseNode> result = assertDoesNotThrow(
                () -> streamArtifactsService.getNodesStreamFromVirtual(artifactFilter));

        assertNotNull(result);
        assertEquals(2, result.count());
        verify(internalRepositoryService, never()).remoteRepositoryByKey("local-repo");
    }

    @Test
    void testGetLocalNodesStreamFiltersUnreadablePaths() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("testRepo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoType()).thenReturn("local");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(itemInfo2.getRepoPath()).thenReturn(restrictedPath);
        when(authorizationService.canRead(restrictedPath)).thenReturn(false);
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("testRepo", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);

        List<BaseNode> result = streamArtifactsService.getNodesStreamFromLocal(artifactFilter).toList();
        assertEquals(1, result.size());
        assertEquals("cool-file", result.getFirst().getText());
    }

    @Test
    void testGetRemoteNodesStreamFiltersUnreadablePaths() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("remote-repo");
        when(artifactFilter.getRepoType()).thenReturn("remote");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        setupRemoteRepoForLiveListing(remoteRepo);
        when(internalRepositoryService.remoteRepositoryByKey("remote-repo")).thenReturn(remoteRepo);
        when(authorizationService.canRead(any())).thenAnswer(inv -> {
            RepoPath rp = inv.getArgument(0);
            return !"restricted-item".equals(rp.getPath());
        });

        List<BaseNode> result = streamArtifactsService.getNodesStreamFromRemote(artifactFilter).toList();
        assertEquals(1, result.size());
        assertEquals("permitted-item", result.getFirst().getText());
    }

    @Test
    void testGetRemoteCachedNodesStreamFiltersUnreadablePaths() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("remote-repo");
        when(artifactFilter.getRepoType()).thenReturn("remote");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(itemInfo2.getRepoPath()).thenReturn(restrictedPath);
        when(authorizationService.canRead(restrictedPath)).thenReturn(false);
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        LocalCacheRepo remoteCacheRepo = mock(LocalCacheRepo.class);
        when(internalRepositoryService.remoteRepositoryByKey("remote-repo")).thenReturn(remoteRepo);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(false);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getLocalCacheRepo()).thenReturn(remoteCacheRepo);
        when(remoteCacheRepo.getKey()).thenReturn("remote-repo-cache");
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("remote-repo-cache", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);

        List<BaseNode> result = streamArtifactsService.getNodesStreamFromRemote(artifactFilter).toList();
        assertEquals(1, result.size());
        assertEquals("cool-file", result.getFirst().getText());
    }

    @Test
    void testGetVirtualNodesStreamFiltersUnreadableLocalPaths() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("virtual-repo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(artifactFilter.getRepoType()).thenReturn("virtual");
        when(itemInfo2.getRepoPath()).thenReturn(restrictedPath);
        when(authorizationService.canRead(restrictedPath)).thenReturn(false);
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        setupVirtualRepo(virtualRepo, "virtual-repo");
        when(internalRepositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);
        when(localRepo.getKey()).thenReturn("local-repo");
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isReal()).thenReturn(true);
        when(virtualRepo.getLocalRepositories()).thenReturn(List.of(localRepo));
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of());
        when(repositoryService.getChildrenStream(InternalRepoPathFactory.create("local-repo", "")))
                .thenReturn(Stream.of(itemInfo1, itemInfo2));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);

        List<BaseNode> result = streamArtifactsService.getNodesStreamFromVirtual(artifactFilter).toList();
        assertEquals(1, result.size());
        assertEquals("cool-file", result.getFirst().getText());
    }

    @Test
    void testGetVirtualNodesStreamFiltersUnreadableRemotePaths() {
        ArtifactFilter artifactFilter = mock(ArtifactFilter.class);
        when(artifactFilter.getRepoKey()).thenReturn("virtual-repo");
        when(artifactFilter.getPath()).thenReturn("");
        when(artifactFilter.getRepoPkgType()).thenReturn("maven");
        when(artifactFilter.getRepoType()).thenReturn("virtual");
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        setupVirtualRepo(virtualRepo, "virtual-repo");
        setupRemoteRepoForLiveListing(remoteRepo);
        when(internalRepositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);
        when(internalRepositoryService.remoteRepositoryByKey("remote-repo")).thenReturn(remoteRepo);
        when(remoteRepo.getKey()).thenReturn("remote-repo");
        when(remoteRepo.isLocal()).thenReturn(false);
        when(remoteRepo.isReal()).thenReturn(true);
        when(virtualRepo.getLocalRepositories()).thenReturn(List.of());
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of(remoteRepo));
        when(authorizationService.canRead(any())).thenAnswer(inv -> {
            RepoPath rp = inv.getArgument(0);
            return !"restricted-item".equals(rp.getPath());
        });

        List<BaseNode> result = streamArtifactsService.getNodesStreamFromVirtual(artifactFilter).toList();
        assertEquals(1, result.size());
        assertEquals("permitted-item", result.getFirst().getText());
    }


    private void setupVirtualRepo(VirtualRepo virtualRepo, String repoKey) {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create(repoKey, RepoType.Generic);
        when(virtualRepo.getKey()).thenReturn(repoKey);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(virtualRepo.isLocal()).thenReturn(false);
        when(virtualRepo.isReal()).thenReturn(false);
        when(virtualRepo.getVirtualRepositories()).thenReturn(List.of());
        when(virtualRepo.isPathPatternValid(any(), any())).thenReturn(true);
        when(virtualRepo.accepts(any())).thenReturn(true);
    }

    private void setupRemoteRepoForLiveListing(RemoteRepo<?, ?> remoteRepo) {
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getUrl()).thenReturn("http://remote-repo.com");
        when(remoteRepo.getRepoPath(any())).thenReturn(InternalRepoPathFactory.create("remote-repo", ""));
        when(remoteRepo.listRemoteResources(any())).thenReturn(List.of(
                new RemoteItem("permitted-item", false),
                new RemoteItem("restricted-item", false)));
    }
}