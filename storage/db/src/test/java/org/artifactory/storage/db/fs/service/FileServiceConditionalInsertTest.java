package org.artifactory.storage.db.fs.service;

import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.fs.service.ConditionalInsertContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rima Polsky
 */
class FileServiceConditionalInsertTest {

    private FileServiceImpl fileService;
    private NodesDao nodesDao;
    private InternalDbService dbService;

    @BeforeEach
    void setUp() throws Exception {
        fileService = new FileServiceImpl();
        nodesDao = mock(NodesDao.class);
        dbService = mock(InternalDbService.class);
        fileService.setNodesDao(nodesDao);

        Field dbServiceField =
                FileServiceImpl.class.getDeclaredField("dbService");
        dbServiceField.setAccessible(true);
        dbServiceField.set(fileService, dbService);

        when(dbService.nextId()).thenReturn(100L);
        ConditionalInsertContext.clear();
    }

    @AfterEach
    void tearDown() {
        ConditionalInsertContext.clear();
    }

    private FileInfo mockFileInfo(String repoKey, String path) {
        RepoPath repoPath = new RepoPathImpl(repoKey, path);
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getRepoKey()).thenReturn(repoKey);
        when(fileInfo.getRelPath()).thenReturn(path);
        when(fileInfo.getSize()).thenReturn(1024L);
        when(fileInfo.getCreated()).thenReturn(System.currentTimeMillis());
        when(fileInfo.getLastModified())
                .thenReturn(System.currentTimeMillis());
        when(fileInfo.getLastUpdated())
                .thenReturn(System.currentTimeMillis());

        ChecksumsInfo checksums = new ChecksumsInfo();
        checksums.addChecksumInfo(new ChecksumInfo(
                ChecksumType.sha1, "orig", "abc123", false));
        checksums.addChecksumInfo(new ChecksumInfo(
                ChecksumType.md5, "orig", "def456", false));
        checksums.addChecksumInfo(new ChecksumInfo(
                ChecksumType.sha256,
                ChecksumInfo.TRUSTED_FILE_MARKER,
                "sha256val", false));
        when(fileInfo.getChecksumsInfo()).thenReturn(checksums);

        return fileInfo;
    }

    private FolderInfo mockFolderInfo(String repoKey, String path) {
        RepoPath repoPath = new RepoPathImpl(repoKey, path);
        FolderInfo folderInfo = mock(FolderInfo.class);
        when(folderInfo.getRepoPath()).thenReturn(repoPath);
        when(folderInfo.getRepoKey()).thenReturn(repoKey);
        when(folderInfo.getRelPath()).thenReturn(path);
        when(folderInfo.getCreated()).thenReturn(System.currentTimeMillis());
        when(folderInfo.getLastModified())
                .thenReturn(System.currentTimeMillis());
        when(folderInfo.getLastUpdated())
                .thenReturn(System.currentTimeMillis());
        return folderInfo;
    }

    @Test
    void createFile_usesStandardInsert() throws SQLException {
        FileInfo file = mockFileInfo("repo", "path/file.jar");
        when(nodesDao.create(any(Node.class))).thenReturn(1);

        long nodeId = fileService.createFile(file);

        assertEquals(100L, nodeId);
        verify(nodesDao).create(any(Node.class));
        verify(nodesDao, never()).createIfNotExists(any(Node.class));
    }

    @Test
    void createFile_duplicateThrowsSQLIntegrityException()
            throws SQLException {
        FileInfo file = mockFileInfo("repo", "path/file.jar");
        when(nodesDao.create(any(Node.class)))
                .thenThrow(
                        new SQLIntegrityConstraintViolationException("dup"));

        assertThrows(Exception.class, () -> fileService.createFile(file));
    }

    private void assertConditionalInsert_newItem_insertsAndReturnsId(
            Function<FileServiceImpl, Long> action) throws SQLException {
        ConditionalInsertContext.set(true);
        when(nodesDao.createIfNotExists(any(Node.class))).thenReturn(1);

        long nodeId = action.apply(fileService);

        assertEquals(100L, nodeId);
        verify(nodesDao).createIfNotExists(any(Node.class));
        verify(nodesDao, never()).create(any(Node.class));
    }

    private void assertConditionalInsert_existingItem_returnsExistingId(
            Function<FileServiceImpl, Long> action) throws SQLException {
        ConditionalInsertContext.set(true);
        when(nodesDao.createIfNotExists(any(Node.class))).thenReturn(0);
        when(nodesDao.getNodeId(any(NodePath.class))).thenReturn(42L);

        long nodeId = action.apply(fileService);

        assertEquals(42L, nodeId);
    }

    private void assertConditionalInsert_existingNodeNotFound_throws(
            Function<FileServiceImpl, Long> action) throws SQLException {
        ConditionalInsertContext.set(true);
        when(nodesDao.createIfNotExists(any(Node.class))).thenReturn(0);
        when(nodesDao.getNodeId(any(NodePath.class)))
                .thenReturn((long) DbService.NO_DB_ID);

        assertThrows(Exception.class, () -> action.apply(fileService));
    }

    private void assertConditionalInsert_newItem_doesNotSetSkipped(
            Function<FileServiceImpl, Long> action) throws SQLException {
        ConditionalInsertContext.set(true);
        when(nodesDao.createIfNotExists(any(Node.class))).thenReturn(1);

        action.apply(fileService);

        assertFalse(ConditionalInsertContext.wasSkipped());
    }

    private void assertConditionalInsert_existingItem_setsSkipped(
            Function<FileServiceImpl, Long> action) throws SQLException {
        ConditionalInsertContext.set(true);
        when(nodesDao.createIfNotExists(any(Node.class))).thenReturn(0);
        when(nodesDao.getNodeId(any(NodePath.class))).thenReturn(42L);

        action.apply(fileService);

        assertTrue(ConditionalInsertContext.wasSkipped());
    }

    @Nested
    class CreateFileWithConditionalInsert {
        private final Function<FileServiceImpl, Long> action = svc ->
                svc.createFile(
                        mockFileInfo("repo", "path/file.jar"));

        @Test
        void newFile_insertsAndReturnsId() throws SQLException {
            assertConditionalInsert_newItem_insertsAndReturnsId(action);
        }

        @Test
        void existingFile_returnsExistingId() throws SQLException {
            assertConditionalInsert_existingItem_returnsExistingId(action);
        }

        @Test
        void existingNodeNotFound_throws() throws SQLException {
            assertConditionalInsert_existingNodeNotFound_throws(action);
        }

        @Test
        void newFile_doesNotSetSkipped() throws SQLException {
            assertConditionalInsert_newItem_doesNotSetSkipped(action);
        }

        @Test
        void existingFile_setsSkipped() throws SQLException {
            assertConditionalInsert_existingItem_setsSkipped(action);
        }
    }

    @Nested
    class CreateFolderWithConditionalInsert {
        private final Function<FileServiceImpl, Long> action = svc ->
                svc.createFolder(
                        mockFolderInfo("repo", "path/subfolder"));

        @Test
        void newFolder_insertsAndReturnsId() throws SQLException {
            assertConditionalInsert_newItem_insertsAndReturnsId(action);
        }

        @Test
        void existingFolder_returnsExistingId() throws SQLException {
            assertConditionalInsert_existingItem_returnsExistingId(action);
        }

        @Test
        void existingNodeNotFound_throws() throws SQLException {
            assertConditionalInsert_existingNodeNotFound_throws(action);
        }

        @Test
        void newFolder_doesNotSetSkipped() throws SQLException {
            assertConditionalInsert_newItem_doesNotSetSkipped(action);
        }

        @Test
        void existingFolder_setsSkipped() throws SQLException {
            assertConditionalInsert_existingItem_setsSkipped(action);
        }
    }
}
