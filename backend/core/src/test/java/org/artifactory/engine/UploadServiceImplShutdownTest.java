package org.artifactory.engine;

import lombok.SneakyThrows;
import lombok.val;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.common.ConstantValues;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.projects.ProjectsService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class UploadServiceImplShutdownTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void whenProcessNotFinishedThenServiceCanNotBeShutdown() throws InterruptedException {
        val uploadService = new UploadServiceForTest();
        val process = new Process(uploadService);

        process.start();
        assertFalse(uploadService.canBeShutdown());

        process.finish();
    }

    @Test
    void whenProcessFinishedThenServiceCanBeShutdown() throws InterruptedException {
        val uploadService = new UploadServiceForTest();
        val process = new Process(uploadService);

        process.start();
        process.finish();
        assertTrue(uploadService.canBeShutdown());
    }

    @Test
    void whenProcessCountMoreThanConfiguredThenOldProcessesRemovedFromTracking() throws InterruptedException {
        setLongSystemProperty(ConstantValues.gracefulShutdownMaxTrackedRequestCount, 1L);
        val uploadService = new UploadServiceForTest();
        val first = new Process(uploadService);
        val second = new Process(uploadService);

        first.start();
        second.start(); // this will remove first process from tracking

        second.finish();
        // Caffeine eviction is amortized — poll until canBeShutdown() converges (or timeout)
        await().atMost(Duration.ofSeconds(5)).until(uploadService::canBeShutdown);

        first.finish();
    }

    private static class UploadServiceForTest extends UploadServiceImpl {
        private final Map<String, CountDownLatch> startLatches = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> progressLatches = new ConcurrentHashMap<>();

        @SneakyThrows
        @Override
        void processUpload(ArtifactoryRequest request, ArtifactoryResponse response) {
            val url = request.getPath();
            notifyAboutStart(url);
            waitForFinish(url);
        }

        private void waitForFinish(String url) throws InterruptedException {
            val progress = progressLatches.get(url);
            progress.await();
        }

        private void notifyAboutStart(String url) {
            val start = startLatches.get(url);
            start.countDown();
        }

        public void setLatches(String url, CountDownLatch start, CountDownLatch progress) {
            this.startLatches.put(url, start);
            this.progressLatches.put(url, progress);
        }
    }

    private static class Process implements Runnable {
        private final UploadServiceForTest uploadService;
        private final CountDownLatch start;
        private final CountDownLatch progress;
        private final CountDownLatch finish;

        public Process(UploadServiceForTest uploadService) {
            this.uploadService = uploadService;
            this.start = new CountDownLatch(1);
            this.progress = new CountDownLatch(1);
            this.finish = new CountDownLatch(1);
        }

        public void start() throws InterruptedException {
            new Thread(this).start();
            start.await();
        }

        public void finish() throws InterruptedException {
            progress.countDown();
            finish.await();
        }

        @Override
        public void run() {
            val traceId = UUID.randomUUID().toString();
            MDC.put(ArtifactoryTracer.UBER_TRACE_ID, traceId);
            try {
                val request = Mockito.mock(ArtifactoryRequest.class);
                when(request.getPath()).thenReturn(traceId);
                this.uploadService.setLatches(traceId, start, progress);
                this.uploadService.upload(request, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                MDC.remove(ArtifactoryTracer.UBER_TRACE_ID);
                finish.countDown();
            }
        }
    }
}