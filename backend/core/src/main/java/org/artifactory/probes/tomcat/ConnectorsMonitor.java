package org.artifactory.probes.tomcat;

import java.util.Optional;
import java.util.Set;

/**
 * @author Igor Usenko
 */
public interface ConnectorsMonitor {

    String CONNECTORS_MONITOR_ENABLED_EXPRESSION =
            "'${artifactory.connectors.monitor.enabled}' == 'true'";

    /**
     * Returns the names of all available connectors.
     *
     * @return set of connector names. empty set in case of error
     */
    Set<String> getConnectorNames();

    /**
     * Returns the metrics for connector
     *
     * @return metrics, empty Optional in case of error
     */
    Optional<ConnectorMetric> getConnectorMetric(String connectorName);

    /**
     * Returns the active connectors' (those that actually serve client requests) metrics.
     *
     * @return set of connector metrics, empty set in case of error
     */
    Set<ConnectorMetric> getActiveConnectorsMetrics();
}
