package org.artifactory.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.artifactory.api.context.ArtifactoryContext;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A factory for Thread Pool executors. When an {@link ArtifactoryContext} is provided, the created Executor itself has
 * access to the app context. Also supports binding executor threads to the app-context & security context.
 *
 * @author Uriah Levy
 */
public class ArtifactoryExecutorFactory {
    private ArtifactoryExecutorFactory() {
    }

    public static ArtifactoryExecutor newArtifactoryExecutor(ExecutorService executorService,
            @Nonnull ArtifactoryContext appContext, boolean bindToContext) {
        return new ArtifactoryExecutor(executorService, appContext, bindToContext);
    }

    public static ArtifactoryExecutor newClassicThreadPool(ThreadPoolConfig config,
            @Nullable ArtifactoryContext appContext, boolean bindToContext) {
        validateConfig(config);
        return new ArtifactoryExecutor(createThreadPoolInternal(config), appContext, bindToContext);
    }

    public static ArtifactoryExecutor newClassicThreadPool(ThreadPoolConfig config,
            @Nullable ArtifactoryContext appContext, boolean bindToContext, boolean inheritSpan) {
        validateConfig(config);
        return new ArtifactoryExecutor(createThreadPoolInternal(config), appContext, bindToContext, inheritSpan);
    }

    public static ArtifactoryExecutor newFixedThreadPool(int numberOfThreads, @Nullable ArtifactoryContext appContext,
            boolean bindToContext) {
        if (bindToContext) {
            throw new UnsupportedOperationException("Bind to context is not supported with a fixed-size executor.");
        }
        return new ArtifactoryExecutor(Executors.newFixedThreadPool(numberOfThreads), appContext, bindToContext);
    }

    private static ThreadPoolExecutor createThreadPoolInternal(ThreadPoolConfig threadPoolConfig) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threadPoolConfig.getCorePoolSize(),
                threadPoolConfig.getMaxThreads(),
                threadPoolConfig.getKeepAliveTimeout(), threadPoolConfig.getKeepAliveTimeoutTimeUnit(),
                threadPoolConfig.getQueue(), getThreadFactory(threadPoolConfig));

        if (threadPoolConfig.getRejectedExecutionHandler() != null) {
            executor.setRejectedExecutionHandler(threadPoolConfig.getRejectedExecutionHandler());
        }
        if (threadPoolConfig.isAllowCoreThreadTimeOut()) {
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    private static ThreadFactory getThreadFactory(ThreadPoolConfig threadPoolConfig) {
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder().setDaemon(threadPoolConfig.isDaemon());
        if (threadPoolConfig.getThreadNameFormat() != null) {
            threadFactoryBuilder.setNameFormat(threadPoolConfig.getThreadNameFormat());
        }
        return threadFactoryBuilder.build();
    }

    private static void validateConfig(ThreadPoolConfig config) {
        if (config.getMaxThreads() <= 0 || config.getQueue() == null || config.getKeepAliveTimeoutTimeUnit() == null) {
            throw new IllegalArgumentException(
                    "Max threads is <= 0, keep-alive timeout unit, or the executor queue are null");
        }
    }
}
