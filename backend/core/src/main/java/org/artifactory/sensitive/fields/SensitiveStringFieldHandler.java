package org.artifactory.sensitive.fields;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.PasswordDecryptionFailureException;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.sensitive.Sensitive;
import org.artifactory.util.StopWatchUtil;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.SensitiveStringDescriptor;

/**
 * Handles {@link Sensitive} String fields on a config graph: validate, encrypt, decrypt, mask.
 *
 * @author Fawaz Atwa
 */
@Slf4j
@Component
public class SensitiveStringFieldHandler implements SensitiveFieldHandler {

    private ArtifactoryHome artifactoryHome;

    @PostConstruct
    public void init() {
        artifactoryHome = ArtifactoryHome.get();
    }

    @Override
    public boolean supports(SensitiveFieldDescriptor descriptor) {
        return descriptor instanceof SensitiveStringDescriptor;
    }

    @Override
    public void validate(SensitiveFieldDescriptor descriptor) {
        SensitiveStringDescriptor stringDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@Sensitive validate took: {}ms",
                () -> validateInternal(stringDescriptor));
    }

    void validateInternal(SensitiveStringDescriptor descriptor) {
        Field field = descriptor.getField();
        if (field.getType() != String.class) {
            throw new IllegalArgumentException(
                    "@Sensitive must be used only on String fields; found " + field.getType() + " on " + field);
        }
    }

    @Override
    public void encrypt(SensitiveFieldDescriptor descriptor) {
        SensitiveStringDescriptor stringDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@Sensitive encrypt took: {}ms",
                () -> encryptInternal(stringDescriptor));
    }

    void encryptInternal(SensitiveStringDescriptor descriptor) {
        Field field = descriptor.getField();
        try {
            ensureAccessible(field);
            String value = (String) field.get(descriptor.getParent());
            if (StringUtils.isBlank(value)) {
                return;
            }
            String encrypted = CryptoHelper.encryptIfNeeded(artifactoryHome, value);
            if (encrypted != null) {
                field.set(descriptor.getParent(), encrypted);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not encrypt sensitive field " + field, ex);
        }
    }

    @Override
    public void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, SensitiveFieldDescriptor descriptor) {
        SensitiveStringDescriptor stringDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@Sensitive encryptForKeyRotation took: {}ms",
                () -> encryptForKeyRotationInternal(newArtifactoryWrapper, stringDescriptor));
    }

    void encryptForKeyRotationInternal(EncryptionWrapper newArtifactoryWrapper, SensitiveStringDescriptor descriptor) {
        Field field = descriptor.getField();
        try {
            ensureAccessible(field);
            String value = (String) field.get(descriptor.getParent());
            if (StringUtils.isBlank(value)) {
                return;
            }
            String encrypted = CryptoHelper.encryptIfNeeded(artifactoryHome, newArtifactoryWrapper, value);
            if (encrypted != null) {
                field.set(descriptor.getParent(), encrypted);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not re-encrypt sensitive field " + field, ex);
        }
    }

    @Override
    public void decrypt(SensitiveFieldDescriptor descriptor) {
        SensitiveStringDescriptor stringDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@Sensitive decrypt took: {}ms",
                () -> decryptInternal(stringDescriptor));
    }

    void decryptInternal(SensitiveStringDescriptor descriptor) {
        Field field = descriptor.getField();
        try {
            ensureAccessible(field);
            String value = (String) field.get(descriptor.getParent());
            if (StringUtils.isBlank(value)) {
                return;
            }
            String decrypted = CryptoHelper.decryptIfNeeded(artifactoryHome, value);
            if (decrypted != null) {
                field.set(descriptor.getParent(), decrypted);
            }
        } catch (ReflectiveOperationException ex) {
            throw new PasswordDecryptionFailureException("Could not decrypt sensitive field: " + field, ex);
        }
    }

    @Override
    public void mask(SensitiveFieldDescriptor descriptor) {
        SensitiveStringDescriptor stringDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@Sensitive mask took: {}ms",
                () -> maskInternal(stringDescriptor));
    }

    void maskInternal(SensitiveStringDescriptor descriptor) {
        Field field = descriptor.getField();
        try {
            ensureAccessible(field);
            String value = (String) field.get(descriptor.getParent());
            if (StringUtils.isBlank(value)) {
                return;
            }
            String mask = descriptor.annotation().maskValue();
            field.set(descriptor.getParent(), mask);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not mask sensitive field " + field, ex);
        }
    }

    private static void ensureAccessible(Field field) {
        field.setAccessible(true);
    }

    private static SensitiveStringDescriptor castDescriptor(SensitiveFieldDescriptor descriptor) {
        if (descriptor instanceof SensitiveStringDescriptor sensitiveStringDescriptor) {
            return sensitiveStringDescriptor;
        }
        throw new IllegalArgumentException(
                "SensitiveStringFieldHandler supports only SensitiveStringDescriptor, got "
                        + descriptor.getClass().getName());
    }
}
