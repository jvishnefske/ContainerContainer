package org.artifactory.rest.resource.traffic;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.summary.TrafficSummary;
import org.artifactory.traffic.summary.TrafficSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author kunalm
 */
@ExtendWith(MockitoExtension.class)
public class TrafficResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TrafficSummaryService trafficSummaryService;

    @InjectMocks
    private TrafficResource trafficResource;

    @Test
    public void testGetMonthlyTrafficSummary_WithValidData_ReturnsTrafficSummary() {
        TrafficSummary expectedSummary = createSampleTrafficSummary();
        when(trafficSummaryService.getMonthlyTrafficSummary()).thenReturn(expectedSummary);

        TrafficSummary result = trafficResource.getMonthlyTrafficSummary();

        assertNotNull(result);
        assertEquals(expectedSummary.from(), result.from());
        assertEquals(expectedSummary.to(), result.to());
        assertEquals(expectedSummary.fromDate(), result.fromDate());
        assertEquals(expectedSummary.toDate(), result.toDate());

        assertEquals(expectedSummary.monthlyRequests().total(), result.monthlyRequests().total());
        assertEquals(expectedSummary.monthlyRequests().downloads(), result.monthlyRequests().downloads());
        assertEquals(expectedSummary.monthlyRequests().uploads(), result.monthlyRequests().uploads());

        assertEquals(expectedSummary.monthlyTransferBytes().total(), result.monthlyTransferBytes().total());
        assertEquals(expectedSummary.monthlyTransferBytes().downloads(), result.monthlyTransferBytes().downloads());
        assertEquals(expectedSummary.monthlyTransferBytes().uploads(), result.monthlyTransferBytes().uploads());

        assertEquals(expectedSummary.hourlySnapshots().size(), result.hourlySnapshots().size());

        verify(trafficSummaryService, times(1)).getMonthlyTrafficSummary();
    }

    private TrafficSummary createSampleTrafficSummary() {
        return TrafficSummary.builder()
                .from(1640995200000L)
                .fromDate("2022-01-01 00:00 UTC")
                .to(1643673600000L)
                .toDate("2022-01-31 23:59 UTC")
                .monthlyRequests(TrafficSummary.MonthlyRequests.builder()
                        .total(1000)
                        .downloads(600)
                        .uploads(400)
                        .build())
                .monthlyTransferBytes(TrafficSummary.MonthlyTransferBytes.builder()
                        .total(1024000)
                        .downloads(614400)
                        .uploads(409600)
                        .build())
                .hourlySnapshots(List.of(
                        TrafficSummary.HourlySnapshot.builder()
                                .time(1640995200000L)
                                .totalRequests(50)
                                .downloadRequests(30)
                                .uploadRequests(20)
                                .totalBytes(51200)
                                .downloadBytes(30720)
                                .uploadBytes(20480)
                                .build()
                ))
                .build();
    }
}
