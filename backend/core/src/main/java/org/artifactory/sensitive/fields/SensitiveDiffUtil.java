package org.artifactory.sensitive.fields;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.Diff;
import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.api.config.DynamicSensitiveValueHandler;
import org.artifactory.util.DiffEntry;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffFunctions;
import org.jfrog.common.config.diff.DiffUtils;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor;
import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.SensitiveStringDescriptor;

/**
 * Annotation-driven sensitive diff: wraps {@link DiffFunctions#diffFor} and replaces values of
 * {@code @Sensitive} / {@code @DynamicSensitive} fields with {@code ***}. Un-annotated fields pass through in clear
 * text. Old and new graphs are scanned independently, so per-side sensitivity toggles are respected. The "changed"
 * signal is always preserved — only the value is masked, not the entry.
 * <p>
 * <strong>Sensitive-collection rule:</strong> if a collection's individual element values are secret, also annotate
 * it {@code @DiffAtomic}. Without it the diff engine uses the element value as the path key, leaking the secret in
 * the path itself regardless of masking.
 * <p>
 * Sensitive field paths are discovered by Java field name and translated through {@link DiffElement#name()} overrides
 * before matching against generated diff paths. Discovery is reflective on every call; a lazy cache is a planned
 * follow-up.
 *
 * @author Revital Abuhatzira
 */
@Slf4j
@UtilityClass
public class SensitiveDiffUtil {

    private static final String MASK = "***";
    /** Single delimiter used both to build sensitive paths and to query handlers, so the two always align. */
    private static final String PATH_DELIMITER = DiffUtils.DELIMITER;

    /**
     * Field-level diff between two objects of the same concrete type, with sensitive leaves masked and entries sorted
     * by attribute name.
     *
     * @param diffFunctions the generated {@link DiffFunctions} registry covering {@code oldObject}/{@code newObject}'s
     *                      concrete type (e.g. the {@code @GenerateDiffFunction}-generated {@code DiffFunctionsImpl}
     *                      for the relevant object graph); supplied by the caller so this utility stays decoupled from
     *                      any specific model
     * @return masked, sorted diffs; empty when inputs are null or of different concrete types
     */
    public static <T> List<DiffEntry> maskedDiff(DiffFunctions diffFunctions, T oldObject, T newObject) {
        if (oldObject == null || newObject == null || oldObject.getClass() != newObject.getClass()) {
            return List.of();
        }
        DiffResult<?> diffResult = diffFor(diffFunctions, oldObject, newObject);
        if (diffResult.getDiffs().isEmpty()) {
            return List.of();
        }
        Set<String> oldSensitivePaths = sensitivePaths(oldObject);
        Set<String> newSensitivePaths = sensitivePaths(newObject);
        return diffResult.getDiffs().stream()
                .map(diff -> toMaskedEntry(diff, oldSensitivePaths, newSensitivePaths))
                .sorted(Comparator.comparing(DiffEntry::getAttributeName))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> DiffResult<?> diffFor(DiffFunctions diffFunctions, T oldObject, T newObject) {
        Class<T> type = (Class<T>) oldObject.getClass();
        DiffResult<?> result = diffFunctions.diffFor(type, oldObject, newObject);
        if (result == null) {
            throw new IllegalArgumentException("No diff function registered for type " + type.getName()
                    + " — ensure the correct @GenerateDiffFunction DiffFunctionsImpl is supplied.");
        }
        return result;
    }

    private static DiffEntry toMaskedEntry(Diff<?> diff, Set<String> oldSensitivePaths,
            Set<String> newSensitivePaths) {
        String path = diff.getFieldName();
        return new DiffEntry(path,
                getMaskedValueIfSensitive(path, diff.getLeft(), oldSensitivePaths),
                getMaskedValueIfSensitive(path, diff.getRight(), newSensitivePaths));
    }

    private static Object getMaskedValueIfSensitive(String path, Object rawValue,
            Set<String> sensitivePaths) {
        if (rawValue == null) {
            return null;
        }
        // Exact match covers normal leaf-level sensitive paths.
        // Prefix match covers fail-closed parent paths added when the handler cannot be invoked
        // (e.g. "headers" masking every "headers»...» value" descendant).
        if (sensitivePaths.contains(path)
                || sensitivePaths.stream().anyMatch(p -> path.startsWith(p + PATH_DELIMITER))) {
            return MASK;
        }
        // Added/removed complex element: with one side null the engine can't emit per-field leaves, so it emits a
        // single "...»<key>»$$new" / "...»<key>»$$deleted" entry whose value is the WHOLE @GenerateDiffFunction
        // object. E.g. adding a header yields "customHttpHeaders»x-api-key»$$new" = CustomHttpHeader{name,value,
        // sensitive} (vs the per-field "customHttpHeaders»x-api-key»value" leaf produced on a plain change). The
        // matches above only see field leaves, so mask the whole value iff any descendant is sensitive (e.g. the
        // added header has sensitive=true); otherwise the raw object passes through. Scalar maps/collections
        // (e.g. Map<String,String>) never use these markers.
        if (path.endsWith(PATH_DELIMITER + DiffUtils.NEW_MARKER)
                || path.endsWith(PATH_DELIMITER + DiffUtils.DELETED_MARKER)) {
            return maskAddedOrRemovedEntry(path, rawValue, sensitivePaths);
        }
        return rawValue;
    }

    private static Object maskAddedOrRemovedEntry(String path, Object rawValue,
            Set<String> sensitivePaths) {
        String entryPrefix = path.substring(0, path.lastIndexOf(PATH_DELIMITER)) + PATH_DELIMITER;
        boolean hasSensitiveDescendant = sensitivePaths.stream()
                .anyMatch(key -> key.startsWith(entryPrefix));
        if (!hasSensitiveDescendant) {
            return rawValue;
        }
        // The entry carries a secret as part of a larger object, so mask the whole value.
        log.debug("Fully masking added/removed {} because it contains a sensitive descendant",
                rawValue.getClass().getSimpleName());
        return MASK;
    }

    /**
     * Sensitive leaf paths for a single object graph.
     */
    private static Set<String> sensitivePaths(Object root) {
        Set<String> paths = new HashSet<>();
        for (SensitiveFieldDescriptor descriptor : SensitiveFieldCollector.collect(root, PATH_DELIMITER)) {
            switch (descriptor) {
                case SensitiveStringDescriptor stringField ->
                        paths.add(toGeneratedDiffPath(root.getClass(), stringField.getPath()));
                case DynamicSensitiveObjectDescriptor dynamicField ->
                        addDynamicSensitivePaths(root.getClass(), dynamicField, paths);
            }
        }
        return paths;
    }

    private static void addDynamicSensitivePaths(Class<?> rootClass, DynamicSensitiveObjectDescriptor dynamicField,
            Set<String> paths) {
        String dynamicFieldPath = toGeneratedDiffPath(rootClass, dynamicField.getPath());
        try {
            Object value = readFieldValue(dynamicField);
            if (value == null) {
                return;
            }
            DynamicSensitiveValueHandler<Object> handler =
                    DynamicSensitiveHandlerResolver.resolve(dynamicField.annotation().handler());
            for (String relativeLeafPath : handler.sensitiveLeafPaths(value, PATH_DELIMITER)) {
                paths.add(dynamicFieldPath + PATH_DELIMITER + relativeLeafPath);
            }
        } catch (RuntimeException ex) {
            // Fail closed: mask the entire parent field on any failure (reflection error, unbound handler,
            // or buggy sensitiveLeafPaths implementation) rather than risk leaking its contents.
            log.warn("Could not process @DynamicSensitive field {} for diff masking — masking the entire "
                    + "field to fail closed", dynamicField.getField(), ex);
            paths.add(dynamicFieldPath);
        }
    }

    // SensitiveFieldCollector reports Java field paths; generated diffs may use @DiffElement names.
    // Convert each path segment before comparing with Diff#getFieldName().
    private static String toGeneratedDiffPath(Class<?> rootClass, String collectorPath) {
        String[] segments = collectorPath.split(PATH_DELIMITER);
        StringBuilder diffPath = new StringBuilder();
        Class<?> currentClass = rootClass;
        for (String segment : segments) {
            Field field = findFieldInHierarchy(currentClass, segment);
            String diffSegment = generatedDiffFieldName(field);
            if (!diffPath.isEmpty()) {
                diffPath.append(PATH_DELIMITER);
            }
            diffPath.append(diffSegment);
            currentClass = field.getType();
        }
        return diffPath.toString();
    }

    private static Field findFieldInHierarchy(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && !current.getName().startsWith("java.")) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("Could not resolve sensitive diff path segment '" + fieldName
                + "' on " + type.getName());
    }

    private static String generatedDiffFieldName(Field field) {
        DiffElement diffElement = field.getAnnotation(DiffElement.class);
        if (diffElement == null || diffElement.name().isBlank()) {
            return field.getName();
        }
        return diffElement.name();
    }

    private static Object readFieldValue(SensitiveFieldDescriptor descriptor) {
        Field field = descriptor.getField();
        try {
            return field.get(descriptor.getParent());
        } catch (ReflectiveOperationException ex) {
            // Should not happen: SensitiveFieldCollector makes fields accessible during discovery.
            // Propagate as unchecked so the caller can fail closed.
            throw new IllegalStateException("Could not read @DynamicSensitive field " + field.getName(), ex);
        }
    }
}
