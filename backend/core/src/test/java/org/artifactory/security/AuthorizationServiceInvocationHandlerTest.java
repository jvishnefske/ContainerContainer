package org.artifactory.security;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Igor Usenko
 */
class AuthorizationServiceInvocationHandlerTest {

    public static final String TOTAL_CALLS = "authorization.service.calls";
    public static final String SHADOW_CALLS = "authorization.service.shadow.calls";
    public static final String REJECTED_SHADOW_CALLS = "authorization.service.rejected.shadow.calls";
    private LegacyAuthorizationServiceTestImpl legacyImpl;
    private MemoryAppender appender;
    private AuthorizationServiceTestImpl newImpl;
    private MeterRegistry meterRegistry;
    private AuthorizationServiceInvocationTestHandler handler;

    // Fork: the handler's executor takes ContextHelper.get(); a mock context leaked by an earlier test in the same
    // JVM (several upstream tests bind one and never unbind) has no tracer, and the shadow call then fails.
    @BeforeEach
    void unbindLeakedContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void legacyAndNewMethodsAreCalled() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = true;
        float probability = 2.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);
        Object result = getResultForIdempotentBooleanMethod(latch);

        assertEquals(true, result);
        assertTrue(legacyImpl.isCalled());
        assertTrue(newImpl.isCalled());
        assertTrue(handler.getDiffMessage().isBlank());
        assertTrue(handler.getErrMessage().isBlank());
        assertEquals(2, meterRegistry.getMeters().size());
        assertCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", true, true);
        assertShadowCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", true, "(none)");
    }

    @Test
    void legacyAndNewMethodsAreCalledForLists() throws Throwable {
        RepoAcl repoAcl1 = mock(RepoAcl.class);
        RepoAcl repoAcl2 = mock(RepoAcl.class);
        List<RepoAcl> legacyValue = List.of(repoAcl1, repoAcl2);
        List<RepoAcl> newValue = List.of(repoAcl2, repoAcl1);
        float probability = 2.0f;

        CountDownLatch latch = setupLists(legacyValue, newValue, probability);
        Object result = getResultForIdempotentListMethod(latch);

        assertEquals(legacyValue, result);
        assertTrue(legacyImpl.isCalled());
        assertTrue(newImpl.isCalled());
        assertTrue(handler.getDiffMessage().isBlank());
        assertTrue(handler.getErrMessage().isBlank());
        assertEquals(2, meterRegistry.getMeters().size());
        assertCallRegistered("public abstract java.util.List<org.artifactory.security.RepoAcl> " +
                "org.artifactory.api.security.AuthorizationService.getAllRepoAcls()", true, true);
        assertShadowCallRegistered("public abstract java.util.List<org.artifactory.security.RepoAcl> " +
                "org.artifactory.api.security.AuthorizationService.getAllRepoAcls()", true, "(none)");
    }

    @Test
    void newImplementationOfNonIdempotentMethod() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = true;
        float probability = 2.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);
        getResultForNonIdempotentMethod(latch);

        assertTrue(legacyImpl.isCalled());
        assertFalse(newImpl.isCalled());
        assertTrue(handler.getDiffMessage().isBlank());
        assertTrue(handler.getErrMessage().isBlank());
        assertEquals(1, meterRegistry.getMeters().size());
        assertCallRegistered("public abstract void org.artifactory.api.security.AuthorizationService." +
                "removeAllGroupAces(java.lang.String)", false, false);
    }

    @Test
    void onlyLegacyMethodsAreCalled() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = true;
        float probability = 0.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);
        Object result = getResultForIdempotentBooleanMethod(latch);

        assertEquals(true, result);
        assertTrue(legacyImpl.isCalled());
        assertFalse(newImpl.isCalled());
        assertTrue(handler.getDiffMessage().isBlank());
        assertTrue(handler.getErrMessage().isBlank());
        assertEquals(1, meterRegistry.getMeters().size());
        assertCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", true, false);
    }

    @Test
    void whenNewMethodThrowsAnExceptionItIsLogged() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = true;
        float probability = 2.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);
        newImpl.setThrowException();
        Object result = getResultForIdempotentBooleanMethod(latch);

        assertEquals(true, result);
        assertTrue(legacyImpl.isCalled());
        assertTrue(newImpl.isCalled());
        assertTrue(handler.getDiffMessage().isBlank());
        assertTrue(appender.contains("canRead(org.artifactory.repo.RepoPath)", Level.WARN));
        assertEquals(2, meterRegistry.getMeters().size());
        assertCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", true, true);
        assertShadowCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", false, "java.lang.RuntimeException");
    }

    @Test
    void whenDifferentResultsItIsLogged() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = false;
        float probability = 2.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);
        Object result = getResultForIdempotentBooleanMethod(latch);

        assertEquals(true, result);
        assertTrue(handler.getErrMessage().isBlank());
        assertTrue(legacyImpl.isCalled());
        assertTrue(newImpl.isCalled());
        assertCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", true, true);
        assertShadowCallRegistered("public abstract boolean org.artifactory.api.security.AuthorizationService." +
                "canRead(org.artifactory.repo.RepoPath)", false, "(none)");
    }

    @Test
    void whenDifferentResultsItIsLoggedForLists() throws Throwable {
        RepoAcl repoAcl1 = mock(RepoAcl.class);
        RepoAcl repoAcl2 = mock(RepoAcl.class);
        RepoAcl repoAcl3 = mock(RepoAcl.class);
        List<RepoAcl> legacyValue = List.of(repoAcl1, repoAcl2);
        List<RepoAcl> newValue = List.of(repoAcl2, repoAcl3);
        float probability = 2.0f;

        CountDownLatch latch = setupLists(legacyValue, newValue, probability);
        Object result = getResultForIdempotentListMethod(latch);

        assertEquals(legacyValue, result);
        assertTrue(handler.getErrMessage().isBlank());
        assertTrue(legacyImpl.isCalled());
        assertTrue(newImpl.isCalled());
        assertCallRegistered("public abstract java.util.List<org.artifactory.security.RepoAcl> " +
                "org.artifactory.api.security.AuthorizationService.getAllRepoAcls()", true, true);
        assertShadowCallRegistered("public abstract java.util.List<org.artifactory.security.RepoAcl> " +
                "org.artifactory.api.security.AuthorizationService.getAllRepoAcls()", false, "(none)");
    }

    @Test
    void rejectWhenQueueIsFull() throws Throwable {
        boolean legacyValue = true;
        boolean newValue = false;
        float probability = 2.0f;

        CountDownLatch latch = setupBooleans(legacyValue, newValue, probability);

        // pause the first call for a while
        // so the second call will be queued
        newImpl.methodPauseLatch = new CountDownLatch(1);

        doSeveralCalls();
        latch.await(400, TimeUnit.MILLISECONDS); // await with timeout to prevent test hanging
        assertEquals(0, latch.getCount()); // ensure that the latch was actually counted down

        assertRejectedShadowCallsRegistered();

        // resume the first call
        newImpl.methodPauseLatch.countDown();
    }

    private void doSeveralCalls() throws Throwable {
        Method method = AuthorizationService.class.getMethod("canRead", RepoPath.class);

        // first -> run and paused by methodPauseLatch
        invokeAsync(method);
        // second -> queued in one element queue because pool is configured with one thread and
        // that thread is paused by first invokeAsync(method) call
        invokeAsync(method);
        // third -> rejected as queue is full
        invokeAsync(method);
    }

    private void invokeAsync(Method method) {
        Thread.startVirtualThread(() -> {
            try {
                handler.invoke(null, method, new Object[] {new RepoPathImpl("a", "b")});
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assertRejectedShadowCallsRegistered() {
        assertEquals(1, meterRegistry.get(REJECTED_SHADOW_CALLS)
                .counter()
                .count());
    }

    private void assertCallRegistered(String signature, boolean canShadow, boolean didShadow) {
        assertEquals(1, meterRegistry.get(TOTAL_CALLS)
                .tag("signature", signature)
                .tag("canShadow", String.valueOf(canShadow))
                .tag("didShadow", String.valueOf(didShadow))
                .counter()
                .count());
    }

    private void assertShadowCallRegistered(String signature, boolean resultMatches, String exceptionMessage) {
        assertEquals(1, meterRegistry.get(SHADOW_CALLS)
                .tag("signature", signature)
                .tag("resultMatches", String.valueOf(resultMatches))
                .tag("ex", exceptionMessage)
                .counter()
                .count());
    }

    private Object getResultForIdempotentBooleanMethod(CountDownLatch latch) throws Throwable {
        Method method = AuthorizationService.class.getMethod("canRead", RepoPath.class);
        Object result = handler.invoke(null, method, new Object[] {new RepoPathImpl("a", "b")});
        latch.await(200, TimeUnit.MILLISECONDS);
        return result;
    }

    private Object getResultForIdempotentListMethod(CountDownLatch latch) throws Throwable {
        Method method = AuthorizationService.class.getMethod("getAllRepoAcls");
        Object result = handler.invoke(null, method, new Object[] {});
        latch.await(200, TimeUnit.MILLISECONDS);
        return result;
    }

    private void getResultForNonIdempotentMethod(CountDownLatch latch) throws Throwable {
        Method method = AuthorizationService.class.getMethod("removeAllGroupAces", String.class);
        handler.invoke(null, method, new Object[] {"b"});
        latch.await(200, TimeUnit.MILLISECONDS);
    }

    private CountDownLatch setupBooleans(boolean legacyValue, boolean newValue, float probability) {
        legacyImpl = new LegacyAuthorizationServiceTestImpl(legacyValue);
        newImpl = new AuthorizationServiceTestImpl(newValue);
        return commonSetup(probability);
    }

    private CountDownLatch setupLists(List<RepoAcl> legacyValue, List<RepoAcl> newValue, float probability) {
        legacyImpl = new LegacyAuthorizationServiceTestImpl(legacyValue);
        newImpl = new AuthorizationServiceTestImpl(newValue);
        return commonSetup(probability);
    }

    private CountDownLatch commonSetup(float probability) {
        meterRegistry = new SimpleMeterRegistry();

        CountDownLatch latch = new CountDownLatch(1);
        appender = MemoryAppenderUtil.createMemoryAppender(AuthorizationServiceInvocationHandler.class, Level.DEBUG);
        handler = new AuthorizationServiceInvocationTestHandler(meterRegistry, 1, 1, 60, latch);
        handler.postConstruct();
        handler.autowire(legacyImpl, newImpl, probability);

        return latch;
    }

    static class AuthorizationServiceInvocationTestHandler extends AuthorizationServiceInvocationHandler {

        private final CountDownLatch latch;
        private final AtomicReference<String> diffMessage;
        private final AtomicReference<String> errMessage;

        public AuthorizationServiceInvocationTestHandler(
                MeterRegistry meterRegistry,
                int corePoolSize,
                int maxThreads,
                int keepAliveTimeout,
                CountDownLatch latch
        ) {
            super(meterRegistry, corePoolSize, maxThreads, keepAliveTimeout, 1);
            this.latch = latch;
            this.diffMessage = new AtomicReference<>("");
            this.errMessage = new AtomicReference<>("");
        }

        public String getDiffMessage() {
            return diffMessage.get();
        }

        public String getErrMessage() {
            return errMessage.get();
        }

        @Override
        void logDifference(String methodSignature, Object legacyValue, Object newValue) {
            super.logDifference(methodSignature, legacyValue, newValue);
            this.diffMessage.set(methodSignature);
            latch.countDown();
        }

        @Override
        void logFailure(String methodSignature, Exception e) {
            super.logFailure(methodSignature, e);
            this.errMessage.set(methodSignature + e.getMessage());
            latch.countDown();
        }

        @Override
        void reportRejectedShadowCall() {
            super.reportRejectedShadowCall();
            latch.countDown();
        }
    }

    static class AuthorizationServiceTestImpl extends AuthorizationServiceImpl {

        private final boolean result;
        private final List<RepoAcl> listResult;
        private final AtomicBoolean called;
        private final AtomicBoolean throwException;

        private CountDownLatch methodPauseLatch = new CountDownLatch(0);

        public AuthorizationServiceTestImpl(boolean result) {
            this.result = result;
            this.listResult = null;
            this.called = new AtomicBoolean(false);
            this.throwException = new AtomicBoolean(false);
        }

        public AuthorizationServiceTestImpl(List<RepoAcl> listResult) {
            this.result = false;
            this.listResult = listResult;
            this.called = new AtomicBoolean(false);
            this.throwException = new AtomicBoolean(false);
        }

        public void setThrowException() {
            throwException.set(true);
        }

        public boolean isCalled() {
            return called.get();
        }

        @SneakyThrows
        @Override
        public boolean canRead(RepoPath repoPath) {
            methodPauseLatch.await(200, TimeUnit.MILLISECONDS);
            assertEquals(0, methodPauseLatch.getCount());
            called.set(true);
            if (throwException.get()) {
                throw new RuntimeException("Test exception");
            }
            return result;
        }

        @Override
        public List<RepoAcl> getAllRepoAcls() {
            called.set(true);
            if (throwException.get()) {
                throw new RuntimeException("Test exception");
            }
            return listResult;
        }

        @Override
        public void removeAllGroupAces(String groupName) {
            called.set(true);
        }
    }

    static class LegacyAuthorizationServiceTestImpl extends LegacyAuthorizationServiceImpl {

        private final boolean result;
        private final List<RepoAcl> listResult;
        private final AtomicBoolean called;

        public LegacyAuthorizationServiceTestImpl(boolean result) {
            this.result = result;
            this.listResult = null;
            this.called = new AtomicBoolean(false);
        }

        public LegacyAuthorizationServiceTestImpl(List<RepoAcl> listResult) {
            this.result = false;
            this.listResult = listResult;
            this.called = new AtomicBoolean(false);
        }

        public boolean isCalled() {
            return called.get();
        }

        @Override
        public boolean canRead(RepoPath repoPath) {
            called.set(true);
            return result;
        }

        @Override
        public List<RepoAcl> getAllRepoAcls() {
            called.set(true);
            return listResult;
        }

        @Override
        public void removeAllGroupAces(String groupName) {
            called.set(true);
        }
    }
}
