package org.artifactory.system.load.monitor;

import lombok.experimental.UtilityClass;
import org.artifactory.probes.tomcat.ConnectorsMonitor;

@UtilityClass
public class SystemLoadMonitorConditions {

    static final String SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION =
            ConnectorsMonitor.CONNECTORS_MONITOR_ENABLED_EXPRESSION +
                    " and '${artifactory.system.load.monitor.enabled}' == 'true'";
    static final String SYSTEM_LOAD_MONITOR_DISABLED_EXPRESSION = "!(" + SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION + ")";
    static final String OUTAGE_SELF_PROBE_ENABLED_EXPRESSION =
            SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION +
                    " and '${artifactory.system.load.outage.probe.enabled:false}' == 'true'";
    static final String OUTAGE_SELF_PROBE_DISABLED_EXPRESSION =
            SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION +
                    " and '${artifactory.system.load.outage.probe.enabled:false}' != 'true'";
}
