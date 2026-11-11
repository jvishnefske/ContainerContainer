package org.artifactory.activity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActivityScopeTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void checkStandardActivityScopeFlow() {
        MDC.clear();
        MDC.put("name", "value");
        try (var _ = new ActivityScope()) {
            MDC.put("name", "modified");
        }
        Map<String, String> actual = MDC.getCopyOfContextMap();
        assertEquals("value", actual.get("name"));
    }

    @Test
    void closeShouldClearMdcWhenSnapshotWasNull() {
        MDC.clear();
        assertNull(MDC.getCopyOfContextMap());

        try (var _ = new ActivityScope()) {
            MDC.put(ActivityConstants.ACTIVITY_PROP_GROUP, "some-group");
        }

        assertNull(MDC.get(ActivityConstants.ACTIVITY_PROP_GROUP));
    }
}