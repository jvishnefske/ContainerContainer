package org.artifactory.activity;

import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_ACTOR;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_GROUP;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_KIND;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_NAME;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_PACKAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultActivityContextTest {
    @BeforeEach
    void setup() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void get_shouldReturnDefaultValue_whenMdcIsEmpty() {
        ActivityContext activity = new DefaultActivityContext();
        String actual = activity.get(ACTIVITY_PROP_KIND, "(default)");
        assertEquals("(default)", actual);
    }

    @Test
    void get_shouldReturnValueFromMdc() {
        ActivityContext activity = new DefaultActivityContext();
        String actual = activity.get(ACTIVITY_PROP_KIND);
        assertNull(actual, "When property is missing in MDC, ActivityContext.get() should return null");

        MDC.put(ACTIVITY_PROP_KIND, "test");
        actual = activity.get(ACTIVITY_PROP_KIND);
        assertEquals("test", actual);
    }

    @Test
    void put_shouldUpdateMdc() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = MDC.get(ACTIVITY_PROP_NAME);
        assertNull(actual, "Initially MDC should be empty");

        activity.put(ACTIVITY_PROP_NAME, "sample");
        actual = MDC.get(ACTIVITY_PROP_NAME);
        assertEquals("sample", actual, "ActivityContext.put() supposed to update MDC");
    }

    @Test
    void put_null_shouldRemoveValueFromMdc() {
        ActivityContext activity = new DefaultActivityContext();

        MDC.put(ACTIVITY_PROP_NAME, "sample");

        activity.put(ACTIVITY_PROP_NAME, null);
        String actual = MDC.get(ACTIVITY_PROP_NAME);
        assertNull(actual, "ActivityContext.put(null) supposed to remove value from MDC");
    }

    @Test
    void testKindProperty() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = activity.getKind();
        assertNull(actual, "getKind() supposed to be null when MDC is empty");

        activity.putKind("test");
        actual = MDC.get(ACTIVITY_PROP_KIND);
        assertEquals("test", actual, "putKind() supposed to update MDC");

        activity.putKind(null);
        actual = MDC.get(ACTIVITY_PROP_KIND);
        assertNull(actual, "putKind(null) supposed to delete value from MDC");

        actual = activity.getKind("(default)");
        assertEquals("(default)", actual, "getKind() supposed to return default when MDC is empty");

        MDC.put(ACTIVITY_PROP_KIND, "sample");
        actual = activity.getKind("(default)");
        assertEquals("sample", actual, "getKind() supposed to return value from MDC");
    }

    @Test
    void testGroupProperty() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = activity.getGroup();
        assertNull(actual, "getGroup() supposed to be null when MDC is empty");

        activity.putGroup("test");
        actual = MDC.get(ACTIVITY_PROP_GROUP);
        assertEquals("test", actual, "putGroup() supposed to update MDC");

        activity.putGroup(null);
        actual = MDC.get(ACTIVITY_PROP_GROUP);
        assertNull(actual, "putGroup(null) supposed to delete value from MDC");

        actual = activity.getGroup("(default)");
        assertEquals("(default)", actual, "getGroup() supposed to return default when MDC is empty");

        MDC.put(ACTIVITY_PROP_GROUP, "sample");
        actual = activity.getGroup("(default)");
        assertEquals("sample", actual, "getGroup() supposed to return value from MDC");
    }

    @Test
    void testPackageProperty() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = activity.getPackageType();
        assertNull(actual, "getPackageType() supposed to be null when MDC is empty");

        activity.putPackageType("test");
        actual = MDC.get(ACTIVITY_PROP_PACKAGE);
        assertEquals("test", actual, "putPackageType() supposed to update MDC");

        activity.putPackageType((String)null);
        actual = MDC.get(ACTIVITY_PROP_PACKAGE);
        assertNull(actual, "putPackageType(null) supposed to delete value from MDC");

        actual = activity.getPackageType("(default)");
        assertEquals("(default)", actual, "getPackageType() supposed to return default when MDC is empty");

        activity.putPackageType(PackageType.NUGET);
        actual = MDC.get(ACTIVITY_PROP_PACKAGE);
        assertEquals("nuget", actual, "putPackageType() supposed to convert enum to lower case");

        MDC.put(ACTIVITY_PROP_PACKAGE, "sample");
        actual = activity.getPackageType("(default)");
        assertEquals("sample", actual, "getPackageType() supposed to return value from MDC");
    }

    @Test
    void testNameProperty() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = activity.getName();
        assertNull(actual, "getName() supposed to be null when MDC is empty");

        activity.putName("test");
        actual = MDC.get(ACTIVITY_PROP_NAME);
        assertEquals("test", actual, "putName() supposed to update MDC");

        activity.putName(null);
        actual = MDC.get(ACTIVITY_PROP_NAME);
        assertNull(actual, "putName(null) supposed to delete value from MDC");

        actual = activity.getName("(default)");
        assertEquals("(default)", actual, "getName() supposed to return default when MDC is empty");

        MDC.put(ACTIVITY_PROP_NAME, "sample");
        actual = activity.getName("(default)");
        assertEquals("sample", actual, "getName() supposed to return value from MDC");
    }

    @Test
    void testActorProperty() {
        ActivityContext activity = new DefaultActivityContext();

        String actual = activity.getActor();
        assertNull(actual, "getActor() supposed to be null when MDC is empty");

        activity.putActor("test");
        actual = MDC.get(ACTIVITY_PROP_ACTOR);
        assertEquals("test", actual, "putActor() supposed to update MDC");

        activity.putActor(null);
        actual = MDC.get(ACTIVITY_PROP_ACTOR);
        assertNull(actual, "putActor(null) supposed to delete value from MDC");

        actual = activity.getActor("(default)");
        assertEquals("(default)", actual, "getActor() supposed to return default when MDC is empty");

        MDC.put(ACTIVITY_PROP_ACTOR, "sample");
        actual = activity.getActor("(default)");
        assertEquals("sample", actual, "getActor() supposed to return value from MDC");
    }
}