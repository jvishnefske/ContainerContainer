package org.artifactory.storage.db.policy.retention.service.packages;

import org.artifactory.policy.PackagePolicySearchCriteria;
import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyConfig;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.dao.PolicyDao;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagePolicyStorageServiceImplTest {

    @Mock
    private PolicyDao policyDao;

    @InjectMocks
    private PackagePolicyStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testGetAll(PolicyType policyType) throws SQLException {
        PolicyItemType itemType = PolicyItemType.PACKAGE;
        List<DbPolicy> expectedPolicies = List.of(createDbPolicy(policyType.getCode(), itemType.getCode()));

        when(policyDao.findByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode()))
                .thenReturn(expectedPolicies);

        List<DbPolicy> result = storageService.getAll(policyType, itemType);

        assertEquals(expectedPolicies, result);
        verify(policyDao).findByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode());
    }

    @Test
    void testGetAllThrowsStorageException() throws SQLException {
        PolicyType policyType = PolicyType.ARCHIVE;
        PolicyItemType itemType = PolicyItemType.PACKAGE;

        when(policyDao.findByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode()))
                .thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.getAll(policyType, itemType));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testInsert(PolicyType policyType) throws SQLException {
        Policy<?> policy = createMockPolicy(policyType);
        when(policyDao.insert(any(DbPolicy.class))).thenReturn(true);

        boolean result = storageService.insert(policy, policyType, PolicyItemType.PACKAGE);

        assertTrue(result);
        verify(policyDao).insert(any(DbPolicy.class));
    }

    @Test
    void testInsertThrowsStorageException() throws SQLException {
        Policy<?> policy = createMockPolicy(PolicyType.ARCHIVE);
        when(policyDao.insert(any(DbPolicy.class))).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.insert(policy, PolicyType.ARCHIVE,
                PolicyItemType.PACKAGE));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testFindByPartialPolicyKey(PolicyType policyType) throws SQLException {
        String partialPolicyKey = "test";
        PolicyItemType itemType = PolicyItemType.PACKAGE;
        List<DbPolicy> expectedPolicies = List.of(createDbPolicy(policyType.getCode(), itemType.getCode()));

        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(), Set.of(itemType.getCode()),
                partialPolicyKey)).thenReturn(expectedPolicies);

        List<DbPolicy> result = storageService.findByPartialPolicyKey(partialPolicyKey, policyType, itemType);

        assertEquals(expectedPolicies, result);
        verify(policyDao).findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(), Set.of(itemType.getCode()),
                partialPolicyKey);
    }

    @Test
    void testFindByPartialPolicyKeyThrowsStorageException() throws SQLException {
        String partialPolicyKey = "test";
        PolicyType policyType = PolicyType.ARCHIVE;
        PolicyItemType itemType = PolicyItemType.PACKAGE;

        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(), Set.of(itemType.getCode()),
                partialPolicyKey)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.findByPartialPolicyKey(partialPolicyKey, policyType,
                itemType));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testFindByPartialPolicyKeyAndItemTypes(PolicyType policyType) throws SQLException {
        String partialPolicyKey = "test";
        Set<PolicyItemType> itemTypes = Set.of(PolicyItemType.PACKAGE);
        List<DbPolicy> expectedPolicies = List.of(createDbPolicy(policyType.getCode(),
                PolicyItemType.PACKAGE.getCode()));

        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(),
                Set.of(PolicyItemType.PACKAGE.getCode()),
                partialPolicyKey)).thenReturn(expectedPolicies);

        List<DbPolicy> result = storageService.findByPartialPolicyKeyAndItemTypes(partialPolicyKey,
                policyType, itemTypes);

        assertEquals(expectedPolicies, result);
        verify(policyDao).findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(),
                Set.of(PolicyItemType.PACKAGE.getCode()),
                partialPolicyKey);
    }

    @Test
    void testFindByPartialPolicyKeyAndItemTypesThrowsStorageException() throws SQLException {
        String partialPolicyKey = "test";
        PolicyType policyType = PolicyType.ARCHIVE;
        Set<PolicyItemType> itemTypes = Set.of(PolicyItemType.PACKAGE);

        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(),
                Set.of(PolicyItemType.PACKAGE.getCode()),
                partialPolicyKey)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.findByPartialPolicyKeyAndItemTypes(
                partialPolicyKey, policyType, itemTypes));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testGetByKey(PolicyType policyType) throws SQLException {
        String policyKey = "test-key";
        PolicyItemType itemType = PolicyItemType.PACKAGE;
        DbPolicy expectedPolicy = createDbPolicy(policyType.getCode(), itemType.getCode());

        when(policyDao.getByKey(policyKey)).thenReturn(Optional.of(expectedPolicy));

        Optional<DbPolicy> result = storageService.getByKey(policyKey, policyType, itemType);

        assertTrue(result.isPresent());
        assertEquals(expectedPolicy, result.get());
        verify(policyDao).getByKey(policyKey);
    }

    @Test
    void testGetByKeyThrowsStorageException() throws SQLException {
        String policyKey = "test-key";
        PolicyType policyType = PolicyType.ARCHIVE;
        PolicyItemType itemType = PolicyItemType.PACKAGE;

        when(policyDao.getByKey(policyKey)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.getByKey(policyKey, policyType, itemType));
    }

    @Test
    void testDeleteByKey() throws SQLException {
        String policyKey = "test-key";
        when(policyDao.deleteByKey(policyKey)).thenReturn(true);

        boolean result = storageService.deleteByKey(policyKey);

        assertTrue(result);
        verify(policyDao).deleteByKey(policyKey);
    }

    @Test
    void testDeleteByKeyThrowsStorageException() throws SQLException {
        String policyKey = "test-key";

        when(policyDao.deleteByKey(policyKey)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.deleteByKey(policyKey));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testUpdate(PolicyType policyType) throws SQLException {
        Policy<?> policy = createMockPolicy(policyType);
        when(policyDao.update(any(DbPolicy.class))).thenReturn(true);

        boolean result = storageService.update(policy, policyType, PolicyItemType.PACKAGE);

        assertTrue(result);
        verify(policyDao).update(any(DbPolicy.class));
    }

    @Test
    void testUpdateThrowsStorageException() throws SQLException {
        Policy<?> policy = createMockPolicy(PolicyType.ARCHIVE);
        when(policyDao.update(any(DbPolicy.class))).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.update(policy, PolicyType.ARCHIVE,
                PolicyItemType.PACKAGE));
    }

    @Test
    void testDisableAll() throws SQLException {
        List<String> policyKeys = List.of("key1", "key2");
        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0, System.currentTimeMillis(), "admin");

        storageService.disableAll(policyKeys, config);

        verify(policyDao).updateAll(policyKeys, config);
    }

    @Test
    void testDisableAllThrowsStorageException() throws SQLException {
        List<String> policyKeys = List.of("key1", "key2");
        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0, System.currentTimeMillis(), "admin");

        doThrow(new SQLException("Database error")).when(policyDao).updateAll(policyKeys, config);

        assertThrows(StorageException.class, () -> storageService.disableAll(policyKeys, config));
    }

    @Test
    void testExistsByKey() throws SQLException {
        String policyKey = "test-key";
        when(policyDao.existsByKey(policyKey)).thenReturn(true);

        boolean result = storageService.existsByKey(policyKey);

        assertTrue(result);
        verify(policyDao).existsByKey(policyKey);
    }

    @Test
    void testExistsByKeyThrowsStorageException() throws SQLException {
        String policyKey = "test-key";

        when(policyDao.existsByKey(policyKey)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.existsByKey(policyKey));
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = {"ARCHIVE", "CLEANUP"})
    void testGetTotalCount(PolicyType policyType) throws SQLException {
        PolicyItemType itemType = PolicyItemType.PACKAGE;
        long expectedCount = 10L;

        when(policyDao.countByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode()))
                .thenReturn(expectedCount);

        long result = storageService.getTotalCount(policyType, itemType);

        assertEquals(expectedCount, result);
        verify(policyDao).countByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode());
    }

    @Test
    void testGetTotalCountThrowsStorageException() throws SQLException {
        PolicyType policyType = PolicyType.ARCHIVE;
        PolicyItemType itemType = PolicyItemType.PACKAGE;

        when(policyDao.countByPolicyTypeAndItemType(policyType.getCode(), itemType.getCode()))
                .thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> storageService.getTotalCount(policyType, itemType));
    }

    @Test
    void testGetAllEmptyResult() throws SQLException {
        when(policyDao.findByPolicyTypeAndItemType(any(), any())).thenReturn(List.of());
        List<DbPolicy> result = storageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindByPartialPolicyKeyNoMatches() throws SQLException {
        when(policyDao.findByTypeAndItemTypesAndPolicyKeyFilter(any(), any(), any())).thenReturn(List.of());
        List<DbPolicy> result = storageService.findByPartialPolicyKey("nonexistent", PolicyType.ARCHIVE,
                PolicyItemType.PACKAGE);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetByKeyNotFound() throws SQLException {
        when(policyDao.getByKey(any())).thenReturn(Optional.empty());
        Optional<DbPolicy> result = storageService.getByKey("nonexistent", PolicyType.ARCHIVE, PolicyItemType.PACKAGE);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateMismatchedPolicyType() {
        Policy<?> policy = createMockPolicy(PolicyType.ARCHIVE);
        assertThrows(IllegalArgumentException.class, () ->
                storageService.update(policy, PolicyType.CLEANUP, PolicyItemType.PACKAGE));
    }

    @Test
    void testDisableAllEmptyList() throws SQLException {
        UpdateAllPoliciesConfig config = new UpdateAllPoliciesConfig(0, System.currentTimeMillis(),
                "admin");
        storageService.disableAll(List.of(), config);
        verify(policyDao).updateAll(List.of(), config);
    }

    @Test
    void testExistsByKeyNotFound() throws SQLException {
        when(policyDao.existsByKey(any())).thenReturn(false);
        assertFalse(storageService.existsByKey("nonexistent"));
    }

    @Test
    void testGetTotalCountZero() throws SQLException {
        when(policyDao.countByPolicyTypeAndItemType(any(), any())).thenReturn(0L);
        assertEquals(0L, storageService.getTotalCount(PolicyType.ARCHIVE, PolicyItemType.PACKAGE));
    }

    private DbPolicy createDbPolicy(String type, String itemType) {
        return DbPolicy.builder()
                .key("test-key")
                .type(type)
                .itemType(itemType)
                .enabled(true)
                .created(System.currentTimeMillis())
                .createdBy("test-user")
                .modified(System.currentTimeMillis())
                .modifiedBy("test-user")
                .cronExp("0 0 * * *")
                .description("Test policy")
                .config(new byte[] {1, 2, 3})
                .build();
    }

    private Policy<?> createMockPolicy(PolicyType policyType) {
        Policy<PolicyConfig> policy = Mockito.mock(Policy.class);
        when(policy.getKey()).thenReturn("test-key");
        when(policy.getType()).thenReturn(policyType);
        when(policy.getItemType()).thenReturn(PolicyItemType.PACKAGE);
        when(policy.getCreatedBy()).thenReturn("test-user");
        when(policy.getModifiedBy()).thenReturn("test-user");
        when(policy.getCreated()).thenReturn(System.currentTimeMillis());
        when(policy.getModified()).thenReturn(System.currentTimeMillis());
        when(policy.isEnabled()).thenReturn(true);
        when(policy.getDescription()).thenReturn("Test policy description");
        when(policy.getCronExp()).thenReturn("0 0 * * *");
        when(policy.getConfig()).thenReturn(createPackageCleanupPolicyConfig());
        when(policy.getDescription()).thenReturn("Test policy");
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
}