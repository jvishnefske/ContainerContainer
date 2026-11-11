package org.artifactory.storage.db.cleanup;

import org.artifactory.policy.PolicyItemType;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupRunConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupRunSummaryDao;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupRunSummaryMigrationRow;
import org.artifactory.storage.db.policy.cleanup.dao.NodeCleanedDao;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.storage.db.policy.cleanup.CleanupRunSummary.Status;
import static org.artifactory.storage.db.policy.cleanup.CleanupRunSummary.Type;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;

public class CleanupRunSummaryDaoTest extends DbBaseTest {

    public static final String POLICY_KEY_1 = "pkey1";
    public static final String POLICY_KEY_2 = "pkey2";
    @Autowired
    private CleanupRunSummaryDao cleanupRunSummaryDao;
    @Autowired
    private QuartzTaskHistoryDao quartzTaskHistoryDao;
    @Autowired
    private NodeCleanedDao nodeCleanedDao;

    private int policyRunId01 = 1;
    private int policyRunId02 = 2;
    private int policyRunId03 = 3;
    private int policyRunId04 = 4;
    private CleanupRunSummary summary01;
    private CleanupRunSummary summary01duplicate;
    private CleanupRunSummary summary02;
    private CleanupRunSummary summary03;
    private CleanupRunSummary summary04;
    private final List<String> excludedPackages = List.of("package1", "package2");
    private final List<String> includedPackages = List.of("package3", "package4");
    private final List<String> excludedRepos = List.of("repo1", "repo2");
    private final List<String> includedRepos = List.of("repo3", "repo4");
    private final List<String> packageTypes = List.of("Docker", "Maven");
    private final Integer durationInMinutes = 10;
    private final Integer createdBeforeInMonths = 12;

    @BeforeMethod
    public void setUp() throws SQLException {
        policyRunId01 = createQuartzHistoryRecord("policy-key");
        CleanupRunConfig runConfig = CleanupRunConfig.builder()
                .cronExp("cron")
                .description("description")
                .created(0)
                .modified(0L)
                .durationInMinutes(durationInMinutes)
                .excludedPackages(excludedPackages)
                .includedPackages(includedPackages)
                .excludedRepos(excludedRepos)
                .repos(includedRepos)
                .includeAllProjects(true)
                .packageTypes(packageTypes)
                .createdBy("test")
                .createdBeforeInMonths(createdBeforeInMonths)
                .build();
        summary01 = CleanupRunSummary.builder()
                .policyKey("policy-key")
                .policyRunId(policyRunId01)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(Status.IN_PROGRESS)
                .type(Type.PACKAGE)
                .tempNodesCleaned(false)
                .deletedPackagesCount(2)
                .failedPackagesCount(3)
                .deletedArtifactsCount(4)
                .failedArtifactsCount(5)
                .originalBinarySize(6)
                .estimatedDeletedBinarySize(7)
                .triggeredBy("username")
                .totalBinarySize(8)
                .runConfig(runConfig)
                .build();
        summary01duplicate = summary01.toBuilder()
                .deletedPackagesCount(20)
                .failedPackagesCount(30)
                .deletedArtifactsCount(40)
                .failedArtifactsCount(50)
                .originalBinarySize(60)
                .estimatedDeletedBinarySize(70)
                .totalBinarySize(80)
                .build();
        policyRunId02 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key",
                "cleanup",
                IN_PROGRESS,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        summary02 = CleanupRunSummary.builder()
                .policyKey("policy-key")
                .policyRunId(policyRunId02)
                .dryRun(true)
                .startedDate(48)
                .finishedDate(49)
                .status(Status.IN_PROGRESS)
                .type(Type.PACKAGE)
                .tempNodesCleaned(false)
                .deletedPackagesCount(201)
                .failedPackagesCount(301)
                .deletedArtifactsCount(401)
                .failedArtifactsCount(501)
                .originalBinarySize(601)
                .estimatedDeletedBinarySize(701)
                .triggeredBy("username")
                .totalBinarySize(801)
                .build();
        policyRunId03 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key-3",
                "cleanup",
                IN_PROGRESS,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        policyRunId04 = quartzTaskHistoryDao.insert(new QuartzTaskHistoryRecord(
                "policy-key-4",
                "cleanup",
                COMPLETED,
                "taskId",
                "serverId",
                true,
                "username"
        ));
        summary03 = CleanupRunSummary.builder()
                .policyKey("policy-key-3")
                .policyRunId(policyRunId03)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(Status.IN_PROGRESS)
                .type(Type.PACKAGE)
                .tempNodesCleaned(false)
                .deletedPackagesCount(202)
                .failedPackagesCount(300)
                .deletedArtifactsCount(401)
                .failedArtifactsCount(501)
                .originalBinarySize(601)
                .estimatedDeletedBinarySize(805)
                .triggeredBy("username")
                .totalBinarySize(800)
                .build();
        summary04 = CleanupRunSummary.builder()
                .policyKey("policy-key-4")
                .policyRunId(policyRunId04)
                .dryRun(false)
                .startedDate(48)
                .finishedDate(49)
                .status(Status.COMPLETED)
                .type(Type.PACKAGE)
                .tempNodesCleaned(false)
                .deletedPackagesCount(0)
                .failedPackagesCount(0)
                .deletedArtifactsCount(0)
                .failedArtifactsCount(0)
                .originalBinarySize(0)
                .estimatedDeletedBinarySize(0)
                .triggeredBy("username")
                .totalBinarySize(0)
                .build();
    }

    @AfterMethod
    public void tearDown() throws SQLException {
        cleanupRunSummaryDao.deleteAll();
    }

    @Test
    public void readWrite() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        assertEquals(summary01, cleanupRunSummaryDao.get("policy-key", policyRunId01));
        assertEquals(summary02, cleanupRunSummaryDao.get("policy-key", policyRunId02));
        assertEquals(2, cleanupRunSummaryDao.getAll().size());
    }

    @Test
    public void rewrite() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        try {
            cleanupRunSummaryDao.insert(summary01duplicate);
            fail();
        } catch (Exception e) {
            // empty
        }
        assertEquals(summary01, cleanupRunSummaryDao.get("policy-key", policyRunId01));
        assertEquals(1, cleanupRunSummaryDao.getAll().size());
    }

    @Test
    public void update() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.update(summary01duplicate);
        assertEquals(summary01duplicate, cleanupRunSummaryDao.get("policy-key", policyRunId01));
        assertEquals(summary02, cleanupRunSummaryDao.get("policy-key", policyRunId02));
        assertEquals(2, cleanupRunSummaryDao.getAll().size());
    }

    @Test
    public void testMarkAsFinished() throws SQLException {
        String policyKey1 = POLICY_KEY_1;
        String policyKey2 = POLICY_KEY_1;
        int policyRunId11 = createQuartzHistoryRecord(policyKey1);
        int policyRunId12 = createQuartzHistoryRecord(policyKey1);
        int policyRunId21 = createQuartzHistoryRecord(policyKey2);
        int policyRunId22 = createQuartzHistoryRecord(policyKey2);

        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId11));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId12));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId21));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId22));

        long timestamp = System.currentTimeMillis();
        cleanupRunSummaryDao.markAsFinished(policyKey1, policyRunId11, timestamp);

        List<CleanupRunSummary> all = cleanupRunSummaryDao.getAll();
        assertEquals(4, all.size());

        List<CleanupRunSummary> finished = all.stream().filter(cr -> cr.finishedDate() > 0).toList();
        assertEquals(1, finished.size());

        assertEquals(finished.get(0).policyKey(), policyKey1);
        assertEquals(finished.get(0).policyRunId(), policyRunId11);
        assertEquals(finished.get(0).finishedDate(), timestamp);
    }

    @Test
    public void markAsCleaned() throws SQLException {
        String policyKey1 = POLICY_KEY_1;
        String policyKey2 = POLICY_KEY_1;
        int policyRunId11 = createQuartzHistoryRecord(policyKey1);
        int policyRunId12 = createQuartzHistoryRecord(policyKey1);
        int policyRunId21 = createQuartzHistoryRecord(policyKey2);
        int policyRunId22 = createQuartzHistoryRecord(policyKey2);

        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId11));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(policyKey1, policyRunId12));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId21));
        cleanupRunSummaryDao.insert(createDryRunCleanupSummary(POLICY_KEY_2, policyRunId22));

        cleanupRunSummaryDao.markAsCleanedInternally(policyKey1, policyRunId11);

        List<CleanupRunSummary> all = cleanupRunSummaryDao.getAll();
        assertEquals(4, all.size());

        List<CleanupRunSummary> finished = all.stream().filter(CleanupRunSummary::tempNodesCleaned).toList();
        assertEquals(finished.size(), 1);

        assertEquals(finished.get(0).policyKey(), policyKey1);
        assertEquals(finished.get(0).policyRunId(), policyRunId11);
    }

    @Test
    public void testGetRunsForInternalCleanup() throws SQLException {
        int policyRunId11 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId12 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId13 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId21 = createQuartzHistoryRecord(POLICY_KEY_2);
        int policyRunId22 = createQuartzHistoryRecord(POLICY_KEY_2);

        // remember timestamp
        long expiredTimestamp = System.currentTimeMillis();
        initDbRecords(expiredTimestamp, policyRunId11, policyRunId12, policyRunId13, policyRunId21, policyRunId22);

        // when
        long timestampToClean = expiredTimestamp + 500;
        Set<CleanupRunPolicyKeyAndRunId> runs = cleanupRunSummaryDao.getRunsForInternalCleanup(timestampToClean);

        // then
        assertEquals(2, runs.size());
        Optional<CleanupRunPolicyKeyAndRunId> expiredRun = runs.stream()
                .filter(run -> run.policyRunId() == policyRunId11)
                .filter(run -> run.policyKey().equals(POLICY_KEY_1))
                .findFirst();
        assertTrue(expiredRun.isPresent());

        Optional<CleanupRunPolicyKeyAndRunId> expiredDryRun = runs.stream()
                .filter(run -> run.policyRunId() == policyRunId13)
                .filter(run -> run.policyKey().equals(POLICY_KEY_1))
                .findFirst();
        assertTrue(expiredDryRun.isPresent());
    }

    @Test
    public void testGetFinishedRunSummaries() throws SQLException {
        int policyRunId11 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId12 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId13 = createQuartzHistoryRecord(POLICY_KEY_1);
        int policyRunId21 = createQuartzHistoryRecord(POLICY_KEY_2);
        int policyRunId22 = createQuartzHistoryRecord(POLICY_KEY_2);
        long startDate = System.currentTimeMillis();
        initDbRecords(startDate + 100, policyRunId11, policyRunId12, policyRunId13, policyRunId21, policyRunId22);
        long endDate = startDate + 500;
        Set<CleanupRunSummary> runs =
                cleanupRunSummaryDao.getFinishedRunSummaries(Set.of(PolicyItemType.PACKAGE.getCode()), startDate,
                        endDate);
        assertNotNull(runs);
        assertEquals(2, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.policyRunId() == policyRunId11));
        assertTrue(runs.stream().anyMatch(run -> run.policyRunId() == policyRunId21));
    }

    // disabling this test as cascade foreign key (nodes_cleaned_cleanup_run_summary_fk) is removed
    @Test(enabled = false)
    public void deleteNodesCleanedRecordsByCascade() throws SQLException {
        String policyKey = "policy-key";
        int policyRunId = createQuartzHistoryRecord(policyKey);
        cleanupRunSummaryDao.insert(createCleanupSummary(policyKey, policyRunId, false));

        nodeCleanedDao.insertCleanedNode(
                new CleanedNode(policyKey, policyRunId, "pname", "pversion", false, "repo", "path", "name", 1L, "sha1",
                        DeleteArtifactStatus.DELETED.getCode(), null, null));
        assertEquals(nodeCleanedDao.countAll(), 1);
        // when
        assertEquals(cleanupRunSummaryDao.deleteAll(), 1);
        // then
        assertEquals(nodeCleanedDao.countAll(), 0);
    }

    public void getCleanupRunsWithoutDates() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), null, null, 1L, 2L, null,
                        null, null, null);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId01).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, policyRunId01);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId02).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRuns() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0,
                        System.currentTimeMillis(), null, null, 1L, 2L, null,
                        null, null, null);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId01).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                            "cronExp": "cron",
                            "description": "description",
                            "created": 0,
                            "modified": 0,
                            "durationInMinutes": 10,
                            "excludedPackages": ["package1", "package2"],
                            "includedPackages": ["package3", "package4"],
                            "excludedRepos": ["repo1", "repo2"],
                            "repos": ["repo3", "repo4"],
                            "includeAllProjects": true,
                            "packageTypes": ["Docker", "Maven"],
                            "createdBy": "test",
                            "createdBeforeInMonths": 12
                        }
                  }
                """, policyRunId01);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId02).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsDifferentItemTypeIdenticalPolicyKey() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        String policyKey = "policy-key";
        int runId = createQuartzHistoryRecord(policyKey);

        CleanupRunSummary cleanupRunSummary = summary01.toBuilder()
                .policyRunId(runId)
                .policyKey(policyKey)
                .type(Type.ARTIFACT)
                .build();
        cleanupRunSummaryDao.insert(cleanupRunSummary);

        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0,
                        System.currentTimeMillis(), null, Set.of(), 1L, 10L, null, null, null, null);

        assertEquals(2, policyRuns.size());

        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId01).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());
        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, policyRunId01);
        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId02).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);

        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsByPolicyKey() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        String policyKey1 = "policy-key";
        int runId = createQuartzHistoryRecord(policyKey1);

        CleanupRunSummary cleanupRunSummary = summary01.toBuilder()
                .policyRunId(runId)
                .type(Type.ARTIFACT)
                .policyKey(policyKey1)
                .build();
        cleanupRunSummaryDao.insert(cleanupRunSummary);

        String policyKey2 = "policy-key2";
        int runId2 = createQuartzHistoryRecord(policyKey2);

        CleanupRunSummary cleanupRunSummary2 = summary01.toBuilder()
                .policyRunId(runId2)
                .policyKey(policyKey2)
                .build();

        cleanupRunSummaryDao.insert(cleanupRunSummary2);

        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of("package"), 0, System.currentTimeMillis(), policyKey2, null,
                        1L, 1L, null, null, null, null);

        assertEquals(1, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId2).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, runId2);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);
    }

    @Test
    public void getCleanupRunsByPolicyKeys() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        String policyKey1 = "policy-key";
        int runId = createQuartzHistoryRecord(policyKey1);

        CleanupRunSummary cleanupRunSummary = summary01.toBuilder()
                .policyRunId(runId)
                .type(Type.PACKAGE)
                .policyKey(policyKey1)
                .build();
        cleanupRunSummaryDao.insert(cleanupRunSummary);

        String policyKey2 = "policy-key2";
        int runId2 = createQuartzHistoryRecord(policyKey2);

        CleanupRunSummary cleanupRunSummary2 = summary01.toBuilder()
                .policyRunId(runId2)
                .type(Type.PACKAGE)
                .policyKey(policyKey2)
                .build();

        cleanupRunSummaryDao.insert(cleanupRunSummary2);

        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of("package"), 0, System.currentTimeMillis(), null,
                        Set.of(policyKey1, policyKey2), 1L, 2L, null, null, null, null);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId2).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, runId2);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());

        String expectedCleanupRun2 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, runId);

        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsByMultipleItemTypes() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        String policyKey1 = "policy-key";
        String policyKey2 = "policy-key2";
        int runId3 = createQuartzHistoryRecord(policyKey1);

        CleanupRunSummary artifactCleanupRunSummary = summary01.toBuilder()
                .policyRunId(runId3)
                .type(Type.PACKAGE)
                .policyKey(policyKey2)
                .build();
        cleanupRunSummaryDao.insert(artifactCleanupRunSummary);

        int runId4 = createQuartzHistoryRecord(policyKey2);

        CleanupRunSummary cleanupRunSummary2 = summary01.toBuilder()
                .policyRunId(runId4)
                .policyKey(policyKey2)
                .build();

        cleanupRunSummaryDao.insert(cleanupRunSummary2);

        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0,
                        System.currentTimeMillis(), null, null, 1L, 10L, null, null, null, null);

        assertEquals(4, policyRuns.size());

        Optional<CleanupPolicyRun> cleanupPolicyRun1 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId01).findAny();
        assertTrue(cleanupPolicyRun1.isPresent());
        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, policyRunId01);
        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId02).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun3 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId3).findAny();
        assertTrue(cleanupPolicyRun3.isPresent());
        String expectedCleanupRun3 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, runId3);
        JSONAssert.assertEquals(expectedCleanupRun3, JsonUtils.getInstance().valueToString(cleanupPolicyRun3.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun4 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == runId4).findAny();
        assertTrue(cleanupPolicyRun4.isPresent());
        String expectedCleanupRun4 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, runId4);
        JSONAssert.assertEquals(expectedCleanupRun4, JsonUtils.getInstance().valueToString(cleanupPolicyRun4.get()),
                true);
    }

    @Test
    public void getCleanupRunsWithOffset() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 1L, null, null, null, null);
        assertEquals(1, policyRuns.size());

        Optional<CleanupPolicyRun> cleanupPolicyRun2 =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId02).findAny();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsSortByDefault() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 2L, "policy_run_id_wrong_name", "ASC", null, null);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, policyRunId01);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 = policyRuns.stream().skip(1).findFirst();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsSortByRunId() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 2L, "runId", "ASC", null, null);

        assertEquals(2, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                    "dryRun": false,
                    "runSummaryConfig": {
                        "cronExp": "cron",
                        "description": "description",
                        "created": 0,
                        "modified": 0,
                        "durationInMinutes": 10,
                        "excludedPackages": ["package1", "package2"],
                        "includedPackages": ["package3", "package4"],
                        "excludedRepos": ["repo1", "repo2"],
                        "repos": ["repo3", "repo4"],
                        "includeAllProjects": true,
                        "packageTypes": ["Docker", "Maven"],
                        "createdBy": "test",
                        "createdBeforeInMonths": 12
                    }
                  }
                """, policyRunId01);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);

        Optional<CleanupPolicyRun> cleanupPolicyRun2 = policyRuns.stream().skip(1).findFirst();
        assertTrue(cleanupPolicyRun2.isPresent());
        String expectedCleanupRun2 = String.format("""
                  {
                    "runId": %d,
                    "policyTaskId": 0,
                    "taskType": "package",
                    "policyKey": "policy-key",
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
                """, policyRunId02);
        JSONAssert.assertEquals(expectedCleanupRun2, JsonUtils.getInstance().valueToString(cleanupPolicyRun2.get()),
                true);
    }

    @Test
    public void getCleanupRunsSortByDeletedBinarySize() throws SQLException, JSONException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.insert(summary03);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 3L, "cleanedSize", "DESC", null, null);

        assertEquals(3, policyRuns.size());
        Optional<CleanupPolicyRun> cleanupPolicyRun1 = policyRuns.stream().findFirst();
        assertTrue(cleanupPolicyRun1.isPresent());

        String expectedCleanupRun1 = String.format("""
                 {
                    "runId": %d,
                    "policyTaskId": 0,
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
                """, policyRunId03);

        JSONAssert.assertEquals(expectedCleanupRun1, JsonUtils.getInstance().valueToString(cleanupPolicyRun1.get()),
                true);
    }

    @Test
    public void getCleanupRunsWithFilter() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.insert(summary03);
        cleanupRunSummaryDao.insert(summary04);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 5L, "runId", "ASC", "true", Set.of());

        assertEquals(3, policyRuns.size());
        Optional<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId04).findAny();
        assertTrue(policyRun.isEmpty());
    }

    @Test
    public void getCleanupRunsWithStatus() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.insert(summary03);
        cleanupRunSummaryDao.insert(summary04);

        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 5L, "runId", "ASC", null, Set.of("completed"));

        assertEquals(1, policyRuns.size());
        Set<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> Objects.equals(cleanupPolicyRun.status(),
                        Status.COMPLETED.getName())).collect(Collectors.toSet());
        long count =
                cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(),
                        null, null, null, Set.of("completed"));
        long countWithoutDates = cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()),
                null, null, null, Set.of("completed"));
        assertEquals(1, policyRun.size());
        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void getCleanupRunsWithOutFilter() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.insert(summary03);
        cleanupRunSummaryDao.insert(summary04);
        Set<CleanupPolicyRun> policyRuns =
                cleanupRunSummaryDao.getPolicyRuns(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(), null,
                        null, 1L, 5L, "runId", "ASC", "false", null);

        assertEquals(4, policyRuns.size());
        Optional<CleanupPolicyRun> policyRun =
                policyRuns.stream().filter(cleanupPolicyRun -> cleanupPolicyRun.runId() == policyRunId04).findAny();
        assertTrue(policyRun.isPresent());
    }

    @Test
    public void getCleanupRunsCount() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);
        cleanupRunSummaryDao.insert(summary02);
        long count =
                cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(),
                        null, null, null, null);
        long countWithoutDates =
                cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()),
                        null, null, null, null);

        assertEquals(2, count);
        assertEquals(2, countWithoutDates);
    }

    @Test
    public void getCleanupRunsCountWithPolicyKeys() throws SQLException {
        cleanupRunSummaryDao.insert(summary01.toBuilder().policyKey("policy-key-1").build());
        cleanupRunSummaryDao.insert(summary02);
        long count =
                cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()), 0, System.currentTimeMillis(),
                        null, Set.of(summary01.policyKey()), null, Set.of());
        long countWithoutDates =
                cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()),
                        null, Set.of(summary01.policyKey()), null, Set.of());

        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void getCleanupRunsCountWithPolicyKey() throws SQLException {
        cleanupRunSummaryDao.insert(summary01.toBuilder().policyKey("policy-key-1").build());
        cleanupRunSummaryDao.insert(summary02);
        long count = cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()), 0,
                System.currentTimeMillis(), "policy-key-1", Set.of(), null, null);
        long countWithoutDates = cleanupRunSummaryDao.getPolicyRunsCount(Set.of(Type.PACKAGE.getName()),
                "policy-key-1", Set.of(), null, null);

        assertEquals(1, count);
        assertEquals(1, countWithoutDates);
    }

    @Test
    public void findByStatusesTest() throws SQLException {
        cleanupRunSummaryDao.insert(summary02);
        cleanupRunSummaryDao.insert(summary03);
        cleanupRunSummaryDao.insert(summary04);

        Set<String> statuses = Set.of(Status.IN_PROGRESS.getName(), Status.IN_PROGRESS_WITH_ERRORS.getName());
        Set<CleanupRunSummary> inProgressRunSummaries = cleanupRunSummaryDao.findByStatuses(statuses);

        assertEquals(2, inProgressRunSummaries.size());
    }

    @Test
    public void getRunSummaryByRunIdTest() throws SQLException {
        cleanupRunSummaryDao.insert(summary01);

        CleanupRunSummary summary = cleanupRunSummaryDao.get("policy-key", policyRunId01);

        assertEquals(policyRunId01, summary.policyRunId());
        assertEquals("cron", summary.runConfig().cronExp());
        assertEquals(excludedPackages, summary.runConfig().excludedPackages());
        assertEquals(includedPackages, summary.runConfig().includedPackages());
        assertEquals(includedRepos, summary.runConfig().repos());
        assertEquals(excludedRepos, summary.runConfig().excludedRepos());
        assertTrue(summary.runConfig().includeAllProjects());
        assertEquals(0, summary.runConfig().created());
        assertEquals("test", summary.runConfig().createdBy());
        assertEquals("description", summary.runConfig().description());
        assertEquals("cron", summary.runConfig().cronExp());
        assertEquals(durationInMinutes, summary.runConfig().durationInMinutes());
        assertEquals(createdBeforeInMonths, summary.runConfig().createdBeforeInMonths());
    }

    @Test
    public void toCleanupRunConfigTest() throws SQLException {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(resultSet.getBytes("run_config")).thenReturn(null);
        assertNull(cleanupRunSummaryDao.toCleanupRunConfig(resultSet));
    }

    private void initDbRecords(long expiredTimestamp, int policyRunId11, int policyRunId12, int policyRunId13,
            int policyRunId21, int policyRunId22) throws SQLException {
        long notExpiredTimestamp = expiredTimestamp + 1000;

        // create cleanup candidate
        CleanupRunSummary policyRun11 = createRunCleanupSummary(POLICY_KEY_1, policyRunId11);
        cleanupRunSummaryDao.insert(policyRun11);
        cleanupRunSummaryDao.markAsFinished(POLICY_KEY_1, policyRunId11, expiredTimestamp);

        // create completed recently
        CleanupRunSummary policyRun12 = createRunCleanupSummary(POLICY_KEY_1, policyRunId12);
        cleanupRunSummaryDao.insert(policyRun12);
        cleanupRunSummaryDao.markAsFinished(POLICY_KEY_1, policyRunId12, notExpiredTimestamp);

        // create completed dry run
        CleanupRunSummary policyRun13 = createDryRunCleanupSummary(POLICY_KEY_1, policyRunId13);
        cleanupRunSummaryDao.insert(policyRun13);
        cleanupRunSummaryDao.markAsFinished(POLICY_KEY_1, policyRunId13, expiredTimestamp);

        // create already cleaned
        CleanupRunSummary alreadyCleaned =
                createRunCleanupSummary(POLICY_KEY_2, policyRunId21);
        cleanupRunSummaryDao.insert(alreadyCleaned);
        cleanupRunSummaryDao.markAsCleanedInternally(POLICY_KEY_2, policyRunId21);
        cleanupRunSummaryDao.markAsFinished(POLICY_KEY_2, policyRunId21, expiredTimestamp);

        // create non-completed recently
        CleanupRunSummary inProgress = createRunCleanupSummary(POLICY_KEY_2, policyRunId22);
        cleanupRunSummaryDao.insert(inProgress);
    }

    private CleanupRunSummary createRunCleanupSummary(String policyKey, int policyRunId) {
        return createCleanupSummary(policyKey, policyRunId, false);
    }

    private CleanupRunSummary createDryRunCleanupSummary(String policyKey, int policyRunId) {
        return createCleanupSummary(policyKey, policyRunId, true);
    }

    private CleanupRunSummary createCleanupSummary(String policyKey, int policyRunId, boolean dryRun) {
        CleanupRunConfig runConfig = CleanupRunConfig.builder()
                .durationInMinutes(1)
                .build();
        return CleanupRunSummary.builder()
                .policyKey(policyKey)
                .policyRunId(policyRunId)
                .dryRun(dryRun)
                .startedDate(System.currentTimeMillis())
                .finishedDate(0)
                .status(Status.COMPLETED)
                .type(Type.PACKAGE)
                .tempNodesCleaned(false)
                .deletedPackagesCount(0)
                .failedPackagesCount(0)
                .deletedArtifactsCount(0)
                .failedArtifactsCount(0)
                .originalBinarySize(0)
                .estimatedDeletedBinarySize(0)
                .triggeredBy("username")
                .totalBinarySize(0)
                .runConfig(runConfig)
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

    @Test
    public void listCleanupRunSummariesForMigrationIncludesAllRunTypes() throws SQLException {
        int packageRunId = createQuartzHistoryRecord(POLICY_KEY_1);
        int artifactRunId = createQuartzHistoryRecord(POLICY_KEY_1);
        cleanupRunSummaryDao.insert(createRunCleanupSummary(POLICY_KEY_1, packageRunId));
        cleanupRunSummaryDao.insert(createRunCleanupSummary(POLICY_KEY_1, artifactRunId).toBuilder()
                .type(Type.ARTIFACT)
                .build());

        List<CleanupRunSummaryMigrationRow> rows =
                cleanupRunSummaryDao.listCleanupRunSummariesForMigration(null, 10);
        assertEquals(2, rows.size());
        assertEquals(packageRunId, rows.get(0).summary().policyRunId());
        assertEquals(artifactRunId, rows.get(1).summary().policyRunId());
        assertEquals(Type.ARTIFACT, rows.get(1).summary().type());
    }

    @Test
    public void listCleanupRunSummariesForMigrationPaginatesByPolicyRunId() throws SQLException {
        int runId1 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId2 = createQuartzHistoryRecord(POLICY_KEY_1);
        int runId3 = createQuartzHistoryRecord(POLICY_KEY_2);
        cleanupRunSummaryDao.insert(createRunCleanupSummary(POLICY_KEY_1, runId1));
        cleanupRunSummaryDao.insert(createRunCleanupSummary(POLICY_KEY_1, runId2));
        cleanupRunSummaryDao.insert(createRunCleanupSummary(POLICY_KEY_2, runId3));

        List<CleanupRunSummaryMigrationRow> firstPage =
                cleanupRunSummaryDao.listCleanupRunSummariesForMigration(null, 2);
        assertEquals(2, firstPage.size());
        assertEquals(runId1, firstPage.get(0).summary().policyRunId());
        assertEquals(runId2, firstPage.get(1).summary().policyRunId());

        List<CleanupRunSummaryMigrationRow> secondPage =
                cleanupRunSummaryDao.listCleanupRunSummariesForMigration((long) runId2, 2);
        assertEquals(1, secondPage.size());
        assertEquals(runId3, secondPage.get(0).summary().policyRunId());
        assertNotNull(secondPage.get(0).runConfigBytes());
    }
}
