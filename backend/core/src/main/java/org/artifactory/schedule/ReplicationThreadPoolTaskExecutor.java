package org.artifactory.schedule;

import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Volodymyr Shulga
 */
public class ReplicationThreadPoolTaskExecutor extends ConcurrentTaskExecutor {

    public ReplicationThreadPoolTaskExecutor() {
        super(new ArtifactoryConcurrentExecutor(
                ThreadPoolConfig.builder()
                        .corePoolSize(ConstantValues.haReplicationMaxPoolSize.getInt())
                        .maxThreads(ConstantValues.haReplicationMaxPoolSize.getInt())
                        .keepAliveTimeout(5L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.MINUTES)
                        .queue(new LinkedBlockingQueue<>(ConstantValues.asyncPoolMaxQueueSize.getInt()))
                        .threadNameFormat("ha-replication-")
                        .allowCoreThreadTimeOut(true)
                        .build()));
    }

    private ArtifactoryConcurrentExecutor getArtifactoryExecutor() {
        return (ArtifactoryConcurrentExecutor) getConcurrentExecutor();
    }

    public void destroy() {
        getArtifactoryExecutor().shutdown();
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return getArtifactoryExecutor().submit(task, result);
    }
}
