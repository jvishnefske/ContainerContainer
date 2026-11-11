package org.artifactory.repo.service.mover;

import ch.qos.logback.classic.Level;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.exception.CancelException;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.service.flexible.tools.MoveCopyTestUtils.prepareCopyOrMoveOverrideMocks;
import static org.artifactory.repo.service.flexible.tools.MoveCopyTestUtils.prepareDbMutableFileMock;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.SKIP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

public class MoverProxyTest extends ArtifactoryHomeBoundTest {

    @Mock
    RepoPath repoPathMock;
    @Mock
    LocalRepo repoMock;
    @Mock
    MutableVfsItem mutableVfsItemMock;
    @Mock
    MoveMultiStatusHolder moveMultiStatusHolderMock;
    @Mock
    SqlStorageSession sqlStorageSessionMock;
    @Mock
    SessionLockEntry sessionLockEntryMock;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "moveCopyFileProvider")
    public void testMoveCopyFile(boolean beforeOperationError, int invokeNumGetMutableFsItem,
            int invokeNumOperationOnFile, int invokeNumSaveSession) {

        BaseRepoPathMover baseRepoPathMover = mock(BaseRepoPathMover.class);
        DbMutableFile dbMutableFile = prepareDbMutableFileMock();
        RepoRepoPath<StoringRepo> repoRepoRepoPath =
                prepareCopyOrMoveOverrideMocks(repoPathMock, repoMock, mutableVfsItemMock, sessionLockEntryMock,
                        sqlStorageSessionMock, moveMultiStatusHolderMock, true);
        MoverProxy moverProxy = new MoverProxy();
        if (beforeOperationError) {
            when(moveMultiStatusHolderMock.getCancelException(null)).thenReturn(mock(CancelException.class));
        }

        moverProxy.moveCopyFile(dbMutableFile, repoRepoRepoPath, baseRepoPathMover, moveMultiStatusHolderMock);

        verify(repoMock, times(invokeNumGetMutableFsItem)).getMutableFsItem(any(RepoPath.class));
        verify(baseRepoPathMover, times(invokeNumOperationOnFile)).operationOnFile(any(), any(), any());
        verify(baseRepoPathMover, times(invokeNumSaveSession)).saveSession();
    }

    @DataProvider
    public Object[][] moveCopyFileProvider() {
        return new Object[][] {
                {false, 1, 1, 1},
                {true, 0, 0, 0}
        };
    }

    @Test
    void testMoveCopyFolder_Allow() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        MoverProxy moverProxy = mock(MoverProxy.class, Answers.CALLS_REAL_METHODS);
        Field field = ReflectionUtils.findField(BaseRepoPathMover.class, "dryRun");
        ReflectionUtils.makeAccessible(field);
        BaseRepoPathMover repoPathMover = mock(BaseRepoPathMover.class);
        when(repoPathMover.checkCopyMove(source, target)).thenReturn(ALLOW);
        ReflectionUtils.setField(field, repoPathMover, true);
        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);

        VfsFolderRepo actual =
                moverProxy.moveCopyFolder(source, target, repoPathMover, status);
        assertNotNull(actual);
        verify(repoPathMover, times(1)).saveSession();
        verify(status, never()).error(any(), any());
    }

    @Test
    void testMoveCopyFolder_Skip() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        MoverProxy moverProxy = mock(MoverProxy.class, Answers.CALLS_REAL_METHODS);
        BaseRepoPathMover repoPathMover = mock(BaseRepoPathMover.class);
        when(repoPathMover.checkCopyMove(source, target)).thenReturn(SKIP);
        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);

        VfsFolderRepo actual =
                moverProxy.moveCopyFolder(source, target, repoPathMover, status);
        assertNull(actual);
        verify(repoPathMover, never()).saveSession();
        verify(status, never()).error(any(), any());
    }

    @Test
    void testMoveCopyFolder_Reject() {
        VfsFolder source = mock(VfsFolder.class);
        when(source.getRepoPath()).thenReturn(new RepoPathImpl("repo", "path"));
        RepoRepoPath<StoringRepo> target = mock(RepoRepoPath.class);
        MoverProxy moverProxy = mock(MoverProxy.class, Answers.CALLS_REAL_METHODS);
        BaseRepoPathMover repoPathMover = mock(BaseRepoPathMover.class);
        when(repoPathMover.checkCopyMove(source, target)).thenReturn(REJECT);
        when(repoPathMover.contains(any())).thenReturn(false);

        MoveMultiStatusHolder status = mock(MoveMultiStatusHolder.class);

        VfsFolderRepo actual =
                moverProxy.moveCopyFolder(source, target, repoPathMover, status);
        assertNull(actual);
        verify(repoPathMover, never()).saveSession();
        verify(status, times(1)).errorWithDifferentLogLevel(
                eq("Cannot create/override the path 'null'. Skipping this path and all its children."),
                eq(SC_BAD_REQUEST), eq(null), any(), eq(Level.WARN));
    }
}