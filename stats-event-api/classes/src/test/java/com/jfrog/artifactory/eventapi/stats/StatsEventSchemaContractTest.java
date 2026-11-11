package com.jfrog.artifactory.eventapi.stats;

import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Schema-contract regression tests for the StatsEvent wire format.
 * <p>
 * Guards the cross-team contract documented in the META-2762 design
 * (RTFACT/2399141901, design.md D11). The 2026-04-30 incident on the
 * artifactory-package-indexed-events topic demonstrated two failure
 * modes that must fail loudly at build time, not at deploy time:
 * <ol>
 *   <li>An OpenAPI-generator upgrade silently changing the
 *       hash-derived protobuf field tags, breaking wire compatibility
 *       for every consumer in the fleet.</li>
 *   <li>An accidental rename or removal of a wire-contract field.</li>
 * </ol>
 * The {@link #allFieldTagsAreStable()} test pins every field tag in
 * the StatsEvent + StatsEntry messages, and additionally fails if a
 * field is added without updating this test (count mismatch) or
 * renamed (name not found in the expected map).
 */
class StatsEventSchemaContractTest {

    private static final Map<String, Integer> EXPECTED_STATS_EVENT_TAGS = Map.of(
            "schema_version", 74106941,
            "event_id", 278118624,
            "emitted_at", 512001678,
            "entries", 517831538);

    private static final Map<String, Integer> EXPECTED_STATS_ENTRY_TAGS = Map.ofEntries(
            Map.entry("repo_name", 371052744),
            Map.entry("repo_package_type", 471435073),
            Map.entry("package_name", 266552607),
            Map.entry("lead_file_path", 273527836),
            Map.entry("additional_downloads", 107050708),
            Map.entry("additional_followers", 97128957),
            Map.entry("last_downloaded", 111264657),
            Map.entry("remote_last_downloaded", 5062264),
            Map.entry("force", 97618667));

    @Test
    void allFieldTagsAreStable() {
        assertAllFieldTagsMatch(
                StatsEventOuterClass.StatsEvent.getDescriptor().getFields(),
                EXPECTED_STATS_EVENT_TAGS,
                "StatsEvent");
        assertAllFieldTagsMatch(
                StatsEntryOuterClass.StatsEntry.getDescriptor().getFields(),
                EXPECTED_STATS_ENTRY_TAGS,
                "StatsEntry");
    }

    @Test
    void requiredTopLevelFieldsArePresent() {
        StatsEventOuterClass.StatsEvent.Builder b = StatsEventOuterClass.StatsEvent.newBuilder()
                .setSchemaVersion("stats.v1")
                .setEventId("test-event-id");
        assertEquals("stats.v1", b.getSchemaVersion());
        assertEquals("test-event-id", b.getEventId());
        assertEquals(0, b.getEntriesCount());
    }

    @Test
    void roundTripSerializationPreservesRequiredFields() throws Exception {
        StatsEventOuterClass.StatsEvent original = StatsEventOuterClass.StatsEvent.newBuilder()
                .setSchemaVersion("stats.v1")
                .setEventId("evt-abc-123")
                .build();
        byte[] bytes = original.toByteArray();
        StatsEventOuterClass.StatsEvent parsed = StatsEventOuterClass.StatsEvent.parseFrom(bytes);
        assertEquals("stats.v1", parsed.getSchemaVersion());
        assertEquals("evt-abc-123", parsed.getEventId());
    }

    private static void assertAllFieldTagsMatch(
            java.util.List<FieldDescriptor> actualFields,
            Map<String, Integer> expectedTags,
            String messageName) {
        assertEquals(expectedTags.size(), actualFields.size(),
                messageName + " field count drift: a field was added or removed without"
                        + " updating " + StatsEventSchemaContractTest.class.getSimpleName()
                        + ". Update the expected-tag map and confirm the change is intentional.");
        for (FieldDescriptor f : actualFields) {
            Integer expectedTag = expectedTags.get(f.getName());
            assertNotNull(expectedTag,
                    messageName + " field '" + f.getName() + "' is not in the expected-tag map."
                            + " A field was likely renamed; renames are wire-breaking changes.");
            assertEquals(expectedTag.intValue(), f.getNumber(),
                    messageName + "." + f.getName() + ": hash-derived tag changed. This is a"
                            + " breaking wire-format change. Investigate before bumping"
                            + " openapi-generator-maven-plugin.");
        }
    }
}
