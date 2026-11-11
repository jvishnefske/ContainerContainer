package org.artifactory.common.config.metadata;

import com.fasterxml.jackson.annotation.JsonGetter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Alexei Vainshtein
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MetadataInfo {

    @JsonGetter("defaultValue")
    String defaultValue() default "";

    @JsonGetter("defaultValues")
    String[] defaultValues() default {};

    @JsonGetter(value = "values")
    String[] values() default {};

    boolean skip() default false;

}
