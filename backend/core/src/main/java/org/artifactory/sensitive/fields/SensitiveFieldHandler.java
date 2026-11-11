package org.artifactory.sensitive.fields;

import org.jfrog.security.crypto.EncryptionWrapper;

/**
 * Contract for handling a single discovered sensitive field (by annotation / descriptor type).
 * Implementations declare {@link #supports(SensitiveFieldDescriptor)} for dispatch.
 */
public interface SensitiveFieldHandler {

    boolean supports(SensitiveFieldDescriptor descriptor);

    void validate(SensitiveFieldDescriptor descriptor);

    void encrypt(SensitiveFieldDescriptor descriptor);

    void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, SensitiveFieldDescriptor descriptor);

    void decrypt(SensitiveFieldDescriptor descriptor);

    void mask(SensitiveFieldDescriptor descriptor);
}
