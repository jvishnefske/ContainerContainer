package org.artifactory.repo.service.mover;

import ch.qos.logback.classic.Level;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.db.fs.model.DbMutableFolder;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.service.flexible.tools.MoveCopyTestUtils.prepareCopyOrMoveOverrideMocks;
import static org.artifactory.repo.service.flexible.tools.MoveCopyTestUtils.prepareDbMutableFileMock;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.SKIP;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class BaseRepoPathMoverTest extends ArtifactoryHomeBoundTest {

    @Mock
    RepoPath repoPathMock;
    @Mock
    LocalRepo repoMock;
    @Mock
    MutableVfsItem mutableVfsItemMock;
    @Mock
    SqlStorageSession sqlStorageSessionMock;
    @Mock
    SessionLockEntry sessionLockEntryMock;
    @Mock
    MoveMultiStatusHolder moveMultiStatusHolderMock;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ReplicationAddon replicationAddon;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryService repositoryService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.getAuthorizationService()).thenReturn(authorizationService);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(replicationAddon);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testMoveFile() {
        BaseRepoPathMover baseRepoPathMove = mock(BaseRepoPathMover.class, Mockito.CALLS_REAL_METHODS);
        DbMutableFile dbMutableFile = prepareDbMutableFileMock();
        RepoRepoPath<StoringRepo> repoRepoPath =
                prepareCopyOrMoveOverrideMocks(repoPathMock, repoMock, mutableVfsItemMock, sessionLockEntryMock,
                        sqlStorageSessionMock, moveMultiStatusHolderMock, true);

        baseRepoPathMove.moveFile(dbMutableFile, repoRepoPath, moveMultiStatusHolderMock, true);

        verify(repoMock, times(1)).createOrGetFile(any());
        verify(sqlStorageSessionMock, times(1)).save();
        verify(moveMultiStatusHolderMock, times(1)).artifactMoved();
    }

    /**
     * Depending on the unixStyleBehaviour target dir will contain `source-dir` as a child.
     */
    @SuppressWarnings("unchecked")
    @Test(dataProvider = "unixStyleBehaviourTest")
    public void testMoveDirUnixStyle(boolean unixStyleBehaviour, String targetDirExpectedPath) {
        RepoPathImpl fromRepoPath = new RepoPathImpl("source-repo", "source-dir");
        RepoPathImpl targetRepoPath = new RepoPathImpl("source-repo", ".index");

        FolderInfoImpl srcFolderInfo = new FolderInfoImpl(fromRepoPath);
        DbMutableFolder srcDir = new DbMutableFolder(
                mock(org.artifactory.storage.fs.repo.StoringRepo.class), 52437842367L, srcFolderInfo);

        FolderInfoImpl destFolderInfo = new FolderInfoImpl(targetRepoPath);
        DbMutableFolder destinationDir = new DbMutableFolder(
                mock(org.artifactory.storage.fs.repo.StoringRepo.class), 35865650921L, destFolderInfo);

        final StoringRepo<LocalRepoDescriptor, LocalRepoConfig> repo = mock(StoringRepo.class);
        when(repo.createOrGetFile(any())).thenReturn(mock(MutableVfsFile.class));
        RepoRepoPath<org.artifactory.repo.StoringRepo> targetRepoRepoPath = new RepoRepoPath<>(repo, targetRepoPath);

        when(repo.getImmutableFsItem(targetRepoPath)).thenReturn(destinationDir);

        BaseRepoPathMover baseRepoPathMove = spy(new DefaultRepoPathMover(new MoveMultiStatusHolder(),
                new MoverConfigBuilder(fromRepoPath, targetRepoPath)
                        .dryRun(true)
                        .unixStyleBehavior(unixStyleBehaviour).build()));
        doReturn(ALLOW).when(baseRepoPathMove).checkCopyMove(eq(srcDir), any());
        final ArgumentCaptor<VfsItem> sourceArg = ArgumentCaptor.forClass(VfsItem.class);
        final ArgumentCaptor<RepoRepoPath> targetArg = ArgumentCaptor.forClass(RepoRepoPath.class);
        doNothing().when(baseRepoPathMove).moveCopyRecursive(sourceArg.capture(), targetArg.capture());
        baseRepoPathMove.moveOrCopy(srcDir, targetRepoRepoPath);

        assertEquals(sourceArg.getValue(), srcDir);
        assertEquals(targetArg.getValue().getRepoPath().getPath(), targetDirExpectedPath);
    }

    @DataProvider(name = "unixStyleBehaviourTest")
    public static Object[][] unixStyleBehaviourTest() {
        return new Object[][] {{true, ".index/source-dir"}, {false, ".index"}};
    }

    @Test
    public void testCanMoveWithoutReadPermissionToSourceRepository() {
        RepoPath sourceRepoPath = mock(RepoPath.class);
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(sourceRepoPath);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.canRead(sourceRepoPath)).thenReturn(false);
        when(context.getAuthorizationService()).thenReturn(authorizationService);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(ReplicationAddon.class)).thenReturn(mock(ReplicationAddon.class));
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        BaseRepoPathMover baseRepoPathMove = mock(BaseRepoPathMover.class,
                withSettings().useConstructor(new MoveMultiStatusHolder(), mock(MoverConfig.class)));
        when(baseRepoPathMove.checkCopyMove(any(), any())).thenCallRealMethod();
        Assert.assertEquals(REJECT, baseRepoPathMove.checkCopyMove(item, null));
        Assert.assertEquals(baseRepoPathMove.status.getStatusCode(), HttpStatus.SC_FORBIDDEN);
        assertTrue(baseRepoPathMove.status.getStatusMsg().contains("User doesn't have permissions to read"));
        verify(item, times(1)).getRepoPath();
        verify(authorizationService, times(1)).canRead(sourceRepoPath);
        verify(context, times(1)).getAuthorizationService();
        verifyNoMoreInteractions(item, authorizationService);
    }

    @Test
    public void testCanMoveFromRootSourceWithReadPermissionToRootTargetWithDeployPermission() {
        RepoPath sourceRepoPath = new RepoPathImpl("source-repo", null);
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(sourceRepoPath);

        RepoRepoPath rrp = mock(RepoRepoPath.class);
        RepoPath targetRepoPath = new RepoPathImpl("target-repo", null);
        when(rrp.getRepoPath()).thenReturn(targetRepoPath);
        StoringRepo targetRepo = mock(DbLocalRepo.class);
        when(((LocalRepo<?, ?>) targetRepo).accepts(any())).thenReturn(true);
        when(rrp.getRepo()).thenReturn(targetRepo);

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.canRead(sourceRepoPath)).thenReturn(true);
        when(authorizationService.canDeploy(targetRepoPath)).thenReturn(true);
        when(context.getAuthorizationService()).thenReturn(authorizationService);

        StorageInterceptors storageInterceptors = mock(StorageInterceptors.class);
        when(storageInterceptors.checkCopyMove(any(), any(), any())).thenReturn(ALLOW);
        when(context.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);

        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        when(internalRepositoryService.releaseBundleRepositoryByKey(any())).thenReturn(null);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);

        MoverConfig mc = mock(MoverConfig.class);
        when(mc.isCopy()).thenReturn(true);
        BaseRepoPathMover baseRepoPathMove = mock(BaseRepoPathMover.class,
                withSettings().useConstructor(new MoveMultiStatusHolder(), mc));
        when(baseRepoPathMove.checkCopyMove(any(), any())).thenCallRealMethod();

        assertEquals(ALLOW, baseRepoPathMove.checkCopyMove(item, rrp));
    }

    @Test
    void handleDir_Allow() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        BaseRepoPathMover baseRepoPathMover = mock(BaseRepoPathMover.class, Answers.CALLS_REAL_METHODS);
        doReturn(ALLOW).when(baseRepoPathMover).checkCopyMove(source, target);
        doNothing().when(baseRepoPathMover).saveSession();
        doNothing().when(baseRepoPathMover).afterOperationOnFolder(any(), any(), any());
        doNothing().when(baseRepoPathMover).deleteAndReplicateAfterMoveEvent(any(), anyInt());

        ReflectionTestUtils.setField(baseRepoPathMover, "dryRun", true);
        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);
        ReflectionTestUtils.setField(baseRepoPathMover, "status", status);

        baseRepoPathMover.handleDir(source, target);
        verify(baseRepoPathMover, times(1)).saveSession();
        verify(status, never()).error(any(), any());
    }

    @Test
    void handleDir_Skip() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        BaseRepoPathMover baseRepoPathMover = mock(BaseRepoPathMover.class, Answers.CALLS_REAL_METHODS);
        doReturn(SKIP).when(baseRepoPathMover).checkCopyMove(source, target);
        doNothing().when(baseRepoPathMover).saveSession();

        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);
        ReflectionTestUtils.setField(baseRepoPathMover, "status", status);

        baseRepoPathMover.handleDir(source, target);
        verify(status, never()).error(any(), any());
        verify(baseRepoPathMover, never()).saveSession();
    }

    @Test
    void handleDir_Reject() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        BaseRepoPathMover baseRepoPathMover = mock(BaseRepoPathMover.class, Answers.CALLS_REAL_METHODS);
        doReturn(REJECT).when(baseRepoPathMover).checkCopyMove(source, target);
        doNothing().when(baseRepoPathMover).saveSession();
        doReturn(false).when(baseRepoPathMover).contains(any());

        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);
        ReflectionTestUtils.setField(baseRepoPathMover, "status", status);

        baseRepoPathMover.handleDir(source, target);
        verify(status).errorWithDifferentLogLevel(
                eq("Cannot create/override the path 'null'. Skipping this path and all its children."),
                eq(SC_BAD_REQUEST), eq(null), any(), eq(Level.WARN));
        verify(baseRepoPathMover, never()).saveSession();
    }

    @Test
    void handleDirMultiTx_shouldReturnEarly_whenCheckDirCopyMoveRejects() {
        // Arrange
        VfsFolder source = mock(VfsFolder.class);
        RepoPath sourceRepoPath = new RepoPathImpl("repo", "folder");
        when(source.getRepoPath()).thenReturn(sourceRepoPath);

        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        RepoPath targetRepoPath = new RepoPathImpl("target-repo", "target-folder");
        when(target.getRepoPath()).thenReturn(targetRepoPath);

        StorageInterceptors storageInterceptors = mock(StorageInterceptors.class);
        when(storageInterceptors.checkDirCopyMove(eq(source), eq(targetRepoPath), any())).thenReturn(REJECT);
        when(context.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);

        MoverConfig moverConfig = mock(MoverConfig.class);
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        BaseRepoPathMover baseRepoPathMover = spy(new DefaultRepoPathMover(status, moverConfig));

        ReflectionTestUtils.setField(baseRepoPathMover, "storageInterceptors", storageInterceptors);

        // Act
        baseRepoPathMover.handleDirMultiTx(source, target, java.util.Map.of());

        // Assert
        verify(storageInterceptors, times(1)).checkDirCopyMove(eq(source), eq(targetRepoPath), any());
    }
}