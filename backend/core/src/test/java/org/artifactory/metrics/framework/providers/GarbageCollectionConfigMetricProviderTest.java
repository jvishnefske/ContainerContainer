package org.artifactory.metrics.framework.providers;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.mockito.Mock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class GarbageCollectionConfigMetricProviderTest {

    @Mock
    CentralConfigService centralConfigService;
    GarbageCollectionConfigMetricProvider gcProvider;

    @BeforeClass
    public void setup() throws IOException {
        centralConfigService = mock(CentralConfigService.class);
        gcProvider = new GarbageCollectionConfigMetricProvider(centralConfigService);
    }


    @Test
    public void testGetName() {
        assertEquals(gcProvider.getName(), "jfrt_artifacts");
    }

    @Test
    public void testGetDescription() {
        assertEquals(gcProvider.getDescription(), "Artifactory Garbage Collection Config Metric Provider");
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        assertEquals(gcProvider.getRefreshIntervalMillis(), 60000);
    }

    @Test
    public void testGetReportType() {
        assertEquals(gcProvider.getReportType(), MetricProvider.ReportType.DEDUPED);
    }

    @Test
    public void testCollectGCRunConfiguration() {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        GcConfigDescriptor gcConfigDescriptor = mock(GcConfigDescriptor.class);

        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getGcConfig()).thenReturn(gcConfigDescriptor);
        when(gcConfigDescriptor.getCronExp()).thenReturn("0 0 /6 * * ?");
        List<Metric> metrics = gcProvider.collect();
        assertEquals(metrics.size(), 1);
    }
}