package org.artifactory.storage.db.cleanup;

import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyViewRun;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupPolicyViewDao;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupRunSummaryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

@Test
public class CleanupPolicyViewDaoTest extends DbBaseTest {

    @Autowired
    private CleanupPolicyViewDao cleanupPolicyViewDao;
    @Autowired
    private CleanupRunSummaryDao cleanupRunSummaryDao;
    @Autowired
    private QuartzTaskHistoryDao quartzTaskHistoryDao;
    private static final String POLICY_1 = "policy1";
    private static final String POLICY_2 = "policy2";
    private static final String POLICY_3 = "policy3";
    private static final String POLICY_4 = "policy4";

    @BeforeMethod
    public void setup() throws SQLException {
        int run1 = createQuartzHistoryRecord(POLICY_1);
        int run2 = createQuartzHistoryRecord(POLICY_1);
        int run3 = createQuartzHistoryRecord(POLICY_1);
        cleanupRunSummaryDao.insert(createSummary(POLICY_1, run1, true));
        cleanupRunSummaryDao.insert(createSummary(POLICY_1, run2, true));
        cleanupRunSummaryDao.insert(createSummary(POLICY_1, run3, true));

        int run4 = createQuartzHistoryRecord(POLICY_2);
        int run5 = createQuartzHistoryRecord(POLICY_2);
        int run6 = createQuartzHistoryRecord(POLICY_2);
        cleanupRunSummaryDao.insert(createSummary(POLICY_2, run4, false));
        cleanupRunSummaryDao.insert(createSummary(POLICY_2, run5, true));
        cleanupRunSummaryDao.insert(createSummary(POLICY_2, run6, false));

        int run7 = createQuartzHistoryRecord(POLICY_3);
        int run8 = createQuartzHistoryRecord(POLICY_3);
        int run9 = createQuartzHistoryRecord(POLICY_3);
        cleanupRunSummaryDao.insert(createSummary(POLICY_3, run7, false));
        cleanupRunSummaryDao.insert(createSummary(POLICY_3, run8, false));
        cleanupRunSummaryDao.insert(createSummary(POLICY_3, run9, false));

        int run10 = createQuartzHistoryRecord(POLICY_4);
        int run11 = createQuartzHistoryRecord(POLICY_4);
        int run12 = createQuartzHistoryRecord(POLICY_4);
        cleanupRunSummaryDao.insert(createSummary(POLICY_4, run10, false));
        cleanupRunSummaryDao.insert(createSummary(POLICY_4, run11, false));
        cleanupRunSummaryDao.insert(
                createSummary(POLICY_4, run12, false).toBuilder().type(CleanupRunSummary.Type.PACKAGE).build());
    }

    @AfterMethod
    public void cleanDb() throws SQLException {
        quartzTaskHistoryDao.deleteAll();
        cleanupRunSummaryDao.deleteAll();
    }

    @Test
    public void testGetPolices() throws SQLException {
        List<CleanupPolicyViewRun> actual =
                cleanupPolicyViewDao.getPolices(Stream.of(POLICY_1, POLICY_2).collect(Collectors.toList()));
        // We should get two policies in response.
        assertEquals(2, actual.size());
        CleanupPolicyViewRun firstPolicy = actual.get(0);
        CleanupPolicyViewRun secondPolicy = actual.get(1);
        assertPolicy(POLICY_1, 3, true, firstPolicy);
        assertPolicy(POLICY_2, 6, false, secondPolicy);
    }

    @Test
    public void testGetPolicesTotalItemTypes() throws SQLException {
        List<CleanupPolicyViewRun> actual =
                cleanupPolicyViewDao.getPolices(Stream.of(POLICY_4).collect(Collectors.toList()));
        // We should get one policy in response.
        assertEquals(1, actual.size());
        CleanupPolicyViewRun firstPolicy = actual.get(0);
        assertEquals(1302, firstPolicy.totalItems());
    }

    @Test
    public void testGetPolicesEmpty() throws SQLException {
        List<CleanupPolicyViewRun> actual =
                cleanupPolicyViewDao.getPolices(List.of("key1", "key2"));
        // We should not get any policies.
        assertEquals(0, actual.size());
    }


    private CleanupRunSummary createSummary(String policy, int runId, boolean dryRun) {
        return CleanupRunSummary.builder()
                .policyKey(policy)
                .policyRunId(runId)
                .dryRun(dryRun)
                .startedDate(1)
                .finishedDate(1)
                .status(CleanupRunSummary.Status.COMPLETED)
                .type(CleanupRunSummary.Type.ARTIFACT)
                .tempNodesCleaned(false)
                .deletedPackagesCount(601)
                .failedPackagesCount(701)
                .deletedArtifactsCount(801)
                .failedArtifactsCount(901)
                .originalBinarySize(1111)
                .estimatedDeletedBinarySize(1111)
                .triggeredBy("test")
                .totalBinarySize(2222)
                .build();
    }

    private void assertPolicy(String key, int runId, boolean dryRun, CleanupPolicyViewRun policy) {
        assertEquals(key, policy.policyKey());
        assertEquals(runId, policy.runId());
        assertEquals(dryRun, policy.dryRun());
    }

    private int createQuartzHistoryRecord(String policyKey) throws SQLException {
        return quartzTaskHistoryDao.insert(QuartzTaskHistoryRecord.builder()
                .taskId("task")
                .taskType("package")
                .externalId(policyKey)
                .username("user")
                .status(QuartzTaskHistoryInfo.Status.COMPLETED)
                .serverId("server").build()
        );
    }
}
