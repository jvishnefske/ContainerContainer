package org.artifactory.policy.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CleanupPolicyRunViewEntryTest {

    @Test
    void testRecordCreation() {
        CleanupPolicyRunViewEntry entry = CleanupPolicyRunViewEntry.builder()
                .runId(1)
                .itemType(CleanupPolicyRunViewEntry.Type.PACKAGE)
                .policyKey("policy1")
                .displayProject("project1")
                .startTime(1625252400L)
                .endTime(1625256000L)
                .totalItems(100L)
                .cleanedSize(5000L)
                .cleanedItems(50L)
                .status(CleanupPolicyRunViewEntry.Status.COMPLETED)
                .triggeredBy("admin")
                .manual(true)
                .dryRun(false)
                .policyTaskId(123)
                .build();

        assertNotNull(entry);
        assertEquals(1, entry.runId());
        assertEquals(CleanupPolicyRunViewEntry.Type.PACKAGE, entry.itemType());
        assertEquals("policy1", entry.policyKey());
        assertEquals("project1", entry.displayProject());
        assertEquals(1625252400L, entry.startTime());
        assertEquals(1625256000L, entry.endTime());
        assertEquals(100L, entry.totalItems());
        assertEquals(5000L, entry.cleanedSize());
        assertEquals(50L, entry.cleanedItems());
        assertEquals(CleanupPolicyRunViewEntry.Status.COMPLETED, entry.status());
        assertEquals("admin", entry.triggeredBy());
        assertTrue(entry.manual());
        assertFalse(entry.dryRun());
        assertEquals(123, entry.policyTaskId());
    }

    @Test
    void testStatusFromString() {
        assertEquals(CleanupPolicyRunViewEntry.Status.COMPLETED,
                CleanupPolicyRunViewEntry.Status.fromString("completed"));
        assertEquals(CleanupPolicyRunViewEntry.Status.IN_PROGRESS,
                CleanupPolicyRunViewEntry.Status.fromString("inprogress"));
        assertNull(CleanupPolicyRunViewEntry.Status.fromString("unknown"));
    }

    @Test
    void testTypeFromString() {
        assertEquals(CleanupPolicyRunViewEntry.Type.PACKAGE,
                CleanupPolicyRunViewEntry.Type.fromString("package"));
        assertEquals(CleanupPolicyRunViewEntry.Type.ARTIFACT,
                CleanupPolicyRunViewEntry.Type.fromString("artifact"));
        assertNull(CleanupPolicyRunViewEntry.Type.fromString("unknown"));
    }
}
