package org.artifactory.sensitive.fields;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;
import org.jfrog.common.config.diff.DiffUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor;
import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.SensitiveStringDescriptor;

/**
 * Reflectively walks an object graph up to a maximum depth and collects {@linkplain SensitiveFieldDescriptor
 * descriptors} for non-static instance fields annotated with {@link Sensitive} (String fields) or
 * {@link DynamicSensitive} (reference types). This is the discovery step of the sensitive-field infrastructure;
 * {@linkplain CryptoServiceImpl} then delegates encrypt (including validation beforehand), decrypt, mask, and
 * key-rotation to {@linkplain SensitiveFieldHandler} implementations such as {@link SensitiveStringFieldHandler} and
 * {@link DynamicSensitiveFieldHandler}.
 * <p>
 * Recursion follows nested reference objects on the graph but does not enter {@code java.*} types, collections, maps,
 * primitives, enums, numeric/wrapper-only leaves, or into the values of {@code @DynamicSensitive} fields themselves.
 *
 * @see CryptoServiceImpl
 * @see SensitiveFieldHandler
 * @see SensitiveStringFieldHandler
 * @see DynamicSensitiveFieldHandler
 */
@Slf4j
@UtilityClass
class SensitiveFieldCollector {

    /**
     * Collects sensitive field descriptors from {@code root}, building diff attribute paths with
     * {@link DiffUtils#DELIMITER}. Recursion depth is bounded by
     * {@link ConstantValues#sensitiveRepoConfigMaxRecursionDepth}.
     */
    static List<SensitiveFieldDescriptor> collect(@Nullable Object root) {
        return collect(root, DiffUtils.DELIMITER);
    }

    /**
     * Collects sensitive field descriptors from {@code root}, building diff attribute paths with the given
     * {@code pathDelimiter}. Recursion depth is bounded by
     * {@link ConstantValues#sensitiveRepoConfigMaxRecursionDepth}.
     */
    static List<SensitiveFieldDescriptor> collect(@Nullable Object root, String pathDelimiter) {
        List<SensitiveFieldDescriptor> out = new ArrayList<>();
        int maxDepth = ConstantValues.sensitiveRepoConfigMaxRecursionDepth.getInt();
        traverse(root, 0, maxDepth, "", pathDelimiter, new IdentityHashMap<>(), out);
        return out;
    }

    /**
     * Same as {@link #collect(Object, String)} but with an explicit {@code maxDepth}. Exposed only for tests that
     * need to exercise depth-boundary behaviour without binding a full Artifactory home.
     */
    @VisibleForTesting
    static List<SensitiveFieldDescriptor> collect(@Nullable Object root, int maxDepth, String pathDelimiter) {
        List<SensitiveFieldDescriptor> out = new ArrayList<>();
        traverse(root, 0, maxDepth, "", pathDelimiter, new IdentityHashMap<>(), out);
        return out;
    }

    private static void traverse(@Nullable Object obj, int depth, int maxDepth, String pathPrefix,
            String pathDelimiter, Map<Object, Object> visited, List<SensitiveFieldDescriptor> out) {
        if (obj == null || depth > maxDepth) {
            return;
        }
        if (visited.containsKey(obj)) {
            return;
        }
        Class<?> clazz = obj.getClass();
        if (clazz.getName().startsWith("java.")) {
            return;
        }
        visited.put(obj, obj);
        Class<?> current = clazz;
        while (current != null && !current.getName().startsWith("java.")) {
            collectFromDeclaredClass(obj, current, depth, maxDepth, pathPrefix, pathDelimiter, visited, out);
            current = current.getSuperclass();
        }
    }

    private static void collectFromDeclaredClass(Object obj, Class<?> current, int depth, int maxDepth,
            String pathPrefix, String pathDelimiter, Map<Object, Object> visited,
            List<SensitiveFieldDescriptor> out) {
        List<Field> instanceFields = Arrays.stream(current.getDeclaredFields())
                .filter(SensitiveFieldCollector::isNonStatic)
                .toList();

        instanceFields.stream()
                .filter(SensitiveFieldCollector::hasSensitiveAnnotation)
                .forEach(field -> addSensitiveStringDescriptor(obj, current, field,
                        appendPath(pathPrefix, field.getName(), pathDelimiter), out));

        instanceFields.stream()
                .filter(Predicate.not(SensitiveFieldCollector::hasSensitiveAnnotation))
                .filter(SensitiveFieldCollector::hasDynamicSensitiveAnnotation)
                .forEach(field -> addDynamicSensitiveDescriptor(obj, field,
                        appendPath(pathPrefix, field.getName(), pathDelimiter), out));

        instanceFields.stream()
                .filter(Predicate.not(SensitiveFieldCollector::hasSensitiveAnnotation))
                .filter(Predicate.not(SensitiveFieldCollector::hasDynamicSensitiveAnnotation))
                .filter(SensitiveFieldCollector::isNestedReferenceCandidate)
                .forEach(field -> recurseIntoField(obj, field,
                        appendPath(pathPrefix, field.getName(), pathDelimiter), depth, maxDepth, pathDelimiter,
                        visited, out));
    }

    private static String appendPath(String pathPrefix, String fieldName, String pathDelimiter) {
        return pathPrefix.isEmpty() ? fieldName : pathPrefix + pathDelimiter + fieldName;
    }

    private static boolean isNonStatic(Field field) {
        return !Modifier.isStatic(field.getModifiers());
    }

    private static boolean hasSensitiveAnnotation(Field field) {
        return field.isAnnotationPresent(Sensitive.class);
    }

    private static boolean hasDynamicSensitiveAnnotation(Field field) {
        return field.isAnnotationPresent(DynamicSensitive.class);
    }

    private static void addSensitiveStringDescriptor(Object obj, Class<?> declaringClass, Field field, String path,
            List<SensitiveFieldDescriptor> out) {
        if (field.getType() != String.class) {
            throw new IllegalArgumentException(
                    "@Sensitive must be used only on String fields; found "
                            + field.getType().getSimpleName() + " on " + declaringClass.getSimpleName()
                            + "." + field.getName());
        }
        field.setAccessible(true);
        out.add(new SensitiveStringDescriptor(obj, field, field.getAnnotation(Sensitive.class), path));
    }

    private static void addDynamicSensitiveDescriptor(Object obj, Field field, String path,
            List<SensitiveFieldDescriptor> out) {
        field.setAccessible(true);
        out.add(new DynamicSensitiveObjectDescriptor(obj, field, field.getAnnotation(DynamicSensitive.class), path));
    }

    private static void recurseIntoField(Object obj, Field field, String path, int depth, int maxDepth,
            String pathDelimiter, Map<Object, Object> visited, List<SensitiveFieldDescriptor> out) {
        try {
            field.setAccessible(true);
            Object child = field.get(obj);
            traverse(child, depth + 1, maxDepth, path, pathDelimiter, visited, out);
        } catch (ReflectiveOperationException ex) {
            log.trace("Could not read field {} for sensitive scan: {}", field.getName(), ex.getMessage());
        }
    }

    private static boolean isNestedReferenceCandidate(Field field) {
        Class<?> declaredType = field.getType();
        return !declaredType.isPrimitive() && declaredType != String.class && !declaredType.isEnum()
                && !Collection.class.isAssignableFrom(declaredType) && !Map.class.isAssignableFrom(declaredType)
                && !Number.class.isAssignableFrom(declaredType) && declaredType != Boolean.class;
    }
}
