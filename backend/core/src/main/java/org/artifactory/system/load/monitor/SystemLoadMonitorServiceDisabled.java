package org.artifactory.system.load.monitor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.listener.SystemLoadEventListener;
import org.springframework.stereotype.Service;

import static org.artifactory.system.load.event.SystemLoadState.HEALTHY;
import static org.artifactory.system.load.monitor.SystemLoadMonitorConditions.SYSTEM_LOAD_MONITOR_DISABLED_EXPRESSION;

@Slf4j
@Service
@ConditionalOnExpression(SYSTEM_LOAD_MONITOR_DISABLED_EXPRESSION)
public class SystemLoadMonitorServiceDisabled implements InternalSystemLoadMonitorService, SystemLoadProvider {

    public SystemLoadMonitorServiceDisabled() {
        log.info(
                "System load monitoring is disabled. No events will be triggered for listeners, and the system " +
                        "load state will always be reported as healthy");
    }

    @Override
    public void registerListener(SystemLoadEventListener listener) {
        // no-op
    }

    @Override
    public void monitorSystemLoad() {
        log.warn("Call to monitorSystemLoad() when System Load Monitor is disabled");
    }

    @Override
    public SystemLoadState getSystemLoadState() {
        return HEALTHY;
    }
}