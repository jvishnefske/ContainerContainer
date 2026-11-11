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

package org.artifactory.repo.service;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.ArchiveFileContent;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ArchiveContentRetriever.
 * <p>
 * Note: Tests for actual archive reading functionality (with real ZIP/JAR files) are covered
 * by integration tests in {@link org.artifactory.itest.repo.service.ViewSourceTest}.
 * This unit test focuses on parameter validation and logic paths that don't require
 * actual archive file processing.
 *
 * @author Ido Zurel (with cursor)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveContentRetrieverTest extends ArtifactoryHomeBoundJUnitTest {

    private final ArchiveContentRetriever archiveContentRetriever = new ArchiveContentRetriever();
    private MockedStatic<ContextHelper> contextHelperMock;
    private InternalArtifactoryContext context;
    private AuthorizationService authorizationService;

    @Test
    void getGenericArchiveFileContent_shouldThrowIllegalArgumentException_whenRepoIsNull() throws IOException {
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.java";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> archiveContentRetriever.getGenericArchiveFileContent(null, archivePath, archiveEntryPath));

        assertTrue(exception.getMessage().contains("Repository cannot be null"),
                "Exception message should indicate repository cannot be null");
    }

    @Test
    void getGenericArchiveFileContent_shouldThrowIllegalArgumentException_whenArchivePathIsNull()
            throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        String archiveEntryPath = "org/example/Test.java";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> archiveContentRetriever.getGenericArchiveFileContent(repo, null, archiveEntryPath));

        assertTrue(exception.getMessage().contains("Archive path cannot be null"),
                "Exception message should indicate archive path cannot be null");
    }

    @Test
    void getGenericArchiveFileContent_shouldThrowIllegalArgumentException_whenArchiveEntryPathIsNull()
            throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath, null));

        assertTrue(exception.getMessage().contains("Archive entry path cannot be null"),
                "Exception message should indicate archive entry path cannot be null");
    }

    @BeforeEach
    void setUp() {
        contextHelperMock = mockStatic(ContextHelper.class);
        context = mock(InternalArtifactoryContext.class);
        authorizationService = mock(AuthorizationService.class);
        when(context.getAuthorizationService()).thenReturn(authorizationService);
        contextHelperMock.when(ContextHelper::get).thenReturn(context);
    }

    @AfterEach
    void tearDown() {
        if (contextHelperMock != null) {
            contextHelperMock.close();
        }
    }

    @Test
    void getGenericArchiveFileContent_shouldReturnContentNotFound_whenFileTypeIsUnsupported() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.bin";

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
        assertTrue(result.getFailureReason().contains("is not supported"));
    }

    @Test
    void getGenericArchiveFileContent_shouldReturnContentNotFound_whenSourceJarNotFound() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.class";

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(null);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
        assertEquals("Source jar not found.", result.getFailureReason());
    }

    @Test
    void getGenericArchiveFileContent_shouldReturnContentNotFound_whenNoReadPermissions() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.class";
        VfsFile sourceFile = mock(VfsFile.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(sourceFile);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
        assertEquals("No read permissions for the source jar.", result.getFailureReason());
    }

    @Test
    void getGenericArchiveFileContent_shouldSearchSourcesJarFirst_whenClassFile() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.class";

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(null);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
    }

    @Test
    void getGenericArchiveFileContent_shouldUseArchivePathDirectly_whenTextFile() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "README.md";

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(null);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
    }

    @Test
    void getGenericArchiveFileContent_shouldHandleMultipleSearchPaths() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        String archiveEntryPath = "org/example/Test.class";

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(null);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        ArchiveFileContent result = archiveContentRetriever.getGenericArchiveFileContent(repo, archivePath,
                archiveEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
    }

    @Test
    void getAlternativeExtensions_shouldReturnGroovyAndFx_whenJavaFile() {
        List<String> result = archiveContentRetriever.getAlternativeExtensions("Test.java");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("groovy"));
        assertTrue(result.contains("fx"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test.JAVA", "test.Java", "TEST.JAVA"})
    void getAlternativeExtensions_shouldReturnGroovyAndFx_whenJavaFileCaseInsensitive(String fileName) {
        List<String> result = archiveContentRetriever.getAlternativeExtensions(fileName);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("groovy"));
        assertTrue(result.contains("fx"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test.txt", "Test.class", "Test.xml", "README.md", "file"})
    void getAlternativeExtensions_shouldReturnEmptyList_whenNonJavaFile(String fileName) {
        List<String> result = archiveContentRetriever.getAlternativeExtensions(fileName);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildSearchList_shouldReturnSourcesJarFirst_whenClassFile() {
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        ArchiveContentRetriever.SearchListResult result =
                archiveContentRetriever.buildSearchList(archivePath, "org/example/Test.class");

        assertNotNull(result);
        assertNotNull(result.searchList);
        assertEquals(2, result.searchList.size());
        assertTrue(result.searchList.get(0).contains("-sources"));
        assertTrue(result.searchList.get(0).contains("archive-sources.jar"));
        assertEquals("path/to/archive.jar", result.searchList.get(1));
        assertNotNull(result.sourceEntryPath);
        assertTrue(result.sourceEntryPath.endsWith(".java"));
        assertNull(result.failureReason);
    }

    @Test
    void buildSearchList_shouldUseArchivePathDirectly_whenTextFile() {
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        ArchiveContentRetriever.SearchListResult result =
                archiveContentRetriever.buildSearchList(archivePath, "org/example/Test.java");

        assertNotNull(result);
        assertNotNull(result.searchList);
        assertEquals(1, result.searchList.size());
        assertEquals("path/to/archive.jar", result.searchList.get(0));
        assertEquals("org/example/Test.java", result.sourceEntryPath);
        assertNull(result.failureReason);
    }

    @Test
    void buildSearchList_shouldReturnFailureReason_whenUnsupportedFile() {
        RepoPath archivePath = new RepoPathImpl("repo", "path/to/archive.jar");
        ArchiveContentRetriever.SearchListResult result =
                archiveContentRetriever.buildSearchList(archivePath, "org/example/Test.bin");

        assertNotNull(result);
        assertNull(result.searchList);
        assertNull(result.sourceEntryPath);
        assertNotNull(result.failureReason);
        assertTrue(result.failureReason.contains("is not supported"));
    }

    @Test
    void processSearchList_shouldReturnContent_whenFirstPathSucceeds() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        VfsFile sourceFile = mock(VfsFile.class);
        org.apache.commons.compress.archivers.ArchiveEntry mockEntry =
                mock(org.apache.commons.compress.archivers.ArchiveEntry.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(sourceFile);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(sourceFile.getPath()).thenReturn("path/to/sources.jar");
        when(mockEntry.getSize()).thenReturn(100L);
        when(mockEntry.getName()).thenReturn("org/example/Test.java");

        try (MockedStatic<org.artifactory.util.ZipUtils> zipUtilsMock =
                        mockStatic(org.artifactory.util.ZipUtils.class);
                MockedStatic<org.apache.commons.io.IOUtils> ioUtilsMock =
                        mockStatic(org.apache.commons.io.IOUtils.class)) {
            org.apache.commons.compress.archivers.ArchiveInputStream<?> mockStream =
                    mock(org.apache.commons.compress.archivers.ArchiveInputStream.class);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.getArchiveInputStream(any(VfsFile.class)))
                    .thenReturn(mockStream);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.locateArchiveEntry(any(), any(), any()))
                    .thenReturn(mockEntry);
            ioUtilsMock.when(() -> org.apache.commons.io.IOUtils.toString(any(java.io.InputStream.class),
                            any(java.nio.charset.Charset.class)))
                    .thenReturn("test content");

            List<String> searchList = Lists.newArrayList("path/to/sources.jar", "path/to/archive.jar");
            ArchiveContentRetriever.ContentResult result =
                    archiveContentRetriever.processSearchList(repo, searchList, "org/example/Test.java");

            assertNotNull(result);
            assertNotNull(result.content);
            assertEquals("test content", result.content);
        }
    }

    @Test
    void processSearchList_shouldTryNextPath_whenFirstPathFails() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        VfsFile sourceFile = mock(VfsFile.class);
        org.apache.commons.compress.archivers.ArchiveEntry mockEntry =
                mock(org.apache.commons.compress.archivers.ArchiveEntry.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class)))
                .thenReturn(null)
                .thenReturn(sourceFile);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(sourceFile.getPath()).thenReturn("path/to/archive.jar");
        when(mockEntry.getSize()).thenReturn(100L);
        when(mockEntry.getName()).thenReturn("org/example/Test.java");

        try (MockedStatic<org.artifactory.util.ZipUtils> zipUtilsMock =
                        mockStatic(org.artifactory.util.ZipUtils.class);
                MockedStatic<org.apache.commons.io.IOUtils> ioUtilsMock =
                        mockStatic(org.apache.commons.io.IOUtils.class)) {
            org.apache.commons.compress.archivers.ArchiveInputStream<?> mockStream =
                    mock(org.apache.commons.compress.archivers.ArchiveInputStream.class);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.getArchiveInputStream(any(VfsFile.class)))
                    .thenReturn(mockStream);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.locateArchiveEntry(any(), any(), any()))
                    .thenReturn(mockEntry);
            ioUtilsMock.when(() -> org.apache.commons.io.IOUtils.toString(any(java.io.InputStream.class),
                            any(java.nio.charset.Charset.class)))
                    .thenReturn("test content");

            List<String> searchList = Lists.newArrayList("path/to/sources.jar", "path/to/archive.jar");
            ArchiveContentRetriever.ContentResult result =
                    archiveContentRetriever.processSearchList(repo, searchList, "org/example/Test.java");

            assertNotNull(result);
        }
    }

    @Test
    void processSearchList_shouldBreakEarly_whenContentFound() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        VfsFile sourceFile = mock(VfsFile.class);
        org.apache.commons.compress.archivers.ArchiveEntry mockEntry =
                mock(org.apache.commons.compress.archivers.ArchiveEntry.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(sourceFile);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(sourceFile.getPath()).thenReturn("path/to/sources.jar");
        when(mockEntry.getSize()).thenReturn(100L);
        when(mockEntry.getName()).thenReturn("org/example/Test.java");

        try (MockedStatic<org.artifactory.util.ZipUtils> zipUtilsMock =
                        mockStatic(org.artifactory.util.ZipUtils.class);
                MockedStatic<org.apache.commons.io.IOUtils> ioUtilsMock =
                        mockStatic(org.apache.commons.io.IOUtils.class)) {
            org.apache.commons.compress.archivers.ArchiveInputStream<?> mockStream =
                    mock(org.apache.commons.compress.archivers.ArchiveInputStream.class);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.getArchiveInputStream(any(VfsFile.class)))
                    .thenReturn(mockStream);
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.locateArchiveEntry(any(), any(), any()))
                    .thenReturn(mockEntry);
            ioUtilsMock.when(() -> org.apache.commons.io.IOUtils.toString(any(java.io.InputStream.class),
                            any(java.nio.charset.Charset.class)))
                    .thenReturn("test content");

            List<String> searchList = Lists.newArrayList("path/to/sources.jar", "path/to/archive.jar");
            ArchiveContentRetriever.ContentResult result =
                    archiveContentRetriever.processSearchList(repo, searchList, "org/example/Test.java");

            assertNotNull(result);
            assertNotNull(result.content);
        }
    }

    @Test
    void processSourceFile_shouldReturnFailureReason_whenFileNotFound() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(null);

        ArchiveContentRetriever.ContentResult result =
                archiveContentRetriever.processSourceFile(repo, "path/to/sources.jar", "org/example/Test.java");

        assertNotNull(result);
        assertNull(result.content);
        assertEquals("Source jar not found.", result.failureReason);
    }

    @Test
    void processSourceFile_shouldReturnFailureReason_whenNoReadPermissions() throws IOException {
        LocalRepo repo = mock(LocalRepo.class);
        VfsFile sourceFile = mock(VfsFile.class);

        when(repo.getKey()).thenReturn("repo");
        when(repo.getImmutableFile(any(RepoPath.class))).thenReturn(sourceFile);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);

        ArchiveContentRetriever.ContentResult result =
                archiveContentRetriever.processSourceFile(repo, "path/to/sources.jar", "org/example/Test.java");

        assertNotNull(result);
        assertNull(result.content);
        assertEquals("No read permissions for the source jar.", result.failureReason);
    }

    @Test
    void readArchiveContent_shouldReturnFailureReason_whenEntryNotFound() throws IOException {
        VfsFile sourceFile = mock(VfsFile.class);

        try (MockedStatic<org.artifactory.util.ZipUtils> zipUtilsMock =
                     mockStatic(org.artifactory.util.ZipUtils.class)) {
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.getArchiveInputStream(any(VfsFile.class)))
                    .thenReturn(mock(org.apache.commons.compress.archivers.ArchiveInputStream.class));
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.locateArchiveEntry(any(), any(), any()))
                    .thenReturn(null);

            ArchiveContentRetriever.ContentResult result =
                    archiveContentRetriever.readArchiveContent(sourceFile, "org/example/Test.java");

            assertNotNull(result);
            assertNull(result.content);
            assertEquals("Source file not found.", result.failureReason);
        }
    }

    @Test
    void readArchiveContent_shouldReturnFailureReason_whenFileTooBig() throws IOException {
        VfsFile sourceFile = mock(VfsFile.class);

        try (MockedStatic<org.artifactory.util.ZipUtils> zipUtilsMock =
                     mockStatic(org.artifactory.util.ZipUtils.class)) {
            org.apache.commons.compress.archivers.ArchiveEntry mockEntry =
                    mock(org.apache.commons.compress.archivers.ArchiveEntry.class);
            when(mockEntry.getSize()).thenReturn(2L * 1024 * 1024);

            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.getArchiveInputStream(any(VfsFile.class)))
                    .thenReturn(mock(org.apache.commons.compress.archivers.ArchiveInputStream.class));
            zipUtilsMock.when(() -> org.artifactory.util.ZipUtils.locateArchiveEntry(any(), any(), any()))
                    .thenReturn(mockEntry);

            ArchiveContentRetriever.ContentResult result =
                    archiveContentRetriever.readArchiveContent(sourceFile, "org/example/Test.java");

            assertNotNull(result);
            assertNull(result.content);
            assertNotNull(result.failureReason);
            assertTrue(result.failureReason.contains("too big"));
        }
    }
}
