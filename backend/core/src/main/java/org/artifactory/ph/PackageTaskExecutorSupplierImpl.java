package org.artifactory.ph;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import com.jfrog.ph.acl.service.PackageTaskExecutorSupplier;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PackageTaskExecutorSupplierImpl implements PackageTaskExecutorSupplier {

    final CachedThreadPoolTaskExecutor taskExecutor;

    public PackageTaskExecutorSupplierImpl(CachedThreadPoolTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public ConcurrentTaskExecutor getDefaultTaskExecutor() {
        return taskExecutor;
    }

    @Override
    public AbstractExecutorService createCustomTaskExecutor(int maxThreads) {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(maxThreads)
                .maxThreads(maxThreads)
                .keepAliveTimeout(5L)
                .keepAliveTimeoutTimeUnit(TimeUnit.MINUTES)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat("art-fixed-%s")
                .build(), ContextHelper.get(), true);

    }

}
