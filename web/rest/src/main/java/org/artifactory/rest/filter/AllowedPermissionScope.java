package org.artifactory.rest.filter;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a resource as accessible via a scoped access token.
 * The scope can be specified using either a single {@code value} or a list of {@code scopes},
 * where each scope is a string in the format: {@code <resource-type>:<target>[/<sub-resource>]:<actions>}.<br/><br/>.
 * <p>
 * NOTE:
 * - You must specify either {@code value} or {@code scopes}, but not both.
 * - Using both parameters or omitting both will result in an InvalidArgumentException.
 * - If used together with <code>@RolesAllowed</code> annotation then a resource will be accessible
 * if the access token has a valid scope OR the role is allowed.
 * <p>
 * See <code>org.artifactory.rest.filter.AllowedPermissionScopeDynamicFeature</code> for details.
 *
 * @author Oleksandr Legenkiy
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface AllowedPermissionScope {

    /**
     * The allowed permission scopes
     */
    String value() default "";

    String[] scopes() default {};

}
