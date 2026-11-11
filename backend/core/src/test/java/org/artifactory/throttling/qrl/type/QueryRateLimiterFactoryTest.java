package org.artifactory.throttling.qrl.type;

import org.artifactory.config.ConfigurationException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.qrlSimulationModeEnabled;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @author Andrey Tashlitsky
 */
class QueryRateLimiterFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void createDisabled() {
        var systemQrl = QueryRateLimiterFactory.create(SYSTEM, new RateLimiterConfig(Set.of()), List.of());
        assertEquals(systemQrl.getClass(), DisabledQueryRateLimiter.class);
    }

    @ParameterizedTest
    @MethodSource("simulationEnabled")
    void createEnabled(boolean enabled, Class<ArtifactoryQueryRateLimiter> clazz) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), String.valueOf(enabled));
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2_000);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, 1_000, 1_000, 500);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        List<ArtifactoryServer> servers = List.of(mock(ArtifactoryServer.class), mock(ArtifactoryServer.class));
        var defaultQrl = QueryRateLimiterFactory.create(DEFAULT, qrlConfig, servers);
        assertEquals(defaultQrl.getClass(), clazz);
    }

    @Test
    void createEnabledException() {
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, 1_000, 1_000, 500);
        var qrlConfig = new RateLimiterConfig(Set.of(lowPrioritySettings));
        List<ArtifactoryServer> servers = List.of(mock(ArtifactoryServer.class), mock(ArtifactoryServer.class));
        assertThrows(ConfigurationException.class, () -> QueryRateLimiterFactory.create(DEFAULT, qrlConfig, servers));
    }

    private static Stream<Arguments> simulationEnabled() {
        return Stream.of(
                Arguments.of(false, EnabledQueryRateLimiter.class),
                Arguments.of(true, SimulationQueryRateLimiter.class)
        );
    }
}
