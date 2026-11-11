package org.artifactory.storage.db.policy.cleanup.service;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.RunSummaryConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupRunSummaryDao;
import org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.storage.db.policy.RetentionRunSummary.ItemType.PACKAGE;
import static org.artifactory.storage.db.policy.RetentionRunSummary.RetentionType.CLEANUP;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.COMPLETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author vovam
 */
class CleanupRunSummaryServiceImplTest {
    @Mock
    private CleanupRunSummaryDao dao;
    @Mock
    private RetentionRunSummaryDao retentionRunSummaryDao;
    @Mock
    private ArtifactoryContext context;
    @InjectMocks
    private CleanupRunSummaryServiceImpl runSummaryService;

    private MockitoSession mockitoSession;

    @BeforeEach
    void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGetRunsForInternalCleanupSuccess() throws SQLException {
        CleanupRunPolicyKeyAndRunId record = new CleanupRunPolicyKeyAndRunId("pkey", 1);

        when(dao.getRunsForInternalCleanup(2)).thenReturn(Set.of(record));
        assertEquals(1, runSummaryService.getRunsForInternalCleanup(2).size());
    }

    @Test
    void testGetRunsForInternalCleanupFail() throws SQLException {
        when(dao.getRunsForInternalCleanup(2)).thenThrow(new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> runSummaryService.getRunsForInternalCleanup(2));
        assertEquals("java.sql.SQLException: Expected exception", storageException.getMessage());
    }

    @Test
    void testMarkAsCleanedInternallyFail() throws SQLException {
        when(dao.markAsCleanedInternally("pkey", 1)).thenThrow(new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> runSummaryService.markAsCleanedInternally("pkey", 1));
        assertEquals("java.sql.SQLException: Expected exception", storageException.getMessage());
    }

    @Test
    void testMarkAsCleanedInternallyFailSuccess() throws SQLException {
        when(dao.markAsCleanedInternally("pkey", 1)).thenReturn(true);
        runSummaryService.markAsCleanedInternally("pkey", 1);
        verify(dao).markAsCleanedInternally("pkey", 1);
    }

    @Test
    void testSqlExceptionThrowsStorageException() throws SQLException {
        when(dao.getPolicyRuns(anySet(), anyLong(), anyLong(), eq(null), eq(null), anyLong(), anyLong(), anyString(),
                anyString(), anyString(), eq(null)))
                .thenThrow(new SQLException("Thrown by test"));
        Set<String> itemTypes = Set.of("itemType");
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(itemTypes, 1L, 2L, null, null, 1L, 1L, "policyKey", "ASC", "true",
                        null, null);

        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRuns(cleanupPolicyRunRequest));
        assertEquals("Failed to get cleanup policy runs by parameters. item types: [itemType], from: 1, to: 2, " +
                        "page: 1, pageSize: 1",
                storageException.getMessage());
    }

    @Test
    void testSqlExceptionThrowsStorageExceptionWithPolicyKey() throws SQLException {
        when(dao.getPolicyRuns(anySet(), anyLong(), anyLong(),
                eq("policyKey"), eq(null), anyLong(), anyLong(), anyString(), anyString(), anyString(), eq(null)))
                .thenThrow(new SQLException("Thrown by test"));
        Set<String> itemTypes = Set.of("itemType");
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(itemTypes, 1L, 2L, "policyKey", null, 1L, 1L, "policyKey", "ASC", "true",
                        null, null);

        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRuns(cleanupPolicyRunRequest));
        assertEquals(
                "Failed to get cleanup policy runs by parameters. item types: [itemType], from: 1, to: 2, policyKey: " +
                        "policyKey, page: 1, pageSize: 1",
                storageException.getMessage());
    }

    @Test
    void getRuns() throws SQLException {
        CleanupRunSummary summary01 = CleanupRunSummary.builder()
                .policyKey("policy01")
                .policyRunId(42)
                .build();
        when(dao.get("policy01", 42)).thenReturn(summary01);
        when(dao.get("policy01", 43)).thenReturn(null);

        CleanupRunPolicyKeyAndRunId id01 = new CleanupRunPolicyKeyAndRunId("policy01", 42);
        CleanupRunPolicyKeyAndRunId id02 = new CleanupRunPolicyKeyAndRunId("policy01", 43);

        Map<CleanupRunPolicyKeyAndRunId, Optional<CleanupRunSummary>> actual =
                runSummaryService.getRuns(Set.of(id01, id02));

        assertEquals(summary01, actual.get(id01).get());
        assertEquals(Optional.empty(), actual.get(id02));
    }

    @Test
    void testGetPolicyRun() throws SQLException {
        RetentionRunSummary retentionRunSummary = RetentionRunSummary.builder().policyKey("policy01").runId(42).build();
        CleanupRunPolicyKeyAndRunId cleanupRunPolicyKeyAndRunId =
                CleanupRunPolicyKeyAndRunId.builder().policyKey("policy01").policyRunId(42).build();

        when(retentionRunSummaryDao.get(cleanupRunPolicyKeyAndRunId.policyKey(),
                cleanupRunPolicyKeyAndRunId.policyRunId())).thenReturn(retentionRunSummary);

        Optional<RetentionRunSummary> response = runSummaryService.getPolicyRun(cleanupRunPolicyKeyAndRunId);

        assertTrue(response.isPresent());
        assertEquals(response.get(), retentionRunSummary);
    }

    @Test
    void testGetPolicyRunException() throws SQLException{
        doThrow(SQLException.class).when(retentionRunSummaryDao).get(anyString(), anyInt());
        assertThrowsExactly(StorageException.class,
                () -> runSummaryService.getPolicyRun(new CleanupRunPolicyKeyAndRunId("policy01", 42)),
                "Failed to get policy run");
    }

    @Test
    void getRunsException() throws SQLException {
        doThrow(SQLException.class).when(dao).get(anyString(), anyInt());
        assertThrowsExactly(StorageException.class,
                () -> runSummaryService.getRuns(Set.of(new CleanupRunPolicyKeyAndRunId("policy01", 42))),
                "Failed to get policy runs");
    }

    @Test
    void getRun() throws SQLException {
        CleanupRunSummary summary01 = CleanupRunSummary.builder()
                .policyKey("policy01")
                .policyRunId(42)
                .build();
        when(dao.get("policy01", 42)).thenReturn(summary01);
        when(dao.get("policy01", 43)).thenReturn(null);

        CleanupRunPolicyKeyAndRunId id01 = new CleanupRunPolicyKeyAndRunId("policy01", 42);
        CleanupRunPolicyKeyAndRunId id02 = new CleanupRunPolicyKeyAndRunId("policy01", 43);

        Optional<CleanupRunSummary> cleanupRunSummary1 = runSummaryService.getRun(id01);
        Optional<CleanupRunSummary> cleanupRunSummary2 = runSummaryService.getRun(id02);

        assertTrue(cleanupRunSummary1.isPresent());
        assertEquals(summary01, cleanupRunSummary1.get());
        assertTrue(cleanupRunSummary2.isEmpty());
    }

    @Test
    void getRunException() throws SQLException {
        doThrow(SQLException.class).when(dao).get(anyString(), anyInt());
        assertThrowsExactly(StorageException.class,
                () -> runSummaryService.getRun(new CleanupRunPolicyKeyAndRunId("policy01", 42)),
                "Failed to get policy run");
    }


    @Test
    void testGetFinishedRunSummaries() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        when(dao.getFinishedRunSummaries(eq(types), anyLong(), anyLong()))
                .thenReturn(Set.of(CleanupRunSummary.builder().policyRunId(123).build()));
        var runs = runSummaryService.getFinishedRunSummaries(types, 1, 2);
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.policyRunId() == 123));
    }

    @Test
    void testGetRunningPolicies() throws SQLException {
        var statuses = Set.of(CleanupRunSummary.Status.IN_PROGRESS.getName(),
                CleanupRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName());
        when(dao.findByStatuses(eq(statuses)))
                .thenReturn(Set.of(CleanupRunSummary.builder().policyRunId(123).build()));
        var runs = runSummaryService.getRunningPolicies();
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.policyRunId() == 123));
    }

    @Test
    void testGetRunningPoliciesThrowsStorageException() throws SQLException {
        var statuses = Set.of(CleanupRunSummary.Status.IN_PROGRESS.getName(),
                CleanupRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName());
        when(dao.findByStatuses(statuses)).thenThrow(SQLException.class);

        assertThrows(StorageException.class, () -> runSummaryService.getRunningPolicies());
    }

    @Test
    void testGetFinishedRunSummariesHandlesSqlExceptions() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        when(dao.getFinishedRunSummaries(eq(types), anyLong(), anyLong())).thenThrow(new SQLException("test"));
        assertThrows(StorageException.class, () -> runSummaryService.getFinishedRunSummaries(types,
                1, 2));
    }

    @Test
    void testGetPolicyRunsWithoutDates() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        String policyKey = "policy";
        long page = 1L;
        long size = 1L;
        int runId = 1;
        String status = "completed";
        CleanupPolicyRun policyRun = CleanupPolicyRun.builder()
                .runId(1)
                .policyKey(policyKey)
                .status(status)
                .build();
        when(dao.getPolicyRuns(types, policyKey, null, page, size, null, null,
                null, null)).thenReturn(Set.of(policyRun));
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(types, null, null, policyKey, null, page, size, null, null, null,
                        null, null);

        var actual = runSummaryService.getPolicyRuns(cleanupPolicyRunRequest);
        assertEquals(1, actual.size());
        var run = actual.iterator().next();
        assertEquals(runId, run.runId());
        assertEquals(policyKey, run.policyKey());
        assertEquals(status, run.status());
    }

    @Test
    void testGetPolicyRuns() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        long from = System.currentTimeMillis();
        long to = System.currentTimeMillis();
        String policyKey = "policy";
        long page = 1L;
        long size = 1L;
        int runId = 1;
        String status = "completed";
        CleanupPolicyRun policyRun = CleanupPolicyRun.builder()
                .runId(1)
                .policyKey(policyKey)
                .status(status)
                .build();
        when(dao.getPolicyRuns(types, from, to, policyKey, null, page, size, null, null,
                null, null)).thenReturn(Set.of(policyRun));
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(types, from, to, policyKey, null, page, size, null, null, null,
                        null, null);

        var actual = runSummaryService.getPolicyRuns(cleanupPolicyRunRequest);
        assertEquals(1, actual.size());
        var run = actual.iterator().next();
        assertEquals(runId, run.runId());
        assertEquals(policyKey, run.policyKey());
        assertEquals(status, run.status());
    }

    @Test
    void testGetPolicyRunsCountWithoutDates() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        String policyKey = "policy";
        long total = 10;
        when(dao.getPolicyRunsCount(types, policyKey, null, null, null)).thenReturn(total);
        var actual = runSummaryService.getPolicyRunsCount(types, null, null, policyKey, null, null, null);
        assertEquals(total, actual);
    }

    @Test
    void testGetPolicyRunsCount() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        long from = System.currentTimeMillis();
        long to = System.currentTimeMillis();
        String policyKey = "policy";
        long total = 10;
        when(dao.getPolicyRunsCount(types, from, to, policyKey, null, null, null)).thenReturn(total);
        var actual = runSummaryService.getPolicyRunsCount(types, from, to, policyKey, null, null, null);
        assertEquals(total, actual);
    }

    @Test
    void testGetPolicyRunsCountException() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        long from = System.currentTimeMillis();
        long to = System.currentTimeMillis();
        String policyKey = "policy";
        when(dao.getPolicyRunsCount(types, from, to, policyKey, null, null, null)).thenThrow(new SQLException());
        StorageException exception = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRunsCount(types, from, to, policyKey, null, null, null));
        assertEquals("Failed to get policy runs count", exception.getMessage());
    }

    @Test
    void testInsertSuccess() throws SQLException {
        CleanupRunSummary summary = createCleanupRunSummary();
        RetentionRunSummary retentionSummary = createRetentionRunSummary();

        doNothing().when(dao).insert(summary);
        when(retentionRunSummaryDao.insert(retentionSummary)).thenReturn(1);
        runSummaryService.insert(summary);
        verify(dao).insert(summary);
        verify(retentionRunSummaryDao, times(1)).insert(any());
    }

    @Test
    void testInsertThrowsStorageException() throws SQLException {
        CleanupRunSummary summary = createCleanupRunSummary();
        doThrow(new SQLException("Expected exception")).when(dao).insert(summary);
        StorageException exception = assertThrows(StorageException.class, () -> runSummaryService.insert(summary));
        assertEquals("java.sql.SQLException: Expected exception", exception.getMessage());
        verify(dao).insert(summary);
        verifyNoInteractions(retentionRunSummaryDao);
    }



    @Test
    void testUpdateSuccess() throws SQLException {
        CleanupRunSummary summary = createCleanupRunSummary();
        RetentionRunSummary retentionSummary = createRetentionRunSummary();

        when(dao.update(summary)).thenReturn(1);
        when(retentionRunSummaryDao.update(retentionSummary)).thenReturn(1);
        int rowsAffected = runSummaryService.update(summary);
        assertEquals(1, rowsAffected, "Expected one row to be affected");
        verify(dao, times(1)).update(any());
        verify(retentionRunSummaryDao, times(1)).update(any());
    }

    @Test
    void testUpdateThrowsStorageException() throws SQLException {
        CleanupRunSummary summary = createCleanupRunSummary();

        doThrow(new SQLException("Expected exception")).when(dao).update(summary);
        StorageException exception = assertThrows(StorageException.class, () -> runSummaryService.update(summary));
        assertEquals("java.sql.SQLException: Expected exception", exception.getMessage(),
                "Expected specific exception message");
        verify(dao, times(1)).update(summary);
        verifyNoInteractions(retentionRunSummaryDao);
    }

    @Test
    void testMapToRetentionRunSummaryConfigWithNewProperties() throws SQLException {
        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                List.of("bundle1", "bundle2"),
                List.of("prod", "staging"),
                List.of("build-*", "test-*"),
                true
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(any(RetentionRunSummary.class));
    }

    @Test
    void testMapToRetentionRunSummaryConfigWithNullNewProperties() throws SQLException {
        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(null, null, null, null);
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(any(RetentionRunSummary.class));
    }

    @Test
    void testMapToRetentionRunSummaryConfigWithEmptyNewProperties() throws SQLException {
        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                List.of(),
                List.of(),
                List.of(),
                false
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(any(RetentionRunSummary.class));
    }

    @Test
    void testMapToRetentionRunSummaryConfigWithMixedNewProperties() throws SQLException {
        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                List.of("bundle1"),
                null,
                List.of("build-*"),
                true
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(any(RetentionRunSummary.class));
    }

    @Test
    void testUpdateWithNewProperties() throws SQLException {
        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                List.of("bundle1", "bundle2"),
                List.of("prod", "staging"),
                List.of("build-*", "test-*"),
                true
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        when(dao.update(summary)).thenReturn(1);
        when(retentionRunSummaryDao.update(any(RetentionRunSummary.class))).thenReturn(1);

        int rowsAffected = runSummaryService.update(summary);

        assertEquals(1, rowsAffected, "Expected one row to be affected");
        verify(dao, times(1)).update(summary);
        verify(retentionRunSummaryDao, times(1)).update(any(RetentionRunSummary.class));
    }

    @Test
    void testMapToRetentionRunSummaryConfigWithLargeNewProperties() throws SQLException {
        List<String> largeReleaseBundles = List.of("bundle1", "bundle2", "bundle3", "bundle4", "bundle5");
        List<String> largeExcludePromotedEnvironments = List.of("prod", "staging", "dev", "test", "qa");
        List<String> largeBuildPatterns = List.of("build-*", "test-*", "release-*", "feature-*", "hotfix-*");

        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                largeReleaseBundles,
                largeExcludePromotedEnvironments,
                largeBuildPatterns,
                true
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(any(RetentionRunSummary.class));
    }

    @Test
    void testMapToRetentionRunSummaryConfigVerifiesNewPropertiesMapping() throws SQLException {
        List<String> expectedReleaseBundles = List.of("bundle1", "bundle2");
        List<String> expectedExcludePromotedEnvironments = List.of("prod", "staging");
        List<String> expectedBuildPatterns = List.of("build-*", "test-*");
        Boolean expectedDeleteBuildArtifacts = true;

        CleanupRunConfig cleanupRunConfig = createCleanupRunConfigWithNewProperties(
                expectedReleaseBundles,
                expectedExcludePromotedEnvironments,
                expectedBuildPatterns,
                expectedDeleteBuildArtifacts
        );
        CleanupRunSummary summary = createCleanupRunSummaryWithConfig(cleanupRunConfig);
        ArgumentCaptor<RetentionRunSummary> retentionRunSummaryCaptor
                = ArgumentCaptor.forClass(RetentionRunSummary.class);

        runSummaryService.insert(summary);
        verify(retentionRunSummaryDao).insert(retentionRunSummaryCaptor.capture());

        RetentionRunSummary capturedRetentionRunSummary = retentionRunSummaryCaptor.getValue();
        RunSummaryConfig capturedRunConfig = (RunSummaryConfig) capturedRetentionRunSummary.runConfig();

        assertEquals(expectedReleaseBundles, capturedRunConfig.getReleaseBundles(),
                "Release bundles should be correctly mapped");
        assertEquals(expectedExcludePromotedEnvironments, capturedRunConfig.getExcludePromotedEnvironments(),
                "Exclude promoted environments should be correctly mapped");
        assertEquals(expectedBuildPatterns, capturedRunConfig.getBuildPatterns(),
                "Build patterns should be correctly mapped");
        assertEquals(expectedDeleteBuildArtifacts, capturedRunConfig.getDeleteBuildArtifacts(),
                "Delete build artifacts should be correctly mapped");
    }

    private CleanupRunConfig createCleanupRunConfigWithNewProperties(
            List<String> releaseBundles,
            List<String> excludePromotedEnvironments,
            List<String> buildPatterns,
            Boolean deleteBuildArtifacts) {
        return CleanupRunConfig.builder()
                .created(123456789L)
                .createdBy("user")
                .modified(123456789L)
                .modifiedBy("user")
                .cronExp("0 0 0 * * ?")
                .description("policy")
                .skipTrashCan(true)
                .durationInMinutes(20)
                .packageTypes(List.of("maven"))
                .repos(List.of("repo-1"))
                .includedPackages(List.of("package-1"))
                .releaseBundles(releaseBundles)
                .excludePromotedEnvironments(excludePromotedEnvironments)
                .buildPatterns(buildPatterns)
                .deleteBuildArtifacts(deleteBuildArtifacts)
                .build();
    }

    private CleanupRunSummary createCleanupRunSummaryWithConfig(CleanupRunConfig config) {
        return CleanupRunSummary.builder()
                .policyKey("policy01")
                .policyRunId(42)
                .dryRun(false)
                .startedDate(123456789)
                .finishedDate(987654321)
                .status(CleanupRunSummary.Status.COMPLETED)
                .type(CleanupRunSummary.Type.PACKAGE)
                .deletedPackagesCount(10)
                .failedPackagesCount(1)
                .rejectedPackagesCount(0)
                .originalBinarySize(100L)
                .estimatedDeletedBinarySize(50L)
                .totalBinarySize(150L)
                .triggeredBy("admin")
                .tempNodesCleaned(true)
                .runConfig(config)
                .build();
    }

    private CleanupRunSummary createCleanupRunSummary() {
        CleanupRunConfig config = CleanupRunConfig.builder()
                .created(123456789L)
                .createdBy("user")
                .modified(123456789L)
                .modifiedBy("user")
                .cronExp("0 0 0 * * ?")
                .description("policy")
                .skipTrashCan(true)
                .durationInMinutes(20)
                .packageTypes(List.of("maven"))
                .repos(List.of("repo-1"))
                .includedPackages(List.of("package-1"))
                .releaseBundles(List.of("bundle1"))
                .excludePromotedEnvironments(List.of("prod"))
                .buildPatterns(List.of("build-*"))
                .deleteBuildArtifacts(true)
                .build();
        return CleanupRunSummary.builder()
                .policyKey("policy01")
                .policyRunId(42)
                .dryRun(false)
                .startedDate(123456789)
                .finishedDate(987654321)
                .status(CleanupRunSummary.Status.COMPLETED)
                .type(CleanupRunSummary.Type.PACKAGE)
                .deletedPackagesCount(10)
                .failedPackagesCount(1)
                .rejectedPackagesCount(0)
                .originalBinarySize(100L)
                .estimatedDeletedBinarySize(50L)
                .totalBinarySize(150L)
                .triggeredBy("admin")
                .tempNodesCleaned(true)
                .runConfig(config)
                .build();
    }

    private RetentionRunSummary createRetentionRunSummary() {
        RunSummaryConfig runSummaryConfig = RunSummaryConfig.builder()
                .created(123456789L)
                .createdBy("user")
                .modified(123456789L)
                .modifiedBy("user")
                .cronExp("0 0 0 * * ?")
                .description("policy")
                .skipTrashCan(true)
                .durationInMinutes(20)
                .packageTypes(List.of("maven"))
                .repos(List.of("repo-1"))
                .includedPackages(List.of("package-1"))
                .releaseBundles(List.of("bundle1"))
                .excludePromotedEnvironments(List.of("prod"))
                .buildPatterns(List.of("build-*"))
                .deleteBuildArtifacts(true)
                .build();
        return RetentionRunSummary.builder()
                .policyKey("policy01")
                .runId(42)
                .dryRun(false)
                .startedDate(123456789)
                .finishedDate(987654321)
                .retentionType(CLEANUP)
                .status(COMPLETED)
                .itemType(PACKAGE)
                .processedItemsCount(10)
                .failedItemsCount(1)
                .totalItemsCount(11)
                .rejectedItemsCount(0)
                .processedPhysicalBinarySize(100L)
                .processedPhysicalBinarySize(50L)
                .totalBinarySize(150L)
                .triggeredBy("admin")
                .tempNodesCleaned(true)
                .runConfig(runSummaryConfig)
                .build();
    }
}