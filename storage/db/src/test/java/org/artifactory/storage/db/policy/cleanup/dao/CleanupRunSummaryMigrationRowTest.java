package org.artifactory.storage.db.policy.cleanup.dao;

import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupRunSummaryMigrationRowTest {

    private static final byte[] RUN_CONFIG_BYTES = "{\"dryRun\":false}".getBytes(StandardCharsets.UTF_8);

    @Test
    void equals_sameContent() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow left =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES.clone());
        CleanupRunSummaryMigrationRow right =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES.clone());

        assertEquals(left, right);
        assertEquals(left, left);
    }

    @Test
    void equals_differentRunConfigBytes() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow left =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES);
        CleanupRunSummaryMigrationRow right = new CleanupRunSummaryMigrationRow(summary,
                "{\"dryRun\":true}".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(left, right);
    }

    @Test
    void equals_nullRunConfigBytes() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow bothNull =
                new CleanupRunSummaryMigrationRow(summary, null);
        CleanupRunSummaryMigrationRow otherNull =
                new CleanupRunSummaryMigrationRow(summary, null);
        CleanupRunSummaryMigrationRow withBytes =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES);

        assertEquals(bothNull, otherNull);
        assertNotEquals(bothNull, withBytes);
        assertNotEquals(withBytes, bothNull);
    }

    @Test
    void equals_rejectsNullAndDifferentType() {
        CleanupRunSummaryMigrationRow row =
                new CleanupRunSummaryMigrationRow(sampleSummary(), RUN_CONFIG_BYTES);

        assertFalse(row.equals(null));
        assertFalse(row.equals("not-a-row"));
    }

    @Test
    void hashCode_equalObjectsSameHash() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow left =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES.clone());
        CleanupRunSummaryMigrationRow right =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES.clone());

        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void hashCode_nullRunConfigBytes() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow bothNull =
                new CleanupRunSummaryMigrationRow(summary, null);
        CleanupRunSummaryMigrationRow otherNull =
                new CleanupRunSummaryMigrationRow(summary, null);

        assertEquals(bothNull.hashCode(), otherNull.hashCode());
    }

    @Test
    void toString_containsSummaryInfo() {
        CleanupRunSummary summary = sampleSummary();
        CleanupRunSummaryMigrationRow row =
                new CleanupRunSummaryMigrationRow(summary, RUN_CONFIG_BYTES);

        String text = row.toString();

        assertTrue(text.contains("CleanupRunSummaryMigrationRow"));
        assertTrue(text.contains("cleanup-docker"));
        assertTrue(text.contains("847291"));
        assertTrue(text.contains("123, 34, 100, 114, 121, 82, 117, 110, 34, 58, 102, 97, 108, 115, 101, 125"));
    }

    private static CleanupRunSummary sampleSummary() {
        return CleanupRunSummary.builder()
                .policyKey("cleanup-docker")
                .policyRunId(847291)
                .dryRun(false)
                .startedDate(100L)
                .finishedDate(200L)
                .status(CleanupRunSummary.Status.COMPLETED)
                .type(CleanupRunSummary.Type.PACKAGE)
                .tempNodesCleaned(true)
                .deletedPackagesCount(10)
                .failedPackagesCount(1)
                .deletedArtifactsCount(0)
                .failedArtifactsCount(0)
                .originalBinarySize(1000)
                .estimatedDeletedBinarySize(500)
                .totalBinarySize(500)
                .triggeredBy("admin")
                .build();
    }
}
