package org.artifactory.rest.common;

import org.artifactory.common.ConstantValues;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Igor Usenko
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisabledApi {
    ConstantValues disabledWhen();
    String responseMessage() default "This API has been disabled.";
}