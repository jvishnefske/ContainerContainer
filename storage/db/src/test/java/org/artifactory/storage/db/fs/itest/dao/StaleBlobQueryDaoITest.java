package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.storage.db.fs.dao.StaleBlobQueryDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Integration test for {@link StaleBlobQueryDao} against a real embedded database.
 * Verifies that the NOT EXISTS SQL query correctly identifies stale blobs
 * (sha256__ files whose parent directory contains no manifest).
 */
@Test
public class StaleBlobQueryDaoITest extends DbBaseTest {

    private static final String REPO = "docker-test";
    private static final String MANIFEST = "manifest.json";
    private static final String LIST_MANIFEST = "list.manifest.json";

    @Autowired
    private StaleBlobQueryDao staleBlobQueryDao;

    @BeforeClass
    public void importTestData() {
        importSql("/sql/stale-blobs.sql");
    }

    @Test
    void shouldReturnOnlyStaleBlobsWithNoManifestInDir() throws SQLException {
        try (Stream<Node> stream = streamAll(null)) {
            List<Node> results = stream.toList();
            Set<String> names = results.stream().map(Node::getName).collect(Collectors.toSet());
            assertEquals(results.size(), 3);
            assertTrue(names.contains("sha256__orphan1"));
            assertTrue(names.contains("sha256__orphan2"));
            assertTrue(names.contains("sha256__oldblob"));
        }
    }

    @Test
    void shouldExcludeBlobsInDirWithManifestJson() throws SQLException {
        try (Stream<Node> stream = streamAll(null)) {
            Set<String> names = stream.map(Node::getName).collect(Collectors.toSet());
            assertTrue(!names.contains("sha256__aaa111"), "Blob in dir with manifest.json should be excluded");
            assertTrue(!names.contains("sha256__aaa222"), "Blob in dir with manifest.json should be excluded");
        }
    }

    @Test
    void shouldExcludeBlobsInDirWithListManifest() throws SQLException {
        try (Stream<Node> stream = streamAll(null)) {
            Set<String> names = stream.map(Node::getName).collect(Collectors.toSet());
            assertTrue(!names.contains("sha256__redis1"), "Blob in dir with list.manifest.json should be excluded");
        }
    }

    @Test
    void shouldExcludeBlobsInUploadsDir() throws SQLException {
        try (Stream<Node> stream = streamAll(null)) {
            Set<String> names = stream.map(Node::getName).collect(Collectors.toSet());
            assertTrue(!names.contains("sha256__uploading"), "Blob in _uploads dir should be excluded");
        }
    }

    @Test
    void shouldFilterByTimeInterval() throws SQLException {
        try (Stream<Node> stream = staleBlobQueryDao.streamStaleBlobs(
                REPO, 1500, 4000, null, MANIFEST, LIST_MANIFEST)) {
            List<Node> results = stream.toList();
            Set<String> names = results.stream().map(Node::getName).collect(Collectors.toSet());
            assertEquals(results.size(), 2);
            assertTrue(names.contains("sha256__orphan1"));
            assertTrue(names.contains("sha256__orphan2"));
        }
    }

    @Test
    void shouldFilterByPathPrefix() throws SQLException {
        try (Stream<Node> stream = streamAll("library/nginx")) {
            List<Node> results = stream.toList();
            Set<String> names = results.stream().map(Node::getName).collect(Collectors.toSet());
            assertEquals(results.size(), 2);
            assertTrue(names.contains("sha256__orphan1"));
            assertTrue(names.contains("sha256__orphan2"));
        }
    }

    @Test
    void shouldReturnEmptyForNonExistentRepo() throws SQLException {
        try (Stream<Node> stream = staleBlobQueryDao.streamStaleBlobs("no-such-repo", 0, 10000, null,
                MANIFEST, LIST_MANIFEST)) {
            assertEquals(stream.count(), 0);
        }
    }

    @Test
    void shouldReturnEmptyWhenTimeIntervalHasNoBlobs() throws SQLException {
        try (Stream<Node> stream = staleBlobQueryDao.streamStaleBlobs(REPO, 9000, 10000, null,
                MANIFEST, LIST_MANIFEST)) {
            assertEquals(stream.count(), 0);
        }
    }

    @Test
    void shouldPopulateNodeFieldsCorrectly() throws SQLException {
        try (Stream<Node> stream = staleBlobQueryDao.streamStaleBlobs(REPO, 1500, 2500, null,
                MANIFEST, LIST_MANIFEST)) {
            Node node = stream.findFirst().orElse(null);
            assertNotNull(node);
            assertEquals(node.getName(), "sha256__orphan1");
            assertEquals(node.getRepo(), REPO);
            assertEquals(node.getPath(), "library/nginx/v1");
            assertEquals(node.getCreated(), 2000L);
            assertEquals(node.getLength(), 512L);
            assertTrue(node.isFile());
        }
    }

    @Test
    void shouldReturnOldestBlobTimestamp() throws SQLException {
        OptionalLong result = staleBlobQueryDao.getOldestBlobCreated(REPO, null);
        assertTrue(result.isPresent());
        assertEquals(result.getAsLong(), 500L);
    }

    @Test
    void shouldReturnEmptyForOldestBlobInNonExistentRepo() throws SQLException {
        OptionalLong result = staleBlobQueryDao.getOldestBlobCreated("no-such-repo", null);
        assertTrue(result.isEmpty());
    }

    private Stream<Node> streamAll(String pathPrefix) throws SQLException {
        return staleBlobQueryDao.streamStaleBlobs(REPO, 0, 10000, pathPrefix, MANIFEST, LIST_MANIFEST);
    }
}
