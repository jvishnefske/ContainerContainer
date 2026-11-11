package org.artifactory.throttling.lh.mode;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.system.load.event.SystemLoadEvent;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.listener.SystemLoadEventListener;
import org.artifactory.throttling.lh.service.LoadHealerReactiveModeMonitorService;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.artifactory.common.ConstantValues.loadHealerReactiveModeDurationMs;
import static org.artifactory.throttling.lh.mode.LoadHealerMode.REACTIVE;

/**
 * @author Amir Avidan
 */
// Doc: ai-knowledge-base/workspace/design/load-healer-reactive-mode.md
@Slf4j
@Service
public class LoadHealerReactiveModeServiceImpl implements LoadHealerReactiveModeMonitorService,
        SystemLoadEventListener {

    private final LoadHealerService loadHealerService;
    private final AtomicBoolean reactiveModeActive = new AtomicBoolean(false);

    public LoadHealerReactiveModeServiceImpl(LoadHealerService loadHealerService) {
        this.loadHealerService = loadHealerService;
    }

    @Override
    public void onSystemLoadEvent(SystemLoadEvent event) {
        if (isNotReactiveMode()) {
            return;
        }
        SystemLoadState currentState = event.systemLoadState();
        log.debug("Received system load event: {}", currentState);
        if (currentState == SystemLoadState.OVERLOADED) {
            log.info("System is overloaded, syncing Load Healer mode from DB");
            loadHealerService.reloadModeFromConfig();
        } else if (currentState == SystemLoadState.OUTAGE) {
            log.info("System outage detected — activating reactive mode");
            activateReactiveMode();
        } else if (reactiveModeActive.get()) {
            log.info("System recovered from outage, " +
                    "but reactive mode thread is still running and will complete its duration");
        }
    }

    @Override
    public void activateReactiveMode() {
        if (isNotReactiveMode()) {
            return;
        }
        if (!reactiveModeActive.compareAndSet(false, true)) {
            log.debug("Reactive mode already active, skipping activation");
            return;
        }
        startReactiveModeThread();
        log.info("Reactive mode activated for {} milliseconds", loadHealerReactiveModeDurationMs.getLong());
    }

    /**
     * Checks the current mode on every call (no caching) because the Load Healer mode
     * can be changed at runtime via the API ({@link LoadHealerService#updateMode}).
     */
    private boolean isNotReactiveMode() {
        return !REACTIVE.name().equals(loadHealerService.getCurrentMode());
    }

    private void startReactiveModeThread() {
        Thread.ofVirtual()
                .name("load-healer-reactive-mode")
                .start(() -> {
                    try {
                        log.info("Starting reactive mode management virtual thread");
                        loadHealerService.setActivitiesToReactiveMode();
                        long durationMillis = loadHealerReactiveModeDurationMs.getLong();
                        log.debug("Reactive mode virtual thread sleeping for {} milliseconds", durationMillis);
                        Thread.sleep(durationMillis);
                        loadHealerService.restoreActivitiesFromReactiveMode();
                        log.info("Reactive mode virtual thread completed - activities restored to normal");
                    } catch (InterruptedException e) {
                        log.info("Reactive mode virtual thread interrupted");
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Error in reactive mode virtual thread", e);
                    } finally {
                        reactiveModeActive.set(false);
                        try {
                            loadHealerService.restoreActivitiesFromReactiveMode();
                        } catch (Exception e) {
                            log.error("Error restoring activities in finally block", e);
                        }
                    }
                });
    }
}
