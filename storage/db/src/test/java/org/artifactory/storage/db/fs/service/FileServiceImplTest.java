package org.artifactory.storage.db.fs.service;

import org.artifactory.checksum.ChecksumType;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodeBuilder;
import org.artifactory.storage.fs.VfsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    private FileServiceImpl fileService;
    private NodesDao nodesDao;

    @BeforeEach
    public void setUp() {
        fileService = new FileServiceImpl();
        nodesDao = mock(NodesDao.class);
        fileService.setNodesDao(nodesDao);
    }

    @Test
    void testIsEmptyRepo() throws SQLException {
        String repoKey = "repo1";
        when(nodesDao.isEmptyRepo(repoKey)).thenReturn(true);

        boolean result = fileService.isEmpty(repoKey);

        assertTrue(result);
        verify(nodesDao).isEmptyRepo(repoKey);
    }

    @Test
    void testIsNotEmptyRepo() throws SQLException {
        String repoKey = "repo1";
        when(nodesDao.isEmptyRepo(repoKey)).thenReturn(false);

        boolean result = fileService.isEmpty(repoKey);

        assertFalse(result);
        verify(nodesDao).isEmptyRepo(repoKey);
    }

    @Test
    void testIsEmptyRepoThrowsException() throws SQLException {
        String repoKey = "repo1";
        when(nodesDao.isEmptyRepo(repoKey)).thenThrow(SQLException.class);

        assertThrows(VfsException.class, () -> fileService.isEmpty(repoKey));
        verify(nodesDao).isEmptyRepo(repoKey);
    }

    @Test
    void testSearchFilesWithBadChecksumEmptyRepos() {
        ChecksumType type = ChecksumType.md5;
        Set<String> repos = Set.of();
        List<FileInfo> result = fileService.searchFilesWithBadChecksum(type, repos);
        assertEquals(List.of(), result);
    }

    @Test
    void testSearchFilesWithBadChecksumInvalidType() {
        ChecksumType type = null;
        Set<String> repos = Set.of("repo1", "repo2");
        List<FileInfo> result = fileService.searchFilesWithBadChecksum(type, repos);
        assertEquals(List.of(), result);
    }

    @Test
    void testSearchFilesWithBadChecksumThrowsVfsException() {
        ChecksumType type = ChecksumType.md5;
        Set<String> repos = Set.of("repo1", "repo2");
        when(nodesDao.searchBadChecksums(any(ChecksumType.class), anySet()))
                .thenThrow(VfsException.class);
        assertThrows(VfsException.class, () -> fileService.searchFilesWithBadChecksum(type, repos));
    }

    @Test
    void getNodeRepoPaths_shouldReturnRepoPaths_whenNodesExist() throws SQLException {
        // Arrange
        Node node1 = new NodeBuilder().nodeId(1).file(true)
                .repo("test-repo").path("folder").name("file1.jar").build();
        Node node2 = new NodeBuilder().nodeId(2).file(true)
                .repo("test-repo").path("folder").name("file2.jar").build();
        when(nodesDao.getNodesByRepo("test-repo", 100)).thenReturn(List.of(node1, node2));

        // Act
        List<RepoPath> result = fileService.getNodeRepoPaths("test-repo", 100);

        // Assert
        assertEquals(2, result.size());
        assertEquals("test-repo", result.get(0).getRepoKey());
        assertEquals("test-repo", result.get(1).getRepoKey());
    }

    @Test
    void getNodeRepoPaths_shouldReturnEmptyList_whenNoNodes() throws SQLException {
        when(nodesDao.getNodesByRepo("empty-repo", 100)).thenReturn(List.of());

        List<RepoPath> result = fileService.getNodeRepoPaths("empty-repo", 100);

        assertTrue(result.isEmpty());
    }

    @Test
    void getNodeRepoPaths_shouldThrowVfsException_whenSqlExceptionOccurs() throws SQLException {
        when(nodesDao.getNodesByRepo("bad-repo", 100)).thenThrow(new SQLException("DB error"));

        assertThrows(VfsException.class, () -> fileService.getNodeRepoPaths("bad-repo", 100));
    }
}
