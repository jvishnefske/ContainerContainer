package org.artifactory.storage.db.aql.sql.model;

import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the static reverse-lookup ({@link AqlFieldExtensionEnum#getExtensionFor}) introduced to replace the
 * per-call {@code values()} linear scan (RTDEV-88344). The lookup must preserve the original behavior exactly.
 */
class AqlFieldExtensionEnumTest {

    /**
     * The cached map must return the same extension that a linear scan over {@code values()} would have returned,
     * for every physical field that is mapped by some extension.
     */
    @Test
    void getExtensionForReturnsSameAsLinearScanForEveryMappedField() {
        // Build the reference mapping the same way the old implementation behaved: first match wins.
        Map<AqlPhysicalFieldEnum, AqlFieldExtensionEnum> expected = new HashMap<>();
        for (AqlFieldExtensionEnum extension : AqlFieldExtensionEnum.values()) {
            expected.putIfAbsent(extension.getExtendedField(), extension);
        }

        for (Map.Entry<AqlPhysicalFieldEnum, AqlFieldExtensionEnum> entry : expected.entrySet()) {
            assertSame(entry.getValue(), AqlFieldExtensionEnum.getExtensionFor(entry.getKey()),
                    "Unexpected extension for physical field " + entry.getKey());
        }
    }

    /**
     * Sanity check across all physical fields: a mapped field resolves to an extension whose extended field matches,
     * and an unmapped field resolves to {@code null} (preserving the original contract).
     */
    @ParameterizedTest
    @EnumSource(AqlPhysicalFieldEnum.class)
    void getExtensionForIsConsistentForEveryPhysicalField(AqlPhysicalFieldEnum field) {
        AqlFieldExtensionEnum extension = AqlFieldExtensionEnum.getExtensionFor(field);
        if (extension != null) {
            assertEquals(field, extension.getExtendedField(),
                    "Resolved extension does not point back to the requested physical field");
        }
    }

    /**
     * Every real physical field is mapped, so the only no-match input is {@code null}. The original linear scan
     * returned {@code null} for it (no extension has a {@code null} aqlField); the map lookup must do the same.
     */
    @Test
    void getExtensionForReturnsNullForNoMatch() {
        assertNull(AqlFieldExtensionEnum.getExtensionFor(null));
    }
}