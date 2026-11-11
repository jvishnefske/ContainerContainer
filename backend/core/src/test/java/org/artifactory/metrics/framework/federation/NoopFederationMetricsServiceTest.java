package org.artifactory.metrics.framework.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Igor Usenko
 */
class NoopFederationMetricsServiceTest {

    @Test
    void noop() {
        assertTrue(new NoopFederationMetricsService().getOpenMetrics(false).isEmpty());
    }
}