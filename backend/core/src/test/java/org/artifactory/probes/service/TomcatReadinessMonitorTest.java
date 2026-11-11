package org.artifactory.probes.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.probes.tomcat.ConnectorMetric;
import org.artifactory.probes.tomcat.ConnectorsMonitor;
import org.artifactory.probes.tomcat.TomcatConnectorsMonitor;
import org.artifactory.probes.tomcat.TomcatReadinessMonitor;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.throttling.lh.model.ActivityState;
import org.artifactory.throttling.lh.service.LoadHealerReactiveModeMonitorService;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.testng.collections.Sets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
class TomcatReadinessMonitorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final int MAX_THREADS = 5;
    private static final int REACTIVE_MODE_FAILED_COUNT = 3;
    private static final int MAX_FAILED_METRICS_COUNT = 6;
    private static final int MIN_PROCESSED_REQUESTS_COUNT = 3;
    private static final String FIRST_CONNECTOR_NAME = "first";
    private static final String SECOND_CONNECTOR_NAME = "second";
    private static final String HTTP_NIO_CONNECTOR_NAME = "\"http-nio-127.0.0.1-8081\"";
    private static final String HTTPS_JSSE_NIO_NAME = "\"https-jsse-nio-8443\"";
    private static final Set<String> ACTIVE_CONNECTORS_NAMES = Set.of(HTTP_NIO_CONNECTOR_NAME, HTTPS_JSSE_NIO_NAME);
    private MemoryAppender memoryAppender;

    @BeforeEach
    void setUp() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(ConnectorMetric.class, Level.INFO);
    }

    @AfterEach
    void tearDown() {
        memoryAppender.stop();
        ((Logger) LoggerFactory.getLogger(TomcatConnectorsMonitor.class)).detachAppender(memoryAppender);
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                        monitorMode,    isReady
                        off,            true
                        monitor,        true
                        active,         true
                        wrong,          true
                    """
    )
    void whenBothConnectorsAreOk(String monitorMode, boolean isReady) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3)
        );

        connectorsMonitor.addSequence(SECOND_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor(monitorMode, connectorsMonitor);
        assertTrue(tomcatMonitor.isReady());
        assertTrue(tomcatMonitor.isReady());
        assertEquals(tomcatMonitor.isReady(), isReady);
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                        monitorMode,    isReady
                        off,            true
                        monitor,        true
                        active,         false
                        wrong,          true
                    """
    )
    void whenOneConnectorIsBusy(String monitorMode, boolean isReady) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),           // Consumed during init
                l(MAX_THREADS, 1), // Failure 1
                l(MAX_THREADS, 2), // Failure 2
                l(MAX_THREADS, 3), // Failure 3
                l(MAX_THREADS, 4), // Failure 4
                l(MAX_THREADS, 5), // Failure 5
                l(MAX_THREADS, 6)  // Failure 6 - now not ready
        );

        connectorsMonitor.addSequence(SECOND_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3),
                l(1, 4),
                l(1, 5),
                l(1, 6)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor(monitorMode, connectorsMonitor);
        assertTrue(tomcatMonitor.isReady()); // Check 1
        assertTrue(tomcatMonitor.isReady()); // Check 2
        assertTrue(tomcatMonitor.isReady()); // Check 3
        assertTrue(tomcatMonitor.isReady()); // Check 4
        assertTrue(tomcatMonitor.isReady()); // Check 5
        assertEquals(tomcatMonitor.isReady(), isReady); // Check 6 - should be not ready for active mode
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                        monitorMode,    isReady
                        off,            true
                        monitor,        true
                        active,         true
                        wrong,          true
                    """
    )
    void whenLimitOfFailedMetricsIsNotReached(String monitorMode, boolean isReady) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),
                l(MAX_THREADS, 1),
                l(1, 10),
                l(1, 20)
        );

        connectorsMonitor.addSequence(SECOND_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor(monitorMode, connectorsMonitor);
        assertTrue(tomcatMonitor.isReady());
        assertTrue(tomcatMonitor.isReady());
        assertEquals(tomcatMonitor.isReady(), isReady);
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                        monitorMode,    isReady
                        off,            true
                        monitor,        true
                        active,         true
                        wrong,          true
                    """
    )
    void whenOneConnectorIsNearTheLimit(String monitorMode, boolean isReady) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),
                l(MAX_THREADS, MIN_PROCESSED_REQUESTS_COUNT),
                l(MAX_THREADS, MIN_PROCESSED_REQUESTS_COUNT * 2),
                l(MAX_THREADS, MIN_PROCESSED_REQUESTS_COUNT * 3)
        );

        connectorsMonitor.addSequence(SECOND_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor(monitorMode, connectorsMonitor);
        assertTrue(tomcatMonitor.isReady());
        assertTrue(tomcatMonitor.isReady());
        assertEquals(tomcatMonitor.isReady(), isReady);
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                        monitorMode,    isReadyFirst,   isReadySecond
                        off,            true,           true
                        monitor,        true,           true
                        active,         false,          true
                        wrong,          true,           true
                    """
    )
    void whenOneConnectorBackToNormal(String monitorMode, boolean isReadyFirst, boolean isReadySecond) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),           // Consumed during init
                l(MAX_THREADS, 1), // Failure 1
                l(MAX_THREADS, 2), // Failure 2
                l(MAX_THREADS, 3), // Failure 3
                l(MAX_THREADS, 4), // Failure 4
                l(MAX_THREADS, 5), // Failure 5
                l(MAX_THREADS, 6), // Failure 6 - now not ready
                l(MAX_THREADS - 1, 103) // Back to normal - ready again
        );

        connectorsMonitor.addSequence(SECOND_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3),
                l(1, 4),
                l(1, 5),
                l(1, 6),
                l(1, 7)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor(monitorMode, connectorsMonitor);
        assertTrue(tomcatMonitor.isReady()); // Check 1
        assertTrue(tomcatMonitor.isReady()); // Check 2
        assertTrue(tomcatMonitor.isReady()); // Check 3
        assertTrue(tomcatMonitor.isReady()); // Check 4
        assertTrue(tomcatMonitor.isReady()); // Check 5
        assertEquals(tomcatMonitor.isReady(), isReadyFirst); // Check 6 - should be not ready for active mode
        assertEquals(tomcatMonitor.isReady(), isReadySecond); // Check 7 - back to normal, should be ready
    }

    private TomcatReadinessMonitor getTomcatReadinessMonitor(String monitorMode,
            ConnectorsMonitorForTest connectorsMonitor) {
        return getTomcatReadinessMonitor(monitorMode, connectorsMonitor, mock(LoadHealerService.class),
                mock(LoadHealerReactiveModeMonitorService.class));
    }

    private TomcatReadinessMonitor getTomcatReadinessMonitor(String monitorMode,
            ConnectorsMonitorForTest connectorsMonitor, LoadHealerService loadHealerService,
            LoadHealerReactiveModeMonitorService loadHealerReactiveModeMonitorService) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TomcatReadinessMonitor monitor =
                new TomcatReadinessMonitor(connectorsMonitor, registry, monitorMode, REACTIVE_MODE_FAILED_COUNT,
                        MAX_FAILED_METRICS_COUNT, MIN_PROCESSED_REQUESTS_COUNT, loadHealerService,
                        loadHealerReactiveModeMonitorService);
        monitor.postConstruct();
        return monitor;
    }

    @ParameterizedTest
    @MethodSource("logScenarios")
    void logInCaseOfHeavyLoad(String name, long maxThreads, long currentThreadsBusy, long processCount,
            long maxConnections, long currentConnections, String expectedLog) {
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        connectorsMonitor.addSequence(name,
                l(10, 0),
                l(name, maxThreads, currentThreadsBusy, processCount, maxConnections, currentConnections)
        );

        TomcatReadinessMonitor tomcatMonitor = getTomcatReadinessMonitor("monitor", connectorsMonitor);
        tomcatMonitor.isReady();
        Assertions.assertTrue(memoryAppender.contains(expectedLog, Level.INFO),
                "Expected log not found: " + expectedLog);
    }

    @ParameterizedTest
    @MethodSource("heavyActivitiesLogScenarios")
    void testHeavyActivitiesLogging(String name, long maxThreads, long currentThreadsBusy, long processCount,
            long maxConnections, long currentConnections, String expectedLog) {
        LoadHealerService mockLoadHealerService = mock(LoadHealerService.class);
        Map<String, ActivityState> heavyActivities = new HashMap<>();
        ActivityState mockState1 = mock(ActivityState.class);
        ActivityState mockState2 = mock(ActivityState.class);
        heavyActivities.put("example:resource1", mockState1);
        heavyActivities.put("example:resource2", mockState2);
        when(mockLoadHealerService.getHeavyActivities()).thenReturn(heavyActivities);
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();
        connectorsMonitor.addSequence(name,
                l(10, 0),
                l(name, maxThreads, currentThreadsBusy, processCount, maxConnections, currentConnections)
        );
        TomcatReadinessMonitor tomcatMonitor =
                getTomcatReadinessMonitor("monitor", connectorsMonitor, mockLoadHealerService,
                        mock(LoadHealerReactiveModeMonitorService.class));
        tomcatMonitor.isReady();
        Assertions.assertTrue(memoryAppender.contains(expectedLog, Level.INFO),
                "Expected log not found: " + expectedLog);
        verify(mockLoadHealerService).getHeavyActivities();
    }

    private static Stream<Arguments> logScenarios() {
        return Stream.of(
                Arguments.of(HTTP_NIO_CONNECTOR_NAME, 5, 5, 100, 300, 200,
                        "connections (200) below max (300)"),
                Arguments.of(HTTPS_JSSE_NIO_NAME, 5, 5, 200, 200, 200,
                        "connections at max (200)"),
                Arguments.of(HTTPS_JSSE_NIO_NAME, 6, 5, 8, 100, 200,
                        "under heavy load: 5 busy threads out of 6")
        );
    }

    private static Stream<Arguments> heavyActivitiesLogScenarios() {
        return Stream.of(
                Arguments.of(HTTP_NIO_CONNECTOR_NAME, 5, 5, 100, 300, 200,
                        "all threads are busy (5 out of 5), activities that take above 20% of threads: " +
                                "{example:resource2"),
                Arguments.of(HTTPS_JSSE_NIO_NAME, 6, 6, 200, 200, 200,
                        "all threads are busy (6 out of 6), activities that take above 20% of threads: " +
                                "{example:resource2"),
                Arguments.of(HTTPS_JSSE_NIO_NAME, 10, 10, 100, 100, 150,
                        "all threads are busy (10 out of 10), activities that take above 20% of threads: " +
                                "{example:resource2")
        );
    }

    private static ConnectorMetric l(long currentThreadsBusy, long processedCount) {
        return new ConnectorMetric("name", MAX_THREADS, currentThreadsBusy, processedCount, 50, 50);
    }

    private static ConnectorMetric l(String name, long maxThreads, long currentThreadsBusy, long processedCount,
            long maxConnections, long currentConnections) {
        return new ConnectorMetric(name, maxThreads, currentThreadsBusy, processedCount, maxConnections,
                currentConnections);
    }

    private static class ConnectorsMonitorForTest implements ConnectorsMonitor {

        private final Map<String, Iterator<ConnectorMetric>> metricsSequence;
        private final Set<ConnectorMetric> activeConnectors;

        ConnectorsMonitorForTest() {
            this.metricsSequence = new HashMap<>();
            this.activeConnectors = Sets.newHashSet();
        }

        public void addSequence(String connectorName, ConnectorMetric... connectorMetrics) {
            metricsSequence.put(connectorName, (Arrays.asList(connectorMetrics)).iterator());
            if (ACTIVE_CONNECTORS_NAMES.contains(connectorName)) {
                activeConnectors.addAll(Arrays.asList(connectorMetrics));
            }
        }

        @Override
        public Set<String> getConnectorNames() {
            return metricsSequence.keySet();
        }

        @Override
        public Optional<ConnectorMetric> getConnectorMetric(String connectorName) {
            return Optional.of(metricsSequence.get(connectorName).next());
        }

        @Override
        public Set<ConnectorMetric> getActiveConnectorsMetrics() {
            return activeConnectors;
        }
    }

    @Test
    void whenLoadHealerServiceIsAvailableAndNotReadyShouldSetActivitiesToReactiveMode() {
        // Given
        LoadHealerReactiveModeMonitorService loadHealerReactiveModeMonitorService =
                mock(LoadHealerReactiveModeMonitorService.class);
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        // Create a scenario where the monitor will not be ready (all threads busy, not enough processed requests)
        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),
                l(MAX_THREADS, 1), // All threads busy, only 1 request processed (< MIN_PROCESSED_REQUESTS_COUNT)
                l(MAX_THREADS, 2),
                l(MAX_THREADS, 2)  // Still not enough processed requests
        );

        // Mock LoadHealerService behavior
        ActivityConfig activity1 = mock(ActivityConfig.class);
        ActivityConfig activity2 = mock(ActivityConfig.class);
        Map<String, ActivityConfig> registeredActivities = new HashMap<>();
        registeredActivities.put("activity1", activity1);
        registeredActivities.put("activity2", activity2);
        registeredActivities.put("activity3", null);

        when(loadHealerService.getRegisteredActivities()).thenReturn(registeredActivities);

        TomcatReadinessMonitor tomcatMonitor =
                getTomcatReadinessMonitor("active", connectorsMonitor, loadHealerService,
                        loadHealerReactiveModeMonitorService);

        tomcatMonitor.isReady();
        tomcatMonitor.isReady();
        boolean isReady = tomcatMonitor.isReady();

        assertTrue(isReady, "System should still be ready at reactive mode threshold");
        verify(loadHealerReactiveModeMonitorService).activateReactiveMode();
    }

    @Test
    void whenLoadHealerServiceIsAvailableAndReadyShouldNotCallLoadHealerService() {
        // Given
        LoadHealerService loadHealerService = mock(LoadHealerService.class);
        ConnectorsMonitorForTest connectorsMonitor = new ConnectorsMonitorForTest();

        // Create a scenario where the monitor will be ready
        connectorsMonitor.addSequence(FIRST_CONNECTOR_NAME,
                l(1, 0),
                l(1, 1),
                l(1, 2),
                l(1, 3)
        );

        TomcatReadinessMonitor tomcatMonitor =
                getTomcatReadinessMonitor("active", connectorsMonitor, loadHealerService,
                        mock(LoadHealerReactiveModeMonitorService.class));

        // When
        tomcatMonitor.isReady(); // Initial call
        boolean isReady = tomcatMonitor.isReady(); // This should not trigger LoadHealerService logic since it's ready

        // Then
        Assertions.assertTrue(isReady);
        verify(loadHealerService, never()).getRegisteredActivities();
        verify(loadHealerService, never()).getActivitiesNameForReactiveMode();
    }
}