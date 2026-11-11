package org.artifactory.metrics.framework;

import org.artifactory.metrics.framework.federation.FederationMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
@ExtendWith({MockitoExtension.class})
class MetricsFacadeTest {

    @Mock
    private FederationMetricsService federationMetricsService;
    @Mock
    private MetricsService metricsService;
    @Mock
    private LastUsageMonitor lastUsageMonitor;

    private MetricsFacade facade;

    @BeforeEach
    void setUp() {
        facade = new MetricsFacade(federationMetricsService, metricsService, lastUsageMonitor);
    }

    @Test
    void metricsCollection() {
        when(metricsService.getOpenMetrics(false)).thenReturn("metrics-false");
        when(federationMetricsService.getOpenMetrics(false)).thenReturn("federationMetrics-false");
        String metrics = facade.getAllOpenMetrics();
        assertEquals("metrics-falsefederationMetrics-false", metrics);

        when(metricsService.getOpenMetrics(true)).thenReturn("metrics-true");
        when(federationMetricsService.getOpenMetrics(true)).thenReturn("federationMetrics-true");
        String appMetrics = facade.getApplicationOpenMetrics();
        assertEquals("metrics-truefederationMetrics-true", appMetrics);
    }

    @Test
    void emptyMetricsNotRegistered() {
        when(metricsService.getOpenMetrics(false)).thenReturn("");
        when(federationMetricsService.getOpenMetrics(false)).thenReturn("");

        facade.getAllOpenMetrics();
        verify(lastUsageMonitor, never()).register();
    }

    @Test
    void notEmptyMetricsRegistered() {
        when(metricsService.getOpenMetrics(false)).thenReturn("a");
        when(federationMetricsService.getOpenMetrics(false)).thenReturn("b");

        facade.getAllOpenMetrics();
        verify(lastUsageMonitor).register();
    }

    @Test
    void emptyAppMetricsNotRegistered() {
        when(metricsService.getOpenMetrics(true)).thenReturn("");
        when(federationMetricsService.getOpenMetrics(true)).thenReturn("");

        facade.getApplicationOpenMetrics();
        verify(lastUsageMonitor, never()).register();
    }

    @Test
    void notEmptyAppMetricsNotRegistered() {
        when(metricsService.getOpenMetrics(true)).thenReturn("a");
        when(federationMetricsService.getOpenMetrics(true)).thenReturn("b");

        facade.getApplicationOpenMetrics();
        verify(lastUsageMonitor, never()).register();
    }

    @Test
    void usageCheck() {
        facade.checkMetricsLastUsage();
        verify(lastUsageMonitor).check();
    }
}