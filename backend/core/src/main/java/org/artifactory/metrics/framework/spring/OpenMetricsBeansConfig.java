package org.artifactory.metrics.framework.spring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Definition of beans, creating the Micrometer's Prometheus metrics registry.  Most of the code
 * in this class was copied from the {@link
 * org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration}
 * class.
 */
@Configuration
public class OpenMetricsBeansConfig {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    @Bean
    @ConditionalOnMissingBean(PrometheusConfig.class)
    public PrometheusConfig prometheusConfig() {
        return PrometheusConfig.DEFAULT;
    }

    @Bean
    @ConditionalOnMissingBean(CollectorRegistry.class)
    public CollectorRegistry collectorRegistry() {
        return new CollectorRegistry(true);
    }

    @Bean(name = "appMetricsCollectorRegistry")
    public CollectorRegistry appMetricsCollectorRegistry() {
        return new CollectorRegistry(true);
    }

    @Bean
    @Primary
    public PrometheusMeterRegistry prometheusMeterRegistry(
            PrometheusConfig prometheusConfig,
            CollectorRegistry collectorRegistry,
            Clock clock,
            ObjectProvider<MeterFilter> meterFilters,
            ObjectProvider<MeterBinder> meterBinders) {
        PrometheusMeterRegistry registry =
                new PrometheusMeterRegistry(prometheusConfig, collectorRegistry, clock, null);
        // Spring Boot does the following binding in a more generic way, using a custom bean post-processor,
        // but we do not really need that level of flexibility, so just bind directly:
        meterFilters.orderedStream().forEach(registry.config()::meterFilter);
        meterBinders.orderedStream().forEach(binder -> binder.bindTo(registry));
        Metrics.addRegistry(registry);
        return registry;
    }

    /**
     * Creates a dedicated Prometheus meter registry for application-level metrics. This registry uses a separate
     * collector registry ({@code appMetricsCollectorRegistry}) to isolate application metrics from system-level
     * metrics.
     * Metrics registered to this registry are considered "application metrics" and can be filtered and exposed
     * separately through the {@code /v1/metrics/application} API endpoint.
     * <p>
     * Unlike the primary {@code prometheusMeterRegistry}, this registry is not added to the global {@link Metrics}
     * registry and does not bind {@link MeterBinder}s, allowing for selective metric collection.
     * </p>
     *
     * @param prometheusConfig  The Prometheus configuration for the registry
     * @param collectorRegistry The application metrics collector registry (qualified as "appMetricsCollectorRegistry")
     * @param clock             The clock to use for timing measurements
     * @param meterFilters      Provider of meter filters to apply to the registry
     * @return A PrometheusMeterRegistry configured for application-level metrics
     */
    @Bean(name = "applicationMeterRegistry")
    @SuppressWarnings("deprecation") // RTDEV-66582 should resolve this
    public PrometheusMeterRegistry applicationMeterRegistry(
            PrometheusConfig prometheusConfig,
            @Qualifier("appMetricsCollectorRegistry") CollectorRegistry collectorRegistry,
            Clock clock,
            ObjectProvider<MeterFilter> meterFilters) {
        PrometheusMeterRegistry registry =
                new PrometheusMeterRegistry(prometheusConfig, collectorRegistry, clock, null);

        meterFilters.orderedStream().forEach(registry.config()::meterFilter);
        return registry;
    }

    @Bean
    public MeterFilter commonTagsFilter() {
        return MeterFilter.commonTags(Tags.of("application", "Artifactory"));
    }

    @Bean
    public SpringOpenMetricsWriter springOpenMetricsWriter(CollectorRegistry collectorRegistry,
            @Qualifier("appMetricsCollectorRegistry") CollectorRegistry appMetricsCollectorRegistry) {
        return new SpringOpenMetricsWriter(collectorRegistry, appMetricsCollectorRegistry);
    }
}