package org.artifactory.spring;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking configuration bean method to be loaded only if no other bean type of the specified class
 * See {@link MissingBeanCondition}
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(MissingBeanCondition.class)
public @interface ConditionalOnMissingBean {
    Class<?> value();
}
