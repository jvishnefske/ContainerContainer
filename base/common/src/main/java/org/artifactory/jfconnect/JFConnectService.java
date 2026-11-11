package org.artifactory.jfconnect;

import org.artifactory.aol.store.StoreService;
import org.artifactory.entitlement.EntitlementService;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.jfconnect.client.JFConnectClient;
import org.jfrog.jfconnect.client.MetricsConfigClient;

import javax.annotation.Nullable;

/**
 * @author Asaf Zalcman
 * This service should be the only connection to the jfconnectClient
 */
public interface JFConnectService extends ReloadableBean, EntitlementService, StoreService {
    @Nullable
    MetricsConfigClient getMetricsConfigClient();

    JFConnectClient getJfConnectClient();
}
