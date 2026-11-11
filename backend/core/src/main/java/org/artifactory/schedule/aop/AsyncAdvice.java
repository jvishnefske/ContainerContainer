/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.schedule.aop;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.artifactory.async.Async;
import org.artifactory.async.AsyncCustomExecutorInfo;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.async.CustomExecutor;
import org.artifactory.sapi.common.Lock;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.fs.lock.aop.LockingAdvice;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.tx.SessionResourceAdapter;
import org.artifactory.workqueue.AsyncWorkQueueService;
import org.artifactory.workqueue.WorkItem;
import org.artifactory.workqueue.WorkQueueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author Yoav Landman
 */
@Component
public class AsyncAdvice implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AsyncAdvice.class);
    private static final String ASYNC_INVOCATION_ERR = "An async invocation of (%1$s): %2$s";
    private static final String REJECTED_TASK_RRR = "Task {} rejected by scheduler: {}";
    private AsyncWorkQueueService asyncWorkQueueService;

    @Override
    public Future<?> invoke(final MethodInvocation invocation) {
        MethodAnnotation<Lock> lockMethodAnnotation = getMethodAnnotation(invocation, Lock.class);
        if (lockMethodAnnotation.annotation != null) {
            throw new IllegalStateException("The @Async annotation cannot be used with the @Lock annotation. " +
                    "Use @Async#transactional=true instead: " + lockMethodAnnotation.method);
        }
        MethodAnnotation<Async> asyncMethodAnnotation = getMethodAnnotation(invocation, Async.class);
        boolean delayExecutionUntilCommit = asyncMethodAnnotation.annotation.delayUntilAfterCommit();
        boolean failIfNotScheduledFromTransaction = asyncMethodAnnotation.annotation
                .failIfNotScheduledFromTransaction();
        boolean inSession = StorageSessionHolder.getSession() != null;
        validateAnnotations(asyncMethodAnnotation, delayExecutionUntilCommit, failIfNotScheduledFromTransaction,
                inSession);
        TraceableMethodInvocation traceableInvocation;
        // Submit invocation use cases:
        // 1. submit async call with delayAfterCommit=false
        // 2. submit async call with delayAfterCommit=true
        // 3. submit work queue with delayAfterCommit=false
        // 4. submit work queue with delayAfterCommit=true
        if (asyncMethodAnnotation.annotation.workQueue().enabled()) {
            validateWorkQueueMethodInvocationSignature(invocation, asyncMethodAnnotation);
            WorkExecution workExecution = new WorkExecution(asyncMethodAnnotation.annotation.workQueue().name(),
                    asyncMethodAnnotation.annotation.workQueue().blockUntilFinished(), delayExecutionUntilCommit,
                    failIfNotScheduledFromTransaction);
            traceableInvocation =
                    new TraceableMethodInvocation(invocation, Thread.currentThread().getName(), workExecution);
        } else {
            traceableInvocation = new TraceableMethodInvocation(invocation, Thread.currentThread().getName());
        }
        log.trace("Adding: {}", traceableInvocation);
        return execute(delayExecutionUntilCommit, inSession, traceableInvocation);
    }

    @VisibleForTesting
    protected Future<?> execute(boolean delayExecutionUntilCommit, boolean inSession,
            TraceableMethodInvocation traceableInvocation) {
        if (delayExecutionUntilCommit && inSession) {
            return executeAfterSession(traceableInvocation);
        } else {
            return executeImmediately(traceableInvocation);
        }
    }

    @Nullable
    private Future<?> executeImmediately(TraceableMethodInvocation traceableInvocation) {
        if (traceableInvocation.getWorkExecution() != null) {
            // Submit work queue with delayAfterCommit=false
            executeWorkQueueImmediately(traceableInvocation.getWorkExecution(), traceableInvocation);
            return CompletableFuture.completedFuture(true);
        } else {
            // Submit async call with delayAfterCommit=false
            return executeAsync(traceableInvocation);
        }
    }

    @Nullable
    private Future<?> executeAfterSession(TraceableMethodInvocation traceableInvocation) {
        if (traceableInvocation.getWorkExecution() == null) {
            //Schedule task submission for session save()
            // 1. submit async call with delayAfterCommit=true
            // 3. submit work queue with delayAfterCommit=true
            StorageSession session = StorageSessionHolder.getSession();
            MethodCallbackSessionResource sessionCallbacks =
                    session.getOrCreateResource(MethodCallbackSessionResource.class);
            sessionCallbacks.set(this, traceableInvocation);
        } else {
            executeWorkQueueAfterCommit(traceableInvocation.getWorkExecution(), traceableInvocation);
        }
        //No future
        return null;
    }

    private void validateAnnotations(MethodAnnotation<Async> asyncMethodAnnotation, boolean delayExecutionUntilCommit,
            boolean failIfNotScheduledFromTransaction, boolean inSession) {
        if (!inSession && delayExecutionUntilCommit) {
            if (failIfNotScheduledFromTransaction) {
                throw new IllegalStateException("Async invocation scheduled for after commit, " +
                        "cannot be scheduled outside a transaction: " + asyncMethodAnnotation.method);
            } else {
                log.debug("Async invocation scheduled for after commit, but not scheduled inside a transaction: {}",
                        asyncMethodAnnotation.method);
            }
        }
    }

    private void validateWorkQueueMethodInvocationSignature(MethodInvocation invocation,
            MethodAnnotation<Async> asyncMethodAnnotation) {
        Type[] genericParameterTypes = invocation.getMethod().getGenericParameterTypes();
        if (genericParameterTypes.length != 1 || !(genericParameterTypes[0].getClass().isInstance(WorkItem.class))) {
            throw new IllegalStateException("Async invocation " + asyncMethodAnnotation.method +
                    " using work queue, but not using single work item parameter");
        }

        if (!invocation.getMethod().getReturnType().equals(Void.TYPE)) {
            throw new IllegalStateException("Async invocation " + asyncMethodAnnotation.method +
                    " using work queue, but not using void return type");
        }
    }

    private AsyncWorkQueueService asyncWorkQueueService() {
        if (asyncWorkQueueService == null) {
            asyncWorkQueueService = InternalContextHelper.get().beanForType(AsyncWorkQueueService.class);
        }
        return asyncWorkQueueService;
    }

    private <T extends Annotation> MethodAnnotation<T> getMethodAnnotation(MethodInvocation invocation,
            Class<T> annotationClass) {
        Method method = invocation.getMethod();
        T annotation = method.getAnnotation(annotationClass);
        //Try to read the class level annotation if the interface is not found
        if (annotation != null) {
            return new MethodAnnotation<>(method, annotation);
        }
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(
                ((ReflectiveMethodInvocation) invocation).getProxy());
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        annotation = specificMethod.getAnnotation(annotationClass);
        return new MethodAnnotation<>(specificMethod, annotation);
    }

    private void executeWorkQueueImmediately(WorkExecution workExecution, TraceableMethodInvocation invocation) {
        Runnable runnable = () -> {
            try {
                invocation.invoke();
            } catch (Exception e) {
                throw new WorkQueueException("Failed to proceed invocation of: " + invocation.getMethod(), e);
            }
        };
        Authentication originalAuthentication = null;
        try {
            MethodAnnotation<Async> methodAnnotation =
                    getMethodAnnotation(invocation.getWrapped(), Async.class);
            originalAuthentication = authenticateAsSystem(methodAnnotation);

            asyncWorkQueueService().execute(workExecution.getWorkQueueName(),
                    (WorkItem) invocation.getArguments()[0], runnable, workExecution.isBlockUntilFinished());
        } finally {
            restoreAuthentication(originalAuthentication);
        }
    }

    /**
     * Restores the original authentication after authentication as the system.
     *
     * @param originalAuthentication The original Authentication object to be restored.
     */
    @VisibleForTesting
    static void restoreAuthentication(Authentication originalAuthentication) {
        if (originalAuthentication != null) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(originalAuthentication);
        }
    }

    /**
     * Authenticates the current user as system if specified by the method annotation.
     *
     * @param methodAnnotation The MethodAnnotation object containing the authentication settings.
     * @return The original Authentication object before authentication as the system, or null if no authentication
     * took place.
     */
    @VisibleForTesting
    static Authentication authenticateAsSystem(MethodAnnotation<Async> methodAnnotation) {
        Authentication originalAuthentication = null;
        if (methodAnnotation.annotation.authenticateAsSystem()) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            originalAuthentication = securityContext.getAuthentication();
            InternalContextHelper.get().getSecurityService().authenticateAsSystem();
        }
        return originalAuthentication;
    }

    private void executeWorkQueueAfterCommit(WorkExecution workExecution, TraceableMethodInvocation invocation) {
        Runnable runnable = () -> {
            try {
                invocation.invoke();
            } catch (Exception e) {
                throw new WorkQueueException("Failed to proceed invocation of: " + invocation.getMethod(), e);
            }
        };

        asyncWorkQueueService().execute(workExecution.getWorkQueueName(),
                (WorkItem) invocation.getArguments()[0], runnable, workExecution.isBlockUntilFinished(),
                workExecution.isDelayAfterCommit(),
                workExecution.isFailIfNoSession());
    }

    private AsyncExecutor executor() {
        AsyncExecutor executor = null;
        AsyncCustomExecutorInfo customExecutor = CustomExecutor.get();
        if (customExecutor != null) {
            executor = customExecutor.getExecutor();
        }

        if (executor == null) {
            executor =
                    InternalContextHelper.get().beanForType(AsyncExecutor.class);
        }
        return executor;
    }

    private Future<?> executeAsync(final TraceableMethodInvocation invocation) {
        if (invocation.getWorkExecution() != null) {
            throw new IllegalStateException("Cannot call submit on work execution for " + invocation);
        }

        Future<?> future = null;
        try {
            future = executor().execute(() -> {
                try {
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        //Sanity check we should never have a tx sync on an existing pooled thread
                        throw new IllegalStateException(
                                String.format(ASYNC_INVOCATION_ERR, invocation.getMethod(),
                                        "should not be associated with an existing transaction."));
                    }
                    Object result = doInvoke(invocation);
                    // if the result is not of type Future don't bother returning it (unless you are fond of
                    //ClassCastExceptions)
                    if (result instanceof Future) {
                        return ((Future<?>) result).get();
                    } else {
                        return null;
                    }
                } catch (Throwable throwable) {
                    Throwable original = invocation.getThrowable();
                    original.initCause(throwable);
                    if (log.isDebugEnabled()) {
                        log.debug(String.format(ASYNC_INVOCATION_ERR, invocation.getMethod(), throwable.getMessage()),
                                original);
                    }
                    return null;
                }
            });
        } catch (TaskRejectedException e) {
            log.error(REJECTED_TASK_RRR, invocation, e.getMessage());
            log.debug(REJECTED_TASK_RRR, invocation, e.getMessage(), e);
        }
        // only return the future result if the method returns a Future object
        if (Future.class.isAssignableFrom(invocation.getMethod().getReturnType())) {
            return future;
        } else {
            return null;
        }
    }

    @VisibleForTesting
    protected Object doInvoke(TraceableMethodInvocation invocation) throws Throwable {
        Authentication originalAuthentication = null;
        try {
            MethodAnnotation<Async> methodAnnotation =
                    getMethodAnnotation(invocation.getWrapped(), Async.class);
            if (methodAnnotation.annotation == null) {
                throw new IllegalArgumentException(
                        String.format(ASYNC_INVOCATION_ERR, invocation.getMethod(),
                                "should be used with an @Async annotated invocation."));
            }
            originalAuthentication = authenticateAsSystem(methodAnnotation);
            Object result;
            if (methodAnnotation.annotation.transactional()) {
                //Wrap in a transaction
                log.trace("Invoking {} in transaction", invocation);
                result = new LockingAdvice().invoke(invocation);
                log.trace("Finished {} in transaction", invocation);
            } else {
                log.trace("Invoking {} ", invocation);
                result = invocation.proceed();
                log.trace("Finished {}", invocation);
            }
            return result;
        } finally {
            restoreAuthentication(originalAuthentication);
        }
    }

    public static class MethodCallbackSessionResource extends SessionResourceAdapter {
        final CompoundInvocation sharedInvocations = new CompoundInvocation();
        AsyncExecutor executor;

        void set(AsyncAdvice advice, TraceableMethodInvocation invocation) {
            sharedInvocations.setAdvice(advice);
            sharedInvocations.add(invocation);
            executor = advice.executor();
        }

        @Override
        public void afterConnectionReleased(boolean commit) {
            if (commit) {
                if (!sharedInvocations.isEmpty()) {
                    try {
                        executor.execute(() -> doInvoke(sharedInvocations));
                    } catch (TaskRejectedException e) {
                        log.error(REJECTED_TASK_RRR, sharedInvocations, e.getMessage(), e);
                    }
                }
            } else {
                sharedInvocations.clear();
            }
        }

        private void doInvoke(CompoundInvocation invocation) {
            try {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    //Sanity check we should never have a tx sync on an existing pooled thread
                    throw new IllegalStateException(
                            String.format(ASYNC_INVOCATION_ERR, invocation.getMethod(),
                                    "should not be associated with an existing transaction."));
                }
                invocation.proceed();
            } catch (Throwable throwable) {
                log.error(String.format(ASYNC_INVOCATION_ERR, invocation.getLatestMethod(),
                        throwable.getMessage()));
            }
        }
    }

    record MethodAnnotation<T extends Annotation>(Method method, T annotation) {
    }
}