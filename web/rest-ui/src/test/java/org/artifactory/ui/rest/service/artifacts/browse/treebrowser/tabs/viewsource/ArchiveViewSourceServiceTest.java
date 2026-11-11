/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.viewsource;

import org.artifactory.api.repo.ArchiveFileContent;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.viewsource.ViewArtifactSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ArchiveViewSourceService.
 *
 * @author Test
 */
@ExtendWith(MockitoExtension.class)
class ArchiveViewSourceServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ArtifactoryRestRequest request;

    @Mock
    private RestResponse response;

    @InjectMocks
    private ArchiveViewSourceService archiveViewSourceService;


    @Test
    void execute_shouldHandleIOExceptionGracefully() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenThrow(new IOException("IO error occurred"));

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("Error reading archive: IO error occurred", captured.getSource());
    }

    @Test
    void execute_shouldPreserveFailureReason_whenIllegalArgumentExceptionThrown() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("nonExistentRepo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        String errorMessage = "Local repository for 'nonExistentRepo/path/to/archive.jar' doesn't exist";
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenThrow(new IllegalArgumentException(errorMessage));

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals(errorMessage, captured.getSource());
    }

    @Test
    void execute_shouldSetSourceFileNotFound_whenArchiveFileContentIsNull() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenReturn(null);

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("Source File not found", captured.getSource());
    }

    @Test
    void execute_shouldSetSourceFileNotFound_whenArchiveFileContentHasNoFailureReason() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        ArchiveFileContent archiveFileContent = new ArchiveFileContent(null,
                InternalRepoPathFactory.create("repo", "path/to/archive.jar"), "org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenReturn(archiveFileContent);

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("Source File not found", captured.getSource());
    }

    @Test
    void execute_shouldSetSourceFileNotFound_whenArchiveFileContentHasEmptyFailureReason() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        RepoPath repoPath = InternalRepoPathFactory.create("repo", "path/to/archive.jar");
        ArchiveFileContent archiveFileContent = ArchiveFileContent.contentNotFound("");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenReturn(archiveFileContent);

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("Source File not found", captured.getSource());
    }

    @Test
    void execute_shouldReturnContent_whenSourceFileIsFound() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        RepoPath repoPath = InternalRepoPathFactory.create("repo", "path/to/archive.jar");
        String expectedContent = "public class Test { }";
        ArchiveFileContent archiveFileContent = new ArchiveFileContent(expectedContent, repoPath,
                "org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenReturn(archiveFileContent);

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals(expectedContent, captured.getSource());
    }

    @Test
    void execute_shouldSetSourceFileNotFound_whenContentIsEmptyString() throws IOException {
        ViewArtifactSource viewArtifact = new ViewArtifactSource();
        viewArtifact.setRepoKey("repo");
        viewArtifact.setArchivePath("path/to/archive.jar");
        viewArtifact.setSourcePath("org/example/Test.java");

        RepoPath repoPath = InternalRepoPathFactory.create("repo", "path/to/archive.jar");
        ArchiveFileContent archiveFileContent = new ArchiveFileContent("", repoPath, "org/example/Test.java");

        when(request.getImodel()).thenReturn(viewArtifact);
        when(repositoryService.getGenericArchiveFileContent(any(RepoPath.class), anyString()))
                .thenReturn(archiveFileContent);

        archiveViewSourceService.execute(request, response);

        verify(repositoryService).getGenericArchiveFileContent(any(RepoPath.class), anyString());
        ArgumentCaptor<ViewArtifactSource> captor = ArgumentCaptor.forClass(ViewArtifactSource.class);
        verify(response).iModel(captor.capture());
        ViewArtifactSource captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("Source File not found", captured.getSource());
    }
}
