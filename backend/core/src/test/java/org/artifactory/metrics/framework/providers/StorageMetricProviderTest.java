package org.artifactory.metrics.framework.providers;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.storage.service.StorageMetric;
import org.artifactory.storage.service.StorageMetricCollector;
import org.jfrog.metrics.model.Metric;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class StorageMetricProviderTest {

    @Mock
    private StorageMetricCollector storageMetricCollector;
    @Mock
    private AddonsManager addonsManagerMock;
    @Mock
    private ArtifactoryContext artifactoryContext;
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeClass() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManagerMock);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void happyFlow() {
        when(storageMetricCollector.get()).thenReturn(new StorageMetric(5));
        when(addonsManagerMock.isEntitled(EntitlementType.COLD_STORAGE)).thenReturn(false);
        StorageMetricProvider storageMetricProvider = new StorageMetricProvider(storageMetricCollector);
        List<Metric> metrics = storageMetricProvider.collect();
        assertEquals(metrics.size(), 1);
        Metric first = metrics.getFirst();
        assertEquals(first.getValue().doubleValue(), 5.0d);
    }

    @Test
    void storageMetricNotReadyFlow() {
        when(storageMetricCollector.get()).thenReturn(new StorageMetric(-1));
        when(addonsManagerMock.isEntitled(EntitlementType.COLD_STORAGE)).thenReturn(false);
        StorageMetricProvider storageMetricProvider = new StorageMetricProvider(storageMetricCollector);
        List<Metric> metrics = storageMetricProvider.collect();
        assertTrue(metrics.isEmpty());
    }

    @Test
    void storageMetricColdStorageFlow() {
        when(addonsManagerMock.isEntitled(EntitlementType.COLD_STORAGE)).thenReturn(true);
        StorageMetricProvider storageMetricProvider = new StorageMetricProvider(storageMetricCollector);
        List<Metric> metrics = storageMetricProvider.collect();
        assertTrue(metrics.isEmpty());
    }

}