package org.artifactory.storage.db.policy.service;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyImpactRow;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.dao.PolicyRunInput;
import org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PolicyRunSummaryServiceImplTest {
    @Mock
    private RetentionRunSummaryDao dao;
    @Mock
    private ArtifactoryContext context;
    @InjectMocks
    private PolicyRunSummaryServiceImpl runSummaryService;

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

        when(dao.getRunsForInternalCleanup(2, PolicyType.ARCHIVE)).thenReturn(Set.of(record));
        assertEquals(1, runSummaryService.getRunsForInternalCleanup(2, PolicyType.ARCHIVE).size());
    }

    @Test
    void testGetRunsForInternalCleanupFail() throws SQLException {
        when(dao.getRunsForInternalCleanup(2, PolicyType.ARCHIVE)).thenThrow(new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> runSummaryService.getRunsForInternalCleanup(2, PolicyType.ARCHIVE));
        assertEquals("java.sql.SQLException: Expected exception", storageException.getMessage());
    }

    @Test
    void testMarkAsCleanedInternallyFail() throws SQLException {
        when(dao.markAsCleanedInternally("pkey", 1, RetentionRunSummary.RetentionType.ARCHIVE)).thenThrow(
                new SQLException("Expected exception"));

        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.markAsCleanedInternally("pkey", 1, RetentionRunSummary.RetentionType.ARCHIVE));
        assertEquals("java.sql.SQLException: Expected exception", storageException.getMessage());
    }

    @Test
    void testMarkAsCleanedInternallyFailSuccess() throws SQLException {
        when(dao.markAsCleanedInternally("pkey", 1, RetentionRunSummary.RetentionType.ARCHIVE)).thenReturn(true);
        runSummaryService.markAsCleanedInternally("pkey", 1, RetentionRunSummary.RetentionType.ARCHIVE);
        verify(dao).markAsCleanedInternally("pkey", 1, RetentionRunSummary.RetentionType.ARCHIVE);
    }

    @Test
    void testUpdateByIdPass() throws SQLException {
        when(dao.updateById(RetentionRunSummary.builder().build())).thenReturn(1);
        runSummaryService.updateById(RetentionRunSummary.builder().build());
        verify(dao).updateById(RetentionRunSummary.builder().build());
    }

    @Test
    void testUpdateByIdFail() throws SQLException {
        when(dao.updateById(RetentionRunSummary.builder().id("1").policyKey("test").build())).thenThrow(
                new SQLException("Thrown by test"));
        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.updateById(RetentionRunSummary.builder().id("1").policyKey("test").build()));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void testSqlExceptionThrowsStorageException() throws SQLException {
        when(dao.getPolicyRuns(any(PolicyRunInput.class), eq(true)))
                .thenThrow(new SQLException("Thrown by test"));
        Set<String> itemTypes = Set.of("itemType");
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(itemTypes, 1L, 2L, null, null, 1L, 1L, "policyKey", "ASC", "true", null,
                        null, PolicyType.ARCHIVE);

        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRuns(cleanupPolicyRunRequest));
        assertEquals("Failed to get cleanup policy runs by parameters. item types: [itemType], from: 1, to: 2, " +
                        "page: 1, pageSize: 1",
                storageException.getMessage());
    }

    @Test
    void testSqlExceptionThrowsStorageExceptionWithPolicyKey() throws SQLException {
        when(dao.getPolicyRuns(any(PolicyRunInput.class), eq(true)))
                .thenThrow(new SQLException("Thrown by test"));
        Set<String> itemTypes = Set.of("itemType");
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(itemTypes, 1L, 2L, "policyKey", null, 1L, 1L, "policyKey", "ASC", "true",
                        null, null, PolicyType.ARCHIVE);

        StorageException storageException = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRuns(cleanupPolicyRunRequest));
        assertEquals(
                "Failed to get cleanup policy runs by parameters. item types: [itemType], from: 1, to: 2, policyKey: " +
                        "policyKey, page: 1, pageSize: 1",
                storageException.getMessage());
    }

    @Test
    void getRuns() throws SQLException {
        RetentionRunSummary summary01 = RetentionRunSummary.builder()
                .policyKey("policy01")
                .runId(42)
                .build();

        when(dao.findByStatuses(PolicyType.ARCHIVE, Set.of("inProgress", "inProgressWithErrors")))
                .thenReturn(new HashSet<>(Set.of(summary01)));

        CleanupRunPolicyKeyAndRunId id01 = new CleanupRunPolicyKeyAndRunId("policy01", 42);
        CleanupRunPolicyKeyAndRunId id02 = new CleanupRunPolicyKeyAndRunId("policy01", 43);

        Set<RetentionRunSummary> actual = runSummaryService.getRuns(Set.of(id01, id02));

        assertTrue(actual.contains(summary01));
    }


    @Test
    void getRunsException() throws SQLException {
        doThrow(SQLException.class).when(dao).findByStatuses(any(), any(Set.class));
        assertThrowsExactly(StorageException.class,
                () -> runSummaryService.getRuns(Set.of(new CleanupRunPolicyKeyAndRunId("policy01", 42))),
                "Failed to get policy runs");
    }

    @Test
    void getRunByIdException() throws SQLException {
        doThrow(SQLException.class).when(dao).get(anyInt());
        assertThrowsExactly(StorageException.class,
                () -> runSummaryService.getRunById(4));
    }

    @Test
    void getRun() throws SQLException {
        RetentionRunSummary summary01 = RetentionRunSummary.builder()
                .policyKey("policy01")
                .runId(42)
                .build();
        when(dao.get("policy01", 42)).thenReturn(summary01);
        when(dao.get("policy01", 43)).thenReturn(null);

        CleanupRunPolicyKeyAndRunId id01 = new CleanupRunPolicyKeyAndRunId("policy01", 42);
        CleanupRunPolicyKeyAndRunId id02 = new CleanupRunPolicyKeyAndRunId("policy01", 43);

        Optional<RetentionRunSummary> cleanupRunSummary1 = runSummaryService.getRun(id01);
        Optional<RetentionRunSummary> cleanupRunSummary2 = runSummaryService.getRun(id02);

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
        when(dao.getFinishedRunSummaries(eq(types), anyLong(), anyLong(), eq(PolicyType.ARCHIVE)))
                .thenReturn(Set.of(RetentionRunSummary.builder().runId(123).build()));
        var runs = runSummaryService.getFinishedRunSummaries(types, 1, 2, PolicyType.ARCHIVE);
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.runId() == 123));
    }

    @Test
    void testGetRunningPolicies() throws SQLException {
        var statuses = Set.of(RetentionRunSummary.Status.IN_PROGRESS.getName(),
                RetentionRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName());
        when(dao.findByStatuses(PolicyType.ARCHIVE, statuses))
                .thenReturn(Set.of(RetentionRunSummary.builder().runId(123).build()));
        var runs = runSummaryService.getRunningPolicies(PolicyType.ARCHIVE);
        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.runId() == 123));
    }

    @Test
    void testGetRunningPoliciesThrowsStorageException() throws SQLException {
        var statuses = Set.of(RetentionRunSummary.Status.IN_PROGRESS.getName(),
                RetentionRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName());
        when(dao.findByStatuses(PolicyType.ARCHIVE, statuses)).thenThrow(SQLException.class);

        assertThrows(StorageException.class, () -> runSummaryService.getRunningPolicies(PolicyType.ARCHIVE));
    }

    @Test
    void testGetFinishedRunSummariesHandlesSqlExceptions() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        when(dao.getFinishedRunSummaries(eq(types), anyLong(), anyLong(), eq(PolicyType.ARCHIVE))).thenThrow(
                new SQLException("test"));
        assertThrows(StorageException.class, () -> runSummaryService.getFinishedRunSummaries(types,
                1, 2, PolicyType.ARCHIVE));
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
        PolicyRunInput input = createPolicyRunInput(types, from, to, policyKey, page, size
        );
        when(dao.getPolicyRuns(input, true)).thenReturn(Set.of(policyRun));
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(types, from, to, policyKey, null, page, size, null, null, null, null,
                        null, PolicyType.ARCHIVE);

        var actual = runSummaryService.getPolicyRuns(cleanupPolicyRunRequest);
        assertEquals(1, actual.size());
        var run = actual.iterator().next();
        assertEquals(runId, run.runId());
        assertEquals(policyKey, run.policyKey());
        assertEquals(status, run.status());
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
        PolicyRunInput input = createPolicyRunInput(types, null, null, policyKey, page, size
        );
        when(dao.getPolicyRuns(input, false)).thenReturn(Set.of(policyRun));
        CleanupPolicyRunRequest cleanupPolicyRunRequest =
                new CleanupPolicyRunRequest(types, null, null, policyKey, null, page, size, null, null, null, null,
                        null, PolicyType.ARCHIVE);

        var actual = runSummaryService.getPolicyRuns(cleanupPolicyRunRequest);
        assertEquals(1, actual.size());
        var run = actual.iterator().next();
        assertEquals(runId, run.runId());
        assertEquals(policyKey, run.policyKey());
        assertEquals(status, run.status());
    }

    @Test
    void testGetPolicyRunsCount() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        long from = System.currentTimeMillis();
        long to = System.currentTimeMillis();
        String policyKey = "policy";
        long total = 10;
        when(dao.getPolicyRunsCount(types, from, to, policyKey, null, null, null, PolicyType.ARCHIVE)).thenReturn(
                total);
        var actual =
                runSummaryService.getPolicyRunsCount(types, from, to, policyKey, null, null, null, PolicyType.ARCHIVE);
        assertEquals(total, actual);
    }

    @Test
    void testGetPolicyRunsCountWithoutDates() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        String policyKey = "policy";
        long total = 10;
        when(dao.getPolicyRunsCount(types, policyKey, null, null, null, PolicyType.ARCHIVE)).thenReturn(
                total);
        var actual =
                runSummaryService.getPolicyRunsCount(types,null,null, policyKey, null, null, null, PolicyType.ARCHIVE);
        assertEquals(total, actual);
    }

    @Test
    void testGetPolicyRunsCountException() throws SQLException {
        var types = Set.of(PolicyItemType.PACKAGE.getCode());
        long from = System.currentTimeMillis();
        long to = System.currentTimeMillis();
        String policyKey = "policy";
        when(dao.getPolicyRunsCount(types, from, to, policyKey, null, null, null, PolicyType.ARCHIVE)).thenThrow(
                new SQLException());
        StorageException exception = assertThrows(StorageException.class,
                () -> runSummaryService.getPolicyRunsCount(types, from, to, policyKey, null, null, null,
                        PolicyType.ARCHIVE));
        assertEquals("Failed to get policy runs count", exception.getMessage());
    }

    @Test
    void testGetStorageImpact() throws SQLException {
        PolicyImpactRow row = new PolicyImpactRow("pk1", "cleanup", 500L);
        when(dao.getStorageImpact(anyLong(), any(), any(), any(), any(), eq(false), any()))
                .thenReturn(List.of(row));
        List<PolicyImpactRow> result = runSummaryService.getStorageImpact(1000L, null, null, null, null, false, null);
        assertEquals(1, result.size());
        assertEquals("pk1", result.get(0).policyKey());
        assertEquals(500L, result.get(0).bytesProcessed());
    }

    @Test
    void testGetStorageImpactThrowsStorageException() throws SQLException {
        doThrow(new SQLException("db error")).when(dao)
                .getStorageImpact(anyLong(), any(), any(), any(), any(), eq(false), any());
        assertThrows(StorageException.class,
                () -> runSummaryService.getStorageImpact(1000L, null, null, null, null, false, null));
    }

    private PolicyRunInput createPolicyRunInput(Set<String> itemTypes, @Nullable Long from, @Nullable Long to,
            String policyKey, long page, long pageSize) {

        PolicyRunInput.PolicyRunInputBuilder builder= PolicyRunInput.builder()
                .itemTypes(itemTypes)
                .policyKey(policyKey)
                .policyKeys(null)
                .page(page)
                .pageSize(pageSize)
                .sortBy(null)
                .sortOrder(null)
                .excludeBlankExecutions(null)
                .status(null)
                .policyType(PolicyType.ARCHIVE);

        if(from != null && to != null) {
            builder.from(from);
            builder.to(to);
        }
        return builder.build();
    }

}
