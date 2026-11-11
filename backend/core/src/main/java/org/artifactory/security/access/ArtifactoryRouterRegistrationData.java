package org.artifactory.security.access;

import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.util.TomcatListeningDetector;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.router.registration.Endpoint;
import org.jfrog.access.router.registration.EndpointProtocol;
import org.jfrog.access.router.registration.LocalEndpoint;
import org.jfrog.access.router.registration.RoutePath;
import org.jfrog.access.router.registration.RouterRegistrationData;

import java.util.List;

import static org.artifactory.common.ConstantValues.routerMetricsMaintenancePortEnabled;

/**
 * @author Tamir Hadad
 */
@Data
public class ArtifactoryRouterRegistrationData {
    private static final String ARTIFACTORY_HEALTH_CHECK_ENDPOINT = "/api/v1/system/readiness";
    private static final String METRICS_PATH = "/api/v1/metrics";
    private final ServiceId serviceId;
    private final String nodeId;
    private final int artPort;
    private final int artMaintenancePort;
    private final String contextPath;

    ArtifactoryRouterRegistrationData(ServiceId serviceId, String nodeId, String contextPath) {
        this.serviceId = serviceId;
        this.nodeId = nodeId;
        this.artPort = TomcatListeningDetector.detectMainListeningPort();
        this.artMaintenancePort = TomcatListeningDetector.getMaintenancePort();
        this.contextPath = contextPath;
    }

    public RouterRegistrationData build() {
        List<LocalEndpoint> endpoints = Lists.newArrayList();
        LocalEndpoint endpoint = new LocalEndpoint(artPort, EndpointProtocol.HTTP1, getRoutePaths());
        endpoints.add(endpoint);
        if (routerMetricsMaintenancePortEnabled.getBoolean()) {
            LocalEndpoint metricsEndpoint =
                    new LocalEndpoint(artMaintenancePort, EndpointProtocol.HTTP1, getMetricsRoutePaths());
            endpoints.add(metricsEndpoint);
        }

        Endpoint healthCheckEndpoint = new Endpoint(artMaintenancePort, getArtifactoryHealthCheckEndpoint());
        return new RouterRegistrationData(serviceId, nodeId, endpoints, healthCheckEndpoint);
    }

    private String getArtifactoryHealthCheckEndpoint() {
        if (StringUtils.isNotBlank(contextPath)) {
            return "/" + contextPath + ARTIFACTORY_HEALTH_CHECK_ENDPOINT;
        }
        return ARTIFACTORY_HEALTH_CHECK_ENDPOINT;
    }

    /**
     * Note that the `/lifecycle` routes reside in Artifactory temporary,
     * until the new JFrog Config microservice is implemented and released.
     * (it is going to replace these resources in Artifactory)
     */
    private List<RoutePath> getRoutePaths() {
        return Lists.newArrayList(
                new RoutePath("/artifactory"),
                new RoutePath("/artifactory/(.*)"),
                new RoutePath("/v2/(.*)"),
                new RoutePath("/.well-known/terraform.json"),
                new RoutePath("/lifecycle/api/v2/release_bundle", "/artifactory/api/v2/release_bundle"),
                new RoutePath("/lifecycle/api/v2/release_bundle/(.*)", "/artifactory/api/v2/release_bundle/$1"),
                new RoutePath("/lifecycle/api/v2/promotion/(.*)", "/artifactory/api/v2/promotion/$1"),
                new RoutePath("/lifecycle/api/v2/keys/(.*)", "/artifactory/api/v2/keys/$1"),
                new RoutePath("/lifecycle/api/v2/audit", "/artifactory/api/v2/audit"),
                new RoutePath("/lifecycle/api/v2/audit/(.*)", "/artifactory/api/v2/audit/$1"),
                new RoutePath("/lifecycle/api/v1/evidence/(.*)", "/artifactory/api/v1/evidence/$1"),
                new RoutePath("/package-reroute", "/artifactory/api/package-reroute"),
                new RoutePath("/package-reroute/(.*)","/artifactory/api/package-reroute/$1")
        );
    }

    /**
     * Metrics-specific routes that should be served on the maintenance port
     */
    private List<RoutePath> getMetricsRoutePaths() {
        return Lists.newArrayList(
                new RoutePath("/artifactory" + METRICS_PATH),
                new RoutePath("/artifactory" + METRICS_PATH + "/(.*)")
        );
    }
}
