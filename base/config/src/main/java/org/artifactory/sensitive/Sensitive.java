package org.artifactory.sensitive;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field in a repository configuration as sensitive.
 * The framework automatically handles validation, encryption before persistence,
 * masking in API responses, and decryption when required internally.
 * <p>
 * Only use on fields of type {@link String}.
 *
 * @author Fawaz Atwa
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * Optional custom mask string when the value is masked (e.g. in GET responses).
     * Default is "***".
     */
    String maskValue() default "***";
}
