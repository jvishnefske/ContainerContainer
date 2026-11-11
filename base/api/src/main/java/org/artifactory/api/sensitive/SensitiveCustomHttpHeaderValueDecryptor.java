package org.artifactory.api.sensitive;

import jakarta.annotation.Nullable;

/**
 * Decrypts stored values for sensitive remote-repository custom HTTP headers.
 * Callers with access to {@link org.artifactory.api.crypto.CryptoService} should prefer
 * {@link org.artifactory.api.crypto.CryptoService#decryptStoredValue}; the runtime implementation is the same bean.
 */
public interface SensitiveCustomHttpHeaderValueDecryptor {

    /**
     * Decrypts {@code storedValue} with the Artifactory/master key material when it is encrypted; otherwise returns
     * it unchanged. Blank or null values are returned as-is.
     */
    String decryptStoredValue(@Nullable String storedValue);
}
