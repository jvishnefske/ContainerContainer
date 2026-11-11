package org.artifactory.probes.tomcat;

/**
 * @author Igor Usenko
 */
public interface ServerReadinessMonitor {

    String SERVER_READINESS_MONITOR_ENABLED_EXPRESSION =
            ConnectorsMonitor.CONNECTORS_MONITOR_ENABLED_EXPRESSION + " and " +
                    "('${artifactory.tomcat.monitor.mode}' == 'monitor' " +
                    "or '${artifactory.tomcat.monitor.mode}' == 'active')";
    String DISABLED_READINESS_MONITOR_EXPRESSION = "!(" + SERVER_READINESS_MONITOR_ENABLED_EXPRESSION + ")";

    /**
     * Returns server readiness status
     *
     * @return true if the server ready to serve a request, false otherwise
     */
    boolean isReady();
}