package org.artifactory.sensitive.fields;

import lombok.experimental.UtilityClass;
import org.artifactory.api.config.DynamicSensitiveValueHandler;
import org.artifactory.api.context.ContextHelper;

/**
 * Resolves the {@link DynamicSensitiveValueHandler} Spring bean for a {@code @DynamicSensitive}-annotated field
 * class. Performs the Spring-context lookup with validation so callers do not duplicate the error handling.
 *
 * @author Fawaz Atwa
 */
@UtilityClass
class DynamicSensitiveHandlerResolver {

    @SuppressWarnings({"unchecked", "rawtypes"})
    static DynamicSensitiveValueHandler<Object> resolve(
            Class<? extends DynamicSensitiveValueHandler<?>> handlerClass) {
        if (!DynamicSensitiveValueHandler.class.isAssignableFrom(handlerClass)) {
            throw new IllegalArgumentException("Handler class must implement DynamicSensitiveValueHandler: "
                    + handlerClass.getName());
        }
        var context = ContextHelper.get();
        if (context == null) {
            throw new IllegalStateException(
                    "No ArtifactoryContext; @DynamicSensitive handler must be a Spring bean: "
                            + handlerClass.getName());
        }
        try {
            return (DynamicSensitiveValueHandler<Object>) context.beanForType((Class) handlerClass);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "@DynamicSensitive handler must be registered as a Spring bean: " + handlerClass.getName(), ex);
        }
    }
}
