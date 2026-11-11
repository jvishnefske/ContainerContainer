package org.artifactory.concurrent;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.artifactory.event.EventHintType.AsyncThread;
import static org.artifactory.mirror.MirrorConstants.ASYNC_THREAD;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Uriah Levy
 */
@Test
public class ArtifactoryExecutorTest {

    private final ArtifactoryTracer tracer = new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
    private AutoCloseable autoCloseable;

    @Mock
    ArtifactoryContext artifactoryContext;


    @BeforeClass
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class))
                .thenReturn(tracer);
    }

    @AfterClass
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testFixedThreadPool() {
        ArtifactoryExecutorFactory.newFixedThreadPool(1, artifactoryContext, true);
    }

    @Test(enabled = false)
    public void testNotBoundClassicThreadPool() throws ExecutionException, InterruptedException {
        ArtifactoryExecutor executor = ArtifactoryExecutorFactory
                .newClassicThreadPool(ThreadPoolConfig.builder()
                        .maxThreads(1)
                        .queue(new LinkedBlockingQueue<>())
                        .keepAliveTimeout(1L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                        .build(), artifactoryContext, false);
        // Not bound
        executor.submit(() -> assertNull(ContextHelper.get())).get();
    }

    public void testBoundClassicThreadPool() throws ExecutionException, InterruptedException {
        ArtifactoryExecutor executor = ArtifactoryExecutorFactory
                .newClassicThreadPool(ThreadPoolConfig.builder()
                        .maxThreads(1)
                        .queue(new LinkedBlockingQueue<>())
                        .keepAliveTimeout(1L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                        .build(), artifactoryContext, true);
        // Bound
        executor.submit(() -> assertNotNull(ContextHelper.get())).get();
    }

    @Test
    public void testTraceIdOnExecutorThread() throws ExecutionException, InterruptedException {
        ArtifactoryExecutor executor = ArtifactoryExecutorFactory
                .newClassicThreadPool(ThreadPoolConfig.builder()
                        .maxThreads(1)
                        .queue(new LinkedBlockingQueue<>())
                        .keepAliveTimeout(1L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                        .build(), artifactoryContext, false);
        // Trace ID exists
        executor.submit(() -> {
            assertNull(ContextHelper.get());
            assertNotNull(tracer.getFullContextTracingId());
        }).get();
    }

    @Test(dataProvider = "bindContext")
    public void testMirrorMarkerIsSetOnEachExecutedRunnable(boolean bindContext)
            throws ExecutionException, InterruptedException {
        ArtifactoryExecutor executor = ArtifactoryExecutorFactory
                .newClassicThreadPool(ThreadPoolConfig.builder()
                        .maxThreads(1)
                        .queue(new LinkedBlockingQueue<>())
                        .keepAliveTimeout(1L)
                        .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                        .build(), artifactoryContext, bindContext);

        executor.submit(() -> {
            Assert.assertNotNull(EventLogHintManager.getHints());
            Assert.assertTrue(EventLogHintManager.getHints().contains(AsyncThread.getPrefix() + ASYNC_THREAD));
        }).get();
    }

    @DataProvider
    public static Object[][] bindContext() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}