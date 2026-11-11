package org.artifactory.repo.service.flexible.listeners;

import ch.qos.logback.classic.Level;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageMoveCopyListenerTest {

    private StorageMoveCopyListener listener;

    @Mock
    private StorageInterceptors storageInterceptors;

    @Mock
    private MoveCopyContext context;

    @Mock
    private MoveMultiStatusHolder status;

    @Mock
    private MoveCopyItemInfo moveCopyItemInfo;

    @Mock
    private VfsItem<ItemInfo> sourceItem;

    @Mock
    private VfsItem<ItemInfo> targetItem;

    @Mock
    private MutableVfsItem<MutableItemInfo> mutableTargetItem;

    @Mock
    private FileInfo fileInfo;

    @Mock
    private ItemInfo folderInfo;

    @Mock
    private RepoPath targetRepoPath;

    @Mock
    private ChecksumsInfo checksumsInfo;

    @BeforeEach
    void setUp() {
        listener = new StorageMoveCopyListener(storageInterceptors);

        when(moveCopyItemInfo.getMutableTargetItem()).thenReturn((MutableVfsItem) mutableTargetItem);
        when(moveCopyItemInfo.getTargetItem()).thenReturn(targetItem);
    }

    @Test
    void notifyAfterMoveCopyFiles_shouldCallAfterMoveForFileWhenMoveContext() {
        when(moveCopyItemInfo.getSourceItem()).thenReturn(sourceItem);
        when(context.isCopy()).thenReturn(false);
        when(context.isDryRun()).thenReturn(false);
        when(targetItem.getInfo()).thenReturn(fileInfo);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        when(mutableTargetItem.isFile()).thenReturn(true);
        when(fileInfo.isFolder()).thenReturn(false);

        listener.notifyAfterMoveCopyFiles(Collections.singletonList(moveCopyItemInfo), context, status);

        verify(storageInterceptors).afterMove(eq(sourceItem),
                                              eq(mutableTargetItem),
                                              eq(status),
                                              any(PropertiesImpl.class),
                                              any(InterceptorMoveCopyContext.class));
        verify(storageInterceptors, never()).afterCopy(any(), any(), any(), any(), any());
    }

    @Test
    void notifyAfterMoveCopyFiles_shouldCallAfterMoveForFileWhenCopyContext() {
        when(moveCopyItemInfo.getSourceItem()).thenReturn(sourceItem);
        when(context.isCopy()).thenReturn(true);
        when(context.isDryRun()).thenReturn(false);
        when(targetItem.getInfo()).thenReturn(fileInfo);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);
        when(mutableTargetItem.isFile()).thenReturn(true);
        when(fileInfo.isFolder()).thenReturn(false);

        listener.notifyAfterMoveCopyFiles(Collections.singletonList(moveCopyItemInfo), context, status);

        verify(storageInterceptors).afterCopy(eq(sourceItem),
                                              eq(mutableTargetItem),
                                              eq(status),
                                              any(PropertiesImpl.class),
                                              any(InterceptorMoveCopyContext.class));

        verify(storageInterceptors, never()).afterMove(any(), any(), any(), any(), any());
    }

    @Test
    void notifyAfterMoveCopyFiles_shouldLogErrorWhenSourceIsFileAndTargetIsFolder() {
        when(context.isDryRun()).thenReturn(false);
        when(targetItem.getInfo()).thenReturn(folderInfo);
        when(folderInfo.isFolder()).thenReturn(true);
        when(mutableTargetItem.isFile()).thenReturn(true);
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(targetRepoPath.toString()).thenReturn("test-repo/path/to/file.jar");

        listener.notifyAfterMoveCopyFiles(Collections.singletonList(moveCopyItemInfo), context, status);

        final String expectedErrorMessage =
                "Target item 'test-repo/path/to/file.jar' was expected to be a file but is actually a folder.";
        verify(status).errorWithDifferentLogLevel(
                eq(expectedErrorMessage),
                eq(HttpStatus.SC_BAD_REQUEST),
                eq(null),
                any(),
                eq(Level.WARN)
        );
        verify(storageInterceptors, never()).afterCopy(any(), any(), any(), any(), any());
        verify(storageInterceptors, never()).afterMove(any(), any(), any(), any(), any());
    }
}
