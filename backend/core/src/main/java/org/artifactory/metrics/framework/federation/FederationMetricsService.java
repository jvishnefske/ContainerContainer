package org.artifactory.metrics.framework.federation;

import org.artifactory.spring.ReloadableBean;

/**
 * A service to perform metrics logging for federated repos (artifactory-federation-metrics.log)
 *
 * @author Oleksandr Legenkiy
 */
public interface FederationMetricsService extends ReloadableBean {

    String getOpenMetrics(boolean applicationMetricsOnly);

}
