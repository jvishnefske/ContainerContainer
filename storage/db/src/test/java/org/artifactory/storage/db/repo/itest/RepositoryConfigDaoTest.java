package org.artifactory.storage.db.repo.itest;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.repo.dao.RepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepoKeyConfigBlob;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamar Rosen
 */
@Test
public class RepositoryConfigDaoTest extends DbBaseTest {
    @Autowired
    private RepositoryConfigDao repositoryConfigDao;

    RepositoryConfigMapperImpl repositoryConfigMapper;

    @BeforeMethod
    public void setUp() throws SQLException {
        repositoryConfigMapper = new RepositoryConfigMapperImpl();
    }

    @AfterMethod
    public void tearDown() throws SQLException {
        repositoryConfigDao.deleteAll();
    }

    public void testBulkDelete() throws SQLException {
        testBulkInsertRepositoryConfig();
        List<String> repoKeysToDelete = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            repoKeysToDelete.add("key" + i);
        }

        repositoryConfigDao.bulkDelete(repoKeysToDelete);
        List<DbRepositoryConfig> retrievedRepoConfigs = repositoryConfigDao.getAll();
        assertEquals(retrievedRepoConfigs.size(), 5);
    }

    public void testBulkInsertRepositoryConfig() throws SQLException {
        List<DbRepositoryConfig> repoConfigs = getDbRepositoryConfigs();
        repositoryConfigDao.bulkInsert(repoConfigs);
        List<DbRepositoryConfig> retrievedRepoConfigs = repositoryConfigDao.getAll();
        assertEquals(repoConfigs.size(), retrievedRepoConfigs.size());
        for (int i = 0; i < 10; i++) {
            EqualsBuilder.reflectionEquals(repoConfigs.get(i), retrievedRepoConfigs.get(i));
        }
    }

    @Nonnull
    private List<DbRepositoryConfig> getDbRepositoryConfigs() {
        List<DbRepositoryConfig> repoConfigs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            repoConfigs.add(dbRepo("key" + i, "type" + i,
                    RepoType.values()[i].getType(), i, i, i,
                    JsonUtils.getInstance().valueToByteArray(new TestBlob(2))));
        }
        return repoConfigs;
    }

    // repo_type_v2 is irrelevant to these DAO tests; production forward-fills it via the mapper/DAO, so it stays null.
    private static DbRepositoryConfig dbRepo(String key, String type, String packageType, long revision,
            long created, long modified, byte[] repositoryInfoBlob) {
        return new DbRepositoryConfig(key, type, null, packageType, revision, created, modified, repositoryInfoBlob);
    }

    public void findRepositoryConfigByKey() throws SQLException {
        List<DbRepositoryConfig> repoConfigs = new ArrayList<>();
        DbRepositoryConfig repoToRetrieve = dbRepo("key-to-retrieve", "local",
                RepoType.Gradle.getType(), 1, 2, 3, JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));
        repoConfigs.add(repoToRetrieve);
        for (int i = 0; i < 10; i++) {
            repoConfigs.add(dbRepo("key" + i, "type" + i,
                    RepoType.values()[i].getType(), i, i, i,
                    JsonUtils.getInstance().valueToByteArray(new TestBlob(2))));
        }
        repositoryConfigDao.bulkInsert(repoConfigs);
        Optional<DbRepositoryConfig> retrievedRepoConfig = repositoryConfigDao.findRepositoryByKey("key-to-retrieve");
        assertTrue(retrievedRepoConfig.isPresent());
        EqualsBuilder.reflectionEquals(retrievedRepoConfig, retrievedRepoConfig);
    }

    public void testInsertRepositoryConfig() throws SQLException {
        LocalRepoConfig repositoryConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        repositoryConfig.getBaseConfig().setModelVersion(0);
        repositoryConfig.getPackageTypeConfig().put("modelVersion", "0");
        DbRepositoryConfig insertMe =
                repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);

        DbRepositoryConfig repoConfig = dbRepo("my-repo", "local", RepoType.Maven.getType(),
                1, 2, 3,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        repositoryConfigDao.insert(insertMe);
        Optional<DbRepositoryConfig> retrievedRepoConfig = repositoryConfigDao.findRepositoryByKey("my-repo");
        assertTrue(retrievedRepoConfig.isPresent());
        EqualsBuilder.reflectionEquals(retrievedRepoConfig, repoConfig);
    }

    public void testUpdateRepositoryConfigBlob() throws SQLException {
        long originalCreated = 2L;
        // insert repo
        DbRepositoryConfig repoConfig =
                dbRepo("my-repo", "local", RepoType.Maven.getType(), 1, originalCreated,
                        3, JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        repositoryConfigDao.insert(repoConfig);

        // get repo
        Optional<DbRepositoryConfig> optionalRetrievedRepoConfig = repositoryConfigDao.findRepositoryByKey("my-repo");
        assertTrue(optionalRetrievedRepoConfig.isPresent());
        DbRepositoryConfig retrievedRepoConfig = optionalRetrievedRepoConfig.get();
        RepositoryConfigDaoTest.TestBlob returnedTestBlob =
                JsonUtils.getInstance().readValue(retrievedRepoConfig.getRepositoryInfoBlob(),
                        RepositoryConfigDaoTest.TestBlob.class);
        long num = returnedTestBlob.getNum();
        assertEquals(num, 1L);

        // update config blob
        retrievedRepoConfig.setCreated(100L);
        returnedTestBlob.setNum(2L);
        retrievedRepoConfig.setRepositoryInfoBlob(JsonUtils.getInstance().valueToByteArray(returnedTestBlob));
        repositoryConfigDao.update(retrievedRepoConfig);

        // get repo and validate config blob was updated
        Optional<DbRepositoryConfig> optionalUpdatedRepoConfig = repositoryConfigDao.findRepositoryByKey("my-repo");
        assertTrue(optionalUpdatedRepoConfig.isPresent());
        DbRepositoryConfig updatedRepoConfig = optionalUpdatedRepoConfig.get();
        RepositoryConfigDaoTest.TestBlob updatedTestBlob =
                JsonUtils.getInstance().readValue(updatedRepoConfig.getRepositoryInfoBlob(),
                        RepositoryConfigDaoTest.TestBlob.class);
        assertEquals(updatedTestBlob.getNum(), 2L);
        assertEquals(updatedRepoConfig.getCreated(), originalCreated);
    }

    public void testInsertAndUpdateRoundTripsRepoTypeV2() throws SQLException {
        // federated repo persists repo_type_v2 = local (forward-fill)
        DbRepositoryConfig repoConfig = new DbRepositoryConfig("fed-repo", "federated", "local",
                RepoType.Maven.getType(), 1, 2, 3, JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        repositoryConfigDao.insert(repoConfig);

        Optional<DbRepositoryConfig> retrieved = repositoryConfigDao.findRepositoryByKey("fed-repo");
        assertTrue(retrieved.isPresent());
        assertEquals(retrieved.get().getType(), "federated");
        assertEquals(retrieved.get().getRepoTypeV2(), "local");

        // update round-trips the new column
        DbRepositoryConfig toUpdate = retrieved.get();
        toUpdate.setRepoTypeV2("remote");
        toUpdate.setModified(99L);
        repositoryConfigDao.update(toUpdate);

        Optional<DbRepositoryConfig> updated = repositoryConfigDao.findRepositoryByKey("fed-repo");
        assertTrue(updated.isPresent());
        assertEquals(updated.get().getRepoTypeV2(), "remote");
    }

    /**
     * Verifies that bulkUpdate correctly persists an enriched federated blob (members in the new
     * baseConfig.federationConfig location) and the repo_type_v2 column. Starts from a legacy row that
     * has null repo_type_v2 and members only in the old repoTypeConfig location, applies the dual-write
     * setters the production enrichment uses, then asserts the DAO round-trip.
     * <p>
     * The guard logic that selects which rows to enrich (isColdFederatedRepo, toFederatedRepoConfig) is
     * tested in FederatedRepoConfigEnrichmentServiceImplTest — not reimplemented here.
     */
    public void testEnrichColdFederatedBlobRoundTripsBlobAndRepoTypeV2() throws SQLException {
        String federatedType = RepoDetailsType.FEDERATED.typeNameLowercase();
        FederatedRepoConfig coldRepo = FederatedRepoConfig.create("cold-fed", RepoType.Generic);
        FederatedMember legacyMember = new FederatedMember();
        legacyMember.setUrl("http://other:8081/artifactory/cold-fed");
        coldRepo.getRepoTypeConfig().getFederatedMembers().add(legacyMember);
        // Seed a legacy row: federated type with null repo_type_v2 and members only in the old location.
        repositoryConfigDao.insert(dbRepo("cold-fed", federatedType, RepoType.Generic.getType(),
                1L, 2L, 3L, JsonUtils.getInstance().valueToByteArray(coldRepo)));

        Optional<DbRepositoryConfig> before = repositoryConfigDao.findRepositoryByKey("cold-fed");
        assertTrue(before.isPresent());
        assertNull(before.get().getRepoTypeV2());

        // Apply the production dual-write setters directly — no stream pipeline, no guard reimplementation.
        FederatedRepoConfig repoConfig =
                (FederatedRepoConfig) repositoryConfigMapper.dbInfoBlobToRepositoryConfig(before.get());
        repoConfig.setFederatedMembers(repoConfig.getFederatedMembers());
        repoConfig.setModificationDate(repoConfig.getModificationDate());
        repoConfig.setModification(System.currentTimeMillis());
        repositoryConfigDao.bulkUpdate(
                List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig)));

        Optional<DbRepositoryConfig> after = repositoryConfigDao.findRepositoryByKey("cold-fed");
        assertTrue(after.isPresent());
        assertEquals(after.get().getRepoTypeV2(), RepoDetailsType.LOCAL.typeNameLowercase());
        FederatedRepoConfig enrichedConfig =
                (FederatedRepoConfig) repositoryConfigMapper.dbInfoBlobToRepositoryConfig(after.get());
        assertEquals(enrichedConfig.getBaseConfig().getFederationConfig().getMembers().size(), 1);
        assertEquals(enrichedConfig.getBaseConfig().getFederationConfig().getMembers().get(0).getUrlOrTemplate(),
                legacyMember.getUrlOrTemplate());
    }

    public void testDeleteByKey() throws SQLException {
        repositoryConfigDao.insert(dbRepo("delete-me", "local", RepoType.YUM.getType(), 1,
                2, 3, JsonUtils.getInstance().valueToByteArray(new TestBlob(2))));
        int deleted = repositoryConfigDao.delete("does-not-exist");
        assertEquals(deleted, 0);

        deleted = repositoryConfigDao.delete("delete-me");
        assertEquals(deleted, 1);
    }

    public void testExists() throws SQLException {
        DbRepositoryConfig repoConfig = dbRepo("my-repo", "local", RepoType.Maven.getType(),
                1, 2, 3,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        repositoryConfigDao.insert(repoConfig);
        assertTrue(repositoryConfigDao.exists("my-repo"));
        assertFalse(repositoryConfigDao.exists("does-not-exist"));
    }

    public void testBulkUpdate() throws SQLException {
        // insert repos
        List<DbRepositoryConfig> repoConfigs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            repoConfigs.add(dbRepo("key" + i, "type" + i,
                    RepoType.values()[i].getType(), i, i, i,
                    JsonUtils.getInstance().valueToByteArray(new TestBlob(i))));
        }
        repositoryConfigDao.bulkInsert(repoConfigs);

        // get repos
        List<DbRepositoryConfig> reposToUpdate = repositoryConfigDao.getAll();
        for (int i = 0; i < reposToUpdate.size(); i++) {
            RepositoryConfigDaoTest.TestBlob returnedTestBlob =
                    JsonUtils.getInstance().readValue(reposToUpdate.get(i).getRepositoryInfoBlob(),
                            RepositoryConfigDaoTest.TestBlob.class);
            assertEquals(returnedTestBlob.getNum(), i);

            // update repos
            returnedTestBlob.setNum(i + 1);
            reposToUpdate.get(i)
                    .setRepositoryInfoBlob(JsonUtils.getInstance().valueToByteArray(returnedTestBlob));
        }

        repositoryConfigDao.bulkUpdate(reposToUpdate);

        // get repos and validate config blob was updated
        List<DbRepositoryConfig> retrievedRepoConfigs = repositoryConfigDao.getAll();
        for (int i = 0; i < retrievedRepoConfigs.size(); i++) {
            RepositoryConfigDaoTest.TestBlob returnedTestBlob =
                    JsonUtils.getInstance().readValue(retrievedRepoConfigs.get(i).getRepositoryInfoBlob(),
                            RepositoryConfigDaoTest.TestBlob.class);
            assertEquals(returnedTestBlob.getNum(), i + 1);
        }
    }

    public void testGetConfigsByKeys() throws SQLException {
        List<DbRepositoryConfig> repoConfigs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            repoConfigs.add(dbRepo("key" + i, "type" + i,
                    RepoType.values()[i].getType(), i, i, i,
                    JsonUtils.getInstance().valueToByteArray(new TestBlob(i))));
        }
        repositoryConfigDao.bulkInsert(repoConfigs);

        Set<DbRepositoryConfig> configsByKeys = repositoryConfigDao.findByKeysWithPagination(
                Set.of("key0", "key1", "key2", "key3", "non-existing-repo"));
        Set<DbRepositoryConfig> dbRepositoryConfigs = new HashSet<>(configsByKeys);

        Set<DbRepositoryConfig> expectedRepos = new HashSet<>(repoConfigs.subList(0, 4));
        assertEquals(dbRepositoryConfigs, expectedRepos);
    }

    public void testFindConfigsByDetailsType() throws SQLException {
        List<DbRepositoryConfig> repoConfigs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            repoConfigs.add(dbRepo("key" + i, "type" + i,
                    RepoType.values()[i].getType(), i, i, i,
                    JsonUtils.getInstance().valueToByteArray(new TestBlob(i))));
        }
        repositoryConfigDao.bulkInsert(repoConfigs);

        Collection<DbRepositoryConfig> configsByKeys = repositoryConfigDao.findRepositoriesByDetailsType("type1");
        assertEquals(configsByKeys.size(), 1);
        assertEquals(configsByKeys.stream().findFirst().get().getType(), "type1");
    }

    public void testCountAll() throws SQLException {
        assertEquals(repositoryConfigDao.countAll(), 0);

        DbRepositoryConfig repoConfig = dbRepo("my-repo", "local", RepoType.Maven.getType(),
                1L, 2L, 3L,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        DbRepositoryConfig repoConfig1 = dbRepo("my-repo1", "remote", RepoType.Conan.getType(),
                10, 20, 30,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));

        repositoryConfigDao.insert(repoConfig);
        repositoryConfigDao.insert(repoConfig1);
        assertEquals(repositoryConfigDao.countAll(), 2);
    }

    public void testGetAllRepoKeys() throws SQLException {
        DbRepositoryConfig repoConfig = dbRepo("all-repo-1", "local", RepoType.Maven.getType(),
                1L, 2L, 3L,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        DbRepositoryConfig repoConfig1 = dbRepo("all-repo-2", "remote", RepoType.Conan.getType(),
                10, 20, 30,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));

        repositoryConfigDao.insert(repoConfig);
        repositoryConfigDao.insert(repoConfig1);
        Set<String> repoKeys = repositoryConfigDao.getAllRepoKeys();
        assertEquals(repoKeys.size(), 2);
        assertTrue(CollectionUtils.isEqualCollection(repoKeys, Set.of("all-repo-1", "all-repo-2")));

    }

    public void getAllReleaseBundlesV2RepoKeys() throws SQLException {
        DbRepositoryConfig repoConfig =
                dbRepo(DEFAULT_RBV2_REPO, "local", RepoType.ReleaseBundles.getType(),
                        1L, 2L, 3L, JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        DbRepositoryConfig repoConfig1 =
                dbRepo("project-release-bundles-v2", "local", RepoType.ReleaseBundles.getType(),
                        10, 20, 30, JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));
        DbRepositoryConfig repoConfig2 =
                dbRepo("release-bundles-v1", RELEASE_BUNDLE.typeNameLowercase(),
                        RepoType.ReleaseBundles.getType(),
                        10, 20, 30, JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));
        DbRepositoryConfig repoConfig3 =
                dbRepo("any-repo", "local", RepoType.Maven.getType(),
                        10, 20, 30, JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));
        DbRepositoryConfig invalidType =
                dbRepo("projectb-release-bundles-v2", RELEASE_BUNDLE.typeNameLowercase(),
                        RepoType.Maven.getType(),
                        10, 20, 30, JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));

        repositoryConfigDao.insert(repoConfig);
        repositoryConfigDao.insert(repoConfig1);
        repositoryConfigDao.insert(repoConfig2);
        repositoryConfigDao.insert(invalidType);
        Set<String> repoKeys = repositoryConfigDao.getAllReleaseBundlesV2RepoKeys();
        assertEquals(repoKeys.size(), 2);
        assertTrue(CollectionUtils.isEqualCollection(repoKeys,
                Set.of(DEFAULT_RBV2_REPO, "project-release-bundles-v2")));

    }

    public void testFindKeysFromSet() throws SQLException {
        assertEquals(repositoryConfigDao.countAll(), 0);

        DbRepositoryConfig repoConfig = dbRepo("my-repo", "local", RepoType.Maven.getType(),
                1L, 2L, 3L,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(1)));
        DbRepositoryConfig repoConfig1 = dbRepo("my-repo1", "remote", RepoType.Conan.getType(),
                10, 20, 30,
                JsonUtils.getInstance().valueToByteArray(new TestBlob(2)));

        repositoryConfigDao.insert(repoConfig);
        repositoryConfigDao.insert(repoConfig1);

        Set<String> keys = repositoryConfigDao.findKeysFromSet(Set.of("my-repo", "my-repo1", "my-repo2"));
        assertEquals(keys.size(), 2);
        assertTrue(keys.contains("my-repo"));
        assertTrue(keys.contains("my-repo1"));
    }

    public void testStreamKeyAndConfigBlob_returnsEmptyWhenNoRepos() throws SQLException {
        try (var stream = repositoryConfigDao.streamKeyAndConfigBlob()) {
            assertEquals(stream.count(), 0);
        }
    }

    public void testStreamKeyAndConfigBlob_returnsAllRowsWithConfigBlob() throws SQLException {
        byte[] alphaBlob = JsonUtils.getInstance().valueToByteArray(new TestBlob(1));
        byte[] zebraBlob = JsonUtils.getInstance().valueToByteArray(new TestBlob(2));

        repositoryConfigDao.insert(new DbRepositoryConfig("zebra-repo", "local", null, RepoType.Generic.getType(),
                1, 1, 1, zebraBlob));
        repositoryConfigDao.insert(new DbRepositoryConfig("alpha-repo", "local", null, RepoType.Generic.getType(),
                2, 2, 2, alphaBlob));

        List<DbRepoKeyConfigBlob> rows;
        try (var stream = repositoryConfigDao.streamKeyAndConfigBlob()) {
            rows = stream.toList();
        }

        assertEquals(rows.size(), 2);
        Set<String> keys = rows.stream().map(DbRepoKeyConfigBlob::repositoryKey).collect(Collectors.toSet());
        assertTrue(keys.contains("alpha-repo"));
        assertTrue(keys.contains("zebra-repo"));
    }

    @Data
    @AllArgsConstructor
    public static class TestBlob {
        private long num;
    }
}