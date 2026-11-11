package org.artifactory.metrics.framework.spring;

import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates Micrometer metric beans, monitoring various system properties: the uptime, the number of processors, etc.
 * Produces the <b>logback_events_total</b> metric (with level tag).
 * <p>
 *     Note: Most of this code was copied from the
 *     {@link org.springframework.boot.actuate.autoconfigure.metrics.LogbackMetricsAutoConfiguration} class.
 * </p>
 */
@Configuration
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
public class SystemMetricsConfiguration {
    @Bean
    @ConditionalOnMissingBean(UptimeMetrics.class)
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessorMetrics.class)
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(FileDescriptorMetrics.class)
    public FileDescriptorMetrics fileDescriptorMetrics() {
        return new FileDescriptorMetrics();
    }
}
