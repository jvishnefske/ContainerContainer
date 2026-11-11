package org.artifactory.jfbus;

import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.jfrog.eventing.facade.impl.jfbus.JfbusEventingFacade;
import org.jfrog.eventing.jfbus.client.JfbusBrokerClient;
import org.jfrog.eventing.jfbus.client.JfbusBrokerRestClient;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JfbusConfigurationTest extends ArtifactoryHomeBoundTest {

    private static final String JFBUS_URL = "http://broker:8057/jfbus/";
    private static final String RT_USER_AGENT = "http://localhost:8082/artifactory";

    @Mock
    private AccessService accessService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private ArtifactorySystemConfig systemConfiguration;

    @Mock
    private ArtifactoryTracer artifactoryTracer;

    @Mock
    private ArtifactoryContext artifactoryContext;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        bindArtifactoryHome();
        getOrCreateArtifactoryHomeStub();
        setStringSystemProperty(ConstantValues.jfrogUserAgentDefaultURL, RT_USER_AGENT);
        when(centralConfigService.getSystemConfiguration()).thenReturn(systemConfiguration);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
    }

    @AfterEach
    void tearDown() throws Exception {
        unbindArtifactoryHome();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testConstructorWithProvidedBrokerUrl() {
        // Act
        new JfbusConfiguration(accessService, centralConfigService, JFBUS_URL);

        // Assert - should NOT fetch router URL when broker URL is provided
        verify(centralConfigService, never()).getSystemConfiguration();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testConstructorWithNullOrEmptyBrokerUrlUsesRouterUrl(String brokerUrl) {
        // Arrange
        when(systemConfiguration.getRouterUrl()).thenReturn("http://router:8082");

        // Act
        new JfbusConfiguration(accessService, centralConfigService, brokerUrl);

        // Assert - should fetch router URL when broker URL is null or empty
        verify(centralConfigService).getSystemConfiguration();
        verify(systemConfiguration).getRouterUrl();
    }

    @Test
    void testConstructorThrowsWhenRouterUrlIsNull() {
        // Arrange
        when(systemConfiguration.getRouterUrl()).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> new JfbusConfiguration(accessService, centralConfigService, ""));
    }

    @Test
    void testJfbusBrokerClientBeanCreation() {
        // Arrange
        JfbusConfiguration config = new JfbusConfiguration(
                accessService, centralConfigService, JFBUS_URL);

        // Act
        JfbusBrokerClient client = createJfbusBrokerClient(config, 7_200_000L, 100, 20);

        // Assert
        assertInstanceOf(JfbusBrokerRestClient.class, client);
        verify(artifactoryContext).beanForType(ArtifactoryTracer.class);
    }

    @Test
    void testJfbusBrokerClientWithCustomConnectionSettings() {
        // Arrange
        JfbusConfiguration config = new JfbusConfiguration(
                accessService, centralConfigService, JFBUS_URL);

        // Act - create RestClient then broker client with custom settings
        JfbusBrokerClient client = createJfbusBrokerClient(config, 3_600_000L, 200, 50);

        // Assert
        assertInstanceOf(JfbusBrokerRestClient.class, client);
        verify(artifactoryContext).beanForType(ArtifactoryTracer.class);
    }

    @Test
    void testJfbusEventingClientBeanCreation() {
        // Arrange
        JfbusConfiguration config = new JfbusConfiguration(
                accessService, centralConfigService, JFBUS_URL);
        JfbusBrokerClient client = createJfbusBrokerClient(config, 7_200_000L, 100, 20);

        // Act
        var facade = config.jfbusEventingClient(client);

        // Assert
        assertInstanceOf(JfbusEventingFacade.class, facade);
    }

    @Test
    void testMultipleBrokerClientCreationsEachFetchTracer() {
        // Arrange
        JfbusConfiguration config = new JfbusConfiguration(
                accessService, centralConfigService, JFBUS_URL);

        // Act - create multiple clients with different connection settings
        createJfbusBrokerClient(config, 7_200_000L, 100, 20);
        createJfbusBrokerClient(config, 3_600_000L, 200, 50);

        // Assert - tracer should be fetched for each RestClient creation
        verify(artifactoryContext, times(2)).beanForType(ArtifactoryTracer.class);
    }

    private static JfbusBrokerClient createJfbusBrokerClient(JfbusConfiguration config,
            long accessTokenExpirationMillis, int maxHttpConnections, int maxPerRoute) {
        RestClient restClient = config.jfbusRestClient(
                accessTokenExpirationMillis, maxHttpConnections, maxPerRoute);
        return config.jfbusBrokerClient(restClient);
    }
}