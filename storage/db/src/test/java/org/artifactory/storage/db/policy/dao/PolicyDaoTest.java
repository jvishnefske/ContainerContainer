package org.artifactory.storage.db.policy.dao;

import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class PolicyDaoTest extends DbBaseTest {
    private static final String KEY = "key";
    private static final String TYPE = "type";
    private static final String ITEM_TYPE = "itemType";
    private static final String DESCRIPTION = "description";
    private static final String CROP_EXP = "cropExp";
    private static final String CONFIG = "config";
    private static final long CREATED = 1;
    private static final String CREATED_BY = "created_by";
    private static final long MODIFIED = 2;
    private static final String MODIFIED_BY = "modified_by";

    @Autowired
    private PolicyDao policyDao;

    @AfterMethod
    public void tearDown() throws SQLException {
        policyDao.truncateAll();
    }

    @Test
    public void insert() throws SQLException {
        assertTrue(policyDao.insert(createDbPolicyBuilder().build()));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'key' field can't be blank")
    public void insertWithInvalidKey(String key) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .key(key)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'type' field can't be blank")
    public void insertWithInvalidType(String type) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .type(type)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'item type' field can't be blank")
    public void insertWithInvalidItemType(String itemType) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .itemType(itemType)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'created by' field can't be blank")
    public void insertWithInvalidCreatedBy(String createdBy) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .createdBy(createdBy)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'config' field can't be null")
    public void insertWithNoConfig() throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .config(null)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test(dataProvider = "zeroAndNegativeLong", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'created' field can't be less or equal 0")
    public void insertWithInvalidCreatedDate(long created) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .created(created)
                .build();
        assertTrue(policyDao.insert(dbPolicy));
    }

    @Test
    public void update() throws SQLException {
        DbPolicy.DbPolicyBuilder dbPolicyBuilder = createDbPolicyBuilder();
        policyDao.insert(dbPolicyBuilder.build());

        DbPolicy dbPolicy = dbPolicyBuilder
                .modified(MODIFIED)
                .modifiedBy(MODIFIED_BY)
                .build();

        assertTrue(policyDao.update(dbPolicy));
    }

    @Test
    public void updateAllPoliciesEnablement() throws SQLException {
        DbPolicy.DbPolicyBuilder dbPolicyBuilder = createDbPolicyBuilder();
        DbPolicy originalPolicy = dbPolicyBuilder.build();
        policyDao.insert(originalPolicy);

        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0,
                System.currentTimeMillis(), "admin");
        List<String> policyKeys = List.of(originalPolicy.key());

        assertTrue(policyDao.updateAll(policyKeys, config));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'key' field can't be blank")
    public void updateWithInvalidKey(String key) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .key(key)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'type' field can't be blank")
    public void updateWithInvalidType(String type) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .type(type)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'item type' field can't be blank")
    public void updateWithInvalidItemType(String itemType) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .itemType(itemType)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'created by' field can't be blank")
    public void updateWithInvalidCreatedBy(String createdBy) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .createdBy(createdBy)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "zeroAndNegativeLong", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'created' field can't be less or equal 0")
    public void updateWithInvalidCreatedDate(long created) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .created(created)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'config' field can't be null")
    public void updateWithNoConfig() throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .config(null)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "blankString", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'modified by' field can't be blank")
    public void updateWithInvalidModifiedBy(String modifiedBy) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .modifiedBy(modifiedBy)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test(dataProvider = "zeroAndNegativeNullLong", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Policy 'modified' field can't be null or less/equal 0")
    public void updateWithInvalidModifiedDate(Long modified) throws SQLException {
        DbPolicy dbPolicy = createDbPolicyBuilder()
                .modifiedBy("test_user")
                .modified(modified)
                .build();
        assertTrue(policyDao.update(dbPolicy));
    }

    @Test
    public void getByKey() throws SQLException {
        policyDao.insert(createDbPolicyBuilder().build());

        Optional<DbPolicy> policyFromDbOptional = policyDao.getByKey(KEY);
        assertTrue(policyFromDbOptional.isPresent());
        DbPolicy policyFromDb = policyFromDbOptional.get();
        assertEquals(KEY, policyFromDb.key());
        assertEquals(TYPE, policyFromDb.type());
        assertEquals(ITEM_TYPE, policyFromDb.itemType());
        assertEquals(DESCRIPTION, policyFromDb.description());
        assertEquals(CROP_EXP, policyFromDb.cronExp());
        assertEquals(CONFIG, new String(policyFromDb.config()));
        assertTrue(policyFromDb.enabled());
    }

    @Test
    public void getByKeyNotFound() throws SQLException {
        policyDao.insert(createDbPolicyBuilder().build());
        Optional<DbPolicy> dbPolicyFromDb = policyDao.getByKey("no-policy");
        assertTrue(dbPolicyFromDb.isEmpty());
    }

    @Test
    public void deleteByKey() throws SQLException {
        policyDao.insert(createDbPolicyBuilder().build());
        assertEquals(policyDao.countAll(), 1);
        assertTrue(policyDao.deleteByKey(KEY));
        assertEquals(policyDao.countAll(), 0);
    }

    @Test
    public void countAll() throws SQLException {
        policyDao.insert(createDbPolicyBuilder().build());
        assertEquals(policyDao.countAll(), 1);
    }

    @Test
    public void countByPolicyTypeAndItemType() throws SQLException {
        assertTrue(policyDao.insert(create("key1", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("key2", PolicyType.CLEANUP, PolicyItemType.PACKAGE)));
        assertTrue(policyDao.insert(create("key3", PolicyType.ARCHIVE, PolicyItemType.PACKAGE)));
        assertTrue(policyDao.insert(create("key4", PolicyType.ARCHIVE, PolicyItemType.RELEASE_BUNDLE)));
        long count =
                policyDao.countByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.PACKAGE.getCode());
        assertEquals(1, count);
    }

    @Test
    public void testFindByPolicyTypeAndItemType() throws SQLException {
        assertTrue(policyDao.insert(create("key1", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("key2", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("key3", PolicyType.ARCHIVE, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("key4", PolicyType.ARCHIVE, PolicyItemType.RELEASE_BUNDLE)));

        List<DbPolicy> cleanupArtifactPolicies =
                policyDao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.ARTIFACT.getCode());
        assertEquals(2, cleanupArtifactPolicies.size());

        List<DbPolicy> sortedCleanupArtifactPolicies =
                cleanupArtifactPolicies.stream().sorted(Comparator.comparing(DbPolicy::key)).toList();

        assertEquals("key1", sortedCleanupArtifactPolicies.get(0).key());
        assertEquals("key2", sortedCleanupArtifactPolicies.get(1).key());

        List<DbPolicy> archiveArtifactPolicies =
                policyDao.findByPolicyTypeAndItemType(PolicyType.ARCHIVE.getCode(), PolicyItemType.ARTIFACT.getCode());

        assertEquals(1, archiveArtifactPolicies.size());
        assertEquals("key3", archiveArtifactPolicies.get(0).key());

        List<DbPolicy> archiveReleaseBundlePolicies =
                policyDao.findByPolicyTypeAndItemType(PolicyType.ARCHIVE.getCode(),
                        PolicyItemType.RELEASE_BUNDLE.getCode());

        assertEquals(1, archiveReleaseBundlePolicies.size());
        assertEquals("key4", archiveReleaseBundlePolicies.get(0).key());
    }

    @Test
    public void testFindByPolicyKeyFilter() throws SQLException {
        assertTrue(policyDao.insert(create("policy", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("mypolicy", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("policy1", PolicyType.ARCHIVE, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("unrelated", PolicyType.ARCHIVE, PolicyItemType.RELEASE_BUNDLE)));

        List<DbPolicy> policies = policyDao.findByPolicyKeyFilter("policy");

        assertEquals(3, policies.size());

        Optional<DbPolicy> unrelated =
                policies.stream().filter(dbPolicy -> dbPolicy.key().equals("unrelated")).findAny();
        assertFalse(unrelated.isPresent());
    }

    @Test
    public void testFindByTypeAndItemTypesAndPolicyKeyFilter() throws SQLException {
        assertTrue(policyDao.insert(create("policy", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("mypolicy", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("policy1", PolicyType.ARCHIVE, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("unrelated", PolicyType.ARCHIVE, PolicyItemType.RELEASE_BUNDLE)));

        List<DbPolicy> policies = policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(PolicyType.CLEANUP.getCode(),
                Set.of(PolicyItemType.ARTIFACT.getCode()), "policy");

        assertEquals(2, policies.size());

        assertExistence("policy", policies);
        assertExistence("mypolicy", policies);
    }

    @Test
    public void existsByKey() throws SQLException {
        policyDao.insert(createDbPolicyBuilder().build());

        assertTrue(policyDao.existsByKey(KEY));
        assertFalse(policyDao.existsByKey("dummy-key"));
    }

    @Test
    public void doesNotExistByKey() throws SQLException {
        assertFalse(policyDao.existsByKey("dummy-key"));
    }

    private static void assertExistence(String policyKey, List<DbPolicy> policies) {
        assertTrue(policies.stream().anyMatch(policy -> policy.key().equals(policyKey)));
    }

    private static DbPolicy.DbPolicyBuilder createDbPolicyBuilder() {
        return DbPolicy.builder()
                .key(KEY)
                .type(TYPE)
                .itemType(ITEM_TYPE)
                .enabled(true)
                .created(CREATED)
                .createdBy(CREATED_BY)
                .modified(MODIFIED)
                .modifiedBy(MODIFIED_BY)
                .cronExp(CROP_EXP)
                .description(DESCRIPTION)
                .config(CONFIG.getBytes());
    }

    @DataProvider
    private Object[][] blankString() {
        return new Object[][] {
                {""},
                {null},
                {"  "}
        };
    }

    @DataProvider
    private Object[][] zeroAndNegativeLong() {
        return new Object[][] {
                {0},
                {-1}
        };
    }

    @DataProvider
    private Object[][] zeroAndNegativeNullLong() {
        return new Object[][] {
                {0L},
                {-1L},
                {null}
        };
    }

    private static DbPolicy create(String key, PolicyType policyType, PolicyItemType itemType) {
        return DbPolicy.builder()
                .key(key)
                .type(policyType.getCode())
                .itemType(itemType.getCode())
                .created(1L)
                .createdBy("test")
                .enabled(true)
                .config("{}".getBytes())
                .build();
    }

    @Test
    public void listCleanupPoliciesForMigrationExcludesArchiveRows() throws SQLException {
        assertTrue(policyDao.insert(create("archive-policy", PolicyType.ARCHIVE, PolicyItemType.PACKAGE)));
        assertTrue(policyDao.insert(create("cleanup-a", PolicyType.CLEANUP, PolicyItemType.PACKAGE)));
        assertTrue(policyDao.insert(create("cleanup-artifact", PolicyType.CLEANUP, PolicyItemType.ARTIFACT)));
        assertTrue(policyDao.insert(create("cleanup-c", PolicyType.CLEANUP, PolicyItemType.BUILD)));

        List<DbPolicy> firstPage = policyDao.listCleanupPoliciesForMigration(null, 2);
        assertEquals(2, firstPage.size());
        assertEquals("cleanup-a", firstPage.get(0).key());
        assertEquals("cleanup-artifact", firstPage.get(1).key());

        List<DbPolicy> secondPage = policyDao.listCleanupPoliciesForMigration("cleanup-artifact", 2);
        assertEquals(1, secondPage.size());
        assertEquals("cleanup-c", secondPage.get(0).key());
    }
}