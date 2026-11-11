package org.artifactory.ph;

import ch.qos.logback.classic.Level;
import com.jfrog.ph.acl.service.PackageTaskExecutorSupplier;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.test.TestUtils;
import org.artifactory.util.ThreadDumpUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;

class PackageTaskExecutorSupplierImplTest {

    @BeforeAll
    static void beforeAll() {
        TestUtils.setLoggingLevel(ThreadDumpUtils.class, Level.TRACE);
    }

    @AfterAll
    static void afterAll() {
        TestUtils.setLoggingLevel(ThreadDumpUtils.class, Level.INFO);
    }

    @Test
    void testGetDefaultTaskExecutor() {
        CachedThreadPoolTaskExecutor cachedThreadPoolTaskExecutor = Mockito.mock(CachedThreadPoolTaskExecutor.class);
        PackageTaskExecutorSupplier packageTaskExecutorSupplier =
                new PackageTaskExecutorSupplierImpl(cachedThreadPoolTaskExecutor);
        assertEquals(packageTaskExecutorSupplier.getDefaultTaskExecutor(), cachedThreadPoolTaskExecutor);
    }

    @Test
    void testCreateSingleTaskExecutor() {
        int maxThread = 1;
        try (MockedStatic<ArtifactoryExecutorFactory> artifactoryExecutorFactoryMockedStatic = mockStatic(
                ArtifactoryExecutorFactory.class)) {
            CachedThreadPoolTaskExecutor cachedThreadPoolTaskExecutor =
                    Mockito.mock(CachedThreadPoolTaskExecutor.class);
            PackageTaskExecutorSupplier packageTaskExecutorSupplier =
                    new PackageTaskExecutorSupplierImpl(cachedThreadPoolTaskExecutor);
            artifactoryExecutorFactoryMockedStatic.when(
                            () -> ArtifactoryExecutorFactory.newClassicThreadPool(any(ThreadPoolConfig.class), any(),
                                    anyBoolean()))
                    .thenAnswer(invocationOnMock -> {
                        ThreadPoolConfig threadPoolConfig = invocationOnMock.getArgument(0);
                        Assertions.assertEquals(maxThread, threadPoolConfig.getCorePoolSize());
                        Assertions.assertEquals(maxThread, threadPoolConfig.getMaxThreads());
                        Assertions.assertEquals(5L, threadPoolConfig.getKeepAliveTimeout());
                        Assertions.assertEquals(TimeUnit.MINUTES, threadPoolConfig.getKeepAliveTimeoutTimeUnit());
                        Assertions.assertTrue(threadPoolConfig.getQueue() instanceof LinkedBlockingQueue<Runnable>);
                        Assertions.assertEquals("art-fixed-%s", threadPoolConfig.getThreadNameFormat());
                        Boolean bindToContext = invocationOnMock.getArgument(2);
                        Assertions.assertTrue(bindToContext);
                        return null;
                    });
            packageTaskExecutorSupplier.createCustomTaskExecutor(maxThread);
        }
    }
}