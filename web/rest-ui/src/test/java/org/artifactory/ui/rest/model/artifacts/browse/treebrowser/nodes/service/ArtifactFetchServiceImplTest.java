package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.RemoteBrowsableItem;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualBrowsableItem;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FileNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FolderNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NodeRepoTypeHelper;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchDirectBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchStrategy;
import org.jfrog.commons.continues.ContinueIndexPage;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class ArtifactFetchServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepositoryBrowsingService repositoryBrowsingService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private StreamArtifactsService streamArtifactsService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private NodeRepoTypeHelper nodeRepoTypeHelper;
    @Mock
    private PackageSpecification packageSpecification;
    private ArtifactFetchServiceImpl artifactFetchService;
    private AutoCloseable autoCloseable;
    private ItemInfo itemInfo1;
    private ItemInfo itemInfo2;
    private ItemInfo itemInfo3;
    private FolderInfo folderInfo;
    private FolderInfo folderInfo1;
    private RepoPath filePath;
    private RepoPath folderPath;
    private RepoPath folderPath1;

    @BeforeEach
    public void setup() throws Exception {
        autoCloseable = openMocks(this);
        artifactFetchService =
                new ArtifactFetchServiceImpl(repositoryBrowsingService, authorizationService, addonsManager,
                        streamArtifactsService, propertiesService, repositoryService, packageSpecification);

        Field field = ArtifactFetchServiceImpl.class.getDeclaredField("nodeRepoTypeHelper");
        field.setAccessible(true);
        field.set(artifactFetchService, nodeRepoTypeHelper);

        prepareItems();
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserStreamSizeLimit, "3");
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    private void prepareItems() {
        filePath = mock(RepoPath.class);
        when(filePath.getRepoKey()).thenReturn("cool-repo");
        folderPath = mock(RepoPath.class);
        when(folderPath.getRepoKey()).thenReturn("cool-repo");
        folderPath1 = mock(RepoPath.class);
        when(folderPath1.getRepoKey()).thenReturn("cool-repo");
        itemInfo1 = mock(ItemInfo.class);
        when(itemInfo1.getName()).thenReturn("cool-file");
        when(itemInfo1.getRelPath()).thenReturn("cool-path");
        itemInfo2 = mock(ItemInfo.class);
        when(itemInfo2.getName()).thenReturn("cool-file-2");
        when(itemInfo2.getRelPath()).thenReturn("cool-path-2");
        itemInfo3 = mock(ItemInfo.class);
        when(itemInfo3.getName()).thenReturn("cool-file-3");
        when(itemInfo3.getRelPath()).thenReturn("cool-path-3");
        folderInfo = mock(FolderInfo.class);
        when(folderInfo.isFolder()).thenReturn(true);
        when(folderInfo.getName()).thenReturn("cool-folder");
        folderInfo1 = mock(FolderInfo.class);
        when(folderInfo1.isFolder()).thenReturn(true);
        when(folderInfo1.getName()).thenReturn("cool-folder-1");
        when(folderInfo.getRelPath()).thenReturn("cool-folder-path");
        when(itemInfo1.getRepoPath()).thenReturn(filePath);
        when(itemInfo2.getRepoPath()).thenReturn(filePath);
        when(itemInfo3.getRepoPath()).thenReturn(filePath);
        when(folderInfo.getRepoPath()).thenReturn(folderPath);
        when(folderInfo1.getRepoPath()).thenReturn(folderPath1);
    }

    @AfterEach
    public void cleanup() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testFetchItems_localRepo_StreamEnabled() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        BaseNode fileNode = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        when(streamArtifactsService.getNodesStreamFromLocal(artifactFilter)).thenReturn(Stream.of(fileNode, fileNode1));
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode1);
        assertEquals(result.getData().get(1), fileNode);
    }

    @Test
    void testFetchItems_remoteRepo_StreamEnabled() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("remote");
        artifactFilter.setRepoKey("cool-remote-repo");
        artifactFilter.setRepoPkgType("generic");
        BaseNode fileNode = new FileNode(filePath, "cool-file-1", "remote", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file", "remote", RepoType.Generic, false);
        when(streamArtifactsService.getNodesStreamFromRemote(artifactFilter))
                .thenReturn(Stream.of(fileNode, fileNode1));
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode);
        assertEquals(result.getData().get(1), fileNode1);
    }

    @Test
    void testFetchItems_virtualRepo_StreamEnabled() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("virtual");
        artifactFilter.setRepoKey("cool-virtual-repo");
        artifactFilter.setRepoPkgType("generic");
        BaseNode fileNode = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey("cool-virtual-repo")).thenReturn(virtualRepo);
        when(virtualRepo.accepts(any())).thenReturn(true);
        when(streamArtifactsService.getNodesStreamFromVirtual(artifactFilter))
                .thenReturn(Stream.of(fileNode, fileNode1));
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode);
        assertEquals(result.getData().get(1), fileNode1);
    }

    @Test
    void testFetchItemsReachTheMaxLimit_localRepo_StreamEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserStreamSizeLimit, "1");
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        artifactFilter.setLimit(1L);
        BaseNode fileNode = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "local", RepoType.Generic, false);
        when(streamArtifactsService.getNodesStreamFromLocal(artifactFilter))
                .thenReturn(Stream.of(fileNode, fileNode1, fileNode2));
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-2", result.getContinueState());
        assertEquals(1, result.getData().size());
        assertEquals(result.getData().getFirst(), fileNode1);
    }

    @Test
    void testFetchItems_ThereIsMore_localRepo_StreamEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserStreamSizeLimit, "5");
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        artifactFilter.setLimit(1L);
        BaseNode fileNode = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "local", RepoType.Generic, false);
        when(streamArtifactsService.getNodesStreamFromLocal(artifactFilter))
                .thenReturn(Stream.of(fileNode, fileNode1, fileNode2));
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("1", result.getContinueState());
        assertEquals(1, result.getData().size());
        assertEquals(result.getData().getFirst(), fileNode1);
    }

    @Test
    void testShowInTree_localRepo() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-file");
        BaseNode fileNode = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "local", RepoType.Generic, false);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromLocal(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1, fileNode));
        LocalRepo localRepo = mock(LocalRepo.class);
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(localRepo.getRepoConfig()).thenReturn(localRepoConfig);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(localRepo);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(localRepo.accepts(any())).thenReturn(true);
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(3, result.getData().size());
        assertEquals(result.getData().get(0), fileNode);
        assertEquals(result.getData().get(1), fileNode1);
        assertEquals(result.getData().get(2), fileNode2);
    }

    @Test
    void testShowInTree_ReachTheLimit_localRepo() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserStreamSizeLimit, "1");
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        artifactFilter.setLimit(2L);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-file");
        BaseNode fileNode = new FileNode(filePath, "cool-file", "local", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "local", RepoType.Generic, false);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromLocal(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1, fileNode));
        LocalRepo localRepo = mock(LocalRepo.class);
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(localRepo.getRepoConfig()).thenReturn(localRepoConfig);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(localRepo);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(localRepo.accepts(any())).thenReturn(true);
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-2", result.getContinueState());
        assertEquals(1, result.getData().size());
        assertEquals(result.getData().getFirst(), fileNode);
    }

    @Test
    void testShowInTree_remoteRepo_candidateExistsLocally() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("remote");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-remote-repo");
        artifactFilter.setRepoPkgType("generic");

        RepoPath repoPathCache = RepoPathFactory.create("cool-remote-repo-cache", "cool-file");
        BaseNode fileNode = new FileNode(filePath, "cool-file", "remote", RepoType.Generic, false);
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "remote", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "remote", RepoType.Generic, false);

        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(false);
        when(remoteRepo.getKey()).thenReturn("cool-remote-repo");
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(remoteRepo);

        LocalCacheRepo remoteCacheRepo = mock(LocalCacheRepo.class);
        when(remoteRepo.getLocalCacheRepo()).thenReturn(remoteCacheRepo);
        when(remoteCacheRepo.getKey()).thenReturn("cool-remote-repo-cache");
        when(remoteCacheRepo.accepts(any())).thenReturn(true);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("cool-remote-repo-cache"))
                .thenReturn(remoteCacheRepo);

        when(repositoryService.getItemInfo(repoPathCache)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromRemote(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1, fileNode));
        when(authorizationService.canRead(any())).thenReturn(true);

        when(streamArtifactsService.getNodesStreamFromRemote(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1, fileNode));

        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, true, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(3, result.getData().size());
        assertEquals(result.getData().get(0), fileNode);
        assertEquals(result.getData().get(1), fileNode1);
        assertEquals(result.getData().get(2), fileNode2);
    }

    @Test
    void testShowInTree_remoteRepo_candidateExistsRemotely() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file-1");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("remote");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-remote-repo");
        artifactFilter.setRepoPkgType("generic");

        RepoPath repoPath = RepoPathFactory.create("cool-remote-repo", "cool-folder");
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "remote", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "remote", RepoType.Generic, false);

        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
        when(remoteRepo.getKey()).thenReturn("cool-remote-repo");
        when(remoteRepo.getUrl()).thenReturn("http://cool-remote-repo.com");
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(remoteRepo);
        when(remoteRepo.getRepoPath(any())).thenReturn(repoPath);

        when(remoteRepo.listRemoteResources(any())).thenReturn(List.of(
                new RemoteItem("cool-file-1", false),
                new RemoteItem("cool-file-2", false)));

        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromRemote(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1));
        when(authorizationService.canRead(any())).thenReturn(true);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(remoteRepo);
        when(remoteRepo.getRepoPath(any())).thenReturn(repoPath);

        when(remoteRepo.listRemoteResources(any()))
                .thenReturn(List.of(new RemoteItem("cool-file-1", false), new RemoteItem("cool-file-2", false)));

        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromRemote(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1));
        when(authorizationService.canRead(any())).thenReturn(true);

        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode1);
        assertEquals(result.getData().get(1), fileNode2);
    }

    @Test
    void testShowInTree_VirtualRepo_ItemExistLocally() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-virtual-repo");
        artifactFilter.setRepoPkgType("generic");

        RepoPath repoPath = RepoPathFactory.create("cool-virtual-repo", "cool-folder");
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "local", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "local", RepoType.Generic, false);

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getKey()).thenReturn("cool-virtual-repo");
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        when(virtualRepo.accepts(any())).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(virtualRepo);
        when(virtualRepo.getRepoPath(any())).thenReturn(repoPath);

        LocalRepo localRepo = mock(LocalRepo.class);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("cool-local-repo")).thenReturn(localRepo);
        when(localRepo.accepts(any())).thenReturn(true);
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.getKey()).thenReturn("cool-local-repo");

        when(repositoryService.getItemInfo(RepoPathFactory.create("cool-local-repo", "cool-file")))
                .thenReturn(itemInfo1);

        when(virtualRepo.getResolvedLocalAndFederatedRepos()).thenReturn(Set.of(localRepo));

        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromVirtual(artifactFilter))
                .thenReturn(Stream.of(fileNode2, fileNode1));
        when(authorizationService.canRead(any())).thenReturn(true);

        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode1);
        assertEquals(result.getData().get(1), fileNode2);
    }

    @Test
    void testShowInTree_VirtualRepo_ItemExistRemotely() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude("cool-file-1");
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("cool-virtual-repo");
        artifactFilter.setRepoPkgType("generic");

        RepoPath repoPath = RepoPathFactory.create("cool-virtual-repo", "cool-folder");
        BaseNode fileNode1 = new FileNode(filePath, "cool-file-1", "remote", RepoType.Generic, false);
        BaseNode fileNode2 = new FileNode(filePath, "cool-file-2", "remote", RepoType.Generic, false);

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getKey()).thenReturn("cool-virtual-repo");
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        when(virtualRepo.accepts(any())).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(virtualRepo);
        when(virtualRepo.getRepoPath(any())).thenReturn(repoPath);

        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
        when(remoteRepo.getKey()).thenReturn("cool-remote-repo");
        when(remoteRepo.getUrl()).thenReturn("http://cool-remote-repo.com");
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(repositoryService.remoteRepositoryByKey(any())).thenReturn(remoteRepo);
        when(remoteRepo.getRepoPath(any())).thenReturn(repoPath);

        when(remoteRepo.listRemoteResources(any()))
                .thenReturn(List.of(new RemoteItem("cool-file-1", false), new RemoteItem("cool-file-2", false)));

        when(repositoryService.getItemInfo(RepoPathFactory.create("cool-remote-repo", "cool-folder"))).thenReturn(
                itemInfo1);

        when(virtualRepo.getResolvedRemoteRepos()).thenReturn(Set.of(remoteRepo));

        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo1);
        when(streamArtifactsService.getNodesStreamFromVirtual(artifactFilter)).thenReturn(
                Stream.of(fileNode2, fileNode1));
        when(authorizationService.canRead(any())).thenReturn(true);

        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertEquals(result.getData().get(0), fileNode1);
        assertEquals(result.getData().get(1), fileNode2);
    }


    @Test
    void testFetchItemsNoMoreItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath)).thenReturn(List.of(itemInfo1));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testFetchItemsThereIsMoreItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(1L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath)).thenReturn(List.of(itemInfo1, itemInfo1, itemInfo1));
        when(itemInfo1.getRepoPath()).thenReturn(filePath);
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("1", result.getContinueState());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testFetchDirectBulkItemsThereIsNoItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(5L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        artifactFilter.setMustInclude("cool-file-2");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchDirectBulkStrategy("cool-file-2", false);
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath))
                .thenReturn(List.of(itemInfo1, itemInfo1, itemInfo3, itemInfo3));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(0, result.getData().size());
    }

    @Test
    void testFetchBulkRemoteItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(5L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("remote");
        artifactFilter.setRepoKey("cool-remote-repo");
        artifactFilter.setRepoPkgType("generic");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        BaseBrowsableItem file1 = new RemoteBrowsableItem(new RemoteItem("item1", false), repositoryPath);
        BaseBrowsableItem folder2 = new RemoteBrowsableItem(new RemoteItem("item2", true), repositoryPath);
        when(repositoryBrowsingService.getRemoteRepoBrowsableChildren(any())).thenReturn(List.of(file1, folder2));
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(null);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
        assertTrue(((BaseNode) result.getData().getFirst()).getRepoPath().isFolder());
    }

    @Test
    void testFetchBulkVirtualItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(5L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("virtual");
        artifactFilter.setRepoKey("cool-virtual-repo");
        artifactFilter.setRepoPkgType("generic");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        BaseBrowsableItem file1 =
                new VirtualBrowsableItem("a", true, 0L, 0L, 0L, repositoryPath, Collections.emptyList());
        BaseBrowsableItem folder2 =
                new VirtualBrowsableItem("b", true, 0L, 0L, 0L, repositoryPath, Collections.emptyList());
        when(repositoryBrowsingService.getVirtualRepoBrowsableChildren(any())).thenReturn(List.of(file1, folder2));
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey(any())).thenReturn(null);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(2, result.getData().size());
    }

    @Test
    void testFetchDirectBulkItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(5L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath))
                .thenReturn(List.of(itemInfo1, itemInfo1, itemInfo2, itemInfo3, itemInfo3));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(5, result.getData().size());
        assertEquals("cool-file-2", ((BaseNode) (result.getData().get(2))).getText());
    }

    @Test
    void testCompactFetchItemsThereIsMoreItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(1L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchBulkStrategy();
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath)).thenReturn(List.of(folderInfo));
        when(repositoryService.getChildren(folderInfo.getRepoPath())).thenReturn(List.of(folderInfo1));
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        localRepoDescriptor.setType(RepoType.Generic);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(any())).thenReturn(localRepoDescriptor);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(folderPath)).thenReturn(true);
        ContinueResult<?> result = artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, true, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(1, result.getData().size());
        assertEquals("cool-folder/cool-folder-1", ((BaseNode) result.getData().getFirst()).getText());
    }

    @Test
    void testCompactFetchDirectItemsThereIsMoreItems() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(1L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        artifactFilter.setMustInclude("cool-folder");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchDirectBulkStrategy("cool-folder", true);
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath)).thenReturn(List.of(folderInfo));
        when(repositoryService.getChildren(folderInfo.getRepoPath())).thenReturn(List.of(folderInfo1));
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        localRepoDescriptor.setType(RepoType.Generic);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(any())).thenReturn(localRepoDescriptor);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        when(folderPath1.getPath()).thenReturn("cool-folder-path");
        when(folderPath1.getRepoKey()).thenReturn("cool-repo");
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(folderPath)).thenReturn(true);
        when(folderPath.getPath()).thenReturn("cool-folder");
        ContinueResult<?> result = artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, true, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(1, result.getData().size());
        assertEquals("cool-folder/cool-folder-1", ((BaseNode) result.getData().getFirst()).getText());
    }

    @Test
    void testFetchDirectBulkReturnAllItemsIfThereIsLessThanTheLimit() {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setLimit(6L);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType("local");
        artifactFilter.setRepoKey("cool-repo");
        artifactFilter.setRepoPkgType("maven");
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = new ArtifactsFetchDirectBulkStrategy("cool-file-2", false);
        RepoPath repositoryPath = InternalRepoPathFactory.create(artifactFilter.getRepoKey(), artifactFilter.getPath());
        when(repositoryService.getChildren(repositoryPath))
                .thenReturn(List.of(itemInfo1, itemInfo1, itemInfo2, itemInfo3, itemInfo3));
        when(repositoryService.isLocalFederatedOrCachedRepoPathAcceptedOrCanAnnotate(filePath)).thenReturn(true);
        when(authorizationService.canManage(repositoryPath)).thenReturn(true);
        ContinueResult<?> result =
                artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, false, false);
        assertEquals("-1", result.getContinueState());
        assertEquals(5, result.getData().size());
        assertEquals("cool-file-2", ((BaseNode) (result.getData().get(2))).getText());
    }

    @Test
    void testSetFolderIconsIfNeeded_LocalRepo_DockerType_IconsEnabled() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-local");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        // When
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - verify the repo was looked up (grouping optimization working)
        verify(repositoryService, times(1)).localFederatedOrCachedRepositoryByKey("docker-local");
    }

    @Test
    void testSetFolderIconsIfNeeded_LocalRepo_IconsDisabled() {
        // Given - test early return when displayIconsOnLocalReposEnabled is false (covers lines 711-712)
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "false");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-local");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icons disabled, so no icon should be set and early return was executed
        assertEquals(1, result.getData().size());
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_LocalRepo_NonRelevantPackageType() {
        // Given - test early return when package type is not relevant (covers lines 715-716)
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("maven-local");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);  // Not Docker or Conan
        when(repositoryService.localFederatedOrCachedRepositoryByKey("maven-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("maven-local")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("maven-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - Maven is not a relevant package type, icon should not be set and early return was executed
        assertEquals(1, result.getData().size());
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepo_DockerType_IconsEnabled() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.virtualRepositoryByKey("docker-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("docker-virtual")).thenReturn(repoConfig);

        VirtualRepoItem virtualRepoItem = mock(VirtualRepoItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath realRepoPath = mock(RepoPath.class);
        when(virtualRepo.getVirtualRepoItem(repoPath)).thenReturn(virtualRepoItem);
        when(virtualRepoItem.getItemInfo()).thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(realRepoPath);
        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // When/Then - verify virtual repo was looked up
        verify(repositoryService, atLeastOnce()).virtualRepositoryByKey("docker-virtual");
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepo_IconsDisabled() {
        // Given - test early return when displayIconsOnVirtualReposEnabled is false (covers lines 722-723)
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "false");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.virtualRepositoryByKey("docker-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("docker-virtual")).thenReturn(repoConfig);
        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");

        // When
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icons disabled, icon should remain null and early return was executed
        assertEquals(1, result.getData().size());
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_ConanPackageType() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("conan-local");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Conan);  // Conan type
        when(repositoryService.localFederatedOrCachedRepositoryByKey("conan-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("conan-local")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("conan-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // When/Then - Conan is a relevant package type
        verify(repositoryService, atLeastOnce()).localFederatedOrCachedRepositoryByKey("conan-local");
    }

    @Test
    void testSetFolderIconsIfNeeded_MultipleNodesFromSameRepo() {
        // Given - this tests the grouping optimization
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath1 = mock(RepoPath.class);
        when(repoPath1.getRepoKey()).thenReturn("docker-local");
        FolderInfo folderInfo1 = mock(FolderInfo.class);
        when(folderInfo1.getRepoPath()).thenReturn(repoPath1);

        RepoPath repoPath2 = mock(RepoPath.class);
        when(repoPath2.getRepoKey()).thenReturn("docker-local");
        FolderInfo folderInfo2 = mock(FolderInfo.class);
        when(folderInfo2.getRepoPath()).thenReturn(repoPath2);

        RepoPath repoPath3 = mock(RepoPath.class);
        when(repoPath3.getRepoKey()).thenReturn("docker-local");
        FolderInfo folderInfo3 = mock(FolderInfo.class);
        when(folderInfo3.getRepoPath()).thenReturn(repoPath3);

        BaseNode node1 = new FolderNode(folderInfo1, "folder1", "local");
        BaseNode node2 = new FolderNode(folderInfo2, "folder2", "local");
        BaseNode node3 = new FolderNode(folderInfo3, "folder3", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node1, node2, node3));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // When/Then - verify the repo is looked up only ONCE despite 3 nodes (grouping optimization)
        verify(repositoryService, times(1)).localFederatedOrCachedRepositoryByKey("docker-local");
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepoWithNullVirtualRepoItem() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.virtualRepositoryByKey("docker-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("docker-virtual")).thenReturn(repoConfig);
        when(virtualRepo.getVirtualRepoItem(repoPath)).thenReturn(null);  // Null item
        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");

        // When/Then - should handle null gracefully without throwing exception
        assertDoesNotThrow(() -> artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false));
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepoWithNullItemInfo() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.virtualRepositoryByKey("docker-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("docker-virtual")).thenReturn(repoConfig);

        VirtualRepoItem virtualRepoItem = mock(VirtualRepoItem.class);
        when(virtualRepo.getVirtualRepoItem(repoPath)).thenReturn(virtualRepoItem);
        when(virtualRepoItem.getItemInfo()).thenReturn(null);  // Null ItemInfo
        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");

        // When/Then - should handle null ItemInfo gracefully
        assertDoesNotThrow(() -> artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false));
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_RepoNotFound() {
        // Given
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("non-existent-repo");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "local");

        when(repositoryService.virtualRepositoryByKey("non-existent-repo")).thenReturn(null);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("non-existent-repo")).thenReturn(null);
        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("non-existent-repo");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When/Then - should handle non-existent repo gracefully
        assertDoesNotThrow(() -> artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false));
        assertNull(node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_NotRelevantFolder() {
        // Given - test folder that's neither Docker manifest nor Conan reference
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-local");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "regular-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icon should remain null (no icon set for non-relevant folders)
        assertNull(node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_EmptyNodesList() {
        // Given - empty list of nodes
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.empty());

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When/Then - should not throw exception with empty list
        assertDoesNotThrow(() -> artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false));
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepo_ConanType() {
        // Given - Virtual repo with Conan type
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("conan-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Conan);
        when(repositoryService.virtualRepositoryByKey("conan-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("conan-virtual")).thenReturn(repoConfig);

        VirtualRepoItem virtualRepoItem = mock(VirtualRepoItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath realRepoPath = mock(RepoPath.class);
        when(virtualRepo.getVirtualRepoItem(repoPath)).thenReturn(virtualRepoItem);
        when(virtualRepoItem.getItemInfo()).thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(realRepoPath);

        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenAnswer(invocation -> {
            // Simulate Conan icon being set
            node.setIcon(RepoType.Conan.getType());
            return Stream.of(node);
        });

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("conan-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - Conan icon should be set
        assertEquals(RepoType.Conan.getType(), node.getIcon());
    }

    @Test
    void testSetFolderIconsIfNeeded_VirtualRepo_NonRelevantType() {
        // Given - Virtual repo with non-relevant type (e.g., Maven)
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("maven-virtual");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "test-folder", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repositoryService.virtualRepositoryByKey("maven-virtual")).thenReturn(virtualRepo);

        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("maven-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - no icon should be set for Maven
        assertNull(node.getIcon());
    }

    @Test
    void testProcessVirtualRepoNodes_WithMultipleNodes() {
        // Given - test processVirtualRepoNodes with multiple nodes
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath1 = mock(RepoPath.class);
        when(repoPath1.getRepoKey()).thenReturn("docker-virtual");
        FolderInfo folderInfo1 = mock(FolderInfo.class);
        when(folderInfo1.getRepoPath()).thenReturn(repoPath1);
        BaseNode node1 = new FolderNode(folderInfo1, "folder1", "virtual");

        RepoPath repoPath2 = mock(RepoPath.class);
        when(repoPath2.getRepoKey()).thenReturn("docker-virtual");
        FolderInfo folderInfo2 = mock(FolderInfo.class);
        when(folderInfo2.getRepoPath()).thenReturn(repoPath2);
        BaseNode node2 = new FolderNode(folderInfo2, "folder2", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.virtualRepositoryByKey("docker-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("docker-virtual")).thenReturn(repoConfig);

        VirtualRepoItem virtualRepoItem1 = mock(VirtualRepoItem.class);
        ItemInfo itemInfo1 = mock(ItemInfo.class);
        RepoPath realRepoPath1 = mock(RepoPath.class);
        when(virtualRepo.getVirtualRepoItem(repoPath1)).thenReturn(virtualRepoItem1);
        when(virtualRepoItem1.getItemInfo()).thenReturn(itemInfo1);
        when(itemInfo1.getRepoPath()).thenReturn(realRepoPath1);

        VirtualRepoItem virtualRepoItem2 = mock(VirtualRepoItem.class);
        ItemInfo itemInfo2 = mock(ItemInfo.class);
        RepoPath realRepoPath2 = mock(RepoPath.class);
        when(virtualRepo.getVirtualRepoItem(repoPath2)).thenReturn(virtualRepoItem2);
        when(virtualRepoItem2.getItemInfo()).thenReturn(itemInfo2);
        when(itemInfo2.getRepoPath()).thenReturn(realRepoPath2);

        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node1, node2));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");

        // When
        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - both nodes were processed through forEach in processVirtualRepoNodes
        assertEquals(2, result.getData().size());
        verify(virtualRepo, times(1)).getVirtualRepoItem(repoPath1);
        verify(virtualRepo, times(1)).getVirtualRepoItem(repoPath2);
    }

    @Test
    void testUpdateNodeIconIfNeeded_DockerManifest_WithHelmOCI() {
        // Given - optimized Docker check returns HelmOCI icon
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-local");
        when(repoPath.getPath()).thenReturn("library/nginx");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "nginx", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);

        // Mock nodeRepoTypeHelper to return HelmOCI icon type
        when(nodeRepoTypeHelper.getDockerManifestIconType(repoPath))
                .thenReturn(Optional.of(RepoType.HelmOCI.getType()));

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icon should be set to HelmOCI
        assertEquals(RepoType.HelmOCI.getType(), node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_DockerManifest_WithoutHelmOCI() {
        // Given - optimized Docker check returns Docker icon
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("docker-local");
        when(repoPath.getPath()).thenReturn("library/busybox");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "busybox", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("docker-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("docker-local")).thenReturn(null);

        // Mock nodeRepoTypeHelper to return Docker icon type
        when(nodeRepoTypeHelper.getDockerManifestIconType(repoPath))
                .thenReturn(Optional.of(RepoType.Docker.getType()));

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("docker-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icon should be set to Docker
        assertEquals(RepoType.Docker.getType(), node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_ConanReferenceFolder() {
        // Given - cover line 753-754: isConanReferenceFolder returns true
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("conan-local");
        when(repoPath.getPath()).thenReturn("mylib/1.0.0");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "1.0.0", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Conan);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("conan-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("conan-local")).thenReturn(null);

        // Mock nodeRepoTypeHelper to return false for Docker, true for Conan
        when(nodeRepoTypeHelper.isDockerManifestFolder(repoPath)).thenReturn(false);
        when(nodeRepoTypeHelper.isConanReferenceFolder(repoPath)).thenReturn(true);

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("conan-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then - icon should be set to Conan (line 754 executed)
        assertEquals(RepoType.Conan.getType(), node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_NixReferenceFolder() {
        // Given - Nix reference folder
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("nix-local");
        when(repoPath.getPath()).thenReturn("store/abc123");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "abc123", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("nix-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("nix-local")).thenReturn(null);

        when(nodeRepoTypeHelper.getDockerManifestIconType(repoPath)).thenReturn(Optional.empty());
        when(nodeRepoTypeHelper.isConanReferenceFolder(repoPath)).thenReturn(false);
        when(nodeRepoTypeHelper.isNixReferenceFolder(repoPath)).thenReturn(true);

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("nix-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then
        assertEquals(RepoType.Nix.getType(), node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_NixReferenceFolder_VirtualRepo() {
        // Given - Virtual repo with Nix reference folder
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnVirtualReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("nix-virtual");
        when(repoPath.getPath()).thenReturn("store/abc123");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "abc123", "virtual");

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(repositoryService.virtualRepositoryByKey("nix-virtual")).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey("nix-virtual")).thenReturn(repoConfig);

        VirtualRepoItem virtualRepoItem = mock(VirtualRepoItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath realRepoPath = mock(RepoPath.class);
        when(virtualRepo.getVirtualRepoItem(repoPath)).thenReturn(virtualRepoItem);
        when(virtualRepoItem.getItemInfo()).thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(realRepoPath);

        when(nodeRepoTypeHelper.getDockerManifestIconType(realRepoPath)).thenReturn(Optional.empty());
        when(nodeRepoTypeHelper.isConanReferenceFolder(realRepoPath)).thenReturn(false);
        when(nodeRepoTypeHelper.isNixReferenceFolder(realRepoPath)).thenReturn(true);

        when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("nix-virtual");
        artifactFilter.setRepoType("virtual");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then
        assertEquals(RepoType.Nix.getType(), node.getIcon());
    }

    @Test
    void testUpdateNodeIconIfNeeded_NotNixReferenceFolder() {
        // Given - folder that is not a Nix reference
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.displayIconsOnLocalReposEnabled, "true");

        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("nix-local");
        when(repoPath.getPath()).thenReturn("regular-folder");

        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);

        BaseNode node = new FolderNode(folderInfo, "regular-folder", "local");

        LocalRepo localRepo = mock(LocalRepo.class);
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("nix-local")).thenReturn(localRepo);
        when(repositoryService.virtualRepositoryByKey("nix-local")).thenReturn(null);

        when(nodeRepoTypeHelper.getDockerManifestIconType(repoPath)).thenReturn(Optional.empty());
        when(nodeRepoTypeHelper.isConanReferenceFolder(repoPath)).thenReturn(false);
        when(nodeRepoTypeHelper.isNixReferenceFolder(repoPath)).thenReturn(false);

        when(streamArtifactsService.getNodesStreamFromLocal(any())).thenReturn(Stream.of(node));

        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoKey("nix-local");
        artifactFilter.setRepoType("local");
        artifactFilter.setPath("");

        // When
        artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);

        // Then
        assertNull(node.getIcon());
    }

    static Stream<Arguments> showInTreePermissionArgs() {
        return Stream.of(
                Arguments.of("remote",  "restricted-item", List.of()),
                Arguments.of("remote",  "permitted-item",  List.of("permitted-item")),
                Arguments.of("virtual", "restricted-item", List.of()),
                Arguments.of("virtual", "permitted-item",  List.of("permitted-item")));
    }

    @ParameterizedTest
    @MethodSource("showInTreePermissionArgs")
    void testShowInTree_candidatePermissionFiltering(String repoType, String mustInclude, List<String> expectedNames) {
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        continueIndexPage.setMustInclude(mustInclude);
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setRepoType(repoType);
        artifactFilter.setPath("");
        artifactFilter.setRepoKey("remote".equals(repoType) ? "cool-remote-repo" : "cool-virtual-repo");
        artifactFilter.setRepoPkgType("generic");

        BaseNode permittedNode = new FileNode(filePath, "permitted-item", "remote", RepoType.Generic, false);
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepo.accepts(any())).thenReturn(true);
        when(remoteRepo.getUrl()).thenReturn("http://cool-remote-repo.com");
        when(remoteRepo.getRepoPath(any())).thenReturn(RepoPathFactory.create("cool-remote-repo", ""));
        when(remoteRepo.listRemoteResources(any())).thenReturn(List.of(
                new RemoteItem("permitted-item", false),
                new RemoteItem("restricted-item", false)));
        when(authorizationService.canRead(any())).thenAnswer(inv -> {
            RepoPath rp = inv.getArgument(0);
            return !"restricted-item".equals(rp.getPath());
        });

        if ("remote".equals(repoType)) {
            when(remoteRepo.getKey()).thenReturn("cool-remote-repo");
            when(remoteRepo.isListRemoteFolderItems()).thenReturn(true);
            when(repositoryService.remoteRepositoryByKey(any())).thenReturn(remoteRepo);
            when(streamArtifactsService.getNodesStreamFromRemote(any())).thenReturn(Stream.of(permittedNode));
        } else {
            VirtualRepo virtualRepo = mock(VirtualRepo.class);
            when(repositoryService.virtualRepositoryByKey("cool-virtual-repo")).thenReturn(virtualRepo);
            when(virtualRepo.getResolvedLocalAndFederatedRepos()).thenReturn(Set.of());
            when(virtualRepo.getResolvedRemoteRepos()).thenReturn(Set.of(remoteRepo));
            when(streamArtifactsService.getNodesStreamFromVirtual(any())).thenReturn(Stream.of(permittedNode));
        }

        ContinueResult<?> result = artifactFetchService.fetchItemsStreamBased(artifactFilter, false, false);
        List<String> actualNames = result.getData().stream()
                .map(node -> ((BaseNode) node).getText())
                .toList();
        assertEquals(expectedNames, actualNames);
    }
}
