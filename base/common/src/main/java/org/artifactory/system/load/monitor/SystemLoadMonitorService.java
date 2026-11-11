package org.artifactory.system.load.monitor;

import org.artifactory.system.load.event.ConnectorLoadState;
import org.artifactory.system.load.event.SystemLoadEvent;

/**
 * Analyzes the system load and triggers events when the system load changes. <br>
 * Should only be called by: {@link org.artifactory.system.load.monitor.job.SystemLoadMonitorJob}.
 * @see SystemLoadEvent
 * @see ConnectorLoadState
 * @see org.artifactory.system.load.listener.SystemLoadEventListener
 * @author Mor Merhav
 */
public interface SystemLoadMonitorService {

    /**
     * Monitors the system load and triggers events to listeners when the system load state changes. <br>
     * <b>Warning</b>: Should only be called by: {@link org.artifactory.system.load.monitor.job.SystemLoadMonitorJob}.
     */
    void monitorSystemLoad();
}