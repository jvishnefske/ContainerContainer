package org.artifactory.api.crypto;

import jakarta.annotation.Nullable;
import org.jfrog.security.crypto.EncryptionWrapper;

import java.util.Collection;
import java.util.List;

/**
 * Decrypts, masks, and re-encrypts sensitive values on object graphs.
 * Collection methods return copies; they do not mutate the input.
 * <p>
 * Implemented by {@code org.artifactory.sensitive.fields.CryptoServiceImpl} together with per-field handlers.
 *
 * @author Fawaz Atwa
 */
public interface CryptoService {

    /**
     * Decrypts a single stored value when encrypted with the Artifactory key (e.g. sensitive custom HTTP header
     * values on outgoing requests). Blank or null values are returned as-is.
     */
    @Nullable
    String decryptStoredValue(@Nullable String storedValue);

    /**
     * Encrypts sensitive fields on each non-null object (one discovery pass per object).
     * Validation runs as part of this path before any encrypt.
     *
     * @param objects the object roots to process; must not be null
     */
    void encryptSensitiveFields(Collection<?> objects);

    /**
     * Discovers sensitive fields, validates them, then encrypts (single discovery pass).
     *
     * @param root the object root to process
     */
    void encryptSensitiveFields(Object root);

    /**
     * Returns copies with all sensitive fields decrypted.
     * For internal use only; callers must enforce access control.
     *
     * @param objects the objects to transform; may be null or empty (null treated as empty)
     * @param <T>     concrete object type supported for defensive copy (see implementation)
     * @return new list with sensitive values in clear text; null entries in {@code objects} are preserved as null
     */
    <T> List<T> decrypt(Collection<T> objects);

    /**
     * Returns copies with sensitive fields masked (e.g. for API responses or logging).
     *
     * @param objects the objects to transform; may be null or empty (null treated as empty)
     * @param <T>     concrete object type supported for defensive copy (see implementation)
     * @return new list with sensitive values masked; null entries in {@code objects} are preserved as null
     */
    <T> List<T> mask(Collection<T> objects);

    /**
     * Returns copies with sensitive values decrypted using the current Artifactory key, then re-encrypted using the
     * given wrapper (e.g. after key rotation before a new {@code artifactory.key} is active).
     *
     * @param objects            the objects to re-encrypt; may be null or empty (null treated as empty)
     * @param encryptionWrapper  wrapper for the new key material; must not be null
     * @param <T>                concrete object type supported for defensive copy (see implementation)
     * @return new list with values re-encrypted
     */
    <T> List<T> reEncrypt(Collection<T> objects, EncryptionWrapper encryptionWrapper);
}
