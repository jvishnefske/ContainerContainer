package org.artifactory.metrics.framework.spring;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates Micrometer metric beans, monitoring the amount of log messages that Artifactory generates (via Logback).
 * Produces the <b>logback_events_total</b> metric (with level tag).
 * <p>
 *     Note: Most of this code was copied from the
 *     {@link org.springframework.boot.actuate.autoconfigure.metrics.LogbackMetricsAutoConfiguration} class.
 * </p>
 */
@Configuration
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
public class LogbackMetricsConfiguration {
    @Bean
    @ConditionalOnMissingBean(LogbackMetrics.class)
    public LogbackMetrics logbackMetrics() {
        return new LogbackMetrics();
    }
}
