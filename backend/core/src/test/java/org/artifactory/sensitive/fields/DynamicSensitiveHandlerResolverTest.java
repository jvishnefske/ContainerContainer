package org.artifactory.sensitive.fields;

import org.artifactory.api.config.DynamicSensitiveValueHandler;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DynamicSensitiveHandlerResolver}: the Spring-bean lookup happy path and every validation/error
 * branch (non-handler class, missing context, unregistered bean).
 */
class DynamicSensitiveHandlerResolverTest {

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void resolvesRegisteredHandlerBean() {
        CustomHttpHeadersSensitiveHandler bean = new CustomHttpHeadersSensitiveHandler();
        bindContextReturning(bean);

        DynamicSensitiveValueHandler<Object> resolved =
                DynamicSensitiveHandlerResolver.resolve(CustomHttpHeadersSensitiveHandler.class);

        assertSame(bean, resolved);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void throwsWhenHandlerClassDoesNotImplementInterface() {
        Class handlerClass = String.class;
        assertThrows(IllegalArgumentException.class,
                () -> DynamicSensitiveHandlerResolver.resolve(handlerClass));
    }

    @Test
    void throwsWhenNoArtifactoryContextBound() {
        ArtifactoryContextThreadBinder.unbind();
        assertThrows(IllegalStateException.class,
                () -> DynamicSensitiveHandlerResolver.resolve(CustomHttpHeadersSensitiveHandler.class));
    }

    @Test
    void throwsWhenHandlerNotRegisteredAsBean() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenThrow(new RuntimeException("no such bean"));
        ArtifactoryContextThreadBinder.bind(context);

        assertThrows(IllegalStateException.class,
                () -> DynamicSensitiveHandlerResolver.resolve(CustomHttpHeadersSensitiveHandler.class));
    }

    @SuppressWarnings("unchecked")
    private static void bindContextReturning(Object bean) {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenReturn(bean);
        ArtifactoryContextThreadBinder.bind(context);
    }
}
