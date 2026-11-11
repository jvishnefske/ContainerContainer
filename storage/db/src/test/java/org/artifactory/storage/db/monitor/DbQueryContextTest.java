package org.artifactory.storage.db.monitor;

import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbQueryContextTest {
    @Test
    void constructor_shouldNotFailWhenThereIsNoCurrentActivity() {
        DbQueryContext context = new DbQueryContext("SELECT * FROM table", true, "select");
        assertEquals("SELECT * FROM table", context.getQuery());
        assertTrue(context.isStreaming());
        assertEquals("select", context.getHint());
        assertNull(context.getActionName());
        assertNull(context.getActionActor());
        assertNull(context.getActionGroup());
        assertNull(context.getActionPackage());
        assertNull(context.getActionKind());
    }

    @Test
    void constructor_shouldTakeCurrentActivity() {
        try (ActivityScope ignored = CurrentActivity.newScope("http/get", "probes")) {
            CurrentActivity.NAME.put("/system/ping");
            CurrentActivity.ACTOR.put("ci");
            CurrentActivity.PACKAGE.put("none");

            DbQueryContext context = new DbQueryContext("SELECT * FROM table", true, "select");
            assertEquals("SELECT * FROM table", context.getQuery());
            assertTrue(context.isStreaming());
            assertEquals("select", context.getHint());
            assertEquals("/system/ping", context.getActionName());
            assertEquals("ci", context.getActionActor());
            assertEquals("probes", context.getActionGroup());
            assertEquals("none", context.getActionPackage());
            assertEquals("http/get", context.getActionKind());
        }
    }

    @Test
    void getRouteKey_shouldReturnActionName_whenPresent() {
        DbQueryContext context = new DbQueryContext("SELECT 1", false, null, false,
                "search/aql", "REST", "search", "maven", "admin");
        assertEquals("search/aql", context.getRouteKey());
    }

    @Test
    void getRouteKey_shouldReturnGroupAndPackage_whenNameIsAbsent() {
        DbQueryContext context = new DbQueryContext("SELECT 1", false, null, false,
                null, "HTTP", "download", "npm", "user");
        assertEquals("download npm", context.getRouteKey());
    }

    @Test
    void getRouteKey_shouldReturnGroupAlone_whenPackageIsAbsent() {
        DbQueryContext context = new DbQueryContext("SELECT 1", false, null, false,
                null, "JOB", "gc", null, "system");
        assertEquals("gc", context.getRouteKey());
    }

    @Test
    void getRouteKey_shouldReturnUnknown_whenNoContextIsAvailable() {
        DbQueryContext context = new DbQueryContext("SELECT 1", false, null, false,
                null, null, null, null, null);
        assertEquals("unknown", context.getRouteKey());
    }
}