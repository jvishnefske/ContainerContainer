package org.artifactory.rest.servlet;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.ServletContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TomcatMetricsConfigurationTest {
    @Test
    void tomcatSessionMetricsShouldExistWhenCatalinaManagerIsAvailable() {
        Manager managerMock = mock(Manager.class);

        StandardContext stdContextMock = mock(StandardContext.class);
        when(stdContextMock.getManager()).thenReturn(managerMock);
        when(stdContextMock.getDomain()).thenReturn("localhost");

        when(managerMock.getContext()).thenReturn(stdContextMock);

        WebResourceRoot webResourceRoot = mock(WebResourceRoot.class);
        when(webResourceRoot.getContext()).thenReturn(stdContextMock);

        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute("org.apache.catalina.resources")).thenReturn(webResourceRoot);

        WebApplicationContext contextMock = mock(WebApplicationContext.class);
        when(contextMock.getServletContext()).thenReturn(servletContext);

        MeterRegistry registry = new SimpleMeterRegistry();

        TomcatMetricsConfiguration configuration = new TomcatMetricsConfiguration();
        try (TomcatMetrics ignored = (TomcatMetrics)configuration.defaultTomcatMetrics(registry, contextMock)) {
            Gauge metric = registry.get("tomcat.sessions.active.max").gauge();
            assertNotNull(metric, "TomcatMetrics supposed to register 'tomcat.sessions.active.max' gauge");
        }
    }

    @Test
    void tomcatSessionMetricsShouldNotExistWhenCatalinaManagerIsNotAvailable() {
        WebApplicationContext contextMock = mock(WebApplicationContext.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        TomcatMetricsConfiguration configuration = new TomcatMetricsConfiguration();
        try (TomcatMetrics actual = (TomcatMetrics)configuration.defaultTomcatMetrics(registry, contextMock)) {
            assertNotNull(actual, "TomcatMetrics should be created even when no Manager is available");
            assertNull(registry.find("tomcat.sessions.active.max").meter(),
                    "TomcatMetrics should not create 'tomcat.sessions.active.max' gauge if manager is null");
        }
    }

    @Test
    void tomcatMetricsShouldBind() {
        Manager managerMock = mock(Manager.class);

        StandardContext stdContextMock = mock(StandardContext.class);
        when(stdContextMock.getManager()).thenReturn(managerMock);
        when(stdContextMock.getDomain()).thenReturn("localhost");

        when(managerMock.getContext()).thenReturn(stdContextMock);

        WebResourceRoot webResourceRoot = mock(WebResourceRoot.class);
        when(webResourceRoot.getContext()).thenReturn(stdContextMock);

        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getAttribute("org.apache.catalina.resources")).thenReturn(webResourceRoot);

        WebApplicationContext contextMock = mock(WebApplicationContext.class);
        when(contextMock.getServletContext()).thenReturn(servletContext);

        MeterRegistry registry = new SimpleMeterRegistry();

        TomcatMetricsConfiguration configuration = new TomcatMetricsConfiguration();
        try (TomcatMetrics ignored = (TomcatMetrics)configuration.defaultTomcatMetrics(registry, contextMock)) {
            Gauge metric = registry.find("tomcat.sessions.active.max").gauge();
            assertNotNull(metric, "TomcatMetrics supposed to register 'tomcat.sessions.active.max' gauge");
        }
    }

    @Test
    void findManager_shouldReturnNull_whenContextHasNoResourcesAttribute() {
        Manager actual = TomcatMetricsConfiguration.findManager(mock(ServletContext.class));
        assertNull(actual);
    }

    @Test
    void findManager_shouldReturnNull_whenResourcesAttributeHasWrongType() {
        ServletContext contextMock = mock(ServletContext.class);
        when(contextMock.getAttribute("org.apache.catalina.resources")).thenReturn(new Object());

        Manager actual = TomcatMetricsConfiguration.findManager(contextMock);
        assertNull(actual);
    }

    @Test
    void findManager_shouldReturnNull_whenResourcesContextIsNotStandard() {
        WebResourceRoot webResourceRoot = mock(WebResourceRoot.class);
        when(webResourceRoot.getContext()).thenReturn(mock(Context.class));

        ServletContext contextMock = mock(ServletContext.class);
        when(contextMock.getAttribute("org.apache.catalina.resources")).thenReturn(webResourceRoot);

        Manager actual = TomcatMetricsConfiguration.findManager(mock(ServletContext.class));
        assertNull(actual);
    }
}