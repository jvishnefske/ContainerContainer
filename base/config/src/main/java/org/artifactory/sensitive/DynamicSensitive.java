package org.artifactory.sensitive;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.artifactory.api.config.DynamicSensitiveValueHandler;

/**
 * Marks an object field in a repository configuration as sensitive.
 * The developer must provide a handler class that implements
 * {@code org.artifactory.api.config.DynamicSensitiveValueHandler} to define validation,
 * encryption, decryption, and masking for this field.
 * <p>
 * Only use on object/reference types. Field must not be String, primitive types, or primitive
 * wrapper types (e.g. int, Integer, long, Long); for String fields use {@link Sensitive}.
 *
 * @author Fawaz Atwa
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamicSensitive {

    /**
     * The handler class that implements {@link org.artifactory.api.config.DynamicSensitiveValueHandler}
     * for this field type.
     * The handler must be registered as a Spring bean; the framework resolves it from the application context
     * and delegates validate/encrypt/decrypt/mask to it.
     */
    Class<? extends DynamicSensitiveValueHandler<?>> handler();
}
