package org.artifactory.traffic;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.traffic.summary.dao.TrafficSummaryDao;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.summary.TrafficSummary;
import org.artifactory.traffic.summary.TrafficSummaryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author kunalm
 */
public class TrafficSummaryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private TaskService taskService;
    @Mock
    private TrafficSummaryDao trafficSummaryDao;

    private MeterRegistry meterRegistry;
    private TrafficSummaryServiceImpl trafficSummaryService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void beforeEach() {
        autoCloseable = openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        doReturn("task-id-1").when(taskService).startTask(any(), anyBoolean());
        doNothing().when(trafficSummaryDao).insert(any());
        doNothing().when(trafficSummaryDao).deleteByThresholdTime(anyLong());
        trafficSummaryService =
                new TrafficSummaryServiceImpl(meterRegistry, taskService, trafficSummaryDao);
    }

    @AfterEach
    void afterEach() throws Exception {
        autoCloseable.close();
    }

    @Test
    void TestBeanCreationShouldConfigureCollectionJobWithMetersInitialized() {
        verify(taskService).startTask(any(), anyBoolean());
        assertEquals(4, meterRegistry.getMeters().size());
    }

    @Test
    void TestCollectTrafficEntryWithNullEntry() {
        trafficSummaryService.collectTrafficEntry(null);
        assertNull(trafficSummaryService.getMonthlyTrafficSummary().hourlySnapshots());
    }

    @Test
    void TestEmptySummaryBeforeSavingSnapshots() {
        TrafficEntry downloadEntry = createDownloadTrafficEntry();
        TrafficEntry uploadEntry = createUploadTrafficEntry();
        trafficSummaryService.collectTrafficEntry(downloadEntry);
        trafficSummaryService.collectTrafficEntry(uploadEntry);
        assertNull(trafficSummaryService.getMonthlyTrafficSummary().hourlySnapshots());
    }

    @Test
    void TestSaveAggregatedTrafficSnapshotWithNoSnapshot() {
        trafficSummaryService.saveAggregatedTrafficSnapshot();
        verify(trafficSummaryDao, never()).insert(any());
        verify(trafficSummaryDao, never()).deleteByThresholdTime(anyLong());
    }

    @Test
    void TestSaveAggregatedTrafficSnapshotWithData() {
        TrafficEntry downloadEntry = createDownloadTrafficEntry();
        trafficSummaryService.collectTrafficEntry(downloadEntry);
        trafficSummaryService.saveAggregatedTrafficSnapshot();
        verify(trafficSummaryDao).insert(any());
        verify(trafficSummaryDao).deleteByThresholdTime(anyLong());
        assertNull(trafficSummaryService.getMonthlyTrafficSummary().hourlySnapshots());
    }

    @Test
    void TestTrafficSummaryDtoFromTrafficSummaryEntities() {
        long from = 1757311128616L;
        long to = 1759903128616L;

        List<TrafficSummaryEntity> entities = List.of(createTrafficEntry(), createTrafficEntry());
        TrafficSummary summary = TrafficSummary.fromTrafficSummaryEntities(entities, from, to);
        assertEquals("2025-09-08 05:58 UTC", summary.fromDate());
        assertEquals("2025-10-08 05:58 UTC", summary.toDate());
        assertEquals(4, summary.monthlyRequests().total());
        assertEquals(2, summary.monthlyRequests().downloads());
        assertEquals(2, summary.monthlyRequests().uploads());
        assertEquals(4000, summary.monthlyTransferBytes().total());
        assertEquals(2000, summary.monthlyTransferBytes().downloads());
        assertEquals(2000, summary.monthlyTransferBytes().uploads());
    }

    private TrafficEntry createDownloadTrafficEntry() {
        return TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .transferredSize(1000L)
                .build();
    }

    private TrafficEntry createUploadTrafficEntry() {
        return TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.UPLOAD)
                .transferredSize(1000L)
                .build();
    }

    private TrafficSummaryEntity createTrafficEntry() {
        return TrafficSummaryEntity.builder()
                .time(1759902128616L)
                .totalRequests(2L)
                .downloadRequests(1L)
                .uploadRequests(1L)
                .totalBytes(2000L)
                .downloadBytes(1000L)
                .uploadBytes(1000L)
                .build();
    }
}
