package org.artifactory.storage.db.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({ SpringExtension.class })
@ContextConfiguration(classes = { DbQueryMonitorConfigTest.Config.class, DbQueryMonitorImpl.class })
@TestPropertySource(properties = {
        "artifactory.metrics.enabled=true",
        "artifactory.query-monitor.metrics.enabled=false",
        "artifactory.query-monitor.cache.maxEntries=12345",
        "jfrog.slow.queries.threshold.ms=7842"
})
class DbQueryMonitorConfigTest {
    @Autowired
    DbQueryMonitorImpl monitor;

    @Test
    void configurationShouldAffectMonitor() {
        boolean actualMetricsEnabled = monitor.isReportMetricsEnabled();
        assertFalse(actualMetricsEnabled,
                "artifactory.query-monitor.metrics.enabled=false => metrics should be disabled");

        String actualCluster = monitor.getDatabaseCluster();
        assertEquals("localhost", actualCluster);

        long slowQueryThreshold = monitor.getSlowQueryThreshold();
        assertEquals(7842 * 1000000L, slowQueryThreshold);
    }

    static class Config {
        @Bean
        public MeterRegistry defaultMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public ArtifactoryDbProperties defaultDbProperties() {
            ArtifactoryDbProperties config = mock(ArtifactoryDbProperties.class);
            when(config.getDbType()).thenReturn(DbType.POSTGRESQL);
            when(config.getConnectionUrl()).thenReturn("jdbc:postgresql://localhost:5432/customer_ja");
            return config;
        }
    }
}
