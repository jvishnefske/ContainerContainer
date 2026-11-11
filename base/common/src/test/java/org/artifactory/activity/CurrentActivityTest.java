package org.artifactory.activity;

import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.Method;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_GROUP;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_KIND;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_PACKAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrentActivityTest {
    @BeforeEach
    void beforeEachTest() {
        // Make sure MDC is empty - some other test could leave it dirty
        MDC.clear();
    }

    @AfterEach
    void afterEachTest() {
        MDC.clear();
    }

    @Test
    void put_shouldUpdateCorrectMdcProperty() {
        CurrentActivity.KIND.put("kind");
        CurrentActivity.GROUP.put("group");
        CurrentActivity.PACKAGE.put(PackageType.DOCKER);
        CurrentActivity.NAME.put("name");
        CurrentActivity.ACTOR.put("actor");
        CurrentActivity.REPO.put("repo");
        CurrentActivity.REPO_TYPE.put("repo-type");

        assertEquals("kind", MDC.get(ActivityConstants.ACTIVITY_PROP_KIND));
        assertEquals("group", MDC.get(ActivityConstants.ACTIVITY_PROP_GROUP));
        assertEquals("docker", MDC.get(ActivityConstants.ACTIVITY_PROP_PACKAGE));
        assertEquals("name", MDC.get(ActivityConstants.ACTIVITY_PROP_NAME));
        assertEquals("actor", MDC.get(ActivityConstants.ACTIVITY_PROP_ACTOR));
        assertEquals("repo", MDC.get(ActivityConstants.ACTIVITY_PROP_REPO_KEY));
        assertEquals("repo-type", MDC.get(ActivityConstants.ACTIVITY_PROP_REPO_TYPE));
    }

    @Test
    void putIfEmpty_shouldUpdateMdcWhenNull() {
        MDC.remove(ActivityConstants.ACTIVITY_PROP_KIND);
        CurrentActivity.KIND.putIfEmpty("first");
        assertEquals("first", MDC.get(ActivityConstants.ACTIVITY_PROP_KIND));
    }

    @Test
    void putIfEmpty_shouldUpdateMdcWhenEmpty() {
        MDC.put(ActivityConstants.ACTIVITY_PROP_KIND, "");
        CurrentActivity.KIND.putIfEmpty("second");
        assertEquals("second", MDC.get(ActivityConstants.ACTIVITY_PROP_KIND));
    }

    @Test
    void putIfEmpty_shouldNotChangeMdcWhenNotEmpty() {
        MDC.put(ActivityConstants.ACTIVITY_PROP_KIND, "first");
        CurrentActivity.KIND.putIfEmpty("second");
        assertEquals("first", MDC.get(ActivityConstants.ACTIVITY_PROP_KIND));
    }

    @Test
    void testEnumMethods() {
        MDC.put(ActivityConstants.ACTIVITY_PROP_KIND, "test");
        assertEquals("test", CurrentActivity.KIND.get(), "get() supposed to read value from MDC");

        CurrentActivity.KIND.remove();
        assertNull(MDC.get(ActivityConstants.ACTIVITY_PROP_KIND), "remove() supposed to delete MDC property");
        assertEquals("default", CurrentActivity.KIND.get("default"));

        CurrentActivity.KIND.put("test");
        assertEquals("test", MDC.get(ActivityConstants.ACTIVITY_PROP_KIND), "put() should update MDC");
    }

    @Test
    void CurrentActivity_context_shouldReturnValue() {
        assertNotNull(CurrentActivity.context(), "CurrentActivity.get() must return non-null value");
    }

    @Test
    void CurrentActivity_updateContext_shouldChangeGlobalContext() {
        ActivityContext previous = CurrentActivity.context();
        DefaultActivityContext newContext = new DefaultActivityContext();

        // Note: In the real life, callers of the CurrentActivity.set would proxy or save the previous context.
        ActivityContext actual = CurrentActivity.updateContext(old -> newContext);

        assertEquals(previous, actual, "CurrentActivity.set() supposed to return the previous context");

        actual = CurrentActivity.context();
        assertEquals(newContext, actual, "CurrentActivity.set() supposed to assign the new context");
    }

    @Test
    void CurrentActivity_newScope_shouldCreateScope() {
        CurrentActivity.context().putKind("before-test");

        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.context().putKind("test");

            String actual = MDC.get(ACTIVITY_PROP_KIND);
            assertEquals("test", actual, "putKind() supposed to change MDC");
        }

        String actual = CurrentActivity.context().getKind();
        assertEquals("before-test", actual, "Closing ActivityScope supposed to restore MDC");
    }

    @Test
    void CurrentActivity_newScopeWithKind_shouldUpdateMdc() {
        try (ActivityScope ignored = CurrentActivity.newScope("check")) {
            String actual = MDC.get(ACTIVITY_PROP_KIND);
            assertEquals("check", actual, "newScope(kind) supposed to store kind in MDC");
        }
    }

    @Test
    void CurrentActivity_newScope_shouldUpdateKindAndGroup() {
        try (ActivityScope ignored = CurrentActivity.newScope("test", "scope")) {
            String actual = MDC.get(ACTIVITY_PROP_KIND);
            assertEquals("test", actual, "newScope(kind, group) supposed to store kind in MDC");

            actual = MDC.get(ACTIVITY_PROP_GROUP);
            assertEquals("scope", actual, "newScope(kind, group) supposed to store group in MDC");

            // current: kind=test, group=scope
            try (ActivityScope ignoredSubScope = CurrentActivity.newScope("test2", null)) {
                // expected: kind=test2, group=scope (kind changed, group remained)
                actual = MDC.get(ACTIVITY_PROP_KIND);
                assertEquals("test2", actual, "newScope(kind, null) supposed to change kind");

                actual = MDC.get(ACTIVITY_PROP_GROUP);
                assertEquals("scope", actual, "newScope(kind, null) supposed to keep the previous group in MDC");
            }

            // current: kind=test, group=scope
            try (ActivityScope ignoredSubScope = CurrentActivity.newScope(null, "child")) {
                // expected: kind=test, group=child (kind remained, group changed)
                actual = MDC.get(ACTIVITY_PROP_KIND);
                assertEquals("test", actual, "newScope(null, child) supposed to keep previous kind");

                actual = MDC.get(ACTIVITY_PROP_GROUP);
                assertEquals("child", actual, "newScope(null, child) supposed to change the group name");
            }
        }
    }

    @Test
    void applyActivityDetailsFromAnnotations_simpleAnnotation() throws NoSuchMethodException {
        // Use case 1: pass interface method, which was not re-annotated on the class level
        BasicPurger target = new BasicPurger();
        CurrentActivity.updateFromAnnotations(target, Purger.class.getMethod("findAndQueueOldFilesToDelete"));

        assertEquals("purger", CurrentActivity.context().getKind(), "Kind should come from the interface");
        assertEquals("queue", CurrentActivity.context().getGroup(), "Group should come from the interface");
    }

    @Test
    void applyActivityDetailsFromAnnotations_classOverridesAnnotation() throws NoSuchMethodException {
        // Use case 2: pass interface method, which was re-annotated on the class level
        BasicPurger target = new BasicPurger();
        CurrentActivity.updateFromAnnotations(target, Purger.class.getMethod("deleteQueuedFiles"));

        assertEquals("purger", CurrentActivity.context().getKind(), "Kind should come from the interface");
        assertEquals("process", CurrentActivity.context().getGroup(), "Group should come from the class");
    }

    @Test
    void applyActivityDetailsFromAnnotations_derivedClassOverridesInterfaceAnnotation() throws NoSuchMethodException {
        // Use case 3: pass interface method, which was re-annotated in the derived class
        DockerPurger target = new DockerPurger();
        CurrentActivity.updateFromAnnotations(target, Purger.class.getMethod("findAndQueueOldFilesToDelete"));

        assertEquals("purger", CurrentActivity.context().getKind(), "Kind should come from the interface");
        assertEquals("find", CurrentActivity.context().getGroup(), "Group should come from the derived class");
        assertEquals("docker", CurrentActivity.context().getPackageType(), "Package type should from from class");
    }

    @Test
    void applyActivityDetailsFromAnnotations_derivedClassOverridesBaseClass() throws NoSuchMethodException {
        // Use case 4: pass interface method, which was re-annotated both in the base class and in the derived class
        DockerPurger target = new DockerPurger();
        CurrentActivity.updateFromAnnotations(target, BasicPurger.class.getMethod("deleteQueuedFiles"));

        assertEquals("purger", CurrentActivity.context().getKind(), "Kind should come from the interface");
        assertEquals("delete", CurrentActivity.context().getGroup(), "Group should come from the derived class");
        assertEquals("docker", CurrentActivity.context().getPackageType(), "Package type should from from class");
    }

    @Test
    void applyActivityDetailsFromAnnotations_staticMethod() throws NoSuchMethodException {
        CurrentActivity.updateFromAnnotations(null, DockerPurger.class.getMethod("diagnose"));

        // Note: annotations of the interface are still inherited by the class, so we should have the kind
        assertEquals("purger", CurrentActivity.context().getKind(), "Kind should come from the interface");
        assertEquals("test", CurrentActivity.context().getGroup(), "Group should come from the method");
        assertEquals("docker", CurrentActivity.context().getPackageType(), "Package type should from from class");
    }

    @Test
    void findAnnotation_onMethod() throws NoSuchMethodException {
        Method method = BasicPurger.class.getMethod("findAndQueueOldFilesToDelete");

        ActivityDetails actual = CurrentActivity.findAnnotation(method, ActivityDetails.class);
        assertNotNull(actual, "findAnnotation() should find annotation on the method");
        assertEquals("queue", actual.group(), "Method's annotation should come from the interface");
    }

    @Test
    void findAnnotation_onClass() throws NoSuchMethodException {
        Method method = Purger.class.getMethod("deleteQueuedFiles");

        ActivityDetails actual = CurrentActivity.findAnnotation(method, ActivityDetails.class);
        assertNotNull(actual, "No annotation on method, so findAnnotation() should get it from class");
        assertEquals("purger", actual.kind(), "Annotation should come from the class");
    }

    @Test
    void findAnnotation_shouldReturnNull_whenNoAnnotationWasFound() throws NoSuchMethodException {
        Method method = CurrentActivity.class.getMethod("context");

        ActivityDetails actual = CurrentActivity.findAnnotation(method, ActivityDetails.class);
        assertNull(actual, "No ActivityDetails annotation should be on the method or class");
    }

    @Test
    void applyOnCurrentActivity_shouldUseCustomPackageType_whenProvided() throws NoSuchMethodException {
        // Test using annotation with custom package type
        Method method = CustomPackageAnnotatedClass.class.getMethod("processCustom");

        // Get the annotation from the method
        ActivityDetails annotation = method.getAnnotation(ActivityDetails.class);
        assertNotNull(annotation, "Annotation should be present on the method");

        CurrentActivity.applyOnCurrentActivity(annotation);

        assertEquals("custom-processing", CurrentActivity.context().getGroup());
        // customPackageType should have priority over packageType
        assertEquals("internal-system", CurrentActivity.context().getPackageType());
        assertEquals("internal-system", MDC.get(ACTIVITY_PROP_PACKAGE));
    }

    @Test
    void applyOnCurrentActivity_shouldUsePackageType_whenCustomPackageTypeIsEmpty() throws NoSuchMethodException {
        // Test using annotation with regular package type but no custom package type
        Method method = RegularPackageAnnotatedClass.class.getMethod("processRegular");

        ActivityDetails annotation = method.getAnnotation(ActivityDetails.class);
        assertNotNull(annotation, "Annotation should be present on the method");

        CurrentActivity.applyOnCurrentActivity(annotation);

        assertEquals("regular-processing", CurrentActivity.context().getGroup());
        assertEquals("maven", CurrentActivity.context().getPackageType());
        assertEquals("maven", MDC.get(ACTIVITY_PROP_PACKAGE));
    }

    @Test
    void applyOnCurrentActivity_customPackageTypeTakesPrecedence() throws NoSuchMethodException {
        // Test annotation with both custom package type and regular package type
        Method method = BothPackageTypesAnnotatedClass.class.getMethod("processBoth");

        ActivityDetails annotation = method.getAnnotation(ActivityDetails.class);
        assertNotNull(annotation, "Annotation should be present on the method");

        CurrentActivity.applyOnCurrentActivity(annotation);

        assertEquals("both-processing", CurrentActivity.context().getGroup());
        // customPackageType should take precedence over packageType
        assertEquals("custom", CurrentActivity.context().getPackageType());
        assertEquals("custom", MDC.get(ACTIVITY_PROP_PACKAGE));
    }

    @Test
    void applyOnCurrentActivity_shouldSkipEmptyCustomPackageType() throws NoSuchMethodException {
        // Test annotation with empty custom package type - should use regular packageType
        Method method = EmptyCustomPackageAnnotatedClass.class.getMethod("processEmpty");

        ActivityDetails annotation = method.getAnnotation(ActivityDetails.class);
        assertNotNull(annotation, "Annotation should be present on the method");

        CurrentActivity.applyOnCurrentActivity(annotation);

        assertEquals("empty-processing", CurrentActivity.context().getGroup());
        assertEquals("docker", CurrentActivity.context().getPackageType());
        assertEquals("docker", MDC.get(ACTIVITY_PROP_PACKAGE));
    }

    @Test
    void applyOnCurrentActivity_customPackageType_fromAnnotations() throws NoSuchMethodException {
        // Test using real annotation via updateFromAnnotations
        CustomPackageAnnotatedClass target = new CustomPackageAnnotatedClass();
        CurrentActivity.updateFromAnnotations(target, CustomPackageAnnotatedClass.class.getMethod("processCustom"));

        assertEquals("processor", CurrentActivity.context().getKind());
        assertEquals("custom-processing", CurrentActivity.context().getGroup());
        assertEquals("internal-system", CurrentActivity.context().getPackageType());
    }

    //#region ---------- Inner classes to test annotations ----------
    @ActivityDetails(kind = "purger")
    interface Purger {
        @ActivityDetails(group = "queue")
        void findAndQueueOldFilesToDelete();

        void deleteQueuedFiles();
    }

    static class BasicPurger implements Purger {
        public void findAndQueueOldFilesToDelete() {
            // Do nothing - just a test
        }

        @ActivityDetails(group = "process")
        public void deleteQueuedFiles() {
            // Do nothing - just a test
        }
    }

    @ActivityDetails(PackageType.DOCKER)
    static class DockerPurger extends BasicPurger {
        @ActivityDetails(group = "find") // override the group defined in the interface
        @Override
        public void findAndQueueOldFilesToDelete() {
            // Do nothing - just a test
        }

        @ActivityDetails(group = "delete") // override the group defined in the base class
        @Override
        public void deleteQueuedFiles() {
            // Do nothing - just a test
        }

        @ActivityDetails(group = "test") // override the group defined in the base class
        public static void diagnose() {
            // Do nothing - just a test
        }
    }

    @ActivityDetails(kind = "processor")
    static class CustomPackageAnnotatedClass {
        @ActivityDetails(group = "custom-processing", customPackageType = "internal-system")
        public void processCustom() {
            // Do nothing - just a test
        }
    }

    static class RegularPackageAnnotatedClass {
        @ActivityDetails(group = "regular-processing", packageType = PackageType.MAVEN)
        public void processRegular() {
            // Do nothing - just a test
        }
    }

    static class BothPackageTypesAnnotatedClass {
        @ActivityDetails(group = "both-processing", packageType = PackageType.DOCKER, customPackageType = "custom")
        public void processBoth() {
            // Do nothing - just a test
        }
    }

    static class EmptyCustomPackageAnnotatedClass {
        @ActivityDetails(group = "empty-processing", packageType = PackageType.DOCKER, customPackageType = "")
        public void processEmpty() {
            // Do nothing - just a test
        }
    }
    //#endregion
}