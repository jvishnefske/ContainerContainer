package org.artifactory.security;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link AuthorizationServiceMetricsConfiguration}
 *
 * @author Andrey Tashlitsky
 */
class AuthorizationServiceMetricsConfigurationTest {

    private MeterRegistry meterRegistry;

    // Fork: the gauge holds the configuration weakly; keep it reachable, or a GC before gauge.value() makes it NaN.
    private AuthorizationServiceMetricsConfiguration config;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void registerMetrics_shouldRegisterGaugeWithLegacyType_whenLegacyIsConfigured() {
        // Arrange & Act
        config = new AuthorizationServiceMetricsConfiguration(meterRegistry, "legacy", 0.25f);

        // Assert
        Gauge gauge = meterRegistry.find("authorization.service.info").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value());
        assertEquals("legacy", gauge.getId().getTag("type"));
        assertEquals("0.25", gauge.getId().getTag("shadow_probability"));
    }

    @Test
    void registerMetrics_shouldRegisterGaugeWithAccessType_whenAccessIsConfigured() {
        // Arrange & Act
        config = new AuthorizationServiceMetricsConfiguration(meterRegistry, "access", 0.15f);

        // Assert
        Gauge gauge = meterRegistry.find("authorization.service.info").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value());
        assertEquals("access", gauge.getId().getTag("type"));
        assertEquals("0.15", gauge.getId().getTag("shadow_probability"));
    }

    @Test
    void registerMetrics_shouldRegisterGaugeWithShadowType_whenShadowIsConfigured() {
        // Arrange & Act
        config = new AuthorizationServiceMetricsConfiguration(meterRegistry, "shadow", 0.5f);

        // Assert
        Gauge gauge = meterRegistry.find("authorization.service.info").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value());
        assertEquals("shadow", gauge.getId().getTag("type"));
        assertEquals("0.5", gauge.getId().getTag("shadow_probability"));
    }

    @Test
    void registerMetrics_shouldDefaultToLegacy_whenPropertyIsEmpty() {
        // Arrange & Act
        config = new AuthorizationServiceMetricsConfiguration(meterRegistry, "", 0.1f);

        // Assert
        Gauge gauge = meterRegistry.find("authorization.service.info").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value());
        assertEquals("legacy", gauge.getId().getTag("type"));
        assertEquals("0.1", gauge.getId().getTag("shadow_probability"));
    }

    @Test
    void registerMetrics_shouldDefaultToLegacy_whenPropertyIsInvalid() {
        // Arrange & Act
        config = new AuthorizationServiceMetricsConfiguration(meterRegistry, "invalid-value", 0.2f);

        // Assert
        Gauge gauge = meterRegistry.find("authorization.service.info").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value());
        assertEquals("legacy", gauge.getId().getTag("type"));
        assertEquals("0.2", gauge.getId().getTag("shadow_probability"));
    }
}
