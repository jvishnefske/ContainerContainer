package org.artifactory.throttling.lh.mode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.aol.AolService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.TestUtils;
import org.artifactory.throttling.lh.config.LoadHealerDynamicActivitiesConfig;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_LOAD_HEALER_MODE;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.EXAMPLE;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.ALREADY_ACTIVE;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.EXTENDED;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.UPDATED;
import static org.artifactory.throttling.lh.model.ReactiveActivityState.IDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class LoadHealerModeChangeTest {

    @Mock
    private ConfigsService configsService;
    @Mock
    private LoadHealerDynamicActivitiesConfig activitiesConfig;
    @Mock
    private AolService aolService;

    private EnabledLoadHealerServiceImpl loadHealerService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        ActivityConfig exampleActivity = new ActivityConfig(EXAMPLE.getActivityName(), 1, 2, 300, IDLE);
        Map<String, ActivityConfig> activitiesMap = new HashMap<>();
        activitiesMap.put(EXAMPLE.getActivityName(), exampleActivity);

        when(activitiesConfig.getActivitiesMap()).thenReturn(activitiesMap);

        meterRegistry = new SimpleMeterRegistry();
        loadHealerService = new EnabledLoadHealerServiceImpl("reactive", activitiesConfig, meterRegistry, aolService);
        TestUtils.setField(loadHealerService, "configsService", configsService);
        loadHealerService.postConstruct();
    }

    @Test
    void testGetCurrentModeReturnsInMemoryMode() {
        String currentMode = loadHealerService.getCurrentMode();
        assertEquals("REACTIVE", currentMode);
    }

    @Test
    void testUpdateModeSavesConfigToDatabase() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);
        Instant revertAt = Instant.now().plus(Duration.ofDays(1));
        loadHealerService.updateMode("PROACTIVE", revertAt);
        verify(configsService).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) ->
                        config.currentMode().equals("PROACTIVE") && config.expiresAt().isAfter(Instant.now())),
                anyLong()
        );
    }

    @Test
    void testUpdateModeChangesLocalMode() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testUpdateModeReturnsUpdatedWhenDBSameButLocalDifferent() {
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(3)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        assertEquals(UPDATED, result.status());
        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
        verify(configsService, times(0)).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) -> config.currentMode().equals("PROACTIVE")),
                anyLong()
        );
    }

    @Test
    void testUpdateModeSyncsLocalModeWhenDBAlreadyHasMode() {
        // Same cross-node scenario as above, focused on the local-mode side effect.
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(3)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        assertEquals(UPDATED, result.status());
        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
        verify(configsService, times(0)).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) -> config.currentMode().equals("PROACTIVE")),
                anyLong()
        );
    }

    @Test
    void testUpdateModeRenewsTTLWhenConfigExpiredButModeMatches() {
        LoadHealerModeConfig expiredConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().minus(Duration.ofHours(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(expiredConfig);

        loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        verify(configsService, times(1)).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) ->
                        config.currentMode().equals("PROACTIVE") && config.expiresAt().isAfter(Instant.now())),
                anyLong()
        );
    }

    @Test
    void testReloadModeFromConfigUpdatesLocalMode() {
        LoadHealerModeConfig config =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(config);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        loadHealerService.reloadModeFromConfig();

        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testReloadModeFromConfigWhenNoConfigExistsDoesNothing() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofHours(1)));
        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);
        loadHealerService.reloadModeFromConfig();

        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testDefaultModeIsSimulationWhenPropertyNotSet() {
        when(configsService.getConfig(ARTIFACTORY_LOAD_HEALER_MODE, LoadHealerModeConfig.class))
                .thenReturn(null);

        EnabledLoadHealerServiceImpl service = createNewService("simulation");
        service.postConstruct();

        assertEquals("SIMULATION", service.getCurrentMode());
    }

    @Test
    void testStartupNoConfigInDBUsesDefaultMode() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        EnabledLoadHealerServiceImpl service = createNewService("reactive");
        service.postConstruct();

        assertEquals("REACTIVE", service.getCurrentMode());
    }

    @Test
    void testStartupWithLegacyEnabledConfigWorksAsProactive() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        EnabledLoadHealerServiceImpl service = createNewService("enabled");
        service.postConstruct();

        assertEquals("PROACTIVE", service.getCurrentMode());
    }

    @Test
    void testStartupConfigExpiredUsesDefaultMode() {
        LoadHealerModeConfig expiredConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().minus(Duration.ofHours(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(expiredConfig);

        EnabledLoadHealerServiceImpl service = createNewService("reactive");
        service.postConstruct();

        assertEquals("REACTIVE", service.getCurrentMode());
    }

    @Test
    void testStartupConfigValidLoadsMode() {
        LoadHealerModeConfig validConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofHours(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(validConfig);

        EnabledLoadHealerServiceImpl service = createNewService("reactive");
        service.postConstruct();

        assertEquals("PROACTIVE", service.getCurrentMode());
    }

    @Test
    void testReloadModeFromConfigSameModeDoesNothing() {
        LoadHealerModeConfig config =
                LoadHealerModeConfig.of("REACTIVE", Instant.now().plus(Duration.ofDays(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(config);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        loadHealerService.reloadModeFromConfig();

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testReloadModeFromConfigWithExpiredConfigDoesNothing() {
        LoadHealerModeConfig expiredConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().minus(Duration.ofSeconds(1)));

        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(expiredConfig);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        loadHealerService.reloadModeFromConfig();

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testUpdateModeDBFailureThrowsExceptionAndDoesNotChangeMode() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        RuntimeException dbException = new RuntimeException("Database connection failed");
        doThrow(dbException).when(configsService).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) -> config.currentMode().equals("PROACTIVE")),
                anyLong()
        );

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));
        });

        assertEquals("Database connection failed", exception.getMessage());
        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testAutoDefaultIsReactiveOnAol() {
        when(aolService.isAol()).thenReturn(true);

        EnabledLoadHealerServiceImpl service = createNewService("");
        service.postConstruct();

        assertEquals("REACTIVE", service.getCurrentMode());
    }

    @Test
    void testAutoDefaultIsSimulationOnPrem() {
        when(aolService.isAol()).thenReturn(false);

        EnabledLoadHealerServiceImpl service = createNewService("");
        service.postConstruct();

        assertEquals("SIMULATION", service.getCurrentMode());
    }

    @Test
    void testExplicitConfiguredModeWinsOverAolAutoDefault() {
        EnabledLoadHealerServiceImpl service = createNewService("simulation");
        service.postConstruct();

        assertEquals("SIMULATION", service.getCurrentMode());
        // Sanity: AolService was never consulted because configured value is non-empty.
        verify(aolService, times(0)).isAol();
    }

    @Test
    void testExplicitConfiguredModeWinsOverOnPremAutoDefault() {
        EnabledLoadHealerServiceImpl service = createNewService("reactive");
        service.postConstruct();

        assertEquals("REACTIVE", service.getCurrentMode());
        verify(aolService, times(0)).isAol();
    }

    @Test
    void testFromStringValidModes() {
        assertEquals(LoadHealerMode.PROACTIVE, LoadHealerMode.fromString("proactive"));
        assertEquals(LoadHealerMode.PROACTIVE, LoadHealerMode.fromString("PROACTIVE"));
        assertEquals(LoadHealerMode.REACTIVE, LoadHealerMode.fromString("reactive"));
        assertEquals(LoadHealerMode.SIMULATION, LoadHealerMode.fromString("simulation"));
        assertEquals(LoadHealerMode.PROACTIVE, LoadHealerMode.fromString("enabled")); // backward-compatible alias
        assertEquals(LoadHealerMode.PROACTIVE, LoadHealerMode.fromString("ENABLED")); // backward-compatible alias
    }

    @Test
    void testFromStringUnknownModeDefaultsToReactive() {
        assertEquals(LoadHealerMode.REACTIVE, LoadHealerMode.fromString("disabled"));
        assertEquals(LoadHealerMode.REACTIVE, LoadHealerMode.fromString("unknown"));
        assertEquals(LoadHealerMode.REACTIVE, LoadHealerMode.fromString(""));
    }

    @Test
    void testUpdateModeReturnsUpdatedWhenModeChanges() {
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(null);

        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        assertEquals(UPDATED, result.status());
    }

    @Test
    void testUpdateModeReturnsAlreadyActiveWhenSameModeWithShorterDuration() {
        // Local mode already matches the request; existing config expires in 3 days; requesting same
        // mode for only 1 day — shorter and local matches, so genuine no-op.
        primeLocalMode(LoadHealerMode.PROACTIVE);
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(3)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        assertEquals(ALREADY_ACTIVE, result.status());
        assertEquals(existingConfig.expiresAt(), result.revertAt());
    }

    @Test
    void testUpdateModeReturnsExtendedWhenSameModeWithLaterRevertAt() {
        // Local mode already matches the request; existing config expires in 1 day; requesting same
        // mode for 3 days — pure extension on a synced node.
        primeLocalMode(LoadHealerMode.PROACTIVE);
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        Instant newRevertAt = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", newRevertAt);

        assertEquals(EXTENDED, result.status());
        assertEquals(newRevertAt, result.revertAt());
    }

    @Test
    void testUpdateModeReturnsUpdatedWhenLocalDifferentDespiteDbExtension() {
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        Instant newRevertAt = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", newRevertAt);

        assertEquals(UPDATED, result.status());
        assertEquals(newRevertAt, result.revertAt());
        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
        // DB IS rewritten on the extend path even though the status hides it as UPDATED.
        verify(configsService, times(1)).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) ->
                        config.currentMode().equals("PROACTIVE") && config.expiresAtMs() == newRevertAt.toEpochMilli()),
                anyLong()
        );
    }

    @Test
    void testUpdateModeWritesNewConfigToDbOnExtension() {
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        Instant newRevertAt = Instant.now().plus(Duration.ofDays(3));
        loadHealerService.updateMode("PROACTIVE", newRevertAt);

        verify(configsService, times(1)).addOrUpdateConfig(
                eq(ARTIFACTORY_LOAD_HEALER_MODE),
                argThat((LoadHealerModeConfig config) ->
                        config.currentMode().equals("PROACTIVE") && config.expiresAtMs() == newRevertAt.toEpochMilli()),
                anyLong()
        );
    }

    @Test
    void testUpdateModeKeepsLocalModeOnExtension() {
        // Node starts in REACTIVE; DB already has PROACTIVE for 1 day; new request extends to 3 days.
        // Local mode flips REACTIVE -> PROACTIVE, so status is UPDATED (not EXTENDED) from this node's POV.
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        assertEquals("REACTIVE", loadHealerService.getCurrentMode());
        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(3)));
        assertEquals(UPDATED, result.status());
        assertEquals("PROACTIVE", loadHealerService.getCurrentMode());
    }

    @Test
    void testUpdateModeDoesNotWriteDbOnAlreadyActive() {
        // Local mode already matches and revertAt is shorter — pure no-op, no DB write.
        primeLocalMode(LoadHealerMode.PROACTIVE);
        LoadHealerModeConfig existingConfig =
                LoadHealerModeConfig.of("PROACTIVE", Instant.now().plus(Duration.ofDays(3)));
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        loadHealerService.updateMode("PROACTIVE", Instant.now().plus(Duration.ofDays(1)));

        verify(configsService, times(0)).addOrUpdateConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), argThat(
                (LoadHealerModeConfig config) -> config.currentMode().equals("PROACTIVE")), anyLong());
    }

    @Test
    void testUpdateModeReturnsAlreadyActiveWhenRevertAtEqualsCurrentExpiry() {
        // Boundary: revertAt == currentConfig.expiresAt(). isAfter(...) is false on equality,
        // so the request is treated as already-active (no DB write, no reschedule).
        // Local mode is primed to match — otherwise the cross-node logic would flip it to UPDATED.
        primeLocalMode(LoadHealerMode.PROACTIVE);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(1));
        LoadHealerModeConfig existingConfig = LoadHealerModeConfig.of("PROACTIVE", expiresAt);
        when(configsService.getConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), eq(LoadHealerModeConfig.class)))
                .thenReturn(existingConfig);

        ModeUpdateResult result = loadHealerService.updateMode("PROACTIVE", existingConfig.expiresAt());

        assertEquals(ALREADY_ACTIVE, result.status());
        assertEquals(existingConfig.expiresAt(), result.revertAt());
        verify(configsService, times(0)).addOrUpdateConfig(eq(ARTIFACTORY_LOAD_HEALER_MODE), argThat(
                (LoadHealerModeConfig config) -> config.currentMode().equals("PROACTIVE")), anyLong());
    }

    private void primeLocalMode(LoadHealerMode mode) {
        // Set the service's internal AtomicReference<LoadHealerMode> directly so tests can simulate a
        // pod whose local mode already matches the request without re-running updateMode first.
        @SuppressWarnings("unchecked")
        AtomicReference<LoadHealerMode> ref =
                (AtomicReference<LoadHealerMode>) TestUtils.getField(loadHealerService, "mode", AtomicReference.class);
        ref.set(mode);
    }

    private EnabledLoadHealerServiceImpl createNewService(String defaultMode) {
        ActivityConfig exampleActivity = new ActivityConfig(EXAMPLE.getActivityName(), 1, 2, 300, IDLE);
        Map<String, ActivityConfig> activitiesMap = new HashMap<>();
        activitiesMap.put(EXAMPLE.getActivityName(), exampleActivity);

        when(activitiesConfig.getActivitiesMap()).thenReturn(activitiesMap);

        EnabledLoadHealerServiceImpl service = new EnabledLoadHealerServiceImpl(
                defaultMode, activitiesConfig, new SimpleMeterRegistry(), aolService);
        TestUtils.setField(service, "configsService", configsService);

        return service;
    }
}
