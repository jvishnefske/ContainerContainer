package org.artifactory.rest.servlet;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Manager;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.ServletContext;
import java.util.List;

/**
 * This class exposes Tomcat metrics to Prometheus, using ready-to-use Micrometer's metrics binder.
 * Micrometer needs {@link org.apache.catalina.Manager} object to monitor Tomcat's sessions. Getting this object
 * is kind of tricky - see the {@link #findManager} method. Note: even if findManager will stop working, Micrometer
 * will still be useful, it will continue reporting other Tomcat metrics, which it will get via MBean interfaces.
 *
 * @author Sergey Steinvil
 */
@Configuration
@Slf4j
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
public class TomcatMetricsConfiguration {
    @Bean
    public AutoCloseable defaultTomcatMetrics(MeterRegistry registry, WebApplicationContext webContext) {
        Manager tomcatManager = findManager(webContext.getServletContext());
        if (tomcatManager == null) {
            log.warn("Tomcat session metrics will not be available");
        }

        TomcatMetrics tomcatMetrics = new TomcatMetrics(tomcatManager, List.of());
        tomcatMetrics.bindTo(registry);
        return tomcatMetrics;
    }

    static Manager findManager(ServletContext servletContext) {
        if (servletContext != null &&
                servletContext.getAttribute("org.apache.catalina.resources") instanceof WebResourceRoot root &&
                root.getContext() instanceof StandardContext stdContext) {
            return stdContext.getManager();
        }
        return null;
    }
}
