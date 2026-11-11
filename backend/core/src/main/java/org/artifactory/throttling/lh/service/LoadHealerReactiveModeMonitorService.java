package org.artifactory.throttling.lh.service;

/**
 * Service for managing reactive mode activation.
 *
 * @author Amir Avidan
 */
public interface LoadHealerReactiveModeMonitorService {

    /**
     * Manually activates reactive mode for the configured duration.
     * Switches activities to REACTIVE mode, runs for configured time, then restores them.
     * Thread-safe and skips if already active.
     */
    default void activateReactiveMode() {
    }
}
