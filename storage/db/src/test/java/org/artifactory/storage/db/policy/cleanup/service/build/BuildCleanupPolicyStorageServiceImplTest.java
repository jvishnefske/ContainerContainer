package org.artifactory.storage.db.policy.cleanup.service.build;

import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicyConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicySearchCriteria;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author kunalm
 */
class BuildCleanupPolicyStorageServiceImplTest {
    private static final String KEY = "key";
    private static final String DESCRIPTION = "description";
    private static final String CROP_EXP = "cropExp";
    private static final long CREATED = 1;
    private static final String CREATED_BY = "created_by";
    private static final long MODIFIED = 2;
    private static final String MODIFIED_BY = "modified_by";
    private static final BuildCleanupPolicyConfig POLICY_CONFIG = createBuildCleanupPolicyConfig();

    @Mock
    private PolicyDao policyDao;

    @InjectMocks
    private BuildCleanupPolicyStorageServiceImpl storageService;

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
        BuildCleanupPolicy policy = createPolicy();
        policy.setType(policyType);
        assertThrows(IllegalArgumentException.class, () -> storageService.insert(policy),
                "Can't perform policy storage operation with non-cleanup type");
    }

    @ParameterizedTest
    @EnumSource(value = PolicyItemType.class, names = "BUILD", mode = EnumSource.Mode.EXCLUDE)
    void insertWithDifferentItemType(PolicyItemType itemType) {
        BuildCleanupPolicy policy = createPolicy();
        policy.setItemType(itemType);
        assertThrows(IllegalArgumentException.class, () -> storageService.insert(policy),
                "Can't perform policy storage operation with non-build type");
    }

    @Test
    void insertDaoThrowsException() throws SQLException {
        when(policyDao.insert(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.insert(createPolicy()),
                "Unable to insert new build cleanup policy with key: " + KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void update(boolean value) throws SQLException {
        when(policyDao.update(any())).thenReturn(value);
        assertEquals(value, storageService.update(createPolicy()));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyItemType.class, names = "BUILD", mode = EnumSource.Mode.EXCLUDE)
    void updateWithDifferentItemType(PolicyItemType itemType) {
        BuildCleanupPolicy policy = createPolicy();
        policy.setItemType(itemType);
        assertThrows(IllegalArgumentException.class, () -> storageService.update(policy),
                "Can't perform policy storage operation with non-build type");
    }

    @Test
    void updateDaoThrowsException() throws SQLException {
        when(policyDao.update(any())).thenThrow(SQLException.class);
        assertThrows(StorageException.class, () -> storageService.update(createPolicy()),
                "Unable to update build cleanup policy with key: " + KEY);
    }

    @Test
    void getByKey() throws SQLException {
        when(policyDao.getByKey(any())).thenReturn(Optional.of(createDbPolicy()));
        Optional<BuildCleanupPolicy> policyOptional = storageService.getByKey(KEY);
        assertTrue(policyOptional.isPresent());
        BuildCleanupPolicy policy = policyOptional.get();

        assertEquals(KEY, policy.getKey());
        assertEquals(DESCRIPTION, policy.getDescription());
        assertEquals(CROP_EXP, policy.getCronExp());
        assertEquals(CREATED, policy.getCreated());
        assertEquals(CREATED_BY, policy.getCreatedBy());
        assertEquals(PolicyItemType.BUILD, policy.getItemType());
        assertEquals(PolicyType.CLEANUP, policy.getType());
        assertEquals(MODIFIED, policy.getModified().longValue());
        assertEquals(MODIFIED_BY, policy.getModifiedBy());
        assertTrue(policy.isEnabled());
        assertEquals(POLICY_CONFIG, policy.getConfig());
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
                "Unable to get build cleanup policy with key: " + KEY);
    }

    @Test
    void getByKeyWithInvalidType() throws SQLException {
        DbPolicy dbPolicy = createDbPolicy("anytype", PolicyItemType.BUILD.getCode());
        when(policyDao.getByKey(any())).thenReturn(Optional.of(dbPolicy));
        assertThrows(IllegalArgumentException.class, () -> storageService.getByKey(KEY),
                "Can't get policy by key " + KEY + " since type is not cleanup");
    }

    @Test
    void getByKeyWithInvalidItemType() throws SQLException {
        DbPolicy dbPolicy = createDbPolicy(PolicyType.CLEANUP.getCode(), "anyitemtype");
        when(policyDao.getByKey(any())).thenReturn(Optional.of(dbPolicy));
        assertThrows(IllegalArgumentException.class, () -> storageService.getByKey(KEY),
                "Can't get policy by key " + KEY + " since item type is not build");
    }

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
                "Unable to delete build cleanup policy with key: " + KEY);
    }

    @Test
    void getAll() throws SQLException {
        when(policyDao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                PolicyItemType.BUILD.getCode())).thenReturn(List.of(createDbPolicy()));
        List<BuildCleanupPolicy> policies = storageService.getAll();
        assertEquals(1, policies.size());
        BuildCleanupPolicy policy = policies.getFirst();
        assertEquals(KEY, policy.getKey());
        assertEquals(DESCRIPTION, policy.getDescription());
        assertEquals(CROP_EXP, policy.getCronExp());
        assertEquals(CREATED, policy.getCreated());
        assertEquals(CREATED_BY, policy.getCreatedBy());
        assertEquals(PolicyItemType.BUILD, policy.getItemType());
        assertEquals(PolicyType.CLEANUP, policy.getType());
        assertEquals(MODIFIED, policy.getModified().longValue());
        assertEquals(MODIFIED_BY, policy.getModifiedBy());
        assertTrue(policy.isEnabled());
        assertEquals(POLICY_CONFIG, policy.getConfig());
    }

    @Test
    void getAllThrowsException() throws SQLException {
        when(policyDao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                PolicyItemType.BUILD.getCode())).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException = assertThrows(StorageException.class, () -> storageService.getAll());
        assertEquals("Unable to find policies by type and item type", storageException.getMessage());
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

    private static BuildCleanupPolicy createPolicy() {
        BuildCleanupPolicy policy = new BuildCleanupPolicy();
        policy.setKey(KEY);
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.BUILD);
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

    private static BuildCleanupPolicyConfig createBuildCleanupPolicyConfig() {
        BuildCleanupPolicySearchCriteria searchCriteria = BuildCleanupPolicySearchCriteria.builder()
                .includedBuilds(List.of(BuildCleanupPolicySearchCriteria.Build.builder()
                        .name("docker")
                        .projectKey("default")
                        .build()))
                .createdBeforeInMonths(10)
                .build();
        return BuildCleanupPolicyConfig.builder().searchCriteria(searchCriteria).build();
    }

    private static DbPolicy createDbPolicy() {
        return createDbPolicy(PolicyType.CLEANUP.getCode(), PolicyItemType.BUILD.getCode());
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