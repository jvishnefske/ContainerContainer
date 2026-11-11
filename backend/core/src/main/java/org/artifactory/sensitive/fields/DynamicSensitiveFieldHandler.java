package org.artifactory.sensitive.fields;

import org.artifactory.api.config.DynamicSensitiveKeyRotationCapable;
import org.artifactory.api.config.DynamicSensitiveValueHandler;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.util.StopWatchUtil;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Objects;

import static org.artifactory.sensitive.fields.SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor;

/**
 * Handles {@link org.artifactory.sensitive.DynamicSensitive} object fields on a config graph.
 * For each field, resolves a {@link DynamicSensitiveValueHandler} from the Spring context and delegates
 * validate / encrypt / decrypt / mask to that bean.
 *
 * @author Fawaz Atwa
 */
@Slf4j
@Component
public class DynamicSensitiveFieldHandler implements SensitiveFieldHandler {

    @Override
    public boolean supports(SensitiveFieldDescriptor descriptor) {
        return descriptor instanceof DynamicSensitiveObjectDescriptor;
    }

    @Override
    public void validate(SensitiveFieldDescriptor descriptor) {
        DynamicSensitiveObjectDescriptor dynamicDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@DynamicSensitive validate took: {}ms",
                () -> validateInternal(dynamicDescriptor));
    }

    void validateInternal(DynamicSensitiveObjectDescriptor descriptor) {
        Field field = descriptor.getField();
        assertLegalDynamicSensitiveFieldType(field);
        try {
            ensureAccessible(field);
            Object value = field.get(descriptor.getParent());
            DynamicSensitiveValueHandler<Object> handler = resolveHandler(descriptor.annotation().handler());
            handler.validate(value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(
                    "Could not validate dynamic sensitive field " + descriptor.getField(), ex);
        }
    }

    @Override
    public void encrypt(SensitiveFieldDescriptor descriptor) {
        DynamicSensitiveObjectDescriptor dynamicDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@DynamicSensitive encrypt took: {}ms",
                () -> encryptInternal(dynamicDescriptor));
    }

    void encryptInternal(DynamicSensitiveObjectDescriptor descriptor) {
        try {
            Field field = descriptor.getField();
            ensureAccessible(field);
            Object value = field.get(descriptor.getParent());
            DynamicSensitiveValueHandler<Object> handler = resolveHandler(descriptor.annotation().handler());
            handler.encrypt(value);
        } catch (ReflectiveOperationException ex) {
            log.debug("Could not encrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to encrypt dynamic sensitive field " + descriptor.getField(), ex);
        } catch (RuntimeException ex) {
            log.debug("Handler failed to encrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to encrypt dynamic sensitive field " + descriptor.getField(), ex);
        }
    }

    @Override
    public void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, SensitiveFieldDescriptor descriptor) {
        DynamicSensitiveObjectDescriptor dynamicDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@DynamicSensitive encryptForKeyRotation took: {}ms",
                () -> encryptForKeyRotationInternal(newArtifactoryWrapper, dynamicDescriptor));
    }

    @SuppressWarnings("unchecked")
    void encryptForKeyRotationInternal(EncryptionWrapper newArtifactoryWrapper,
            DynamicSensitiveObjectDescriptor descriptor) {
        Objects.requireNonNull(newArtifactoryWrapper, "newArtifactoryWrapper");
        try {
            Field field = descriptor.getField();
            ensureAccessible(field);
            Object value = field.get(descriptor.getParent());
            DynamicSensitiveValueHandler<Object> handler = resolveHandler(descriptor.annotation().handler());
            if (handler instanceof DynamicSensitiveKeyRotationCapable<?>) {
                ((DynamicSensitiveKeyRotationCapable<Object>) handler)
                        .encryptForKeyRotation(newArtifactoryWrapper, value);
            } else {
                throw new IllegalStateException(
                        "Key rotation is not supported for @DynamicSensitive handler "
                                + handler.getClass().getName() + "; implement "
                                + DynamicSensitiveKeyRotationCapable.class.getName());
            }
        } catch (ReflectiveOperationException ex) {
            log.debug("Could not re-encrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to re-encrypt dynamic sensitive field " + descriptor.getField(), ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.debug("Handler failed to re-encrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to re-encrypt dynamic sensitive field " + descriptor.getField(), ex);
        }
    }

    @Override
    public void decrypt(SensitiveFieldDescriptor descriptor) {
        DynamicSensitiveObjectDescriptor dynamicDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@DynamicSensitive decrypt took: {}ms",
                () -> decryptInternal(dynamicDescriptor));
    }

    void decryptInternal(DynamicSensitiveObjectDescriptor descriptor) {
        try {
            Field field = descriptor.getField();
            ensureAccessible(field);
            Object value = field.get(descriptor.getParent());
            DynamicSensitiveValueHandler<Object> handler = resolveHandler(descriptor.annotation().handler());
            handler.decrypt(value);
        } catch (ReflectiveOperationException ex) {
            log.debug("Could not decrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to decrypt dynamic sensitive field " + descriptor.getField(), ex);
        } catch (RuntimeException ex) {
            log.debug("Handler failed to decrypt dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to decrypt dynamic sensitive field " + descriptor.getField(), ex);
        }
    }

    @Override
    public void mask(SensitiveFieldDescriptor descriptor) {
        DynamicSensitiveObjectDescriptor dynamicDescriptor = castDescriptor(descriptor);
        StopWatchUtil.measureAndLogTime(log, Level.DEBUG, "@DynamicSensitive mask took: {}ms",
                () -> maskInternal(dynamicDescriptor));
    }

    void maskInternal(DynamicSensitiveObjectDescriptor descriptor) {
        try {
            Field field = descriptor.getField();
            ensureAccessible(field);
            Object value = field.get(descriptor.getParent());
            DynamicSensitiveValueHandler<Object> handler = resolveHandler(descriptor.annotation().handler());
            Object masked = handler.mask(value);
            field.set(descriptor.getParent(), masked);
        } catch (ReflectiveOperationException ex) {
            log.debug("Could not mask dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to mask dynamic sensitive field " + descriptor.getField() + "; config may still contain "
                            + "unmasked data", ex);
        } catch (RuntimeException ex) {
            log.debug("Handler failed to mask dynamic sensitive field {}", descriptor.getField(), ex);
            throw new IllegalStateException(
                    "Failed to mask dynamic sensitive field " + descriptor.getField() + "; config may still contain "
                            + "unmasked data", ex);
        }
    }

    private DynamicSensitiveValueHandler<Object> resolveHandler(
            Class<? extends DynamicSensitiveValueHandler<?>> handlerClass) {
        return DynamicSensitiveHandlerResolver.resolve(handlerClass);
    }

    private static void assertLegalDynamicSensitiveFieldType(Field field) {
        Class<?> type = field.getType();
        if (type == String.class || type.isPrimitive() || isBoxedPrimitive(type)) {
            throw new IllegalArgumentException(
                    "@DynamicSensitive must not be used on String, primitive, or primitive wrapper types; "
                            + "use @Sensitive for String. Field: " + field.getDeclaringClass().getSimpleName() + "."
                            + field.getName());
        }
    }

    private static boolean isBoxedPrimitive(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Double.class || type == Float.class
                || type == Boolean.class || type == Byte.class || type == Short.class || type == Character.class;
    }

    private static void ensureAccessible(Field field) {
        field.setAccessible(true);
    }

    private static DynamicSensitiveObjectDescriptor castDescriptor(SensitiveFieldDescriptor descriptor) {
        if (descriptor instanceof DynamicSensitiveObjectDescriptor dynamicSensitiveObjectDescriptor) {
            return dynamicSensitiveObjectDescriptor;
        }
        throw new IllegalArgumentException(
                "DynamicSensitiveFieldHandler supports only DynamicSensitiveObjectDescriptor, got "
                        + descriptor.getClass().getName());
    }
}
