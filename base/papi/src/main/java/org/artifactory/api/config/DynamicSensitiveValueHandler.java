package org.artifactory.api.config;

import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * Handler for sensitive object fields marked with {@link org.artifactory.sensitive.DynamicSensitive}.
 * Developers implement this interface to define how a complex object is validated, encrypted,
 * decrypted, and masked.
 * <p>
 * The framework delegates to the handler instance resolved from the annotation's {@code handler}
 * attribute.
 *
 * @param <T> the type of the sensitive object
 * @author Fawaz Atwa
 */
public interface DynamicSensitiveValueHandler<T> {

    /**
     * Validates the object before persistence.
     * Implementations may throw if the value is invalid.
     *
     * @param value the object to validate (may be null)
     * @throws IllegalArgumentException or similar if validation fails
     */
    void validate(@Nullable T value);

    /**
     * The implementation may mutate the given object.
     *
     * @param value the object whose sensitive parts should be encrypted (may be null)
     */
    void encrypt(@Nullable T value);

    /**
     * Decrypts sensitive data within the object in place (or returns a decrypted representation).
     * Called when clear text is required (e.g. internal use, key rotation).
     * The implementation may mutate the given object.
     *
     * @param value the object whose sensitive parts should be decrypted (may be null)
     */
    void decrypt(@Nullable T value);

    /**
     * Produces a masked representation of the object for API responses or logging.
     * Should not mutate the original; typically returns a copy with sensitive values replaced.
     *
     * @param value the object to mask (may be null)
     * @return masked representation (e.g. copy with "***" for sensitive sub-fields)
     */
    @Nullable
    T mask(@Nullable T value);

    /**
     * Reports the leaf paths, relative to this field, that are sensitive for the given value. Nested segments are
     * joined with {@code pathDelimiter} so the caller can compose each result with this field's own path. Sensitivity
     * can depend on the value itself (e.g. a per-element flag), so it is evaluated against the concrete value rather
     * than declared statically. The default reports nothing.
     *
     * @param value         the object to inspect (may be null)
     * @param pathDelimiter the delimiter used to join nested path segments; supplied by the caller so the same
     *                      delimiter is used when paths are produced and when they are matched
     * @return relative sensitive leaf paths; empty when none
     */
    default Set<String> sensitiveLeafPaths(@Nullable T value, String pathDelimiter) {
        return Set.of();
    }
}
