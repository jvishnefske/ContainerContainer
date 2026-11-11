package org.artifactory.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.artifactory.permissions.PermissionsCacheType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration component for registering authorization service metrics.
 * Registers an info-style metric that reports the active authorization cache type
 * and shadow testing probability configuration.
 *
 * @author Andrey Tashlitsky
 */
@Component
public class AuthorizationServiceMetricsConfiguration {

    public AuthorizationServiceMetricsConfiguration(
            MeterRegistry meterRegistry,
            @Value("${artifactory.permissions.cache.type}") String permissionsCacheType,
            @Value("${artifactory.permissions.cache.shadow.probability}") float shadowProbability
    ) {
        meterRegistry.gauge(
                "authorization.service.info",
                Tags.of(
                        "type", PermissionsCacheType.fromName(permissionsCacheType).name().toLowerCase(),
                        "shadow_probability", String.valueOf(shadowProbability)
                ),
                this,
                obj -> 1.0
        );
    }
}
