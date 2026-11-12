/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import java.util.List;
import org.artifactory.fs.ItemInfo;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.resource.ResourceStreamHandle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A7: what the {@code repositories} binding offers a registry: digests, streamed content, move and copy. */
class PluginRepositoriesApiTest {

    private static final String SHA256 = "a".repeat(64);

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final PluginRepositories repositories =
            new PluginRepositories(repositoryService, mock(PropertiesService.class), path -> true);
    private final RepoPath blob = RepoPathFactory.create("images", "blobs/sha256/" + SHA256);

    /** A binding whose caller may read nothing; every read must look absent or refuse. */
    private final PluginRepositories denied =
            new PluginRepositories(repositoryService, mock(PropertiesService.class), path -> false);

    @Test
    void unreadablePathsLookAbsentAndNeverReachTheContentService() {
        when(repositoryService.exists(blob)).thenReturn(true);
        assertFalse(denied.exists(blob));
        assertTrue(denied.findFile(blob).isEmpty());
        assertTrue(denied.openContent(blob).isEmpty());
        assertTrue(denied.getChildren(blob.getParent()).isEmpty());
        assertThrows(PluginRepositories.ReadDeniedException.class, () -> denied.getContent(blob));
        assertThrows(PluginRepositories.ReadDeniedException.class, () -> denied.getStringContent(blob));
        assertThrows(PluginRepositories.ReadDeniedException.class, () -> denied.getFileInfo(blob));
        assertThrows(PluginRepositories.ReadDeniedException.class, () -> denied.getProperties(blob));
        verify(repositoryService, never()).getResourceStreamHandle(any());
        verify(repositoryService, never()).getStringContent(any(RepoPath.class));
        verify(repositoryService, never()).getFileInfo(any());
        verify(repositoryService, never()).getChildren(any());
    }

    @Test
    void childrenTheCallerMayNotReadAreLeftOut() {
        RepoPath folder = RepoPathFactory.create("images", "blobs");
        ItemInfo open = mock(ItemInfo.class);
        ItemInfo hidden = mock(ItemInfo.class);
        RepoPath openPath = RepoPathFactory.create("images", "blobs/open");
        RepoPath hiddenPath = RepoPathFactory.create("images", "blobs/hidden");
        when(open.getRepoPath()).thenReturn(openPath);
        when(hidden.getRepoPath()).thenReturn(hiddenPath);
        when(repositoryService.getChildren(folder)).thenReturn(List.of(open, hidden));
        PluginRepositories partial = new PluginRepositories(repositoryService, mock(PropertiesService.class),
                path -> !path.equals(hiddenPath));
        assertEquals(List.of(open), partial.getChildren(folder));
    }

    @Test
    void findFileReportsSizeAndStoredSha256() {
        FileInfo info = mock(FileInfo.class);
        ChecksumsInfo checksums = new ChecksumsInfo();
        checksums.addChecksumInfo(new ChecksumInfo(ChecksumType.sha256, SHA256, SHA256));
        checksums.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, "b".repeat(40), "b".repeat(40)));
        when(info.getChecksumsInfo()).thenReturn(checksums);
        when(info.getSize()).thenReturn(1234L);
        when(info.getLastModified()).thenReturn(99L);
        when(info.getMimeType()).thenReturn("application/octet-stream");
        when(repositoryService.exists(blob)).thenReturn(true);
        when(repositoryService.getFileInfo(blob)).thenReturn(info);

        PluginRepositories.StoredFile file = repositories.findFile(blob).orElseThrow();
        assertEquals(1234L, file.size());
        assertEquals(SHA256, file.sha256());
        assertEquals("b".repeat(40), file.sha1());
        assertEquals(99L, file.lastModified());
    }

    @Test
    void findFileIsEmptyForMissingPathsAndFolders() {
        assertTrue(repositories.findFile(blob).isEmpty());
        RepoPath folder = RepoPathFactory.create("images", "blobs");
        when(repositoryService.exists(folder)).thenReturn(true);
        when(repositoryService.getFileInfo(folder)).thenThrow(new FileExpectedException(folder));
        assertTrue(repositories.findFile(folder).isEmpty());
        assertTrue(repositories.openContent(blob).isEmpty());
        verify(repositoryService, never()).getResourceStreamHandle(any());
    }

    @Test
    void openContentStreamsWithTheFileSizeAndReleasesTheHandle() throws IOException {
        FileInfo info = mock(FileInfo.class);
        when(repositoryService.exists(blob)).thenReturn(true);
        when(repositoryService.getFileInfo(blob)).thenReturn(info);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        when(handle.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(handle.getSize()).thenReturn(3L);
        when(repositoryService.getResourceStreamHandle(blob)).thenReturn(handle);

        Optional<PluginApiResponse.Body.Stream> content = repositories.openContent(blob);
        PluginApiResponse.Body.Stream stream = content.orElseThrow();
        assertEquals(3L, stream.size());
        assertEquals(3, stream.input().readAllBytes().length);
        stream.input().close();
        verify(handle).close();
    }

    @Test
    void deployHandsTheCallersStreamToTheUploadPath() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        BasicStatusHolder ok = new BasicStatusHolder();
        when(repositoryService.deploy(blob, content)).thenReturn(ok);
        assertSame(ok, repositories.deploy(blob, content));
        verify(repositoryService).deploy(blob, content);
    }

    @Test
    void moveAndCopyStayWithinTheRepository() {
        RepoPath upload = RepoPathFactory.create("images", "_uploads/1");
        MoveMultiStatusHolder moved = new MoveMultiStatusHolder();
        when(repositoryService.move(upload, blob, false, true, true)).thenReturn(moved);
        MoveMultiStatusHolder copied = new MoveMultiStatusHolder();
        when(repositoryService.copy(upload, blob)).thenReturn(copied);
        assertSame(moved, repositories.move(upload, blob));
        assertSame(copied, repositories.copy(upload, blob));

        RepoPath elsewhere = RepoPathFactory.create("other", "blobs/x");
        StatusHolder refusedMove = repositories.move(upload, elsewhere);
        StatusHolder refusedCopy = repositories.copy(upload, elsewhere);
        assertTrue(refusedMove.isError());
        assertEquals(400, refusedMove.getStatusCode());
        assertTrue(refusedCopy.isError());
        verify(repositoryService, never()).move(upload, elsewhere, false, true, true);
        verify(repositoryService, never()).copy(upload, elsewhere);
    }
}
