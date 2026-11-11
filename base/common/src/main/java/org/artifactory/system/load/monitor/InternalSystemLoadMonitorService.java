package org.artifactory.system.load.monitor;


import org.artifactory.system.load.listener.SystemLoadEventListener;

/**
 * Internal interface for the System Load Monitor Service, allowing dynamic registration of listeners.
 *
 * @see SystemLoadMonitorService
 * @see SystemLoadEventListener
 */
public interface InternalSystemLoadMonitorService extends SystemLoadMonitorService {

    void registerListener(SystemLoadEventListener listener);
}