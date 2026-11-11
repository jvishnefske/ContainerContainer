package org.artifactory.throttling.lh.mode;


import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aol.AolService;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.throttling.lh.config.LoadHealerActivity;
import org.artifactory.throttling.lh.config.LoadHealerDynamicActivitiesConfig;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.throttling.lh.model.ActivityState;
import org.artifactory.throttling.lh.model.ReactiveActivityState;
import org.artifactory.throttling.lh.model.RunningActivityHandler;
import org.artifactory.throttling.lh.service.LoadHealerRunnable;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.micrometer.core.instrument.Clock.SYSTEM;
import static java.lang.System.currentTimeMillis;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_LOAD_HEALER_MODE;
import static org.artifactory.throttling.lh.mode.LoadHealerMode.REACTIVE;
import static org.artifactory.throttling.lh.mode.LoadHealerMode.SIMULATION;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.ALREADY_ACTIVE;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.EXTENDED;
import static org.artifactory.throttling.lh.mode.ModeUpdateStatus.UPDATED;
import static org.artifactory.throttling.lh.model.RunningActivityHandler.nonBlockingActivityHandler;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@Service
public final class EnabledLoadHealerServiceImpl implements LoadHealerService {

    @Getter
    private final Map<String, ActivityConfig> registeredActivities = new HashMap<>();
    @Getter
    private final Set<String> activitiesNameForReactiveMode = new CopyOnWriteArraySet<>();
    private final Map<String, ActivityState> runningActivitiesStates = new ConcurrentHashMap<>();
    private final Map<String, TimeWindowMax> activitySummaries = new ConcurrentHashMap<>();
    private static final String ACTIVITY = "activity";
    private static final String ACTIVITY_REACTIVE_STATE = "activity-reactive-state";
    private static final String LOAD_HEALER_RUNNING_MODE = "load-healer-running-mode";
    private static final String REASON = "reason";
    private static final String REASON_REACTIVE_MODE_NOT_STARTED = "reactive-mode-not-started";

    private final AtomicReference<LoadHealerMode> mode;
    private final LoadHealerMode defaultMode;
    private final MeterRegistry meterRegistry;
    private final long heavyActivityThreshold;

    @Getter(lazy = true)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("load-healer-auto-revert").factory());
    private ScheduledFuture<?> autoRevertFuture;

    @Autowired
    private ConfigsService configsService;

    public EnabledLoadHealerServiceImpl(
            @Value("${artifactory.load.healer.mode:}") @Nonnull String configuredMode,
            LoadHealerDynamicActivitiesConfig activitiesConfig, MeterRegistry meterRegistry, AolService aolService) {
        this.defaultMode = resolveDefaultMode(configuredMode, aolService);
        this.mode = new AtomicReference<>(this.defaultMode);
        this.meterRegistry = meterRegistry;
        this.heavyActivityThreshold = (long) (activitiesConfig.getMaxThreads() * 0.2);
        for (ActivityConfig activityConfig : activitiesConfig.getActivitiesMap().values()) {
            registerActivity(activityConfig);
        }
        log.info("Initialized Load Healer with {} total activities", registeredActivities.size());
    }

    private static LoadHealerMode resolveDefaultMode(String configuredMode, AolService aolService) {
        if (isNotEmpty(configuredMode)) {
            LoadHealerMode explicit = LoadHealerMode.fromString(configuredMode);
            log.info("Load Healer mode explicitly configured to {} via artifactory.load.healer.mode", explicit);
            return explicit;
        }
        boolean isAol = aolService.isAol();
        LoadHealerMode autoDefault = isAol ? REACTIVE : SIMULATION;
        log.info("Load Healer mode auto-defaulted to {} ({} deployment)", autoDefault, isAol ? "AOL" : "on-prem");
        return autoDefault;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void registerActivity(@Nonnull ActivityConfig activityConfig) {
        registeredActivities.put(activityConfig.name(), activityConfig);
        runningActivitiesStates.putIfAbsent(activityConfig.name(), new ActivityState());
        TimeWindowMax timeWindowMax = createNewTimeWindow();
        String currentModeName = mode.get().name();
        Gauge.builder("load.healer.running.activities.max", timeWindowMax, TimeWindowMax::poll)
                .description("The maximum number of running activities within a rolling window")
                .tag(ACTIVITY, activityConfig.name())
                .tag(ACTIVITY_REACTIVE_STATE, activityConfig.reactiveState().name())
                .tag(LOAD_HEALER_RUNNING_MODE, currentModeName)
                .register(meterRegistry);
        activitySummaries.put(activityConfig.name(), timeWindowMax);
        Gauge.builder("load.healer.max.threshold", activityConfig::maxThreshold)
                .description("The maximum threshold for running activities.")
                .tag(ACTIVITY, activityConfig.name())
                .tag(ACTIVITY_REACTIVE_STATE, activityConfig.reactiveState().name())
                .tag(LOAD_HEALER_RUNNING_MODE, currentModeName)
                .register(meterRegistry);
        Gauge.builder("load.healer.min.threshold", activityConfig::minThreshold)
                .description("The minimum threshold for running activities.")
                .tag(ACTIVITY, activityConfig.name())
                .tag(ACTIVITY_REACTIVE_STATE, activityConfig.reactiveState().name())
                .tag(LOAD_HEALER_RUNNING_MODE, currentModeName)
                .register(meterRegistry);
    }

    private TimeWindowMax createNewTimeWindow() {
        return new TimeWindowMax(SYSTEM, 10_000, 6);
    }


    @Override
    public RunningActivityHandler startActivity(LoadHealerActivity activity) {
        return startActivity(activity, null);
    }

    @Override
    public RunningActivityHandler startActivity(LoadHealerActivity lhActivity, String resource) {
        String activityName = lhActivity.getActivityName();
        log.debug("Starting activity '{}'", activityName);
        ActivityConfig activityConfig = registeredActivities.get(activityName);
        if (activityConfig == null) {
            log.debug("Activity '{}' is not registered, returning nonBlockingActivityHandler", activityName);
            return nonBlockingActivityHandler;
        }
        String activityKey = getActivityKey(activityName, resource);
        return getPermit(activityKey, activityConfig, resource);
    }

    private RunningActivityHandler getPermit(String activityKey, ActivityConfig activityConfig, String resource) {
        RunningActivityHandler handler = new RunningActivityHandler(this, activityConfig.name(), resource,
                false, false);
        runningActivitiesStates.compute(activityKey, (key, existingState) -> {
            ActivityState stateToUpdate = (existingState != null) ? existingState : new ActivityState();
            if (isBlocked(activityKey, activityConfig, stateToUpdate.getRunningActivities().get())) {
                LoadHealerMode currentMode = mode.get();
                boolean isSimulation = currentMode == SIMULATION;
                handler.setBlocked(!isSimulation);
                handler.setShouldDecreaseRunningActivities(false);
            } else {
                handler.setBlocked(false);
                handler.setShouldDecreaseRunningActivities(true);
                long currentRunning = stateToUpdate.getRunningActivities().incrementAndGet();
                updateMaxRunningActivities(activityConfig.name(), currentRunning);
            }
            return stateToUpdate;
        });
        return handler;
    }

    private String getActivityKey(String name, String resource) {
        return name + (isNotEmpty(resource) ? ":" + resource : "");
    }

    private void updateMaxRunningActivities(String activityName, long currentRunning) {
        TimeWindowMax summary = activitySummaries.get(activityName);
        summary.record(currentRunning);
    }

    private boolean isBlocked(String activityKey, @Nonnull ActivityConfig activityConfig, Long currentCount) {
        if (currentCount < activityConfig.maxThreshold()) {
            return false;
        } else {
            LoadHealerMode currentMode = mode.get();
            if (currentMode == REACTIVE) {
                activitiesNameForReactiveMode.add(activityConfig.name());
                if (activityConfig.reactiveState() != ReactiveActivityState.REACTIVE) {
                    log.debug("Activity '{}' exceeded threshold but is not yet in REACTIVE state, not blocking",
                            activityConfig.name());
                    incrementSkippedBlockedRequests(activityKey, currentMode);
                    return false;
                }
            }
            meterRegistry.counter("load.healer.blocked.requests",
                    ACTIVITY, activityKey,
                    ACTIVITY_REACTIVE_STATE, activityConfig.reactiveState().name(),
                    LOAD_HEALER_RUNNING_MODE, currentMode.name()).increment();
            return true;
        }
    }

    private void incrementSkippedBlockedRequests(String activityKey, LoadHealerMode currentMode) {
        meterRegistry.counter("load.healer.skipped.blocked.requests", ACTIVITY, activityKey, LOAD_HEALER_RUNNING_MODE,
                currentMode.name(), REASON, REASON_REACTIVE_MODE_NOT_STARTED).increment();
    }

    @Override
    public void endActivity(@Nonnull RunningActivityHandler handler) {
        log.debug("Ending activity '{}'", handler.getActivityName());
        String activityKey = getActivityKey(handler.getActivityName(), handler.getResource());
        runningActivitiesStates.computeIfPresent(activityKey, (key, state) -> {
            if (handler.isShouldDecreaseRunningActivities()) {
                state.getRunningActivities().decrementAndGet();
                updateMaxRunningActivities(handler.getActivityName(), state.getRunningActivities().get());
                ActivityConfig activityConfig = registeredActivities.get(handler.getActivityName());
                if (shouldRemoveActivityNameFromReactiveModeSet(state, activityConfig)) {
                    activitiesNameForReactiveMode.remove(handler.getActivityName());
                }
                if (state.getRunningActivities().get() == 0) {
                    return null;
                }
            }
            return state;
        });
        log.debug("Ended activity '{}' for resource '{}'", handler.getActivityName(), handler.getResource());
    }

    private boolean shouldRemoveActivityNameFromReactiveModeSet(ActivityState state, ActivityConfig activityConfig) {
        LoadHealerMode currentMode = mode.get();
        return currentMode == REACTIVE && activityConfig != null &&
                state.getRunningActivities().get() <= activityConfig.minThreshold();
    }

    @Override
    public void assertAllows(RunningActivityHandler handler) {
        if (handler.isBlocked()) {
            log.debug("Load healer blocked polling for {}", handler.getActivityName());
            throw new LoadHealerException("Unable to process activity due to too many other requests.");
        }
    }

    @Override
    @SneakyThrows
    public void executeWithActivity(LoadHealerActivity activity, String resource, LoadHealerRunnable runnable) {
        if (getRegisteredActivities().containsKey(activity.getActivityName())) {
            try (RunningActivityHandler handler = startActivity(activity, resource)) {
                assertAllows(handler);
                runnable.run();
            }
        } else {
            log.debug("Activity '{}' is not registered, executing runnable without load healer control",
                    activity.getActivityName());
            runnable.run();
        }
    }

    @PostConstruct
    public void postConstruct() {
        reloadModeFromConfig();
        log.info("Artifactory Load Healer running on {} mode", mode.get().name());
        for (LoadHealerMode lhMode : LoadHealerMode.values()) {
            Gauge.builder("load.healer.mode", mode, ref -> ref.get() == lhMode ? 1.0 : 0.0)
                    .description("LoadHealer mode indicator: 1 = current mode, 0 = inactive. " +
                            "Exactly one tag value is 1 per pod at any time.")
                    .tag(LOAD_HEALER_RUNNING_MODE, lhMode.name())
                    .register(meterRegistry);
        }
    }

    @PreDestroy
    public synchronized void preDestroy() {
        cancelPendingRevert();
        getScheduler().shutdown();
    }

    private synchronized void cancelPendingRevert() {
        if (autoRevertFuture != null && !autoRevertFuture.isDone()) {
            autoRevertFuture.cancel(false);
            log.info("Cancelled previous auto-revert schedule");
        }
    }

    private synchronized void scheduleAutoRevert(Instant revertAt) {
        cancelPendingRevert();
        long delayMs = Instant.now().until(revertAt, ChronoUnit.MILLIS);
        if (delayMs <= 0) {
            mode.set(defaultMode);
            log.info("Revert time already passed ({}), immediately reverted Load Healer mode to default: {}",
                    revertAt, defaultMode);
            return;
        }
        autoRevertFuture = getScheduler().schedule(() -> {
            mode.set(defaultMode);
            log.info("Auto-reverted Load Healer mode to default: {}", defaultMode);
        }, delayMs, TimeUnit.MILLISECONDS);
        log.info("Scheduled auto-revert to default mode ({}) at {}", defaultMode, revertAt);
    }

    @Override
    public Map<String, ActivityState> getHeavyActivities() {
        Map<String, ActivityState> heavyActivities = new HashMap<>();
        runningActivitiesStates.forEach((activityKey, activityState) -> {
            String activityName = extractActivityName(activityKey);
            ActivityConfig config = registeredActivities.get(activityName);
            if (config != null && activityState.getRunningActivities().get() >= heavyActivityThreshold) {
                heavyActivities.put(activityKey, activityState);
            }
        });
        return heavyActivities;
    }

    private String extractActivityName(String activityKey) {
        int colonIndex = activityKey.indexOf(':');
        return colonIndex != -1 ? activityKey.substring(0, colonIndex) : activityKey;
    }

    @Override
    public void setActivitiesToReactiveMode() {
        log.info("Setting activities to reactive mode: {}", activitiesNameForReactiveMode);
        activitiesNameForReactiveMode.forEach(activityName ->
                registeredActivities.computeIfPresent(activityName, (key, config) -> {
                    config.setReactiveState(ReactiveActivityState.REACTIVE);
                    return config;
                })
        );
    }

    @Override
    public void restoreActivitiesFromReactiveMode() {
        registeredActivities.values().forEach(config -> {
            if (config.reactiveState() == ReactiveActivityState.REACTIVE) {
                config.setReactiveState(ReactiveActivityState.IDLE);
                log.info("Restored activity '{}' from reactive mode to IDLE", config.name());
            }
        });
        activitiesNameForReactiveMode.clear();
    }

    @Override
    public String getCurrentMode() {
        return mode.get().name();
    }

    @Override
    public ModeUpdateResult updateMode(String modeString, Instant revertAt) {
        Instant revertAtMs = revertAt.truncatedTo(ChronoUnit.MILLIS);
        LoadHealerMode newMode = LoadHealerMode.fromString(modeString);
        LoadHealerModeConfig currentConfig = loadConfigFromDB();
        if (isSameModeAndActive(currentConfig, newMode)) {
            return handleSameModeUpdate(newMode, currentConfig, revertAtMs);
        }
        return applyNewMode(newMode, revertAtMs);
    }

    private ModeUpdateResult handleSameModeUpdate(LoadHealerMode newMode, LoadHealerModeConfig currentConfig,
            Instant revertAt) {
        boolean localModeChanging = mode.get() != newMode;
        if (revertAt.isAfter(currentConfig.expiresAt())) {
            return extendModeExpiry(newMode, currentConfig, revertAt, localModeChanging);
        }
        log.info("Load Healer mode update is a no-op: mode={}, expiresAt={}, localModeChanging={}, reason={}",
                newMode, currentConfig.expiresAt(), localModeChanging,
                localModeChanging ? "db-already-active-syncing-local" : "already-active");
        syncLocalMode(newMode, currentConfig.expiresAt());
        return new ModeUpdateResult(localModeChanging ? UPDATED : ALREADY_ACTIVE, currentConfig.expiresAt());
    }

    private ModeUpdateResult extendModeExpiry(LoadHealerMode newMode, LoadHealerModeConfig currentConfig,
            Instant revertAt, boolean localModeChanging) {
        log.info("Load Healer mode expiry extended: mode={}, previousExpiresAt={}, newExpiresAt={}, " +
                "localModeChanging={}", newMode, currentConfig.expiresAt(), revertAt, localModeChanging);
        LoadHealerModeConfig extendedConfig = LoadHealerModeConfig.of(newMode.name(), revertAt);
        configsService.addOrUpdateConfig(ARTIFACTORY_LOAD_HEALER_MODE, extendedConfig, currentTimeMillis());
        syncLocalMode(newMode, revertAt);
        scheduleAutoRevert(revertAt);
        return new ModeUpdateResult(localModeChanging ? UPDATED : EXTENDED, revertAt);
    }

    private ModeUpdateResult applyNewMode(LoadHealerMode newMode, Instant revertAt) {
        LoadHealerMode oldMode = mode.get();
        log.info("Updating Load Healer mode: {} -> {} (revert to {} at {})", oldMode, newMode, defaultMode, revertAt);
        LoadHealerModeConfig newConfig = LoadHealerModeConfig.of(newMode.name(), revertAt);
        try {
            configsService.addOrUpdateConfig(ARTIFACTORY_LOAD_HEALER_MODE, newConfig, currentTimeMillis());
            log.debug("Successfully saved Load Healer mode config to DB");
        } catch (Exception e) {
            log.warn("Failed to save Load Healer mode config to DB: {} -> {}", oldMode, newMode, e);
            throw e;
        }
        mode.set(newMode);
        log.debug("Updated local Load Healer mode: {} -> {}", oldMode, newMode);
        scheduleAutoRevert(revertAt);
        return new ModeUpdateResult(UPDATED, revertAt);
    }

    @Override
    public void reloadModeFromConfig() {
        LoadHealerModeConfig config = loadConfigFromDB();
        if (isConfigNullOrExpired(config)) {
            log.debug("No valid Load Healer config in DB, keeping current mode: {}", mode.get());
            return;
        }
        LoadHealerMode newMode = LoadHealerMode.fromString(config.currentMode());
        if (mode.get() == newMode) {
            log.debug("Load Healer mode already {}, no change needed", newMode);
            return;
        }
        LoadHealerMode oldMode = mode.getAndSet(newMode);
        log.info("Load Healer mode synced: {} -> {} (expires at {})", oldMode, newMode, config.expiresAt());
        scheduleAutoRevert(config.expiresAt());
    }

    private void syncLocalMode(LoadHealerMode newMode, Instant expiresAt) {
        if (mode.get() != newMode) {
            LoadHealerMode oldMode = mode.getAndSet(newMode);
            log.info("Load Healer mode synced locally: {} -> {} (expires at {})", oldMode, newMode, expiresAt);
            scheduleAutoRevert(expiresAt);
        } else {
            log.info("Load Healer mode is already {}, skipping update", newMode);
        }
    }

    private LoadHealerModeConfig loadConfigFromDB() {
        return configsService.getConfig(ARTIFACTORY_LOAD_HEALER_MODE, LoadHealerModeConfig.class);
    }

    private boolean isSameModeAndActive(LoadHealerModeConfig config, LoadHealerMode newMode) {
        return config != null && config.currentMode().equals(newMode.name()) && !config.isExpired();
    }

    private boolean isConfigNullOrExpired(LoadHealerModeConfig config) {
        return config == null || config.isExpired();
    }
}
