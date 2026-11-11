package org.artifactory.metrics.framework.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jersey.server.AnnotationFinder;
import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.JerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.spring.customizers.ResourceConfigCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Creates Micrometer metric beans, instrumenting JAX-RS (REST API calls, processed by the Jersey server).  This
 * instrumentation defines and registers Jersey interceptor, which will moniotor REST API calls and generate
 * three metrics: <b>http_server_requests_seconds_sum</b>, <b>http_server_requests_seconds_count</b>,
 * and <b>http_server_requests_seconds_max</b>.
 * <p>
 *     Note: Most of this code was copied from the
 *     {@link org.springframework.boot.actuate.autoconfigure.metrics.jersey.JerseyServerMetricsAutoConfiguration} class.
 * </p>
 */
@Configuration
@Slf4j
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
public class JerseyServerMetricsConfiguration {
    static final String METRIC_NAME = "http.server.requests";

    @Bean
    @ConditionalOnMissingBean(JerseyTagsProvider.class)
    public DefaultJerseyTagsProvider jerseyTagsProvider() {
        return new DefaultJerseyTagsProvider();
    }

    @Bean
    @ConditionalOnProperty(value="artifactory.metrics.http-server.enabled", havingValue="true")
    public ResourceConfigCustomizer jerseyServerMetricsResourceConfigCustomizer(
            MeterRegistry meterRegistry,
            JerseyTagsProvider tagsProvider) {
        return config -> config.register(new MetricsApplicationEventListener(meterRegistry, tagsProvider,
                METRIC_NAME, true, AnnotationFinder.DEFAULT)
        );
    }

    /**
     * Limit the number of URI tags in the http_server_requests_XXX metrics.
     * @param maxUriTags The maximal number of URI tags to keep. Spring Boot usually limits it to 100, we allow 200.
     * @return The {@link MeterFilter} that will limit the maximal number of "uri" tags.
     */
    @Bean
    @Order(0)
    public MeterFilter jerseyMetricsUriTagFilter(
            @Value("${management.metrics.web.server.max-uri-tags:200}") int maxUriTags) {
        log.info("Spring will limit the 'uri' tag for the {} metric to {} values.", METRIC_NAME, maxUriTags);
        // Note: Spring Boot usually writes a warning to logs when the limit is reached. We will probably add
        // this feature later.
        return MeterFilter.maximumAllowableTags(METRIC_NAME, "uri", maxUriTags, MeterFilter.deny());
    }
}
