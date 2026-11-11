package org.artifactory.storage.db.fs.model;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbMutableFolderTest {

    private InfoFactory infoFactory;
    private StoringRepo storingRepo;
    private FolderInfoImpl folderInfo;
    private RepoPath folderRepoPath;

    @BeforeEach
    void setUp() {
        infoFactory = mock(InfoFactory.class);
        folderRepoPath = new RepoPathImpl("test-repo", "test/folder");
        folderInfo = new FolderInfoImpl(folderRepoPath, 1L);
        when(infoFactory.copyFolderInfo(folderInfo)).thenReturn(folderInfo);
        storingRepo = mock(StoringRepo.class);
    }

    @Test
    void deleteChildren_whenSkipChildrenDeleteIsTrueAndNoChildren() {
        when(storingRepo.getMutableChildren(any())).thenReturn(Collections.emptyList());
        try (MockedStatic<InfoFactoryHolder> infoFactoryHolderMockedStatic = mockStatic(InfoFactoryHolder.class)) {
            infoFactoryHolderMockedStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
            DbMutableFolder folder = new DbMutableFolder(storingRepo, 1L, folderInfo);
            DbMutableFolder spiedFolder = spy(folder);
            doReturn(folderRepoPath).when(spiedFolder).getRepoPath();
            DeleteContext deleteContext = new DeleteContext(folderRepoPath).isExpectEmpty(true);
            spiedFolder.deleteChildren(deleteContext);
        }
    }

    @Test
    void deleteChildren_shouldDeleteChildren() {
        DbMutableFolder child1 = mock(DbMutableFolder.class);
        DbMutableFolder child2 = mock(DbMutableFolder.class);
        List<MutableVfsItem> children = List.of(child1, child2);
        when(storingRepo.getMutableChildren(any())).thenReturn(children);
        doNothing().when(child1).delete(any());
        doNothing().when(child2).delete(any());
        try (MockedStatic<InfoFactoryHolder> infoFactoryHolderMockedStatic = mockStatic(InfoFactoryHolder.class)) {
            infoFactoryHolderMockedStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
            DbMutableFolder folder = new DbMutableFolder(storingRepo, 1L, folderInfo);
            DbMutableFolder spiedFolder = spy(folder);
            doReturn(folderRepoPath).when(spiedFolder).getRepoPath();
            DeleteContext deleteContext = new DeleteContext(folderRepoPath).isExpectEmpty(false);

            spiedFolder.deleteChildren(deleteContext);
            verify(child1, times(1)).delete(deleteContext);
            verify(child2, times(1)).delete(deleteContext);
        }
    }

    @Test
    void deleteChildren_shouldSkipDeletion() {
        DbMutableFolder child1 = mock(DbMutableFolder.class);
        DbMutableFolder child2 = mock(DbMutableFolder.class);
        RepoPath child1Path = new RepoPathImpl("test-repo", "test/folder/child1");
        RepoPath child2Path = new RepoPathImpl("test-repo", "test/folder/child2");
        when(child1.getRepoPath()).thenReturn(child1Path);
        when(child2.getRepoPath()).thenReturn(child2Path);
        List<MutableVfsItem> children = List.of(child1, child2);
        when(storingRepo.getMutableChildren(any())).thenReturn(children);
        try (MockedStatic<InfoFactoryHolder> infoFactoryHolderMockedStatic = mockStatic(InfoFactoryHolder.class)) {
            infoFactoryHolderMockedStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
            DbMutableFolder folder = new DbMutableFolder(storingRepo, 1L, folderInfo);
            DbMutableFolder spiedFolder = spy(folder);
            doReturn(folderRepoPath).when(spiedFolder).getRepoPath();
            DeleteContext deleteContext = new DeleteContext(folderRepoPath).isExpectEmpty(true);
            assertThrows(IllegalStateException.class, () -> spiedFolder.deleteChildren(deleteContext));
            verify(child1, never()).delete(any());
            verify(child2, never()).delete(any());
        }
    }

    @Test
    void deleteUsesProvidedDeleteContextForCanDelete() {
        DeleteContext deleteContext = new DeleteContext(folderRepoPath);
        try (MockedStatic<InfoFactoryHolder> infoFactoryHolderMockedStatic = mockStatic(InfoFactoryHolder.class)) {
            infoFactoryHolderMockedStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
            DbMutableFolder folder = new DbMutableFolder(storingRepo, 1L, folderInfo);
            DbMutableFolder spiedFolder = spy(folder);
            doReturn(false).when(spiedFolder).isNew();
            doReturn(false).when(spiedFolder).canDelete(deleteContext);

            spiedFolder.delete(deleteContext, new BasicStatusHolder());

            verify(spiedFolder).canDelete(deleteContext);
            verify(spiedFolder, never()).deleteChildren(any());
        }
    }
}
