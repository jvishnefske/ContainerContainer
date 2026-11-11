package org.artifactory.sensitive.fields;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.fields.SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor;
import org.jfrog.common.config.diff.DiffElement;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.DiffEntry;
import org.jfrog.common.config.diff.DiffFunctions;
import org.jfrog.common.config.diff.DiffUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SensitiveDiffUtil} against synthetic {@code @GenerateDiffFunction} fixtures
 * ({@link TestSensitiveDiffRoot} / {@link TestSensitiveHeader}), with no repo-config coupling: static
 * {@code @Sensitive} masking, non-sensitive pass-through, and per-side {@code @DynamicSensitive} masking
 * (including sensitivity that toggles between sides and newly added sensitive elements). The {@link DiffFunctions}
 * registry is the annotation-processor-generated {@code DiffFunctionsImpl} for this test package.
 */
class SensitiveDiffUtilTest extends ArtifactoryHomeBoundJUnitTest {

    private static final DiffFunctions DIFF = new DiffFunctionsImpl();
    private static final String D = DiffUtils.DELIMITER;
    private static final String PASSWORD_PATH = "password";
    private static final String URL_PATH = "url";
    private static final String HEADER_VALUE_PATH = "headers" + D + "Authorization" + D + "value";

    @BeforeEach
    void setUp() {
        bindArtifactoryContextWithHeaderHandler();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void masksSensitiveStringFieldOnBothSides() {
        TestSensitiveDiffRoot oldConfig = root("http://same", "oldSecret", null);
        TestSensitiveDiffRoot newConfig = root("http://same", "newSecret", null);

        DiffEntry password = entry(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig), PASSWORD_PATH);
        assertEquals("***", password.getOldValue());
        assertEquals("***", password.getNewValue());
    }

    @Test
    void leavesNonSensitiveFieldsInClearText() {
        TestSensitiveDiffRoot oldConfig = root("http://old", null, null);
        TestSensitiveDiffRoot newConfig = root("http://new", null, null);

        DiffEntry url = entry(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig), URL_PATH);
        assertEquals("http://old", url.getOldValue());
        assertEquals("http://new", url.getNewValue());
    }

    @Test
    void masksSensitiveHeaderValueOnBothSides() {
        TestSensitiveDiffRoot oldConfig = root("u", null, headers("Authorization", "oldToken", true));
        TestSensitiveDiffRoot newConfig = root("u", null, headers("Authorization", "newToken", true));

        DiffEntry value = entry(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig), HEADER_VALUE_PATH);
        assertEquals("***", value.getOldValue());
        assertEquals("***", value.getNewValue());
    }

    @Test
    void masksOnlyTheSensitiveSideWhenHeaderSensitivityToggles() {
        TestSensitiveDiffRoot oldConfig = root("u", null, headers("Authorization", "oldToken", true));
        TestSensitiveDiffRoot newConfig = root("u", null, headers("Authorization", "newToken", false));

        DiffEntry value = entry(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig), HEADER_VALUE_PATH);
        assertEquals("***", value.getOldValue());
        assertEquals("newToken", value.getNewValue());
    }

    @Test
    void fullyMasksNewlyAddedHeaderWithSensitiveDescendant() {
        TestSensitiveDiffRoot oldConfig = root("u", null, headers("X-Common", "common", false));
        TestSensitiveDiffRoot newConfig = root("u", null, new ArrayList<>(List.of(
                new TestSensitiveHeader("X-Common", "common", false),
                new TestSensitiveHeader("Authorization", "secret", true))));

        DiffEntry added = entryEndingWith(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig),
                "headers" + D + "Authorization" + D + DiffUtils.NEW_MARKER);
        assertNull(added.getOldValue());
        assertEquals("***", added.getNewValue());
    }

    @Test
    void fullyMasksDeletedHeaderWithSensitiveDescendant() {
        TestSensitiveDiffRoot oldConfig = root("u", null, new ArrayList<>(List.of(
                new TestSensitiveHeader("X-Common", "common", false),
                new TestSensitiveHeader("Authorization", "secret", true))));
        TestSensitiveDiffRoot newConfig = root("u", null, headers("X-Common", "common", false));

        DiffEntry deleted = entryEndingWith(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig),
                "headers" + D + "Authorization" + D + DiffUtils.DELETED_MARKER);
        assertEquals("***", deleted.getOldValue());
        assertNull(deleted.getNewValue());
    }

    @Test
    void leavesNonSensitiveNewlyAddedHeaderInClearText() {
        TestSensitiveDiffRoot oldConfig = root("u", null, headers("X-Common", "common", false));
        TestSensitiveDiffRoot newConfig = root("u", null, new ArrayList<>(List.of(
                new TestSensitiveHeader("X-Common", "common", false),
                new TestSensitiveHeader("X-Extra", "plain", false))));

        DiffEntry added = entryEndingWith(SensitiveDiffUtil.maskedDiff(DIFF, oldConfig, newConfig),
                "headers" + D + "X-Extra" + D + DiffUtils.NEW_MARKER);
        assertNull(added.getOldValue());
        TestSensitiveHeader addedHeader = (TestSensitiveHeader) added.getNewValue();
        assertEquals("plain", addedHeader.getValue());
    }

    @Test
    void returnsEmptyWhenNoFieldsChanged() {
        TestSensitiveDiffRoot config = root("http://same", "secret", headers("Authorization", "token", true));
        assertTrue(SensitiveDiffUtil.maskedDiff(DIFF, config, copyOf(config)).isEmpty());
    }

    @Test
    void returnsEmptyForNullOrDifferentTypes() {
        TestSensitiveDiffRoot root = root("u", null, null);
        assertTrue(SensitiveDiffUtil.maskedDiff(DIFF, null, root).isEmpty());
        assertTrue(SensitiveDiffUtil.maskedDiff(DIFF, root, null).isEmpty());
        assertTrue(SensitiveDiffUtil.maskedDiff(DIFF, new TestSensitiveHeader(), root).isEmpty());
    }

    @Test
    @SuppressWarnings({"java:S3011", "unchecked"})
    void masksEntireParentPathWhenDynamicFieldReflectionFails() throws Exception {
        // Build a descriptor whose field is intentionally NOT made accessible — field.get() will throw
        // IllegalAccessException, which readFieldValue must re-throw as IllegalStateException, and
        // addDynamicSensitivePaths must catch to add the parent path (fail-closed contract).
        TestSensitiveDiffRoot root = root("u", null, headers("Authorization", "secret", true));
        Field headersField = TestSensitiveDiffRoot.class.getDeclaredField("headers");
        // Do NOT call setAccessible(true) — leave it private so field.get() fails.
        org.artifactory.sensitive.DynamicSensitive annotation =
                headersField.getAnnotation(org.artifactory.sensitive.DynamicSensitive.class);
        DynamicSensitiveObjectDescriptor descriptor =
                new DynamicSensitiveObjectDescriptor(root, headersField, annotation, "headers");

        Method method = SensitiveDiffUtil.class.getDeclaredMethod(
                "addDynamicSensitivePaths", Class.class, DynamicSensitiveObjectDescriptor.class, Set.class);
        method.setAccessible(true);
        Set<String> paths = new HashSet<>();
        method.invoke(null, root.getClass(), descriptor, paths);

        assertTrue(paths.contains("headers"),
                "Fail-closed: parent 'headers' path must be masked when reflection fails");
    }

    @Test
    @SuppressWarnings({"java:S3011", "unchecked"})
    void failClosedUsesTranslatedPathWhenReflectionFails() throws Exception {
        // Arrange: a root whose @DynamicSensitive field has a @DiffElement alias.
        // The descriptor path is the Java field name ("headers"); toGeneratedDiffPath must translate it to
        // the alias ("renamedHeaders"). The catch block must then add the translated path — not the raw name.
        WithDiffElementHeaders root = new WithDiffElementHeaders();
        Field headersField = WithDiffElementHeaders.class.getDeclaredField("headers");
        // Do NOT call setAccessible(true) — field.get() must throw so the catch runs.
        org.artifactory.sensitive.DynamicSensitive annotation =
                headersField.getAnnotation(org.artifactory.sensitive.DynamicSensitive.class);
        DynamicSensitiveObjectDescriptor descriptor =
                new DynamicSensitiveObjectDescriptor(root, headersField, annotation, "headers");

        Method method = SensitiveDiffUtil.class.getDeclaredMethod(
                "addDynamicSensitivePaths", Class.class, DynamicSensitiveObjectDescriptor.class, Set.class);
        method.setAccessible(true);
        Set<String> paths = new HashSet<>();
        method.invoke(null, WithDiffElementHeaders.class, descriptor, paths);

        assertTrue(paths.contains("renamedHeaders"),
                "Fail-closed catch must add the @DiffElement-translated path, not the Java field name");
    }

    @Test
    @SuppressWarnings("java:S3011")
    void findFieldInHierarchyThrowsForUnknownSegment() throws Exception {
        Method method = SensitiveDiffUtil.class.getDeclaredMethod("findFieldInHierarchy", Class.class, String.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException ex = org.junit.jupiter.api.Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(null, TestSensitiveDiffRoot.class, "nonExistentField"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @SuppressWarnings("java:S3011")
    void findFieldInHierarchyResolvesInheritedFieldAfterSubclassMiss() throws Exception {
        Method method = SensitiveDiffUtil.class.getDeclaredMethod("findFieldInHierarchy", Class.class, String.class);
        method.setAccessible(true);

        Field field = (Field) method.invoke(null, InheritedSensitiveChild.class, "inheritedSecret");

        assertEquals(InheritedSensitiveBase.class, field.getDeclaringClass());
    }

    private static TestSensitiveDiffRoot root(String url, String password, List<TestSensitiveHeader> headers) {
        TestSensitiveDiffRoot root = new TestSensitiveDiffRoot();
        root.setUrl(url);
        root.setPassword(password);
        root.setHeaders(headers);
        return root;
    }

    private static TestSensitiveDiffRoot copyOf(TestSensitiveDiffRoot config) {
        List<TestSensitiveHeader> headers = config.getHeaders() == null ? null : config.getHeaders().stream()
                .map(header -> new TestSensitiveHeader(header.getName(), header.getValue(), header.isSensitive()))
                .collect(Collectors.toCollection(ArrayList::new));
        return root(config.getUrl(), config.getPassword(), headers);
    }

    private static List<TestSensitiveHeader> headers(String name, String value, boolean sensitive) {
        return new ArrayList<>(List.of(new TestSensitiveHeader(name, value, sensitive)));
    }

    private static DiffEntry entry(List<DiffEntry> diffs, String attributeName) {
        return diffs.stream()
                .filter(diff -> attributeName.equals(diff.getAttributeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No diff entry for '" + attributeName + "' in " + diffs.stream()
                        .map(DiffEntry::getAttributeName).toList()));
    }

    private static DiffEntry entryEndingWith(List<DiffEntry> diffs, String attributeNameSuffix) {
        return diffs.stream()
                .filter(diff -> diff.getAttributeName().endsWith(attributeNameSuffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No diff entry ending with '" + attributeNameSuffix + "' in "
                        + diffs.stream().map(DiffEntry::getAttributeName).toList()));
    }

    @SuppressWarnings("unchecked")
    private static void bindArtifactoryContextWithHeaderHandler() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == TestSensitiveHeaderListHandler.class) {
                return new TestSensitiveHeaderListHandler();
            }
            throw new IllegalArgumentException("Unexpected handler type for test context: " + type.getName());
        });
        ArtifactoryContextThreadBinder.bind(context);
    }

    private static class WithDiffElementHeaders {
        @DiffElement(name = "renamedHeaders")
        @DynamicSensitive(handler = TestSensitiveHeaderListHandler.class)
        @SuppressWarnings("unused")
        private List<TestSensitiveHeader> headers;
    }

    private static class InheritedSensitiveBase {
        @SuppressWarnings("unused")
        private String inheritedSecret;
    }

    private static class InheritedSensitiveChild extends InheritedSensitiveBase {
    }

}
