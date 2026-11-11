package org.artifactory.itest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @AccessServer annotation is a custom annotation used to indicate whether a test class requires an Access server
 * setup. It has a mock property that determines if the Access server should be set up or if a mock should be
 * used instead. This helps in managing resources efficiently by avoiding unnecessary setup of the Access server when
 * it is not needed.
 *
 * @author Amir Avidan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AccessServer {
    /**
     *  A boolean value that determines if the Access server should be set up or if a mock should be used instead.
     */
    boolean mock();
}
