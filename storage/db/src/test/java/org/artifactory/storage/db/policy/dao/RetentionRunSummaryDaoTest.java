package org.artifactory.storage.db.policy.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.policy.PolicyImpactRow;
import org.artifactory.storage.db.policy.RestoreRunSummaryConfig;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyViewRun;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

@Slf4j
public class RetentionRunSummaryDaoTest extends DbBaseTest {

    public static final String POLICY_KEY_1 = "pkey1";
    public static final String POLICY_KEY_2 = "pkey2";
    @Autowired
    private RetentionRunSummaryDao retentionRunSummaryDao;
    @Autowired
    private QuartzTaskHistoryDao quartzTaskHistoryDao;

    private int runId01 = 1;
    private int runId02 = 2;
    private int runId03 = 3;
    private int runId04 = 4;
    private RetentionRunSummary summary01;
    private RetentionRunSummary summary01duplicate;
    private RetentionRunSummary summary02;
    private RetentionRunSummary summary03;
    private RetentionRunSummary summary04;

    @BeforeMethod
    public void setUp() throws SQLException {
        runId01 = createQuartzHistoryRecord("policy-key");
        summary01 = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("policy-key")
                .runId(runId01)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(RetentionRunSummary.Status.IN_PROGRESS)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(2)
                .failedItemsCount(3)
                .totalItemsCount(5)
                .totalBinarySize(6)
                .processedPhysicalBinarySize(7)
                .triggeredBy("username")
                .totalBinarySize(8)
                .build();
        summary01duplicate = summary01.toBuilder()
                .processedItemsCount(20)
                .failedItemsCount(30)
                .totalItemsCount(50)
                .totalBinarySize(60)
                .processedPhysicalBinarySize(70)
                .totalBinarySize(80)
                .build();
        runId02 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key",
                "cleanup",
                IN_PROGRESS,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        summary02 = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("policy-key1")
                .runId(runId02)
                .dryRun(true)
                .startedDate(48)
                .finishedDate(49)
                .status(RetentionRunSummary.Status.IN_PROGRESS)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(201)
                .failedItemsCount(301)
                .totalItemsCount(502)
                .totalBinarySize(601)
                .processedPhysicalBinarySize(701)
                .triggeredBy("username")
                .totalBinarySize(801)
                .tempNodesCleaned(false)
                .build();
        runId03 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key-3",
                "cleanup",
                IN_PROGRESS,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        runId04 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key-4",
                "cleanup",
                COMPLETED,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        summary03 = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("policy-key-3")
                .runId(runId03)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(RetentionRunSummary.Status.IN_PROGRESS)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(202)
                .failedItemsCount(300)
                .totalItemsCount(502)
                .totalBinarySize(601)
                .processedPhysicalBinarySize(805)
                .triggeredBy("username")
                .totalBinarySize(800)
                .build();
        summary04 = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("policy-key-4")
                .runId(runId04)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(RetentionRunSummary.Status.COMPLETED)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(0)
                .failedItemsCount(0)
                .totalItemsCount(0)
                .totalBinarySize(0)
                .processedPhysicalBinarySize(0)
                .triggeredBy("username")
                .totalBinarySize(0)
                .build();
    }

    @AfterMethod
    public void tearDown() throws SQLException {
        retentionRunSummaryDao.deleteAll();
    }

    @Test
    public void readWrite() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        assertRunSummary(summary01, retentionRunSummaryDao.get("policy-key", runId01));
        assertRunSummary(summary02, retentionRunSummaryDao.get("policy-key1", runId02));
        assertEquals(2, retentionRunSummaryDao.getAll().size());
    }

    @Test
    public void update() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.update(summary01duplicate);
        assertRunSummary(summary01duplicate, retentionRunSummaryDao.get("policy-key", runId01));
        assertRunSummary(summary02, retentionRunSummaryDao.get("policy-key1", runId02));
        assertEquals(2, retentionRunSummaryDao.getAll().size());
    }

    @Test
    public void updateById() throws SQLException {
        retentionRunSummaryDao.insert(summary01.toBuilder().policyKey("random").id("1").build());
        retentionRunSummaryDao.updateById(summary01duplicate.toBuilder().id("1").build());
        assertEquals(1, retentionRunSummaryDao.getAll().size());
    }

    @Test
    public void testMarkAsFinished() throws SQLException {
        String policyKey1 = POLICY_KEY_1;
        String policyKey2 = POLICY_KEY_1;
        int runId11 = createQuartzHistoryRecord(policyKey1);
        int runId12 = createQuartzHistoryRecord(policyKey1);
        int runId21 = createQuartzHistoryRecord(policyKey2);
        int runId22 = createQuartzHistoryRecord(policyKey2);

        retentionRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, runId11));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, runId12));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, runId21));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, runId22));

        long timestamp = System.currentTimeMillis();
        retentionRunSummaryDao.markAsFinished(policyKey1, runId11, timestamp);

        List<RetentionRunSummary> all = retentionRunSummaryDao.getAll();
        assertEquals(4, all.size());

        List<RetentionRunSummary> finished = all.stream().filter(cr -> cr.finishedDate() > 0).toList();
        assertEquals(1, finished.size());

        assertEquals(finished.get(0).policyKey(), policyKey1);
        assertEquals(finished.get(0).runId(), runId11);
        assertEquals(finished.get(0).finishedDate(), timestamp);
    }


    @Test
    public void testGetRunsForInternalCleanup() throws SQLException {
        int runId11 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId12 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId13 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId21 = createQuartzHistoryRecord(POLICY_KEY_2);
        int runId22 = createQuartzHistoryRecord(POLICY_KEY_2);

        // remember timestamp
        long expiredTimestamp = System.currentTimeMillis();
        initDbRecords(expiredTimestamp, runId11, runId12, runId13, runId21, runId22);

        // when
        long timestampToClean = expiredTimestamp + 500;
        Set<CleanupRunPolicyKeyAndRunId> runs =
                retentionRunSummaryDao.getRunsForInternalCleanup(timestampToClean, PolicyType.ARCHIVE);

        // then
        assertEquals(3, runs.size());
        Optional<CleanupRunPolicyKeyAndRunId> expiredRun = runs.stream()
                .filter(run -> run.policyRunId() == runId11)
                .filter(run -> run.policyKey().equals(POLICY_KEY_1))
                .findFirst();
        assertTrue(expiredRun.isPresent());

        Optional<CleanupRunPolicyKeyAndRunId> expiredDryRun = runs.stream()
                .filter(run -> run.policyRunId() == runId13)
                .filter(run -> run.policyKey().equals(POLICY_KEY_1))
                .findFirst();
        assertTrue(expiredDryRun.isPresent());
    }

    @Test
    public void testGetFinishedRunSummaries() throws SQLException {
        int runId11 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId12 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId13 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId21 = createQuartzHistoryRecord(POLICY_KEY_2);
        int runId22 = createQuartzHistoryRecord(POLICY_KEY_2);
        long startDate = System.currentTimeMillis();
        initDbRecords(startDate + 100, runId11, runId12, runId13, runId21, runId22);
        long endDate = startDate + 500;
        Set<RetentionRunSummary> runs =
                retentionRunSummaryDao.getFinishedRunSummaries(Set.of(PolicyItemType.PACKAGE.getCode()), startDate,
                        endDate, PolicyType.ARCHIVE);
        assertNotNull(runs);
        assertEquals(2, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.runId() == runId11));
        assertTrue(runs.stream().anyMatch(run -> run.runId() == runId21));
    }

    @Test
    public void getPolicyRunsWithoutDates() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        PolicyRunInput params =
                createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), null, null, null, null, 1L,
                        2L, null,
                        null, null, null, null);
        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, false);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary01.policyKey())).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                  {
                     "runId": 1,
                     "policyTaskId": 5,
                     "taskType": "package",
                     "policyKey": %s,
                     "startTime": 48,
                     "endTime": 49,
                     "cleanedItems": 2,
                     "totalItems": 5,
                     "cleanedSize": 7,
                     "status": "inProgress",
                     "triggeredBy": "username",
                     "manual": true,
                     "dryRun": false
                   }
                """, summary01.policyKey());

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary02.policyKey())).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": 2,
                    "policyTaskId": 6,
                    "taskType": "package",
                    "policyKey": %s,
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 201,
                    "totalItems": 502,
                    "cleanedSize": 701,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": true
                  }
                """, summary02.policyKey());
        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRuns() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 2L, null,
                null, null, null, null);
        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary01.policyKey())).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                  {
                     "runId": 1,
                     "policyTaskId": 5,
                     "taskType": "package",
                     "policyKey": %s,
                     "startTime": 48,
                     "endTime": 49,
                     "cleanedItems": 2,
                     "totalItems": 5,
                     "cleanedSize": 7,
                     "status": "inProgress",
                     "triggeredBy": "username",
                     "manual": true,
                     "dryRun": false
                   }
                """, summary01.policyKey());

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary02.policyKey())).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": 2,
                    "policyTaskId": 6,
                    "taskType": "package",
                    "policyKey": %s,
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 201,
                    "totalItems": 502,
                    "cleanedSize": 701,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": true
                  }
                """, summary02.policyKey());
        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRunsByPolicyKey() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        String policyKey1 = "policy-key";
        int runId = createQuartzHistoryRecord(policyKey1);

        RetentionRunSummary policyRunSummary = summary01.toBuilder()
                .runId(runId)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .policyKey(policyKey1)
                .build();
        retentionRunSummaryDao.insert(policyRunSummary);

        String policyKey2 = "policy-key2";
        int runId2 = createQuartzHistoryRecord(policyKey2);

        RetentionRunSummary policyRunSummary2 = summary01.toBuilder()
                .runId(runId2)
                .policyKey(policyKey2)
                .build();

        retentionRunSummaryDao.insert(policyRunSummary2);

        PolicyRunInput params =
                createPolicyRunInput(Set.of("package"), 0L, System.currentTimeMillis(), policyKey2, null, 1L, 1L,
                        null, null, null, null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(1, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream()
                        .filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(), policyKey2))
                        .findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = """
                 {
                    "runId": 4,
                    "policyTaskId": 14,
                    "taskType": "package",
                    "policyKey": "policy-key2",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 2,
                    "totalItems": 5,
                    "cleanedSize": 7,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """;

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));
    }

    @Test
    public void getPolicyRunsByPolicyKeys() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        String policyKey1 = "policy-key-1";
        int runId = createQuartzHistoryRecord(policyKey1);

        RetentionRunSummary policyRunSummary = summary01.toBuilder()
                .runId(runId)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .policyKey(policyKey1)
                .build();
        retentionRunSummaryDao.insert(policyRunSummary);

        String policyKey2 = "policy-key2";
        int runId2 = createQuartzHistoryRecord(policyKey2);

        RetentionRunSummary policyRunSummary2 = summary01.toBuilder()
                .runId(runId2)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .policyKey(policyKey2)
                .build();

        retentionRunSummaryDao.insert(policyRunSummary2);

        PolicyRunInput params = createPolicyRunInput(Set.of("package"), 0L, System.currentTimeMillis(), null,
                Set.of(policyKey1, policyKey2), 1L, 2L, null, null, null, null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream()
                        .filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(), policyKey2))
                        .findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = """
                 {
                    "runId": 4,
                    "policyTaskId": 20,
                    "taskType": "package",
                    "policyKey": "policy-key2",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 2,
                    "totalItems": 5,
                    "cleanedSize": 7,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """;

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream()
                        .filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(), policyKey1))
                        .findAny();
        assertTrue(cleanupPolicyRun2.isPresent());

        String expectedCleanupRun2 = """
                 {
                    "runId": 3,
                    "policyTaskId": 19,
                    "taskType": "package",
                    "policyKey": "policy-key-1",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 2,
                    "totalItems": 5,
                    "cleanedSize": 7,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """;

        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRunsWithOffset() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 1L, null, null, null, null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);
        assertEquals(1, policyRuns.size());

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary02.policyKey())).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key1",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 201,
                    "totalItems": 502,
                    "cleanedSize": 701,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": true
                  }
                """, runId02, runId02);
        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRunsSortByDefault() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 2L, "policy_run_id_wrong_name", "ASC", null, null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 2,
                    "totalItems": 5,
                    "cleanedSize": 7,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """, runId01, runId01);

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));

        Optional<CleanupPolicyRun> cleanupPolicyRun2 = policyRuns.stream().skip(1).findFirst();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key1",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 201,
                    "totalItems": 502,
                    "cleanedSize": 701,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": true
                  }
                """, runId02, runId02);
        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRunsSortByRunId() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 2L, "runId", "ASC", null, null, null);

        Set<CleanupPolicyRun> policyRuns = retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 2,
                    "totalItems": 5,
                    "cleanedSize": 7,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """, runId01, runId01);

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));

        Optional<CleanupPolicyRun> cleanupPolicyRun2 = policyRuns.stream().skip(1).findFirst();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key1",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 201,
                    "totalItems": 502,
                    "cleanedSize": 701,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": true
                  }
                """, runId02, 42);
        assertJsonWithoutRunId(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()));
    }

    @Test
    public void getPolicyRunsSortByDeletedBinarySize() throws SQLException, JSONException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 3L, "cleanedSize", "DESC", null, null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(3, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": %d,
                    "taskType": "package",
                    "policyKey": "policy-key-3",
                    "startTime": 48,
                    "endTime": 49,
                    "cleanedItems": 202,
                    "totalItems": 502,
                    "cleanedSize": 805,
                    "status": "inProgress",
                    "triggeredBy": "username",
                    "manual": true,
                    "dryRun": false
                  }
                """, runId03, runId03);

        assertJsonWithoutRunId(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()));
    }

    @Test
    public void getPolicyRunsWithFilter() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);
        retentionRunSummaryDao.insert(summary04);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 5L, "runId", "ASC", "true", Set.of(), null);
        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(3, policyRuns.size());
        Optional<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId04).findAny();
        assertTrue(policyRun.isEmpty());
    }

    @Test
    public void getPolicyRunsWithStatus() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);
        retentionRunSummaryDao.insert(summary04);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 5L, "runId", "ASC", null, Set.of("completed"), null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(1, policyRuns.size());
        Set<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.status(),
                        RetentionRunSummary.Status.COMPLETED.getName())).collect(Collectors.toSet());
        long count =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0,
                        System.currentTimeMillis(),
                        null, null, null, Set.of("completed"), PolicyType.ARCHIVE);
        long countWithoutDates =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()),
                        null, null, null, Set.of("completed"), PolicyType.ARCHIVE);
        assertEquals(1, policyRun.size());
        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void getPolicyRunsWithRunIds() throws SQLException {
        int runId1 = retentionRunSummaryDao.insert(summary01);
        int runId2 = retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);
        retentionRunSummaryDao.insert(summary04);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 5L, "runId", "ASC", null, null,
                Set.of(runId1, runId2));

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(2, policyRuns.size());
    }

    @Test
    public void getPolicyRunsWithOutFilter() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);
        retentionRunSummaryDao.insert(summary04);

        PolicyRunInput params = createPolicyRunInput(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0L,
                System.currentTimeMillis(), null, null, 1L, 5L, "runId", "ASC", "false", null, null);

        Set<CleanupPolicyRun> policyRuns =
                retentionRunSummaryDao.getPolicyRuns(params, true);

        assertEquals(4, policyRuns.size());
        Optional<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.policyKey(),
                        summary04.policyKey())).findAny();
        assertTrue(policyRun.isPresent());
    }

    @Test
    public void getPolicyRunsCount() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        long count =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0,
                        System.currentTimeMillis(),
                        null, null, null, null, PolicyType.ARCHIVE);
        long countWithoutDates =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()),
                        null, null, null, null, PolicyType.ARCHIVE);

        assertEquals(2, count);
        assertEquals(2, countWithoutDates);
    }

    @Test
    public void getPolicyRunsCountWithPolicyKeys() throws SQLException {
        retentionRunSummaryDao.insert(summary01.toBuilder().policyKey("policy-key-1").build());
        retentionRunSummaryDao.insert(summary02);
        long count =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0,
                        System.currentTimeMillis(),
                        null, Set.of("policy-key-1"), null, Set.of(), PolicyType.ARCHIVE);
        long countWithoutDates =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()),
                        null, Set.of("policy-key-1"), null, Set.of(), PolicyType.ARCHIVE);

        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void getPolicyRunsCountWithPolicyKey() throws SQLException {
        retentionRunSummaryDao.insert(summary01.toBuilder().policyKey("policy-key-1").build());
        retentionRunSummaryDao.insert(summary02);
        long count =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()), 0,
                        System.currentTimeMillis(), "policy-key-1", Set.of(), null, null,
                        PolicyType.ARCHIVE);
        long countWithoutDates =
                retentionRunSummaryDao.getPolicyRunsCount(Set.of(RetentionRunSummary.ItemType.PACKAGE.getName()),
                        "policy-key-1", Set.of(), null, null, PolicyType.ARCHIVE);

        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void findByStatusesTest() throws SQLException {
        retentionRunSummaryDao.insert(summary02);
        retentionRunSummaryDao.insert(summary03);
        retentionRunSummaryDao.insert(summary04);

        Set<String> statuses = Set.of(RetentionRunSummary.Status.IN_PROGRESS.getName(),
                RetentionRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName());
        Set<RetentionRunSummary> inProgressRunSummaries =
                retentionRunSummaryDao.findByStatuses(PolicyType.ARCHIVE, statuses);

        assertEquals(2, inProgressRunSummaries.size());
    }

    @Test
    public void testGetPolices() throws SQLException {
        retentionRunSummaryDao.insert(summary01);
        retentionRunSummaryDao.insert(summary02);
        List<CleanupPolicyViewRun> actual = retentionRunSummaryDao.getPolices(List.of("policy-key", "policy-key1"),
                RetentionRunSummary.RetentionType.ARCHIVE);
        // We should get two policies in response.
        assertEquals(2, actual.size());

        // Check the first element
        if ("policy-key".equals(actual.get(0).policyKey())) {
            assertFalse(actual.getFirst().dryRun());
        } else if ("policy-key1".equals(actual.get(0).policyKey())) {
            assertTrue(actual.getFirst().dryRun());
        }

        // Check the second element
        if ("policy-key".equals(actual.get(1).policyKey())) {
            assertFalse(actual.get(1).dryRun());
        } else if ("policy-key1".equals(actual.get(1).policyKey())) {
            assertTrue(actual.get(1).dryRun());
        }
    }

    @Test
    public void testGetPolicesTotalItemTypes() throws SQLException {
        retentionRunSummaryDao.insert(summary02);
        List<CleanupPolicyViewRun> actual =
                retentionRunSummaryDao.getPolices(List.of("policy-key1"), RetentionRunSummary.RetentionType.ARCHIVE);
        // We should get one policy in response.
        assertEquals(1, actual.size());
        CleanupPolicyViewRun firstPolicy = actual.get(0);
        assertEquals(502, firstPolicy.totalItems());
    }

    @Test
    public void testGetPolicesEmpty() throws SQLException {
        List<CleanupPolicyViewRun> actual =
                retentionRunSummaryDao.getPolices(List.of("key1", "key2"), RetentionRunSummary.RetentionType.ARCHIVE);
        // We should not get any policies.
        assertEquals(0, actual.size());
    }

    @Test
    public void toRunConfigTest() throws SQLException, JsonProcessingException {
        // Cleanup & Archive run config
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getBytes("run_config")).thenReturn(null);
        assertNull(retentionRunSummaryDao.toRunConfig(resultSet));

        // Restore run config
        when(resultSet.getString("retention_type")).thenReturn("restore");
        assertNull(retentionRunSummaryDao.toRunConfig(resultSet));

        RestoreRunSummaryConfig restoreRunSummaryConfig =
                RestoreRunSummaryConfig.builder()
                        .restoreToOriginal(true)
                        .fallbackDestinationPath("fallbackDestinationPath")
                        .build();
        String restoreRunSummaryConfigString =
                new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(restoreRunSummaryConfig);
        when(resultSet.getBytes("run_config")).thenReturn(restoreRunSummaryConfigString.getBytes());
        assertNotNull(retentionRunSummaryDao.toRunConfig(resultSet));
        assertEquals(restoreRunSummaryConfig.getRestoreToOriginal(),
                ((RestoreRunSummaryConfig) retentionRunSummaryDao.toRunConfig(resultSet)).getRestoreToOriginal());
    }

    @Test
    public void markAsCleaned() throws SQLException {
        String policyKey1 = POLICY_KEY_1;
        String policyKey2 = POLICY_KEY_1;
        int policyRunId11 = createQuartzHistoryRecord(policyKey1);
        int policyRunId12 = createQuartzHistoryRecord(policyKey1);
        int policyRunId21 = createQuartzHistoryRecord(policyKey2);
        int policyRunId22 = createQuartzHistoryRecord(policyKey2);

        retentionRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId11));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId12));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId21));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId22));

        retentionRunSummaryDao.markAsCleanedInternally(policyKey1, policyRunId11,
                RetentionRunSummary.RetentionType.ARCHIVE);

        List<RetentionRunSummary> all = retentionRunSummaryDao.getAll();
        assertEquals(4, all.size());

        List<RetentionRunSummary> finished = all.stream().filter(RetentionRunSummary::tempNodesCleaned).toList();
        assertEquals(finished.size(), 1);

        assertEquals(finished.get(0).policyKey(), policyKey1);
        assertEquals(finished.get(0).runId(), policyRunId11);
    }


    @Test
    public void getConsumptionRunsForDateRange_returnsMatchingRuns() throws SQLException {
        long baseTime = 1_000_000_000L;
        long day = 86_400_000L;

        int archiveRunId = createQuartzHistoryRecord("pk-archive");
        RetentionRunSummary archiveRun = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-archive").runId(archiveRunId).dryRun(false)
                .startedDate(baseTime + 5 * day).finishedDate(baseTime + 5 * day + 1000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(500L).triggeredBy("user").build();
        retentionRunSummaryDao.insert(archiveRun);

        int restoreRunId = createQuartzHistoryRecord("pk-restore");
        RetentionRunSummary restoreRun = RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.RESTORE)
                .policyKey("pk-restore").runId(restoreRunId).dryRun(false)
                .startedDate(baseTime + 10 * day).finishedDate(baseTime + 10 * day + 1000)
                .status(RetentionRunSummary.Status.STOPPED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(200L).triggeredBy("user").build();
        retentionRunSummaryDao.insert(restoreRun);

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(baseTime, baseTime + 15 * day);

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.runId() == archiveRunId));
        assertTrue(results.stream().anyMatch(r -> r.runId() == restoreRunId));
    }

    @Test
    public void getConsumptionRunsForDateRange_excludesOutOfRange() throws SQLException {
        long baseTime = 1_000_000_000L;
        long day = 86_400_000L;

        int inRangeId = createQuartzHistoryRecord("pk-in");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-in").runId(inRangeId).dryRun(false)
                .startedDate(baseTime + 5 * day).finishedDate(baseTime + 5 * day + 1000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        int beforeRangeId = createQuartzHistoryRecord("pk-before");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-before").runId(beforeRangeId).dryRun(false)
                .startedDate(baseTime - day).finishedDate(baseTime - day + 1000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        int afterRangeId = createQuartzHistoryRecord("pk-after");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-after").runId(afterRangeId).dryRun(false)
                .startedDate(baseTime + 20 * day).finishedDate(baseTime + 20 * day + 1000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(baseTime, baseTime + 10 * day);

        assertEquals(1, results.size());
        assertEquals(inRangeId, results.get(0).runId());
    }

    @Test
    public void getConsumptionRunsForDateRange_excludesDryRuns() throws SQLException {
        long baseTime = 1_000_000_000L;

        int dryRunId = createQuartzHistoryRecord("pk-dry");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-dry").runId(dryRunId).dryRun(true)
                .startedDate(baseTime + 1000).finishedDate(baseTime + 2000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(baseTime, baseTime + 86_400_000L);

        assertTrue(results.isEmpty());
    }

    @Test
    public void getConsumptionRunsForDateRange_excludesNonTerminalStatuses() throws SQLException {
        long baseTime = 1_000_000_000L;

        int inProgressId = createQuartzHistoryRecord("pk-ip");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-ip").runId(inProgressId).dryRun(false)
                .startedDate(baseTime + 1000).finishedDate(0)
                .status(RetentionRunSummary.Status.IN_PROGRESS).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        int failedId = createQuartzHistoryRecord("pk-fail");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-fail").runId(failedId).dryRun(false)
                .startedDate(baseTime + 2000).finishedDate(baseTime + 3000)
                .status(RetentionRunSummary.Status.FAILED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(baseTime, baseTime + 86_400_000L);

        assertTrue(results.isEmpty());
    }

    @Test
    public void getConsumptionRunsForDateRange_excludesCleanupRetentionType() throws SQLException {
        long baseTime = 1_000_000_000L;

        int cleanupId = createQuartzHistoryRecord("pk-cleanup");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.CLEANUP)
                .policyKey("pk-cleanup").runId(cleanupId).dryRun(false)
                .startedDate(baseTime + 1000).finishedDate(baseTime + 2000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(baseTime, baseTime + 86_400_000L);

        assertTrue(results.isEmpty());
    }

    @Test
    public void getConsumptionRunsForDateRange_emptyForNoData() throws SQLException {
        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(0L, Long.MAX_VALUE);

        assertTrue(results.isEmpty());
    }

    @Test
    public void getConsumptionRunsForDateRange_boundaryInclusive() throws SQLException {
        long exactStart = 5_000_000L;
        long exactEnd = 10_000_000L;

        int atStartId = createQuartzHistoryRecord("pk-start");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-start").runId(atStartId).dryRun(false)
                .startedDate(exactStart).finishedDate(exactStart + 1000)
                .status(RetentionRunSummary.Status.COMPLETED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(100L).triggeredBy("user").build());

        int atEndId = createQuartzHistoryRecord("pk-end");
        retentionRunSummaryDao.insert(RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey("pk-end").runId(atEndId).dryRun(false)
                .startedDate(exactEnd).finishedDate(exactEnd + 1000)
                .status(RetentionRunSummary.Status.STOPPED).itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedVirtualBinarySize(200L).triggeredBy("user").build());

        List<RetentionRunSummary> results =
                retentionRunSummaryDao.getConsumptionRunsForDateRange(exactStart, exactEnd);

        assertEquals(2, results.size());
        assertEquals(atStartId, results.get(0).runId());
        assertEquals(atEndId, results.get(1).runId());
    }

    private void initDbRecords(long expiredTimestamp, int runId11, int runId12, int runId13,
            int runId21, int runId22) throws SQLException {
        long notExpiredTimestamp = expiredTimestamp + 1000;

        // create cleanup candidate
        RetentionRunSummary policyRun11 = createRunCleanupSummary(POLICY_KEY_1, runId11);
        retentionRunSummaryDao.insert(policyRun11);
        retentionRunSummaryDao.markAsFinished(POLICY_KEY_1, runId11, expiredTimestamp);

        // create completed recently
        RetentionRunSummary policyRun12 = createRunCleanupSummary(POLICY_KEY_1, runId12);
        retentionRunSummaryDao.insert(policyRun12);
        retentionRunSummaryDao.markAsFinished(POLICY_KEY_1, runId12, notExpiredTimestamp);

        // create completed dry run
        RetentionRunSummary policyRun13 = createDryRunCleanupSummary(POLICY_KEY_1, runId13);
        retentionRunSummaryDao.insert(policyRun13);
        retentionRunSummaryDao.markAsFinished(POLICY_KEY_1, runId13, expiredTimestamp);

        // create already cleaned
        RetentionRunSummary alreadyCleaned =
                createRunCleanupSummary(POLICY_KEY_2, runId21);
        retentionRunSummaryDao.insert(alreadyCleaned);
        retentionRunSummaryDao.markAsFinished(POLICY_KEY_2, runId21, expiredTimestamp);

        // create non-completed recently
        RetentionRunSummary inProgress = createRunCleanupSummary(POLICY_KEY_2, runId22);
        retentionRunSummaryDao.insert(inProgress);
    }

    @Test
    public void testGetRunsBatchForPolicies_emptyPolicyKeys_returnsEmpty() throws SQLException {
        List<RetentionRunSummary> result = retentionRunSummaryDao.getRunsBatchForPolicies(
                Set.of(), RetentionRunSummary.RetentionType.CLEANUP);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetRunsBatchForPolicies_returnsRunsForMatchingPolicies() throws SQLException {
        String p1 = "batch-policy-1";
        String p2 = "batch-policy-2";
        int rid1 = createQuartzHistoryRecord(p1);
        int rid2 = createQuartzHistoryRecord(p1);
        int rid3 = createQuartzHistoryRecord(p2);

        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid1));
        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid2));
        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p2, rid3));

        List<RetentionRunSummary> result = retentionRunSummaryDao.getRunsBatchForPolicies(
                Set.of(p1, p2), RetentionRunSummary.RetentionType.CLEANUP);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(r -> r.policyKey().equals(p1) || r.policyKey().equals(p2)));
    }

    @Test
    public void testGetRunsBatchForPolicies_excludesDryRuns() throws SQLException {
        String p1 = "batch-policy-dry";
        int rid1 = createQuartzHistoryRecord(p1);
        int rid2 = createQuartzHistoryRecord(p1);

        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid1));
        retentionRunSummaryDao.insert(createDryRunCleanupSummary(p1, rid2));

        List<RetentionRunSummary> result = retentionRunSummaryDao.getRunsBatchForPolicies(
                Set.of(p1), RetentionRunSummary.RetentionType.CLEANUP);

        assertEquals(1, result.size());
        assertFalse(result.get(0).dryRun());
    }

    @Test
    public void testGetRunsBatchForPolicies_orderedByPolicyKeyAscRunIdDesc() throws SQLException {
        String p1 = "aaa-policy";
        String p2 = "zzz-policy";
        int rid1 = createQuartzHistoryRecord(p1);
        int rid2 = createQuartzHistoryRecord(p1);
        int rid3 = createQuartzHistoryRecord(p2);

        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid1));
        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid2));
        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p2, rid3));

        List<RetentionRunSummary> result = retentionRunSummaryDao.getRunsBatchForPolicies(
                Set.of(p1, p2), RetentionRunSummary.RetentionType.CLEANUP);

        assertEquals(3, result.size());
        // p1 (aaa) comes before p2 (zzz)
        assertEquals(p1, result.get(0).policyKey());
        assertEquals(p1, result.get(1).policyKey());
        assertEquals(p2, result.get(2).policyKey());
        // within p1, newest run_id first
        assertTrue(result.get(0).runId() > result.get(1).runId());
    }

    @Test
    public void testGetRunsBatchForPolicies_excludesWrongRetentionType() throws SQLException {
        String p1 = "retention-type-policy";
        int rid1 = createQuartzHistoryRecord(p1);

        retentionRunSummaryDao.insert(createNonDryRunCleanupSummary(p1, rid1));

        // Query for ARCHIVE but data is CLEANUP — should return empty
        List<RetentionRunSummary> result = retentionRunSummaryDao.getRunsBatchForPolicies(
                Set.of(p1), RetentionRunSummary.RetentionType.ARCHIVE);

        assertTrue(result.isEmpty());
    }

    private RetentionRunSummary createNonDryRunCleanupSummary(String policyKey, int runId) {
        return RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.CLEANUP)
                .policyKey(policyKey)
                .runId(runId)
                .dryRun(false)
                .startedDate(System.currentTimeMillis())
                .finishedDate(0)
                .status(RetentionRunSummary.Status.FAILED)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(0)
                .failedItemsCount(1)
                .processedPhysicalBinarySize(0)
                .triggeredBy("scheduler")
                .totalBinarySize(0)
                .build();
    }

    private RetentionRunSummary createRunCleanupSummary(String policyKey, int runId) {
        return createCleanupSummary(policyKey, runId, false);
    }

    private RetentionRunSummary createDryRunCleanupSummary(String policyKey, int runId) {
        return createCleanupSummary(policyKey, runId, true);
    }

    private RetentionRunSummary createCleanupSummary(String policyKey, int runId, boolean dryRun) {
        return RetentionRunSummary.builder()
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .policyKey(policyKey)
                .runId(runId)
                .dryRun(dryRun)
                .startedDate(System.currentTimeMillis())
                .finishedDate(0)
                .status(RetentionRunSummary.Status.COMPLETED)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(0)
                .failedItemsCount(0)
                .processedPhysicalBinarySize(0)
                .triggeredBy("username")
                .totalBinarySize(0)
                .build();
    }

    private int createQuartzHistoryRecord(String policyKey) throws SQLException {
        return quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                policyKey,
                "cleanup",
                IN_PROGRESS,
                "taskId",
                "serverId",
                true,
                "username"
        ));
    }

    private void assertJsonWithoutRunId(String expectedJson, String actualJson) throws JSONException {
        JSONObject expectedJsonObject = new JSONObject(expectedJson);
        JSONObject actualJsonObject = new JSONObject(actualJson);

        expectedJsonObject.remove("runId");
        actualJsonObject.remove("runId");
        expectedJsonObject.remove("policyTaskId");
        actualJsonObject.remove("policyTaskId");

        JSONAssert.assertEquals(expectedJsonObject.toString(), actualJsonObject.toString(), JSONCompareMode.STRICT);
    }

    private PolicyRunInput createPolicyRunInput(Set<String> itemTypes, Long from, Long to,
            @Nullable String policyKey, @Nullable Set<String> policyKeys, long page, long pageSize, String sortBy,
            String sortOrder, String excludeBlankExecutions, Set<String> status, Set<Integer> runIds) {

        PolicyRunInput.PolicyRunInputBuilder builder = PolicyRunInput.builder()
                .itemTypes(itemTypes)
                .policyKey(policyKey)
                .policyKeys(policyKeys)
                .page(page)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .excludeBlankExecutions(excludeBlankExecutions)
                .status(status)
                .policyType(PolicyType.ARCHIVE)
                .runIds(runIds);

        if (from != null) {
            builder.from(from);
            builder.to(to);
        }

        return builder.build();
    }


    @Test
    public void getStorageImpact_noFilters_returnsAllCompletedSortedDesc() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.ARCHIVE, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, null, null, false, null);

        assertEquals(2, result.size());
        assertEquals(1000L, result.get(0).bytesProcessed());
        assertEquals("impact-pkey-a", result.get(0).policyKey());
        assertEquals(300L, result.get(1).bytesProcessed());
    }

    @Test
    public void getStorageImpact_withRetentionTypeFilter_returnsOnlyMatchingType() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.ARCHIVE, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(fromMillis,
                Set.of(RetentionRunSummary.RetentionType.CLEANUP.getName()), null, null, null, false, null);

        assertEquals(1, result.size());
        assertEquals("impact-pkey-a", result.get(0).policyKey());
    }

    @Test
    public void getStorageImpact_withPolicyKeysFilter_returnsOnlyMatchingPolicies() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, Set.of("impact-pkey-a"), null, null, false, null);

        assertEquals(1, result.size());
        assertEquals("impact-pkey-a", result.get(0).policyKey());
    }

    @Test
    public void getStorageImpact_withMinBytesFilter_returnsOnlyAboveThreshold() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, 500L, null, false, null);

        assertEquals(1, result.size());
        assertEquals("impact-pkey-a", result.get(0).policyKey());
    }

    @Test
    public void getStorageImpact_withMaxBytesOnly_usesHavingClause() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, null, 300L, false, null);

        assertEquals(1, result.size());
        assertEquals("impact-pkey-b", result.get(0).policyKey());
    }

    @Test
    public void getStorageImpact_withMinAndMaxBytes_returnsOnlyWithinRange() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 500L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-c", 203, RetentionRunSummary.RetentionType.CLEANUP, 100L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, 400L, 600L, false, null);

        assertEquals(1, result.size());
        assertEquals("impact-pkey-b", result.get(0).policyKey());
    }

    @Test
    public void getStorageImpact_withLimit_capsNumberOfResults() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, null, null, false, 1);

        assertEquals(1, result.size());
        assertEquals(1000L, result.get(0).bytesProcessed());
    }

    @Test
    public void getStorageImpact_sortAsc_returnsInAscendingOrder() throws SQLException {
        long fromMillis = System.currentTimeMillis() - 60_000L;
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-b", 202, RetentionRunSummary.RetentionType.CLEANUP, 300L));

        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, null, null, true, null);

        assertEquals(2, result.size());
        assertEquals(300L, result.get(0).bytesProcessed());
        assertEquals(1000L, result.get(1).bytesProcessed());
    }

    @Test
    public void getStorageImpact_windowExcludesOldRuns_returnsEmpty() throws SQLException {
        retentionRunSummaryDao.insert(
                createImpactSummary("impact-pkey-a", 201, RetentionRunSummary.RetentionType.CLEANUP, 1000L));

        long fromMillis = System.currentTimeMillis() + 60_000L;
        List<PolicyImpactRow> result = retentionRunSummaryDao.getStorageImpact(
                fromMillis, null, null, null, null, false, null);

        assertTrue(result.isEmpty());
    }

    private RetentionRunSummary createImpactSummary(String policyKey, int runId,
            RetentionRunSummary.RetentionType retentionType, long processedPhysicalBinarySize) {
        return RetentionRunSummary.builder()
                .retentionType(retentionType)
                .policyKey(policyKey)
                .runId(runId)
                .dryRun(false)
                .startedDate(System.currentTimeMillis())
                .finishedDate(System.currentTimeMillis())
                .status(RetentionRunSummary.Status.COMPLETED)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(1)
                .failedItemsCount(0)
                .totalItemsCount(1)
                .totalBinarySize(processedPhysicalBinarySize)
                .processedPhysicalBinarySize(processedPhysicalBinarySize)
                .triggeredBy("username")
                .build();
    }

    private void assertRunSummary(RetentionRunSummary expected, RetentionRunSummary actual) {
        RetentionRunSummary actualWithoutDynamicId = actual.toBuilder().id(null).build();
        assertEquals(expected, actualWithoutDynamicId);
    }
}
