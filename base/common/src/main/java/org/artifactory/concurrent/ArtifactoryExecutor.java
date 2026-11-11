package org.artifactory.concurrent;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.TraceableCallableDecorator;
import org.artifactory.opentracing.TraceableRunnableDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Basic Impl of a delegating executor. Currently supports the following decorations:
 * {@link TraceableRunnableDecorator} / {@link TraceableCallableDecorator}
 * {@link ArtifactoryRunnable}
 * can be easily extended to use other decorators / execution strategies.
 *
 * @author Uriah Levy
 */
public class ArtifactoryExecutor extends AbstractExecutorService {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryExecutor.class);

    private ExecutorService executorService;
    private ArtifactoryContext appContext;
    private boolean bindToContext;
    private boolean inheritSpan;

    ArtifactoryExecutor(ExecutorService executorService, @Nullable ArtifactoryContext appContext,
            boolean shouldBindToContext) {
        this(executorService, appContext, shouldBindToContext, true);
    }

    ArtifactoryExecutor(ExecutorService executorService, @Nullable ArtifactoryContext appContext,
            boolean shouldBindToContext, boolean inheritSpan) {
        this.executorService = executorService;
        this.appContext = appContext;
        this.bindToContext = shouldBindToContext;
        this.inheritSpan = inheritSpan;
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Nonnull
    @Override
    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    public ExecutorService getExecutor() {
        return executorService;
    }

    @Override
    public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(@Nonnull Runnable task) {
        executorService.execute(getDecoratedRunnable(task));
    }

    @Nonnull
    @Override
    public Future<?> submit(Runnable task) {
        return executorService.submit(getDecoratedRunnable(task));
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
        return executorService.submit(getDecoratedCallable(task));
    }

    @Nonnull
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        if (appContext != null) {
            ArtifactoryTracer artifactoryTracer = appContext.beanForType(ArtifactoryTracer.class);
            List<Callable<T>> decorated = tasks.stream()
                    .map(task -> bindToContext ? new TraceableCallableDecorator<>(
                            new ArtifactoryCallable<>(task, appContext,
                                    SecurityContextHolder.getContext().getAuthentication()), artifactoryTracer,
                            inheritSpan ? artifactoryTracer.getCurrentThreadActiveSpan() : null) :
                            new TraceableCallableDecorator<>(task, artifactoryTracer,
                                    inheritSpan ? artifactoryTracer.getCurrentThreadActiveSpan() : null))
                    .collect(Collectors.toList());
            return executorService.invokeAll(decorated);
        } else {
            return executorService.invokeAll(tasks);
        }
    }

    private Runnable getDecoratedRunnable(Runnable runnable) {
        Runnable decorated = runnable;
        if (appContext != null) {
            if (bindToContext) {
                log.debug("Binding thread to App and Security context");
                decorated = new ArtifactoryRunnable(runnable, appContext,
                        SecurityContextHolder.getContext().getAuthentication());
            } else {
                ArtifactoryTracer artifactoryTracer = appContext.beanForType(ArtifactoryTracer.class);
                return new TraceableRunnableDecorator(decorated, artifactoryTracer,
                        inheritSpan ? artifactoryTracer.getCurrentThreadActiveSpan() : null);
            }
        }
        return decorated;
    }

    private <T> Callable<T> getDecoratedCallable(Callable<T> callable) {
        Callable decorated = callable;
        if (appContext != null) {
            if (bindToContext) {
                log.debug("Binding thread to App and Security context");
                decorated = new ArtifactoryCallable(decorated, appContext,
                        SecurityContextHolder.getContext().getAuthentication());
            } else {
                ArtifactoryTracer artifactoryTracer = appContext.beanForType(ArtifactoryTracer.class);
                return new TraceableCallableDecorator<>(decorated, artifactoryTracer,
                        inheritSpan ? artifactoryTracer.getCurrentThreadActiveSpan() : null);
            }
        }
        return decorated;
    }
}
