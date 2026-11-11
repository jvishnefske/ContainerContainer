package org.artifactory.throttling.lh.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.aol.AolService;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.TestUtils;
import org.artifactory.throttling.lh.config.LoadHealerActivity;
import org.artifactory.throttling.lh.config.LoadHealerDynamicActivitiesConfig;
import org.artifactory.throttling.lh.mode.EnabledLoadHealerServiceImpl;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.throttling.lh.model.ActivityState;
import org.artifactory.throttling.lh.model.ReactiveActivityState;
import org.artifactory.throttling.lh.model.RunningActivityHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.throttling.lh.config.LoadHealerActivity.EXAMPLE;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.XRAY_BLOCK_UNSCANNED;
import static org.artifactory.throttling.lh.model.ReactiveActivityState.IDLE;
import static org.artifactory.throttling.lh.model.ReactiveActivityState.REACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class LoadHealerServiceImplTest {

    private EnabledLoadHealerServiceImpl enabledLoadHealerService;
    private EnabledLoadHealerServiceImpl reactiveLoadHealerService;
    private EnabledLoadHealerServiceImpl simulationLoadHealerService;
    private MeterRegistry enabledMeterRegistry;
    private MeterRegistry simulationMeterRegistry;

    @Mock
    private LoadHealerDynamicActivitiesConfig activitiesConfig;

    @Mock
    private ConfigsService configsService;

    @Mock
    private AolService aolService;

    private static Stream<Arguments> dataProvider() {
        return Stream.of(
                null,
                Arguments.of("resource1")
        );
    }

    @BeforeEach
    void setUp() {
        ActivityConfig example_1 = new ActivityConfig(EXAMPLE.getActivityName(), 1, 2, 300, IDLE);
        ActivityConfig example_2 = new ActivityConfig(EXAMPLE.getActivityName() + "_2", 1, 2, 300, IDLE);
        ActivityConfig xrayBlockUnscanned =
                new ActivityConfig(XRAY_BLOCK_UNSCANNED.getActivityName(), 1, 2, 300, IDLE);
        Map<String, ActivityConfig> activitiesMap = new HashMap<>();
        activitiesMap.put(EXAMPLE.getActivityName(), example_1);
        activitiesMap.put(EXAMPLE.getActivityName() + "_2", example_2);
        activitiesMap.put(XRAY_BLOCK_UNSCANNED.getActivityName(), xrayBlockUnscanned);
        when(activitiesConfig.getActivitiesMap()).thenReturn(activitiesMap);
        when(configsService.getConfig(any(), any())).thenReturn(null);

        enabledMeterRegistry = new SimpleMeterRegistry();
        simulationMeterRegistry = new SimpleMeterRegistry();

        enabledLoadHealerService = new EnabledLoadHealerServiceImpl("enabled", activitiesConfig,
                enabledMeterRegistry, aolService);
        TestUtils.setField(enabledLoadHealerService, "configsService", configsService);
        enabledLoadHealerService.postConstruct();

        reactiveLoadHealerService =
                new EnabledLoadHealerServiceImpl("reactive", activitiesConfig, enabledMeterRegistry, aolService);
        TestUtils.setField(reactiveLoadHealerService, "configsService", configsService);
        reactiveLoadHealerService.postConstruct();

        simulationLoadHealerService = new EnabledLoadHealerServiceImpl("simulation", activitiesConfig,
                simulationMeterRegistry, aolService);
        TestUtils.setField(simulationLoadHealerService, "configsService", configsService);
        simulationLoadHealerService.postConstruct();
    }

    @ParameterizedTest
    @MethodSource("dataProvider")
    void testLoadHealerScenario(String resource) {

        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, resource);
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, resource);
        RunningActivityHandler handler3 = enabledLoadHealerService.startActivity(EXAMPLE, resource);

        // Verify that the activity count is incremented
        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertTrue(handler3.isBlocked());

        handler3.close();
        handler2.close();

        RunningActivityHandler handler_4 = enabledLoadHealerService.startActivity(EXAMPLE, resource);
        assertFalse(handler_4.isBlocked());

        handler1.close();
        handler_4.close();
    }

    @Test
    void startActivityShouldReturnHandlerWhenCalledWithoutResource() {
        // Act
        RunningActivityHandler handler = enabledLoadHealerService.startActivity(EXAMPLE);

        // Assert
        assertNotNull(handler);
        assertFalse(handler.isBlocked());
        handler.close();
    }

    @Test
    void testStartActivityWithoutRegistering() {
        RunningActivityHandler handler1 =
                enabledLoadHealerService.startActivity(mock(LoadHealerActivity.class), "resource");
        assertEquals(0, enabledLoadHealerService.getActivitiesNameForReactiveMode().size());
        assertFalse(handler1.isBlocked());
        handler1.close();
    }

    @Test
    void testStartActivityInSimulationMode() {
        RunningActivityHandler handler1 = simulationLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        RunningActivityHandler handler2 = simulationLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        RunningActivityHandler handler3 = simulationLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());
        assertEquals(0, simulationLoadHealerService.getActivitiesNameForReactiveMode().size());
        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testStartActivityInReactiveMode() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(XRAY_BLOCK_UNSCANNED);
        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());
        assertEquals(1, reactiveLoadHealerService.getActivitiesNameForReactiveMode().size());
        assertEquals("xrayBlockUnscanned",
                reactiveLoadHealerService.getActivitiesNameForReactiveMode().stream().findFirst().get());
        handler1.close();
        handler2.close();
        assertEquals(0, reactiveLoadHealerService.getActivitiesNameForReactiveMode().size());
        handler3.close();
    }

    @Test
    void testStartEnabledActivityInSimulationMode() {
        LoadHealerActivity loadHealerActivity = mock(LoadHealerActivity.class);
        when(loadHealerActivity.getActivityName()).thenReturn("example_2");
        RunningActivityHandler handler1 = simulationLoadHealerService.startActivity(loadHealerActivity);
        RunningActivityHandler handler2 = simulationLoadHealerService.startActivity(loadHealerActivity);
        RunningActivityHandler handler3 = simulationLoadHealerService.startActivity(loadHealerActivity);
        RunningActivityHandler handler_4 = simulationLoadHealerService.startActivity(loadHealerActivity);
        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());
        assertFalse(handler_4.isBlocked());
        handler1.close();
        handler2.close();
        handler3.close();
        handler_4.close();
    }

    @Test
    void testCollectMetrics() {
        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");

        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "PROACTIVE").count());

        Gauge summary = enabledMeterRegistry
                .find("load.healer.running.activities.max")
                .tags("activity", EXAMPLE.getActivityName(),
                        "activity-reactive-state", "IDLE",
                        "load-healer-running-mode", "PROACTIVE").gauge();
        assertNotNull(summary);
        assertEquals(2, summary.value());

        Gauge maxThresholdMetric = enabledMeterRegistry.find("load.healer.max.threshold").
                tags("activity", EXAMPLE.getActivityName(),
                        "activity-reactive-state", "IDLE",
                        "load-healer-running-mode", "PROACTIVE"
                ).gauge();

        assertNotNull(maxThresholdMetric);
        assertEquals(2, maxThresholdMetric.value());

        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void assertServiceEnabled() {
        assertTrue(enabledLoadHealerService.isEnabled());
        assertTrue(simulationLoadHealerService.isEnabled());
    }

    @Test
    void assertLoadHealerAllows() {
        RunningActivityHandler handler = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        enabledLoadHealerService.assertAllows(handler);
        handler.close();
    }

    @Test
    void assertLoadHealerAllowsBlocked() {
        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, "resource1");
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, "resource1");
        RunningActivityHandler handler3 = enabledLoadHealerService.startActivity(EXAMPLE, "resource1");
        Throwable error =
                assertThrows(LoadHealerException.class,
                        () -> enabledLoadHealerService.assertAllows(handler3));
        assertEquals("Unable to process activity due to too many other requests.", error.getMessage());
        handler3.close();
        handler2.close();
        handler1.close();
    }

    @Test
    void isBlockedWithSimulationMode() {
        RunningActivityHandler handler1 = simulationLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = simulationLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = simulationLoadHealerService.startActivity(EXAMPLE, "resource");

        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());

        Gauge summary = simulationMeterRegistry
                .find("load.healer.running.activities.max")
                .tags("activity", EXAMPLE.getActivityName()).gauge();
        assertNotNull(summary);
        assertEquals(2, summary.value());
        assertEquals(1, simulationMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "SIMULATION").count());
        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testSetActivitiesToReactiveMode() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        assertTrue(reactiveLoadHealerService.getActivitiesNameForReactiveMode().contains(EXAMPLE.getActivityName()));
        ActivityConfig originalConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        ReactiveActivityState originalState = originalConfig.reactiveState();
        assertEquals(IDLE, originalState);
        assertFalse(handler1.isBlocked());
        reactiveLoadHealerService.setActivitiesToReactiveMode();
        ActivityConfig updatedConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        assertEquals(REACTIVE, updatedConfig.reactiveState());
        RunningActivityHandler handler_4 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler_5 = reactiveLoadHealerService.startActivity(EXAMPLE, "notBlocked");
        assertTrue(handler_4.isBlocked());
        assertFalse(handler_5.isBlocked());
        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "REACTIVE",
                "load-healer-running-mode", "REACTIVE").count());

        handler1.close();
        handler2.close();
        handler3.close();
        handler_5.close();
    }

    @Test
    void testSetActivitiesToReactiveModeWithEmptyReactiveSet() {
        // Test when there are no activities in reactive mode
        assertTrue(reactiveLoadHealerService.getActivitiesNameForReactiveMode().isEmpty());

        // Get original reactive state
        ActivityConfig originalConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        ReactiveActivityState originalState = originalConfig.reactiveState();

        // Call setActivitiesToReactiveMode
        reactiveLoadHealerService.setActivitiesToReactiveMode();

        // Verify reactive state remains unchanged since no activities were in reactive mode
        ActivityConfig configAfter = reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        assertEquals(originalState, configAfter.reactiveState());
    }

    @Test
    void testSetActivitiesToReactiveModeMultipleCalls() {
        // Add an activity to reactive mode
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");

        // Verify activity is in reactive mode set
        assertFalse(reactiveLoadHealerService.getActivitiesNameForReactiveMode().isEmpty());

        // Call setActivitiesToReactiveMode
        reactiveLoadHealerService.setActivitiesToReactiveMode();

        // Verify the state was set to REACTIVE
        ActivityConfig config = reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        assertEquals(REACTIVE, config.reactiveState());

        // Clean up
        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testGetHeavyActivitiesWithLightActivities() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource1");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource2");

        Map<String, ActivityState> heavyActivities = reactiveLoadHealerService.getHeavyActivities();
        assertFalse(heavyActivities.isEmpty());
        assertTrue(heavyActivities.containsKey("example:resource1"));
        assertTrue(heavyActivities.containsKey("example:resource2"));
        handler1.close();
        handler2.close();
    }

    @Test
    void testRestoreActivitiesFromReactiveMode() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        assertTrue(reactiveLoadHealerService.getActivitiesNameForReactiveMode().contains(EXAMPLE.getActivityName()));
        ActivityConfig originalConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        ReactiveActivityState originalState = originalConfig.reactiveState();
        assertEquals(IDLE, originalState);
        reactiveLoadHealerService.setActivitiesToReactiveMode();
        ActivityConfig reactiveConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        assertEquals(REACTIVE, reactiveConfig.reactiveState());
        reactiveLoadHealerService.restoreActivitiesFromReactiveMode();
        ActivityConfig restoredConfig =
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName());
        assertEquals(originalState, restoredConfig.reactiveState());
        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testBlockedRequestsCounterWithTags() {
        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, "test-resource");
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, "test-resource");
        RunningActivityHandler handler3 = enabledLoadHealerService.startActivity(EXAMPLE, "test-resource");

        assertTrue(handler3.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:test-resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "PROACTIVE").count());

        handler1.close();
        handler2.close();
        handler3.close();

        RunningActivityHandler simHandler1 = simulationLoadHealerService.startActivity(EXAMPLE, "sim-resource");
        RunningActivityHandler simHandler2 = simulationLoadHealerService.startActivity(EXAMPLE, "sim-resource");
        RunningActivityHandler simHandler3 = simulationLoadHealerService.startActivity(EXAMPLE, "sim-resource");

        assertFalse(simHandler3.isBlocked());

        assertEquals(1, simulationMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:sim-resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "SIMULATION").count());

        simHandler1.close();
        simHandler2.close();
        simHandler3.close();


        RunningActivityHandler reactHandler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "react-resource");
        RunningActivityHandler reactHandler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "react-resource");
        RunningActivityHandler reactHandler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "react-resource");

        assertFalse(reactHandler3.isBlocked());

        reactiveLoadHealerService.setActivitiesToReactiveMode();

        RunningActivityHandler reactHandler4 = reactiveLoadHealerService.startActivity(EXAMPLE, "react-resource");
        assertTrue(reactHandler4.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:react-resource",
                "activity-reactive-state", "REACTIVE",
                "load-healer-running-mode", "REACTIVE").count());

        reactHandler1.close();
        reactHandler2.close();
        reactHandler3.close();
        reactHandler4.close();
    }

    @Test
    void testRestoreActivitiesFromReactiveModeWithMultipleActivities() throws Exception {
        ActivityConfig activity1 = new ActivityConfig("activity1", 1, 5, 1000, IDLE);
        ActivityConfig activity2 = new ActivityConfig("activity2", 1, 3, 2000, IDLE);
        ActivityConfig activity3 = new ActivityConfig("activity3", 1, 2, 1500, IDLE);
        reactiveLoadHealerService.registerActivity(activity1);
        reactiveLoadHealerService.registerActivity(activity2);
        reactiveLoadHealerService.registerActivity(activity3);
        Field activitiesField = EnabledLoadHealerServiceImpl.class.getDeclaredField("activitiesNameForReactiveMode");
        activitiesField.setAccessible(true);
        Set<String> reactiveModeSet = (Set<String>) activitiesField.get(reactiveLoadHealerService);
        reactiveModeSet.add("activity1");
        reactiveModeSet.add("activity2");
        reactiveModeSet.add("activity3");
        reactiveLoadHealerService.setActivitiesToReactiveMode();
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity1").reactiveState());
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity2").reactiveState());
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity3").reactiveState());
        reactiveLoadHealerService.restoreActivitiesFromReactiveMode();
        assertEquals(IDLE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity1").reactiveState());
        assertEquals(IDLE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity2").reactiveState());
        assertEquals(IDLE,
                reactiveLoadHealerService.getRegisteredActivities().get("activity3").reactiveState());
    }

    @Test
    void testRestoreActivitiesFromReactiveModeResetsStatesToIdle() throws Exception {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        reactiveLoadHealerService.setActivitiesToReactiveMode();
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName()).reactiveState());
        reactiveLoadHealerService.restoreActivitiesFromReactiveMode();
        assertEquals(IDLE,
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName()).reactiveState());
        handler1.close();
        handler2.close();
        handler3.close();
    }

    // executeWithActivity tests

    @Test
    void executeWithActivityShouldRunRunnableWhenActivityIsRegistered() {
        // Arrange
        boolean[] executed = {false};

        // Act
        enabledLoadHealerService.executeWithActivity(EXAMPLE, "resource", () -> executed[0] = true);

        // Assert
        assertTrue(executed[0]);
    }

    @Test
    void executeWithActivityShouldRunRunnableWhenActivityIsNotRegistered() {
        // Arrange
        boolean[] executed = {false};
        LoadHealerActivity unregisteredActivity = mock(LoadHealerActivity.class);
        when(unregisteredActivity.getActivityName()).thenReturn("unregistered");

        // Act
        enabledLoadHealerService.executeWithActivity(unregisteredActivity, "resource", () -> executed[0] = true);

        // Assert
        assertTrue(executed[0]);
    }

    @Test
    void executeWithActivityShouldPropagateRuntimeException() {
        // Arrange & Act & Assert
        assertThrows(RuntimeException.class, () ->
                enabledLoadHealerService.executeWithActivity(EXAMPLE, "resource", () -> {
                    throw new RuntimeException("Test exception");
                })
        );
    }

    @Test
    void executeWithActivityShouldPropagateCheckedException() {
        // Arrange & Act & Assert
        assertThrows(Exception.class, () ->
                enabledLoadHealerService.executeWithActivity(EXAMPLE, "resource", () -> {
                    throw new Exception("Test checked exception");
                })
        );
    }

    @Test
    void executeWithActivityShouldBlockWhenThresholdExceeded() {
        // Arrange - fill up to threshold
        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");

        // Act & Assert - third call should be blocked and throw LoadHealerException
        assertThrows(LoadHealerException.class, () ->
                enabledLoadHealerService.executeWithActivity(EXAMPLE, "resource", () -> {})
        );

        // Cleanup
        handler1.close();
        handler2.close();
    }

    @Test
    void testSkippedBlockedRequestsReactiveModeNotStartedForAllActivities() {
        LoadHealerActivity activity = mock(LoadHealerActivity.class);
        when(activity.getActivityName()).thenReturn("example_2");

        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(activity, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(activity, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(activity, "resource");

        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.skipped.blocked.requests",
                "activity", "example_2:resource",
                "load-healer-running-mode", "REACTIVE",
                "reason", "reactive-mode-not-started").count());

        assertEquals(0, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example_2:resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "REACTIVE").count());

        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testSkippedBlockedRequestsReactiveModeNotStarted() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");

        assertFalse(handler1.isBlocked());
        assertFalse(handler2.isBlocked());
        assertFalse(handler3.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.skipped.blocked.requests",
                "activity", "example:resource",
                "load-healer-running-mode", "REACTIVE",
                "reason", "reactive-mode-not-started").count());

        assertEquals(0, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "REACTIVE").count());

        handler1.close();
        handler2.close();
        handler3.close();
    }

    @Test
    void testNoSkippedMetricWhenBlockedInEnabledMode() {
        RunningActivityHandler handler1 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = enabledLoadHealerService.startActivity(EXAMPLE, "resource");

        assertTrue(handler3.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "IDLE",
                "load-healer-running-mode", "PROACTIVE").count());

        assertEquals(0, enabledMeterRegistry.counter("load.healer.skipped.blocked.requests",
                "activity", "example:resource",
                "load-healer-running-mode", "PROACTIVE",
                "reason", "reactive-mode-not-started").count());

        handler1.close();
        handler2.close();
        handler3.close();
    }

    /**
     * Regression test for RTDEV-83967.
     * If an activity is removed from activitiesNameForReactiveMode mid-outage (because it dropped below
     * minThreshold while the reactive mode thread was sleeping), restoreActivitiesFromReactiveMode() must
     * still reset it to IDLE — it should not stay stuck in REACTIVE state permanently.
     */
    @Test
    void testRestoreActivitiesFromReactiveModeRestoresActivityRemovedFromSetDuringOutage() {
        // Start enough activities to exceed maxThreshold, adding EXAMPLE to activitiesNameForReactiveMode
        // (maxThreshold=2, so on the 3rd start the set gets populated)
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        assertTrue(reactiveLoadHealerService.getActivitiesNameForReactiveMode().contains(EXAMPLE.getActivityName()));

        // Outage detected: flip activities to REACTIVE blocking state
        reactiveLoadHealerService.setActivitiesToReactiveMode();
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName()).reactiveState());

        // Activity slows down during the outage window.
        // handler3 close: count 3 -> 2 (still above minThreshold=1, stays in set)
        // handler2 close: count 2 -> 1 (== minThreshold, endActivity removes from activitiesNameForReactiveMode)
        handler3.close();
        handler2.close();

        // Activity was removed from the reactive set, but its reactiveState is still REACTIVE — the bug
        assertFalse(reactiveLoadHealerService.getActivitiesNameForReactiveMode().contains(EXAMPLE.getActivityName()),
                "activity must have been removed from the set when it dropped to minThreshold");
        assertEquals(REACTIVE,
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName()).reactiveState(),
                "reactiveState must still be REACTIVE before restore is called");

        // Reactive mode duration expires — restore should still reset the activity to IDLE
        reactiveLoadHealerService.restoreActivitiesFromReactiveMode();

        assertEquals(IDLE,
                reactiveLoadHealerService.getRegisteredActivities().get(EXAMPLE.getActivityName()).reactiveState(),
                "activity stuck mid-outage must be restored to IDLE even when absent from " +
                        "activitiesNameForReactiveMode");
        assertTrue(reactiveLoadHealerService.getActivitiesNameForReactiveMode().isEmpty(),
                "activitiesNameForReactiveMode must be cleared after restore");

        handler1.close();
    }

    @Test
    void testSkippedMetricStopsAfterReactiveModeActivated() {
        RunningActivityHandler handler1 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler2 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        RunningActivityHandler handler3 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");

        assertFalse(handler3.isBlocked());
        assertEquals(1, enabledMeterRegistry.counter("load.healer.skipped.blocked.requests",
                "activity", "example:resource",
                "load-healer-running-mode", "REACTIVE",
                "reason", "reactive-mode-not-started").count());

        reactiveLoadHealerService.setActivitiesToReactiveMode();

        RunningActivityHandler handler4 = reactiveLoadHealerService.startActivity(EXAMPLE, "resource");
        assertTrue(handler4.isBlocked());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.blocked.requests",
                "activity", "example:resource",
                "activity-reactive-state", "REACTIVE",
                "load-healer-running-mode", "REACTIVE").count());

        assertEquals(1, enabledMeterRegistry.counter("load.healer.skipped.blocked.requests",
                "activity", "example:resource",
                "load-healer-running-mode", "REACTIVE",
                "reason", "reactive-mode-not-started").count());

        handler1.close();
        handler2.close();
        handler3.close();
        handler4.close();
    }
}
