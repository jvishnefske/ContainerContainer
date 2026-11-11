package org.artifactory.api.config;

import jakarta.annotation.Nullable;
import org.jfrog.security.crypto.EncryptionWrapper;

/**
 * Optional extension for {@link DynamicSensitiveValueHandler} implementations that support Artifactory key rotation
 * on repository configuration (decrypt with current key, re-encrypt with pending key material).
 *
 * @param <T> the sensitive object type
 */
public interface DynamicSensitiveKeyRotationCapable<T> extends DynamicSensitiveValueHandler<T> {

    /**
     * Re-encrypts sensitive content with the pending Artifactory wrapper after the value was decrypted with the
     * current on-disk key. Implementations may mutate {@code value}.
     *
     * @param newArtifactoryWrapper wrapper built from pending key material for rotation
     * @param value the object to re-encrypt (may be null)
     */
    void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, @Nullable T value);
}
