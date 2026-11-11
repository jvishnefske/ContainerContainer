package org.artifactory.metrics.framework.spring;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates Micrometer metric beans, providing many "standard" metrics about the Java Virtual Machine:
 * JVM memory, heap pressure, garbage collection statistics, class loading metrics, and much more.
 * <p>
 *     Note: Most of this code was copied from the
 *     {@link org.springframework.boot.actuate.autoconfigure.metrics.JvmMetricsAutoConfiguration} class.
 * </p>
 */
@Configuration
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
public class JvmMetricsConfiguration {
    @Bean
    @ConditionalOnMissingBean(JvmGcMetrics.class)
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmHeapPressureMetrics.class)
    public JvmHeapPressureMetrics jvmHeapPressureMetrics() {
        return new JvmHeapPressureMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmMemoryMetrics.class)
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmThreadMetrics.class)
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(ClassLoaderMetrics.class)
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmInfoMetrics.class)
    public JvmInfoMetrics jvmInfoMetrics() {
        return new JvmInfoMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(JvmCompilationMetrics.class)
    public JvmCompilationMetrics jvmCompilationMetrics() {
        return new JvmCompilationMetrics();
    }
}
