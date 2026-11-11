package org.artifactory.throtlling.qrl.model;

import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Andrey Tashlitsky
 */
class RateLimiterSettingsTest {

    @Test
    void testEquals() {
        RateLimiterSettings qrlSettingsDefault = new RateLimiterSettings(DEFAULT, 1_000, 1_000, 500);
        RateLimiterSettings qrlSettingsDefaultOther = new RateLimiterSettings(DEFAULT, 50_000, 5_000, 50_000);
        RateLimiterSettings qrlSettingsSystem = new RateLimiterSettings(SYSTEM, 1_000, 1_000, 500);
        assertEquals(qrlSettingsDefault, qrlSettingsDefaultOther);
        assertNotEquals(qrlSettingsDefault, qrlSettingsSystem);
        assertNotEquals(qrlSettingsDefault, new Object());
    }

    @Test
    void testHashcode() {
        RateLimiterSettings qrlSettingsDefault = new RateLimiterSettings(DEFAULT, 1_000, 1_000, 500);
        assertEquals(qrlSettingsDefault.hashCode(), Objects.hash(qrlSettingsDefault.getRlType()));
    }
}
