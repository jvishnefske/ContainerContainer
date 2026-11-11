package org.artifactory.system.load.monitor;

import org.artifactory.system.load.event.SystemLoadState;

/**
 * Provides the current system load state.
 * Should be used for polling the current system load state.
 * For getting push events on system load state changes, implement
 * {@link org.artifactory.system.load.listener.SystemLoadEventListener} instead.
 *
 * @see SystemLoadState
 * @author Mor Merhav
 */
public interface SystemLoadProvider {

    SystemLoadState getSystemLoadState();
}
