package org.artifactory.system.load.listener;

import org.artifactory.system.load.event.SystemLoadEvent;

/**
 * Interface for listening to system load events.
 * Implementing this interface allows the registration of a listener that will be notified of system load changes.
 * The registration of the listener will be done automatically by the infrastructure.
 * @see SystemLoadEvent
 * @see org.artifactory.system.load.event.SystemLoadState
 * @see org.artifactory.system.load.monitor.SystemLoadMonitorService
 * @author Mor Merhav
 */
public interface SystemLoadEventListener {

    /**
     * Handles a system load event.
     * This method will be called when the system load state changes.
     *
     * @param event the system load event containing the new state
     */
    void onSystemLoadEvent(SystemLoadEvent event);
}