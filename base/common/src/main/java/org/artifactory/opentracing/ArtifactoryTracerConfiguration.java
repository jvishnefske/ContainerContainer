package org.artifactory.opentracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Uriah Levy
 */
@Configuration
@Slf4j
public class ArtifactoryTracerConfiguration {
    /**
     * Exposes a default OpenTelemetry tracer bean for Artifactory.
     * Note: SpringBoot services will get similar bean "for free", just by adding the OpenTelemetry dependency.
     * @param openTelemetry the OpenTelemetry instance to use for creating the tracer
     * @return a Tracer instance for Artifactory
     */
    @Bean
    public Tracer defaultTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("org.artifactory");
    }

    /**
     * Exposes a default OpenTelemetry instance for Artifactory.
     * Note: SpringBoot services will get similar bean "for free", just by adding the OpenTelemetry dependency.
     * @return an OpenTelemetry instance for Artifactory
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetry(ObjectProvider<SdkTracerProvider> tracerProvider,
                                          ObjectProvider<ContextPropagators> propagators,
                                          ObjectProvider<SdkLoggerProvider> loggerProvider,
                                          ObjectProvider<SdkMeterProvider> meterProvider) {
        if (isOpenTelemetryAgentActive()) {
            log.info("OpenTelemetry Java agent is active. Using GlobalOpenTelemetry instance.");
            return GlobalOpenTelemetry.get();
        }

        log.info("OpenTelemetry Java agent is not active. Creating a new OpenTelemetrySdk instance.");
        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder();
        tracerProvider.ifAvailable(builder::setTracerProvider);
        propagators.ifAvailable(builder::setPropagators);
        loggerProvider.ifAvailable(builder::setLoggerProvider);
        meterProvider.ifAvailable(builder::setMeterProvider);

        return builder.build(); // We supposed to use buildAndRegisterGlobal() but for some reason it fails in tests.
    }

    public static boolean isOpenTelemetryAgentActive() {
        try {
            // This is the main class of the OpenTelemetry Java Agent
            Class.forName("io.opentelemetry.javaagent.OpenTelemetryAgent");
            return true;
        } catch (ClassNotFoundException e) {
            // The class was not found, meaning the agent is likely not active
            return false;
        }
    }

    /**
     * TODO: Move to a shared test module (after fixing ArtifactoryHomeConfigListener.getArtifactoryTracer()).
     * <p>
     * This tracer exists just because prior to the migration to OpenTelemetry,
     * Artifactory's production code had a static method, creating a new tracer.
     * This method was used only in tests.  This function is a replacement for
     * that static method.
     * <p>
     * In the near future we will either remove this method completely
     * or move it to a shared test module.
     *
     * @return a Tracer instance for testing
     */
    public static Tracer testTracer() {
        if (isOpenTelemetryAgentActive()) {
            log.info("OpenTelemetry Java agent is active. Using GlobalOpenTelemetry instance for testing.");
            return GlobalOpenTelemetry.get().getTracer("org.artifactory");
        }
        return SdkTracerProvider.builder()
                .build()
                .tracerBuilder("org.artifactory")
                .build();
    }
}