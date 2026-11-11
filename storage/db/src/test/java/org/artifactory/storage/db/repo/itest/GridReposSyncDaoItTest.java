package org.artifactory.storage.db.repo.itest;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.repo.dao.RepositoryConfigDao;
import org.artifactory.storage.db.repo.dao.TransparentRepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepoKeyConfigBlob;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * DB-level integration tests for the Grid repos sync flow.
 * Covers the DAO streaming interactions relied on by GridCandidateSetBuilder:
 * reading repo keys from both the regular and transparent repo tables.
 *
 * @author Hussein Alshqerat
 */
@Test
public class GridReposSyncDaoItTest extends DbBaseTest {

    @Autowired
    private RepositoryConfigDao repoDao;

    @Autowired
    private TransparentRepositoryConfigDao transparentDao;

    @AfterMethod
    public void tearDown() throws SQLException {
        repoDao.deleteAll();
        transparentDao.getAll().forEach(c -> {
            try {
                transparentDao.delete(c.getKey());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // GridCandidateSetBuilder scenarios

    public void streamKeyAndConfigBlob_fromBothTables_returnsAllKeys() throws SQLException {
        repoDao.bulkInsert(List.of(
                config("libs-release-local"),
                config("docker-local")));
        transparentDao.bulkInsert(List.of(
                config("peer-libs-local"),
                config("peer-docker-local")));

        Set<String> regularKeys = streamKeys(repoDao);
        Set<String> transparentKeys = streamTransparentKeys(transparentDao);

        assertTrue(regularKeys.containsAll(List.of("libs-release-local", "docker-local")));
        assertTrue(transparentKeys.containsAll(List.of("peer-libs-local", "peer-docker-local")));
    }

    public void streamKeyAndConfigBlob_noFilter_returnsBothRegularAndTransparentKeys() throws SQLException {
        repoDao.bulkInsert(List.of(config("my-local-repo")));
        transparentDao.bulkInsert(List.of(config("remote-grid-repo")));

        Set<String> regular = streamKeys(repoDao);
        Set<String> transparent = streamTransparentKeys(transparentDao);

        // Simulates the combined lower-key set built by GridCandidateSetBuilder
        Set<String> combined = new HashSet<>();
        combined.addAll(regular);
        combined.addAll(transparent);

        assertTrue(combined.contains("my-local-repo"));
        assertTrue(combined.contains("remote-grid-repo"));
        assertEquals(combined.size(), 2);
    }

    public void streamKeyAndConfigBlob_preservesOriginalCasing() throws SQLException {
        repoDao.bulkInsert(List.of(config("MyMixedCaseRepo")));

        Set<String> keys = streamKeys(repoDao);

        assertTrue(keys.contains("MyMixedCaseRepo"),
                "Stream must preserve original casing so GridCandidateSetBuilder can lower-case keys correctly");
    }

    public void streamKeyAndConfigBlob_withGridTopologyIdBlob_returnsReadableBlob() throws SQLException {
        byte[] blobWithTopologyId = gridTopologyBlob("topology-abc-123");
        repoDao.bulkInsert(List.of(configWithBlob("federated-repo", blobWithTopologyId)));

        List<DbRepoKeyConfigBlob> rows;
        try (var stream = repoDao.streamKeyAndConfigBlob()) {
            rows = stream.collect(Collectors.toList());
        }

        assertEquals(rows.size(), 1);
        assertEquals(rows.get(0).repositoryKey(), "federated-repo");
        // Raw blob round-trip is intact — GridRepoConfigProjection.extractGridTopologyId can parse it
        assertFalse(rows.get(0).configBlob() == null || rows.get(0).configBlob().length == 0);
    }

    // helpers

    private static Set<String> streamKeys(RepositoryConfigDao dao) throws SQLException {
        try (var stream = dao.streamKeyAndConfigBlob()) {
            return stream.map(DbRepoKeyConfigBlob::repositoryKey).collect(Collectors.toSet());
        }
    }

    private static Set<String> streamTransparentKeys(TransparentRepositoryConfigDao dao) throws SQLException {
        try (var stream = dao.streamKeyAndConfigBlob()) {
            return stream.map(DbRepoKeyConfigBlob::repositoryKey).collect(Collectors.toSet());
        }
    }

    private static DbRepositoryConfig config(String key) {
        return new DbRepositoryConfig(key, "local", null, "maven", 1L, 100L, 200L, new byte[] {1, 2, 3});
    }

    private static DbRepositoryConfig configWithBlob(String key, byte[] blob) {
        return new DbRepositoryConfig(key, "local", null, "maven", 1L, 100L, 200L, blob);
    }

    /**
     * Returns a minimal JSON blob that GridRepoConfigProjection.extractGridTopologyId() can parse.
     */
    private static byte[] gridTopologyBlob(String topologyId) {
        String json = String.format(
                "{\"baseConfig\":{\"federationConfig\":{\"gridTopologyId\":\"%s\"}}}", topologyId);
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
