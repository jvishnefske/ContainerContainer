package org.artifactory.rest.resource.system;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

@Test
public class MetricsResourceTest {
    private MetricsResource metricsResource;

    @Mock
    private AolService aolService;

    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
        metricsResource = new MetricsResource();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void testGetDbMetricsUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.getDbMetrics().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testEnableDbMetricsUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.enableDbMetrics().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testDisableDbMetricsUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.disableDbMetrics().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testResetDbMetricsUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.resetDbMetrics().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testGetSqlMetricsUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.getSqlMetrics().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testEnableSqlTracingUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.enableSqlTracing().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testDisableSqlTracingUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.disableSqlTracing().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    public void testResetSqlTracingUnauthorized() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        assertEquals(metricsResource.resetSqlTracing().getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }
}