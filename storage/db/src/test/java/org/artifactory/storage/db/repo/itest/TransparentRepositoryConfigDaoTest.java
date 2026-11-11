package org.artifactory.storage.db.repo.itest;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.repo.dao.TransparentRepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepoKeyConfigBlob;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test
public class TransparentRepositoryConfigDaoTest extends DbBaseTest {

    @Autowired
    private TransparentRepositoryConfigDao dao;

    @AfterMethod
    public void tearDown() throws SQLException {
        dao.getAll().forEach(c -> {
            try {
                dao.delete(c.getKey());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void testInsert_andFindByKey() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        dao.insert(config);

        Optional<DbRepositoryConfig> result = dao.findByKey("repo1");
        assertTrue(result.isPresent());
        assertEquals(result.get().getKey(), "repo1");
        assertEquals(result.get().getType(), "local");
        assertEquals(result.get().getPackageType(), "maven");
    }

    public void testFindByKey_returnsEmpty_whenNotFound() throws SQLException {
        assertTrue(dao.findByKey("does-not-exist").isEmpty());
    }

    public void testUpdate_updatesFieldsButNotCreated() throws SQLException {
        DbRepositoryConfig config = config("repo1");
        dao.insert(config);

        DbRepositoryConfig updated = config("repo1");
        updated.setType("remote");
        updated.setModified(999L);
        updated.setRepositoryInfoBlob(new byte[]{9, 9, 9});
        dao.update(updated);

        Optional<DbRepositoryConfig> result = dao.findByKey("repo1");
        assertTrue(result.isPresent());
        assertEquals(result.get().getType(), "remote");
        assertEquals(result.get().getModified(), 999L);
        assertEquals(result.get().getCreated(), config.getCreated());
    }

    public void testGetAll() throws SQLException {
        dao.insert(config("repo1"));
        dao.insert(config("repo2"));
        dao.insert(config("repo3"));

        List<DbRepositoryConfig> all = dao.getAll();
        assertEquals(all.size(), 3);
    }

    public void testExistsByKey() throws SQLException {
        dao.insert(config("repo1"));
        assertTrue(dao.existsByKey("repo1"));
        assertFalse(dao.existsByKey("does-not-exist"));
    }

    public void testExistsByKeyIgnoreCase() throws SQLException {
        dao.insert(config("MyRepo"));
        assertTrue(dao.existsByKeyIgnoreCase("myrepo"));
        assertTrue(dao.existsByKeyIgnoreCase("MYREPO"));
        assertTrue(dao.existsByKeyIgnoreCase("MyRepo"));
        assertFalse(dao.existsByKeyIgnoreCase("other-repo"));
    }

    public void testDelete() throws SQLException {
        dao.insert(config("repo1"));
        assertTrue(dao.existsByKey("repo1"));

        int deleted = dao.delete("repo1");
        assertEquals(deleted, 1);
        assertFalse(dao.existsByKey("repo1"));
    }

    public void testDelete_returnsZero_whenNotFound() throws SQLException {
        assertEquals(dao.delete("does-not-exist"), 0);
    }

    // --- bulk operations ---

    public void testBulkInsert() throws SQLException {
        List<DbRepositoryConfig> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(config("key" + i));
        }
        dao.bulkInsert(batch);
        assertEquals(dao.getAll().size(), 5);
    }

    public void testBulkInsert_isNoOp_forEmpty() throws SQLException {
        dao.bulkInsert(List.of());
        assertEquals(dao.getAll().size(), 0);
    }

    public void testBulkInsert_isNoOp_forNull() throws SQLException {
        dao.bulkInsert(null);
        assertEquals(dao.getAll().size(), 0);
    }

    public void testBulkDelete() throws SQLException {
        for (int i = 0; i < 5; i++) {
            dao.insert(config("key" + i));
        }
        dao.bulkDelete(List.of("key0", "key1", "key2"));
        List<DbRepositoryConfig> remaining = dao.getAll();
        assertEquals(remaining.size(), 2);
        Set<String> remainingKeys = remaining.stream().map(DbRepositoryConfig::getKey).collect(Collectors.toSet());
        assertTrue(remainingKeys.contains("key3"));
        assertTrue(remainingKeys.contains("key4"));
    }

    public void testBulkDelete_isNoOp_forEmpty() throws SQLException {
        dao.insert(config("key0"));
        dao.bulkDelete(List.of());
        assertEquals(dao.getAll().size(), 1);
    }

    public void testBulkDelete_isNoOp_forNull() throws SQLException {
        dao.insert(config("key0"));
        dao.bulkDelete(null);
        assertEquals(dao.getAll().size(), 1);
    }

    public void testFindByKeys() throws SQLException {
        for (int i = 0; i < 5; i++) {
            dao.insert(config("key" + i));
        }
        List<DbRepositoryConfig> found = dao.findByKeys(List.of("key0", "key2", "missing"));
        assertEquals(found.size(), 2);
        Set<String> foundKeys = found.stream().map(DbRepositoryConfig::getKey).collect(Collectors.toSet());
        assertTrue(foundKeys.contains("key0"));
        assertTrue(foundKeys.contains("key2"));
    }

    public void testFindByKeys_returnsEmpty_forEmptyInput() throws SQLException {
        dao.insert(config("key0"));
        assertTrue(dao.findByKeys(List.of()).isEmpty());
    }

    public void testFindByKeys_returnsEmpty_forNullInput() throws SQLException {
        dao.insert(config("key0"));
        assertTrue(dao.findByKeys(null).isEmpty());
    }

    public void testStreamKeyAndConfigBlob_returnsEmptyWhenNoRepos() throws SQLException {
        try (var stream = dao.streamKeyAndConfigBlob()) {
            assertEquals(stream.count(), 0);
        }
    }

    public void testStreamKeyAndConfigBlob_returnsAllRowsWithConfigBlob() throws SQLException {
        byte[] alphaBlob = new byte[] {1};
        byte[] zebraBlob = new byte[] {2};

        dao.insert(configWithBlob("zebra-repo", zebraBlob));
        dao.insert(configWithBlob("alpha-repo", alphaBlob));

        List<DbRepoKeyConfigBlob> rows;
        try (var stream = dao.streamKeyAndConfigBlob()) {
            rows = stream.toList();
        }

        assertEquals(rows.size(), 2);
        Set<String> keys = rows.stream().map(DbRepoKeyConfigBlob::repositoryKey).collect(Collectors.toSet());
        assertTrue(keys.contains("alpha-repo"));
        assertTrue(keys.contains("zebra-repo"));
    }

    /**
     * Pins the precondition the active-transition recompute relies on: transparent_repo_config has no
     * repo_type_v2 column, so even if a config is inserted carrying a non-null shadow value, it is dropped
     * and read back as null. TransparentRepositoryConfigServiceImpl#transitionReposToActive reconstructs it
     * from the preserved type. If this assertion ever fails (e.g. a shadow column was added), revisit that
     * recompute so it does not clobber a now-persisted value.
     */
    public void testRepoTypeV2_isNotCarried_byTransparentTable() throws SQLException {
        DbRepositoryConfig federated = new DbRepositoryConfig("fed-repo", "federated", "local", "maven",
                1L, 100L, 200L, new byte[]{1, 2, 3});
        dao.insert(federated);

        Optional<DbRepositoryConfig> single = dao.findByKey("fed-repo");
        assertTrue(single.isPresent());
        assertEquals(single.get().getType(), "federated");
        assertNull(single.get().getRepoTypeV2());

        List<DbRepositoryConfig> batch = dao.findByKeys(List.of("fed-repo"));
        assertEquals(batch.size(), 1);
        assertNull(batch.get(0).getRepoTypeV2());
    }

    private DbRepositoryConfig config(String key) {
        return new DbRepositoryConfig(key, "local", null, "maven", 1L, 100L, 200L, new byte[] {1, 2, 3});
    }

    private DbRepositoryConfig configWithBlob(String key, byte[] configBlob) {
        return new DbRepositoryConfig(key, "local", null, "maven", 1L, 100L, 200L, configBlob);
    }
}
