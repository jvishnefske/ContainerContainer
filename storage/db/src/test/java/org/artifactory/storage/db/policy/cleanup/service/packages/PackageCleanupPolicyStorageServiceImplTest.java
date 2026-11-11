package org.artifactory.storage.db.policy.cleanup.service.packages;

import org.artifactory.policy.PackagePolicySearchCriteria;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.dao.PolicyDao;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author vovam
 */
class PackageCleanupPolicyStorageServiceImplTest {
    private static final String KEY = "key";
    private static final String DESCRIPTION = "description";
    private static final String CROP_EXP = "cropExp";
    private static final long CREATED = 1;
    private static final String CREATED_BY = "created_by";
    private static final long MODIFIED = 2;
    private static final String MODIFIED_BY = "modified_by";
    private static final PackageCleanupPolicyConfig POLICY_CONFIG = createPackageCleanupPolicyConfig();

    @Mock
    private PolicyDao policyDao;

    @InjectMocks
    private PackageCleanupPolicyStorageServiceImpl storageService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void insert(boolean value) throws SQLException {
        when(policyDao.insert(any())).thenReturn(value);
        assertEquals(value, storageService.insert(createPolicy()));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = "CLEANUP", mode = EnumSource.Mode.EXCLUDE)
    void insertWithDifferentType(PolicyType policyType) {
        PackageCleanupPolicy policy = createPolicy();
        policy.setType(policyType);
        assertThrows(IllegalArgumentException.class, () -> storageService.insert(policy),
                "Can't perform policy storage operation with non-cleanup type");
    }

    @ParameterizedTest
    @EnumSource(value = PolicyItemType.class, names = "PACKAGE", mode = EnumSource.Mode.EXCLUDE)
    void insertWithDifferentItemType(PolicyItemType itemType) {
        PackageCleanupPolicy policy = createPolicy();
        policy.setItemType(itemType);
        assertThrows(IllegalArgumentException.class, () -> storageService.insert(policy),
                "Can't perform policy storage operation with non-package type");
    }

    @Test
    void insertDaoThrowsException() throws SQLException {
        when(policyDao.insert(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.insert(createPolicy()),
                "Unable to insert new package cleanup policy with key: " + KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void update(boolean value) throws SQLException {
        when(policyDao.update(any())).thenReturn(value);
        assertEquals(value, storageService.update(createPolicy()));
    }

    //@ParameterizedTest
    //@EnumSource(value = PolicyType.class, names = "CLEANUP", mode = EnumSource.Mode.EXCLUDE)
    //void updateWithDifferentType(PolicyType policyType) {
    //    PackageCleanupPolicy policy = createPolicy();
    //    policy.setType(policyType);
    //    assertThrows(IllegalArgumentException.class, () -> storageService.update(policy),
    //            "Can't perform policy storage operation with non-cleanup type");
    //}

    //@ParameterizedTest
    //@EnumSource(value = PolicyItemType.class, names = "PACKAGE", mode = EnumSource.Mode.EXCLUDE)
    //void updateWithDifferentItemType(PolicyItemType itemType) {
    //    PackageCleanupPolicy policy = createPolicy();
    //    policy.setItemType(itemType);
    //    assertThrows(IllegalArgumentException.class, () -> storageService.update(policy),
    //            "Can't perform policy storage operation with non-package type");
    //}

    @Test
    void updateDaoThrowsException() throws SQLException {
        when(policyDao.update(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.update(createPolicy()),
                "Unable to update package cleanup policy with key: " + KEY);
    }

    @Test
    void getByKey() throws SQLException {
        when(policyDao.getByKey(any())).thenReturn(Optional.of(createDbPolicy()));
        Optional<PackageCleanupPolicy> policyOptional = storageService.getByKey(KEY);
        assertTrue(policyOptional.isPresent());
        PackageCleanupPolicy policy = policyOptional.get();

        assertEquals(policy.getKey(), KEY);
        assertEquals(policy.getDescription(), DESCRIPTION);
        assertEquals(policy.getCronExp(), CROP_EXP);
        assertEquals(policy.getCreated(), CREATED);
        assertEquals(policy.getCreatedBy(), CREATED_BY);
        assertEquals(policy.getItemType(), PolicyItemType.PACKAGE);
        assertEquals(policy.getType(), PolicyType.CLEANUP);
        assertEquals(policy.getModified().longValue(), MODIFIED);
        assertEquals(policy.getModifiedBy(), MODIFIED_BY);
        assertTrue(policy.isEnabled());

        assertEquals(policy.getConfig().searchCriteria().getPackageTypes(),
                POLICY_CONFIG.searchCriteria().getPackageTypes());
        assertEquals(policy.getConfig().searchCriteria().getRepos(), POLICY_CONFIG.searchCriteria().getRepos());
        assertEquals(policy.getConfig().searchCriteria().getCreatedBeforeInMonths(),
                POLICY_CONFIG.searchCriteria().getCreatedBeforeInMonths());
        assertEquals(policy.getConfig().searchCriteria().getLastDownloadedBeforeInMonths(),
                POLICY_CONFIG.searchCriteria().getLastDownloadedBeforeInMonths());
    }

    @Test
    void getByKeyReturnsEmpty() throws SQLException {
        when(policyDao.getByKey(any())).thenReturn(Optional.empty());
        assertTrue(storageService.getByKey(KEY).isEmpty());
    }

    @Test
    void getByKeyDaoThrowsException() throws SQLException {
        when(policyDao.getByKey(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.getByKey(KEY),
                "Unable to get package cleanup policy with key: " + KEY);
    }

    @Test
    void getByKeyWithInvalidType() throws SQLException {
        DbPolicy dbPolicy = createDbPolicy("anytype", PolicyItemType.PACKAGE.getCode());
        when(policyDao.getByKey(any())).thenReturn(Optional.of(dbPolicy));
        assertThrows(IllegalArgumentException.class, () -> storageService.getByKey(KEY),
                "Can't get policy by key " + KEY + " since type is not cleanup");
    }

    //@Test
    //void getByKeyWithInvalidItemType() throws SQLException {
    //    DbPolicy dbPolicy = createDbPolicy(PolicyType.CLEANUP.getCode(), "anyitemtype");
    //    when(policyDao.getByKey(any())).thenReturn(Optional.of(dbPolicy));
    //    assertThrows(IllegalArgumentException.class, () -> storageService.getByKey(KEY),
    //            "Can't get policy by key " + KEY + " since item type is not package");
    //}

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void deleteByKey(boolean value) throws SQLException {
        when(policyDao.deleteByKey(any())).thenReturn(value);
        assertEquals(value, storageService.deleteByKey(KEY));
    }

    @Test
    void deleteByKeyDaoThrowsException() throws SQLException {
        when(policyDao.deleteByKey(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.deleteByKey(KEY),
                "Unable to delete package cleanup policy with key: " + KEY);
    }

    @Test
    void getAll() throws SQLException {
        when(policyDao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                PolicyItemType.PACKAGE.getCode())).thenReturn(List.of(createDbPolicy()));
        List<PackageCleanupPolicy> policies = storageService.getAll();
        assertEquals(1, policies.size());
        PackageCleanupPolicy policy = policies.get(0);
        assertEquals(policy.getKey(), KEY);
        assertEquals(policy.getDescription(), DESCRIPTION);
        assertEquals(policy.getCronExp(), CROP_EXP);
        assertEquals(policy.getCreated(), CREATED);
        assertEquals(policy.getCreatedBy(), CREATED_BY);
        assertEquals(policy.getItemType(), PolicyItemType.PACKAGE);
        assertEquals(policy.getType(), PolicyType.CLEANUP);
        assertEquals(policy.getModified().longValue(), MODIFIED);
        assertEquals(policy.getModifiedBy(), MODIFIED_BY);
        assertTrue(policy.isEnabled());
        //assertEquals(policy.getConfig(), POLICY_CONFIG);
    }

    @Test
    void getAllThrowsException() throws SQLException {
        when(policyDao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                PolicyItemType.PACKAGE.getCode())).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException = assertThrows(StorageException.class, () -> storageService.getAll());
        assertEquals("Unable to find policies by type and item type", storageException.getMessage());
    }

    @Test
    void findByPolicyKeyFilter() throws SQLException {
        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(PolicyType.CLEANUP.getCode(),
                Set.of(PolicyItemType.PACKAGE.getCode()), "policyKey")).thenReturn(List.of(createDbPolicy()));
        List<PackageCleanupPolicy> policies =
                storageService.findByPolicyKeyFilter("policyKey");
        assertEquals(1, policies.size());
        PackageCleanupPolicy policy = policies.get(0);
        assertEquals(policy.getKey(), KEY);
        assertEquals(policy.getDescription(), DESCRIPTION);
        assertEquals(policy.getCronExp(), CROP_EXP);
        assertEquals(policy.getCreated(), CREATED);
        assertEquals(policy.getCreatedBy(), CREATED_BY);
        assertEquals(policy.getItemType(), PolicyItemType.PACKAGE);
        assertEquals(policy.getType(), PolicyType.CLEANUP);
        assertEquals(policy.getModified().longValue(), MODIFIED);
        assertEquals(policy.getModifiedBy(), MODIFIED_BY);
        assertTrue(policy.isEnabled());
        //assertEquals(policy.getConfig(), POLICY_CONFIG);
    }

    @Test
    void findByPolicyKeyFilterThrowsException() throws SQLException {
        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(PolicyType.CLEANUP.getCode(),
                Set.of(PolicyItemType.PACKAGE.getCode()), "policyKey")).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> storageService.findByPolicyKeyFilter("policyKey"));
        assertEquals("Unable to find policies by policy key filter: policyKey", storageException.getMessage());
    }

    @Test
    void existByKeyDaoThrowsException() throws SQLException {
        when(policyDao.existsByKey(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.existsByKey(KEY),
                "Unable to check if policy exists by key: " + KEY);
    }

    @Test
    void testGetTotalCount() throws SQLException {
        when(policyDao.countByPolicyTypeAndItemType(any(), any())).thenReturn(23L);
        assertEquals(23, storageService.getTotalCount());
    }

    @Test
    void testGetTotalCountHandlesSqlException() throws SQLException {
        when(policyDao.countByPolicyTypeAndItemType(any(), any())).thenThrow(new StorageException("test"));
        assertThrows(StorageException.class, () -> storageService.getTotalCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void existByKey(boolean exist) throws SQLException {
        when(policyDao.existsByKey(any())).thenReturn(exist);
        assertEquals(storageService.existsByKey(KEY), exist);
    }

    @Test
    void disableAllSuccessfullyUpdatesPolicies() throws SQLException {
        List<String> policyKeys = List.of("key1", "key2");
        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0, System.currentTimeMillis(), "admin");

        when(policyDao.updateAll(policyKeys, config)).thenReturn(true);

        assertDoesNotThrow(() -> storageService.disableAll(policyKeys, config));
        verify(policyDao, times(1)).updateAll(policyKeys, config);
    }

    @Test
    void disableAllThrowsStorageExceptionOnSqlException() throws SQLException {
        List<String> policyKeys = List.of("key1", "key2");
        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0, System.currentTimeMillis(), "admin");

        when(policyDao.updateAll(policyKeys, config)).thenThrow(SQLException.class);

        assertThrows(StorageException.class, () -> storageService.disableAll(policyKeys, config));
    }

    private static PackageCleanupPolicy createPolicy() {
        PackageCleanupPolicy policy = new PackageCleanupPolicy();
        policy.setKey(KEY);
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setEnabled(true);
        policy.setDescription(DESCRIPTION);
        policy.setCronExp(CROP_EXP);
        policy.setCreated(CREATED);
        policy.setCreatedBy(CREATED_BY);
        policy.setModifiedBy(MODIFIED_BY);
        policy.setModified(MODIFIED);
        policy.setConfig(POLICY_CONFIG);
        return policy;
    }

    private static PackageCleanupPolicyConfig createPackageCleanupPolicyConfig() {
        PackagePolicySearchCriteria searchCriteria = PackagePolicySearchCriteria.builder()
                .packageTypes(List.of("docker"))
                .repos(List.of("repo"))
                .createdBeforeInMonths(10)
                .lastDownloadedBeforeInMonths(20)
                .build();
        PackageCleanupPolicyConfig config = PackageCleanupPolicyConfig.builder()
                .skipTrashcan(true)
                .searchCriteria(searchCriteria)
                .build();
        return config;
    }

    private static DbPolicy createDbPolicy() {
        return createDbPolicy(PolicyType.CLEANUP.getCode(), PolicyItemType.PACKAGE.getCode());
    }

    private static DbPolicy createDbPolicy(String type, String itemType) {
        return DbPolicy.builder().key(KEY)
                .type(type)
                .itemType(itemType)
                .enabled(true)
                .created(CREATED)
                .createdBy(CREATED_BY)
                .modified(MODIFIED)
                .modifiedBy(MODIFIED_BY)
                .cronExp(CROP_EXP)
                .description(DESCRIPTION)
                .config(JsonUtils.getInstance().valueToByteArray(POLICY_CONFIG))
                .build();
    }
}