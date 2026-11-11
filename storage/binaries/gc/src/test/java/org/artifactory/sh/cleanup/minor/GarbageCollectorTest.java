package org.artifactory.sh.cleanup.minor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.cleanup.GCProvider;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.storage.DeletedGCCandidate;
import org.artifactory.storage.GCCandidate;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.ifc.GarbageCollectListenerResultStatus;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author dudim
 */
@Test
public class GarbageCollectorTest extends ArtifactoryHomeBoundTest {
    private AutoCloseable autoCloseable;

    private MinorGcCollector garbageCollector;

    private int originalBatchSize;
    @Mock
    private SecurityService securityService;
    @Mock
    private GCService gcService;
    @Mock
    private GCProvider gcProvider;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    @BeforeMethod
    private void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        // Makes it possible to trace after log messages
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(mockAppender);

        when(gcProvider.getName()).thenReturn("myname");
        originalBatchSize = ConstantValues.trashcanMaxSearchResults.getInt();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.trashcanMaxSearchResults.getPropertyName(), "3");
        garbageCollector = new MinorGcCollector(gcProvider, securityService, gcService, DbType.MYSQL);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        // Restore original logger settings.
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(mockAppender);
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(
                ConstantValues.trashcanMaxSearchResults.getPropertyName(), String.valueOf(originalBatchSize));
        autoCloseable.close();
    }

    public void testRun() {
        testGarbageCollection();
    }

    public void testDestroyWithError() {
        MinorGcCollector garbageCollectorSpy = spy(garbageCollector);
        ArtifactoryExecutor executor = mock(ArtifactoryExecutor.class);
        ReflectionTestUtils.setField(garbageCollectorSpy, "executor", executor);
        try {
            when(executor.awaitTermination(120, TimeUnit.SECONDS)).thenThrow(new InterruptedException("TEST"));
            doReturn(executor).when(garbageCollectorSpy).getExecutor();
            garbageCollectorSpy.start();

            assertTrue(Thread.currentThread().isInterrupted());
            verifyLog("""
                    Failed shutting down Garbage Collector executor service
                    java.lang.InterruptedException: TEST""");

        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    public void testRunWithError() {
        testRunWithError(new RuntimeException("Error occurred"), """
                Error occurred during Garbage Collection.
                java.lang.RuntimeException: Error occurred""");
    }

    public void testThreadNameInExecutor() {
        // Keep original log level and temporarily set it to DEBUG
        Level loggerLevelToKeep = ((Logger) LoggerFactory.getLogger(MinorGcCollector.class)).getLevel();
        ((Logger) LoggerFactory.getLogger(MinorGcCollector.class)).setLevel(Level.DEBUG);

        doThrow(new RuntimeException("test")).when(gcProvider).getAction();
        when(gcProvider.getBatch()).thenReturn(List.of(new GCCandidate(null, null, null, null, -1L)));
        garbageCollector.start();
        //Verify our logging interactions
        verify(mockAppender, atLeastOnce()).doAppend(captorLoggingEvent.capture());
        boolean anyMatch = captorLoggingEvent.getAllValues().stream()
                .anyMatch(logEvent -> logEvent.getThreadName().contains("art-GC-worker-"));
        //Check the message being logged is correct
        assertTrue(anyMatch);
        // Restore original log level
        ((Logger) LoggerFactory.getLogger(MinorGcCollector.class)).setLevel(loggerLevelToKeep);
    }

    public void testRunWithLayeredError() {
        testRunWithError(new RuntimeException(new SQLException(new IOException("TEST"))), """
                Error occurred during Garbage Collection.
                java.lang.RuntimeException: java.sql.SQLException: java.io.IOException: TEST
                Caused by: java.sql.SQLException: java.io.IOException: TEST
                Caused by: java.io.IOException: TEST""");
    }

    private void testRunWithError(Exception e, String expectedReason) {
        doThrow(e).when(gcProvider).getBatch();
        garbageCollector.start();

        //Verify our logging interactions
        verifyLog(expectedReason);

        verify(gcProvider, times(1)).getBatch();
        verify(gcService, times(0)).deleteBlobInfos(any());
    }

    // Verifies logging interactions
    private void verifyLog(String expectedReason) {
        //Verify our logging interactions
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        final LoggingEvent loggingEvent = captorLoggingEvent.getValue();
        //Check the message being logged is correct
        assertEquals(expectedReason, loggingEvent.getFormattedMessage());
    }

    public void testGarbageCollection() {
        List<GCCandidate> firstBatch = Arrays.asList(
                new GCCandidate(null, null, "x", null, 0),
                new GCCandidate(null, null, "y", null, 0),
                new GCCandidate(null, null, "`", null, 0)
        );
        List<GCCandidate> secondBatch = Arrays.asList(
                new GCCandidate(null, null, "1", null, 0),
                new GCCandidate(null, null, "2", null, 0),
                new GCCandidate(null, null, "3", null, 0)
        );

        mockTwoBatches(firstBatch, secondBatch).when(gcProvider).getBatch();

        when(gcProvider.getAction()).thenReturn((candidate, result) -> true);
        garbageCollector.start();

        verify(gcProvider, times(3)).getBatch();
        List<GCCandidate> allBatches = Stream.concat(firstBatch.stream(), secondBatch.stream()).toList();
        Set<String> sha2s = allBatches.stream().map(GCCandidate::sha2).collect(Collectors.toSet());
        ArgumentCaptor<List<String>> argument = ArgumentCaptor.forClass(List.class);
        verify(gcService, times(2)).deleteBlobInfos(argument.capture());
        List<List<String>> capturedBlobInfoDeletionRequests = argument.getAllValues();
        Set<String> capturedShas = capturedBlobInfoDeletionRequests.stream().flatMap(List::stream)
                .collect(Collectors.toSet());
        Assert.assertEquals(sha2s, capturedShas);

        ExecutorService executor = (ExecutorService) ReflectionTestUtils.getField(garbageCollector, "executor");
        assert executor != null;
        assertTrue(executor.isTerminated());
    }

    public void testGarbageCollectionSmallBatch() {
        List<GCCandidate> firstBatch = Arrays.asList(
                new GCCandidate(null, null, "file-1", null, 0),
                new GCCandidate(null, null, "file-2", null, 0));
        List<GCCandidate> secondBatch = Collections.singletonList(
                new GCCandidate(null, null, "x", null, 0));
        mockTwoBatches(firstBatch, secondBatch).when(gcProvider).getBatch();
        when(gcProvider.getAction()).thenReturn((candidate, result) -> true);

        garbageCollector.start();
        // ensure second batch was never called as the first one was not full
        verify(gcProvider, times(1)).getBatch();
    }

    public void testGarbageCollectionWithSingleError() {
        List<GCCandidate> firstBatch = Arrays.asList(
                new GCCandidate(null, null, "x", null, 0),
                new GCCandidate(null, null, "y", null, 0),
                new GCCandidate(null, null, "z", null, 0)
        );
        List<GCCandidate> secondBatch = Arrays.asList(
                new GCCandidate(null, null, "1", null, 0),
                new GCCandidate(null, null, "2", null, 0),
                new GCCandidate(null, null, "3", null, 0)

        );
        mockTwoBatches(firstBatch, secondBatch).when(gcProvider).getBatch();

        AtomicInteger counter = new AtomicInteger(0);
        when(gcProvider.getAction()).thenReturn((candidate, result) -> {
            counter.getAndIncrement();
            return !candidate.sha2().equals("x");
        });
        garbageCollector.start();

        verify(gcProvider, times(3)).getBatch();
        List<GCCandidate> allBatches = Stream.concat(firstBatch.stream(), secondBatch.stream()).toList();
        Set<String> sha2s = allBatches.stream().map(GCCandidate::sha2)
                // filter out the first event which failed by the action
                .filter(sha -> !sha.equals(firstBatch.get(0).sha2()))
                .collect(Collectors.toSet());
        ArgumentCaptor<List<String>> argument = ArgumentCaptor.forClass(List.class);
        verify(gcService, times(2)).deleteBlobInfos(argument.capture());
        List<List<String>> capturedBlobInfoDeletionRequests = argument.getAllValues();
        Set<String> capturedShas = capturedBlobInfoDeletionRequests.stream().flatMap(List::stream)
                .collect(Collectors.toSet());
        Assert.assertEquals(sha2s, capturedShas);
    }

    public void testExecuteTask() {
        GCCandidate candidate = new GCCandidate(null, null, "x", null, 0);
        GarbageCollectorInfo gcInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        when(gcProvider.getAction()).thenReturn((gcCandidate, rs) -> true);

        DeletedGCCandidate deletedGCCandidate = garbageCollector.executeTask(gcProvider, candidate, gcInfo);
        Assert.assertEquals(deletedGCCandidate.getDeletedCandidate().sha2(), candidate.sha2());

        verify(securityService, times(1)).authenticateAsSystem();
        verify(gcProvider, times(1)).getAction();
    }

    public void testExecuteTaskWithError() {
        GCCandidate candidate = new GCCandidate(null, null, "x", null, 0);
        GarbageCollectorInfo gcInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);

        doThrow(new RuntimeException("message")).when(gcProvider).getAction();
        DeletedGCCandidate deletedGCCandidate = garbageCollector.executeTask(gcProvider, candidate, gcInfo);
        Assert.assertNull(deletedGCCandidate);
        verify(securityService, times(1)).authenticateAsSystem();
        verify(gcProvider, times(1)).getAction();
    }


    private Stubber mockTwoBatches(List<GCCandidate> firstBatchCandidates, List<GCCandidate> secondBatchCandidates) {
        return doAnswer(new Answer() {
            private int count = 0;

            public Object answer(InvocationOnMock invocation) {
                count++;
                if (count == 1) {
                    return firstBatchCandidates;
                }
                if (count == 2) {
                    return secondBatchCandidates;
                }
                return Collections.emptyList();
            }
        });
    }

    public void testDeleteBlobInfos() {
        List<DeletedGCCandidate> deletedBinaries = Arrays.asList(
                new DeletedGCCandidate(new GCCandidate(null, "1", "x", null, 0)),
                new DeletedGCCandidate(new GCCandidate(null, "2", "", null, 0)),
                new DeletedGCCandidate(new GCCandidate(null, "3", null, null, 0))
        );
        garbageCollector.deleteBlobInfos(deletedBinaries);
        ArgumentCaptor<List<String>> argument = ArgumentCaptor.forClass(List.class);
        verify(gcService, times(1)).deleteBlobInfos(argument.capture());
        List<String> capturedSha2s = argument.getValue();
        Assert.assertEquals(capturedSha2s.size(), 1);
        Assert.assertEquals(capturedSha2s.get(0), deletedBinaries.get(0).getDeletedCandidate().sha2());
    }

    public void testWaitForWorkers() throws InterruptedException {
        List<Future<DeletedGCCandidate>> futures = new ArrayList<>();
        TrashAndBinariesGCProvider gcProviderMockFirst = mock(TrashAndBinariesGCProvider.class);
        when(gcProviderMockFirst.getAction()).thenReturn((candidate, result) -> true);
        GCCandidate gcCandidate = new GCCandidate(null, null, "t", null, 0);
        GarbageCollectorInfo info =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        ThreadPoolExecutor executor = getExecutorService(garbageCollector);
        try {
            futures.add(executor.submit(() -> garbageCollector.executeTask(gcProviderMockFirst, gcCandidate, info)));

            TrashAndBinariesGCProvider gcProviderMockSecond = mock(TrashAndBinariesGCProvider.class);
            when(gcProviderMockSecond.getAction()).thenReturn((candidate, result) -> false);
            GCCandidate gcCandidateTwo = new GCCandidate(null, null, "y", null, 0);
            futures.add(
                    executor.submit(() -> garbageCollector.executeTask(gcProviderMockSecond, gcCandidateTwo, info)));

            TrashAndBinariesGCProvider gcProviderMockThird = spy(
                    new TrashAndBinariesGCProvider(mock(RepositoryService.class), mock(
                            TrashService.class), mock(GCService.class)));
            doThrow(new RuntimeException("test")).when(gcProviderMockThird).actionProvider();
            futures.add(executor.submit(() -> garbageCollector.executeTask(gcProviderMockThird, gcCandidateTwo, info)));
            info.setCandidatesForDeletion(3);
            List<DeletedGCCandidate> resultsDeletedGcCandidates = garbageCollector.waitForWorkers(futures);
            Assert.assertEquals(resultsDeletedGcCandidates.size(), 1);
            Assert.assertEquals(info.getFailures().get(), 1);
            Assert.assertEquals(info.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);
            info.calcStatus();
            Assert.assertEquals(info.getStatus(), GarbageCollectListenerResultStatus.COMPLETED);
            Assert.assertEquals(resultsDeletedGcCandidates.get(0).getDeletedCandidate().sha2(), "t");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }
    }

    public void testMssqlThreads() {
        garbageCollector = new MinorGcCollector(gcProvider, securityService, gcService, DbType.MSSQL);
        Assert.assertEquals(garbageCollector.numberOfThreads, 1);

        Arrays.stream(DbType.values())
                .filter(dbType -> dbType != DbType.MSSQL)
                .forEach(dbType -> {
                    garbageCollector = new MinorGcCollector(gcProvider, securityService, gcService, DbType.MYSQL);
                    Assert.assertEquals(garbageCollector.numberOfThreads,
                            ConstantValues.gcNumberOfWorkersThreads.getInt());
                });
    }

    public void testWaitForWorkersFailed() throws InterruptedException {
        List<Future<DeletedGCCandidate>> futures = new ArrayList<>();
        TrashAndBinariesGCProvider gcProviderMockFirst = mock(TrashAndBinariesGCProvider.class);
        when(gcProviderMockFirst.getAction()).thenReturn((candidate, result) -> true);
        GCCandidate gcCandidate = new GCCandidate(null, null, "t", null, 0);
        GarbageCollectorInfo info =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        garbageCollector.numberOfThreads = 20;
        ThreadPoolExecutor executor = getExecutorService(garbageCollector);
        try {
            futures.add(executor.submit(() -> garbageCollector.executeTask(gcProviderMockFirst, gcCandidate, info)));
            TrashAndBinariesGCProvider gcProviderMockSecond = mock(TrashAndBinariesGCProvider.class);
            when(gcProviderMockSecond.getAction()).thenReturn((candidate, result) -> false);
            GCCandidate gcCandidateTwo = new GCCandidate(null, null, "y", null, 0);
            futures.add(
                    executor.submit(() -> garbageCollector.executeTask(gcProviderMockSecond, gcCandidateTwo, info)));

            TrashAndBinariesGCProvider gcProviderMockThird = spy(
                    new TrashAndBinariesGCProvider(mock(RepositoryService.class), mock(
                            TrashService.class), mock(GCService.class)));
            doThrow(new RuntimeException("test")).when(gcProviderMockThird).actionProvider();

            for (int i = 0; i < 10; i++) {
                futures.add(
                        executor.submit(() -> garbageCollector.executeTask(gcProviderMockThird, gcCandidateTwo, info)));
            }
            info.setCandidatesForDeletion(12);
            List<DeletedGCCandidate> resultsDeletedGcCandidates = garbageCollector.waitForWorkers(futures);
            Assert.assertEquals(resultsDeletedGcCandidates.size(), 1);
            Assert.assertEquals(info.getFailures().get(), 10);
            Assert.assertEquals(info.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);
            Assert.assertEquals(resultsDeletedGcCandidates.get(0).getDeletedCandidate().sha2(), "t");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }
    }

    private ThreadPoolExecutor getExecutorService(MinorGcCollector garbageCollector) {
        return new ThreadPoolExecutor(garbageCollector.numberOfThreads, garbageCollector.numberOfThreads, 5L,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(ConstantValues.trashcanMaxSearchResults.getInt() * 2),
                new ThreadFactoryBuilder().setDaemon(true)
                        .setNameFormat("art-GC-worker-%s")
                        .build());
    }
}