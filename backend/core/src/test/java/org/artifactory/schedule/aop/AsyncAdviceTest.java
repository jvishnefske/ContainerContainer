package org.artifactory.schedule.aop;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.async.Async;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.schedule.aop.AsyncAdvice.MethodCallbackSessionResource;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Nir
 */
class AsyncAdviceTest extends ArtifactoryHomeBoundTest {
    @Mock
    SecurityContext securityContext;
    @Mock
    SecurityService securityService;
    @Mock
    InternalArtifactoryContext context;
    @Mock
    Authentication authentication;
    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        SecurityContextHolder.setContext(securityContext);
        when(context.getSecurityService()).thenReturn(securityService);
    }

    @ParameterizedTest
    @MethodSource("provideAuthenticateAsSystemParams")
    void authenticateAsSystemTest(boolean shouldAuthenticateAsSystem, int times) {
        AsyncAdvice.MethodAnnotation<Async> methodAnnotation =
                new AsyncAdvice.MethodAnnotation<>(null, createAsyncAnnotation(shouldAuthenticateAsSystem));
        AsyncAdvice.authenticateAsSystem(methodAnnotation);
        verify(securityService, times(times)).authenticateAsSystem();
    }

    @Test
    void restoreAuthenticationTest() {
        AsyncAdvice.restoreAuthentication(authentication);
        verify(securityContext, times(1)).setAuthentication(authentication);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void methodCallbackSessionResource_afterConnectionReleased(boolean committed) {
        MethodCallbackSessionResource resource = new MethodCallbackSessionResource();
        AsyncExecutor executor = mock(AsyncExecutor.class);
        CompoundInvocation invocations = new CompoundInvocation();
        ReflectionTestUtils.setField(resource, "executor", executor);
        ReflectionTestUtils.setField(resource, "sharedInvocations", invocations);

        resource.afterConnectionReleased(committed);

        if (committed) {
            assertTrue(invocations.isEmpty());
        } else {
            assertTrue(invocations.isEmpty());
        }
        verify(executor, never()).execute(isA(Runnable.class));
    }

    @Test
    void methodCallbackSessionResource_afterConnectionReleased_withInvocations() {
        MethodCallbackSessionResource resource = new MethodCallbackSessionResource();
        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncAdvice advice = new AsyncAdvice();
        TraceableMethodInvocation invocation = mock(TraceableMethodInvocation.class);

        CompoundInvocation invocations = new CompoundInvocation();
        invocations.setAdvice(advice);
        invocations.add(invocation);

        ReflectionTestUtils.setField(resource, "executor", executor);
        ReflectionTestUtils.setField(resource, "sharedInvocations", invocations);

        resource.afterConnectionReleased(true);

        verify(executor).execute(isA(Runnable.class));
    }

    private Async createAsyncAnnotation(boolean authenticateAsSystem) {
        return new Async() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Async.class;
            }

            @Override
            public boolean transactional() {
                return false;
            }

            @Override
            public boolean delayUntilAfterCommit() {
                return false;
            }

            @Override
            public boolean shared() {
                return true;
            }

            @Override
            public boolean failIfNotScheduledFromTransaction() {
                return false;
            }

            @Override
            public boolean authenticateAsSystem() {
                return authenticateAsSystem;
            }

            @Override
            public WorkQueueConfig workQueue() {
                return null;
            }
        };
    }

    public static Stream<Arguments> provideAuthenticateAsSystemParams() {
        return Stream.of(
                Arguments.of(true, 1),
                Arguments.of(false, 0)
        );
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
    }
}