package org.artifactory.repo.service.flexible.tools;

import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.session.StorageSessionHolder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author AndreiK.
 */
public class MoveCopyTestUtils {

    public static RepoRepoPath<org.artifactory.repo.StoringRepo> prepareCopyOrMoveOverrideMocks(RepoPath targetRepoPath,
            LocalRepo repoMock,
            MutableVfsItem mutableVfsItem,
            SessionLockEntry sessionLockEntryMock,
            SqlStorageSession sqlStorageSessionMock,
            MoveMultiStatusHolder moveMultiStatusHolderMock,
            boolean returnTargetItem) {

        when(repoMock.createOrGetFile(any())).thenReturn(mock(MutableVfsFile.class));
        RepoRepoPath<org.artifactory.repo.StoringRepo> targetRepoRepoPath = new RepoRepoPath(repoMock, targetRepoPath);
        when(mutableVfsItem.getRepoPath()).thenReturn(targetRepoPath);
        doNothing().when(mutableVfsItem).delete(any());
        if (returnTargetItem) {
            when(repoMock.getMutableFsItem(any(RepoPath.class))).thenReturn(mutableVfsItem);
        } else {
            when(repoMock.getMutableFsItem(any(RepoPath.class))).thenReturn(null);
        }
        when(sqlStorageSessionMock.getLockEntry(any())).thenReturn(sessionLockEntryMock);
        StorageSessionHolder.setSession(sqlStorageSessionMock);
        doNothing().when(moveMultiStatusHolderMock).error(any(), any());
        return targetRepoRepoPath;
    }

    public static DbMutableFile prepareDbMutableFileMock() {
        RepoPathImpl repoPath = new RepoPathImpl("source-repo", "test.file");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        return new DbMutableFile(mock(StoringRepo.class), 52437842367L, fileInfo);
    }

}
