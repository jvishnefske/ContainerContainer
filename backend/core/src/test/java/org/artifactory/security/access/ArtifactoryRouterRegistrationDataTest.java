package org.artifactory.security.access;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.TomcatListeningDetector;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.router.registration.Endpoint;
import org.jfrog.access.router.registration.LocalEndpoint;
import org.jfrog.access.router.registration.RoutePath;
import org.jfrog.access.router.registration.RouterRegistrationData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tamir Hadad
 */
class ArtifactoryRouterRegistrationDataTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryContext applicationContext;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private SysConfig sysConfig;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = openMocks(this);
        ArtifactoryHome home = spy(getOrCreateArtifactoryHomeStub());
        when(home.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(home.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(home);
        when(applicationContext.getArtifactoryHome()).thenReturn(home);
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        unbindArtifactoryHome();
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testRouterRegistrationDataBuilder(boolean metricsPortEnabled) {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.routerMetricsMaintenancePortEnabled)).thenReturn(metricsPortEnabled);

        int port = 9090;
        when(sysConfig.getInt(eq(TomcatListeningDetector.SYS_MAINTENANCE_ARTIFACTORY_PORT), anyInt()))
                .thenReturn(port);
        ServiceId serviceId = new ServiceId("jf", "test");
        RouterRegistrationData registrationData = new ArtifactoryRouterRegistrationData(
                serviceId, "nodeId", "test").build();
        assertThat(registrationData.getNodeId()).isEqualTo("nodeId");
        assertThat(registrationData.getServiceId()).isEqualTo(serviceId);
        List<LocalEndpoint> endpoints = registrationData.getEndpoints();

        assertThat(endpoints).hasSize(metricsPortEnabled ? 2 : 1);

        List<RoutePath> mainRoutePaths = endpoints.get(0).getRoutePaths();
        assertThat(mainRoutePaths).hasSize(13).containsExactly(
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
                new RoutePath("/package-reroute/(.*)", "/artifactory/api/package-reroute/$1")
        );

        if (metricsPortEnabled) {
            List<RoutePath> metricsRoutePaths = endpoints.get(1).getRoutePaths();
            assertThat(metricsRoutePaths).hasSize(2).containsExactly(
                    new RoutePath("/artifactory/api/v1/metrics"),
                    new RoutePath("/artifactory/api/v1/metrics/(.*)")
            );
        }

        Endpoint healthCheckEndpoint = registrationData.getHealthCheckEndpoint();
        assertThat(healthCheckEndpoint.getPath()).isEqualTo("/test/api/v1/system/readiness");
        assertThat(healthCheckEndpoint.getPort()).isEqualTo(port);
    }
}
