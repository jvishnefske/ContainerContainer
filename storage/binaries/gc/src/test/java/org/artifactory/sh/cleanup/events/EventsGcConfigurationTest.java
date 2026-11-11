package org.artifactory.sh.cleanup.events;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsGcConfigurationTest extends ArtifactoryHomeBoundJUnitTest {

    private void setEventsGcCleanupEnabled(boolean enabled) {
        setStringSystemProperty(ConstantValues.gcEventsCleanupEnabled, String.valueOf(enabled));
    }

    @Test
    void useEventGCinMinorGC_enabled() {
        setEventsGcCleanupEnabled(true);
        assertTrue(EventsGcConfiguration.useEventGCinMinorGC());
    }

    @Test
    void useEventGCinMinorGC_disabled() {
        setEventsGcCleanupEnabled(false);
        assertFalse(EventsGcConfiguration.useEventGCinMinorGC());
    }
}