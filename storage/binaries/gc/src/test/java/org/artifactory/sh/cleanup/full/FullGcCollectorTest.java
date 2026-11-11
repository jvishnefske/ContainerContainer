package org.artifactory.sh.cleanup.full;

import org.artifactory.common.ConstantValues;
import org.artifactory.sh.cleanup.GCServiceImpl;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.StorageException;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.never;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
class FullGcCollectorTest extends ArtifactoryHomeBoundJUnitTest {

    private FullGcCollector fullGcCollector;

    @Mock
    GCServiceImpl gcService;
    @Mock
    StorageService storageService;
    @Mock
    BinariesGcDao binariesGcDao;
    private AutoCloseable mockitoClosable;

    @BeforeEach
    public void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        homeStub.setProperty(ConstantValues.gcFailCountThreshold, "10");
        homeStub.setProperty(ConstantValues.gcNumberOfWorkersThreads, "10");
        fullGcCollector = spy(new FullGcCollector(gcService, storageService, binariesGcDao));
    }

    @AfterEach
    public void close() throws Exception {
        this.mockitoClosable.close();
    }

    @Test
    public void testFullGcHappyFlowSingleCycle() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(
                        List.of(new BinariesGcDao.BinaryEntity("sha1", "sha2", "md5", 4)))
                .thenReturn(List.of());

        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    ((GarbageCollectorInfo) i.getArguments()[1]).setBinariesCleaned(new AtomicInteger(1));
                    return true;
                });
        fullGcCollector.start();
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testFullGcHappyFlow() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(
                        List.of(new BinariesGcDao.BinaryEntity("sha1", "sha2", "md5", 4)))
                .thenReturn(List.of(new BinariesGcDao.BinaryEntity("sha1new", "sha2new", "md5new", 4)))
                .thenReturn(List.of());

        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    ((GarbageCollectorInfo) i.getArguments()[1]).setBinariesCleaned(new AtomicInteger(1));
                    return true;
                });
        fullGcCollector.start();
        verify(gcService, times(2)).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testFullGcHappyFlowWithoutOptimize() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(
                        List.of(new BinariesGcDao.BinaryEntity("sha1", "sha2", "md5", 4)))
                .thenReturn(List.of(new BinariesGcDao.BinaryEntity("sha1new", "sha2new", "md5new", 4)))
                .thenReturn(List.of());


        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    ((GarbageCollectorInfo) i.getArguments()[1]).setBinariesCleaned(new AtomicInteger(1));
                    return true;
                });
        ReflectionTestUtils.setField(fullGcCollector, "gcCheckBalanceAfterFullGc", false);
        fullGcCollector.start();
        verify(gcService, times(2)).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testFullGcHappyFlowNoResults() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(List.of());
        fullGcCollector.start();
        verify(gcService, never()).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testFullGcThreads() throws SQLException {
        List<BinariesGcDao.BinaryEntity> gcElements = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            gcElements.add(new BinariesGcDao.BinaryEntity("thsha1_" + i, "sha2", "md5", 4));
        }
        Set<String> threadNames = new HashSet<>();
        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    threadNames.add(Thread.currentThread().getName());
                    ((GarbageCollectorInfo) i.getArguments()[1]).setBinariesCleaned(new AtomicInteger(1));
                    return true;
                });
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(gcElements);
        final GarbageCollectorInfo result = fullGcCollector.start();
        assertNotNull(result);
        assertTrue(threadNames.size() > 1);
    }

    @Test
    public void testFullGcMaxFailures() throws SQLException {
        List<BinariesGcDao.BinaryEntity> gcElements = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            gcElements.add(new BinariesGcDao.BinaryEntity("sha1_" + i, "sha2", "md5", 4));
        }
        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any())).thenThrow(new RuntimeException("Test"));
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(gcElements);
        final GarbageCollectorInfo result = fullGcCollector.start();
        // gcFailCountThreshold is 10, but because of 10 threads are working, the result can be between 10-19
        assertTrue(result.getFailures().get() >= 10);
        assertTrue(result.getFailures().get() <= 19);
        verify(gcService, atLeast(10)).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(gcService, atMost(19)).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testIterationCount() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(
                List.of(new BinariesGcDao.BinaryEntity("sha1", "sha2", "md5", 4)));
        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    i.getArgument(1, GarbageCollectorInfo.class).getBinariesCleaned().incrementAndGet();
                    return true;
                });
        final GarbageCollectorInfo result = fullGcCollector.start();
        assertTrue(result.isInterruptedByMaxNumberOfIterations());
        verify(gcService, times(50)).executeBinaryCleaner(any(BinaryElement.class), any());
        verify(storageService, never()).callManualShardingBalancer(any());
    }

    @Test
    public void testFailureFindPotentialDeletion() throws SQLException {
        when(binariesGcDao.findPotentialDeletion(any())).thenThrow(new SQLException("test"));
        Assertions.assertThrows(StorageException.class, () -> fullGcCollector.start());
    }

    @Test
    public void testStopByIterationCount() throws SQLException {
        homeStub.setProperty(ConstantValues.gcBinariesToDeleteIterationAmount, "2");
        when(binariesGcDao.findPotentialDeletion(any())).thenReturn(
                        List.of(new BinariesGcDao.BinaryEntity("sha1", "sha2", "md5", 4)))
                .thenReturn(List.of(new BinariesGcDao.BinaryEntity("sha1new", "sha2new", "md5new", 4)))
                .thenReturn(List.of(new BinariesGcDao.BinaryEntity("sha1newnew", "sha2newnew", "md5newnew", 4)))
                .thenReturn(List.of());

        when(gcService.executeBinaryCleaner(any(BinaryElement.class), any(GarbageCollectorInfo.class)))
                .thenAnswer(i -> {
                    final GarbageCollectorInfo argument = (GarbageCollectorInfo) i.getArguments()[1];
                    argument.getBinariesCleaned().incrementAndGet();
                    final long length = ((BinaryElement) i.getArguments()[0]).getLength();
                    argument.getTotalSizeCleaned()
                            .addAndGet(length);
                    return true;
                });
        final FullGcCollector fullGcCollector1 = new FullGcCollector(gcService, storageService, binariesGcDao);
        final GarbageCollectorInfo result = fullGcCollector1.start();
        Assertions.assertEquals(2, result.getBinariesCleaned().get());
        Assertions.assertEquals(8, result.getTotalSizeCleaned().get());
    }
}